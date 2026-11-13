package com.musely.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import java.io.FileNotFoundException
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/** Owns the player so music keeps going when the app's screen is closed. */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val artworkExecutor: ListeningExecutorService =
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    override fun onCreate() {
        super.onCreate()
        // Audio renderer only: lets the build strip the video and text pipelines.
        val renderers = RenderersFactory { handler, _, audioListener, _, _ ->
            arrayOf(MediaCodecAudioRenderer(this, MediaCodecSelector.DEFAULT, handler, audioListener))
        }
        val player = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(SessionCallback())
            .setBitmapLoader(CoverBitmapLoader(applicationContext, artworkExecutor))
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        artworkExecutor.shutdownNow()
        super.onDestroy()
    }

    private class CoverBitmapLoader(
        private val context: Context,
        private val executor: ListeningExecutorService,
    ) : BitmapLoader {

        override fun supportsMimeType(mimeType: String): Boolean =
            mimeType.startsWith("image/", ignoreCase = true)

        override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
            executor.submit(Callable {
                decodeSampledBitmap(data, NOTIFICATION_ART_PX)
                    ?.downscaleIfNeeded(NOTIFICATION_ART_PX)
                    ?: throw IllegalArgumentException("Could not decode cover image bytes")
            })

        override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
            executor.submit(Callable {
                ArtCache.loadFromUri(context, uri, NOTIFICATION_ART_PX)
                    ?: throw FileNotFoundException("No cover art available for $uri")
            })

        companion object {
            private const val NOTIFICATION_ART_PX = 384
        }
    }

    private class SessionCallback : MediaSession.Callback {
        /** Controllers send only the track id; the playable audio or video URI is rebuilt here. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> = Futures.immediateFuture(
            mediaItems.map { item ->
                val id = item.mediaId.toLongOrNull() ?: return@map item
                item.buildUpon()
                    .setUri(trackUri(id))
                    .build()
            },
        )
    }
}
