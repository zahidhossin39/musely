package com.musely.app

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

internal data class ArtKey(
    val isSong: Boolean,
    val id: Long,
    val large: Boolean,
)

internal data class ArtSubject(
    val isSong: Boolean,
    val id: Long,
)

internal fun computeInSampleSize(srcWidth: Int, srcHeight: Int, targetPx: Int): Int {
    if (srcWidth <= 0 || srcHeight <= 0 || targetPx <= 0) return 1
    var sample = 1
    while (srcWidth / (sample * 2) >= targetPx && srcHeight / (sample * 2) >= targetPx) {
        sample *= 2
    }
    return sample
}

internal fun computeScaledDimensions(srcWidth: Int, srcHeight: Int, maxPx: Int): Pair<Int, Int>? {
    if (srcWidth <= 0 || srcHeight <= 0 || maxPx <= 0) return null
    val longest = maxOf(srcWidth, srcHeight)
    if (longest <= maxPx) return null
    val scale = maxPx.toFloat() / longest.toFloat()
    val dstWidth = (srcWidth * scale).toInt().coerceAtLeast(1)
    val dstHeight = (srcHeight * scale).toInt().coerceAtLeast(1)
    return dstWidth to dstHeight
}

internal fun Bitmap.downscaleIfNeeded(maxPx: Int): Bitmap {
    val (dstWidth, dstHeight) = computeScaledDimensions(width, height, maxPx) ?: return this
    return Bitmap.createScaledBitmap(this, dstWidth, dstHeight, true)
}

internal fun Bitmap.toSoftwareBitmap(): Bitmap {
    if (config == Bitmap.Config.HARDWARE) {
        val copied: Bitmap? = copy(Bitmap.Config.ARGB_8888, false)
        return copied ?: this
    }
    return this
}

internal fun decodeSampledBitmap(bytes: ByteArray, targetPx: Int): Bitmap? {
    if (bytes.isEmpty()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val opts = BitmapFactory.Options().apply {
        inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, targetPx)
    }
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
    return decoded.toSoftwareBitmap()
}

