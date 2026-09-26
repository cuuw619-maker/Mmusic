package com.example.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PlaybackState
import com.example.model.RepeatMode
import com.example.model.Song
import com.example.ui.animation.FluidMotionConstants
import com.example.ui.animation.HapticType
import com.example.ui.animation.calculateRubberBandOffset
import com.example.ui.animation.fluidPress
import com.example.ui.animation.performHapticFeedback
import com.example.ui.components.AppIcons
import com.example.ui.components.InteractiveProgressBar
import com.example.ui.components.MorphingPlayPauseButton
import com.example.ui.components.TrackArtwork
import com.example.ui.dialogs.TrackDetailsDialog
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Fullscreen Now Playing Screen with Material 3 Expressive aesthetics:
 * - Rounded rectangle play/pause button.
 * - Symmetrical pause icon & play triangle morph.
 * - Vertical pill thumb progress bar with time bubble.
 * - Horizontal swipe on artwork for Next/Previous track.
 * - Vertical swipe does NOT accidentally close player (only dedicated collapse button closes it).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    playbackState: PlaybackState,
    onCollapse: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onRewind10: () -> Unit,
    onForward10: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onOpenQueue: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onOpenSleepTimer: () -> Unit,
    currentSpeed: Float = 1.0f,
    currentPitchSemitones: Int = 0,
    onSpeedChanged: (Float) -> Unit = {},
    onPitchChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentSong = playbackState.currentSong ?: return
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var showSpeedPitchSheet by remember { mutableStateOf(false) }
    var showTrackDetailsDialog by remember { mutableStateOf(false) }

    val artOffsetX = remember { Animatable(0f) }
    var isForwardTransition by remember { mutableStateOf(true) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
                            MaterialTheme.colorScheme.surfaceContainerLowest,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top App Bar with Close / Collapse Button
                TopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "СЕЙЧАС ИГРАЕТ",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (currentSong.album.isNotBlank()) {
                                Text(
                                    text = currentSong.album,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                performHapticFeedback(view, HapticType.LIGHT)
                                onCollapse()
                            },
                            modifier = Modifier.testTag("now_playing_collapse")
                        ) {
                            Icon(
                                imageVector = AppIcons.ExpandMore,
                                contentDescription = "Свернуть",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                performHapticFeedback(view, HapticType.LIGHT)
                                onOpenSleepTimer()
                            }
                        ) {
                            Icon(
                                imageVector = AppIcons.SleepTimer,
                                contentDescription = "Таймер сна",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                performHapticFeedback(view, HapticType.LIGHT)
                                showSpeedPitchSheet = true
                            }
                        ) {
                            Icon(
                                imageVector = AppIcons.Speed,
                                contentDescription = "Скорость и тональность",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                performHapticFeedback(view, HapticType.LIGHT)
                                showTrackDetailsDialog = true
                            }
                        ) {
                            Icon(
                                imageVector = AppIcons.MoreVert,
                                contentDescription = "О треке",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Artwork with Horizontal Swipe gestures for Track Switching
                // (Vertical gestures are NOT consumed to prevent accidental closing)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .aspectRatio(1f)
                        .offset { IntOffset(artOffsetX.value.roundToInt(), 0) }
                        .shadow(
                            elevation = 16.dp,
                            shape = RoundedCornerShape(24.dp),
                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        )
                        .clip(RoundedCornerShape(24.dp))
                        .pointerInput(currentSong.id) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    val threshold = 120f
                                    if (artOffsetX.value < -threshold) {
                                        // Swipe Left -> Next Track
                                        isForwardTransition = true
                                        performHapticFeedback(view, HapticType.LIGHT)
                                        onSkipNext()
                                    } else if (artOffsetX.value > threshold) {
                                        // Swipe Right -> Previous Track
                                        isForwardTransition = false
                                        performHapticFeedback(view, HapticType.LIGHT)
                                        onSkipPrevious()
                                    }
                                    scope.launch {
                                        artOffsetX.animateTo(0f, FluidMotionConstants.dragSpring())
                                    }
                                },
                                onDragCancel = {
                                    scope.launch { artOffsetX.animateTo(0f) }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    scope.launch {
                                        artOffsetX.snapTo(calculateRubberBandOffset(artOffsetX.value + dragAmount, 0.25f))
                                    }
                                }
                            )
                        }
                        .pointerInput(currentSong.id) {
                            detectTapGestures(
                                onDoubleTap = {
                                    performHapticFeedback(view, HapticType.TOGGLE)
                                    onToggleFavorite(currentSong.id)
                                },
                                onLongPress = {
                                    performHapticFeedback(view, HapticType.HEAVY)
                                    showTrackDetailsDialog = true
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = currentSong,
                        transitionSpec = {
                            if (isForwardTransition) {
                                (slideInHorizontally(FluidMotionConstants.artworkSpring()) { width -> (width * 0.3f).toInt() } + fadeIn(FluidMotionConstants.pressSpring())) togetherWith
                                (slideOutHorizontally(FluidMotionConstants.artworkSpring()) { width -> (-width * 0.3f).toInt() } + fadeOut(FluidMotionConstants.pressSpring()))
                            } else {
                                (slideInHorizontally(FluidMotionConstants.artworkSpring()) { width -> (-width * 0.3f).toInt() } + fadeIn(FluidMotionConstants.pressSpring())) togetherWith
                                (slideOutHorizontally(FluidMotionConstants.artworkSpring()) { width -> (width * 0.3f).toInt() } + fadeOut(FluidMotionConstants.pressSpring()))
                            }
                        },
                        label = "now_playing_art_anim"
                    ) { song ->
                        TrackArtwork(
                            artUriString = song.albumArtUriString,
                            contentDescription = song.title,
                            cornerRadius = 24.dp,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Title, Artist and Favorite
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentSong.title,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("now_playing_title")
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentSong.artist,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("now_playing_artist")
                        )
                    }

                    IconButton(
                        onClick = {
                            performHapticFeedback(view, HapticType.TOGGLE)
                            onToggleFavorite(currentSong.id)
                        }
                    ) {
                        Icon(
                            imageVector = if (currentSong.isFavorite) AppIcons.Favorite else AppIcons.FavoriteBorder,
                            contentDescription = if (currentSong.isFavorite) "Удалить из избранного" else "В избранное",
                            tint = if (currentSong.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Seek Bar with Vertical Pill Thumb
                Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                    InteractiveProgressBar(
                        currentPositionMs = playbackState.currentPositionMs,
                        durationMs = playbackState.durationMs,
                        onSeekTo = onSeekTo
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Primary Controls: Shuffle, Previous, Play/Pause, Next, Repeat
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle
                    IconButton(
                        onClick = {
                            performHapticFeedback(view, HapticType.LIGHT)
                            onToggleShuffle()
                        },
                        modifier = Modifier.testTag("now_playing_shuffle")
                    ) {
                        Icon(
                            imageVector = AppIcons.Shuffle,
                            contentDescription = "Случайный порядок",
                            tint = if (playbackState.shuffleModeEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            }
                        )
                    }

                    // Previous Track
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .fluidPress(
                                scaleDown = 0.90f,
                                hapticType = HapticType.LIGHT,
                                onClick = onSkipPrevious
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.Previous,
                            contentDescription = "Предыдущий",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    // Main Morphing Play / Pause Button - Rounded Rectangle!
                    MorphingPlayPauseButton(
                        isPlaying = playbackState.isPlaying,
                        onClick = onPlayPause,
                        width = 86.dp,
                        height = 62.dp,
                        iconSize = 32.dp,
                        cornerRadius = 24.dp,
                        modifier = Modifier.testTag("now_playing_play_pause")
                    )

                    // Next Track
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .fluidPress(
                                scaleDown = 0.90f,
                                hapticType = HapticType.LIGHT,
                                onClick = onSkipNext
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.Next,
                            contentDescription = "Следующий",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    // Repeat
                    IconButton(
                        onClick = {
                            performHapticFeedback(view, HapticType.LIGHT)
                            onCycleRepeatMode()
                        },
                        modifier = Modifier.testTag("now_playing_repeat")
                    ) {
                        val repeatIcon = when (playbackState.repeatMode) {
                            RepeatMode.ONE -> AppIcons.RepeatOne
                            else -> AppIcons.Repeat
                        }
                        val repeatTint = when (playbackState.repeatMode) {
                            RepeatMode.OFF -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.primary
                        }
                        Icon(
                            imageVector = repeatIcon,
                            contentDescription = "Режим повтора",
                            tint = repeatTint
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Bottom Quick Action Bar (Queue, Equalizer, Add to Playlist)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 36.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            performHapticFeedback(view, HapticType.LIGHT)
                            onOpenQueue()
                        },
                        modifier = Modifier.testTag("now_playing_queue")
                    ) {
                        Icon(
                            imageVector = AppIcons.Queue,
                            contentDescription = "Очередь воспроизведения",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = {
                            performHapticFeedback(view, HapticType.LIGHT)
                            onOpenEqualizer()
                        },
                        modifier = Modifier.testTag("now_playing_equalizer")
                    ) {
                        Icon(
                            imageVector = AppIcons.Equalizer,
                            contentDescription = "Эквалайзер",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = {
                            performHapticFeedback(view, HapticType.LIGHT)
                            onAddToPlaylist(currentSong)
                        },
                        modifier = Modifier.testTag("now_playing_add_to_playlist")
                    ) {
                        Icon(
                            imageVector = AppIcons.PlaylistAdd,
                            contentDescription = "Добавить в плейлист",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showSpeedPitchSheet) {
        SpeedPitchBottomSheet(
            currentSpeed = currentSpeed,
            currentPitchSemitones = currentPitchSemitones,
            onSpeedChanged = onSpeedChanged,
            onPitchChanged = onPitchChanged,
            onDismiss = { showSpeedPitchSheet = false }
        )
    }

    if (showTrackDetailsDialog) {
        TrackDetailsDialog(
            song = currentSong,
            onDismiss = { showTrackDetailsDialog = false }
        )
    }
}
