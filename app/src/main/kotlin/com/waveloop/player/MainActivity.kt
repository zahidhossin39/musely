package com.waveloop.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            WaveloopTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App()
                }
            }
        }
    }
}

@Composable
private fun WaveloopTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

private val audioPermission =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
private fun App(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    LaunchedEffect(granted) { if (granted) vm.loadLibrary() }

    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        if (!granted) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Waveloop needs access to the music on this device.",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { request.launch(audioPermission) }) { Text("Allow access") }
            }
        } else {
            var showPlayer by rememberSaveable { mutableStateOf(false) }
            val playback by vm.playback.collectAsStateWithLifecycle()
            if (showPlayer && playback.song != null) {
                BackHandler { showPlayer = false }
                NowPlaying(vm, playback, onClose = { showPlayer = false })
            } else {
                Column(Modifier.fillMaxSize()) {
                    Library(vm, Modifier.weight(1f))
                    playback.song?.let { MiniPlayer(vm, it, playback.isPlaying) { showPlayer = true } }
                }
            }
        }
    }
}

/** A drilled-in album or artist. */
private data class Detail(val title: String, val songs: List<Song>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Library(vm: MainViewModel, modifier: Modifier) {
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val all by vm.songs.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val artists by vm.artists.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<Detail?>(null) }

    Column(modifier) {
        val open = detail
        if (open != null) {
            BackHandler { detail = null }
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { detail = null }) {
                    Icon(painterResource(R.drawable.ic_back), "Back")
                }
                Text(
                    open.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SongList(open.songs, vm)
            return@Column
        }

        TextField(
            value = query,
            onValueChange = { vm.query.value = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            placeholder = { Text("Search songs, artists, albums") },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { vm.query.value = "" }) {
                        Icon(painterResource(R.drawable.ic_close), "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )

        when {
            !loaded -> Centered { CircularProgressIndicator() }
            all.isEmpty() -> Centered { Text("No music found on this device.") }
            query.isNotBlank() ->
                if (results.isEmpty()) Centered { Text("No results for \"${query.trim()}\"") }
                else SongList(results, vm)
            else -> {
                PrimaryTabRow(selectedTabIndex = tab) {
                    listOf("Songs", "Albums", "Artists").forEachIndexed { i, label ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                    }
                }
                when (tab) {
                    0 -> SongList(all, vm)
                    1 -> LazyColumn(Modifier.fillMaxSize()) {
                        items(albums, key = { it.id }) { album ->
                            ListRow(
                                title = album.title,
                                subtitle = "${album.artist} · ${countLabel(album.songCount)}",
                                albumId = album.id,
                                onClick = {
                                    detail = Detail(
                                        album.title,
                                        all.filter { it.albumId == album.id }.sortedBy { it.track },
                                    )
                                },
                            )
                        }
                    }
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(artists, key = { it.name }) { artist ->
                            ListRow(
                                title = artist.name,
                                subtitle = countLabel(artist.songCount),
                                albumId = null,
                                onClick = {
                                    detail = Detail(artist.name, all.filter { it.artist == artist.name })
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongList(songs: List<Song>, vm: MainViewModel) {
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
            ListRow(
                title = song.title,
                subtitle = "${song.artist} · ${formatTime(song.durationMs)}",
                albumId = song.albumId,
                onClick = { vm.play(songs, index) },
            )
        }
    }
}

@Composable
private fun ListRow(title: String, subtitle: String, albumId: Long?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (albumId != null) {
            AlbumArt(albumId, Modifier.size(48.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MiniPlayer(vm: MainViewModel, song: Song, isPlaying: Boolean, onOpen: () -> Unit) {
    Surface(tonalElevation = 6.dp, modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            AlbumArt(song.albumId, Modifier.size(44.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = vm::togglePlay) {
                Icon(
                    painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                    if (isPlaying) "Pause" else "Play",
                )
            }
            IconButton(onClick = vm::next) { Icon(painterResource(R.drawable.ic_next), "Next") }
        }
    }
}

@Composable
private fun NowPlaying(vm: MainViewModel, state: PlaybackState, onClose: () -> Unit) {
    val song = state.song ?: return
    var position by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(song.id, state.isPlaying) {
        while (true) {
            if (!dragging) position = vm.positionMs()
            if (!state.isPlaying) break
            delay(500)
        }
    }
    val duration = (if (state.durationMs > 0) state.durationMs else song.durationMs).coerceAtLeast(1)

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_down), "Close player") }
        }
        Spacer(Modifier.weight(1f))
        AlbumArt(song.albumId, Modifier.fillMaxWidth().aspectRatio(1f), large = true, corner = 20.dp)
        Spacer(Modifier.weight(1f))
        Text(
            song.title,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            song.artist,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(16.dp))
        Slider(
            value = if (dragging) dragValue else position.toFloat().coerceIn(0f, duration.toFloat()),
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = {
                position = dragValue.toLong()
                vm.seekTo(position)
                dragging = false
            },
            valueRange = 0f..duration.toFloat(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val shown = if (dragging) dragValue.toLong() else position
            Text(formatTime(shown), style = MaterialTheme.typography.labelMedium)
            Text(formatTime(duration), style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val active = MaterialTheme.colorScheme.primary
            val inactive = MaterialTheme.colorScheme.onSurfaceVariant
            IconButton(onClick = vm::toggleShuffle) {
                Icon(
                    painterResource(R.drawable.ic_shuffle),
                    if (state.shuffle) "Shuffle on" else "Shuffle off",
                    tint = if (state.shuffle) active else inactive,
                )
            }
            IconButton(onClick = vm::previous, modifier = Modifier.size(56.dp)) {
                Icon(painterResource(R.drawable.ic_prev), "Previous", Modifier.size(36.dp))
            }
            FilledIconButton(onClick = vm::togglePlay, modifier = Modifier.size(72.dp)) {
                Icon(
                    painterResource(if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                    if (state.isPlaying) "Pause" else "Play",
                    Modifier.size(40.dp),
                )
            }
            IconButton(onClick = vm::next, modifier = Modifier.size(56.dp)) {
                Icon(painterResource(R.drawable.ic_next), "Next", Modifier.size(36.dp))
            }
            IconButton(onClick = vm::cycleRepeat) {
                Icon(
                    painterResource(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) R.drawable.ic_repeat_one
                        else R.drawable.ic_repeat,
                    ),
                    when (state.repeatMode) {
                        Player.REPEAT_MODE_ONE -> "Repeat one"
                        Player.REPEAT_MODE_ALL -> "Repeat all"
                        else -> "Repeat off"
                    },
                    tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) inactive else active,
                )
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) { content() }
}

private fun countLabel(count: Int) = if (count == 1) "1 song" else "$count songs"

private fun formatTime(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
