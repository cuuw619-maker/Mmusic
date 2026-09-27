package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.util.HapticFeedbackType
import com.example.ui.util.rememberHapticHelper
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

data class OvalTabItem(
    val title: String,
    val icon: ImageVector? = null,
    val testTag: String = ""
)

/**
 * Reusable "Oval inside Oval" selector component.
 * Features:
 * - Outer rounded capsule container with elevation and outline.
 * - Inner interactive sliding/bouncing pill indicator with spring physics and squash & stretch.
 * - Interactive finger tracking drag and tap switching with haptic feedback.
 */
@Composable
fun OvalTabRow(
    tabs: List<OvalTabItem>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    containerHeightDp: Int = 48
) {
    if (tabs.isEmpty()) return

    val density = LocalDensity.current
    val haptic = rememberHapticHelper()
    val scope = rememberCoroutineScope()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(32.dp),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                ambientColor = Color.Black.copy(alpha = 0.08f)
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                shape = RoundedCornerShape(32.dp)
            )
            .clip(RoundedCornerShape(32.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shape = RoundedCornerShape(32.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(containerHeightDp.dp)
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            val totalWidthPx = constraints.maxWidth.toFloat()
            val tabCount = tabs.size
            val slotWidthPx = totalWidthPx / tabCount.toFloat()

            val indicatorWidthPx = slotWidthPx * 0.94f
            val indicatorHeightDp = (containerHeightDp - 8).coerceAtLeast(32).dp

            val minAllowedLeft = 0f
            val maxAllowedLeft = (totalWidthPx - indicatorWidthPx).coerceAtLeast(0f)

            val indicatorOffsetXPx = remember { Animatable(0f) }
            var dragVisualXPx by remember { mutableFloatStateOf(0f) }
            var isPressed by remember { mutableStateOf(false) }
            var isDragging by remember { mutableStateOf(false) }

            // Dynamic bouncy squash & stretch
            val capsuleScaleX by animateFloatAsState(
                targetValue = when {
                    isDragging -> 1.14f
                    isPressed -> 1.07f
                    else -> 1.0f
                },
                animationSpec = spring(dampingRatio = 0.65f, stiffness = 450f),
                label = "oval_scale_x"
            )
            val capsuleScaleY by animateFloatAsState(
                targetValue = when {
                    isDragging -> 1.05f
                    isPressed -> 1.07f
                    else -> 1.0f
                },
                animationSpec = spring(dampingRatio = 0.65f, stiffness = 450f),
                label = "oval_scale_y"
            )

            // Sync indicator position on index changes
            LaunchedEffect(selectedIndex, slotWidthPx, indicatorWidthPx, isDragging) {
                if (!isDragging) {
                    val targetCenterPx = (selectedIndex + 0.5f) * slotWidthPx
                    val targetLeftPx = targetCenterPx - (indicatorWidthPx / 2f)
                    val clampedTarget = targetLeftPx.coerceIn(minAllowedLeft, maxAllowedLeft)
                    indicatorOffsetXPx.animateTo(
                        targetValue = clampedTarget,
                        animationSpec = spring(dampingRatio = 0.70f, stiffness = 380f)
                    )
                }
            }

            val indicatorWidthDp = with(density) { indicatorWidthPx.toDp() }

            // ==========================================
            // LAYER 1: THE INTERACTIVE INNER OVAL
            // ==========================================
            Box(
                modifier = Modifier
                    .offset {
                        val currentX = if (isDragging) dragVisualXPx else indicatorOffsetXPx.value
                        IntOffset(x = currentX.roundToInt(), y = 0)
                    }
                    .width(indicatorWidthDp)
                    .height(indicatorHeightDp)
                    .align(Alignment.CenterStart)
                    .graphicsLayer {
                        scaleX = capsuleScaleX
                        scaleY = capsuleScaleY
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(24.dp)
                    )
            )

            // ==========================================
            // LAYER 2: TAB LABELS & ICONS
            // ==========================================
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = selectedIndex == index
                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = tween(durationMillis = 150, easing = LinearEasing),
                        label = "oval_content_color_$index"
                    )

                    val tabScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.08f else 1.0f,
                        animationSpec = spring(dampingRatio = 0.55f, stiffness = 400f),
                        label = "oval_tab_scale_$index"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .graphicsLayer {
                                scaleX = tabScale
                                scaleY = tabScale
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        ) {
                            if (tab.icon != null) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title,
                                    tint = contentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = tab.title,
                                color = contentColor,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = if (tabCount > 4) 10.5.sp else 12.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // ==========================================
            // LAYER 3: INTERACTIVE TOUCH LAYER
            // ==========================================
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(tabs, slotWidthPx, indicatorWidthPx, totalWidthPx, selectedIndex) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val dragStartFingerX = down.position.x
                            val dragStartOvalX = indicatorOffsetXPx.value

                            val ovalLeft = dragStartOvalX
                            val ovalRight = dragStartOvalX + indicatorWidthPx
                            val isTouchOnOval = down.position.x in (ovalLeft - 16.dp.toPx())..(ovalRight + 16.dp.toPx())

                            if (isTouchOnOval) {
                                isPressed = true
                            }

                            var isDragStarted = false
                            var currentVisualX = dragStartOvalX
                            var lastHapticIndex = selectedIndex

                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break

                                    val currentFingerX = change.position.x
                                    val totalFingerDeltaX = currentFingerX - dragStartFingerX
                                    val totalFingerDeltaY = change.position.y - down.position.y

                                    if (!isDragStarted) {
                                        if (abs(totalFingerDeltaX) > viewConfiguration.touchSlop &&
                                            abs(totalFingerDeltaX) > abs(totalFingerDeltaY)
                                        ) {
                                            isDragStarted = true
                                            isDragging = true
                                            change.consume()
                                            val newX = dragStartOvalX + totalFingerDeltaX * 0.94f
                                            currentVisualX = newX.coerceIn(minAllowedLeft, maxAllowedLeft)
                                            dragVisualXPx = currentVisualX
                                            haptic.performHaptic(HapticFeedbackType.LIGHT_TICK)
                                        } else if (abs(totalFingerDeltaY) > viewConfiguration.touchSlop) {
                                            break
                                        }
                                    } else {
                                        change.consume()
                                        val newX = dragStartOvalX + totalFingerDeltaX * 0.94f
                                        currentVisualX = newX.coerceIn(minAllowedLeft, maxAllowedLeft)
                                        dragVisualXPx = currentVisualX

                                        val currentCenter = currentVisualX + indicatorWidthPx / 2f
                                        val hoveredIndex = tabs.indices.minByOrNull { i ->
                                            val tabCenter = (i + 0.5f) * slotWidthPx
                                            abs(tabCenter - currentCenter)
                                        } ?: selectedIndex

                                        if (hoveredIndex != lastHapticIndex) {
                                            lastHapticIndex = hoveredIndex
                                            haptic.performHaptic(HapticFeedbackType.LIGHT_TICK)
                                        }
                                    }
                                }
                            } finally {
                                isPressed = false
                                if (isDragStarted) {
                                    isDragging = false
                                    val finalCenter = currentVisualX + indicatorWidthPx / 2f
                                    val closestIndex = tabs.indices.minByOrNull { i ->
                                        val tabCenter = (i + 0.5f) * slotWidthPx
                                        abs(tabCenter - finalCenter)
                                    } ?: selectedIndex

                                    if (closestIndex != selectedIndex) {
                                        haptic.performHaptic(HapticFeedbackType.LIGHT_TICK)
                                        onTabSelected(closestIndex)
                                    }

                                    scope.launch {
                                        indicatorOffsetXPx.snapTo(currentVisualX)
                                        val targetCenter = (closestIndex + 0.5f) * slotWidthPx
                                        val targetLeft = (targetCenter - indicatorWidthPx / 2f)
                                            .coerceIn(minAllowedLeft, maxAllowedLeft)
                                        indicatorOffsetXPx.animateTo(
                                            targetValue = targetLeft,
                                            animationSpec = spring(
                                                dampingRatio = 0.68f,
                                                stiffness = 400f
                                            )
                                        )
                                    }
                                } else {
                                    val tappedIndex = (down.position.x / slotWidthPx).toInt()
                                        .coerceIn(0, tabs.size - 1)
                                    if (tappedIndex != selectedIndex) {
                                        haptic.performHaptic(HapticFeedbackType.LIGHT_TICK)
                                        onTabSelected(tappedIndex)
                                    }
                                }
                            }
                        }
                    }
            )
        }
    }
}
