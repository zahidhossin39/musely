package com.musely.app.download

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

/** Where a link points. Drives labels, badges and how a link is resolved. */
enum class MediaSource(val label: String) {
    YouTube("YouTube"),
    YouTubeMusic("YouTube Music"),
    Spotify("Spotify"),
}

/** A link Musely knows how to download from, normalised to the parts that matter. */
sealed interface MediaLink {
    val source: MediaSource

    /** Canonical URL used for extraction and display. */
    val url: String

    data class YouTubeVideo(val videoId: String, val music: Boolean) : MediaLink {
        override val source get() = if (music) MediaSource.YouTubeMusic else MediaSource.YouTube
        override val url get() = "https://www.youtube.com/watch?v=$videoId"
    }

    data class YouTubePlaylist(val playlistId: String, val music: Boolean) : MediaLink {
        override val source get() = if (music) MediaSource.YouTubeMusic else MediaSource.YouTube
        override val url get() = "https://www.youtube.com/playlist?list=$playlistId"
    }

    data class SpotifyTrack(val id: String) : MediaLink {
        override val source get() = MediaSource.Spotify
        override val url get() = "https://open.spotify.com/track/$id"
    }

    data class SpotifyAlbum(val id: String) : MediaLink {
        override val source get() = MediaSource.Spotify
        override val url get() = "https://open.spotify.com/album/$id"
    }

    data class SpotifyPlaylist(val id: String) : MediaLink {
        override val source get() = MediaSource.Spotify
        override val url get() = "https://open.spotify.com/playlist/$id"
    }

    /** `spotify.link` / `spoti.fi` share links; they must be followed to learn what they point at. */
    data class SpotifyShortLink(override val url: String) : MediaLink {
        override val source get() = MediaSource.Spotify
    }
}

/** Outcome of reading pasted or shared text. */
sealed interface ParsedLink {
    data class Supported(val link: MediaLink) : ParsedLink
    data class Unsupported(val reason: String) : ParsedLink
    data object NoLink : ParsedLink
}

/** Turns pasted text or a share-sheet payload into a [ParsedLink]. Pure, so it is unit tested. */
object MediaLinkParser {

