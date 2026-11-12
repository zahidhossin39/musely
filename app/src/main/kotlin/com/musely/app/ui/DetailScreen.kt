package com.musely.app.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musely.app.Album
import com.musely.app.AlbumArt
import com.musely.app.MainViewModel
import com.musely.app.PlaybackState
import com.musely.app.R
import com.musely.app.Song
import com.musely.app.ui.theme.LocalSpotlight

/** An album or artist the user drilled into. */
@Immutable
data class LibraryDetail(
    val kind: String,
    val title: String,
    val subtitle: String,
    val albumId: Long?,
    val songs: List<Song>,
)

internal fun albumDetail(album: Album, all: List<Song>): LibraryDetail {
    val songs = all.filter { it.albumId == album.id }.sortedBy { it.track }
    return LibraryDetail(
        kind = "ALBUM",
        title = album.title,
        subtitle = "${album.artist} · ${songCount(songs.size)} · ${formatTotal(songs)}",
        albumId = album.id,
        songs = songs,
    )
}

internal fun artistDetail(name: String, all: List<Song>): LibraryDetail {
    val songs = all.filter { it.artist == name }
    val albums = songs.map { it.albumId }.distinct().size
    return LibraryDetail(
        kind = "ARTIST",
        title = name,
        subtitle = "${songCount(songs.size)} · ${albumCount(albums)} · ${formatTotal(songs)}",
        albumId = null,
        songs = songs,
    )
}

private val HeaderArtSize = 128.dp

@Composable
fun DetailScreen(
    detail: LibraryDetail,
    vm: MainViewModel,
    playback: PlaybackState,
    bottomInset: Dp,
    onBack: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomInset + 24.dp),
    ) {
        item(key = "back") {
            IconButton(onClick = onBack, modifier = Modifier.padding(vertical = 4.dp)) {
                Icon(painterResource(R.drawable.ic_back), "Back to library")
            }
        }
        item(key = "header") { Header(detail) }
        item(key = "actions") {
            Actions(onPlay = { vm.playAll(detail.songs) }, onShuffle = { vm.shuffle(detail.songs) })
        }
        itemsIndexed(detail.songs, key = { _, song -> song.id }) { index, song ->
            SongRow(
                song = song,
                isCurrent = playback.song?.id == song.id,
                isPlaying = playback.isPlaying,
                onClick = { vm.play(detail.songs, index) },
            )
        }
    }
}

@Composable
private fun Header(detail: LibraryDetail) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = ItemInset, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (detail.albumId != null) {
            val songIds = remember(detail.songs) { detail.songs.map { it.id } }
            AlbumArt(
                detail.albumId,
                Modifier.size(HeaderArtSize),
                fallbackSongIds = songIds,
                corner = 24.dp,
            )
        } else {
            InitialAvatar(detail.title, Modifier.size(HeaderArtSize))
        }
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(
                detail.kind,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                detail.title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                detail.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Actions(onPlay: () -> Unit, onShuffle: () -> Unit) {
    val spotlight = LocalSpotlight.current
    Row(
        Modifier.fillMaxWidth().padding(start = ItemInset, end = ItemInset, top = 20.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onPlay,
            modifier = Modifier.weight(1f).height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = spotlight.container, contentColor = spotlight.content),
        ) {
            Icon(painterResource(R.drawable.ic_play), null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Play")
        }
        FilledTonalButton(onClick = onShuffle, modifier = Modifier.weight(1f).height(52.dp)) {
            Icon(painterResource(R.drawable.ic_shuffle), null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Shuffle")
        }
    }
}
