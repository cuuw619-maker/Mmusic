package com.example.ui.components

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.example.model.Song
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
    // 0f = Paused (Play triangle ▸), 1f = Playing (Pause bars ❚❚)
    val progress by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "play_pause_geometric_morph"
    )

    Surface(
        modifier = modifier
            .size(size)
            .shadow(elevation = 6.dp, shape = CircleShape)
            .fluidPress(
                scaleDown = 0.94f,
                hapticType = HapticType.MEDIUM,
                onClick = onClick
            ),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.testTag(if (isPlaying) "pause_button" else "play_button")
        ) {
            Canvas(modifier = Modifier.size(iconSize)) {
                val w = this.size.width
                val h = this.size.height
                val t = progress
                val overlap = (1f - t) * 0.015f

                // Left Polygon (Left bar in pause -> Left trapezoid of play triangle)
                val leftPath = Path().apply {
                    val p1x = lerp(0.24f, 0.22f, t) * w
                    val p1y = lerp(0.18f, 0.18f, t) * h

                    val p2x = (lerp(0.54f, 0.42f, t) + overlap) * w
                    val p2y = lerp(0.34f, 0.18f, t) * h

                    val p3x = (lerp(0.54f, 0.42f, t) + overlap) * w
                    val p3y = lerp(0.66f, 0.82f, t) * h

                    val p4x = lerp(0.24f, 0.22f, t) * w
                    val p4y = lerp(0.82f, 0.82f, t) * h

                    moveTo(p1x, p1y)
                    lineTo(p2x, p2y)
                    lineTo(p3x, p3y)
                    lineTo(p4x, p4y)
                    close()
                }

                // Right Polygon (Right bar in pause -> Right tip triangle of play triangle)
                val rightPath = Path().apply {
                    val p1x = (lerp(0.54f, 0.58f, t) - overlap) * w
                    val p1y = lerp(0.34f, 0.18f, t) * h

                    val p2x = lerp(0.84f, 0.78f, t) * w
                    val p2y = lerp(0.50f, 0.18f, t) * h

                    val p3x = lerp(0.84f, 0.78f, t) * w
                    val p3y = lerp(0.50f, 0.82f, t) * h

                    val p4x = (lerp(0.54f, 0.58f, t) - overlap) * w
                    val p4y = lerp(0.66f, 0.82f, t) * h

                    moveTo(p1x, p1y)
                    lineTo(p2x, p2y)
                    lineTo(p3x, p3y)
                    lineTo(p4x, p4y)
                    close()
                }

                drawPath(leftPath, color = contentColor, style = Fill)
                drawPath(rightPath, color = contentColor, style = Fill)
            }
        }
    }
}

/**
 * Modern Interactive Progress Bar with Vertical Pill/Stick Thumb:
 * - Vertical stick thumb (5dp width, 26dp height, rounded capsule)
 * - Dynamic feedback on touch (thumb height 26dp -> 30dp, width 5dp -> 6dp)
 * - Real-time floating time bubble indicator during scrub
 * - Smooth, responsive drag and seek with haptic ticks
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

    // Subtle track height expansion on touch
    val barHeight by animateDpAsState(
        targetValue = if (isDragging) 6.dp else 4.dp,
        animationSpec = tween(150, easing = LinearOutSlowInEasing),
        label = "bar_thickness"
    )

    // Vertical pill stick dimensions
    val thumbWidth by animateDpAsState(
        targetValue = if (isDragging) 6.dp else 5.dp,
        animationSpec = tween(150, easing = LinearOutSlowInEasing),
        label = "thumb_width"
    )

    val thumbHeight by animateDpAsState(
        targetValue = if (isDragging) 30.dp else 26.dp,
        animationSpec = tween(150, easing = LinearOutSlowInEasing),
        label = "thumb_height"
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

            // Draggable Thumb: Vertical Pill / Stick
            val thumbWidthPx = with(density) { thumbWidth.toPx() }
            val thumbOffsetPx = (activeWidthPx - (thumbWidthPx / 2f)).coerceIn(
                0f,
                totalWidthPx - thumbWidthPx
            )

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(thumbOffsetPx.roundToInt(), 0)
                    }
                    .size(width = thumbWidth, height = thumbHeight)
                    .shadow(
                        elevation = if (isDragging) 6.dp else 2.dp,
                        shape = RoundedCornerShape(3.dp),
                        spotColor = activeColor.copy(alpha = 0.5f)
                    )
                    .clip(RoundedCornerShape(3.dp))
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
