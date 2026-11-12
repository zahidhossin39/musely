package com.musely.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musely.app.Album
import com.musely.app.AlbumArt
import com.musely.app.MainViewModel
import com.musely.app.R
import com.musely.app.Song
import kotlinx.coroutines.delay

/** Inner horizontal padding of list items; with the 12dp list padding content sits on a 20dp gutter. */
internal val ItemInset = 8.dp

private const val POSITION_POLL_MS = 500L

/** Live playback position, polled while playing. */
@Composable
internal fun rememberPositionMs(vm: MainViewModel, songId: Long, isPlaying: Boolean): MutableLongState {
    val position = remember { mutableLongStateOf(0L) }
    LaunchedEffect(songId, isPlaying) {
        while (true) {
            position.longValue = vm.positionMs()
            if (!isPlaying) break
            delay(POSITION_POLL_MS)
        }
    }
    return position
}

@Composable
internal fun SongRow(song: Song, isCurrent: Boolean, isPlaying: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = ItemInset, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArt(song.albumId, Modifier.size(52.dp), corner = 14.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) colors.primary else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        if (isCurrent) {
            EqualizerBars(isPlaying, colors.primary)
        } else {
            Text(
                formatTime(song.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun AlbumCard(album: Album, onClick: () -> Unit) {
    Column(
        Modifier
            .padding(start = ItemInset, end = ItemInset, bottom = 20.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
    ) {
        AlbumArt(album.id, Modifier.fillMaxWidth().aspectRatio(1f), corner = 20.dp)
        Spacer(Modifier.height(10.dp))
        Text(album.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "${album.artist} · ${songCount(album.songCount)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun ArtistRow(name: String, count: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = ItemInset, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InitialAvatar(name, Modifier.size(52.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                songCount(count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A circle with the name's initial, tinted with one of the brand container colours. */
@Composable
internal fun InitialAvatar(name: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val palette = listOf(
        colors.primaryContainer to colors.onPrimaryContainer,
        colors.tertiaryContainer to colors.onTertiaryContainer,
        colors.secondaryContainer to colors.onSecondaryContainer,
    )
    val (container, content) = palette[Math.floorMod(name.hashCode(), palette.size)]
    Box(modifier.clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
        Text(initialOf(name), style = MaterialTheme.typography.titleLarge, color = content)
    }
}

/** Three dancing bars marking the song that's playing now; they rest when paused. */
@Composable
internal fun EqualizerBars(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "equalizer")
    val first by transition.animateFloat(0.3f, 1f, bounce(520), label = "bar1")
    val second by transition.animateFloat(0.3f, 1f, bounce(410), label = "bar2")
    val third by transition.animateFloat(0.3f, 1f, bounce(630), label = "bar3")
    Row(
        modifier.size(18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        listOf(first, second, third).forEach { level ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(if (playing) level else 0.3f)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color),
            )
        }
    }
}

private fun bounce(durationMs: Int) =
    infiniteRepeatable<Float>(tween(durationMs, easing = FastOutSlowInEasing), RepeatMode.Reverse)

/** Friendly full-width message for empty, loading-free states. */
@Composable
internal fun StateMessage(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_note), null,
                Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
