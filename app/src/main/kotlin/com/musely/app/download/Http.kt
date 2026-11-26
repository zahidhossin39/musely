package com.musely.app.download

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Which hosts a request may reach, checked on the first URL and on every redirect hop. Shared
 * links are untrusted input, so they must never steer Musely to arbitrary servers.
 */
internal enum class HostPolicy(private val suffixes: List<String>) {
    /** Stream files and artwork from YouTube and Spotify CDNs. */
    Media(listOf("googlevideo.com", "youtube.com", "ytimg.com", "ggpht.com", "googleusercontent.com", "scdn.co", "spotifycdn.com")),
    /** Spotify embed pages. */
    Spotify(listOf("spotify.com")),
    /** Spotify share links bounce through Spotify's link service before reaching open.spotify.com. */
    SpotifyShortLink(listOf("spotify.link", "spoti.fi", "app.link", "spotify.com")),
    ;

    fun allows(url: URL): Boolean {
        if (!url.protocol.equals("https", ignoreCase = true)) return false
        val host = url.host?.lowercase() ?: return false
        return suffixes.any { host == it || host.endsWith(".$it") }
    }
}

internal object Http {
    /** Desktop browser identity, matching what YouTube's web endpoints expect. */
    const val BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
    const val CONNECT_TIMEOUT_MS = 15_000
    const val READ_TIMEOUT_MS = 30_000
    private const val MAX_TEXT_BYTES = 8 * 1024 * 1024
    private const val MAX_REDIRECTS = 5

    /**
     * Opens [url] and follows up to five redirects by hand, re-checking [policy] at each hop.
     * The returned connection has already received its response headers.
     */
    fun open(
        url: String,
        policy: HostPolicy,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
    ): HttpURLConnection {
        var current = URL(url)
        repeat(MAX_REDIRECTS + 1) {
            if (!policy.allows(current)) throw IOException("Blocked a request to an untrusted host")
            val conn = (current.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", BROWSER_USER_AGENT)
                headers.forEach { (name, value) -> setRequestProperty(name, value) }
            }
            val code = conn.responseCode
            val location = conn.getHeaderField("Location")
            if (code !in 300..399 || location.isNullOrBlank()) return conn
            conn.disconnect()
            current = URL(current, location)
        }
        throw IOException("Too many redirects")
    }

    /** GETs [url] as text. Throws [IOException] for non-2xx responses. */
    fun getText(url: String, policy: HostPolicy): TextResponse {
        val conn = open(url, policy)
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code from ${conn.url.host}")
            val bytes = conn.inputStream.use { it.readBytesLimited(MAX_TEXT_BYTES) }
                ?: throw IOException("Response from ${conn.url.host} is too large")
            return TextResponse(String(bytes, Charsets.UTF_8), conn.url.toString())
        } finally {
            conn.disconnect()
        }
    }

    /** GETs [url] as bytes, or null on any failure, a disallowed host, or more than [maxBytes]. */
    fun getBytes(url: String, maxBytes: Int, policy: HostPolicy = HostPolicy.Media): ByteArray? {
        val conn = try {
            open(url, policy)
        } catch (_: IOException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        return try {
            if (conn.responseCode !in 200..299) null else conn.inputStream.use { it.readBytesLimited(maxBytes) }
        } catch (_: IOException) {
            null
        } finally {
            conn.disconnect()
        }
    }

    /** Reads at most [limit] bytes; null if the stream is longer. */
    fun InputStream.readBytesLimited(limit: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            if (out.size() + read > limit) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    data class TextResponse(val body: String, val finalUrl: String)
}

/** Decodes untrusted image bytes without letting a tiny file claim gigapixel dimensions. */
internal object BoundedBitmap {
    private const val MAX_PIXELS = 24L * 1024 * 1024

    fun decode(bytes: ByteArray, targetEdgePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0 || width.toLong() * height > MAX_PIXELS) return null
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= targetEdgePx) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}

/** The networking backend NewPipeExtractor uses for every page and API call. */
internal class NewPipeHttpDownloader : Downloader() {

    override fun execute(request: Request): Response {
        val url = request.url()
        require(url.startsWith("https://", ignoreCase = true)) { "Only HTTPS URLs are fetched" }
        // Extractor URLs are built by the library itself (YouTube/Google endpoints), not taken from
        // shared text, so the platform's https-only redirect handling is enough here.
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = request.httpMethod()
            connectTimeout = Http.CONNECT_TIMEOUT_MS
            readTimeout = Http.READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", Http.BROWSER_USER_AGENT)
        }
        try {
            request.headers().forEach { (name, values) ->
                values.firstOrNull()?.let { conn.setRequestProperty(name, it) }
                values.drop(1).forEach { conn.addRequestProperty(name, it) }
            }
            request.dataToSend()?.let { body ->
                conn.doOutput = true
                conn.setFixedLengthStreamingMode(body.size)
                conn.outputStream.use { it.write(body) }
            }
            val code = conn.responseCode
            if (code == HTTP_TOO_MANY_REQUESTS) {
                throw ReCaptchaException("YouTube is rate limiting requests. Try again in a few minutes.", url)
            }
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val body = stream?.use { input ->
                with(Http) { input.readBytesLimited(MAX_RESPONSE_BYTES) }
                    ?: throw IOException("Response from ${conn.url.host} is too large")
            }?.let { String(it, Charsets.UTF_8) }.orEmpty()
            val headers = conn.headerFields.filterKeys { it != null }.mapKeys { it.key.orEmpty() }
            return Response(code, conn.responseMessage.orEmpty(), headers, body, conn.url.toString())
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val MAX_RESPONSE_BYTES = 32 * 1024 * 1024
    }
}
