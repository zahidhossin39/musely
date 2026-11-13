package com.musely.app.ui

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musely.app.MainViewModel
import com.musely.app.PlaybackState
import com.musely.app.Playlist
import com.musely.app.R
import com.musely.app.Song
import com.musely.app.UpdateStatus
import com.musely.app.ui.theme.LocalSpotlight
import java.time.LocalTime

internal enum class Section(val label: String) {
    Songs("Songs"),
    Videos("Videos"),
    Albums("Albums"),
    Artists("Artists"),
    Playlists("Playlists"),
}

@Composable
internal fun LibraryScreen(
    vm: MainViewModel,
    playback: PlaybackState,
    gridState: LazyGridState,
    section: Section,
    onSectionChange: (Section) -> Unit,
    hasVideoAccess: Boolean,
    hasPartialVideoAccess: Boolean,
    videoDeclined: Boolean,
    onRequestVideoAccess: () -> Unit,
    onOpenSettings: () -> Unit,
    bottomInset: Dp,
    songActions: SongActionsUi,
    onOpenDetail: (LibraryDetail) -> Unit,
    onMessage: (message: String, actionLabel: String?, onAction: (() -> Unit)?) -> Unit,
) {
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val allSongs by vm.allSongs.collectAsStateWithLifecycle()
    val songs by vm.songs.collectAsStateWithLifecycle()
    val allVideos by vm.allVideos.collectAsStateWithLifecycle()
    val videos by vm.videos.collectAsStateWithLifecycle()
    val allVisibleTracks by vm.allVisibleTracks.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val artists by vm.artists.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val starredIds by vm.starredIds.collectAsStateWithLifecycle()
    val hiddenSongs by vm.hiddenSongs.collectAsStateWithLifecycle()
    val updateStatus by vm.updateStatus.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val hour = remember { LocalTime.now().hour }
    val songsById = remember(allVisibleTracks) { allVisibleTracks.associateBy { it.id } }
    val shuffleQueue = if (section == Section.Videos) videos else songs

    var showUpdateSheet by remember { mutableStateOf(false) }
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
        fullWidth("brand") {
            BrandBar(
                hasUpdate = updateStatus is UpdateStatus.Available,
                checking = updateStatus is UpdateStatus.Checking,
                onUpdateClick = { showUpdateSheet = true },
            )
        }
        fullWidth("greeting") { Greeting(greeting(hour), subtitle(loaded, songs.size, videos.size)) }
        fullWidth("search") { SearchField(query, onChange = { vm.query.value = it }) }
        when {
            !loaded -> fullWidth("loading") { Loading() }
            query.isNotBlank() -> searchResults(query.trim(), results, playback, vm, starredIds, songActions)
            else -> {
                fullWidth("sections") {
                    SectionChips(
                        current = section,
                        onSelect = onSectionChange,
                        shuffleEnabled = shuffleQueue.isNotEmpty(),
                        shuffleLabel = if (section == Section.Videos) "Shuffle all videos" else "Shuffle all songs",
                        onShuffle = { vm.shuffle(shuffleQueue) },
                    )
                }
                when (section) {
                    Section.Songs -> {
                        when {
                            allSongs.isEmpty() -> fullWidth("empty") {
                                StateMessage(
                                    "It's quiet in here",
                                    "Add songs to your phone and Musely will pick them up automatically. No refresh needed.",
                                )
                            }
                            songs.isEmpty() -> fullWidth("all-hidden") {
                                StateMessage(
                                    "All songs are hidden",
                                    "Open Playlists → Hidden to unhide songs.",
                                )
                            }
                            else -> songRows(songs, playback, vm, starredIds, songActions)
                        }
                    }
                    Section.Videos -> {
                        if (!hasVideoAccess) {
                            fullWidth("video-permission") {
                                VideoAccessCard(
                                    declined = videoDeclined,
                                    onAllow = onRequestVideoAccess,
                                    onOpenSettings = onOpenSettings,
                                )
                            }
                        } else {
                            if (hasPartialVideoAccess) {
                                fullWidth("video-partial-access") {
                                    PartialVideoAccessBanner(onSelectMore = onRequestVideoAccess)
                                }
                            }
                            when {
                                allVideos.isEmpty() -> fullWidth("videos-empty") {
                                    StateMessage(
                                        title = "No videos found",
                                        body = if (hasPartialVideoAccess) {
                                            "Choose videos using \"Select more\" above and Musely will play their audio."
                                        } else {
                                            "Add videos to your phone and Musely will play their audio automatically."
                                        },
                                        iconRes = R.drawable.ic_video,
                                    )
                                }
                                videos.isEmpty() -> fullWidth("videos-hidden") {
                                    StateMessage(
                                        title = "All videos are hidden",
                                        body = "Open Playlists → Hidden to unhide videos.",
                                        iconRes = R.drawable.ic_video,
                                    )
                                }
                                else -> songRows(videos, playback, vm, starredIds, songActions)
                            }
                        }
                    }
                    Section.Albums -> {
                        if (albums.isEmpty()) {
                            fullWidth("albums-empty") {
                                StateMessage("No albums yet", "Albums from your songs will appear here.")
                            }
                        } else {
                            items(albums, key = { "album-${it.id}" }) { album ->
                                AlbumCard(album) { onOpenDetail(albumDetail(album, songs)) }
                            }
                        }
                    }
                    Section.Artists -> {
                        if (artists.isEmpty()) {
                            fullWidth("artists-empty") {
                                StateMessage("No artists yet", "Artists from your songs will appear here.")
                            }
                        } else {
                            items(
                                artists,
                                key = { "artist-${it.name}" },
                                span = { GridItemSpan(maxLineSpan) },
                            ) { artist ->
                                ArtistRow(artist.name, artist.songCount) { onOpenDetail(artistDetail(artist, songs)) }
                            }
                        }
                    }
                    Section.Playlists -> {
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
                }
            }
        }
    }

    if (showUpdateSheet) {
        UpdateSheet(
            installedVersion = vm.installedVersion,
            status = updateStatus,
            onDismiss = { showUpdateSheet = false },
            onCheckAgain = { vm.checkForUpdatesManually() },
            onUpdateClick = { url -> vm.openUpdateUrl(url) },
        )
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

private fun subtitle(loaded: Boolean, songTotal: Int, videoTotal: Int) = when {
    !loaded -> "Tuning up your library…"
    songTotal == 0 && videoTotal == 0 -> "Let's find your music."
    videoTotal == 0 -> "${songCount(songTotal)}, ready when you are."
    songTotal == 0 -> "${videoCount(videoTotal)}, ready when you are."
    else -> "${songCount(songTotal)} · ${videoCount(videoTotal)}"
}

private fun LazyGridScope.fullWidth(key: Any, content: @Composable () -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

private fun LazyGridScope.songRows(
    songs: List<Song>,
    playback: PlaybackState,
    vm: MainViewModel,
    starredIds: Set<Long>,
    songActions: SongActionsUi,
) = itemsIndexed(songs, key = { _, song -> song.id }, span = { _, _ -> GridItemSpan(maxLineSpan) }) { index, song ->
    SongRow(
        song = song,
        isCurrent = playback.song?.id == song.id,
        isPlaying = playback.isPlaying,
        onClick = { vm.play(songs, index) },
        isStarred = song.id in starredIds,
        onToggleStar = { songActions.toggleStar(song) },
        onMoreClick = { songActions.openMenu(song, null) },
    )
}

private fun LazyGridScope.searchResults(
    query: String,
    results: List<Song>,
    playback: PlaybackState,
    vm: MainViewModel,
    starredIds: Set<Long>,
    songActions: SongActionsUi,
) {
    if (results.isEmpty()) {
        fullWidth("no-results") {
            StateMessage(
                "No matches for “$query”",
                "Try an artist, an album, or just a few letters of a title.",
            )
        }
        return
    }
    fullWidth("results-label") {
        Text(
            if (results.size == 1) "1 match" else "${results.size} matches",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = ItemInset, top = 20.dp, bottom = 8.dp),
        )
    }
    songRows(results, playback, vm, starredIds, songActions)
}

@Composable
private fun BrandBar(
    hasUpdate: Boolean,
    checking: Boolean,
    onUpdateClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = ItemInset, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.musely_mark),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = LocalSpotlight.current.container,
        )
        Spacer(Modifier.width(10.dp))
        Text("Musely", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        UpdateTopBarButton(
            hasUpdate = hasUpdate,
            checking = checking,
            onClick = onUpdateClick,
        )
    }
}

