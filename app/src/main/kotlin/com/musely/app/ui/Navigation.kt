package com.musely.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.musely.app.R
import com.musely.app.ui.theme.LocalSpotlight

/** Top-level destinations shown in the bottom navigation bar, in display order. */
internal enum class MainTab(val label: String, @param:DrawableRes val iconRes: Int) {
    Home("Home", R.drawable.ic_home),
    Playlists("Playlists", R.drawable.ic_library),
    Downloads("Downloads", R.drawable.ic_download),
    Settings("Settings", R.drawable.ic_settings),
}

/**
 * Decides where system back goes from the root of a tab: every tab other than Home returns to
 * Home first, and Home lets the system handle back (closing the app).
 */
internal fun backDestination(current: MainTab): MainTab? =
    if (current == MainTab.Home) null else MainTab.Home

/**
 * Bottom navigation bar. [badgedTabs] get a small dot, e.g. Settings when an update is ready
 * or Downloads while something is in progress.
 */
@Composable
internal fun MuselyBottomBar(
    current: MainTab,
    onSelect: (MainTab) -> Unit,
    badgedTabs: Set<MainTab>,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val spotlight = LocalSpotlight.current
    NavigationBar(
        modifier = modifier,
        containerColor = colors.surfaceContainer,
    ) {
        MainTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == current,
                onClick = { onSelect(tab) },
                icon = {
                    BadgedBox(
                        badge = {
                            if (tab in badgedTabs) {
                                Badge(containerColor = spotlight.container)
                            }
                        },
                    ) {
                        Icon(painterResource(tab.iconRes), contentDescription = null)
                    }
                },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = spotlight.content,
                    indicatorColor = spotlight.container,
                    selectedTextColor = colors.onSurface,
                    unselectedIconColor = colors.onSurfaceVariant,
                    unselectedTextColor = colors.onSurfaceVariant,
                ),
            )
        }
    }
}

/** Large title + supporting line used at the top of the Playlists, Downloads and Settings tabs. */
@Composable
internal fun TabHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(start = ItemInset, end = ItemInset, top = 24.dp, bottom = 16.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
