package com.musely.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.musely.app.AlbumArt
import com.musely.app.MainViewModel
import com.musely.app.PlaybackState
import com.musely.app.R
import com.musely.app.Song
import com.musely.app.ui.theme.LocalSpotlight

private const val PAUSED_ART_SCALE = 0.88f

@Composable
fun NowPlayingScreen(vm: MainViewModel, songActions: SongActionsUi, onClose: () -> Unit) {
    val state by vm.playback.collectAsStateWithLifecycle()
    val starredIds by vm.starredIds.collectAsStateWithLifecycle()
    val song = state.song ?: return
    val isStarred = song.id in starredIds
    val colors = MaterialTheme.colorScheme
    val spotlight = LocalSpotlight.current
    // The cover breathes: full size while playing, settles back when paused.
    val artScale by animateFloatAsState(
        if (state.isPlaying) 1f else PAUSED_ART_SCALE,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "artScale",
    )
    val artShape = RoundedCornerShape(28.dp)

    Box(
        Modifier
            .fillMaxSize()
            // Swallow touches so taps on empty areas never reach the library underneath.
            .pointerInput(Unit) {}
            .background(colors.surface)
            .background(Brush.verticalGradient(0f to colors.primaryContainer.copy(alpha = 0.65f), 0.75f to Color.Transparent)),
    ) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar(song.album, onClose)
            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                AlbumArt(
                    song.albumId,
                    Modifier
                        .aspectRatio(1f)
                        .graphicsLayer { scaleX = artScale; scaleY = artScale }
                        .shadow(32.dp, artShape, ambientColor = colors.primary, spotColor = colors.primary),
                    songId = song.id,
                    large = true,
                    corner = 28.dp,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        song.artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { songActions.toggleStar(song) }) {
                    Icon(
                        painterResource(if (isStarred) R.drawable.ic_star else R.drawable.ic_star_outline),
                        contentDescription = if (isStarred) "Remove from Liked Music" else "Add to Liked Music",
                        tint = if (isStarred) spotlight.container else colors.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { songActions.openAddToPlaylist(song) }) {
                    Icon(
                        painterResource(R.drawable.ic_playlist_add),
                        contentDescription = "Add to playlist",
                        tint = colors.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { songActions.openMenu(song, null) }) {
                    Icon(
                        painterResource(R.drawable.ic_more_vert),
                        contentDescription = "More options",
                        tint = colors.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            SeekBar(vm, state, song)
            Spacer(Modifier.height(8.dp))
            Controls(vm, state)
            Spacer(Modifier.height(24.dp))
            UpNext(state.nextSong)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TopBar(album: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_down), "Close player") }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "NOW PLAYING",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 2.sp,
            )
            Text(album, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(48.dp))
    }
}

@Composable
private fun SeekBar(vm: MainViewModel, state: PlaybackState, song: Song) {
    val colors = MaterialTheme.colorScheme
    var position by rememberPositionMs(vm, song.id, state.isPlaying)
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val duration = (if (state.durationMs > 0) state.durationMs else song.durationMs).coerceAtLeast(1)

    Slider(
        value = if (dragging) dragValue else position.toFloat().coerceIn(0f, duration.toFloat()),
        onValueChange = { dragging = true; dragValue = it },
        onValueChangeFinished = {
            position = dragValue.toLong()
            vm.seekTo(position)
            dragging = false
        },
        valueRange = 0f..duration.toFloat(),
        colors = SliderDefaults.colors(
            thumbColor = colors.primary,
            activeTrackColor = colors.primary,
            inactiveTrackColor = colors.onSurface.copy(alpha = 0.16f),
        ),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val shown = if (dragging) dragValue.toLong() else position
        Text(formatTime(shown), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        Text(formatTime(duration), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun Controls(vm: MainViewModel, state: PlaybackState) {
    val colors = MaterialTheme.colorScheme
    val spotlight = LocalSpotlight.current
    // Play is a circle; while music plays it softens into a rounded square (M3 shape morph).
    val corner by animateDpAsState(if (state.isPlaying) 28.dp else 40.dp, label = "playCorner")
    val toggleColors = IconButtonDefaults.filledTonalIconToggleButtonColors(
        containerColor = Color.Transparent,
        contentColor = colors.onSurfaceVariant,
        checkedContainerColor = colors.secondaryContainer,
        checkedContentColor = colors.onSecondaryContainer,
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconToggleButton(
            checked = state.shuffle,
            onCheckedChange = { vm.toggleShuffle() },
            colors = toggleColors,
        ) {
            Icon(painterResource(R.drawable.ic_shuffle), if (state.shuffle) "Shuffle on" else "Shuffle off")
        }
        IconButton(onClick = vm::previous, modifier = Modifier.size(56.dp)) {
            Icon(painterResource(R.drawable.ic_prev), "Previous song", Modifier.size(32.dp))
        }
        FilledIconButton(
            onClick = vm::togglePlay,
            modifier = Modifier.size(80.dp),
            shape = RoundedCornerShape(corner),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = spotlight.container,
                contentColor = spotlight.content,
            ),
        ) {
            Icon(
                painterResource(if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                if (state.isPlaying) "Pause" else "Play",
                Modifier.size(36.dp),
            )
        }
        IconButton(onClick = vm::next, modifier = Modifier.size(56.dp)) {
            Icon(painterResource(R.drawable.ic_next), "Next song", Modifier.size(32.dp))
        }
        FilledTonalIconToggleButton(
            checked = state.repeatMode != Player.REPEAT_MODE_OFF,
            onCheckedChange = { vm.cycleRepeat() },
            colors = toggleColors,
        ) {
            Icon(
                painterResource(
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat,
                ),
                when (state.repeatMode) {
                    Player.REPEAT_MODE_ONE -> "Repeat this song"
                    Player.REPEAT_MODE_ALL -> "Repeat all"
                    else -> "Repeat off"
                },
            )
        }
    }
}

@Composable
private fun UpNext(next: Song?) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceContainerHigh.copy(alpha = 0.8f),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (next == null) {
                Text(
                    "That's the end of the queue. Enjoy the quiet, or hit shuffle.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                return@Row
            }
            AlbumArt(next.albumId, Modifier.size(40.dp), songId = next.id, corner = 10.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "UP NEXT",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary,
                    letterSpacing = 1.5.sp,
                )
                Text(
                    "${next.title} · ${next.artist}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
