package com.musely.app.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musely.app.AlbumArt
import com.musely.app.DeleteSongOutcome
import com.musely.app.MainViewModel
import com.musely.app.Playlist
import com.musely.app.PlaylistStore
import com.musely.app.R
import com.musely.app.Song
import com.musely.app.SongFileInfo
import com.musely.app.search
import com.musely.app.ui.theme.LocalSpotlight
import kotlinx.coroutines.launch

@Immutable
class SongActionsUi(
    val openMenu: (song: Song, playlistId: String?) -> Unit,
    val toggleStar: (Song) -> Unit,
    val openAddToPlaylist: (Song) -> Unit,
    val openFileInfo: (Song) -> Unit,
    val hideSong: (Song) -> Unit,
    val deleteSong: (Song) -> Unit,
)

@Composable
fun rememberSongActionsUi(
    vm: MainViewModel,
    onMessage: (message: String, actionLabel: String?, onAction: (() -> Unit)?) -> Unit,
): SongActionsUi {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val starredIds by vm.starredIds.collectAsStateWithLifecycle()
    val visibleTracks by vm.allVisibleTracks.collectAsStateWithLifecycle()
    val songsById = remember(visibleTracks) {
        visibleTracks.associateBy { it.id }
    }

    var menuTarget by remember { mutableStateOf<Pair<Song, String?>?>(null) }
    var addToPlaylistSong by remember { mutableStateOf<Song?>(null) }
    var fileInfoSong by remember { mutableStateOf<Song?>(null) }
    var confirmDeleteSong by remember { mutableStateOf<Song?>(null) }
    var pendingPermissionSong by remember { mutableStateOf<Song?>(null) }
    var activeIntentSong by remember { mutableStateOf<Song?>(null) }

    val intentSenderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val song = activeIntentSong
        activeIntentSong = null
        if (result.resultCode == Activity.RESULT_OK && song != null) {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
                scope.launch {
                    when (val outcome = vm.requestDeleteSong(song)) {
                        DeleteSongOutcome.Deleted -> {
                            onMessage("Deleted \"${song.title}\"", null, null)
                        }
                        is DeleteSongOutcome.RequiresConfirmation -> {
                            onMessage("Could not delete \"${song.title}\"", null, null)
                        }
                        is DeleteSongOutcome.Failed -> {
                            onMessage(outcome.message, null, null)
                        }
                    }
                }
            } else {
                vm.onSongDeleted(song.id)
                onMessage("Deleted \"${song.title}\"", null, null)
            }
        }
    }

    fun executeDelete(song: Song) {
        scope.launch {
            when (val outcome = vm.requestDeleteSong(song)) {
                DeleteSongOutcome.Deleted -> {
                    onMessage("Deleted \"${song.title}\"", null, null)
                }
                is DeleteSongOutcome.RequiresConfirmation -> {
                    activeIntentSong = song
                    intentSenderLauncher.launch(
                        IntentSenderRequest.Builder(outcome.intentSender).build(),
                    )
                }
                is DeleteSongOutcome.Failed -> {
                    onMessage(outcome.message, null, null)
                }
            }
        }
    }

    val writePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val song = pendingPermissionSong
        pendingPermissionSong = null
        if (song != null) {
            if (granted) {
                confirmDeleteSong = song
            } else {
                onMessage(
                    "Storage permission is required to delete ${if (song.isVideo) "videos" else "songs"}",
                    null,
                    null,
                )
            }
        }
    }

    fun startDeleteFlow(song: Song) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            executeDelete(song)
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            confirmDeleteSong = song
        } else {
            val hasWrite = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
            if (hasWrite) {
                confirmDeleteSong = song
            } else {
                pendingPermissionSong = song
                writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    val actions = remember(vm, onMessage) {
        SongActionsUi(
            openMenu = { song, playlistId -> menuTarget = song to playlistId },
            toggleStar = { song ->
                val starred = vm.toggleStar(song.id)
                onMessage(
                    if (starred) "Added to Liked Music" else "Removed from Liked Music",
                    null,
                    null,
                )
            },
            openAddToPlaylist = { song -> addToPlaylistSong = song },
            openFileInfo = { song -> fileInfoSong = song },
            hideSong = { song ->
                vm.hideSong(song.id)
                onMessage("Hidden \"${song.title}\"", "Undo") { vm.unhideSong(song.id) }
            },
            deleteSong = { song -> startDeleteFlow(song) },
        )
    }

    menuTarget?.let { (song, playlistId) ->
        SongActionSheet(
            song = song,
            playlistId = playlistId,
            isStarred = song.id in starredIds,
            onDismiss = { menuTarget = null },
            onToggleStar = {
                menuTarget = null
                actions.toggleStar(song)
            },
            onAddToPlaylist = {
                menuTarget = null
                addToPlaylistSong = song
            },
            onRemoveFromPlaylist = if (playlistId != null && playlistId != PlaylistStore.STARRED_PLAYLIST_ID) {
                {
                    menuTarget = null
                    if (vm.removeSongIdFromPlaylist(playlistId, song.id)) {
                        onMessage("Removed from playlist", null, null)
                    }
                }
            } else {
                null
            },
            onFileInfo = {
                menuTarget = null
                fileInfoSong = song
            },
            onHide = {
                menuTarget = null
                actions.hideSong(song)
            },
            onDelete = {
                menuTarget = null
                startDeleteFlow(song)
            },
        )
    }

    addToPlaylistSong?.let { song ->
        AddToPlaylistSheet(
            song = song,
            playlists = playlists,
            songsById = songsById,
            starredIds = starredIds,
            onDismiss = { addToPlaylistSong = null },
            onTogglePlaylist = { playlist ->
                if (playlist.isSystem || playlist.id == PlaylistStore.STARRED_PLAYLIST_ID) {
                    val nowStarred = song.id !in starredIds
                    vm.setStarred(song.id, nowStarred)
                } else if (song.id in playlist.songIds) {
                    vm.removeSongIdFromPlaylist(playlist.id, song.id)
                } else {
                    vm.addSongToPlaylist(playlist.id, song.id)
                }
            },
            onCreateAndAdd = { name ->
                val created = vm.createPlaylist(name)
                if (created != null) {
                    vm.addSongToPlaylist(created.id, song.id)
                    onMessage("Added to \"${created.name}\"", null, null)
                }
            },
        )
    }

    fileInfoSong?.let { song ->
        FileInfoSheet(
            song = song,
            loadInfo = { vm.loadFileInfo(song) },
            onDismiss = { fileInfoSong = null },
        )
    }

    confirmDeleteSong?.let { song ->
        ConfirmDeleteSongDialog(
            songTitle = song.title,
            isVideo = song.isVideo,
            onDismiss = { confirmDeleteSong = null },
            onConfirm = {
                confirmDeleteSong = null
                executeDelete(song)
            },
        )
    }

    return actions
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongActionSheet(
    song: Song,
    playlistId: String?,
    isStarred: Boolean,
    onDismiss: () -> Unit,
    onToggleStar: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onRemoveFromPlaylist: (() -> Unit)?,
    onFileInfo: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = MaterialTheme.colorScheme
    val spotlight = LocalSpotlight.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AlbumArt(
                    song.albumId,
                    Modifier.size(48.dp),
                    songId = song.id,
                    isVideo = song.isVideo,
                    corner = 12.dp,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        song.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TrackSubtitleRow(
                        song = song,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = colors.outlineVariant.copy(alpha = 0.5f),
            )
            SheetActionRow(
                iconRes = if (isStarred) R.drawable.ic_star else R.drawable.ic_star_outline,
                label = if (isStarred) "Remove from Liked Music" else "Add to Liked Music",
                tint = if (isStarred) spotlight.container else colors.onSurface,
                onClick = onToggleStar,
            )
            SheetActionRow(
                iconRes = R.drawable.ic_playlist_add,
                label = "Add to playlist",
                onClick = onAddToPlaylist,
            )
            if (onRemoveFromPlaylist != null && playlistId != null) {
                SheetActionRow(
                    iconRes = R.drawable.ic_close,
                    label = "Remove from playlist",
                    onClick = onRemoveFromPlaylist,
                )
            }
            SheetActionRow(
                iconRes = R.drawable.ic_info,
                label = "File info",
                onClick = onFileInfo,
            )
            SheetActionRow(
                iconRes = R.drawable.ic_visibility_off,
                label = "Hide from library",
                onClick = onHide,
            )
            SheetActionRow(
                iconRes = R.drawable.ic_delete,
                label = "Delete from device",
                tint = colors.error,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun SheetActionRow(
    iconRes: Int,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = tint,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToPlaylistSheet(
    song: Song,
    playlists: List<Playlist>,
    songsById: Map<Long, Song>,
    starredIds: Set<Long>,
    onDismiss: () -> Unit,
    onTogglePlaylist: (Playlist) -> Unit,
    onCreateAndAdd: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = MaterialTheme.colorScheme
    val spotlight = LocalSpotlight.current
    var creatingNew by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Add to playlist",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { creatingNew = !creatingNew }) {
                    Icon(
                        painterResource(R.drawable.ic_add),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("New playlist")
                }
            }

            if (creatingNew) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = { Text("Playlist name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            val trimmed = newName.trim()
                            if (trimmed.isNotEmpty()) {
                                onCreateAndAdd(trimmed)
                                newName = ""
                                creatingNew = false
                            }
                        },
                        enabled = newName.trim().isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = spotlight.container,
                            contentColor = spotlight.content,
                        ),
                    ) {
                        Text("Create")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    val resolvedSongs = remember(playlist.songIds, songsById) {
                        playlist.songIds.mapNotNull { songsById[it] }
                    }
                    val isStarredPlaylist = playlist.isSystem || playlist.id == PlaylistStore.STARRED_PLAYLIST_ID
                    val isPresent = if (isStarredPlaylist) {
                        song.id in starredIds
                    } else {
                        song.id in playlist.songIds
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTogglePlaylist(playlist) }
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlaylistArtwork(
                            songIds = resolvedSongs.map { it.id },
                            albumIds = resolvedSongs.map { it.albumId },
                            isStarred = isStarredPlaylist,
                            modifier = Modifier.size(48.dp),
                            corner = 12.dp,
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                playlist.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                collectionSummary(resolvedSongs),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        if (isPresent) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = "Added",
                                modifier = Modifier.size(22.dp),
                                tint = spotlight.container,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileInfoSheet(
    song: Song,
    loadInfo: suspend () -> SongFileInfo,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = MaterialTheme.colorScheme
    var info by remember(song.id) { mutableStateOf<SongFileInfo?>(null) }

    LaunchedEffect(song.id) {
        info = loadInfo()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
        ) {
            Text("File info", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                song.title,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            val loaded = info
            if (loaded == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else {
                Surface(
                    color = colors.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FileInfoRow("File name", loaded.fileName)
                        FileInfoRow("Folder / Path", loaded.pathOrRelative.ifBlank { "Internal storage" })
                        if (song.isVideo) {
                            FileInfoRow("Source", "Video file (audio-only playback)")
                            val audioTrackLabel = when (loaded.hasAudioTrack) {
                                true -> "Detected"
                                false -> "None detected"
                                null -> "Unknown"
                            }
                            FileInfoRow("Audio track", audioTrackLabel)
                        }
                        FileInfoRow("Format", loaded.mimeType.ifBlank { "Unknown" })
                        FileInfoRow("Size", formatFileSize(loaded.sizeBytes))
                        FileInfoRow("Duration", formatTime(loaded.durationMs))
                        FileInfoRow("Bitrate", formatBitrate(loaded.bitrateBps) ?: "Unknown")
                        FileInfoRow("Sample rate", formatSampleRate(loaded.sampleRateHz) ?: "Unknown")
                        FileInfoRow("Modified", formatDateSeconds(loaded.dateModifiedSec).ifBlank { "Unknown" })
                    }
                }
            }
        }
    }
}

@Composable
private fun FileInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(104.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ConfirmDeleteSongDialog(
    songTitle: String,
    isVideo: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isVideo) "Delete video?" else "Delete song?") },
        text = { Text("“$songTitle” will be permanently deleted from your device.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun PlaylistNameDialog(
    title: String,
    initialName: String = "",
    confirmLabel: String = "Save",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    val trimmed = name.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Playlist name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (trimmed.isNotEmpty()) onConfirm(trimmed) },
                enabled = trimmed.isNotEmpty(),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun ConfirmDeletePlaylistDialog(
    playlistName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete playlist?") },
        text = { Text("Delete “$playlistName”? The media files on your device won't be removed.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private enum class PickerFilter(val label: String) {
    All("All"),
    Songs("Songs"),
    Videos("Videos"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSongsPickerSheet(
    playlistName: String,
    availableSongs: List<Song>,
    onDismiss: () -> Unit,
    onAddSelected: (List<Long>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = MaterialTheme.colorScheme
    val spotlight = LocalSpotlight.current
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(PickerFilter.All) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }

    val hasSongs = remember(availableSongs) { availableSongs.any { !it.isVideo } }
    val hasVideos = remember(availableSongs) { availableSongs.any { it.isVideo } }
    val showFilterChips = hasSongs && hasVideos

    val effectiveFilter = if (showFilterChips) filter else PickerFilter.All
    val filtered = remember(availableSongs, query, effectiveFilter) {
        val typeFiltered = when (effectiveFilter) {
            PickerFilter.All -> availableSongs
            PickerFilter.Songs -> availableSongs.filter { !it.isVideo }
            PickerFilter.Videos -> availableSongs.filter { it.isVideo }
        }
        typeFiltered.search(query)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Add to $playlistName",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(12.dp))
            if (availableSongs.isEmpty()) {
                Text(
                    text = "All available tracks are already in this playlist.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                )
            } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(if (hasVideos) "Filter songs or videos" else "Filter songs") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                )
                if (showFilterChips) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PickerFilter.entries.forEach { option ->
                            FilterChip(
                                selected = option == filter,
                                onClick = { filter = option },
                                label = { Text(option.label) },
                                shape = CircleShape,
                                border = null,
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = colors.surfaceContainerHigh,
                                    labelColor = colors.onSurfaceVariant,
                                    selectedContainerColor = colors.primary,
                                    selectedLabelColor = colors.onPrimary,
                                ),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(filtered, key = { it.id }) { song ->
                        val checked = song.id in selectedIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedIds = if (checked) selectedIds - song.id else selectedIds + song.id
                                }
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AlbumArt(
                                song.albumId,
                                Modifier.size(44.dp),
                                songId = song.id,
                                isVideo = song.isVideo,
                                corner = 10.dp,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    song.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                TrackSubtitleRow(
                                    song = song,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    selectedIds = if (isChecked) selectedIds + song.id else selectedIds - song.id
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onAddSelected(selectedIds.toList()) },
                    enabled = selectedIds.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = spotlight.container,
                        contentColor = spotlight.content,
                    ),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                ) {
                    Text(if (selectedIds.isEmpty()) "Add tracks" else "Add (${selectedIds.size})")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenSongsSheet(
    hiddenSongs: List<Song>,
    onDismiss: () -> Unit,
    onUnhideSong: (Long) -> Unit,
    onUnhideAll: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Hidden tracks",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                if (hiddenSongs.isNotEmpty()) {
                    TextButton(onClick = onUnhideAll) {
                        Text("Unhide all")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (hiddenSongs.isEmpty()) {
                Text(
                    "No hidden songs or videos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                ) {
                    items(hiddenSongs, key = { it.id }) { song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AlbumArt(
                                song.albumId,
                                Modifier.size(44.dp),
                                songId = song.id,
                                isVideo = song.isVideo,
                                corner = 10.dp,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    song.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                TrackSubtitleRow(
                                    song = song,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                            OutlinedButton(
                                onClick = { onUnhideSong(song.id) },
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_visibility),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Unhide")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackSubtitleRow(
    song: Song,
    style: TextStyle,
    color: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (song.isVideo) {
            Icon(
                painter = painterResource(R.drawable.ic_video),
                contentDescription = "Video audio",
                modifier = Modifier.size(12.dp),
                tint = color,
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = song.artist,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
