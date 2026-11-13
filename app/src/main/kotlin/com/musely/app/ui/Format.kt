package com.musely.app.ui

import com.musely.app.Song
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

internal fun formatTime(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Total listening time, e.g. "48 min" or "1 hr 12 min". */
internal fun formatTotal(songs: List<Song>): String {
    val minutes = songs.sumOf { it.durationMs } / 60_000
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0L -> "$m min"
        m == 0L -> "$h hr"
        else -> "$h hr $m min"
    }
}

internal fun songCount(count: Int) = if (count == 1) "1 song" else "$count songs"

internal fun videoCount(count: Int) = if (count == 1) "1 video" else "$count videos"

internal fun trackCount(count: Int) = if (count == 1) "1 track" else "$count tracks"

internal fun collectionSummary(songs: List<Song>): String = when {
    songs.isEmpty() -> songCount(0)
    songs.all { it.isVideo } -> videoCount(songs.size)
    songs.none { it.isVideo } -> songCount(songs.size)
    else -> trackCount(songs.size)
}

internal fun albumCount(count: Int) = if (count == 1) "1 album" else "$count albums"

internal fun greeting(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Late-night listening"
}

/** First letter or digit of a name, for avatar tiles. */
internal fun initialOf(name: String): String =
    name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "♪"

/** Human-readable file size, e.g. "8.4 MB" or "420 KB". */
internal fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        bytes < kb -> "$bytes B"
        bytes < mb -> "${(bytes / kb).roundToInt()} KB"
        bytes < gb -> "%.1f MB".format(Locale.US, bytes / mb)
        else -> "%.2f GB".format(Locale.US, bytes / gb)
    }
}

/** Human-readable audio bitrate, e.g. "320 kbps". */
internal fun formatBitrate(bps: Int?): String? {
    if (bps == null || bps <= 0) return null
    val kbps = (bps / 1000.0).roundToInt().coerceAtLeast(1)
    return "$kbps kbps"
}

/** Human-readable audio sample rate, e.g. "44.1 kHz" or "48 kHz". */
internal fun formatSampleRate(hz: Int?): String? {
    if (hz == null || hz <= 0) return null
    return if (hz % 1000 == 0) {
        "${hz / 1000} kHz"
    } else {
        "%.1f kHz".format(Locale.US, hz / 1000.0)
    }
}

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

/** Formats a Unix timestamp in seconds as a short local date, e.g. "Nov 12, 2025". */
internal fun formatDateSeconds(epochSec: Long): String {
    if (epochSec <= 0L) return ""
    return dateFormatter.withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(epochSec))
}
