package com.musely.app.ui

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musely.app.AlbumArt
import com.musely.app.MainViewModel
import com.musely.app.R
import com.musely.app.Song
import com.musely.app.download.DownloadFormat
import com.musely.app.download.DownloadItem
import com.musely.app.download.DownloadState
import com.musely.app.download.DownloadsViewModel
import com.musely.app.ui.theme.LocalSpotlight

/**
 * What to do with a finished download: play it, file it into a playlist, like it, share it or
 * clean it up. Until the media library has indexed the file, only "remove from list" is offered.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadActionsSheet(
    item: DownloadItem,
    song: Song?,
    isStarred: Boolean,
    onDismiss: () -> Unit,
    onPlay: (Song) -> Unit,
    songActions: SongActionsUi,
    onShare: (Song) -> Unit,
    onRemoveFromList: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val spotlight = LocalSpotlight.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (song != null) {
                    AlbumArt(song.albumId, Modifier.size(56.dp), songId = song.id, isVideo = song.isVideo, corner = 12.dp)
                } else {
                    RemoteImage(item.request.thumbnailUrl, size = 56.dp)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.request.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${item.request.artist} · ${savedLocation(item.request.format)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = colors.outlineVariant.copy(alpha = 0.5f))

            if (song == null) {
                Row(Modifier.padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = spotlight.container)
                    Spacer(Modifier.width(16.dp))
                    Text("Adding to your library…", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                ActionRow(R.drawable.ic_play, "Play now", spotlight.container) {
                    onDismiss()
                    onPlay(song)
                }
                ActionRow(R.drawable.ic_playlist_add, "Add to playlist") {
                    onDismiss()
                    songActions.openAddToPlaylist(song)
                }
                ActionRow(
                    if (isStarred) R.drawable.ic_star else R.drawable.ic_star_outline,
                    if (isStarred) "Remove from Liked Music" else "Add to Liked Music",
                    if (isStarred) spotlight.container else colors.onSurface,
                ) {
                    onDismiss()
                    songActions.toggleStar(song)
                }
                ActionRow(R.drawable.ic_share, "Share") {
                    onDismiss()
                    onShare(song)
                }
                ActionRow(R.drawable.ic_info, "File info") {
                    onDismiss()
                    songActions.openFileInfo(song)
                }
                ActionRow(R.drawable.ic_delete, "Delete from device", colors.error) {
                    onDismiss()
                    songActions.deleteSong(song)
                }
            }
            ActionRow(R.drawable.ic_close, "Remove from downloads list") {
                onDismiss()
                onRemoveFromList()
            }
        }
    }
}

/**
 * Hosts [DownloadActionsSheet] for one download. Collects the download list and library only while
 * open, so progress ticks elsewhere never recompose the whole app.
 */
@Composable
internal fun DownloadActionsHost(
    downloadId: String,
    downloads: DownloadsViewModel,
    vm: MainViewModel,
    songActions: SongActionsUi,
    onClose: () -> Unit,
    onShare: (Song) -> Unit,
) {
    val items by downloads.items.collectAsStateWithLifecycle()
    val songs by vm.allSongs.collectAsStateWithLifecycle()
    val videos by vm.allVideos.collectAsStateWithLifecycle()
    val starredIds by vm.starredIds.collectAsStateWithLifecycle()
    val item = items.firstOrNull { it.request.id == downloadId }
    if (item == null) {
        LaunchedEffect(downloadId) { onClose() }
        return
    }
    val trackId = (item.state as? DownloadState.Completed)?.trackId
    val song = trackId?.let { id -> songs.firstOrNull { it.id == id } ?: videos.firstOrNull { it.id == id } }
    DownloadActionsSheet(
        item = item,
        song = song,
        isStarred = song != null && song.id in starredIds,
        onDismiss = onClose,
        onPlay = { vm.play(listOf(it), 0) },
        songActions = songActions,
        onShare = onShare,
        onRemoveFromList = { downloads.remove(downloadId) },
    )
}

internal fun savedLocation(format: DownloadFormat): String =
    if (format == DownloadFormat.Video) "Movies/Musely" else "Music/Musely"

/** Opens the system share sheet for a downloaded track. */
internal fun shareTrack(context: Context, song: Song) {
    val send = Intent(Intent.ACTION_SEND)
        .setType(if (song.isVideo) "video/*" else "audio/*")
        .putExtra(Intent.EXTRA_STREAM, song.uri)
        .putExtra(Intent.EXTRA_TITLE, song.title)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Share \"${song.title}\""))
}

@Composable
private fun ActionRow(
    @DrawableRes iconRes: Int,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(22.dp), tint = tint)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}
