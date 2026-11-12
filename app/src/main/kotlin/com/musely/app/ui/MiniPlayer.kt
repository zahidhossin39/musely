package com.musely.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musely.app.AlbumArt
import com.musely.app.MainViewModel
import com.musely.app.PlaybackState
import com.musely.app.R
import com.musely.app.ui.theme.LocalSpotlight

/** Height the library reserves at the bottom so the floating mini player never hides a row. */
val MiniPlayerReserve = 112.dp

/** Floating "now playing" card; tap to open the full player. */
@Composable
fun MiniPlayer(vm: MainViewModel, state: PlaybackState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val song = state.song ?: return
    val colors = MaterialTheme.colorScheme
    val spotlight = LocalSpotlight.current
    val position by rememberPositionMs(vm, song.id, state.isPlaying)
    val duration = (if (state.durationMs > 0) state.durationMs else song.durationMs).coerceAtLeast(1)
    val progress = (position.toFloat() / duration).coerceIn(0f, 1f)

    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth().padding(12.dp),
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceContainerHighest,
        shadowElevation = 8.dp,
    ) {
        Column {
            Row(
                Modifier.padding(start = 10.dp, end = 6.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AlbumArt(song.albumId, Modifier.size(48.dp), songId = song.id, corner = 14.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FilledIconButton(
                    onClick = vm::togglePlay,
                    modifier = Modifier.size(44.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = spotlight.container,
                        contentColor = spotlight.content,
                    ),
                ) {
                    Icon(
                        painterResource(if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                        if (state.isPlaying) "Pause" else "Play",
                        Modifier.size(22.dp),
                    )
                }
                IconButton(onClick = vm::next) { Icon(painterResource(R.drawable.ic_next), "Next song") }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(colors.onSurface.copy(alpha = 0.12f)),
            ) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(colors.primary))
            }
        }
    }
}
