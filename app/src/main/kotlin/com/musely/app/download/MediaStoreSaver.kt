package com.musely.app.download

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.graphics.scale
import com.musely.app.videoTrackId
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.math.min

/** A file published to the shared media collection, identified the way the library identifies tracks. */
data class SavedMedia(val trackId: Long, val uri: Uri, val sizeBytes: Long)

/** Publishes finished downloads to `Music/Musely` or `Movies/Musely` so every player can see them. */
internal object MediaStoreSaver {

    const val FOLDER_NAME = "Musely"

    /** [onPublishing] receives the track id just before the file becomes visible to the library. */
    suspend fun save(
        context: Context,
        file: File,
        displayName: String,
        mimeType: String,
        isVideo: Boolean,
        onPublishing: (trackId: Long) -> Unit = {},
    ): SavedMedia =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveScoped(context, file, displayName, mimeType, isVideo, onPublishing)
        } else {
            saveLegacy(context, file, displayName, mimeType, isVideo, onPublishing)
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveScoped(
        context: Context,
        file: File,
        displayName: String,
        mimeType: String,
        isVideo: Boolean,
        onPublishing: (Long) -> Unit,
    ): SavedMedia {
        val resolver = context.contentResolver
        val collection = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val directory = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_MUSIC
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$directory/$FOLDER_NAME")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("Couldn't create the file in your media library")
        val trackId = trackIdFor(ContentUris.parseId(uri), isVideo)
        try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: throw IOException("Couldn't write to your media library")
            onPublishing(trackId)
            // Clearing IS_PENDING publishes the file and triggers the media scan that reads its tags.
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return SavedMedia(trackId, uri, file.length())
    }

    /** Android 8–9: write into the public folder (needs WRITE_EXTERNAL_STORAGE) and scan it. */
    @Suppress("DEPRECATION")
    private suspend fun saveLegacy(
        context: Context,
        file: File,
        displayName: String,
        mimeType: String,
        isVideo: Boolean,
        onPublishing: (Long) -> Unit,
    ): SavedMedia {
        val root = Environment.getExternalStoragePublicDirectory(
            if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_MUSIC,
        )
        val folder = File(root, FOLDER_NAME).apply { mkdirs() }
        val target = uniqueFile(folder, displayName)
        val uri = try {
            file.copyTo(target)
            suspendCancellableCoroutine<Uri?> { continuation ->
                MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mimeType)) { _, scanned ->
                    continuation.resume(scanned)
                }
            } ?: throw IOException("Your media library didn't pick up the file")
        } catch (e: Throwable) {
            // Includes cancellation: never leave a half-copied file in the public Music folder.
            target.delete()
            throw e
        }
        val trackId = trackIdFor(ContentUris.parseId(uri), isVideo)
        onPublishing(trackId)
        return SavedMedia(trackId, uri, target.length())
    }

    private fun trackIdFor(mediaStoreId: Long, isVideo: Boolean): Long =
        if (isVideo) videoTrackId(mediaStoreId) else mediaStoreId

    private fun uniqueFile(folder: File, displayName: String): File {
        val base = displayName.substringBeforeLast('.')
        val extension = displayName.substringAfterLast('.', "")
        var candidate = File(folder, displayName)
        var counter = 1
        while (candidate.exists()) {
            candidate = File(folder, "$base ($counter).$extension")
            counter++
        }
        return candidate
    }
}

/** Turns a downloaded thumbnail into JPEG cover art: square for songs, as-is for videos. */
internal object CoverArt {

    private const val MAX_EDGE_PX = 720
    private const val JPEG_QUALITY = 90
    private const val MAX_THUMBNAIL_BYTES = 4 * 1024 * 1024

    fun fetchJpeg(url: String?, squareCrop: Boolean): ByteArray? {
        if (url.isNullOrBlank()) return null
        val bytes = Http.getBytes(url, MAX_THUMBNAIL_BYTES) ?: return null
        val source = BoundedBitmap.decode(bytes, MAX_EDGE_PX) ?: return null
        val cropped = if (squareCrop) centerSquare(source) else source
        val scale = min(1f, MAX_EDGE_PX.toFloat() / maxOf(cropped.width, cropped.height))
        val scaled = if (scale < 1f) {
            cropped.scale((cropped.width * scale).toInt(), (cropped.height * scale).toInt())
        } else {
            cropped
        }
        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
    }

    private fun centerSquare(bitmap: Bitmap): Bitmap {
        val edge = min(bitmap.width, bitmap.height)
        if (bitmap.width == bitmap.height) return bitmap
        return Bitmap.createBitmap(bitmap, (bitmap.width - edge) / 2, (bitmap.height - edge) / 2, edge, edge)
    }
}