    private val urlPattern = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val spotifyUriPattern = Regex("""spotify:(track|album|playlist):([A-Za-z0-9]{22})""")
    private val videoIdPattern = Regex("""^[A-Za-z0-9_-]{11}$""")
    private val playlistIdPattern = Regex("""^[A-Za-z0-9_-]{10,64}$""")
    private val spotifyIdPattern = Regex("""^[A-Za-z0-9]{22}$""")
    private val trailingJunk = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '»', '"', '\'')

    private val youtubeHosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com")
    private val youtubeMusicHosts = setOf("music.youtube.com")
    private val spotifyHosts = setOf("open.spotify.com", "play.spotify.com")
    private val spotifyShortHosts = setOf("spotify.link", "spoti.fi")

    const val UNSUPPORTED_SITE = "Only YouTube, YouTube Music and Spotify links are supported."
    const val UNSUPPORTED_MIX = "YouTube Mixes are endless radios and can't be downloaded. Share a regular playlist instead."
    const val UNSUPPORTED_YTM_PAGE = "Open the album or playlist in YouTube Music and share it from there."
    const val UNSUPPORTED_SPOTIFY_PAGE = "Share a Spotify track, album or playlist."

    /** The first http(s) URL in [text], with trailing sentence punctuation removed. */
    fun extractUrl(text: String): String? =
        urlPattern.find(text)?.value?.trimEnd(*trailingJunk)?.takeIf { it.length > "https://".length }

    fun parse(text: String): ParsedLink {
        spotifyUriPattern.find(text)?.let { match ->
            return ParsedLink.Supported(spotifyLink(match.groupValues[1], match.groupValues[2]))
        }
        val raw = extractUrl(text) ?: return ParsedLink.NoLink
        val uri = try {
            URI(raw)
        } catch (_: URISyntaxException) {
            return ParsedLink.Unsupported(UNSUPPORTED_SITE)
        }
        val host = uri.host?.lowercase() ?: return ParsedLink.Unsupported(UNSUPPORTED_SITE)
        val segments = uri.rawPath.orEmpty().split('/').filter { it.isNotEmpty() }
        val query = parseQuery(uri.rawQuery)
        return when {
            host == "youtu.be" -> segments.firstOrNull()?.let(::videoOrNull)
                ?.let { ParsedLink.Supported(MediaLink.YouTubeVideo(it, music = false)) }
                ?: ParsedLink.Unsupported(UNSUPPORTED_SITE)
            host in youtubeHosts -> parseYouTube(segments, query, music = false)
            host in youtubeMusicHosts -> parseYouTube(segments, query, music = true)
            host in spotifyHosts -> parseSpotify(segments)
            host in spotifyShortHosts && segments.isNotEmpty() ->
                ParsedLink.Supported(MediaLink.SpotifyShortLink(raw))
            else -> ParsedLink.Unsupported(UNSUPPORTED_SITE)
        }
    }

    private fun parseYouTube(segments: List<String>, query: Map<String, String>, music: Boolean): ParsedLink {
        val first = segments.firstOrNull()
        val videoId = query["v"]?.let(::videoOrNull)
        val listId = query["list"]?.takeIf { playlistIdPattern.matches(it) }
        return when {
            // A song shared from a playlist or radio carries both; the user meant the song.
            first == "watch" && videoId != null -> ParsedLink.Supported(MediaLink.YouTubeVideo(videoId, music))
            first in setOf("shorts", "live", "embed", "v") ->
                segments.getOrNull(1)?.let(::videoOrNull)
                    ?.let { ParsedLink.Supported(MediaLink.YouTubeVideo(it, music)) }
                    ?: ParsedLink.Unsupported(UNSUPPORTED_SITE)
            (first == "playlist" || first == "watch") && listId != null -> playlistOrMix(listId, music)
            music && first == "browse" -> ParsedLink.Unsupported(UNSUPPORTED_YTM_PAGE)
            else -> ParsedLink.Unsupported(UNSUPPORTED_SITE)
        }
    }

    private fun playlistOrMix(listId: String, music: Boolean): ParsedLink =
        if (isMixId(listId)) {
            ParsedLink.Unsupported(UNSUPPORTED_MIX)
        } else {
            ParsedLink.Supported(MediaLink.YouTubePlaylist(listId, music))
        }

    /** Auto-generated radios ("RD…") never end, so they are not offered for download. */
    internal fun isMixId(listId: String): Boolean = listId.startsWith("RD")

    private fun parseSpotify(segments: List<String>): ParsedLink {
        // Localised links look like /intl-de/track/<id>.
        val parts = if (segments.firstOrNull()?.startsWith("intl-") == true) segments.drop(1) else segments
        val kind = parts.getOrNull(0)
        val id = parts.getOrNull(1)?.takeIf { spotifyIdPattern.matches(it) }
        return if (kind != null && kind in setOf("track", "album", "playlist") && id != null) {
            ParsedLink.Supported(spotifyLink(kind, id))
        } else {
            ParsedLink.Unsupported(UNSUPPORTED_SPOTIFY_PAGE)
        }
    }

    private fun spotifyLink(kind: String, id: String): MediaLink = when (kind) {
        "track" -> MediaLink.SpotifyTrack(id)
        "album" -> MediaLink.SpotifyAlbum(id)
        else -> MediaLink.SpotifyPlaylist(id)
    }

    private fun videoOrNull(candidate: String): String? = candidate.takeIf { videoIdPattern.matches(it) }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrEmpty()) return emptyMap()
        return rawQuery.split('&').mapNotNull { pair ->
            val key = pair.substringBefore('=', missingDelimiterValue = "")
            if (key.isEmpty()) return@mapNotNull null
            val value = pair.substringAfter('=', missingDelimiterValue = "")
            runCatching { URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8") }.getOrNull()
        }.toMap()
    }
}
