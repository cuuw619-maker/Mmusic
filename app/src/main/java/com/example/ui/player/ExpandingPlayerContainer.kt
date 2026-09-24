package com.example.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.PlaybackState
import com.example.model.Song
import com.example.ui.animation.HapticType
import com.example.ui.animation.fluidPress
import com.example.ui.animation.performHapticFeedback
import com.example.ui.components.AppIcons
import com.example.ui.components.MorphingPlayPauseButton

/**
 * Geometric Morphing Container: Mini Player <-> Fullscreen Now Playing.
 *
 * Smoothly animates:
 * - Container bounds (height, width, corner radius, elevation, padding)
 * - Artwork physical position, scale, and rounded corners
 * - Song title & artist typography and positioning
 * - Morphing Play/Pause button and controls
 * - Bottom progress line expanding into full scrubber
 */
@Composable
fun ExpandingPlayerContainer(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    playbackState: PlaybackState,
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
    currentSpeed: Float,
    currentPitchSemitones: Int,
    onSpeedChanged: (Float) -> Unit,
    onPitchChanged: (Int) -> Unit,
    bottomNavOffset: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val song = playbackState.currentSong ?: return
    val view = LocalView.current

    BackHandler(enabled = isExpanded) {
        onExpandedChange(false)
    }

    val expansionProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "player_expansion_progress"
    )

    val p = expansionProgress

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val totalWidth = maxWidth
        val totalHeight = maxHeight

        // When fully expanded, display the full interactive NowPlayingScreen with all gestures
        if (p >= 0.999f && isExpanded) {
            NowPlayingScreen(
                playbackState = playbackState,
                onCollapse = { onExpandedChange(false) },
                onPlayPause = onPlayPause,
                onSkipNext = onSkipNext,
                onSkipPrevious = onSkipPrevious,
                onSeekTo = onSeekTo,
                onRewind10 = onRewind10,
                onForward10 = onForward10,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeatMode = onCycleRepeatMode,
                onToggleFavorite = onToggleFavorite,
                onOpenQueue = onOpenQueue,
                onOpenEqualizer = onOpenEqualizer,
                onAddToPlaylist = onAddToPlaylist,
                onOpenSleepTimer = onOpenSleepTimer,
                currentSpeed = currentSpeed,
                currentPitchSemitones = currentPitchSemitones,
                onSpeedChanged = onSpeedChanged,
                onPitchChanged = onPitchChanged,
                modifier = Modifier.fillMaxSize()
            )
            return@BoxWithConstraints
        }

        // When collapsed, display standard clickable MiniPlayer
        if (p <= 0.001f && !isExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomNavOffset)
            ) {
                MiniPlayer(
                    playbackState = playbackState,
                    onClick = { onExpandedChange(true) },
                    onPlayPause = onPlayPause,
                    onSkipNext = onSkipNext
                )
            }
            return@BoxWithConstraints
        }

        // ==============================================================
        // ACTIVE GEOMETRIC MORPHING STATE (0.001f < p < 0.999f)
        // ==============================================================
        val containerRadius = lerp(22.dp, 0.dp, p)
        val containerPaddingH = lerp(14.dp, 0.dp, p)
        val containerPaddingB = lerp(bottomNavOffset + 4.dp, 0.dp, p)
        val containerHeight = lerp(64.dp, totalHeight, p)
        val containerElevation = lerp(8.dp, 0.dp, p)

        // Artwork geometric interpolation
        val fullArtSize = (totalWidth - 48.dp).coerceAtMost(330.dp)
        val artSize = lerp(48.dp, fullArtSize, p)
        val artCornerRadius = lerp(14.dp, 28.dp, p)
        val artStartX = lerp(24.dp, (totalWidth - fullArtSize) / 2, p)
        val artStartY = lerp(8.dp, 84.dp, p)

        // Container Surface
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(containerHeight)
                .align(Alignment.BottomCenter)
                .padding(horizontal = containerPaddingH)
                .padding(bottom = containerPaddingB)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(elevation = containerElevation, shape = RoundedCornerShape(containerRadius))
                    .clip(RoundedCornerShape(containerRadius))
                    .testTag("expanding_player_surface"),
                shape = RoundedCornerShape(containerRadius),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = lerp(6.dp, 0.dp, p)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Full player elements fade in as expansion approaches completion
                    if (p > 0.15f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = ((p - 0.15f) / 0.85f).coerceIn(0f, 1f)
                                }
                        ) {
                            NowPlayingScreen(
                                playbackState = playbackState,
                                onCollapse = { onExpandedChange(false) },
                                onPlayPause = onPlayPause,
                                onSkipNext = onSkipNext,
                                onSkipPrevious = onSkipPrevious,
                                onSeekTo = onSeekTo,
                                onRewind10 = onRewind10,
                                onForward10 = onForward10,
                                onToggleShuffle = onToggleShuffle,
                                onCycleRepeatMode = onCycleRepeatMode,
                                onToggleFavorite = onToggleFavorite,
                                onOpenQueue = onOpenQueue,
                                onOpenEqualizer = onOpenEqualizer,
                                onAddToPlaylist = onAddToPlaylist,
                                onOpenSleepTimer = onOpenSleepTimer,
                                currentSpeed = currentSpeed,
                                currentPitchSemitones = currentPitchSemitones,
                                onSpeedChanged = onSpeedChanged,
                                onPitchChanged = onPitchChanged,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Morphing Artwork (Glides and scales continuously)
                    if (p <= 0.85f) {
                        Box(
                            modifier = Modifier
                                .offset(x = artStartX, y = artStartY)
                                .size(artSize)
                                .clip(RoundedCornerShape(artCornerRadius))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!song.albumArtUriString.isNullOrEmpty()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(song.albumArtUriString)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = song.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = AppIcons.Library,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(artSize * 0.5f)
                                )
                            }
                        }
                    }

                    // Mini player elements fade out as container expands
                    if (p < 0.40f) {
                        val miniAlpha = (1f - p * 2.5f).coerceIn(0f, 1f)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .padding(start = 72.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
                                .graphicsLayer { alpha = miniAlpha },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            MorphingPlayPauseButton(
                                isPlaying = playbackState.isPlaying,
                                onClick = onPlayPause,
                                size = 44.dp,
                                iconSize = 22.dp
                            )

                            Spacer(modifier = Modifier.width(4.dp))

                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .fluidPress(
                                        scaleDown = 0.92f,
                                        hapticType = HapticType.LIGHT,
                                        onClick = onSkipNext
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = AppIcons.Next,
                                    contentDescription = "Следующий",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Mini progress bar
                        val progress = if (playbackState.durationMs > 0) {
                            (playbackState.currentPositionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .align(Alignment.BottomCenter)
                                .graphicsLayer { alpha = miniAlpha },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }
    }
}
