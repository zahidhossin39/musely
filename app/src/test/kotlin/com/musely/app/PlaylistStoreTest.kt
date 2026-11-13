package com.musely.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.json.JSONObject
import java.io.File

class PlaylistStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newStore(
        dir: File = tempFolder.root,
        fileName: String = PlaylistStore.DEFAULT_FILE_NAME,
        clock: () -> Long = { 1_700_000_000_000L },
    ): PlaylistStore = PlaylistStore(dir, fileName, clock)

    @Test
    fun encodeAndDecodeStateJson_roundTripsAllFields() {
        val original = LibraryUserState(
            playlists = listOf(
                Playlist(
                    id = "pl-1",
                    name = "Road Trip",
                    songIds = listOf(10L, 20L, 30L),
                    isSystem = false,
                    createdAtMs = 123_456L,
                ),
                Playlist(
                    id = "pl-2",
                    name = "Focus",
                    songIds = emptyList(),
                    isSystem = false,
                    createdAtMs = 789_012L,
                ),
            ),
            starredSongIds = listOf(99L, 42L),
            hiddenSongIds = setOf(7L, 8L),
        )

        val json = encodeStateJson(original)
        val decoded = decodeStateJson(json)

        assertEquals(original, decoded)
    }

    @Test
    fun decodeStateJson_handlesEmptyAndCorruptInputsSafely() {
        assertEquals(LibraryUserState(), decodeStateJson(""))
        assertEquals(LibraryUserState(), decodeStateJson("   "))
        assertEquals(LibraryUserState(), decodeStateJson("{not valid json"))
        assertEquals(LibraryUserState(), decodeStateJson("[]"))
        assertEquals(LibraryUserState(), decodeStateJson("null"))

        val partial = """
            {
              "playlists": [
                {"id": "", "name": "No ID", "songIds": [1]},
                {"id": "sys", "name": "Sys", "isSystem": true, "songIds": [2]},
                {"id": "__starred__", "name": "Fake Starred", "songIds": [3]},
                {"id": "ok", "name": "  Chill  ", "songIds": [5, 5, "6", -1, "bad"], "createdAtMs": 42}
              ],
              "starredSongIds": [10, 10, "11", "nope"],
              "hiddenSongIds": [20, 20, -5]
            }
        """.trimIndent()

        val decoded = decodeStateJson(partial)
        assertEquals(1, decoded.playlists.size)
        assertEquals(
            Playlist(
                id = "ok",
                name = "Chill",
                songIds = listOf(5L, 6L),
                isSystem = false,
                createdAtMs = 42L,
            ),
            decoded.playlists[0],
        )
        assertEquals(listOf(10L, 11L), decoded.starredSongIds)
        assertEquals(setOf(20L), decoded.hiddenSongIds)
    }

    @Test
    fun playlistCrud_createRenameDeleteAndSystemGuards() {
        var now = 1000L
        val store = newStore(clock = { now })

        assertNull(store.createPlaylist("   "))
        val created = store.createPlaylist("  Late Night  ")
        assertNotNull(created)
        assertEquals("Late Night", created!!.name)
        assertEquals(1000L, created.createdAtMs)
        assertFalse(created.isSystem)

        // Rename guards
        assertFalse(store.renamePlaylist(created.id, "   "))
        assertFalse(store.renamePlaylist("missing-id", "New Name"))
        assertFalse(store.renamePlaylist(PlaylistStore.STARRED_PLAYLIST_ID, "Hacked"))
        assertTrue(store.renamePlaylist(created.id, "  Midnight Vibes  "))
        assertEquals("Midnight Vibes", store.loadState().playlists.single().name)

        // Delete guards
        assertFalse(store.deletePlaylist(PlaylistStore.STARRED_PLAYLIST_ID))
        assertFalse(store.deletePlaylist("missing-id"))
        assertTrue(store.deletePlaylist(created.id))
        assertTrue(store.loadState().playlists.isEmpty())
    }

    @Test
    fun playlistSongs_deduplicatesRemovesAndReorders() {
        val store = newStore()
        val pl = store.createPlaylist("Mix")!!

        assertTrue(store.addSongToPlaylist(pl.id, 1L))
        assertFalse(store.addSongToPlaylist(pl.id, 1L))
        assertTrue(store.addSongsToPlaylist(pl.id, listOf(1L, 2L, 3L, 2L, 4L)))
        assertFalse(store.addSongsToPlaylist(pl.id, listOf(1L, 4L)))
        assertFalse(store.addSongToPlaylist("unknown", 99L))

        assertEquals(listOf(1L, 2L, 3L, 4L), store.loadState().playlists.single().songIds)

        // Reorder forward and backward
        assertFalse(store.moveSongInPlaylist(pl.id, 1, 1))
        assertFalse(store.moveSongInPlaylist(pl.id, -1, 2))
        assertFalse(store.moveSongInPlaylist(pl.id, 0, 99))
        assertTrue(store.moveSongInPlaylist(pl.id, 0, 2))
        assertEquals(listOf(2L, 3L, 1L, 4L), store.loadState().playlists.single().songIds)
        assertTrue(store.moveSongInPlaylist(pl.id, 3, 1))
        assertEquals(listOf(2L, 4L, 3L, 1L), store.loadState().playlists.single().songIds)

        // Remove by index & by songId
        assertFalse(store.removeSongFromPlaylist(pl.id, 10))
        assertTrue(store.removeSongFromPlaylist(pl.id, 1))
        assertEquals(listOf(2L, 3L, 1L), store.loadState().playlists.single().songIds)

        assertFalse(store.removeSongIdFromPlaylist(pl.id, 999L))
        assertTrue(store.removeSongIdFromPlaylist(pl.id, 3L))
        assertEquals(listOf(2L, 1L), store.loadState().playlists.single().songIds)
    }

    @Test
    fun starredPlaylist_prependsNewestAndPinsFirstInBuildPlaylistsWithStarred() {
        val store = newStore()
        val custom = store.createPlaylist("Workout")!!

        assertTrue(store.toggleStar(10L))
        assertTrue(store.toggleStar(20L))
        assertEquals(listOf(20L, 10L), store.loadState().starredSongIds)

        assertFalse(store.toggleStar(10L))
        assertEquals(listOf(20L), store.loadState().starredSongIds)

        store.setStarred(30L, true)
        store.setStarred(30L, true)
        assertEquals(listOf(30L, 20L), store.loadState().starredSongIds)
        store.setStarred(20L, false)
        assertEquals(listOf(30L), store.loadState().starredSongIds)

        val all = buildPlaylistsWithStarred(store.loadState())
        assertEquals(2, all.size)
        assertEquals(PlaylistStore.STARRED_PLAYLIST_ID, all[0].id)
        assertEquals(PlaylistStore.STARRED_PLAYLIST_NAME, all[0].name)
        assertTrue(all[0].isSystem)
        assertEquals(listOf(30L), all[0].songIds)
        assertEquals(custom.id, all[1].id)
    }

    @Test
    fun hiddenSongs_hideUnhideAndUnhideAll() {
        val store = newStore()
        store.hideSong(101L)
        store.hideSong(102L)
        store.hideSong(101L)
        assertEquals(setOf(101L, 102L), store.loadState().hiddenSongIds)

        store.unhideSong(101L)
        assertEquals(setOf(102L), store.loadState().hiddenSongIds)

        store.unhideAll()
        assertTrue(store.loadState().hiddenSongIds.isEmpty())
    }

    @Test
    fun pruneSongsAndRemoveDeletedSong_cleansPlaylistsStarredAndHidden() {
        val store = newStore()
        val pl = store.createPlaylist("Favorites")!!
        store.addSongsToPlaylist(pl.id, listOf(1L, 2L, 3L))
        store.setStarred(1L, true)
        store.setStarred(2L, true)
        store.hideSong(2L)
        store.hideSong(4L)

        assertTrue(store.removeDeletedSong(2L))
        assertFalse(store.removeDeletedSong(2L))
        var state = store.loadState()
        assertEquals(listOf(1L, 3L), state.playlists.single().songIds)
        assertEquals(listOf(1L), state.starredSongIds)
        assertEquals(setOf(4L), state.hiddenSongIds)

        assertTrue(store.pruneSongs(existingSongIds = setOf(1L)))
        assertFalse(store.pruneSongs(existingSongIds = setOf(1L)))
        state = store.loadState()
        assertEquals(listOf(1L), state.playlists.single().songIds)
        assertEquals(listOf(1L), state.starredSongIds)
        assertTrue(state.hiddenSongIds.isEmpty())
    }

    @Test
    fun persistence_survivesNewStoreInstanceWithoutLeavingTempFile() {
        val dir = tempFolder.newFolder("state-dir")
        val writer = newStore(dir = dir)
        val pl = writer.createPlaylist("Saved")!!
        writer.addSongsToPlaylist(pl.id, listOf(11L, 22L))
        writer.toggleStar(33L)
        writer.hideSong(44L)

        val reader = newStore(dir = dir)
        val loaded = reader.loadState()
        assertEquals(1, loaded.playlists.size)
        assertEquals("Saved", loaded.playlists[0].name)
        assertEquals(listOf(11L, 22L), loaded.playlists[0].songIds)
        assertEquals(listOf(33L), loaded.starredSongIds)
        assertEquals(setOf(44L), loaded.hiddenSongIds)
        assertFalse(File(dir, "${PlaylistStore.DEFAULT_FILE_NAME}.tmp").exists())
    }

    @Test
    fun encodeAndDecodeStateJson_preservesInterleavedAudioAndVideoOrderAndLegacyFields() {
        val v5 = videoTrackId(5L)
        val v0 = videoTrackId(0L)
        val v9 = videoTrackId(9L)
        val v3 = videoTrackId(3L)
        val original = LibraryUserState(
            playlists = listOf(
                Playlist(
                    id = "pl-mix",
                    name = "Concert Mix",
                    songIds = listOf(10L, v5, 20L, v0),
                    isSystem = false,
                    createdAtMs = 555_000L,
                ),
            ),
            starredSongIds = listOf(v9, 42L),
            hiddenSongIds = setOf(7L, v3),
        )

        assertEquals("a:10", encodeTrackKey(10L))
        assertEquals("v:5", encodeTrackKey(v5))
        assertEquals(10L, decodeTrackKey("a:10"))
        assertEquals(v5, decodeTrackKey("v:5"))
        assertNull(decodeTrackKey("x:1"))
        assertNull(decodeTrackKey("a:-1"))
        assertNull(decodeTrackKey("v:-1"))

        val json = encodeStateJson(original)
        val decoded = decodeStateJson(json)
        assertEquals(original, decoded)

        // Legacy fields retain only non-negative audio IDs so v2.0.0 decoders stay valid
        val obj = JSONObject(json)
        val plObj = obj.getJSONArray("playlists").getJSONObject(0)
        assertEquals("[10,20]", plObj.getJSONArray("songIds").toString())
        assertEquals("[\"a:10\",\"v:5\",\"a:20\",\"v:0\"]", plObj.getJSONArray("trackKeys").toString())

        val partialWithTrackKeys = """
            {
              "playlists": [
                {"id": "mix", "name": "Mix", "songIds": [99], "trackKeys": ["v:5", "bad", "a:-2", "v:-1", "a:10", "v:5"]}
              ],
              "starredTrackKeys": ["v:9", "nope", "a:42"],
              "hiddenTrackKeys": ["a:7", "v:3", "z:1"]
            }
        """.trimIndent()
        val parsed = decodeStateJson(partialWithTrackKeys)
        assertEquals(listOf(v5, 10L), parsed.playlists.single().songIds)
        assertEquals(listOf(v9, 42L), parsed.starredSongIds)
        assertEquals(setOf(7L, v3), parsed.hiddenSongIds)
    }

    @Test
    fun pruneSongs_respectsPruneAudioAndPruneVideosFlags() {
        val store = newStore()
        val pl = store.createPlaylist("Audio + Video")!!
        val v10 = videoTrackId(10L)
        val v20 = videoTrackId(20L)
        store.addSongsToPlaylist(pl.id, listOf(1L, v10, 2L, v20))
        store.setStarred(2L, true)
        store.setStarred(v10, true)
        store.hideSong(2L)
        store.hideSong(v20)

        // Audio permission granted, video permission not granted -> pruneAudio = true, pruneVideos = false
        assertTrue(store.pruneSongs(existingSongIds = setOf(1L), pruneAudio = true, pruneVideos = false))
        var state = store.loadState()
        assertEquals(listOf(1L, v10, v20), state.playlists.single().songIds)
        assertEquals(listOf(v10), state.starredSongIds)
        assertEquals(setOf(v20), state.hiddenSongIds)

        // Video permission granted with only v10 still present -> pruneVideos = true drops v20
        assertTrue(store.pruneSongs(existingSongIds = setOf(1L, v10), pruneAudio = true, pruneVideos = true))
        state = store.loadState()
        assertEquals(listOf(1L, v10), state.playlists.single().songIds)
        assertEquals(listOf(v10), state.starredSongIds)
        assertTrue(state.hiddenSongIds.isEmpty())
    }

    @Test
    fun mixedAudioAndVideoPlaylistCrud_persistsStarReorderAndDelete() {
        val dir = tempFolder.newFolder("mixed-state")
        val v7 = videoTrackId(7L)
        val writer = newStore(dir = dir)
        val pl = writer.createPlaylist("Clips")!!

        assertTrue(writer.addSongsToPlaylist(pl.id, listOf(100L, v7)))
        assertTrue(writer.toggleStar(v7))
        assertTrue(writer.moveSongInPlaylist(pl.id, 1, 0))

        val reader = newStore(dir = dir)
        val loaded = reader.loadState()
        assertEquals(listOf(v7, 100L), loaded.playlists.single().songIds)
        assertEquals(listOf(v7), loaded.starredSongIds)

        assertTrue(reader.removeDeletedSong(v7))
        val afterDelete = newStore(dir = dir).loadState()
        assertEquals(listOf(100L), afterDelete.playlists.single().songIds)
        assertTrue(afterDelete.starredSongIds.isEmpty())
    }
}
