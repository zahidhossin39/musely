package com.musely.app.download

/** What the user wants saved. Audio is the default and the priority path. */
enum class DownloadFormat(val label: String) {
    Audio("Audio"),
    Video("Video"),
    ;

    companion object {
        fun fromName(name: String?): DownloadFormat = entries.firstOrNull { it.name == name } ?: Audio
    }
}

/** Where the bytes come from. Spotify tracks are matched on YouTube Music when the download starts. */
sealed interface DownloadSource {
    data class YouTube(val url: String) : DownloadSource
    data class SpotifyMatch(val track: SpotifyTrackInfo) : DownloadSource
}

/** One file to fetch, with everything needed to retry it later. */
data class DownloadRequest(
    val id: String,
    val title: String,
    val artist: String,
    /** Album name to tag with; null means "use the title" (single). */
    val album: String?,
    val thumbnailUrl: String?,
    val durationMs: Long,
    val source: DownloadSource,
    val origin: MediaSource,
    val format: DownloadFormat,
    val quality: VideoQuality,
    /** Musely playlist to add the finished track to, if the user asked for one. */
    val playlistId: String? = null,
    /** Shared by every track queued from the same album or playlist link. */
    val batchId: String? = null,
)

sealed interface DownloadState {
    data object Queued : DownloadState
    /** Looking up streams (or the YouTube Music match for a Spotify track). */
    data object Preparing : DownloadState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : DownloadState {
        /** 0..1, or null while the size is unknown. */
        val fraction: Float? get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null
    }
    /** Merging, tagging and saving to the media library. */
    data object Processing : DownloadState
    data class Completed(val trackId: Long, val sizeBytes: Long, val finishedAtMs: Long) : DownloadState
    data class Failed(val message: String) : DownloadState
    data object Canceled : DownloadState
}

val DownloadState.isActive: Boolean
    get() = when (this) {
        DownloadState.Queued, DownloadState.Preparing, is DownloadState.Downloading, DownloadState.Processing -> true
        is DownloadState.Completed, is DownloadState.Failed, DownloadState.Canceled -> false
    }

data class DownloadItem(
    val request: DownloadRequest,
    val state: DownloadState,
    val createdAtMs: Long,
)

/** Things the UI reacts to once, e.g. to offer post-download actions. */
sealed interface DownloadEvent {
    data class Completed(val item: DownloadItem, val trackId: Long) : DownloadEvent
    data class BatchFinished(val batchId: String, val completed: Int, val failed: Int, val playlistId: String?) : DownloadEvent
}
