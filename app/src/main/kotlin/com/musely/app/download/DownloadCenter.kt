package com.musely.app.download

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.musely.app.PlaylistStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-wide download queue. Runs downloads two at a time on background threads, keeps their
 * live state for the UI and the foreground service, and remembers finished ones across restarts.
 */
object DownloadCenter {

    private const val TAG = "MuselyDownloads"
    private const val PARALLEL_DOWNLOADS = 2
    private const val HISTORY_FILE = "downloads.json"
    private const val WORK_DIR = "downloads"
    private const val MAX_HISTORY = 300
    private const val PROGRESS_INTERVAL_MS = 250L
    private const val RECENT_SAVE_WINDOW_MS = 10L * 60 * 1000
    private const val COMMENT = "Downloaded with Musely"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<String>(Channel.UNLIMITED)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val recentlySaved = ConcurrentHashMap<Long, Long>()
    private val emittedBatches: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val persistLock = Mutex()

    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())
    /** Every known download, newest first. */
    val items: StateFlow<List<DownloadItem>> = _items

    private val _events = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<DownloadEvent> = _events

    @Volatile private var appContext: Context? = null
    private lateinit var playlistStore: PlaylistStore

    fun init(context: Context) {
        if (appContext != null) return
        synchronized(this) {
            if (appContext != null) return
            val app = context.applicationContext
            playlistStore = PlaylistStore.shared(app)
            val history = runCatching { File(app.filesDir, HISTORY_FILE).readText() }
                .map(DownloadHistoryCodec::decode)
                .getOrDefault(emptyList())
            _items.value = history
            appContext = app
            repeat(PARALLEL_DOWNLOADS) { scope.launch { for (id in queue) runQueued(id) } }
        }
    }

    private fun context(): Context = checkNotNull(appContext) { "DownloadCenter.init() must be called first" }

    /** New download id; also names the temp folder, so it must stay a plain UUID. */
    fun newId(): String = UUID.randomUUID().toString()

    fun enqueue(requests: List<DownloadRequest>) {
        if (requests.isEmpty()) return
        val now = System.currentTimeMillis()
        val added = requests.map { DownloadItem(it, DownloadState.Queued, now) }
        val addedIds = requests.map { it.id }.toSet()
        requests.mapNotNull { it.batchId }.forEach(emittedBatches::remove)
        _items.update { current -> added + current.filterNot { it.request.id in addedIds } }
        persist()
        requests.forEach { queue.trySend(it.id) }
        DownloadService.start(context())
    }

    fun cancel(id: String) {
        setState(id) { if (it.isActive) DownloadState.Canceled else it }
        jobs[id]?.cancel()
        persist()
    }

    fun cancelAll() {
        _items.value.filter { it.state.isActive }.forEach { cancel(it.request.id) }
    }

    fun retry(id: String) {
        val item = _items.value.firstOrNull { it.request.id == id } ?: return
        if (item.state.isActive || item.state is DownloadState.Completed) return
        item.request.batchId?.let(emittedBatches::remove)
        setState(id) { DownloadState.Queued }
        persist()
        queue.trySend(id)
        DownloadService.start(context())
    }

    fun remove(id: String) {
        jobs[id]?.cancel()
        _items.update { list -> list.filterNot { it.request.id == id } }
        persist()
    }

    fun clearFinished() {
        _items.update { list -> list.filter { it.state.isActive } }
        persist()
    }

    /**
     * Tracks saved in the last few minutes. A library rescan that started before a save finished
     * must not prune these from playlists just because its snapshot didn't include them yet.
     */
    fun recentlySavedTrackIds(): Set<Long> {
        val cutoff = SystemClock.elapsedRealtime() - RECENT_SAVE_WINDOW_MS
        recentlySaved.entries.removeIf { it.value < cutoff }
        return recentlySaved.keys.toSet()
    }

    private suspend fun runQueued(id: String) {
        // A canceled attempt can still be unwinding from a blocking network call. Let it finish so
        // its cleanup never touches the new attempt's files or state.
        jobs[id]?.join()
        if (!claim(id)) return
        val request = _items.value.firstOrNull { it.request.id == id }?.request ?: return
        val job = scope.launch { process(request) }
        jobs[id] = job
        job.join()
        jobs.remove(id, job)
    }

    /** Atomically moves [id] from Queued to Preparing. False if it was canceled or another worker won. */
    private fun claim(id: String): Boolean {
        var claimed = false
        _items.update { list ->
            claimed = false
            list.map { item ->
                if (item.request.id == id && item.state == DownloadState.Queued) {
                    claimed = true
                    item.copy(state = DownloadState.Preparing)
                } else {
                    item
                }
            }
        }
        return claimed
    }

    private suspend fun process(request: DownloadRequest) {
        val id = request.id
        val workDir = workDirFor(id) ?: run {
            setState(id) { DownloadState.Failed("This download can't be retried. Remove it and download again.") }
            return
        }
        workDir.deleteRecursively()
        workDir.mkdirs()
        try {
            val videoUrl = when (val source = request.source) {
                is DownloadSource.YouTube -> source.url
                is DownloadSource.SpotifyMatch -> MediaResolver.matchOnYouTubeMusic(source.track)
            }
            ensureStillWanted(id)
            val progress = ProgressReporter(id)
            val prepared = when (request.format) {
                DownloadFormat.Audio -> MediaFetcher.fetchAudio(videoUrl, workDir, progress::report)
                DownloadFormat.Video -> MediaFetcher.fetchVideo(videoUrl, request.quality, workDir, progress::report)
            }
            ensureStillWanted(id)
            setState(id) { if (it.isRunning) DownloadState.Processing else it }
            val saved = finalize(request, prepared, workDir)
            request.playlistId?.let { playlistStore.addSongToPlaylist(it, saved.trackId) }
            // Once the file is in the library, report it honestly even if cancel arrived meanwhile.
            setState(id) { DownloadState.Completed(saved.trackId, saved.sizeBytes, System.currentTimeMillis()) }
            _items.value.firstOrNull { it.request.id == id }?.let { _events.tryEmit(DownloadEvent.Completed(it, saved.trackId)) }
        } catch (e: CancellationException) {
            // Queued means the user already retried; leave that request alone.
            setState(id) { if (it.isRunning) DownloadState.Canceled else it }
            throw e
        } catch (e: ResolveException) {
            setState(id) { DownloadState.Failed(e.message ?: "Download failed.") }
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Download $id ran out of memory", e)
            setState(id) { DownloadState.Failed("Not enough memory to finish this download.") }
        } catch (e: Exception) {
            Log.w(TAG, "Download $id failed", e)
            setState(id) { DownloadState.Failed(MediaResolver.userMessage(e)) }
        } finally {
            workDir.deleteRecursively()
            persist()
            emitBatchIfFinished(request)
        }
    }

    /** Temp folder for a download, or null if the id could escape the downloads cache folder. */
    private fun workDirFor(id: String): File? {
        if (!DownloadHistoryCodec.isValidId(id)) return null
        val root = File(context().cacheDir, WORK_DIR)
        val dir = File(root, id)
        return dir.takeIf { it.canonicalFile.parentFile == root.canonicalFile }
    }

    /** Stops work that was canceled even if its job reference was never registered. */
    private suspend fun ensureStillWanted(id: String) {
        currentCoroutineContext().ensureActive()
        if (stateOf(id)?.isRunning != true) throw CancellationException("Download $id was canceled")
    }

    private suspend fun finalize(request: DownloadRequest, prepared: PreparedMedia, workDir: File): SavedMedia {
        val tags = TrackTags(request.title, request.artist, request.album ?: request.title)
        val file = if (prepared.taggable) {
            val cover = runCatching { CoverArt.fetchJpeg(request.thumbnailUrl ?: prepared.sourceThumbnailUrl, squareCrop = !prepared.isVideo) }
                .getOrNull()
            val tagged = File(workDir, "tagged.${prepared.extension}")
            val ok = runCatching {
                Mp4Tagger.tag(prepared.file, tagged, Mp4Tags(tags.title, tags.artist, tags.album, COMMENT, cover))
            }.getOrDefault(false)
            if (ok) tagged else prepared.file
        } else {
            prepared.file
        }
        ensureStillWanted(request.id)
        return try {
            MediaStoreSaver.save(
                context = context(),
                file = file,
                displayName = safeFileName(tags, prepared.extension),
                mimeType = prepared.mimeType,
                isVideo = prepared.isVideo,
                onPublishing = { trackId -> recentlySaved[trackId] = SystemClock.elapsedRealtime() },
            )
        } catch (e: IOException) {
            throw ResolveException(e.message ?: "Couldn't save to your media library.", e)
        } catch (e: SecurityException) {
            throw ResolveException("Musely isn't allowed to save to your media library.", e)
        }
    }

    private fun emitBatchIfFinished(request: DownloadRequest) {
        val batchId = request.batchId ?: return
        val batch = _items.value.filter { it.request.batchId == batchId }
        if (batch.isEmpty() || batch.any { it.state.isActive }) return
        // Both workers can finish the last two items at once; announce the batch only once.
        if (!emittedBatches.add(batchId)) return
        _events.tryEmit(
            DownloadEvent.BatchFinished(
                batchId = batchId,
                completed = batch.count { it.state is DownloadState.Completed },
                failed = batch.count { it.state is DownloadState.Failed },
                playlistId = request.playlistId,
            ),
        )
    }

    private fun stateOf(id: String): DownloadState? = _items.value.firstOrNull { it.request.id == id }?.state

    private fun setState(id: String, transform: (DownloadState) -> DownloadState) {
        _items.update { list ->
            list.map { item -> if (item.request.id == id) item.copy(state = transform(item.state)) else item }
        }
    }

    private fun persist() {
        val app = appContext ?: return
        scope.launch {
            persistLock.withLock {
                // Read inside the lock so the last write always holds the newest state.
                val trimmed = trimHistory(_items.value)
                val target = File(app.filesDir, HISTORY_FILE)
                val temp = File(app.filesDir, "$HISTORY_FILE.tmp")
                runCatching {
                    temp.writeText(DownloadHistoryCodec.encode(trimmed))
                    if (!temp.renameTo(target)) {
                        temp.copyTo(target, overwrite = true)
                        temp.delete()
                    }
                }.onFailure { Log.w(TAG, "Couldn't save download history", it) }
            }
        }
    }

    /** Keeps every active download and the newest finished ones. */
    internal fun trimHistory(items: List<DownloadItem>, max: Int = MAX_HISTORY): List<DownloadItem> {
        if (items.size <= max) return items
        val active = items.filter { it.state.isActive }
        val finished = items.filterNot { it.state.isActive }.take((max - active.size).coerceAtLeast(0))
        val keep = (active + finished).map { it.request.id }.toSet()
        return items.filter { it.request.id in keep }
    }

    /** True while a worker is actually processing; Queued means waiting (or retried). */
    private val DownloadState.isRunning: Boolean
        get() = this == DownloadState.Preparing || this is DownloadState.Downloading || this == DownloadState.Processing

    /** Coalesces byte counts from every stream of one download into throttled state updates. */
    private class ProgressReporter(private val id: String) {
        private val parts = HashMap<String, Pair<Long, Long>>()
        private var lastUpdate = 0L

        @Synchronized
        fun report(part: String, downloaded: Long, total: Long) {
            parts[part] = downloaded to total
            val now = SystemClock.elapsedRealtime()
            val finished = total in 1..downloaded
            if (!finished && now - lastUpdate < PROGRESS_INTERVAL_MS) return
            lastUpdate = now
            val bytes = parts.values.sumOf { it.first }
            val totals = parts.values.map { it.second }
            val sum = if (totals.all { it > 0 }) totals.sum() else -1L
            setState(id) { state ->
                if (state == DownloadState.Preparing || state is DownloadState.Downloading) {
                    DownloadState.Downloading(bytes, sum)
                } else {
                    state
                }
            }
        }
    }
}
