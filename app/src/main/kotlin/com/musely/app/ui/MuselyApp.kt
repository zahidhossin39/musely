package com.musely.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musely.app.MainViewModel
import com.musely.app.Playlist
import com.musely.app.Song
import com.musely.app.UpdateStatus
import com.musely.app.audioPermission
import com.musely.app.hasAudioAccess
import com.musely.app.hasPartialVideoAccess
import com.musely.app.hasVideoAccess
import kotlinx.coroutines.launch

private val videoPermissions: Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

@Composable
fun MuselyApp(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val openAppSettings = remember(context) {
        {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            )
        }
    }

    var granted by remember { mutableStateOf(hasAudioAccess(context)) }
    var declined by rememberSaveable { mutableStateOf(false) }
    var videoGranted by remember { mutableStateOf(hasVideoAccess(context)) }
    var partialVideoAccess by remember { mutableStateOf(hasPartialVideoAccess(context)) }
    var videoDeclined by rememberSaveable { mutableStateOf(false) }

    val refreshVideoAccess = {
        val access = hasVideoAccess(context)
        videoGranted = access
        partialVideoAccess = hasPartialVideoAccess(context)
        if (access) videoDeclined = false
    }

    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        declined = !ok
        if (ok) {
            refreshVideoAccess()
        }
    }
    val requestVideo = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        val access = hasVideoAccess(context)
        videoGranted = access
        partialVideoAccess = hasPartialVideoAccess(context)
        videoDeclined = !access
        if (access) {
            vm.loadLibrary()
        }
    }

    // Picks up access granted or modified from system settings when the user comes back.
    LifecycleResumeEffect(Unit) {
        val prevAudio = granted
        val prevVideo = videoGranted
        val prevPartial = partialVideoAccess
        granted = hasAudioAccess(context)
        refreshVideoAccess()
        if (granted && (granted != prevAudio || videoGranted != prevVideo || partialVideoAccess != prevPartial)) {
            vm.loadLibrary()
        }
        onPauseOrDispose { }
    }
    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.checkForUpdatesOnResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(granted) { if (granted) vm.loadLibrary() }

    if (granted) {
        Player(
            vm = vm,
            hasVideoAccess = videoGranted,
            hasPartialVideoAccess = partialVideoAccess,
            videoDeclined = videoDeclined,
            onRequestVideoAccess = { requestVideo.launch(videoPermissions) },
            onOpenSettings = openAppSettings,
        )
    } else {
        WelcomeScreen(
            declined = declined,
            onAllow = { request.launch(audioPermission()) },
            onOpenSettings = openAppSettings,
        )
    }
}

