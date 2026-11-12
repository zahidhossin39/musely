package com.musely.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musely.app.Album
import com.musely.app.AlbumArt
import com.musely.app.Artist
import com.musely.app.MainViewModel
import com.musely.app.Playlist
import com.musely.app.R
import com.musely.app.Song
import com.musely.app.ui.theme.LocalSpotlight

enum class DetailKind { Album, Artist, Playlist }

/** An album, artist, or playlist the user drilled into. */
@Immutable
data class LibraryDetail(
    val kind: DetailKind,
    val title: String,
    val subtitle: String,
    val albumId: Long?,
    val songs: List<Song>,
    val playlistId: String? = null,
    val isSystemPlaylist: Boolean = false,
)

fun albumDetail(album: Album, songs: List<Song>): LibraryDetail {
    val albumSongs = songs.filter { it.albumId == album.id }.sortedBy { it.track }
    return LibraryDetail(
        kind = DetailKind.Album,
        title = album.title,
        subtitle = "${album.artist} · ${songCount(albumSongs.size)} · ${formatTotal(albumSongs)}",
        albumId = album.id,
        songs = albumSongs,
    )
}

fun artistDetail(name: String, songs: List<Song>): LibraryDetail {
    val artistSongs = songs.filter { it.artist == name }
    val albums = artistSongs.map { it.albumId }.distinct().size
    return LibraryDetail(
        kind = DetailKind.Artist,
        title = name,
        subtitle = "${songCount(artistSongs.size)} · ${albumCount(albums)} · ${formatTotal(artistSongs)}",
        albumId = null,
        songs = artistSongs,
    )
}

fun artistDetail(artist: Artist, songs: List<Song>): LibraryDetail =
    artistDetail(artist.name, songs)

fun playlistDetail(playlist: Playlist, visibleSongsById: Map<Long, Song>): LibraryDetail {
    val resolved = playlist.songIds.mapNotNull { visibleSongsById[it] }
    val subtitle = if (resolved.isEmpty()) {
        if (playlist.isSystem) "Starred tracks" else "Empty playlist"
    } else {
        "${songCount(resolved.size)} · ${formatTotal(resolved)}"
    }
    return LibraryDetail(
        kind = DetailKind.Playlist,
        title = playlist.name,
        subtitle = subtitle,
        albumId = null,
        songs = resolved,
        playlistId = playlist.id,
        isSystemPlaylist = playlist.isSystem,
    )
}

private val HeaderArtSize = 128.dp
private val PlaylistHeaderArtSize = 164.dp

@Composable
fun DetailScreen(
    detail: LibraryDetail,
    vm: MainViewModel,
    bottomInset: Dp,
    songActions: SongActionsUi,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onMessage: (message: String, actionLabel: String?, onAction: (() -> Unit)?) -> Unit = { _, _, _ -> },
) {
    val playback by vm.playback.collectAsStateWithLifecycle()
    val starredIds by vm.starredIds.collectAsStateWithLifecycle()
    val allVisibleSongs by vm.songs.collectAsStateWithLifecycle()

    var showAddSongs by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val isCustomPlaylist = detail.kind == DetailKind.Playlist &&
        !detail.isSystemPlaylist &&
        detail.playlistId != null
    val visibleSongIds = remember(detail.songs) { detail.songs.map { it.id } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomInset + 24.dp),
    ) {
        item(key = "back") {
            IconButton(onClick = onBack, modifier = Modifier.padding(vertical = 4.dp)) {
                Icon(painterResource(R.drawable.ic_back), "Back to library")
            }
        }
        item(key = "header") { Header(detail) }
        if (detail.songs.isNotEmpty()) {
            item(key = "actions") {
                Actions(
                    onPlay = { vm.playAll(detail.songs) },
                    onShuffle = { vm.shuffle(detail.songs) },
                )
            }
        }
        if (isCustomPlaylist) {
            item(key = "playlistControls") {
                CustomPlaylistControls(
                    onAddSongs = { showAddSongs = true },
                    onRename = { showRenameDialog = true },
                    onDelete = { showDeleteDialog = true },
                )
            }
        }
        if (detail.kind == DetailKind.Playlist && detail.songs.isEmpty()) {
            item(key = "emptyPlaylist") {
                EmptyPlaylistState(
                    playlistName = detail.title,
                    isSystem = detail.isSystemPlaylist,
                    onAddSongs = if (isCustomPlaylist) ({ showAddSongs = true }) else null,
                )
            }
        } else {
            itemsIndexed(detail.songs, key = { _, song -> song.id }) { index, song ->
                val row = @Composable { rowModifier: Modifier ->
                    SongRow(
                        song = song,
                        isCurrent = playback.song?.id == song.id,
                        isPlaying = playback.isPlaying,
                        onClick = { vm.play(detail.songs, index) },
                        modifier = rowModifier,
                        showArt = detail.kind != DetailKind.Album,
                        isStarred = song.id in starredIds,
                        onToggleStar = { songActions.toggleStar(song) },
                        onMoreClick = { songActions.openMenu(song, detail.playlistId) },
                    )
                }
                if (isCustomPlaylist) {
                    val playlistId = detail.playlistId
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        row(Modifier.weight(1f))
                        ReorderControls(
                            canMoveUp = index > 0,
                            canMoveDown = index < detail.songs.lastIndex,
                            onMoveUp = {
                                vm.moveSongInPlaylist(playlistId, visibleSongIds, index, index - 1)
                            },
                            onMoveDown = {
                                vm.moveSongInPlaylist(playlistId, visibleSongIds, index, index + 1)
                            },
                        )
                    }
                } else {
                    row(Modifier.fillMaxWidth())
                }
            }
        }
    }

    if (showAddSongs && isCustomPlaylist) {
        val playlistId = detail.playlistId
        val availableSongs = remember(allVisibleSongs, detail.songs) {
            val existingIds = detail.songs.mapTo(HashSet()) { it.id }
            allVisibleSongs.filter { it.id !in existingIds }
        }
        AddSongsPickerSheet(
            playlistName = detail.title,
            availableSongs = availableSongs,
            onDismiss = { showAddSongs = false },
            onAddSelected = { selectedIds ->
                if (selectedIds.isNotEmpty() && vm.addSongsToPlaylist(playlistId, selectedIds)) {
                    onMessage("Added ${songCount(selectedIds.size)} to ${detail.title}", null, null)
                }
                showAddSongs = false
            },
        )
    }

    if (showRenameDialog && isCustomPlaylist) {
        val playlistId = detail.playlistId
        PlaylistNameDialog(
            title = "Rename playlist",
            initialName = detail.title,
            confirmLabel = "Save",
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                val trimmed = newName.trim()
                if (trimmed.isNotEmpty() && vm.renamePlaylist(playlistId, trimmed)) {
                    onMessage("Renamed playlist to $trimmed", null, null)
                }
                showRenameDialog = false
            },
        )
    }

    if (showDeleteDialog && isCustomPlaylist) {
        val playlistId = detail.playlistId
        ConfirmDeletePlaylistDialog(
            playlistName = detail.title,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                val deleted = vm.deletePlaylist(playlistId)
                showDeleteDialog = false
                if (deleted) {
                    onMessage("Deleted ${detail.title}", null, null)
                    onBack()
                }
            },
        )
    }
}

