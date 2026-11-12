package com.musely.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Playlist(
    val id: String,
    val name: String,
    val songIds: List<Long>,
    val isSystem: Boolean = false,
    val createdAtMs: Long = 0L,
)

data class LibraryUserState(
    val playlists: List<Playlist> = emptyList(),
    val starredSongIds: List<Long> = emptyList(),
    val hiddenSongIds: Set<Long> = emptySet(),
)

private const val STATE_JSON_VERSION = 1

/**
 * Serializes [state] into a UTF-8 JSON string using standard `org.json` types.
 */
fun encodeStateJson(state: LibraryUserState): String {
    val root = JSONObject()
    root.put("version", STATE_JSON_VERSION)

    val playlistsArray = JSONArray()
    for (playlist in state.playlists) {
        if (playlist.isSystem || playlist.id == PlaylistStore.STARRED_PLAYLIST_ID) continue
        val item = JSONObject()
        item.put("id", playlist.id)
        item.put("name", playlist.name)
        item.put("isSystem", false)
        item.put("createdAtMs", playlist.createdAtMs)
        val songIdsArray = JSONArray()
        for (songId in playlist.songIds.distinct()) {
            songIdsArray.put(songId)
        }
        item.put("songIds", songIdsArray)
        playlistsArray.put(item)
    }
    root.put("playlists", playlistsArray)

    val starredArray = JSONArray()
    for (songId in state.starredSongIds.distinct()) {
        starredArray.put(songId)
    }
    root.put("starredSongIds", starredArray)

    val hiddenArray = JSONArray()
    for (songId in state.hiddenSongIds) {
        hiddenArray.put(songId)
    }
    root.put("hiddenSongIds", hiddenArray)

    return root.toString()
}

/**
 * Parses [raw] JSON into a [LibraryUserState].
 * Returns an empty [LibraryUserState] when [raw] is blank or malformed.
 */
fun decodeStateJson(raw: String): LibraryUserState {
    if (raw.isBlank()) return LibraryUserState()
    return try {
        val root = JSONObject(raw)
        val playlists = parsePlaylists(root.optJSONArray("playlists"))
        val starredSongIds = parseLongList(root.optJSONArray("starredSongIds")).distinct()
        val hiddenSongIds = parseLongList(root.optJSONArray("hiddenSongIds")).toSet()
        LibraryUserState(
            playlists = playlists,
            starredSongIds = starredSongIds,
            hiddenSongIds = hiddenSongIds,
        )
    } catch (_: Throwable) {
        LibraryUserState()
    }
}

private fun parsePlaylists(array: JSONArray?): List<Playlist> {
    if (array == null) return emptyList()
    val result = ArrayList<Playlist>(array.length())
    val seenIds = HashSet<String>()
    for (i in 0 until array.length()) {
        val obj = array.optJSONObject(i) ?: continue
        val id = obj.optString("id", "").trim()
        val name = obj.optString("name", "").trim()
        val isSystem = obj.optBoolean("isSystem", false)
        if (id.isEmpty() || name.isEmpty() || isSystem || id == PlaylistStore.STARRED_PLAYLIST_ID) {
            continue
        }
        if (!seenIds.add(id)) continue
        val createdAtMs = obj.optLong("createdAtMs", 0L).coerceAtLeast(0L)
        val songIds = parseLongList(obj.optJSONArray("songIds")).distinct()
        result += Playlist(
            id = id,
            name = name,
            songIds = songIds,
            isSystem = false,
            createdAtMs = createdAtMs,
        )
    }
    return result
}

private fun parseLongList(array: JSONArray?): List<Long> {
    if (array == null) return emptyList()
    val result = ArrayList<Long>(array.length())
    for (i in 0 until array.length()) {
        val value = array.opt(i)
        val id = when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
        if (id != null && id >= 0L) {
            result += id
        }
    }
    return result
}

/**
 * Returns the pinned system "Liked Music" playlist first (backed by [LibraryUserState.starredSongIds]),
 * followed by all custom playlists in [state].
 */
fun buildPlaylistsWithStarred(state: LibraryUserState): List<Playlist> {
    val starred = Playlist(
        id = PlaylistStore.STARRED_PLAYLIST_ID,
        name = PlaylistStore.STARRED_PLAYLIST_NAME,
        songIds = state.starredSongIds,
        isSystem = true,
        createdAtMs = 0L,
    )
    val custom = state.playlists.filter { !it.isSystem && it.id != PlaylistStore.STARRED_PLAYLIST_ID }
    return listOf(starred) + custom
}

/**
 * Thread-safe, atomically-persisted store for user playlists, starred songs, and hidden songs.
 *
 * Ordering policy for [LibraryUserState.starredSongIds]: newly starred songs are prepended to the
 * front of the list so the most recently liked track appears first in "Liked Music".
 */
