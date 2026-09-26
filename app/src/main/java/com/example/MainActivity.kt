package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.Song
import com.example.ui.dialogs.AddToPlaylistDialog
import com.example.ui.dialogs.CreatePlaylistDialog
import com.example.ui.dialogs.SleepTimerBottomSheet
import com.example.ui.equalizer.EqualizerSheet
import com.example.ui.favorites.FavoritesScreen
import com.example.ui.home.HomeScreen
import com.example.ui.library.LibraryScreen
import com.example.ui.navigation.NavDestination
import com.example.ui.permissions.PermissionScreen
import com.example.ui.player.ExpandingPlayerContainer
import com.example.ui.player.MiniPlayer
import com.example.ui.player.NowPlayingScreen
import com.example.ui.player.QueueSheet
import com.example.ui.playlists.PlaylistsScreen
import com.example.ui.search.SearchScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.MusicAppTheme
import com.example.ui.viewmodel.MusicViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MusicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
            val amoledDark by viewModel.amoledDark.collectAsStateWithLifecycle()

            MusicAppTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                amoledDark = amoledDark
            ) {
                com.example.ui.theme.ProvideResponsiveLayout {
                    MainApp(viewModel = viewModel, checkPermission = ::hasAudioPermission)
                }
            }
        }
    }

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
fun MainApp(
    viewModel: MusicViewModel,
    checkPermission: () -> Boolean
) {
    var hasPermission by remember { mutableStateOf(checkPermission()) }
    var currentDestination by remember { mutableStateOf(viewModel.preferencesManager.selectedDestination.value) }

    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val isNowPlayingExpanded by viewModel.isNowPlayingExpanded.collectAsStateWithLifecycle()
    val isQueueVisible by viewModel.isQueueSheetVisible.collectAsStateWithLifecycle()
    val isEqualizerVisible by viewModel.isEqualizerSheetVisible.collectAsStateWithLifecycle()
    val isSleepTimerVisible by viewModel.isSleepTimerSheetVisible.collectAsStateWithLifecycle()
    val isCreatePlaylistVisible by viewModel.isCreatePlaylistDialogVisible.collectAsStateWithLifecycle()
    val songToAddToPlaylist by viewModel.songToAddToPlaylist.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val equalizerState by viewModel.equalizerState.collectAsStateWithLifecycle()
    val sleepTimerState by viewModel.sleepTimerState.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val pitchSemitones by viewModel.pitchSemitones.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(playbackState.errorMessage) {
        val err = playbackState.errorMessage
        if (!err.isNullOrEmpty()) {
            snackbarHostState.showSnackbar(err)
        }
    }

    if (!hasPermission) {
        PermissionScreen(
            onPermissionGranted = {
                hasPermission = true
                viewModel.scanLibrary()
            }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideScreen = maxWidth >= 600.dp

            if (isWideScreen) {
                // Adaptive Tablet / Foldable layout: Side Navigation Rail
                Row(modifier = Modifier.fillMaxSize()) {
                    com.example.ui.navigation.ExpressiveNavigationRail(
                        currentDestination = currentDestination,
                        onNavigate = { currentDestination = it; viewModel.preferencesManager.setSelectedDestination(it) }
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            snackbarHost = { SnackbarHost(snackbarHostState) }
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                AppScreenContent(
                                    destination = currentDestination,
                                    viewModel = viewModel,
                                    onNavigate = { currentDestination = it; viewModel.preferencesManager.setSelectedDestination(it) }
                                )
                            }
                        }

                        if (playbackState.currentSong != null) {
                            ExpandingPlayerContainer(
                                isExpanded = isNowPlayingExpanded,
                                onExpandedChange = { viewModel.setNowPlayingExpanded(it) },
                                playbackState = playbackState,
                                onPlayPause = { viewModel.playPause() },
                                onSkipNext = { viewModel.skipToNext() },
                                onSkipPrevious = { viewModel.skipToPrevious() },
                                onSeekTo = { viewModel.seekTo(it) },
                                onRewind10 = { viewModel.rewind10() },
                                onForward10 = { viewModel.forward10() },
                                onToggleShuffle = { viewModel.toggleShuffle() },
                                onCycleRepeatMode = { viewModel.cycleRepeatMode() },
                                onToggleFavorite = { viewModel.toggleFavorite(it) },
                                onOpenQueue = { viewModel.setQueueSheetVisible(true) },
                                onOpenEqualizer = { viewModel.setEqualizerSheetVisible(true) },
                                onAddToPlaylist = { viewModel.setSongToAddToPlaylist(it) },
                                onOpenSleepTimer = { viewModel.setSleepTimerSheetVisible(true) },
                                currentSpeed = playbackSpeed,
                                currentPitchSemitones = pitchSemitones,
                                onSpeedChanged = { viewModel.setPlaybackSpeed(it) },
                                onPitchChanged = { viewModel.setPitchSemitones(it) },
                                bottomNavOffset = 8.dp
                            )
                        }
                    }
                }
            } else {
                // Phone layout: Expressive Floating Capsule Navigation Bar + Morphing Player
                val navBarTranslationY by animateDpAsState(
                    targetValue = if (isNowPlayingExpanded) 110.dp else 0.dp,
                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                    label = "phone_nav_bar_slide"
                )
                val navBarAlpha by animateFloatAsState(
                    targetValue = if (isNowPlayingExpanded) 0f else 1f,
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                    label = "phone_nav_bar_alpha"
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        snackbarHost = { SnackbarHost(snackbarHostState) }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            AppScreenContent(
                                destination = currentDestination,
                                viewModel = viewModel,
                                onNavigate = { currentDestination = it; viewModel.preferencesManager.setSelectedDestination(it) }
                            )
                        }
                    }

                    // Morphing Mini Player <-> Full Now Playing Screen
                    if (playbackState.currentSong != null) {
                        ExpandingPlayerContainer(
                            isExpanded = isNowPlayingExpanded,
                            onExpandedChange = { viewModel.setNowPlayingExpanded(it) },
                            playbackState = playbackState,
                            onPlayPause = { viewModel.playPause() },
                            onSkipNext = { viewModel.skipToNext() },
                            onSkipPrevious = { viewModel.skipToPrevious() },
                            onSeekTo = { viewModel.seekTo(it) },
                            onRewind10 = { viewModel.rewind10() },
                            onForward10 = { viewModel.forward10() },
                            onToggleShuffle = { viewModel.toggleShuffle() },
                            onCycleRepeatMode = { viewModel.cycleRepeatMode() },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onOpenQueue = { viewModel.setQueueSheetVisible(true) },
                            onOpenEqualizer = { viewModel.setEqualizerSheetVisible(true) },
                            onAddToPlaylist = { viewModel.setSongToAddToPlaylist(it) },
                            onOpenSleepTimer = { viewModel.setSleepTimerSheetVisible(true) },
                            currentSpeed = playbackSpeed,
                            currentPitchSemitones = pitchSemitones,
                            onSpeedChanged = { viewModel.setPlaybackSpeed(it) },
                            onPitchChanged = { viewModel.setPitchSemitones(it) },
                            bottomNavOffset = 76.dp
                        )
                    }

                    // Floating Navigation Bar at the bottom
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .offset(y = navBarTranslationY)
                            .graphicsLayer { alpha = navBarAlpha }
                    ) {
                        com.example.ui.navigation.ExpressiveFloatingNavigationBar(
                            currentDestination = currentDestination,
                            onNavigate = { currentDestination = it; viewModel.preferencesManager.setSelectedDestination(it) }
                        )
                    }
                }
            }
        }
    }


    // Modal Bottom Sheets & Dialogs
    if (isQueueVisible) {
        QueueSheet(
            playbackState = playbackState,
            onDismiss = { viewModel.setQueueSheetVisible(false) },
            onPlayQueueIndex = { viewModel.playQueueIndex(it) },
            onRemoveFromQueue = { viewModel.removeFromQueue(it) },
            onReorderQueue = { from, to -> viewModel.reorderQueue(from, to) },
            onClearQueue = { viewModel.clearQueue() }
        )
    }

    if (isEqualizerVisible) {
        EqualizerSheet(
            equalizerState = equalizerState,
            onToggleEnabled = { viewModel.setEqualizerEnabled(it) },
            onBandLevelChange = { band, level -> viewModel.setEqualizerBandLevel(band, level) },
            onBassBoostChange = { viewModel.setBassBoost(it) },
            onVirtualizerChange = { viewModel.setVirtualizer(it) },
            onLoudnessEnhancerChange = { viewModel.setLoudnessEnhancer(it) },
            onSelectPreset = { viewModel.useEqualizerPreset(it) },
            onDismiss = { viewModel.setEqualizerSheetVisible(false) }
        )
    }

    if (isSleepTimerVisible) {
        SleepTimerBottomSheet(
            sleepTimerState = sleepTimerState,
            onSelectOption = { viewModel.setSleepTimer(it) },
            onCancelTimer = { viewModel.cancelSleepTimer() },
            onDismiss = { viewModel.setSleepTimerSheetVisible(false) }
        )
    }

    if (isCreatePlaylistVisible) {
        CreatePlaylistDialog(
            onDismiss = { viewModel.setCreatePlaylistDialogVisible(false) },
            onConfirm = { name ->
                viewModel.createPlaylist(name)
                viewModel.setCreatePlaylistDialogVisible(false)
            }
        )
    }

    songToAddToPlaylist?.let { song ->
        AddToPlaylistDialog(
            song = song,
            playlists = playlists,
            onDismiss = { viewModel.setSongToAddToPlaylist(null) },
            onSelectPlaylist = { playlistId ->
                viewModel.addSongToPlaylist(playlistId, song.id)
                viewModel.setSongToAddToPlaylist(null)
            },
            onCreateNewPlaylist = {
                viewModel.setSongToAddToPlaylist(null)
                viewModel.setCreatePlaylistDialogVisible(true)
            }
        )
    }
}

