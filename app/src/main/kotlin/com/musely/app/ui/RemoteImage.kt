package com.musely.app.ui

import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musely.app.R
import com.musely.app.download.BoundedBitmap
import com.musely.app.download.HostPolicy
import com.musely.app.download.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Small in-memory cache of downloaded thumbnails, sized for list rows and preview cards. */
private object RemoteImageCache {
    private const val MAX_BYTES = 8 * 1024 * 1024
    private const val TARGET_EDGE_PX = 360
    private const val MAX_DOWNLOAD_BYTES = 3 * 1024 * 1024

    private val cache = object : LruCache<String, ImageBitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun cached(url: String): ImageBitmap? = cache.get(url)

    suspend fun load(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        cache.get(url)?.let { return@withContext it }
        // Thumbnail URLs come from link metadata: only YouTube/Spotify image CDNs, size-capped.
        val bytes = Http.getBytes(url, MAX_DOWNLOAD_BYTES, HostPolicy.Media) ?: return@withContext null
        val bitmap = BoundedBitmap.decode(bytes, TARGET_EDGE_PX) ?: return@withContext null
        bitmap.asImageBitmap().also { cache.put(url, it) }
    }
}

/** A rounded thumbnail loaded from [url], with a music-note placeholder while loading or on failure. */
@Composable
internal fun RemoteImage(
    url: String?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    corner: Dp = 12.dp,
) {
    val image by produceState(initialValue = url?.let(RemoteImageCache::cached), url) {
        value = url?.let { RemoteImageCache.load(it) }
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        val loaded = image
        if (loaded != null) {
            Image(loaded, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            Icon(
                painterResource(R.drawable.ic_note),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size / 2.5f),
            )
        }
    }
}
