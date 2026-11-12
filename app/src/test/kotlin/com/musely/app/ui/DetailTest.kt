package com.musely.app.ui

import com.musely.app.Album
import com.musely.app.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailTest {

    private fun song(id: Long, albumId: Long, track: Int, artist: String = "Artist") = Song(
        id = id,
        title = "Song $id",
        artist = artist,
        album = "Album $albumId",
        albumId = albumId,
        durationMs = 60_000L,
        track = track,
    )

    @Test
    fun albumDetail_filtersAlbumAndSortsByTrack() {
        val songs = listOf(
            song(id = 1L, albumId = 7L, track = 3),
            song(id = 2L, albumId = 8L, track = 1),
            song(id = 3L, albumId = 7L, track = 1),
            song(id = 4L, albumId = 7L, track = 2),
        )
        val album = Album(id = 7L, title = "Album 7", artist = "Artist", songCount = 3, songIds = listOf(1L, 3L, 4L))

        val detail = albumDetail(album, songs)

        assertEquals(7L, detail.albumId)
        assertEquals(listOf(3L, 4L, 1L), detail.songs.map { it.id })
    }

    @Test
    fun artistDetail_filtersArtistLeavesAlbumIdNullAndHandlesEmpty() {
        val songs = listOf(
            song(id = 1L, albumId = 10L, track = 1, artist = "Mira"),
            song(id = 2L, albumId = 11L, track = 1, artist = "Mira"),
            song(id = 3L, albumId = 12L, track = 1, artist = "Other"),
        )

        val detail = artistDetail("Mira", songs)
        assertNull(detail.albumId)
        assertEquals(listOf(1L, 2L), detail.songs.map { it.id })

        val empty = artistDetail("Unknown", songs)
        assertTrue(empty.songs.isEmpty())
    }
}
