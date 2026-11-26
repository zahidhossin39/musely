package com.musely.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musely.app.AlbumArt
import com.musely.app.R
import com.musely.app.Song
import com.musely.app.download.DownloadFormat
import com.musely.app.download.DownloadItem
import com.musely.app.download.DownloadState
import com.musely.app.download.MediaSource
import com.musely.app.download.ResolvedMedia
import com.musely.app.download.VideoQuality
import com.musely.app.ui.theme.LocalSpotlight

private const val PREVIEW_ROWS = 4

/** One-line status under a download's title. */
internal fun downloadStatusText(state: DownloadState, format: DownloadFormat): String = when (state) {
    DownloadState.Queued -> "Waiting…"
    DownloadState.Preparing -> "Preparing…"
    is DownloadState.Downloading -> if (state.totalBytes > 0) {
        "${formatFileSize(state.downloadedBytes)} of ${formatFileSize(state.totalBytes)}"
    } else {
        formatFileSize(state.downloadedBytes)
    }
    DownloadState.Processing -> if (format == DownloadFormat.Video) "Merging and saving…" else "Saving to your library…"
    is DownloadState.Completed -> "${format.label} · ${formatFileSize(state.sizeBytes)}"
    is DownloadState.Failed -> state.message
    DownloadState.Canceled -> "Canceled"
}

/** Second line of the preview card. */
internal fun previewSummary(media: ResolvedMedia): String =
    if (media.isCollection) {
        "${media.subtitle} · ${trackCount(media.entries.size)}"
    } else {
        val duration = media.entries.firstOrNull()?.durationMs ?: 0L
        if (duration > 0) "${media.subtitle} · ${formatTime(duration)}" else media.subtitle
    }

/** Paste-a-link field with paste and look-up actions. */
@Composable
internal fun LinkInputCard(
    value: String,
    onValueChange: (String) -> Unit,
    onPaste: () -> Unit,
    onSubmit: () -> Unit,
    busy: Boolean,
) {
    val focus = LocalFocusManager.current
    val colors = MaterialTheme.colorScheme
    val spotlight = LocalSpotlight.current
    val submit = {
        focus.clearFocus()
        onSubmit()
    }
    Surface(
        color = colors.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = ItemInset),
    ) {
        Column(Modifier.padding(16.dp)) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Paste a YouTube, YouTube Music or Spotify link") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_link), contentDescription = null) },
                trailingIcon = {
                    if (value.isEmpty()) {
                        IconButton(onClick = onPaste) { Icon(painterResource(R.drawable.ic_paste), "Paste link") }
                    } else {
                        IconButton(onClick = { onValueChange("") }) { Icon(painterResource(R.drawable.ic_close), "Clear link") }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.surfaceContainerHighest,
                    unfocusedContainerColor = colors.surfaceContainerHighest,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Tip: tap Share in YouTube, YouTube Music or Spotify and pick Musely.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = submit,
                    enabled = value.isNotBlank() && !busy,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = spotlight.container, contentColor = spotlight.content),
                ) {
                    Text("Find")
                }
            }
        }
    }
}

