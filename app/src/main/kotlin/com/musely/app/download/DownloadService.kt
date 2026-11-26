package com.musely.app.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.musely.app.MainActivity
import com.musely.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps downloads running while Musely is in the background and shows their progress. Stops
 * itself as soon as nothing is queued or downloading.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null
    private var lastStartId = 0
    private var completedThisRun = 0
    private var failedThisRun = 0
    private val seenFinished = HashSet<String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        DownloadCenter.init(this)
        createChannel(this)
        DownloadCenter.items.value.filterNot { it.state.isActive }.forEach { seenFinished += it.request.id }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        if (intent?.action == ACTION_CANCEL_ALL) DownloadCenter.cancelAll()
        // Must be called for every startForegroundService() call, not just the first.
        ServiceCompat.startForeground(
            this,
            PROGRESS_NOTIFICATION_ID,
            buildProgress(DownloadCenter.items.value),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        if (observer == null) observer = scope.launch { observe() }
        return START_NOT_STICKY
    }

    private suspend fun observe() {
        DownloadCenter.items
            .map { items -> items.filter { it.state.isActive || it.request.id !in seenFinished } }
            .distinctUntilChanged()
            .collect { relevant ->
                relevant.filterNot { it.state.isActive }.forEach { item ->
                    if (seenFinished.add(item.request.id)) {
                        when (item.state) {
                            is DownloadState.Completed -> completedThisRun++
                            is DownloadState.Failed -> failedThisRun++
                            else -> Unit
                        }
                    }
                }
                val all = DownloadCenter.items.value
                if (all.none { it.state.isActive }) {
                    finish()
                } else {
                    notify(PROGRESS_NOTIFICATION_ID, buildProgress(all))
                }
            }
    }

    private fun finish() {
        // A new download may have been queued while this ran; keep going if so.
        if (DownloadCenter.items.value.any { it.state.isActive }) return
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (completedThisRun > 0 || failedThisRun > 0) {
            notify(SUMMARY_NOTIFICATION_ID, buildSummary(completedThisRun, failedThisRun))
        }
        completedThisRun = 0
        failedThisRun = 0
        // Only stops if no newer start request arrived; a pending start keeps the service alive.
        stopSelf(lastStartId)
    }

    /** Android 15 limits dataSync services to six hours a day; stop cleanly instead of crashing. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        DownloadCenter.cancelAll()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildProgress(items: List<DownloadItem>): Notification {
        val active = items.filter { it.state.isActive }
        val current = active.firstOrNull { it.state is DownloadState.Downloading || it.state == DownloadState.Processing }
            ?: active.firstOrNull()
        val percent = (current?.state as? DownloadState.Downloading)?.fraction?.let { (it * 100).toInt() }
        val title = when (active.size) {
            0 -> "Finishing downloads"
            1 -> "Downloading 1 item"
            else -> "Downloading ${active.size} items"
        }
        return baseBuilder()
            .setContentTitle(title)
            .setContentText(current?.request?.title.orEmpty())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent ?: 0, percent == null)
            .addAction(0, "Cancel all", cancelAllIntent())
            .build()
    }

    private fun buildSummary(completed: Int, failed: Int): Notification {
        val title = when {
            failed == 0 -> if (completed == 1) "Download complete" else "$completed downloads complete"
            completed == 0 -> if (failed == 1) "Download failed" else "$failed downloads failed"
            else -> "$completed downloaded, $failed failed"
        }
        return baseBuilder()
            .setContentTitle(title)
            .setContentText("Tap to open your downloads")
            .setAutoCancel(true)
            .build()
    }

    private fun baseBuilder(): NotificationCompat.Builder =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentIntent(openDownloadsIntent(this))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            // Track titles stay off the lock screen; the public version shows only that Musely is busy.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_download)
                    .setContentTitle("Musely downloads")
                    .build(),
            )

    private fun notify(id: Int, notification: Notification) {
        val manager = NotificationManagerCompat.from(this)
        if (manager.areNotificationsEnabled()) {
            runCatching { manager.notify(id, notification) }
        }
    }

    private fun cancelAllIntent(): PendingIntent = PendingIntent.getService(
        this,
        1,
        Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL_ALL),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val PROGRESS_NOTIFICATION_ID = 41
        private const val SUMMARY_NOTIFICATION_ID = 42
        private const val ACTION_CANCEL_ALL = "com.musely.app.download.CANCEL_ALL"
        const val EXTRA_OPEN_DOWNLOADS = "com.musely.app.extra.OPEN_DOWNLOADS"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
        }

        private fun openDownloadsIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            2,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_DOWNLOADS, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progress of songs and videos being downloaded"
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}
