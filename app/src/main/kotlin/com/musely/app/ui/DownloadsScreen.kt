package com.musely.app.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musely.app.MainViewModel
import com.musely.app.R
import com.musely.app.Song
import com.musely.app.download.DownloadFormat
import com.musely.app.download.DownloadState
import com.musely.app.download.DownloadsViewModel
import com.musely.app.download.LinkLookup
import com.musely.app.download.ResolvedMedia
import com.musely.app.download.VideoQuality
import com.musely.app.download.isActive

/** The Downloads tab: paste or share a link, pick audio or video, and follow progress. */
@Composable
internal fun DownloadsScreen(
    downloads: DownloadsViewModel,
    vm: MainViewModel,
    listState: LazyListState,
    bottomInset: Dp,
    onOpenActions: (downloadId: String) -> Unit,
    onPlay: (Song) -> Unit,
    onMessage: (message: String, actionLabel: String?, onAction: (() -> Unit)?) -> Unit,
) {
    val context = LocalContext.current
    val items by downloads.items.collectAsStateWithLifecycle()
    val input by downloads.input.collectAsStateWithLifecycle()
    val lookup by downloads.lookup.collectAsStateWithLifecycle()
    val defaultFormat by downloads.defaultFormat.collectAsStateWithLifecycle()
    val videoQuality by downloads.videoQuality.collectAsStateWithLifecycle()
    val songs by vm.allSongs.collectAsStateWithLifecycle()
    val videos by vm.allVideos.collectAsStateWithLifecycle()
    val tracksById = remember(songs, videos) { (songs + videos).associateBy { it.id } }

    val active = remember(items) { items.filter { it.state.isActive } }
    val finished = remember(items) { items.filterNot { it.state.isActive } }

    val runWithPermissions = rememberDownloadPermissions(onDenied = {
        onMessage("Musely needs storage access to save downloads on this Android version.", null, null)
    })

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = bottomInset + 24.dp),
    ) {
        item(key = "header") {
            TabHeader(
                title = "Downloads",
                subtitle = if (active.isEmpty()) "Save songs and videos to your phone." else "Downloading ${trackCount(active.size)}",
            )
        }
        item(key = "link") {
            LinkInputCard(
                value = input,
                onValueChange = { downloads.input.value = it },
                onPaste = { readClipboard(context)?.let { downloads.lookUp(it) } },
                onSubmit = { downloads.lookUp() },
                busy = lookup is LinkLookup.Loading,
            )
        }
        item(key = "lookup") {
            when (val state = lookup) {
                LinkLookup.Idle -> Unit
                is LinkLookup.Loading -> LookupLoadingCard(state.source)
                is LinkLookup.Error -> LookupErrorCard(state.message, onDismiss = downloads::clearLookup)
                is LinkLookup.Ready -> PreviewSection(
                    media = state.media,
                    defaultFormat = defaultFormat,
                    videoQuality = videoQuality,
                    onVideoQualityChange = downloads::setVideoQuality,
                    onCancel = downloads::clearLookup,
                    onDownload = { format, playlistName ->
                        runWithPermissions {
                            val playlistId = playlistName?.let { vm.createPlaylist(it)?.id }
                            val count = downloads.download(state.media, format, playlistId)
                            onMessage(
                                if (count == 1) "Downloading \"${state.media.title}\"" else "Downloading $count tracks",
                                null,
                                null,
                            )
                        }
                    },
                )
            }
        }

        if (active.isNotEmpty()) {
            item(key = "active-title") {
                SectionHeader("In progress", actionLabel = "Cancel all", onAction = downloads::cancelAll)
            }
            items(active, key = { "active-${it.request.id}" }) { item ->
                DownloadRow(
                    item = item,
                    song = null,
                    onOpen = {},
                    onPlay = {},
                    onCancel = { downloads.cancel(item.request.id) },
                    onRetry = {},
                    onRemove = {},
                )
            }
        }
        if (finished.isNotEmpty()) {
            item(key = "finished-title") {
                SectionHeader("Downloaded", actionLabel = "Clear list", onAction = downloads::clearFinished)
            }
            items(finished, key = { "finished-${it.request.id}" }) { item ->
                val song = (item.state as? DownloadState.Completed)?.let { tracksById[it.trackId] }
                DownloadRow(
                    item = item,
                    song = song,
                    onOpen = { onOpenActions(item.request.id) },
                    onPlay = { song?.let(onPlay) },
                    onCancel = {},
                    onRetry = { runWithPermissions { downloads.retry(item.request.id) } },
                    onRemove = { downloads.remove(item.request.id) },
                )
            }
        }
        if (items.isEmpty() && lookup == LinkLookup.Idle) {
            item(key = "empty") {
                StateMessage(
                    title = "Nothing downloaded yet",
                    body = "Paste a link above, or share one from YouTube, YouTube Music or Spotify. " +
                        "Songs land in Music/Musely and show up in your library.",
                    iconRes = R.drawable.ic_download,
                )
            }
        }
    }
}

@Composable
private fun PreviewSection(
    media: ResolvedMedia,
    defaultFormat: DownloadFormat,
    videoQuality: VideoQuality,
    onVideoQualityChange: (VideoQuality) -> Unit,
    onCancel: () -> Unit,
    onDownload: (format: DownloadFormat, playlistName: String?) -> Unit,
) {
    var format by remember(media) { mutableStateOf(defaultFormat) }
    var saveAsPlaylist by remember(media) { mutableStateOf(media.isCollection) }
    var playlistName by remember(media) { mutableStateOf(media.title) }
    DownloadPreviewCard(
        media = media,
        format = format,
        onFormatChange = { format = it },
        videoQuality = videoQuality,
        onVideoQualityChange = onVideoQualityChange,
        saveAsPlaylist = saveAsPlaylist,
        onSaveAsPlaylistChange = { saveAsPlaylist = it },
        playlistName = playlistName,
        onPlaylistNameChange = { playlistName = it },
        onCancel = onCancel,
        onDownload = {
            val name = playlistName.trim().takeIf { media.isCollection && saveAsPlaylist && it.isNotEmpty() }
            onDownload(format, name)
        },
    )
}

@Composable
private fun SectionHeader(title: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = ItemInset + 12.dp, end = ItemInset, top = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

private fun readClipboard(context: Context): String? {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
    val clip = clipboard.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }
}

/**
 * Wraps an action that starts downloads: asks for storage access on Android 8–9 (required to
 * write to Music/) and, once, for notifications on Android 13+ (progress; never blocking).
 */
@Composable
private fun rememberDownloadPermissions(onDenied: () -> Unit): (action: () -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var askedNotifications by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        val action = pending
        pending = null
        if (needsLegacyStorage(context)) onDenied() else action?.invoke()
    }
    return remember(context) {
        { action ->
            val missing = buildList {
                if (needsLegacyStorage(context)) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !askedNotifications &&
                    !isGranted(context, Manifest.permission.POST_NOTIFICATIONS)
                ) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            if (missing.isEmpty()) {
                action()
            } else {
                askedNotifications = true
                pending = action
                launcher.launch(missing.toTypedArray())
            }
        }
    }
}

private fun needsLegacyStorage(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !isGranted(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)

private fun isGranted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
