package com.musely.app.download

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Progress of looking up a pasted or shared link. */
sealed interface LinkLookup {
    data object Idle : LinkLookup
    data class Loading(val source: MediaSource) : LinkLookup
    data class Ready(val media: ResolvedMedia) : LinkLookup
    data class Error(val message: String) : LinkLookup
}

class DownloadsViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        DownloadCenter.init(app)
    }

    val items: StateFlow<List<DownloadItem>> = DownloadCenter.items
    val events: SharedFlow<DownloadEvent> = DownloadCenter.events
    val hasActiveDownloads: StateFlow<Boolean> = DownloadCenter.items
        .map { list -> list.any { it.state.isActive } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _defaultFormat = MutableStateFlow(DownloadFormat.fromName(prefs.getString(KEY_FORMAT, null)))
    val defaultFormat: StateFlow<DownloadFormat> = _defaultFormat

    private val _videoQuality = MutableStateFlow(VideoQuality.fromName(prefs.getString(KEY_VIDEO_QUALITY, null)))
    val videoQuality: StateFlow<VideoQuality> = _videoQuality

    val input = MutableStateFlow("")

    private val _lookup = MutableStateFlow<LinkLookup>(LinkLookup.Idle)
    val lookup: StateFlow<LinkLookup> = _lookup
    private var lookupJob: Job? = null

    fun setDefaultFormat(format: DownloadFormat) {
        _defaultFormat.value = format
        prefs.edit { putString(KEY_FORMAT, format.name) }
    }

    fun setVideoQuality(quality: VideoQuality) {
        _videoQuality.value = quality
        prefs.edit { putString(KEY_VIDEO_QUALITY, quality.name) }
    }

    /** Parses [text] and, for a supported link, resolves it into a preview. */
    fun lookUp(text: String = input.value) {
        input.value = text.trim()
        lookupJob?.cancel()
        when (val parsed = MediaLinkParser.parse(text)) {
            ParsedLink.NoLink -> _lookup.value = LinkLookup.Error("Paste a link from YouTube, YouTube Music or Spotify.")
            is ParsedLink.Unsupported -> _lookup.value = LinkLookup.Error(parsed.reason)
            is ParsedLink.Supported -> {
                _lookup.value = LinkLookup.Loading(parsed.link.source)
                lookupJob = viewModelScope.launch {
                    _lookup.value = try {
                        LinkLookup.Ready(MediaResolver.resolve(parsed.link))
                    } catch (e: ResolveException) {
                        LinkLookup.Error(e.message ?: "Couldn't read this link.")
                    }
                }
            }
        }
    }

    fun clearLookup() {
        lookupJob?.cancel()
        _lookup.value = LinkLookup.Idle
        input.value = ""
    }

    /**
     * Queues every entry of [media]. [playlistId] adds the finished tracks to a Musely playlist.
     * Returns how many downloads were queued.
     */
    fun download(media: ResolvedMedia, format: DownloadFormat, playlistId: String?): Int {
        val batchId = if (media.isCollection) DownloadCenter.newId() else null
        val quality = _videoQuality.value
        val requests = media.entries.map { entry ->
            DownloadRequest(
                id = DownloadCenter.newId(),
                title = entry.title,
                artist = entry.artist,
                album = entry.album,
                thumbnailUrl = entry.thumbnailUrl ?: media.thumbnailUrl.takeUnless { media.isCollection },
                durationMs = entry.durationMs,
                source = entry.source,
                origin = media.link.source,
                format = format,
                quality = quality,
                playlistId = playlistId,
                batchId = batchId,
            )
        }
        DownloadCenter.enqueue(requests)
        clearLookup()
        return requests.size
    }

    fun cancel(id: String) = DownloadCenter.cancel(id)
    fun cancelAll() = DownloadCenter.cancelAll()
    fun retry(id: String) = DownloadCenter.retry(id)
    fun remove(id: String) = DownloadCenter.remove(id)
    fun clearFinished() = DownloadCenter.clearFinished()

    private companion object {
        const val PREFS_NAME = "musely_download_prefs"
        const val KEY_FORMAT = "default_format"
        const val KEY_VIDEO_QUALITY = "video_quality"
    }
}
