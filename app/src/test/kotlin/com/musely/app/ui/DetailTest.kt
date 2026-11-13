package com.musely.app.ui

import com.musely.app.Album
import com.musely.app.Playlist
import com.musely.app.Song
import com.musely.app.videoTrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailTest {

    private fun song(id: Long, albumId: Long, track: Int, artist: String = "Artist", isVideo: Boolean = false) = Song(
        id = id,
        title = "Song $id",
        artist = artist,
        album = "Album $albumId",
        albumId = albumId,
        durationMs = 60_000L,
        track = track,
        isVideo = isVideo,
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

    @Test
    fun playlistDetail_resolvesMixedAudioAndVideosAndFormatsSubtitles() {
        val v2 = videoTrackId(2L)
        val audio = song(id = 10L, albumId = 1L, track = 1)
        val video = song(id = v2, albumId = 2L, track = 0, artist = "Movies", isVideo = true)
        val visibleById = mapOf(audio.id to audio, video.id to video)

        val mixed = Playlist(id = "p1", name = "Mix", songIds = listOf(v2, 999L, 10L))
        val mixedDetail = playlistDetail(mixed, visibleById)
        assertEquals(listOf(video, audio), mixedDetail.songs)
        assertEquals("2 tracks · 2 min", mixedDetail.subtitle)

        val videoOnly = Playlist(id = "p2", name = "Clips", songIds = listOf(v2))
        assertEquals("1 video · 1 min", playlistDetail(videoOnly, visibleById).subtitle)

        val emptyCustom = Playlist(id = "p3", name = "Empty", songIds = emptyList(), isSystem = false)
        assertEquals("Empty playlist", playlistDetail(emptyCustom, visibleById).subtitle)

        val emptyStarred = Playlist(id = "__starred__", name = "Liked Music", songIds = emptyList(), isSystem = true)
        assertEquals("Starred tracks", playlistDetail(emptyStarred, visibleById).subtitle)
    }
}
