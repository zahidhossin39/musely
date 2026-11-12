package com.musely.app

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val track: Int,
) {
    val uri: Uri get() = songUri(id)

    /** Lower-cased text that search matches against. */
    val searchKey: String = "$title\n$artist\n$album".lowercase()
}

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val songCount: Int,
    val songIds: List<Long> = emptyList(),
)

data class Artist(val name: String, val songCount: Int)

fun songUri(songId: Long): Uri =
    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)

fun albumUri(albumId: Long): Uri =
    ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId)

fun albumArtUri(albumId: Long): Uri =
    ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

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

fun List<Song>.toAlbums(): List<Album> =
    groupBy { it.albumId }
        .map { (id, s) -> Album(id, s[0].album, s[0].artist, s.size, s.map { it.id }) }
        .sortedBy { it.title.lowercase() }

fun List<Song>.toArtists(): List<Artist> =
    groupBy { it.artist }
        .map { (name, s) -> Artist(name, s.size) }
        .sortedBy { it.name.lowercase() }

/** Every whitespace-separated word of [query] must appear in the title, artist or album. */
fun List<Song>.search(query: String): List<Song> {
    val words = query.lowercase().split(' ').filter { it.isNotEmpty() }
    if (words.isEmpty()) return this
    return filter { song -> words.all { it in song.searchKey } }
}
