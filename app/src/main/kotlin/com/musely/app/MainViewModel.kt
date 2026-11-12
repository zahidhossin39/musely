package com.musely.app

import android.app.Application
import android.content.ComponentName
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class PlaybackState(
    val song: Song? = null,
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val durationMs: Long = 0,
    /** The song that plays after the current one, if the queue has one. */
    val nextSong: Song? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs

    private val _loaded = MutableStateFlow(false)
    /** False until the first library scan has finished. */
    val loaded: StateFlow<Boolean> = _loaded

    val query = MutableStateFlow("")

    val results: StateFlow<List<Song>> = combine(_songs, query) { s, q -> s.search(q) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val albums: StateFlow<List<Album>> = _songs.map { it.toAlbums() }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val artists: StateFlow<List<Artist>> = _songs.map { it.toArtists() }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _playback = MutableStateFlow(PlaybackState())
    val playback: StateFlow<PlaybackState> = _playback

    private var songsById: Map<Long, Song> = emptyMap()
    private var controller: MediaController? = null
    private val controllerFuture: ListenableFuture<MediaController>
    private var loadJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = syncPlayback()
    }

    /** Rescans when files are added or removed while the app is open. */
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = loadLibrary(debounceMs = 800)
    }

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(app, token).buildAsync()
        controllerFuture.addListener({
            controller = runCatching { controllerFuture.get() }.getOrNull()
            controller?.addListener(playerListener)
            syncPlayback()
        }, ContextCompat.getMainExecutor(app))
        app.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer,
        )
    }

    fun loadLibrary(debounceMs: Long = 0) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            delay(debounceMs)
            val list = withContext(Dispatchers.IO) {
                runCatching { loadSongs(getApplication()) }.getOrDefault(emptyList())
            }
            songsById = list.associateBy { it.id }
            _songs.value = list
            _loaded.value = true
            syncPlayback()
        }
    }

    private fun syncPlayback() {
        val c = controller ?: return
        val nextIndex = c.nextMediaItemIndex
        _playback.value = PlaybackState(
            song = c.currentMediaItem?.let(::songFor),
            isPlaying = c.isPlaying,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            durationMs = c.duration.coerceAtLeast(0),
            nextSong = if (nextIndex == C.INDEX_UNSET) null else songFor(c.getMediaItemAt(nextIndex)),
        )
    }

    private fun songFor(item: MediaItem): Song? = item.mediaId.toLongOrNull()?.let { songsById[it] }

    /** Replaces the queue with [queue] and starts playing at [index]. */
    fun play(queue: List<Song>, index: Int) {
        val c = controller ?: return
        c.setMediaItems(queue.map { it.toMediaItem() }, index, 0L)
        c.prepare()
        c.play()
    }

    /** Plays [queue] in order from the top, turning shuffle off. */
    fun playAll(queue: List<Song>) {
        if (queue.isEmpty()) return
        controller?.shuffleModeEnabled = false
        play(queue, 0)
    }

    /** Plays [queue] shuffled, starting from a random song. */
    fun shuffle(queue: List<Song>) {
        if (queue.isEmpty()) return
        controller?.shuffleModeEnabled = true
        play(queue, Random.nextInt(queue.size))
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() { controller?.seekToNextMediaItem() }

    fun previous() { controller?.seekToPrevious() }

    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs) }

    fun positionMs(): Long = controller?.currentPosition ?: 0

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    override fun onCleared() {
        getApplication<Application>().contentResolver.unregisterContentObserver(observer)
        controller?.removeListener(playerListener)
        MediaController.releaseFuture(controllerFuture)
    }

    private fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(albumArtUri(albumId))
                .build(),
        )
        .build()
}
