package com.musely.app

import android.app.Application
import android.content.ComponentName
import android.content.Context
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

private const val UPDATER_PREFS_NAME = "musely_updater_prefs"
private const val KEY_LAST_UPDATE_CHECK_MS = "last_update_check_ms"

/**
 * Maps drag-and-drop indices from a hidden-filtered [visibleSongIds] list into raw indices
 * within [rawSongIds] so hidden tracks never misalign swaps.
 */
internal fun mapVisibleMoveToRawIndices(
    rawSongIds: List<Long>,
    visibleSongIds: List<Long>,
    fromIndex: Int,
    toIndex: Int,
): Pair<Int, Int>? {
    if (fromIndex !in visibleSongIds.indices || toIndex !in visibleSongIds.indices || fromIndex == toIndex) {
        return null
    }
    val fromId = visibleSongIds[fromIndex]
    val toId = visibleSongIds[toIndex]
    val rawFrom = rawSongIds.indexOf(fromId)
    val rawTo = rawSongIds.indexOf(toId)
    if (rawFrom < 0 || rawTo < 0 || rawFrom == rawTo) return null
    return rawFrom to rawTo
}

/** Returns queue indices matching [songId] in descending order for safe removal. */
internal fun findMatchingQueueIndicesDescending(mediaIds: List<String>, songId: Long): List<Int> {
    val target = songId.toString()
    val indices = ArrayList<Int>()
    for (i in mediaIds.lastIndex downTo 0) {
        if (mediaIds[i] == target) {
            indices += i
        }
    }
    return indices
}

/**
 * Resolves the next [UpdateStatus] after a check completes, preserving [UpdateStatus.Available]
 * when a background check encounters a transient error.
 */
