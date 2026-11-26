package com.musely.app.download

/**
 * The parts of an extractor stream that matter for choosing what to download. Kept separate from
 * the extractor's own classes so selection is plain Kotlin and unit tested.
 */
data class StreamCandidate(
    val url: String,
    /** File extension of the container, e.g. `m4a`, `webm`, `mp4`. */
    val suffix: String,
    val codec: String?,
    val bitrate: Int,
    val height: Int = 0,
    val fps: Int = 0,
    /** Only plain HTTP files can be fetched directly; DASH/HLS manifests are skipped. */
    val progressive: Boolean = true,
)

/** Video resolutions offered in Settings, capped at the chosen height. */
enum class VideoQuality(val maxHeight: Int, val label: String) {
    P360(360, "360p"),
    P480(480, "480p"),
    P720(720, "720p"),
    P1080(1080, "1080p"),
    ;

    companion object {
        val DEFAULT = P720
        fun fromName(name: String?): VideoQuality = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

object StreamSelector {

    private fun StreamCandidate.isAac() = suffix.equals("m4a", true) || codec?.startsWith("mp4a", true) == true

    /** H.264 is the one video codec every Android device can mux into MP4 and play back. */
    private fun StreamCandidate.isH264Mp4() =
        suffix.equals("mp4", true) && codec?.startsWith("avc1", true) == true

    /**
     * Best audio-only stream: AAC in M4A first (taggable, plays everywhere), else the best Opus/WebM.
     */
    fun pickAudio(streams: List<StreamCandidate>): StreamCandidate? {
        val usable = streams.filter { it.progressive && it.url.isNotBlank() }
        return usable.filter { it.isAac() }.maxByOrNull { it.bitrate }
            ?: usable.maxByOrNull { it.bitrate }
    }

    /** AAC audio to pair with a video-only stream (MP4 can only hold AAC from these sources). */
    fun pickAacAudio(streams: List<StreamCandidate>): StreamCandidate? =
        streams.filter { it.progressive && it.url.isNotBlank() && it.isAac() }.maxByOrNull { it.bitrate }

    /**
     * Highest H.264 video-only stream at or below [maxHeight]; if every stream is taller, the
     * smallest one so the user still gets a video.
     */
    fun pickVideoOnly(streams: List<StreamCandidate>, maxHeight: Int): StreamCandidate? {
        val h264 = streams.filter { it.progressive && it.url.isNotBlank() && it.isH264Mp4() && it.height > 0 }
        val comparator = compareBy<StreamCandidate>({ it.height }, { it.fps }, { it.bitrate })
        return h264.filter { it.height <= maxHeight }.maxWithOrNull(comparator)
            ?: h264.minWithOrNull(comparator)
    }

    /** Pre-muxed MP4 (video + audio in one file), used when separate streams are unavailable. */
    fun pickMuxed(streams: List<StreamCandidate>, maxHeight: Int): StreamCandidate? {
        val mp4 = streams.filter { it.progressive && it.url.isNotBlank() && it.suffix.equals("mp4", true) }
        val comparator = compareBy<StreamCandidate>({ it.height }, { it.bitrate })
        return mp4.filter { it.height <= maxHeight }.maxWithOrNull(comparator) ?: mp4.minWithOrNull(comparator)
    }
}
