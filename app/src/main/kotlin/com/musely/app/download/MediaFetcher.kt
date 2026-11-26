package com.musely.app.download

import android.util.Log
import java.io.File

/** A downloaded file ready to tag and publish. */
internal data class PreparedMedia(
    val file: File,
    val extension: String,
    val mimeType: String,
    val isVideo: Boolean,
    /** MP4-family containers can carry title/artist/cover tags. */
    val taggable: Boolean,
    /** Thumbnail of the YouTube video the file came from. */
    val sourceThumbnailUrl: String? = null,
)

/** Fetches the right streams for a YouTube video and turns them into one playable file. */
internal object MediaFetcher {

    private const val TAG = "MuselyDownloads"
    private const val NO_AUDIO = "This video has no downloadable audio."
    private const val NO_VIDEO = "This video has no downloadable video stream."

    /** Reports `(part, downloadedBytes, totalBytes)`; video downloads have two parts. */
    fun interface Progress {
        fun report(part: String, downloaded: Long, total: Long)
    }

    suspend fun fetchAudio(videoUrl: String, workDir: File, progress: Progress): PreparedMedia {
        val streams = MediaResolver.fetchStreams(videoUrl)
        val chosen = StreamSelector.pickAudio(streams.audio) ?: throw ResolveException(NO_AUDIO)
        val isAac = chosen.suffix.equals("m4a", ignoreCase = true)
        val raw = File(workDir, "audio.${if (isAac) "m4a" else chosen.suffix.ifBlank { "webm" }}")
        downloadStream(videoUrl, streams, NO_AUDIO, { StreamSelector.pickAudio(it.audio) }, raw) { d, t ->
            progress.report("audio", d, t)
        }

        if (!isAac) {
            return PreparedMedia(raw, raw.extension, "audio/webm", isVideo = false, taggable = false, sourceThumbnailUrl = streams.thumbnailUrl)
        }
        // YouTube serves fragmented M4A; remuxing gives a regular file that can hold tags.
        val remuxed = File(workDir, "audio-remuxed.m4a")
        val ok = runCatching { MediaRemuxer.remux(listOf(raw), remuxed) }
            .onFailure { Log.w(TAG, "Audio remux failed, keeping the original stream", it) }
            .isSuccess && remuxed.length() > 0
        return PreparedMedia(if (ok) remuxed else raw, "m4a", "audio/mp4", isVideo = false, taggable = ok, sourceThumbnailUrl = streams.thumbnailUrl)
    }

    suspend fun fetchVideo(videoUrl: String, quality: VideoQuality, workDir: File, progress: Progress): PreparedMedia {
        val streams = MediaResolver.fetchStreams(videoUrl)
        val output = File(workDir, "video.mp4")
        val pickVideo = { set: StreamSet -> StreamSelector.pickVideoOnly(set.videoOnly, quality.maxHeight) }
        val pickAudio = { set: StreamSet -> StreamSelector.pickAacAudio(set.audio) }

        if (pickVideo(streams) != null && pickAudio(streams) != null) {
            val videoFile = File(workDir, "video-track.mp4")
            val audioFile = File(workDir, "audio-track.m4a")
            downloadStream(videoUrl, streams, NO_VIDEO, pickVideo, videoFile) { d, t -> progress.report("video", d, t) }
            downloadStream(videoUrl, streams, NO_AUDIO, pickAudio, audioFile) { d, t -> progress.report("audio", d, t) }
            MediaRemuxer.remux(listOf(videoFile, audioFile), output)
            videoFile.delete()
            audioFile.delete()
            return PreparedMedia(output, "mp4", "video/mp4", isVideo = true, taggable = true, sourceThumbnailUrl = streams.thumbnailUrl)
        }

        // Fall back to a stream that already contains both picture and sound.
        val pickMuxed = { set: StreamSet -> StreamSelector.pickMuxed(set.muxed, quality.maxHeight) }
        pickMuxed(streams) ?: throw ResolveException(NO_VIDEO)
        downloadStream(videoUrl, streams, NO_VIDEO, pickMuxed, output) { d, t -> progress.report("video", d, t) }
        return PreparedMedia(output, "mp4", "video/mp4", isVideo = true, taggable = true, sourceThumbnailUrl = streams.thumbnailUrl)
    }

    private fun sameVariant(a: StreamCandidate, b: StreamCandidate): Boolean =
        a.suffix == b.suffix && a.codec == b.codec && a.bitrate == b.bitrate && a.height == b.height && a.fps == b.fps

    /**
     * Downloads the stream [pick] selects. If YouTube rejects the signed URL (it expires after a
     * few hours, or after long pauses) the streams are fetched again once and the transfer resumes.
     */
    private suspend fun downloadStream(
        videoUrl: String,
        initial: StreamSet,
        missingMessage: String,
        pick: (StreamSet) -> StreamCandidate?,
        dest: File,
        onBytes: (Long, Long) -> Unit,
    ) {
        var streams = initial
        var refreshed = false
        var previous: StreamCandidate? = null
        while (true) {
            val stream = pick(streams) ?: throw ResolveException(missingMessage)
            // After a refresh YouTube may offer a different variant; never splice two streams together.
            val last = previous
            if (last != null && !sameVariant(last, stream)) dest.delete()
            previous = stream
            try {
                ChunkedDownloader(streams.userAgent).download(stream.url, dest, onBytes)
                return
            } catch (e: StreamExpiredException) {
                if (refreshed) throw ResolveException("YouTube refused the download. Try again later.", e)
                refreshed = true
                streams = MediaResolver.fetchStreams(videoUrl)
            }
        }
    }
}
