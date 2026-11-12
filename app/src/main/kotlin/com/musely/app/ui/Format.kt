package com.musely.app.ui

import com.musely.app.Song

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
