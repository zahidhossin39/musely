package com.musely.app.ui

import com.musely.app.download.DownloadFormat
import com.musely.app.download.DownloadSource
import com.musely.app.download.DownloadState
import com.musely.app.download.MediaEntry
import com.musely.app.download.MediaLink
import com.musely.app.download.ResolvedMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadUiTextTest {

    @Test
    fun `status text covers every download state`() {
        assertEquals("Waiting…", downloadStatusText(DownloadState.Queued, DownloadFormat.Audio))
        assertEquals("Preparing…", downloadStatusText(DownloadState.Preparing, DownloadFormat.Audio))
        assertEquals("1.0 MB of 4.0 MB", downloadStatusText(DownloadState.Downloading(1_048_576, 4_194_304), DownloadFormat.Audio))
        assertEquals("512 KB", downloadStatusText(DownloadState.Downloading(524_288, -1), DownloadFormat.Audio))
        assertEquals("Saving to your library…", downloadStatusText(DownloadState.Processing, DownloadFormat.Audio))
        assertEquals("Merging and saving…", downloadStatusText(DownloadState.Processing, DownloadFormat.Video))
        assertEquals("Video · 2.0 MB", downloadStatusText(DownloadState.Completed(1, 2_097_152, 0), DownloadFormat.Video))
        assertEquals("No match", downloadStatusText(DownloadState.Failed("No match"), DownloadFormat.Audio))
        assertEquals("Canceled", downloadStatusText(DownloadState.Canceled, DownloadFormat.Audio))
    }

    @Test
    fun `download progress fraction is clamped and unknown without a size`() {
        assertEquals(0.25f, DownloadState.Downloading(25, 100).fraction)
        assertNull(DownloadState.Downloading(25, -1).fraction)
        assertEquals(1f, DownloadState.Downloading(150, 100).fraction)
    }

    @Test
    fun `preview summary shows duration for singles and track count for collections`() {
        val entry = MediaEntry("Song", "Artist", null, 213_000, null, DownloadSource.YouTube("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        val single = ResolvedMedia(MediaLink.YouTubeVideo("dQw4w9WgXcQ", false), "Song", "Artist", null, false, listOf(entry))
        assertEquals("Artist · 3:33", previewSummary(single))

        val collection = single.copy(subtitle = "Spotify", isCollection = true, entries = listOf(entry, entry, entry))
        assertEquals("Spotify · 3 tracks", previewSummary(collection))
    }

    @Test
    fun `saved location matches the format folder`() {
        assertEquals("Music/Musely", savedLocation(DownloadFormat.Audio))
        assertEquals("Movies/Musely", savedLocation(DownloadFormat.Video))
    }
}