internal fun resolveUpdateStatusAfterCheck(
    current: UpdateStatus,
    result: UpdateStatus,
    isManual: Boolean,
): UpdateStatus {
    if (isManual) return result
    if (result is UpdateStatus.Available) return result
    if (result is UpdateStatus.Error) return current
    return if (current is UpdateStatus.Available) current else result
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val store = PlaylistStore(app)
    private val _userState = MutableStateFlow(store.loadState())

    private val _allSongs = MutableStateFlow<List<Song>>(emptyList())
    val allSongs: StateFlow<List<Song>> = _allSongs

    val hiddenIds: StateFlow<Set<Long>> = _userState
        .map { it.hiddenSongIds }
        .stateIn(viewModelScope, SharingStarted.Eagerly, _userState.value.hiddenSongIds)

    val songs: StateFlow<List<Song>> = combine(_allSongs, hiddenIds) { all, hidden ->
        if (hidden.isEmpty()) all else all.filterNot { it.id in hidden }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hiddenSongs: StateFlow<List<Song>> = combine(_allSongs, hiddenIds) { all, hidden ->
        if (hidden.isEmpty()) emptyList() else all.filter { it.id in hidden }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val starredIds: StateFlow<Set<Long>> = _userState
        .map { it.starredSongIds.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, _userState.value.starredSongIds.toSet())

    val playlists: StateFlow<List<Playlist>> = _userState
        .map { buildPlaylistsWithStarred(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, buildPlaylistsWithStarred(_userState.value))

    private val _loaded = MutableStateFlow(false)
    /** False until the first library scan has finished. */
    val loaded: StateFlow<Boolean> = _loaded

    val query = MutableStateFlow("")

    val results: StateFlow<List<Song>> = combine(songs, query) { s, q -> s.search(q) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val albums: StateFlow<List<Album>> = songs.map { it.toAlbums() }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val artists: StateFlow<List<Artist>> = songs.map { it.toArtists() }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _playback = MutableStateFlow(PlaybackState())
    val playback: StateFlow<PlaybackState> = _playback

    val installedVersion: String = getAppVersion(app)
    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus
    private val updaterPrefs = app.getSharedPreferences(UPDATER_PREFS_NAME, Context.MODE_PRIVATE)
    private var startupUpdateChecked = false
    private var updateJob: Job? = null

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
        checkForUpdatesOnStartup()
    }

    fun loadLibrary(debounceMs: Long = 0) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            delay(debounceMs)
            ArtCache.clearMissing()
            val list = withContext(Dispatchers.IO) {
                runCatching { loadSongs(getApplication()) }.getOrDefault(emptyList())
            }
            withContext(Dispatchers.IO) {
                store.pruneSongs(list.map { it.id }.toSet())
            }
            songsById = list.associateBy { it.id }
            _allSongs.value = list
            refreshUserState()
            _loaded.value = true
            syncPlayback()
        }
    }

    private fun refreshUserState() {
        _userState.value = store.loadState()
    }

    fun createPlaylist(name: String): Playlist? =
        store.createPlaylist(name)?.also { refreshUserState() }

    fun renamePlaylist(playlistId: String, newName: String): Boolean =
        store.renamePlaylist(playlistId, newName).also { if (it) refreshUserState() }

    fun deletePlaylist(playlistId: String): Boolean =
        store.deletePlaylist(playlistId).also { if (it) refreshUserState() }

    fun addSongToPlaylist(playlistId: String, songId: Long): Boolean =
        store.addSongToPlaylist(playlistId, songId).also { if (it) refreshUserState() }

    fun addSongsToPlaylist(playlistId: String, songIds: List<Long>): Boolean =
        store.addSongsToPlaylist(playlistId, songIds).also { if (it) refreshUserState() }

    fun removeSongFromPlaylist(playlistId: String, index: Int): Boolean =
        store.removeSongFromPlaylist(playlistId, index).also { if (it) refreshUserState() }

    fun removeSongIdFromPlaylist(playlistId: String, songId: Long): Boolean =
        store.removeSongIdFromPlaylist(playlistId, songId).also { if (it) refreshUserState() }

    fun moveSongInPlaylist(
        playlistId: String,
        visibleSongIds: List<Long>,
        fromIndex: Int,
        toIndex: Int,
    ): Boolean {
        val state = _userState.value
        val rawSongIds = if (playlistId == PlaylistStore.STARRED_PLAYLIST_ID) {
            state.starredSongIds
        } else {
            state.playlists.firstOrNull { it.id == playlistId }?.songIds ?: return false
        }
        val (rawFrom, rawTo) = mapVisibleMoveToRawIndices(
            rawSongIds = rawSongIds,
            visibleSongIds = visibleSongIds,
            fromIndex = fromIndex,
            toIndex = toIndex,
        ) ?: return false
        return store.moveSongInPlaylist(playlistId, rawFrom, rawTo).also {
            if (it) refreshUserState()
        }
    }

    fun toggleStar(songId: Long): Boolean =
        store.toggleStar(songId).also { refreshUserState() }

    fun setStarred(songId: Long, starred: Boolean) {
        store.setStarred(songId, starred)
        refreshUserState()
    }

    fun hideSong(songId: Long) {
        store.hideSong(songId)
        refreshUserState()
        if (_playback.value.song?.id == songId) {
            removeSongFromQueue(songId)
        }
    }

    fun unhideSong(songId: Long) {
        store.unhideSong(songId)
        refreshUserState()
    }

    fun unhideAll() {
        store.unhideAll()
        refreshUserState()
    }

    suspend fun loadFileInfo(song: Song): SongFileInfo = withContext(Dispatchers.IO) {
        loadSongFileInfo(getApplication(), song)
    }

    suspend fun requestDeleteSong(song: Song): DeleteSongOutcome {
        val outcome = withContext(Dispatchers.IO) {
            deleteSongFromDevice(getApplication(), song)
        }
        if (outcome is DeleteSongOutcome.Deleted) {
            onSongDeleted(song.id)
        }
        return outcome
    }

    fun onSongDeleted(songId: Long) {
        store.removeDeletedSong(songId)
        refreshUserState()
        val remaining = _allSongs.value.filterNot { it.id == songId }
        songsById = remaining.associateBy { it.id }
        _allSongs.value = remaining
        removeSongFromQueue(songId)
        loadLibrary()
    }

    fun checkForUpdatesOnStartup() {
        if (startupUpdateChecked) return
        startupUpdateChecked = true
        launchUpdateCheck(isManual = false)
    }

    fun checkForUpdatesOnResume() {
        val nowMs = System.currentTimeMillis()
        val lastCheckedMs = updaterPrefs.getLong(KEY_LAST_UPDATE_CHECK_MS, 0L)
        if (!shouldCheckThrottled(nowMs, lastCheckedMs)) return
        launchUpdateCheck(isManual = false)
    }

    fun checkForUpdatesManually() {
        launchUpdateCheck(isManual = true)
    }

    fun openUpdateUrl(url: String): Boolean = com.musely.app.openUrl(getApplication(), url)

    private fun launchUpdateCheck(isManual: Boolean) {
        if (updateJob?.isActive == true && !isManual) return
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            if (isManual) {
                _updateStatus.value = UpdateStatus.Checking
            }
            val result = checkForUpdate(getApplication())
            if (result !is UpdateStatus.Error) {
                updaterPrefs.edit()
                    .putLong(KEY_LAST_UPDATE_CHECK_MS, System.currentTimeMillis())
                    .apply()
            }
            _updateStatus.value = resolveUpdateStatusAfterCheck(
                current = _updateStatus.value,
                result = result,
                isManual = isManual,
            )
        }
    }

    private fun removeSongFromQueue(songId: Long) {
        val c = controller ?: return
        val mediaIds = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }
        val indices = findMatchingQueueIndicesDescending(mediaIds, songId)
        if (indices.isEmpty()) return
        for (index in indices) {
            c.removeMediaItem(index)
        }
        syncPlayback()
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
                .setArtworkUri(uri)
                .build(),
        )
        .build()
}
