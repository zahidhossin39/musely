package com.musely.app

import android.provider.MediaStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoAudioReviewTest {
    private fun track(
        id: Long,
        title: String,
        artist: String = "Artist",
        album: String = "Album",
        isVideo: Boolean = false,
    ) = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = 1L,
        durationMs = 12_000L,
        track = 1,
        isVideo = isVideo,
    )

    @Test
    fun resolveVideoTitleIgnoresMediaStoreUnknownString() {
        assertEquals(
            "concert_clip",
            resolveVideoTitle(MediaStore.UNKNOWN_STRING, "concert_clip.mp4"),
        )
        assertEquals(
            "concert_clip",
            resolveVideoTitle("  ${MediaStore.UNKNOWN_STRING}  ", "concert_clip.mp4"),
        )
        assertEquals(
            "Actual Title",
            resolveVideoTitle("  Actual Title  ", "concert_clip.mp4"),
        )
    }

    @Test
    fun searchMatchesMultiWordQueriesAcrossMixedAudioAndVideo() {
        val items = listOf(
            track(1L, "Midnight Drive", artist = "Synth", isVideo = false),
            track(videoTrackId(10L), "Live Session", artist = "Camera Roll", album = "DCIM", isVideo = true),
            track(videoTrackId(11L), "Acoustic Jam", artist = "Studio", album = "Rehearsal", isVideo = true),
        )

        assertEquals(listOf(videoTrackId(10L)), items.search("camera live").map { it.id })
        assertEquals(listOf(videoTrackId(11L)), items.search("studio video").map { it.id })
    }

    @Test
    fun videoTrackAudioWarningCoversMissingAndUnsupportedAudio() {
        val video = track(videoTrackId(5L), "Clip", isVideo = true)
        val audio = track(5L, "Song", isVideo = false)

        assertNull(
            MainViewModel.PlaybackMessages.videoTrackAudioWarning(
                song = null,
                hasGroups = true,
                hasAudioGroup = false,
                hasSupportedAudio = false,
            ),
        )
        assertNull(
            MainViewModel.PlaybackMessages.videoTrackAudioWarning(
                song = audio,
                hasGroups = true,
                hasAudioGroup = false,
                hasSupportedAudio = false,
            ),
        )
        assertNull(
            MainViewModel.PlaybackMessages.videoTrackAudioWarning(
                song = video,
                hasGroups = false,
                hasAudioGroup = false,
                hasSupportedAudio = false,
            ),
        )
        assertEquals(
            "\"Clip\" has no audio track",
            MainViewModel.PlaybackMessages.videoTrackAudioWarning(
                song = video,
                hasGroups = true,
                hasAudioGroup = false,
                hasSupportedAudio = false,
            ),
        )
        assertEquals(
            "Audio format in \"Clip\" is not supported",
            MainViewModel.PlaybackMessages.videoTrackAudioWarning(
                song = video,
                hasGroups = true,
                hasAudioGroup = true,
                hasSupportedAudio = false,
            ),
        )
        assertNull(
            MainViewModel.PlaybackMessages.videoTrackAudioWarning(
                song = video,
                hasGroups = true,
                hasAudioGroup = true,
                hasSupportedAudio = true,
            ),
        )
    }

    @Test
    fun playbackErrorMessageDistinguishesAudioFromVideo() {
        val video = track(videoTrackId(5L), "Clip", isVideo = true)
        val audio = track(5L, "Song", isVideo = false)

        assertEquals("Unable to play this track", MainViewModel.PlaybackMessages.playbackErrorMessage(null))
        assertEquals("Unable to play \"Song\"", MainViewModel.PlaybackMessages.playbackErrorMessage(audio))
        assertEquals("Unable to play audio from \"Clip\"", MainViewModel.PlaybackMessages.playbackErrorMessage(video))
    }
}
