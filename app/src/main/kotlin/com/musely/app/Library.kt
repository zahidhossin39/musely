package com.musely.app

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val track: Int,
    val isVideo: Boolean = false,
) {
    val mediaStoreId: Long get() = mediaStoreIdFromTrackId(id)

    val uri: Uri get() = if (isVideo) videoUri(mediaStoreId) else songUri(mediaStoreId)

    /** Lower-cased text that search matches against. */
    val searchKey: String = if (isVideo) {
        "$title\n$artist\n$album\nvideo".lowercase()
    } else {
        "$title\n$artist\n$album".lowercase()
    }
}

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val songCount: Int,
    val songIds: List<Long> = emptyList(),
)

data class Artist(val name: String, val songCount: Int)

fun isVideoTrackId(trackId: Long): Boolean = trackId < 0L

/**
 * Encodes a non-negative `MediaStore.Video.Media._ID` into a negative track ID so audio and
 * video items never collide in collections, caches, or Compose lazy lists.
 */
fun videoTrackId(mediaStoreVideoId: Long): Long {
    require(mediaStoreVideoId >= 0L) { "mediaStoreVideoId must be non-negative: $mediaStoreVideoId" }
    return -(mediaStoreVideoId + 1L)
}

fun mediaStoreIdFromTrackId(trackId: Long): Long =
    if (trackId < 0L) -(trackId + 1L) else trackId

fun songUri(songId: Long): Uri =
    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)

fun videoUri(videoId: Long): Uri =
    ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videoId)

fun trackUri(trackId: Long): Uri =
    if (isVideoTrackId(trackId)) videoUri(mediaStoreIdFromTrackId(trackId)) else songUri(trackId)

fun albumUri(albumId: Long): Uri =
    ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId)

fun albumArtUri(albumId: Long): Uri =
    ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

fun audioPermission(): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

fun videoPermission(): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
    else Manifest.permission.READ_EXTERNAL_STORAGE

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

fun hasAudioAccess(context: Context): Boolean =
    hasPermission(context, audioPermission())

fun hasFullVideoAccess(context: Context): Boolean =
    hasPermission(context, videoPermission())

fun hasPartialVideoAccess(context: Context): Boolean =
    Build.VERSION.SDK_INT >= 34 &&
        !hasPermission(context, Manifest.permission.READ_MEDIA_VIDEO) &&
        hasPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)

fun hasVideoAccess(context: Context): Boolean =
    hasFullVideoAccess(context) || hasPartialVideoAccess(context)

internal fun resolveVideoTitle(rawTitle: String?, displayName: String?): String {
    rawTitle?.trim()?.takeIf { it.isNotEmpty() && !it.equals(MediaStore.UNKNOWN_STRING, ignoreCase = true) }?.let { return it }
    val trimmedDisplay = displayName?.trim()?.takeIf { it.isNotEmpty() } ?: return "Unknown video"
    val withoutExt = trimmedDisplay.substringBeforeLast('.').trim()
    return withoutExt.ifEmpty { trimmedDisplay }
}

internal fun resolveVideoArtist(rawArtist: String?, bucketDisplayName: String?): String {
    val artist = rawArtist?.trim()
    if (!artist.isNullOrEmpty() && artist != MediaStore.UNKNOWN_STRING) return artist
    val bucket = bucketDisplayName?.trim()
    if (!bucket.isNullOrEmpty()) return bucket
    return "Video audio"
}

internal fun resolveVideoAlbum(rawAlbum: String?, bucketDisplayName: String?): String {
    val album = rawAlbum?.trim()
    if (!album.isNullOrEmpty() && album != MediaStore.UNKNOWN_STRING) return album
    val bucket = bucketDisplayName?.trim()
    if (!bucket.isNullOrEmpty()) return bucket
    return "Video Audio"
}

/** Reads every music track the system media index knows about. */
fun loadSongs(context: Context): List<Song> {
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.TRACK,
    )
    val songs = ArrayList<Song>()
    context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        "${MediaStore.Audio.Media.IS_MUSIC} != 0",
        null,
        "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE",
    )?.use { c ->
        songs.ensureCapacity(c.count)
        while (c.moveToNext()) {
            val artist = c.getString(2)
            songs += Song(
                id = c.getLong(0),
                title = c.getString(1) ?: "Unknown",
                artist = if (artist.isNullOrBlank() || artist == MediaStore.UNKNOWN_STRING) "Unknown artist" else artist,
                album = c.getString(3) ?: "Unknown album",
                albumId = c.getLong(4),
                durationMs = c.getLong(5),
                // TRACK is encoded as disc * 1000 + track number.
                track = c.getInt(6),
            )
        }
    }
    return songs
}

/** Reads device video files from [MediaStore] so their audio can be played without copying files. */
fun loadVideos(context: Context): List<Song> {
    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.TITLE,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.ARTIST,
        MediaStore.Video.Media.ALBUM,
        MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Video.Media.DURATION,
    )
    val videos = ArrayList<Song>()
    context.contentResolver.query(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        projection,
        null,
        null,
        "${MediaStore.Video.Media.TITLE} COLLATE NOCASE",
    )?.use { c ->
        val idIdx = c.getColumnIndex(MediaStore.Video.Media._ID)
        val titleIdx = c.getColumnIndex(MediaStore.Video.Media.TITLE)
        val displayIdx = c.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
        val artistIdx = c.getColumnIndex(MediaStore.Video.Media.ARTIST)
        val albumIdx = c.getColumnIndex(MediaStore.Video.Media.ALBUM)
        val bucketIdx = c.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
        val durationIdx = c.getColumnIndex(MediaStore.Video.Media.DURATION)
        if (idIdx < 0) return emptyList()

        videos.ensureCapacity(c.count)
        while (c.moveToNext()) {
            val mediaId = c.getLong(idIdx)
            if (mediaId < 0L) continue
            val rawTitle = if (titleIdx >= 0) c.getString(titleIdx) else null
            val displayName = if (displayIdx >= 0) c.getString(displayIdx) else null
            val rawArtist = if (artistIdx >= 0) c.getString(artistIdx) else null
            val rawAlbum = if (albumIdx >= 0) c.getString(albumIdx) else null
            val bucket = if (bucketIdx >= 0) c.getString(bucketIdx) else null
            val durationMs = if (durationIdx >= 0) c.getLong(durationIdx).coerceAtLeast(0L) else 0L

            videos += Song(
                id = videoTrackId(mediaId),
                title = resolveVideoTitle(rawTitle, displayName),
                artist = resolveVideoArtist(rawArtist, bucket),
                album = resolveVideoAlbum(rawAlbum, bucket),
                albumId = -1L,
                durationMs = durationMs,
                track = 0,
                isVideo = true,
            )
        }
    }
    return videos.sortedBy { it.title.lowercase() }
}

fun List<Song>.toAlbums(): List<Album> =
    filterNot { it.isVideo }
        .groupBy { it.albumId }
        .map { (id, s) -> Album(id, s[0].album, s[0].artist, s.size, s.map { it.id }) }
        .sortedBy { it.title.lowercase() }

fun List<Song>.toArtists(): List<Artist> =
    filterNot { it.isVideo }
        .groupBy { it.artist }
        .map { (name, s) -> Artist(name, s.size) }
        .sortedBy { it.name.lowercase() }

/** Every whitespace-separated word of [query] must appear in the title, artist or album. */
fun List<Song>.search(query: String): List<Song> {
    val words = query.lowercase().split(' ').filter { it.isNotEmpty() }
    if (words.isEmpty()) return this
    return filter { song -> words.all { it in song.searchKey } }
}
