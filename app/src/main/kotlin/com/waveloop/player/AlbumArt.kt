package com.waveloop.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Small in-memory cover cache, capped at 1/12 of the app's heap. */
private object ArtCache {
    private val cache = object : LruCache<Long, Bitmap>((Runtime.getRuntime().maxMemory() / 12).toInt()) {
        override fun sizeOf(key: Long, value: Bitmap) = value.byteCount
    }
    private val missing = HashSet<Long>()

    fun key(albumId: Long, large: Boolean) = albumId * 2 + if (large) 1 else 0

    fun get(key: Long): Bitmap? = cache.get(key)

    fun isMissing(albumId: Long) = synchronized(missing) { albumId in missing }

    fun load(context: Context, albumId: Long, large: Boolean): Bitmap? {
        val px = if (large) 900 else 160
        val uri = albumArtUri(albumId)
        val bitmap = try {
            if (Build.VERSION.SDK_INT >= 29) {
                context.contentResolver.loadThumbnail(uri, Size(px, px), null)
            } else {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply {
                        inSampleSize = if (large) 1 else 4
                    })
                }
            }
        } catch (_: Exception) {
            null
        }
        if (bitmap == null) synchronized(missing) { missing += albumId }
        else cache.put(key(albumId, large), bitmap)
        return bitmap
    }
}

@Composable
fun AlbumArt(albumId: Long, modifier: Modifier = Modifier, large: Boolean = false, corner: Dp = 6.dp) {
    val context = LocalContext.current.applicationContext
    val key = ArtCache.key(albumId, large)
    val image by produceState<ImageBitmap?>(ArtCache.get(key)?.asImageBitmap(), key) {
        value = ArtCache.get(key)?.asImageBitmap()
        if (value == null && !ArtCache.isMissing(albumId)) {
            value = withContext(Dispatchers.IO) { ArtCache.load(context, albumId, large) }?.asImageBitmap()
        }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val art = image
        if (art != null) {
            Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(
                painterResource(R.drawable.ic_note), null,
                Modifier.fillMaxSize().padding(if (large) 96.dp else 12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
