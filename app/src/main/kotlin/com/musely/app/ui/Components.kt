package com.musely.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.musely.app.Playlist
import com.musely.app.R
import com.musely.app.Song
import com.musely.app.ui.theme.LocalSpotlight
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SongRow(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showArt: Boolean = true,
    isStarred: Boolean = false,
    onToggleStar: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val spotlight = LocalSpotlight.current
    val rowModifier = modifier
        .fillMaxWidth()
        .clip(MaterialTheme.shapes.medium)
        .let { base ->
            if (onMoreClick != null) {
                base.combinedClickable(onClick = onClick, onLongClick = onMoreClick)
            } else {
                base.clickable(onClick = onClick)
            }
        }
        .padding(start = ItemInset, end = if (onMoreClick != null || onToggleStar != null) 0.dp else ItemInset, top = 8.dp, bottom = 8.dp)

    Row(
        rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showArt) {
            AlbumArt(
                song.albumId,
                Modifier.size(52.dp),
                songId = song.id,
                corner = 14.dp,
                isVideo = song.isVideo,
            )
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) colors.primary else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (song.isVideo) {
                    Icon(
                        painterResource(R.drawable.ic_video),
                        contentDescription = "Video audio",
                        modifier = Modifier.size(14.dp),
                        tint = colors.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        if (isCurrent) {
            EqualizerBars(isPlaying, colors.primary)
        } else {
            Text(
                formatTime(song.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
        }
        if (onToggleStar != null) {
            IconButton(
                onClick = onToggleStar,
                modifier = Modifier.size(38.dp),
            ) {
                Icon(
                    painter = painterResource(if (isStarred) R.drawable.ic_star else R.drawable.ic_star_outline),
                    contentDescription = if (isStarred) "Remove from Liked Music" else "Add to Liked Music",
                    modifier = Modifier.size(18.dp),
                    tint = if (isStarred) spotlight.container else colors.onSurfaceVariant,
                )
            }
        }
        if (onMoreClick != null) {
            IconButton(
                onClick = onMoreClick,
                modifier = Modifier.size(38.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_vert),
                    contentDescription = "Song options",
                    modifier = Modifier.size(18.dp),
                    tint = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun PlaylistCard(
    playlist: Playlist,
    songs: List<Song>,
    onClick: () -> Unit,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val songIds = remember(songs) { songs.map { it.id } }
    val albumIds = remember(songs) { songs.map { it.albumId } }

    Column(
        Modifier
            .padding(start = ItemInset, end = ItemInset, bottom = 20.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            PlaylistArtwork(
                songIds = songIds,
                albumIds = albumIds,
                isStarred = playlist.isSystem,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                corner = 20.dp,
            )
            if (!playlist.isSystem && (onRename != null || onDelete != null)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                ) {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(colors.scrim.copy(alpha = 0.45f)),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_more_vert),
                            contentDescription = "Playlist options",
                            modifier = Modifier.size(18.dp),
                            tint = Color.White,
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        if (onRename != null) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                leadingIcon = {
                                    Icon(
                                        painterResource(R.drawable.ic_edit),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onRename()
                                },
                            )
                        }
                        if (onDelete != null) {
                            DropdownMenuItem(
                                text = { Text("Delete", color = colors.error) },
                                leadingIcon = {
                                    Icon(
                                        painterResource(R.drawable.ic_delete),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = colors.error,
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            playlist.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (songs.isEmpty()) {
                if (playlist.isSystem) "Star songs or videos to collect here" else "Empty playlist"
            } else {
                "${collectionSummary(songs)} · ${formatTotal(songs)}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
        AlbumArt(
            album.id,
            Modifier.fillMaxWidth().aspectRatio(1f),
            fallbackSongIds = album.songIds,
            corner = 20.dp,
        )
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
internal fun StateMessage(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    iconRes: Int = R.drawable.ic_note,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(iconRes), null,
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
