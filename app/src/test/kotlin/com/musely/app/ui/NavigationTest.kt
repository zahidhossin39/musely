package com.musely.app.ui

import androidx.compose.ui.unit.dp
import com.musely.app.UpdateInfo
import com.musely.app.UpdateStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationTest {

    @Test
    fun `tabs appear in the bottom bar in the agreed order`() {
        assertEquals(
            listOf("Home", "Playlists", "Downloads", "Settings"),
            MainTab.entries.map { it.label },
        )
    }

    @Test
    fun `back from home is left to the system`() {
        assertNull(backDestination(MainTab.Home))
    }

    @Test
    fun `back from any other tab returns home`() {
        listOf(MainTab.Playlists, MainTab.Downloads, MainTab.Settings).forEach { tab ->
            assertEquals(MainTab.Home, backDestination(tab))
        }
    }

    @Test
    fun `playlists subtitle counts only user playlists`() {
        assertEquals("Liked Music lives here. Make a playlist of your own.", playlistsSubtitle(0))
        assertEquals("Liked Music and 1 playlist", playlistsSubtitle(1))
        assertEquals("Liked Music and 4 playlists", playlistsSubtitle(4))
    }

    @Test
    fun `hidden summary pluralises correctly`() {
        assertEquals("Nothing hidden", hiddenSummary(0))
        assertEquals("1 item hidden", hiddenSummary(1))
        assertEquals("12 items hidden", hiddenSummary(12))
    }

    @Test
    fun `snackbar sits just above the edge while the full player is open`() {
        assertEquals(12.dp, snackbarBottomPadding(playerOpen = true, barVisible = true, miniPlayerVisible = true))
    }

    @Test
    fun `snackbar clears the bottom bar and mini player`() {
        assertEquals(80.dp + MiniPlayerReserve, snackbarBottomPadding(false, barVisible = true, miniPlayerVisible = true))
        assertEquals(80.dp + 12.dp, snackbarBottomPadding(false, barVisible = true, miniPlayerVisible = false))
        assertEquals(12.dp, snackbarBottomPadding(false, barVisible = false, miniPlayerVisible = false))
    }

    @Test
    fun `update card title reflects every status`() {
        val info = UpdateInfo("3.5.0", "v3.5.0", "https://example.test/Musely.apk", "", "")
        assertEquals("Update available", updateTitle(UpdateStatus.Available(info)))
        assertEquals("You're up to date", updateTitle(UpdateStatus.UpToDate("3.5.0")))
        assertEquals("Checking for updates", updateTitle(UpdateStatus.Checking))
        assertEquals("Update check failed", updateTitle(UpdateStatus.Error("offline")))
        assertEquals("App updates", updateTitle(UpdateStatus.Idle))
    }
}
