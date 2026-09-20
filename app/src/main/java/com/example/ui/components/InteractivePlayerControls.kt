package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Song
import com.example.ui.animation.FluidMotionConstants
import com.example.ui.animation.HapticType
import com.example.ui.animation.fluidPress
import com.example.ui.animation.performHapticFeedback
import kotlin.math.roundToInt

@Composable
fun MorphingPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    iconSize: Dp = 32.dp,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    Surface(
        modifier = modifier
            .size(size)
            .shadow(elevation = 6.dp, shape = CircleShape)
            .fluidPress(
                scaleDown = 0.92f,
                hapticType = HapticType.MEDIUM,
                onClick = onClick
            ),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = isPlaying,
                transitionSpec = {
                    (fadeIn(animationSpec = spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMediumLow)) togetherWith
                     fadeOut(animationSpec = spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMediumLow)))
                },
                label = "play_pause_morph"
            ) { targetIsPlaying ->
                val rotation = remember { Animatable(if (targetIsPlaying) 0f else -90f) }
                LaunchedEffect(targetIsPlaying) {
                    rotation.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = 0.58f,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }

                Icon(
                    imageVector = if (targetIsPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (targetIsPlaying) "Пауза" else "Воспроизведение",
                    modifier = Modifier
                        .size(iconSize)
                        .graphicsLayer {
                            rotationZ = rotation.value
                        }
                )
            }
        }
    }
}

/**
 * Physical Interactive Progress Bar:
 * - Dynamic thickness expansion on touch (4dp -> 8dp)
 * - Thumb expansion (12dp -> 18dp) with spring physics
 * - Real-time floating time bubble indicator while dragging
 * - Precise scrub & seek with haptic ticks
 */
@Composable
fun InteractiveProgressBar(
    currentPositionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val view = LocalView.current
    val density = LocalDensity.current

    val safeDuration = durationMs.coerceAtLeast(1L)
    val playbackFraction = (currentPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val displayFraction = if (isDragging) dragFraction else playbackFraction

    // Thickness spring expands on touch
    val barHeight by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 4.dp,
        animationSpec = spring(
            dampingRatio = 0.58f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "bar_thickness"
    )

    val thumbSize by animateDpAsState(
        targetValue = if (isDragging) 18.dp else 12.dp,
        animationSpec = spring(
            dampingRatio = 0.58f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "thumb_size"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .pointerInput(safeDuration) {
                    detectTapGestures(
                        onPress = { offset ->
                            isDragging = true
                            performHapticFeedback(view, HapticType.LIGHT)
                            val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            dragFraction = fraction
                            val released = tryAwaitRelease()
                            if (released) {
                                isDragging = false
                                val targetMs = (dragFraction * safeDuration).toLong()
                                performHapticFeedback(view, HapticType.MEDIUM)
                                onSeekTo(targetMs)
                            } else {
                                isDragging = false
                            }
                        }
                    )
                }
                .pointerInput(safeDuration) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            performHapticFeedback(view, HapticType.LIGHT)
                            val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            dragFraction = fraction
                        },
                        onDragEnd = {
                            isDragging = false
                            val targetMs = (dragFraction * safeDuration).toLong()
                            performHapticFeedback(view, HapticType.MEDIUM)
                            onSeekTo(targetMs)
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val fraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            dragFraction = fraction
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            val totalWidthPx = constraints.maxWidth.toFloat()
            val activeWidthPx = totalWidthPx * displayFraction

            // Inactive Track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .clip(RoundedCornerShape(barHeight / 2))
                    .background(inactiveColor)
            )

            // Active Track
            Box(
                modifier = Modifier
                    .height(barHeight)
                    .fillMaxWidth(displayFraction)
                    .clip(RoundedCornerShape(barHeight / 2))
                    .background(activeColor)
            )

            // Floating Time Bubble Indicator on Drag
            if (isDragging) {
                val currentDragMs = (dragFraction * safeDuration).toLong()
                val bubbleOffset = (activeWidthPx - 28f).coerceIn(0f, totalWidthPx - 56f)
                Box(
                    modifier = Modifier
                        .offset { IntOffset(bubbleOffset.roundToInt(), -55) }
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.inverseSurface)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = Song.formatDuration(currentDragMs),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.inverseOnSurface
                    )
                }
            }

            // Draggable Thumb Circle
            Box(
                modifier = Modifier
                    .offset {
                        val thumbOffset = (activeWidthPx - (with(density) { thumbSize.toPx() } / 2f)).coerceIn(
                            0f,
                            totalWidthPx - with(density) { thumbSize.toPx() }
                        )
                        IntOffset(thumbOffset.roundToInt(), 0)
                    }
                    .size(thumbSize)
                    .shadow(elevation = if (isDragging) 6.dp else 2.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(activeColor)
            )
        }

        // Time labels: elapsed & remaining
        val currentMs = if (isDragging) (dragFraction * safeDuration).toLong() else currentPositionMs
        val remainingMs = (durationMs - currentMs).coerceAtLeast(0L)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = Song.formatDuration(currentMs),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "-${Song.formatDuration(remainingMs)}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
