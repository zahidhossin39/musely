package com.musely.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musely.app.MainViewModel
import com.musely.app.Playlist
import com.musely.app.R

/** The Playlists tab: Liked Music, the user's playlists, and the hidden-tracks shortcut. */
@Composable
internal fun PlaylistsScreen(
    vm: MainViewModel,
    gridState: LazyGridState,
    bottomInset: Dp,
    onOpenDetail: (LibraryDetail) -> Unit,
    onMessage: (message: String, actionLabel: String?, onAction: (() -> Unit)?) -> Unit,
) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val allVisibleTracks by vm.allVisibleTracks.collectAsStateWithLifecycle()
    val hiddenSongs by vm.hiddenSongs.collectAsStateWithLifecycle()
    val songsById = remember(allVisibleTracks) { allVisibleTracks.associateBy { it.id } }

    var showCreatePlaylist by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Playlist?>(null) }
    var deleteTarget by remember { mutableStateOf<Playlist?>(null) }
    var showHiddenSheet by remember { mutableStateOf(false) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = bottomInset + 24.dp),
    ) {
        fullWidth("playlists-header") {
            TabHeader(title = "Playlists", subtitle = playlistsSubtitle(playlists.count { !it.isSystem }))
        }
        fullWidth("playlist-actions") {
            PlaylistActionsBar(
                hiddenCount = hiddenSongs.size,
                onNewPlaylist = { showCreatePlaylist = true },
                onOpenHidden = { showHiddenSheet = true },
            )
        }
        items(playlists, key = { "playlist-${it.id}" }) { playlist ->
            val playlistSongs = remember(playlist.songIds, songsById) {
                playlist.songIds.mapNotNull { songsById[it] }
            }
            PlaylistCard(
                playlist = playlist,
                songs = playlistSongs,
                onClick = { onOpenDetail(playlistDetail(playlist, songsById)) },
                onRename = if (!playlist.isSystem) { { renameTarget = playlist } } else null,
                onDelete = if (!playlist.isSystem) { { deleteTarget = playlist } } else null,
            )
        }
    }

    if (showCreatePlaylist) {
        PlaylistNameDialog(
            title = "New playlist",
            confirmLabel = "Create",
            onDismiss = { showCreatePlaylist = false },
            onConfirm = { name ->
                showCreatePlaylist = false
                val created = vm.createPlaylist(name)
                if (created != null) {
                    onMessage("Created \"${created.name}\"", null, null)
                }
            },
        )
    }

    renameTarget?.let { target ->
        PlaylistNameDialog(
            title = "Rename playlist",
            initialName = target.name,
            confirmLabel = "Save",
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                renameTarget = null
                vm.renamePlaylist(target.id, name)
            },
        )
    }

    deleteTarget?.let { target ->
        ConfirmDeletePlaylistDialog(
            playlistName = target.name,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                deleteTarget = null
                vm.deletePlaylist(target.id)
                onMessage("Deleted \"${target.name}\"", null, null)
            },
        )
    }

    if (showHiddenSheet) {
        HiddenSongsSheet(
            hiddenSongs = hiddenSongs,
            onDismiss = { showHiddenSheet = false },
            onUnhideSong = { vm.unhideSong(it) },
            onUnhideAll = {
                vm.unhideAll()
                showHiddenSheet = false
            },
        )
    }
}

/** Supporting line under the Playlists title; counts only user-created playlists. */
internal fun playlistsSubtitle(userPlaylistCount: Int): String = when (userPlaylistCount) {
    0 -> "Liked Music lives here. Make a playlist of your own."
    1 -> "Liked Music and 1 playlist"
    else -> "Liked Music and $userPlaylistCount playlists"
}

@Composable
private fun PlaylistActionsBar(
    hiddenCount: Int,
    onNewPlaylist: () -> Unit,
    onOpenHidden: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ItemInset, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalButton(
            onClick = onNewPlaylist,
            shape = CircleShape,
        ) {
            Icon(
                painterResource(R.drawable.ic_add),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("New playlist")
        }
        if (hiddenCount > 0) {
            OutlinedButton(
                onClick = onOpenHidden,
                shape = CircleShape,
            ) {
                Icon(
                    painterResource(R.drawable.ic_visibility_off),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("Hidden ($hiddenCount)")
            }
        }
    }
}