/** Small in-memory cover cache, capped at 1/12 of the app's heap (between 4 MB and 64 MB). */
object ArtCache {
    private val cache = object : LruCache<ArtKey, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 12L)
            .coerceIn(4L * 1024 * 1024, 64L * 1024 * 1024)
            .toInt(),
    ) {
        override fun sizeOf(key: ArtKey, value: Bitmap) = value.byteCount
    }
    private val missing = HashSet<ArtSubject>()
    private val _clearVersion = MutableStateFlow(0)
    internal val clearVersion: StateFlow<Int> = _clearVersion

    internal fun key(albumId: Long, songId: Long?, large: Boolean): ArtKey =
        if (songId != null) ArtKey(isSong = true, id = songId, large = large)
        else ArtKey(isSong = false, id = albumId, large = large)

    internal fun subject(albumId: Long, songId: Long?): ArtSubject =
        if (songId != null) ArtSubject(isSong = true, id = songId)
        else ArtSubject(isSong = false, id = albumId)

    internal fun get(key: ArtKey): Bitmap? = cache.get(key)

    internal fun isMissing(subject: ArtSubject): Boolean = synchronized(missing) { subject in missing }

    /** Clears negative-lookup cache so newly indexed or updated audio covers can be retried. */
    fun clearMissing() {
        synchronized(missing) {
            missing.clear()
            _clearVersion.value += 1
        }
    }

    internal fun load(
        context: Context,
        key: ArtKey,
        subject: ArtSubject,
        albumId: Long,
        songId: Long?,
        fallbackSongIds: List<Long>,
        large: Boolean,
    ): Bitmap? {
        cache.get(key)?.let { return it }
        val px = if (large) 900 else 256
        val bitmap = if (songId != null) {
            loadForSong(context, songId, albumId, px)
        } else {
            loadForAlbum(context, albumId, fallbackSongIds, px)
        }
        if (bitmap == null) {
            synchronized(missing) { missing += subject }
        } else {
            synchronized(missing) { missing -= subject }
            cache.put(key, bitmap)
        }
        return bitmap
    }

    /**
     * Resolves cover art for [PlaybackService] notifications and MediaSession metadata.
     * Supports MediaStore audio track URIs, video track URIs, album URIs, legacy albumart URIs, and generic streams.
     */
    fun loadFromUri(context: Context, uri: Uri, targetPx: Int = 384): Bitmap? {
        val uriText = uri.toString()
        val mediaPrefix = "${MediaStore.Audio.Media.EXTERNAL_CONTENT_URI}/"
        val videoPrefix = "${MediaStore.Video.Media.EXTERNAL_CONTENT_URI}/"
        val albumPrefix = "${MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI}/"
        val legacyPrefix = "content://media/external/audio/albumart/"

        val loaded = when {
            uriText.startsWith(mediaPrefix) -> {
                val songId = runCatching { ContentUris.parseId(uri) }.getOrNull()
                if (songId != null && songId >= 0L) {
                    cache.get(ArtKey(isSong = true, id = songId, large = true))
                        ?: loadForSong(context, songId, queryAlbumId(context, uri), targetPx)
                } else {
                    loadThumbnail(context, uri, targetPx) ?: loadEmbeddedArt(context, uri, targetPx)
                }
            }
            uriText.startsWith(videoPrefix) -> {
                val videoId = runCatching { ContentUris.parseId(uri) }.getOrNull()
                if (videoId != null && videoId >= 0L) {
                    val trackId = videoTrackId(videoId)
                    cache.get(ArtKey(isSong = true, id = trackId, large = true))
                        ?: loadForVideo(context, videoId, targetPx)
                } else {
                    loadThumbnail(context, uri, targetPx)
                        ?: loadEmbeddedArt(context, uri, targetPx)
                        ?: loadVideoFrame(context, uri, targetPx)
                }
            }
            uriText.startsWith(albumPrefix) || uriText.startsWith(legacyPrefix) -> {
                val albumId = runCatching { ContentUris.parseId(uri) }.getOrNull()
                if (albumId != null && albumId >= 0L) {
                    loadForAlbum(context, albumId, emptyList(), targetPx)
                } else {
                    loadThumbnail(context, uri, targetPx) ?: decodeSampledStream(context, uri, targetPx)
                }
            }
            else -> loadThumbnail(context, uri, targetPx) ?: decodeSampledStream(context, uri, targetPx)
        }
        return loaded?.toSoftwareBitmap()?.downscaleIfNeeded(targetPx)
    }

    private fun loadForSong(context: Context, songId: Long, albumId: Long?, targetPx: Int): Bitmap? {
        if (isVideoTrackId(songId)) {
            return loadForVideo(context, mediaStoreIdFromTrackId(songId), targetPx)
        }
        val sUri = songUri(songId)
        return loadThumbnail(context, sUri, targetPx)
            ?: loadEmbeddedArt(context, sUri, targetPx)
            ?: albumId?.let { loadAlbumSources(context, it, targetPx) }
    }

    private fun loadForVideo(context: Context, videoId: Long, targetPx: Int): Bitmap? {
        val vUri = videoUri(videoId)
        return loadThumbnail(context, vUri, targetPx)
            ?: loadEmbeddedArt(context, vUri, targetPx)
            ?: loadVideoFrame(context, vUri, targetPx)
    }

    private fun loadForAlbum(
        context: Context,
        albumId: Long,
        fallbackSongIds: List<Long>,
        targetPx: Int,
    ): Bitmap? {
        loadAlbumSources(context, albumId, targetPx)?.let { return it }
        for (sid in fallbackSongIds.take(12)) {
            val sUri = songUri(sid)
            val art = loadThumbnail(context, sUri, targetPx) ?: loadEmbeddedArt(context, sUri, targetPx)
            if (art != null) return art
        }
        return null
    }

    private fun loadAlbumSources(context: Context, albumId: Long, targetPx: Int): Bitmap? {
        if (albumId < 0L) return null
        val officialUri = albumUri(albumId)
        loadThumbnail(context, officialUri, targetPx)?.let { return it }
        val legacyUri = albumArtUri(albumId)
        return loadThumbnail(context, legacyUri, targetPx)
            ?: decodeSampledStream(context, legacyUri, targetPx)
    }

    private fun loadThumbnail(context: Context, uri: Uri, targetPx: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < 29) return null
        return try {
            context.contentResolver.loadThumbnail(uri, Size(targetPx, targetPx), null).toSoftwareBitmap()
        } catch (_: Throwable) {
            null
        }
    }

    private fun setRetrieverDataSource(context: Context, retriever: MediaMetadataRetriever, uri: Uri): Boolean =
        try {
            retriever.setDataSource(context, uri)
            true
        } catch (_: Throwable) {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    retriever.setDataSource(pfd.fileDescriptor)
                    true
                } ?: false
            } catch (_: Throwable) {
                false
            }
        }

    private fun loadEmbeddedArt(context: Context, songUri: Uri, targetPx: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        try {
            if (!setRetrieverDataSource(context, retriever, songUri)) return null
            val bytes = retriever.embeddedPicture ?: return null
            return decodeSampledBitmap(bytes, targetPx)
        } catch (_: Throwable) {
            return null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun loadVideoFrame(context: Context, videoUri: Uri, targetPx: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        try {
            if (!setRetrieverDataSource(context, retriever, videoUri)) return null
            val px = targetPx.coerceAtLeast(1)
            val raw = if (Build.VERSION.SDK_INT >= 27) {
                runCatching {
                    retriever.getScaledFrameAtTime(-1L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, px, px)
                }.getOrNull() ?: retriever.frameAtTime
            } else {
                retriever.frameAtTime
            } ?: return null
            return raw.toSoftwareBitmap().downscaleIfNeeded(px)
        } catch (_: Throwable) {
            return null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decodeSampledStream(context: Context, uri: Uri, targetPx: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            } ?: return null
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val opts = BitmapFactory.Options().apply {
                inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, targetPx)
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, opts)
            }?.toSoftwareBitmap()
        } catch (_: Throwable) {
            null
        }
    }

    private fun queryAlbumId(context: Context, songUri: Uri): Long? =
        try {
            context.contentResolver.query(
                songUri,
                arrayOf(MediaStore.Audio.Media.ALBUM_ID),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            }
        } catch (_: Throwable) {
            null
        }
}

