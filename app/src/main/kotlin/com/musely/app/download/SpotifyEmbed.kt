package com.musely.app.download

import org.json.JSONArray
import org.json.JSONObject

/** One Spotify track as listed on its public embed page. */
data class SpotifyTrackInfo(
    val title: String,
    val artists: List<String>,
    val durationMs: Long,
)

/** What a Spotify embed page describes: a single track, or an album/playlist with its tracks. */
sealed interface SpotifyEntity {
    val name: String
    val imageUrl: String?

    data class Track(val info: SpotifyTrackInfo, override val imageUrl: String?) : SpotifyEntity {
        override val name: String get() = info.title
    }

    data class Collection(
        override val name: String,
        val owner: String,
        val isAlbum: Boolean,
        override val imageUrl: String?,
        val tracks: List<SpotifyTrackInfo>,
    ) : SpotifyEntity
}

/**
 * Reads Spotify's public embed page (`open.spotify.com/embed/...`). Spotify audio is DRM
 * protected, so Musely only takes the title, artists and duration from here and downloads the
 * matching song from YouTube Music.
 */
object SpotifyEmbedParser {

    private val nextDataPattern = Regex(
        """<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""",
        RegexOption.DOT_MATCHES_ALL,
    )

    /** Embed URL for a parsed Spotify link, or null for links that need resolving first. */
    fun embedUrl(link: MediaLink): String? = when (link) {
        is MediaLink.SpotifyTrack -> "https://open.spotify.com/embed/track/${link.id}"
        is MediaLink.SpotifyAlbum -> "https://open.spotify.com/embed/album/${link.id}"
        is MediaLink.SpotifyPlaylist -> "https://open.spotify.com/embed/playlist/${link.id}"
        else -> null
    }

    fun parse(html: String): SpotifyEntity? {
        val json = nextDataPattern.find(html)?.groupValues?.get(1) ?: return null
        val entity = runCatching {
            JSONObject(json)
                .getJSONObject("props")
                .getJSONObject("pageProps")
                .getJSONObject("state")
                .getJSONObject("data")
                .getJSONObject("entity")
        }.getOrNull() ?: return null
        val image = largestImage(entity.optJSONObject("visualIdentity")?.optJSONArray("image"))
        return when (entity.optString("type")) {
            "track" -> parseTrack(entity)?.let { SpotifyEntity.Track(it, image) }
            "album", "playlist" -> SpotifyEntity.Collection(
                name = entity.optString("name").ifBlank { entity.optString("title") },
                owner = entity.optString("subtitle"),
                isAlbum = entity.optString("type") == "album",
                imageUrl = image,
                tracks = parseTrackList(entity.optJSONArray("trackList")),
            )
            else -> null
        }
    }

    private fun parseTrack(entity: JSONObject): SpotifyTrackInfo? {
        val title = entity.optString("name").ifBlank { entity.optString("title") }.trim()
        if (title.isEmpty()) return null
        val artistsJson = entity.optJSONArray("artists")
        val artists = buildList {
            if (artistsJson != null) {
                for (i in 0 until artistsJson.length()) {
                    artistsJson.optJSONObject(i)?.optString("name")?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
                }
            }
        }
        return SpotifyTrackInfo(title, artists, entity.optLong("duration", 0L).coerceAtLeast(0L))
    }

    private fun parseTrackList(array: JSONArray?): List<SpotifyTrackInfo> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            val title = item.optString("title").trim()
            if (title.isEmpty()) return@mapNotNull null
            SpotifyTrackInfo(
                title = title,
                artists = splitArtists(item.optString("subtitle")),
                durationMs = item.optLong("duration", 0L).coerceAtLeast(0L),
            )
        }
    }

    /** Track list subtitles join artists with a comma and a non-breaking space. */
    fun splitArtists(subtitle: String): List<String> =
        subtitle.split(',')
            .map { it.replace(' ', ' ').trim() }
            .filter { it.isNotEmpty() }

    private fun largestImage(images: JSONArray?): String? {
        if (images == null) return null
        var best: String? = null
        var bestWidth = -1
        for (i in 0 until images.length()) {
            val image = images.optJSONObject(i) ?: continue
            val url = image.optString("url").takeIf { it.startsWith("https://") } ?: continue
            val width = image.optInt("maxWidth", 0)
            if (width > bestWidth) {
                best = url
                bestWidth = width
            }
        }
        return best
    }

    /** Finds an `open.spotify.com` track/album/playlist URL inside a short-link landing page. */
    fun findSpotifyUrl(html: String): String? =
        Regex("""https://open\.spotify\.com/(?:intl-[a-z-]+/)?(?:track|album|playlist)/[A-Za-z0-9]{22}""")
            .find(html)?.value
}
