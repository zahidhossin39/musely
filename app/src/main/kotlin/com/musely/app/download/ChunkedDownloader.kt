package com.musely.app.download

import android.annotation.SuppressLint
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection

/** The stream URL stopped being valid (YouTube signs URLs for a few hours); re-extract and retry. */
class StreamExpiredException : IOException("The stream link expired")

/**
 * Downloads a media stream in ranged chunks. Chunking keeps YouTube from throttling long
 * transfers, and lets a dropped connection resume from the last byte instead of restarting.
 */
internal class ChunkedDownloader(private val userAgent: String) {

    /** Writes [url] into [dest] (appending to a partial file) and reports `(downloaded, total)`. */
    suspend fun download(url: String, dest: File, onProgress: (Long, Long) -> Unit) {
        RandomAccessFile(dest, "rw").use { file ->
            var position = file.length()
            var total = -1L
            var failures = 0
            while (total < 0 || position < total) {
                currentCoroutineContext().ensureActive()
                try {
                    val result = fetchChunk(url, position, file, onProgress, total, dest)
                    total = result.total
                    position = result.position
                    failures = 0
                    if (result.endOfStream) break
                } catch (e: StreamExpiredException) {
                    throw e
                } catch (e: ResolveException) {
                    throw e
                } catch (e: IOException) {
                    currentCoroutineContext().ensureActive()
                    failures++
                    if (failures > MAX_RETRIES) throw e
                    delay(RETRY_BASE_DELAY_MS * failures)
                    position = file.length()
                }
            }
            if (total >= 0 && file.length() != total) {
                throw IOException("Download ended early (${file.length()} of $total bytes)")
            }
        }
    }

    private data class ChunkResult(val position: Long, val total: Long, val endOfStream: Boolean)

    private suspend fun fetchChunk(
        url: String,
        start: Long,
        file: RandomAccessFile,
        onProgress: (Long, Long) -> Unit,
        knownTotal: Long,
        dest: File,
    ): ChunkResult {
        val end = start + CHUNK_BYTES - 1
        val conn = Http.open(url, HostPolicy.Media, headers = mapOf("User-Agent" to userAgent, "Range" to "bytes=$start-$end"))
        // Blocking reads ignore coroutine cancellation; closing the socket makes them return.
        val cancelHook = currentCoroutineContext()[Job]?.invokeOnCompletion { conn.disconnect() }
        try {
            val code = conn.responseCode
            when {
                code == HttpURLConnection.HTTP_FORBIDDEN || code == HttpURLConnection.HTTP_GONE ->
                    throw StreamExpiredException()
                // Asked for bytes past the end: with an unknown size this is how the stream ends.
                code == HTTP_RANGE_NOT_SATISFIABLE && start > 0 && (knownTotal < 0 || start >= knownTotal) ->
                    return ChunkResult(start, if (knownTotal >= 0) knownTotal else start, endOfStream = true)
                code != HttpURLConnection.HTTP_PARTIAL && code != HttpURLConnection.HTTP_OK ->
                    throw IOException("HTTP $code while downloading")
            }
            // A 200 means the server ignored the range and is sending the whole file.
            val fullBody = code == HttpURLConnection.HTTP_OK
            val total = if (fullBody) {
                conn.contentLengthLong
            } else {
                parseContentRangeTotal(conn.getHeaderField("Content-Range")) ?: knownTotal
            }
            checkCapacity(total, dest)
            var position = if (fullBody) 0L else start
            if (fullBody) file.setLength(0)
            file.seek(position)
            val chunkStart = position
            val buffer = ByteArray(BUFFER_BYTES)
            conn.inputStream.use { input ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    file.write(buffer, 0, read)
                    position += read
                    if (position > MAX_FILE_BYTES) throw ResolveException(TOO_LARGE)
                    onProgress(position, total)
                }
            }
            if (position == chunkStart && !fullBody) throw IOException("The server sent an empty response")
            return ChunkResult(position, if (fullBody && total < 0) position else total, endOfStream = fullBody)
        } finally {
            cancelHook?.dispose()
            conn.disconnect()
        }
    }

    // A conservative free-space check is intended: it only guards against obviously impossible downloads.
    @SuppressLint("UsableSpace")
    private fun checkCapacity(total: Long, dest: File) {
        if (total <= 0) return
        if (total > MAX_FILE_BYTES) throw ResolveException(TOO_LARGE)
        val free = dest.parentFile?.usableSpace ?: return
        if (total - dest.length() > free - FREE_SPACE_MARGIN_BYTES) {
            throw ResolveException("Not enough free space on your phone for this download.")
        }
    }

    companion object {
        /** 10 MiB per request: large enough to be efficient, small enough to avoid throttling. */
        const val CHUNK_BYTES = 10L * 1024 * 1024
        /** No single stream Musely downloads should come near this; it guards against bogus servers. */
        const val MAX_FILE_BYTES = 4L * 1024 * 1024 * 1024
        private const val FREE_SPACE_MARGIN_BYTES = 64L * 1024 * 1024
        private const val BUFFER_BYTES = 64 * 1024
        private const val MAX_RETRIES = 4
        private const val RETRY_BASE_DELAY_MS = 1_500L
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
        private const val TOO_LARGE = "This file is too large to download."

        /** Total size from a `Content-Range: bytes 0-99/1234` header, or null when unknown (`*`). */
        fun parseContentRangeTotal(header: String?): Long? =
            header?.substringAfterLast('/', "")?.trim()?.toLongOrNull()?.takeIf { it >= 0 }
    }
}