class PlaylistStore private constructor(
    private val directory: File,
    private val fileName: String,
    private val clock: () -> Long,
) {
    constructor(
        context: Context,
        fileName: String = DEFAULT_FILE_NAME,
    ) : this(context.filesDir, fileName, System::currentTimeMillis)

    internal constructor(
        directory: File,
        fileName: String = DEFAULT_FILE_NAME,
        clock: () -> Long = System::currentTimeMillis,
        @Suppress("UNUSED_PARAMETER") testMarker: Unit = Unit,
    ) : this(directory, fileName, clock)

    private val lock = Any()
    private val stateFile: File get() = File(directory, fileName)
    private var cachedState: LibraryUserState? = null

    fun loadState(): LibraryUserState = synchronized(lock) {
        loadLocked()
    }

    fun createPlaylist(name: String): Playlist? = synchronized(lock) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val current = loadLocked()
        val created = Playlist(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            songIds = emptyList(),
            isSystem = false,
            createdAtMs = clock(),
        )
        saveLocked(current.copy(playlists = current.playlists + created))
        created
    }

    fun renamePlaylist(playlistId: String, newName: String): Boolean = synchronized(lock) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || playlistId == STARRED_PLAYLIST_ID) return false
        val current = loadLocked()
        val index = current.playlists.indexOfFirst { it.id == playlistId && !it.isSystem }
        if (index < 0) return false
        val target = current.playlists[index]
        if (target.name == trimmed) return true
        val updated = current.playlists.toMutableList()
        updated[index] = target.copy(name = trimmed)
        saveLocked(current.copy(playlists = updated))
        true
    }

    fun deletePlaylist(playlistId: String): Boolean = synchronized(lock) {
        if (playlistId == STARRED_PLAYLIST_ID) return false
        val current = loadLocked()
        val filtered = current.playlists.filterNot { it.id == playlistId && !it.isSystem }
        if (filtered.size == current.playlists.size) return false
        saveLocked(current.copy(playlists = filtered))
        true
    }

    fun addSongToPlaylist(playlistId: String, songId: Long): Boolean =
        addSongsToPlaylist(playlistId, listOf(songId))

    fun addSongsToPlaylist(playlistId: String, songIds: List<Long>): Boolean = synchronized(lock) {
        if (songIds.isEmpty()) return false
        val current = loadLocked()
        if (playlistId == STARRED_PLAYLIST_ID) {
            val existing = current.starredSongIds.toHashSet()
            val toAdd = songIds.distinct().filter { it !in existing }
            if (toAdd.isEmpty()) return false
            // Prepend newly starred songs (most recently added first).
            val updatedStarred = toAdd.reversed() + current.starredSongIds
            saveLocked(current.copy(starredSongIds = updatedStarred))
            return true
        }
        val index = current.playlists.indexOfFirst { it.id == playlistId }
        if (index < 0) return false
        val target = current.playlists[index]
        val existing = target.songIds.toHashSet()
        val toAdd = songIds.distinct().filter { it !in existing }
        if (toAdd.isEmpty()) return false
        val updated = current.playlists.toMutableList()
        updated[index] = target.copy(songIds = target.songIds + toAdd)
        saveLocked(current.copy(playlists = updated))
        true
    }

    fun removeSongFromPlaylist(playlistId: String, index: Int): Boolean = synchronized(lock) {
        val current = loadLocked()
        if (playlistId == STARRED_PLAYLIST_ID) {
            if (index !in current.starredSongIds.indices) return false
            val updatedStarred = current.starredSongIds.toMutableList().apply { removeAt(index) }
            saveLocked(current.copy(starredSongIds = updatedStarred))
            return true
        }
        val playlistIndex = current.playlists.indexOfFirst { it.id == playlistId }
        if (playlistIndex < 0) return false
        val target = current.playlists[playlistIndex]
        if (index !in target.songIds.indices) return false
        val updatedSongs = target.songIds.toMutableList().apply { removeAt(index) }
        val updatedPlaylists = current.playlists.toMutableList()
        updatedPlaylists[playlistIndex] = target.copy(songIds = updatedSongs)
        saveLocked(current.copy(playlists = updatedPlaylists))
        true
    }

    fun removeSongIdFromPlaylist(playlistId: String, songId: Long): Boolean = synchronized(lock) {
        val current = loadLocked()
        if (playlistId == STARRED_PLAYLIST_ID) {
            val updatedStarred = current.starredSongIds.filterNot { it == songId }
            if (updatedStarred.size == current.starredSongIds.size) return false
            saveLocked(current.copy(starredSongIds = updatedStarred))
            return true
        }
        val playlistIndex = current.playlists.indexOfFirst { it.id == playlistId }
        if (playlistIndex < 0) return false
        val target = current.playlists[playlistIndex]
        val updatedSongs = target.songIds.filterNot { it == songId }
        if (updatedSongs.size == target.songIds.size) return false
        val updatedPlaylists = current.playlists.toMutableList()
        updatedPlaylists[playlistIndex] = target.copy(songIds = updatedSongs)
        saveLocked(current.copy(playlists = updatedPlaylists))
        true
    }

    fun moveSongInPlaylist(playlistId: String, fromIndex: Int, toIndex: Int): Boolean = synchronized(lock) {
        val current = loadLocked()
        if (playlistId == STARRED_PLAYLIST_ID) {
            if (fromIndex !in current.starredSongIds.indices ||
                toIndex !in current.starredSongIds.indices ||
                fromIndex == toIndex
            ) {
                return false
            }
            val updated = current.starredSongIds.toMutableList()
            val moved = updated.removeAt(fromIndex)
            updated.add(toIndex, moved)
            saveLocked(current.copy(starredSongIds = updated))
            return true
        }
        val playlistIndex = current.playlists.indexOfFirst { it.id == playlistId }
        if (playlistIndex < 0) return false
        val target = current.playlists[playlistIndex]
        if (fromIndex !in target.songIds.indices ||
            toIndex !in target.songIds.indices ||
            fromIndex == toIndex
        ) {
            return false
        }
        val updatedSongs = target.songIds.toMutableList()
        val moved = updatedSongs.removeAt(fromIndex)
        updatedSongs.add(toIndex, moved)
        val updatedPlaylists = current.playlists.toMutableList()
        updatedPlaylists[playlistIndex] = target.copy(songIds = updatedSongs)
        saveLocked(current.copy(playlists = updatedPlaylists))
        true
    }

    /**
     * Toggles the starred state of [songId] and returns the new `isStarred` value.
     * Newly starred songs are prepended to the front of [LibraryUserState.starredSongIds].
     */
    fun toggleStar(songId: Long): Boolean = synchronized(lock) {
        val current = loadLocked()
        val isCurrentlyStarred = songId in current.starredSongIds
        val updatedStarred = if (isCurrentlyStarred) {
            current.starredSongIds.filterNot { it == songId }
        } else {
            listOf(songId) + current.starredSongIds
        }
        saveLocked(current.copy(starredSongIds = updatedStarred))
        !isCurrentlyStarred
    }

    fun setStarred(songId: Long, starred: Boolean) = synchronized(lock) {
        val current = loadLocked()
        val isCurrentlyStarred = songId in current.starredSongIds
        if (starred == isCurrentlyStarred) return@synchronized
        val updatedStarred = if (starred) {
            listOf(songId) + current.starredSongIds
        } else {
            current.starredSongIds.filterNot { it == songId }
        }
        saveLocked(current.copy(starredSongIds = updatedStarred))
    }

    fun hideSong(songId: Long) = synchronized(lock) {
        val current = loadLocked()
        if (songId in current.hiddenSongIds) return@synchronized
        saveLocked(current.copy(hiddenSongIds = current.hiddenSongIds + songId))
    }

    fun unhideSong(songId: Long) = synchronized(lock) {
        val current = loadLocked()
        if (songId !in current.hiddenSongIds) return@synchronized
        saveLocked(current.copy(hiddenSongIds = current.hiddenSongIds - songId))
    }

    fun unhideAll() = synchronized(lock) {
        val current = loadLocked()
        if (current.hiddenSongIds.isEmpty()) return@synchronized
        saveLocked(current.copy(hiddenSongIds = emptySet()))
    }

    fun pruneSongs(existingSongIds: Set<Long>): Boolean = synchronized(lock) {
        val current = loadLocked()
        val newStarred = current.starredSongIds.filter { it in existingSongIds }
        val newHidden = current.hiddenSongIds.filter { it in existingSongIds }.toSet()
        val newPlaylists = current.playlists.map { playlist ->
            val filtered = playlist.songIds.filter { it in existingSongIds }
            if (filtered.size == playlist.songIds.size) playlist else playlist.copy(songIds = filtered)
        }
        val next = LibraryUserState(
            playlists = newPlaylists,
            starredSongIds = newStarred,
            hiddenSongIds = newHidden,
        )
        if (next == current) return false
        saveLocked(next)
        true
    }

    fun removeDeletedSong(songId: Long): Boolean = synchronized(lock) {
        val current = loadLocked()
        val newStarred = current.starredSongIds.filterNot { it == songId }
        val newHidden = current.hiddenSongIds - songId
        val newPlaylists = current.playlists.map { playlist ->
            val filtered = playlist.songIds.filterNot { it == songId }
            if (filtered.size == playlist.songIds.size) playlist else playlist.copy(songIds = filtered)
        }
        val next = LibraryUserState(
            playlists = newPlaylists,
            starredSongIds = newStarred,
            hiddenSongIds = newHidden,
        )
        if (next == current) return false
        saveLocked(next)
        true
    }

    private fun loadLocked(): LibraryUserState {
        cachedState?.let { return it }
        val file = stateFile
        val loaded = if (file.exists()) {
            runCatching { decodeStateJson(file.readText(Charsets.UTF_8)) }
                .getOrDefault(LibraryUserState())
        } else {
            LibraryUserState()
        }
        cachedState = loaded
        return loaded
    }

    private fun saveLocked(state: LibraryUserState) {
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val target = stateFile
        val temp = File(directory, "$fileName.tmp")
        temp.writeText(encodeStateJson(state), Charsets.UTF_8)
        if (!temp.renameTo(target)) {
            if (target.exists()) {
                target.delete()
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        }
        cachedState = state
    }

    companion object {
        const val STARRED_PLAYLIST_ID = "__starred__"
        const val STARRED_PLAYLIST_NAME = "Liked Music"
        const val DEFAULT_FILE_NAME = "musely_library_state.json"
    }
}