private val SCREEN_ORDER = listOf(
    NavDestination.HOME,
    NavDestination.SEARCH,
    NavDestination.LIBRARY,
    NavDestination.PLAYLISTS,
    NavDestination.FAVORITES,
    NavDestination.SETTINGS
)

@Composable
private fun AppScreenContent(
    destination: NavDestination,
    viewModel: MusicViewModel,
    onNavigate: (NavDestination) -> Unit
) {
    AnimatedContent(
        targetState = destination,
        transitionSpec = {
            val initialIndex = SCREEN_ORDER.indexOf(initialState).coerceAtLeast(0)
            val targetIndex = SCREEN_ORDER.indexOf(targetState).coerceAtLeast(0)
            val direction = if (targetIndex >= initialIndex) 1 else -1
            val duration = 280
            val easing = FastOutSlowInEasing

            val enterTransition = slideInHorizontally(
                animationSpec = tween(duration, easing = easing)
            ) { width -> direction * width } +
            fadeIn(
                animationSpec = tween(duration, easing = easing)
            ) +
            scaleIn(
                initialScale = 0.96f,
                animationSpec = tween(duration, easing = easing)
            )

            val exitTransition = slideOutHorizontally(
                animationSpec = tween(duration, easing = easing)
            ) { width -> -direction * width } +
            fadeOut(
                animationSpec = tween(duration, easing = easing)
            ) +
            scaleOut(
                targetScale = 0.96f,
                animationSpec = tween(duration, easing = easing)
            )

            enterTransition togetherWith exitTransition
        },
        label = "DirectionalScreenTransition"
    ) { targetDest ->
        when (targetDest) {
            NavDestination.HOME -> HomeScreen(
                viewModel = viewModel,
                onNavigateToLibrary = { onNavigate(NavDestination.LIBRARY) },
                onNavigateToFavorites = { onNavigate(NavDestination.FAVORITES) },
                onNavigateToPlaylists = { onNavigate(NavDestination.PLAYLISTS) }
            )
            NavDestination.SEARCH -> SearchScreen(
                viewModel = viewModel,
                onSongSelected = { song -> viewModel.playSong(song) },
                onAlbumSelected = { albumId, _ ->
                    val album = viewModel.albums.value.find { it.id == albumId }
                    viewModel.selectAlbum(album)
                    onNavigate(NavDestination.LIBRARY)
                },
                onArtistSelected = { artistName ->
                    val artist = viewModel.artists.value.find { it.name.equals(artistName, ignoreCase = true) }
                    viewModel.selectArtist(artist)
                    onNavigate(NavDestination.LIBRARY)
                }
            )
            NavDestination.LIBRARY -> LibraryScreen(viewModel = viewModel)
            NavDestination.PLAYLISTS -> PlaylistsScreen(viewModel = viewModel)
            NavDestination.FAVORITES -> FavoritesScreen(
                viewModel = viewModel,
                onNavigateToLibrary = { onNavigate(NavDestination.LIBRARY) }
            )
            NavDestination.SETTINGS -> SettingsScreen(viewModel = viewModel)
        }
    }
}
