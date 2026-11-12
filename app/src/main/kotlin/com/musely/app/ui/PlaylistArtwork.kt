package com.musely.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musely.app.AlbumArt
import com.musely.app.R
import com.musely.app.ui.theme.MuselyLime
import com.musely.app.ui.theme.MuselyViolet

/**
 * Selects up to 4 `(songId, albumId)` pairs for a 2x2 playlist collage, preferring distinct
 * albums first and falling back to distinct `(songId, albumId)` pairs.
 */
internal fun selectCollagePairs(songIds: List<Long>, albumIds: List<Long>): List<Pair<Long, Long>> {
    val count = minOf(songIds.size, albumIds.size)
    if (count == 0) return emptyList()
    val distinctPairs = List(count) { i -> songIds[i] to albumIds[i] }.distinct()
    val byAlbum = distinctPairs.distinctBy { it.second }
    if (byAlbum.size >= 4) return byAlbum.take(4)
    if (distinctPairs.size >= 4) {
        return (byAlbum + distinctPairs.filterNot { it in byAlbum }).take(4)
    }
    return listOf(distinctPairs.first())
}

/**
 * YouTube-Music-inspired playlist artwork tile:
 * - Starred playlists render a vibrant Violet-to-primaryContainer/Lime gradient with a centered star.
 * - Playlists with 4+ distinct tracks/albums render a crisp 2x2 [AlbumArt] collage.
 * - Playlists with 1-3 tracks render the lead track's [AlbumArt].
 * - Empty playlists render a soft brand-tinted placeholder with `ic_playlist_play`.
 */
@Composable
fun PlaylistArtwork(
    songIds: List<Long>,
    albumIds: List<Long>,
    isStarred: Boolean,
    modifier: Modifier = Modifier,
    corner: Dp = 20.dp,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(corner)

    if (isStarred) {
        val gradient = Brush.linearGradient(
            listOf(MuselyViolet, colors.primaryContainer, MuselyLime.copy(alpha = 0.90f)),
        )
        Box(
            modifier = modifier
                .clip(shape)
                .background(gradient),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_star),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(0.42f),
                tint = Color.White,
            )
        }
        return
    }

    val selected = remember(songIds, albumIds) { selectCollagePairs(songIds, albumIds) }

    when {
        selected.size >= 4 -> {
            Column(modifier = modifier.clip(shape)) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    AlbumArt(
                        albumId = selected[0].second,
                        songId = selected[0].first,
                        corner = 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                    AlbumArt(
                        albumId = selected[1].second,
                        songId = selected[1].first,
                        corner = 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    AlbumArt(
                        albumId = selected[2].second,
                        songId = selected[2].first,
                        corner = 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                    AlbumArt(
                        albumId = selected[3].second,
                        songId = selected[3].first,
                        corner = 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
        selected.isNotEmpty() -> {
            AlbumArt(
                albumId = selected[0].second,
                songId = selected[0].first,
                corner = corner,
                modifier = modifier,
            )
        }
        else -> {
            val placeholder = Brush.linearGradient(
                listOf(colors.primary.copy(alpha = 0.30f), colors.primary.copy(alpha = 0.08f)),
            )
            Box(
                modifier = modifier
                    .clip(shape)
                    .background(colors.surfaceContainerHigh)
                    .background(placeholder),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_playlist_play),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(0.4f),
                    tint = colors.primary.copy(alpha = 0.85f),
                )
            }
        }
    }
}