@Composable
private fun Greeting(title: String, subtitle: String) {
    Column(Modifier.padding(start = ItemInset, end = ItemInset, top = 20.dp, bottom = 20.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val focus = LocalFocusManager.current
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    TextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = ItemInset),
        placeholder = { Text("Songs, videos, artists, albums") },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(painterResource(R.drawable.ic_close), "Clear search")
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = container,
            unfocusedContainerColor = container,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun SectionChips(
    current: Section,
    onSelect: (Section) -> Unit,
    shuffleEnabled: Boolean,
    shuffleLabel: String,
    onShuffle: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val spotlight = LocalSpotlight.current
    Row(
        Modifier.fillMaxWidth().padding(start = ItemInset, end = ItemInset, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Section.entries.forEach { section ->
                FilterChip(
                    selected = section == current,
                    onClick = { onSelect(section) },
                    label = { Text(section.label) },
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
        Spacer(Modifier.width(8.dp))
        FilledIconButton(
            onClick = onShuffle,
            enabled = shuffleEnabled,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = spotlight.container,
                contentColor = spotlight.content,
            ),
        ) {
            Icon(painterResource(R.drawable.ic_shuffle), shuffleLabel)
        }
    }
}

@Composable
private fun VideoAccessCard(
    declined: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val spotlight = LocalSpotlight.current
    Surface(
        color = colors.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ItemInset, vertical = 12.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                color = colors.primaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_video),
                        contentDescription = null,
                        tint = colors.onPrimaryContainer,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Listen to videos as audio",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (declined) {
                    "Allow video access so Musely can play audio directly from videos on your device without copying files."
                } else {
                    "Play any video's audio directly in Musely, star favorites to Liked Music, and add them to playlists — no file copying."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onAllow,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = spotlight.container,
                    contentColor = spotlight.content,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (declined) "Try again" else "Allow video access")
            }
            if (declined) {
                TextButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text("Open app settings")
                }
            }
        }
    }
}

@Composable
private fun PartialVideoAccessBanner(onSelectMore: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ItemInset, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.ic_video),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Showing selected videos",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    "Grant more access anytime to include other videos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            TextButton(onClick = onSelectMore) {
                Text("Select more")
            }
        }
    }
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

@Composable
private fun Loading() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}
