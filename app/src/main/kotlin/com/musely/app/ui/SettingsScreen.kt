package com.musely.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musely.app.MainViewModel
import com.musely.app.R
import com.musely.app.download.DownloadFormat
import com.musely.app.download.DownloadsViewModel
import com.musely.app.download.VideoQuality

private const val PROJECT_URL = "https://github.com/zahidhossin39/musely"
private const val ISSUES_URL = "$PROJECT_URL/issues/new/choose"
private const val LICENSE_URL = "$PROJECT_URL/blob/main/LICENSE"
private const val NOTICES_URL = "$PROJECT_URL/blob/main/THIRD_PARTY_NOTICES.md"

/** The Settings tab: app updates, library housekeeping and project links. */
@Composable
internal fun SettingsScreen(
    vm: MainViewModel,
    downloads: DownloadsViewModel,
    listState: LazyListState,
    bottomInset: Dp,
    onOpenSystemSettings: () -> Unit,
) {
    val updateStatus by vm.updateStatus.collectAsStateWithLifecycle()
    val hiddenSongs by vm.hiddenSongs.collectAsStateWithLifecycle()
    val defaultFormat by downloads.defaultFormat.collectAsStateWithLifecycle()
    val videoQuality by downloads.videoQuality.collectAsStateWithLifecycle()
    var showHiddenSheet by remember { mutableStateOf(false) }
    val openUrl: (String) -> Unit = { url -> vm.openUpdateUrl(url) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = bottomInset + 24.dp),
    ) {
        item(key = "header") {
            TabHeader(title = "Settings", subtitle = "Musely v${vm.installedVersion}")
        }

        item(key = "updates-title") { SettingsSectionTitle("App updates") }
        item(key = "updates") {
            UpdateCard(
                installedVersion = vm.installedVersion,
                status = updateStatus,
                onCheckAgain = vm::checkForUpdatesManually,
                onOpenUrl = openUrl,
                modifier = Modifier.padding(horizontal = ItemInset),
            )
        }

        item(key = "downloads-title") { SettingsSectionTitle("Downloads") }
        item(key = "downloads") {
            SettingsGroup {
                ChoiceRow(
                    iconRes = R.drawable.ic_download,
                    title = "Default format",
                    options = DownloadFormat.entries,
                    selected = defaultFormat,
                    label = { if (it == DownloadFormat.Audio) "Audio (M4A)" else "Video (MP4)" },
                    onSelect = downloads::setDefaultFormat,
                )
                SettingsDivider()
                ChoiceRow(
                    iconRes = R.drawable.ic_video,
                    title = "Video quality",
                    options = VideoQuality.entries,
                    selected = videoQuality,
                    label = { "Up to ${it.label}" },
                    onSelect = downloads::setVideoQuality,
                )
                SettingsDivider()
                SettingsRow(
                    iconRes = R.drawable.ic_note,
                    title = "Saved to",
                    subtitle = "Music/Musely for songs · Movies/Musely for videos",
                    onClick = null,
                )
            }
        }

        item(key = "library-title") { SettingsSectionTitle("Library") }
        item(key = "library") {
            SettingsGroup {
                SettingsRow(
                    iconRes = R.drawable.ic_visibility_off,
                    title = "Hidden songs & videos",
                    subtitle = hiddenSummary(hiddenSongs.size),
                    onClick = if (hiddenSongs.isNotEmpty()) { { showHiddenSheet = true } } else null,
                )
                SettingsDivider()
                SettingsRow(
                    iconRes = R.drawable.ic_info,
                    title = "App permissions",
                    subtitle = "Music, video and notification access",
                    onClick = onOpenSystemSettings,
                    trailingIconRes = R.drawable.ic_open_in_new,
                )
            }
        }

        item(key = "about-title") { SettingsSectionTitle("About") }
        item(key = "about") {
            SettingsGroup {
                SettingsRow(
                    iconRes = R.drawable.ic_note,
                    title = "Source code",
                    subtitle = "github.com/zahidhossin39/musely",
                    onClick = { openUrl(PROJECT_URL) },
                    trailingIconRes = R.drawable.ic_open_in_new,
                )
                SettingsDivider()
                SettingsRow(
                    iconRes = R.drawable.ic_info,
                    title = "Report a problem",
                    subtitle = "Open an issue on GitHub",
                    onClick = { openUrl(ISSUES_URL) },
                    trailingIconRes = R.drawable.ic_open_in_new,
                )
                SettingsDivider()
                SettingsRow(
                    iconRes = R.drawable.ic_check,
                    title = "License",
                    subtitle = "MIT",
                    onClick = { openUrl(LICENSE_URL) },
                    trailingIconRes = R.drawable.ic_open_in_new,
                )
                SettingsDivider()
                SettingsRow(
                    iconRes = R.drawable.ic_info,
                    title = "Open-source licenses",
                    subtitle = "Includes NewPipe Extractor (GPL-3.0)",
                    onClick = { openUrl(NOTICES_URL) },
                    trailingIconRes = R.drawable.ic_open_in_new,
                )
            }
        }
    }

    if (showHiddenSheet) {
        HiddenSongsSheet(
            hiddenSongs = hiddenSongs,
            onDismiss = { showHiddenSheet = false },
            onUnhideSong = { vm.unhideSong(it) },
            onUnhideAll = {
                vm.unhideAll()
                showHiddenSheet = false
            },
        )
    }
}

internal fun hiddenSummary(count: Int): String = when (count) {
    0 -> "Nothing hidden"
    1 -> "1 item hidden"
    else -> "$count items hidden"
}

@Composable
internal fun SettingsSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = ItemInset + 12.dp, end = ItemInset, top = 20.dp, bottom = 8.dp),
    )
}

/** Rounded container that groups related [SettingsRow]s. */
@Composable
internal fun SettingsGroup(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = ItemInset),
    ) {
        Column { content() }
    }
}

@Composable
internal fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 58.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

/** One tappable settings line. A null [onClick] renders it as read-only. */
@Composable
internal fun SettingsRow(
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
    @DrawableRes trailingIconRes: Int? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = colors.onSurfaceVariant,
        )
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        } else if (trailingIconRes != null) {
            Spacer(Modifier.width(12.dp))
            Icon(
                painterResource(trailingIconRes),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colors.onSurfaceVariant,
            )
        }
    }
}

/** A settings line whose value is picked from a short dropdown list. */
@Composable
internal fun <T> ChoiceRow(
    @DrawableRes iconRes: Int,
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SettingsRow(iconRes = iconRes, title = title, subtitle = label(selected), onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.padding(horizontal = 4.dp)) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    trailingIcon = if (option == selected) {
                        { Icon(painterResource(R.drawable.ic_check), contentDescription = "Selected", modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}
