package com.musely.app.download

/** Title/artist/album written into a downloaded file so the library shows them properly. */
data class TrackTags(
    val title: String,
    val artist: String,
    val album: String,
)

private const val TOPIC_SUFFIX = " - Topic"
private const val MAX_FILE_NAME_LENGTH = 120
private val illegalFileNameChars = Regex("""[\\/:*?"<>|\u0000-\u001F\u007F-\u009F\u200E\u200F\u202A-\u202E\u2066-\u2069]""")
private val whitespaceRun = Regex("""\s+""")

/** Bracketed noise that YouTube titles carry but a music library should not. */
private val titleNoise = Regex(
    """\s*[(\[](?:official\s+)?(?:music\s+|lyric\s+|audio\s+|hd\s+|4k\s+)?""" +
        """(?:video|audio|lyrics?|visualizer|visualiser|mv|hd|hq|4k)(?:\s+video)?[)\]]""",
    RegexOption.IGNORE_CASE,
)

/** Strips YouTube Music's auto-generated " - Topic" channel suffix. */
fun cleanUploader(uploader: String): String = uploader.trim().removeSuffix(TOPIC_SUFFIX).trim()

/** Removes "(Official Video)", "[Lyrics]" and similar decorations from a title. */
fun cleanTitle(title: String): String = titleNoise.replace(title, "").replace(whitespaceRun, " ").trim()

/**
 * Derives tags from a YouTube video's [name] and [uploader]. Topic channels and YouTube Music
 * uploads already have a clean title; ordinary uploads often use "Artist - Title".
 */
fun deriveYouTubeTags(name: String, uploader: String): TrackTags {
    val isTopicChannel = uploader.trim().endsWith(TOPIC_SUFFIX)
    val cleanName = cleanTitle(name)
    val artistFromUploader = cleanUploader(uploader).ifBlank { "Unknown artist" }
    if (!isTopicChannel) {
        val dash = cleanName.indexOf(" - ")
        if (dash > 0 && dash < cleanName.length - 3) {
            val artist = cleanName.substring(0, dash).trim()
            val title = cleanName.substring(dash + 3).trim()
            if (artist.isNotEmpty() && title.isNotEmpty()) {
                return TrackTags(title = title, artist = artist, album = title)
            }
        }
    }
    val title = cleanName.ifBlank { name.trim() }
    return TrackTags(title = title, artist = artistFromUploader, album = title)
}

/** A file name safe on every Android storage backend, e.g. `Artist - Title.m4a`. */
fun safeFileName(tags: TrackTags, extension: String): String {
    val base = listOf(tags.artist, tags.title)
        .filter { it.isNotBlank() }
        .joinToString(" - ")
        .replace(illegalFileNameChars, "_")
        .replace(whitespaceRun, " ")
        .trim()
        .trimStart('.')
        .take(MAX_FILE_NAME_LENGTH)
        .trimEnd('.', ' ')
        .trim()
        .ifEmpty { "Musely download" }
    return "$base.$extension"
}
