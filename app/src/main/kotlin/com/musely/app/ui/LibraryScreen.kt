package com.musely.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musely.app.MainViewModel
import com.musely.app.PlaybackState
import com.musely.app.R
import com.musely.app.Song
import com.musely.app.ui.theme.LocalSpotlight
import java.time.LocalTime

internal enum class Section(val label: String) { Songs("Songs"), Albums("Albums"), Artists("Artists") }

@Composable
internal fun LibraryScreen(
    vm: MainViewModel,
    playback: PlaybackState,
    gridState: LazyGridState,
    section: Section,
    onSectionChange: (Section) -> Unit,
    bottomInset: Dp,
    onOpenDetail: (LibraryDetail) -> Unit,
) {
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val songs by vm.songs.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val artists by vm.artists.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val hour = remember { LocalTime.now().hour }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = bottomInset + 24.dp),
    ) {
        fullWidth("brand") { BrandBar() }
        fullWidth("greeting") { Greeting(greeting(hour), subtitle(loaded, songs.size)) }
        fullWidth("search") { SearchField(query, onChange = { vm.query.value = it }) }
        when {
            !loaded -> fullWidth("loading") { Loading() }
            songs.isEmpty() -> fullWidth("empty") {
                StateMessage(
                    "It's quiet in here",
                    "Add songs to your phone and Musely will pick them up automatically. No refresh needed.",
                )
            }
            query.isNotBlank() -> searchResults(query.trim(), results, playback, vm)
            else -> {
                fullWidth("sections") {
                    SectionChips(section, onSelect = onSectionChange, onShuffle = { vm.shuffle(songs) })
                }
                when (section) {
                    Section.Songs -> songRows(songs, playback, vm)
                    Section.Albums -> items(albums, key = { "album-${it.id}" }) { album ->
                        AlbumCard(album) { onOpenDetail(albumDetail(album, songs)) }
                    }
                    Section.Artists -> items(
                        artists,
                        key = { "artist-${it.name}" },
                        span = { GridItemSpan(maxLineSpan) },
                    ) { artist ->
                        ArtistRow(artist.name, artist.songCount) { onOpenDetail(artistDetail(artist.name, songs)) }
                    }
                }
            }
        }
    }
}

private fun subtitle(loaded: Boolean, count: Int) = when {
    !loaded -> "Tuning up your library…"
    count == 0 -> "Let's find your music."
    else -> "${songCount(count)}, ready when you are."
}

private fun LazyGridScope.fullWidth(key: Any, content: @Composable () -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

private fun LazyGridScope.songRows(songs: List<Song>, playback: PlaybackState, vm: MainViewModel) =
    itemsIndexed(songs, key = { _, song -> song.id }, span = { _, _ -> GridItemSpan(maxLineSpan) }) { index, song ->
        SongRow(
            song = song,
            isCurrent = playback.song?.id == song.id,
            isPlaying = playback.isPlaying,
            onClick = { vm.play(songs, index) },
        )
    }

private fun LazyGridScope.searchResults(query: String, results: List<Song>, playback: PlaybackState, vm: MainViewModel) {
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
    songRows(results, playback, vm)
}

@Composable
private fun BrandBar() {
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
        Text("Musely", style = MaterialTheme.typography.titleLarge)
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
        placeholder = { Text("Songs, artists, albums") },
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
private fun SectionChips(current: Section, onSelect: (Section) -> Unit, onShuffle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val spotlight = LocalSpotlight.current
    Row(
        Modifier.fillMaxWidth().padding(start = ItemInset, end = ItemInset, top = 16.dp, bottom = 8.dp),
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
        Spacer(Modifier.weight(1f))
        FilledIconButton(
            onClick = onShuffle,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = spotlight.container,
                contentColor = spotlight.content,
            ),
        ) {
            Icon(painterResource(R.drawable.ic_shuffle), "Shuffle all songs")
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
