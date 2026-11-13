package com.musely.app.ui

import com.musely.app.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class FormatTest {

    private fun song(durationMs: Long, isVideo: Boolean = false) = Song(
        id = if (isVideo) -2L else 1L,
        title = "Track",
        artist = "Artist",
        album = "Album",
        albumId = 1L,
        durationMs = durationMs,
        track = 1,
        isVideo = isVideo,
    )

    @Test
    fun formatTime_formatsMinutesAndHours() {
        assertEquals("0:00", formatTime(0L))
        assertEquals("0:45", formatTime(45_000L))
        assertEquals("3:05", formatTime(185_000L))
        assertEquals("1:02:03", formatTime(3_723_000L))
    }

    @Test
    fun formatTotal_formatsHoursAndMinutes() {
        assertEquals("0 min", formatTotal(emptyList()))
        assertEquals("48 min", formatTotal(listOf(song(48 * 60_000L))))
        assertEquals("2 hr", formatTotal(listOf(song(120 * 60_000L))))
        assertEquals("1 hr 12 min", formatTotal(listOf(song(72 * 60_000L))))
    }

    @Test
    fun countsAndGreetingAndInitial() {
        assertEquals("1 song", songCount(1))
        assertEquals("3 songs", songCount(3))
        assertEquals("1 album", albumCount(1))
        assertEquals("0 albums", albumCount(0))
        assertEquals("Good morning", greeting(8))
        assertEquals("Good afternoon", greeting(14))
        assertEquals("Good evening", greeting(19))
        assertEquals("Late-night listening", greeting(2))
        assertEquals("M", initialOf("  musely"))
        assertEquals("♪", initialOf("..."))
    }

    @Test
    fun formatFileSize_formatsBytesKilobytesMegabytesGigabytes() {
        assertEquals("0 B", formatFileSize(0L))
        assertEquals("0 B", formatFileSize(-50L))
        assertEquals("512 B", formatFileSize(512L))
        assertEquals("420 KB", formatFileSize(420L * 1024L))
        assertEquals("8.4 MB", formatFileSize((8.4 * 1024 * 1024).toLong()))
        assertEquals("1.50 GB", formatFileSize((1.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun formatBitrate_formatsPositiveAndReturnsNullForInvalid() {
        assertNull(formatBitrate(null))
        assertNull(formatBitrate(0))
        assertNull(formatBitrate(-128_000))
        assertEquals("320 kbps", formatBitrate(320_000))
        assertEquals("192 kbps", formatBitrate(192_000))
        assertEquals("1 kbps", formatBitrate(400))
    }

    @Test
    fun formatSampleRate_formatsKilohertzAndReturnsNullForInvalid() {
        assertNull(formatSampleRate(null))
        assertNull(formatSampleRate(0))
        assertNull(formatSampleRate(-44_100))
        assertEquals("44.1 kHz", formatSampleRate(44_100))
        assertEquals("48 kHz", formatSampleRate(48_000))
        assertEquals("96 kHz", formatSampleRate(96_000))
    }

    @Test
    fun formatDateSeconds_formatsEpochSecondsAndHandlesNonPositive() {
        assertEquals("", formatDateSeconds(0L))
        assertEquals("", formatDateSeconds(-10L))

        val epochSec = 1_762_905_600L // 2025-11-12T00:00:00Z
        val expected = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochSecond(epochSec))
        val actual = formatDateSeconds(epochSec)
        assertTrue(actual.isNotBlank())
        assertEquals(expected, actual)
    }

    @Test
    fun videoCount_trackCount_andCollectionSummary_handleAudioVideoAndMixedLists() {
        assertEquals("0 videos", videoCount(0))
        assertEquals("1 video", videoCount(1))
        assertEquals("4 videos", videoCount(4))

        assertEquals("0 tracks", trackCount(0))
        assertEquals("1 track", trackCount(1))
        assertEquals("3 tracks", trackCount(3))

        assertEquals("0 songs", collectionSummary(emptyList()))
        assertEquals("2 songs", collectionSummary(listOf(song(1_000L), song(2_000L))))
        assertEquals("2 videos", collectionSummary(listOf(song(1_000L, isVideo = true), song(2_000L, isVideo = true))))
        assertEquals(
            "3 tracks",
            collectionSummary(listOf(song(1_000L), song(2_000L), song(3_000L, isVideo = true))),
        )
    }
}