@Composable
internal fun LookupLoadingCard(source: MediaSource) {
    MessageCard {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = LocalSpotlight.current.container)
        Spacer(Modifier.width(14.dp))
        Text("Looking up ${source.label} link…", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun LookupErrorCard(message: String, onDismiss: () -> Unit) {
    MessageCard {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss) { Text("Dismiss") }
    }
}

@Composable
private fun MessageCard(content: @Composable RowScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = ItemInset, vertical = 8.dp),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/** Preview of a resolved link with format choice and, for collections, a save-as-playlist option. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadPreviewCard(
    media: ResolvedMedia,
    format: DownloadFormat,
    onFormatChange: (DownloadFormat) -> Unit,
    videoQuality: VideoQuality,
    onVideoQualityChange: (VideoQuality) -> Unit,
    saveAsPlaylist: Boolean,
    onSaveAsPlaylistChange: (Boolean) -> Unit,
    playlistName: String,
    onPlaylistNameChange: (String) -> Unit,
    onCancel: () -> Unit,
    onDownload: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val spotlight = LocalSpotlight.current
    Surface(
        color = colors.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = ItemInset, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RemoteImage(media.thumbnailUrl, size = 72.dp, corner = 14.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(media.link.source.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.primary)
                    Text(media.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        previewSummary(media),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (media.isCollection) {
                Spacer(Modifier.height(12.dp))
                media.entries.take(PREVIEW_ROWS).forEachIndexed { index, entry ->
                    Text(
                        "${index + 1}. ${entry.title} · ${entry.artist}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (media.entries.size > PREVIEW_ROWS) {
                    Text("and ${media.entries.size - PREVIEW_ROWS} more", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            if (media.link.source == MediaSource.Spotify) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Spotify audio is protected, so Musely finds the same songs on YouTube Music.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                DownloadFormat.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = option == format,
                        onClick = { onFormatChange(option) },
                        shape = SegmentedButtonDefaults.itemShape(index, DownloadFormat.entries.size),
                        label = { Text(if (option == DownloadFormat.Audio) "Audio (M4A)" else "Video (MP4)") },
                    )
                }
            }
            if (format == DownloadFormat.Video) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoQuality.entries.forEach { quality ->
                        FilterChip(
                            selected = quality == videoQuality,
                            onClick = { onVideoQualityChange(quality) },
                            label = { Text(quality.label) },
                        )
                    }
                }
            }
            if (media.isCollection) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onSaveAsPlaylistChange(!saveAsPlaylist) },
                ) {
                    Checkbox(checked = saveAsPlaylist, onCheckedChange = onSaveAsPlaylistChange)
                    Text("Save as a Musely playlist", style = MaterialTheme.typography.bodyMedium)
                }
                if (saveAsPlaylist) {
                    OutlinedTextField(
                        value = playlistName,
                        onValueChange = onPlaylistNameChange,
                        label = { Text("Playlist name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onDownload,
                    enabled = !(media.isCollection && saveAsPlaylist && playlistName.isBlank()),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = spotlight.container, contentColor = spotlight.content),
                ) {
                    Icon(painterResource(R.drawable.ic_download), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (media.isCollection) "Download ${media.entries.size}" else "Download")
                }
            }
        }
    }
}

/** A queued, running, finished or failed download. */
@Composable
internal fun DownloadRow(
    item: DownloadItem,
    song: Song?,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val state = item.state
    val completed = state is DownloadState.Completed
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (completed) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(horizontal = ItemInset + 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (song != null) {
            AlbumArt(song.albumId, Modifier.size(52.dp), songId = song.id, isVideo = song.isVideo, corner = 12.dp)
        } else {
            RemoteImage(item.request.thumbnailUrl, size = 52.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.request.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (completed) "${item.request.artist} · ${downloadStatusText(state, item.request.format)}"
                else downloadStatusText(state, item.request.format),
                style = MaterialTheme.typography.bodySmall,
                color = if (state is DownloadState.Failed) colors.error else colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (state is DownloadState.Downloading || state == DownloadState.Preparing || state == DownloadState.Processing) {
                Spacer(Modifier.height(6.dp))
                val fraction = (state as? DownloadState.Downloading)?.fraction
                if (fraction != null) {
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        when (state) {
            DownloadState.Queued, DownloadState.Preparing, is DownloadState.Downloading, DownloadState.Processing ->
                IconButton(onClick = onCancel) { Icon(painterResource(R.drawable.ic_close), "Cancel download") }
            is DownloadState.Completed -> {
                IconButton(onClick = onPlay, enabled = song != null) { Icon(painterResource(R.drawable.ic_play), "Play") }
                IconButton(onClick = onOpen) { Icon(painterResource(R.drawable.ic_more_vert), "More options") }
            }
            is DownloadState.Failed, DownloadState.Canceled -> {
                IconButton(onClick = onRetry) { Icon(painterResource(R.drawable.ic_retry), "Retry download") }
                IconButton(onClick = onRemove) { Icon(painterResource(R.drawable.ic_close), "Remove from list") }
            }
        }
    }
}