@Composable
fun AlbumArt(
    albumId: Long,
    modifier: Modifier = Modifier,
    songId: Long? = null,
    fallbackSongIds: List<Long> = emptyList(),
    large: Boolean = false,
    corner: Dp = 12.dp,
    isVideo: Boolean = songId != null && isVideoTrackId(songId),
) {
    val context = LocalContext.current.applicationContext
    val key = ArtCache.key(albumId, songId, large)
    val subject = ArtCache.subject(albumId, songId)
    val clearVersion by ArtCache.clearVersion.collectAsState()
    var image by remember(key) { mutableStateOf(ArtCache.get(key)?.asImageBitmap()) }

    LaunchedEffect(key, fallbackSongIds, clearVersion) {
        if (image == null && !ArtCache.isMissing(subject)) {
            image = withContext(Dispatchers.IO) {
                ArtCache.load(context, key, subject, albumId, songId, fallbackSongIds, large)
            }?.asImageBitmap()
        }
    }

    val colors = MaterialTheme.colorScheme
    // Songs without artwork get a soft brand-tinted tile instead of a flat grey box.
    val placeholder = Brush.linearGradient(
        listOf(colors.primary.copy(alpha = 0.30f), colors.primary.copy(alpha = 0.08f)),
    )
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(colors.surfaceContainerHigh)
            .background(placeholder),
        contentAlignment = Alignment.Center,
    ) {
        val art = image
        if (art != null) {
            Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(
                painterResource(if (isVideo) R.drawable.ic_video else R.drawable.ic_note),
                null,
                Modifier.fillMaxSize(0.4f),
                tint = colors.primary.copy(alpha = 0.85f),
            )
        }
    }
}
