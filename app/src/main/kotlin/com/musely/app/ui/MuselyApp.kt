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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musely.app.MainViewModel
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

/** Library, album/artist/playlist detail, the floating mini player and the full-screen player. */
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
    val visibleSongsById = remember(allVisibleTracks) { allVisibleTracks.associateBy { it.id } }

    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var detail by remember { mutableStateOf<LibraryDetail?>(null) }
    val gridState = rememberLazyGridState()
    // Lives here, not in LibraryScreen, so the tab survives a trip into an album, artist, or playlist.
    var section by rememberSaveable { mutableStateOf(Section.Songs) }
    val hasSong = playback.song != null
    val bottomInset = if (hasSong) MiniPlayerReserve else 0.dp

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

    val currentDetail = detail
    val resolvedDetail = remember(currentDetail, playlists, visibleSongsById) {
        if (currentDetail != null && currentDetail.kind == DetailKind.Playlist && currentDetail.playlistId != null) {
            val livePlaylist = playlists.find { it.id == currentDetail.playlistId }
            livePlaylist?.let { playlistDetail(it, visibleSongsById) }
        } else {
            currentDetail
        }
    }

    LaunchedEffect(currentDetail, resolvedDetail) {
        if (currentDetail != null && resolvedDetail == null) {
            detail = null
        }
    }

    LaunchedEffect(hasSong) {
        if (!hasSong && showPlayer) {
            showPlayer = false
        }
    }

    BackHandler(enabled = !showPlayer && resolvedDetail != null) { detail = null }
    BackHandler(enabled = showPlayer) { showPlayer = false }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            AnimatedContent(
                targetState = resolvedDetail,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "libraryContent",
            ) { open ->
                if (open == null) {
                    LibraryScreen(
                        vm = vm,
                        playback = playback,
                        gridState = gridState,
                        section = section,
                        onSectionChange = { section = it },
                        hasVideoAccess = hasVideoAccess,
                        hasPartialVideoAccess = hasPartialVideoAccess,
                        videoDeclined = videoDeclined,
                        onRequestVideoAccess = onRequestVideoAccess,
                        onOpenSettings = onOpenSettings,
                        bottomInset = bottomInset,
                        songActions = songActions,
                        onOpenDetail = { detail = it },
                        onMessage = showMessage,
                    )
                } else {
                    DetailScreen(
                        detail = open,
                        vm = vm,
                        bottomInset = bottomInset,
                        songActions = songActions,
                        onBack = { detail = null },
                        onOpenNowPlaying = { showPlayer = true },
                        onMessage = showMessage,
                    )
                }
            }
            AnimatedVisibility(
                visible = hasSong && !showPlayer,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                MiniPlayer(vm, playback, onOpen = { showPlayer = true })
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
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(bottom = if (hasSong && !showPlayer) MiniPlayerReserve else 12.dp),
        )
    }
}