/**
 * The main shell: bottom navigation between Home, Playlists, Downloads and Settings, each tab's
 * album/artist/playlist detail, the floating mini player and the full-screen player.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Player(
    vm: MainViewModel,
    hasVideoAccess: Boolean,
    hasPartialVideoAccess: Boolean,
    videoDeclined: Boolean,
    onRequestVideoAccess: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val playback by vm.playback.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val allVisibleTracks by vm.allVisibleTracks.collectAsStateWithLifecycle()
    val updateStatus by vm.updateStatus.collectAsStateWithLifecycle()
    val visibleSongsById = remember(allVisibleTracks) { allVisibleTracks.associateBy { it.id } }

    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(MainTab.Home) }
    // Each tab keeps its own drill-in so switching tabs and coming back lands where you left off.
    var homeDetail by remember { mutableStateOf<LibraryDetail?>(null) }
    var playlistsDetail by remember { mutableStateOf<LibraryDetail?>(null) }
    val homeGridState = rememberLazyGridState()
    val playlistsGridState = rememberLazyGridState()
    val downloadsListState = rememberLazyListState()
    val settingsListState = rememberLazyListState()
    // Lives here, not in LibraryScreen, so the section survives a trip into an album or artist.
    var section by rememberSaveable { mutableStateOf(Section.Songs) }
    val hasSong = playback.song != null
    val bottomInset = if (hasSong) MiniPlayerReserve else 0.dp
    val imeVisible = WindowInsets.isImeVisible

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String, String?, (() -> Unit)?) -> Unit = remember(snackbarHostState, scope) {
        { message, actionLabel, onAction ->
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                val result = snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = actionLabel,
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    onAction?.invoke()
                }
            }
        }
    }

    val songActions = rememberSongActionsUi(vm = vm, onMessage = showMessage)

    LaunchedEffect(vm, showMessage) {
        vm.playbackMessages.collect { message ->
            showMessage(message, null, null)
        }
    }

    val resolvedHomeDetail = rememberLiveDetail(homeDetail, playlists, visibleSongsById)
    val resolvedPlaylistsDetail = rememberLiveDetail(playlistsDetail, playlists, visibleSongsById)
    LaunchedEffect(homeDetail, resolvedHomeDetail) {
        if (homeDetail != null && resolvedHomeDetail == null) homeDetail = null
    }
    LaunchedEffect(playlistsDetail, resolvedPlaylistsDetail) {
        if (playlistsDetail != null && resolvedPlaylistsDetail == null) playlistsDetail = null
    }

    LaunchedEffect(hasSong) {
        if (!hasSong && showPlayer) {
            showPlayer = false
        }
    }

    val currentTabDetail = when (tab) {
        MainTab.Home -> resolvedHomeDetail
        MainTab.Playlists -> resolvedPlaylistsDetail
        MainTab.Downloads, MainTab.Settings -> null
    }
    val closeCurrentDetail: () -> Unit = {
        when (tab) {
            MainTab.Home -> homeDetail = null
            MainTab.Playlists -> playlistsDetail = null
            MainTab.Downloads, MainTab.Settings -> Unit
        }
    }
    val selectTab: (MainTab) -> Unit = { target ->
        if (target == tab) {
            // Re-selecting the current tab pops its drill-in, like most music apps.
            closeCurrentDetail()
        } else {
            tab = target
        }
    }

    // Composed later = higher priority: player first, then detail, then tab root back to Home.
    val tabBack = backDestination(tab)
    BackHandler(enabled = !showPlayer && currentTabDetail == null && tabBack != null) {
        tabBack?.let { tab = it }
    }
    BackHandler(enabled = !showPlayer && currentTabDetail != null) { closeCurrentDetail() }
    BackHandler(enabled = showPlayer) { showPlayer = false }

    val badgedTabs = remember(updateStatus) {
        if (updateStatus is UpdateStatus.Available) setOf(MainTab.Settings) else emptySet()
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    )
                    .then(if (imeVisible) Modifier.imePadding() else Modifier),
            ) {
                AnimatedContent(
                    targetState = TabScreen(tab, currentTabDetail),
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    contentKey = { it.key },
                    label = "tabContent",
                ) { screen ->
                    val open = screen.detail
                    when {
                        open != null -> DetailScreen(
                            detail = open,
                            vm = vm,
                            bottomInset = bottomInset,
                            songActions = songActions,
                            onBack = closeCurrentDetail,
                            onOpenNowPlaying = { showPlayer = true },
                            onMessage = showMessage,
                        )
                        screen.tab == MainTab.Home -> LibraryScreen(
                            vm = vm,
                            playback = playback,
                            gridState = homeGridState,
                            section = section,
                            onSectionChange = { section = it },
                            hasVideoAccess = hasVideoAccess,
                            hasPartialVideoAccess = hasPartialVideoAccess,
                            videoDeclined = videoDeclined,
                            onRequestVideoAccess = onRequestVideoAccess,
                            onOpenSettings = onOpenSettings,
                            bottomInset = bottomInset,
                            songActions = songActions,
                            onOpenDetail = { homeDetail = it },
                        )
                        screen.tab == MainTab.Playlists -> PlaylistsScreen(
                            vm = vm,
                            gridState = playlistsGridState,
                            bottomInset = bottomInset,
                            onOpenDetail = { playlistsDetail = it },
                            onMessage = showMessage,
                        )
                        screen.tab == MainTab.Downloads -> DownloadsScreen(listState = downloadsListState, bottomInset = bottomInset)
                        else -> SettingsScreen(
                            vm = vm,
                            listState = settingsListState,
                            bottomInset = bottomInset,
                            onOpenSystemSettings = onOpenSettings,
                        )
                    }
                }
                // Fully qualified: inside the Column, the ColumnScope overload would otherwise win.
                androidx.compose.animation.AnimatedVisibility(
                    visible = hasSong && !showPlayer,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    MiniPlayer(vm, playback, onOpen = { showPlayer = true })
                }
            }
            // The keyboard covers the bar anyway; dropping it keeps text fields above the keyboard.
            if (!imeVisible) {
                MuselyBottomBar(
                    current = tab,
                    onSelect = selectTab,
                    badgedTabs = badgedTabs,
                )
            }
        }
        AnimatedVisibility(
            visible = hasSong && showPlayer,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            NowPlayingScreen(
                vm = vm,
                songActions = songActions,
                onClose = { showPlayer = false },
            )
        }
        // Last child so messages also show above the full-screen player.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(
                    bottom = snackbarBottomPadding(
                        playerOpen = showPlayer,
                        barVisible = !imeVisible,
                        miniPlayerVisible = hasSong,
                    ),
                ),
        )
    }
}

/** Height of the Material 3 navigation bar above the system navigation inset. */
private val BottomBarHeight = 80.dp

/** Keeps snackbars clear of the bottom bar and mini player, or just off the edge over the player. */
internal fun snackbarBottomPadding(playerOpen: Boolean, barVisible: Boolean, miniPlayerVisible: Boolean): Dp {
    if (playerOpen) return 12.dp
    val bar = if (barVisible) BottomBarHeight else 0.dp
    val mini = if (miniPlayerVisible) MiniPlayerReserve else 12.dp
    return bar + mini
}

/** What the content area shows: a tab's root, or a detail drilled into from that tab. */
private data class TabScreen(val tab: MainTab, val detail: LibraryDetail?) {
    /** Stable identity so live playlist edits update in place instead of cross-fading. */
    val key: Any get() = Triple(tab, detail?.kind, detail?.playlistId ?: detail?.title)
}

/**
 * Keeps an open playlist detail in sync with live edits (renames, added or removed tracks) and
 * returns null once the playlist it shows has been deleted.
 */
@Composable
private fun rememberLiveDetail(
    detail: LibraryDetail?,
    playlists: List<Playlist>,
    songsById: Map<Long, Song>,
): LibraryDetail? = remember(detail, playlists, songsById) {
    if (detail != null && detail.kind == DetailKind.Playlist && detail.playlistId != null) {
        playlists.find { it.id == detail.playlistId }?.let { playlistDetail(it, songsById) }
    } else {
        detail
    }
}
