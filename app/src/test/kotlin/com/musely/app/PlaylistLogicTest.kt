package com.musely.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PlaylistLogicTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `mapVisibleMoveToRawIndices maps visible indices across hidden songs`() {
        val raw = listOf(10L, 20L, 30L, 40L, 50L) // 20 and 40 are hidden
        val visible = listOf(10L, 30L, 50L)

        assertEquals(0 to 4, mapVisibleMoveToRawIndices(raw, visible, 0, 2))
        assertEquals(4 to 0, mapVisibleMoveToRawIndices(raw, visible, 2, 0))
        assertEquals(0 to 2, mapVisibleMoveToRawIndices(raw, visible, 0, 1))
        assertEquals(2 to 0, mapVisibleMoveToRawIndices(raw, visible, 1, 0))
    }

    @Test
    fun `mapVisibleMoveToRawIndices returns null for invalid or no-op moves`() {
        val raw = listOf(10L, 20L, 30L)
        val visible = listOf(10L, 30L)

        assertNull(mapVisibleMoveToRawIndices(raw, visible, 0, 0))
        assertNull(mapVisibleMoveToRawIndices(raw, visible, -1, 1))
        assertNull(mapVisibleMoveToRawIndices(raw, visible, 0, 2))
        assertNull(mapVisibleMoveToRawIndices(raw, listOf(10L, 99L), 0, 1))
    }

    @Test
    fun `mapped move preserves hidden songs in PlaylistStore`() {
        val store = PlaylistStore(tempFolder.root)
        val playlist = store.createPlaylist("Mix")!!
        store.addSongsToPlaylist(playlist.id, listOf(10L, 20L, 30L, 40L))
        store.hideSong(20L)

        val stateBefore = store.loadState()
        val rawBefore = stateBefore.playlists.first { it.id == playlist.id }.songIds
        val visibleBefore = rawBefore.filterNot { it in stateBefore.hiddenSongIds }
        assertEquals(listOf(10L, 30L, 40L), visibleBefore)

        val (rawFrom, rawTo) = mapVisibleMoveToRawIndices(rawBefore, visibleBefore, 0, 2)!!
        assertTrue(store.moveSongInPlaylist(playlist.id, rawFrom, rawTo))

        val stateAfter = store.loadState()
        val rawAfter = stateAfter.playlists.first { it.id == playlist.id }.songIds
        assertEquals(listOf(20L, 30L, 40L, 10L), rawAfter)
        assertEquals(listOf(30L, 40L, 10L), rawAfter.filterNot { it in stateAfter.hiddenSongIds })
    }

    @Test
    fun `findMatchingQueueIndicesDescending returns matches in descending order`() {
        val mediaIds = listOf("1", "42", "3", "42", "5")
        assertEquals(listOf(3, 1), findMatchingQueueIndicesDescending(mediaIds, 42L))
        assertEquals(emptyList<Int>(), findMatchingQueueIndicesDescending(mediaIds, 99L))
    }

    @Test
    fun `resolveUpdateStatusAfterCheck preserves Available on background error`() {
        val available = UpdateStatus.Available(
            UpdateInfo(
                version = "2.1.0",
                tagName = "v2.1.0",
                apkUrl = "https://example.com/Musely.apk",
                releaseUrl = "https://example.com/tag/v2.1.0",
                releaseNotes = "Notes",
            ),
        )
        val error = UpdateStatus.Error("Offline")

        assertEquals(available, resolveUpdateStatusAfterCheck(available, error, isManual = false))
        assertEquals(UpdateStatus.Idle, resolveUpdateStatusAfterCheck(UpdateStatus.Idle, error, isManual = false))
        assertEquals(available, resolveUpdateStatusAfterCheck(UpdateStatus.Idle, available, isManual = false))
        assertEquals(
            UpdateStatus.UpToDate("2.0.0"),
            resolveUpdateStatusAfterCheck(UpdateStatus.Idle, UpdateStatus.UpToDate("2.0.0"), isManual = false),
        )
        assertEquals(error, resolveUpdateStatusAfterCheck(available, error, isManual = true))
    }

    @Test
    fun `mapVisibleMoveToRawIndices works with mixed audio and negative video track IDs`() {
        val v1 = videoTrackId(1L)
        val v2 = videoTrackId(2L)
        val raw = listOf(10L, v1, 20L, v2) // v1 is hidden
        val visible = listOf(10L, 20L, v2)

        assertEquals(0 to 3, mapVisibleMoveToRawIndices(raw, visible, 0, 2))
        assertEquals(3 to 0, mapVisibleMoveToRawIndices(raw, visible, 2, 0))
    }

    @Test
    fun `findMatchingQueueIndicesDescending matches negative video track IDs`() {
        val v4 = videoTrackId(4L)
        val mediaIds = listOf("10", v4.toString(), "20", v4.toString())
        assertEquals(listOf(3, 1), findMatchingQueueIndicesDescending(mediaIds, v4))
    }
}