@Composable
private fun Header(detail: LibraryDetail) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = ItemInset, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (detail.kind) {
            DetailKind.Playlist -> {
                val songIds = remember(detail.songs) { detail.songs.map { it.id } }
                val albumIds = remember(detail.songs) { detail.songs.map { it.albumId } }
                PlaylistArtwork(
                    songIds = songIds,
                    albumIds = albumIds,
                    isStarred = detail.isSystemPlaylist,
                    modifier = Modifier.size(PlaylistHeaderArtSize),
                    corner = 24.dp,
                )
            }
            DetailKind.Album -> {
                val songIds = remember(detail.songs) { detail.songs.map { it.id } }
                if (detail.albumId != null) {
                    AlbumArt(
                        detail.albumId,
                        Modifier.size(HeaderArtSize),
                        fallbackSongIds = songIds,
                        corner = 24.dp,
                    )
                } else {
                    InitialAvatar(detail.title, Modifier.size(HeaderArtSize))
                }
            }
            DetailKind.Artist -> {
                InitialAvatar(detail.title, Modifier.size(HeaderArtSize))
            }
        }
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            val kindLabel = when (detail.kind) {
                DetailKind.Album -> "ALBUM"
                DetailKind.Artist -> "ARTIST"
                DetailKind.Playlist -> if (detail.isSystemPlaylist) "COLLECTION" else "PLAYLIST"
            }
            Text(
                kindLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                detail.title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                detail.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Actions(onPlay: () -> Unit, onShuffle: () -> Unit) {
    val spotlight = LocalSpotlight.current
    Row(
        Modifier.fillMaxWidth().padding(start = ItemInset, end = ItemInset, top = 20.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onPlay,
            modifier = Modifier.weight(1f).height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = spotlight.container, contentColor = spotlight.content),
        ) {
            Icon(painterResource(R.drawable.ic_play), null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Play")
        }
        FilledTonalButton(onClick = onShuffle, modifier = Modifier.weight(1f).height(52.dp)) {
            Icon(painterResource(R.drawable.ic_shuffle), null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Shuffle")
        }
    }
}

@Composable
private fun CustomPlaylistControls(
    onAddSongs: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = ItemInset, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalButton(
            onClick = onAddSongs,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Icon(painterResource(R.drawable.ic_add), null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add songs", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        OutlinedButton(
            onClick = onRename,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(painterResource(R.drawable.ic_edit), null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Rename", maxLines = 1)
        }
        OutlinedButton(
            onClick = onDelete,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(painterResource(R.drawable.ic_delete), null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Delete", maxLines = 1)
        }
    }
}

@Composable
private fun EmptyPlaylistState(
    playlistName: String,
    isSystem: Boolean,
    onAddSongs: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    val spotlight = LocalSpotlight.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(colors.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(if (isSystem) R.drawable.ic_star_outline else R.drawable.ic_playlist_play),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = colors.primary,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            if (isSystem) "No liked songs yet" else "Start building $playlistName",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (isSystem) {
                "Tap the star on any song in your library or player to collect your favorites here."
            } else {
                "Add songs from your library to play and reorder them here."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (onAddSongs != null) {
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onAddSongs,
                colors = ButtonDefaults.buttonColors(
                    containerColor = spotlight.container,
                    contentColor = spotlight.content,
                ),
            ) {
                Icon(painterResource(R.drawable.ic_add), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add songs")
            }
        }
    }
}

@Composable
private fun ReorderControls(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onMoveUp,
            enabled = canMoveUp,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_arrow_up),
                contentDescription = "Move up",
                modifier = Modifier.size(18.dp),
                tint = if (canMoveUp) colors.onSurfaceVariant else colors.onSurfaceVariant.copy(alpha = 0.28f),
            )
        }
        IconButton(
            onClick = onMoveDown,
            enabled = canMoveDown,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_arrow_down),
                contentDescription = "Move down",
                modifier = Modifier.size(18.dp),
                tint = if (canMoveDown) colors.onSurfaceVariant else colors.onSurfaceVariant.copy(alpha = 0.28f),
            )
        }
    }
}
