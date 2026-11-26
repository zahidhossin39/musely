package com.musely.app.download

import org.json.JSONArray
import org.json.JSONObject

/** Message shown for downloads that were still running when the app was closed. */
const val INTERRUPTED_MESSAGE = "Stopped before it finished. Tap retry to try again."

/**
 * JSON form of the download list, stored in app-private storage so history and retry survive
 * restarts. Downloads that were in progress come back as failed so they can be retried.
 */
object DownloadHistoryCodec {

    private const val VERSION = 1
    private val uuidPattern = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    /** Ids name temp folders, so anything other than a UUID (such as a tampered backup) is rejected. */
    fun isValidId(id: String): Boolean = uuidPattern.matches(id)

    fun encode(items: List<DownloadItem>): String {
        val array = JSONArray()
        items.forEach { array.put(encodeItem(it)) }
        return JSONObject().put("version", VERSION).put("items", array).toString()
    }

    fun decode(raw: String): List<DownloadItem> {
        val array = runCatching { JSONObject(raw).optJSONArray("items") }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { runCatching { decodeItem(it) }.getOrNull() }
        }
    }

    private fun encodeItem(item: DownloadItem): JSONObject {
        val r = item.request
        val json = JSONObject()
            .put("id", r.id)
            .put("title", r.title)
            .put("artist", r.artist)
            .put("album", r.album ?: JSONObject.NULL)
            .put("thumbnail", r.thumbnailUrl ?: JSONObject.NULL)
            .put("durationMs", r.durationMs)
            .put("origin", r.origin.name)
            .put("format", r.format.name)
            .put("quality", r.quality.name)
            .put("playlistId", r.playlistId ?: JSONObject.NULL)
            .put("batchId", r.batchId ?: JSONObject.NULL)
            .put("createdAt", item.createdAtMs)
        when (val source = r.source) {
            is DownloadSource.YouTube -> json.put("sourceType", "youtube").put("sourceUrl", source.url)
            is DownloadSource.SpotifyMatch -> json.put("sourceType", "spotify")
                .put("spotifyTitle", source.track.title)
                .put("spotifyArtists", JSONArray(source.track.artists))
                .put("spotifyDurationMs", source.track.durationMs)
        }
        when (val state = item.state) {
            is DownloadState.Completed -> json.put("state", "completed")
                .put("trackId", state.trackId)
                .put("sizeBytes", state.sizeBytes)
                .put("finishedAt", state.finishedAtMs)
            is DownloadState.Failed -> json.put("state", "failed").put("error", state.message)
            DownloadState.Canceled -> json.put("state", "canceled")
            DownloadState.Queued, DownloadState.Preparing, is DownloadState.Downloading, DownloadState.Processing ->
                json.put("state", "interrupted")
        }
        return json
    }

    private fun decodeItem(json: JSONObject): DownloadItem {
        val source = when (json.getString("sourceType")) {
            "youtube" -> DownloadSource.YouTube(json.getString("sourceUrl"))
            else -> {
                val artists = json.optJSONArray("spotifyArtists")
                DownloadSource.SpotifyMatch(
                    SpotifyTrackInfo(
                        title = json.getString("spotifyTitle"),
                        artists = (0 until (artists?.length() ?: 0)).mapNotNull { artists?.optString(it) },
                        durationMs = json.optLong("spotifyDurationMs"),
                    ),
                )
            }
        }
        val id = json.getString("id")
        require(isValidId(id)) { "Invalid download id" }
        val request = DownloadRequest(
            id = id,
            title = json.getString("title"),
            artist = json.getString("artist"),
            album = json.optStringOrNull("album"),
            thumbnailUrl = json.optStringOrNull("thumbnail"),
            durationMs = json.optLong("durationMs"),
            source = source,
            origin = MediaSource.entries.firstOrNull { it.name == json.optString("origin") } ?: MediaSource.YouTube,
            format = DownloadFormat.fromName(json.optString("format")),
            quality = VideoQuality.fromName(json.optString("quality")),
            playlistId = json.optStringOrNull("playlistId"),
            batchId = json.optStringOrNull("batchId"),
        )
        val state = when (json.optString("state")) {
            "completed" -> DownloadState.Completed(
                trackId = json.getLong("trackId"),
                sizeBytes = json.optLong("sizeBytes"),
                finishedAtMs = json.optLong("finishedAt"),
            )
            "failed" -> DownloadState.Failed(json.optString("error").ifBlank { INTERRUPTED_MESSAGE })
            "canceled" -> DownloadState.Canceled
            else -> DownloadState.Failed(INTERRUPTED_MESSAGE)
        }
        return DownloadItem(request, state, json.optLong("createdAt"))
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
}
