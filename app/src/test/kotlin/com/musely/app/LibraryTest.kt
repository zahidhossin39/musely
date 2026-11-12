package com.musely.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTest {

    private fun song(
        id: Long,
        title: String = "Track $id",
        artist: String = "Artist",
        album: String = "Album",
        albumId: Long = 10L,
        durationMs: Long = 180_000L,
        track: Int = 1,
    ) = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        durationMs = durationMs,
        track = track,
    )

    @Test
    fun toAlbums_emptyListReturnsEmpty() {
        assertTrue(emptyList<Song>().toAlbums().isEmpty())
    }

    @Test
    fun toAlbums_collectsAllSongIdsPerAlbumAndSortsCaseInsensitively() {
        val songs = listOf(
            song(id = 101L, album = "zeta", artist = "Band Z", albumId = 1L),
            song(id = 102L, album = "Alpha", artist = "Band A", albumId = 2L),
            song(id = 103L, album = "zeta", artist = "Band Z", albumId = 1L),
            song(id = 104L, album = "zeta", artist = "Band Z", albumId = 1L),
        )

        val albums = songs.toAlbums()

        assertEquals(2, albums.size)
        assertEquals(2L, albums[0].id)
        assertEquals("Alpha", albums[0].title)
        assertEquals(listOf(102L), albums[0].songIds)

        assertEquals(1L, albums[1].id)
        assertEquals("zeta", albums[1].title)
        assertEquals(3, albums[1].songCount)
        assertEquals(listOf(101L, 103L, 104L), albums[1].songIds)
    }

    @Test
    fun toArtists_emptyListAndCountingAndSorting() {
        assertTrue(emptyList<Song>().toArtists().isEmpty())

        val songs = listOf(
            song(id = 1L, artist = "zoe"),
            song(id = 2L, artist = "Alice"),
            song(id = 3L, artist = "zoe"),
        )
        val artists = songs.toArtists()

        assertEquals(listOf(Artist("Alice", 1), Artist("zoe", 2)), artists)
    }

    @Test
    fun search_matchesMultiWordAcrossFieldsAndHandlesBlankQuery() {
        val songs = listOf(
            song(id = 1L, title = "Midnight Echo", artist = "Luna", album = "Nightfall"),
            song(id = 2L, title = "Daybreak", artist = "Sol", album = "Morning"),
        )

        assertEquals(songs, songs.search(""))
        assertEquals(songs, songs.search("   "))
        assertEquals(listOf(songs[0]), songs.search("luna nightfall"))
        assertEquals(listOf(songs[0]), songs.search("ECHO"))
        assertTrue(songs.search("luna morning").isEmpty())
    }
}
