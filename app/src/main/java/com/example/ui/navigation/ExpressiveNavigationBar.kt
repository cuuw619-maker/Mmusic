package com.example.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.util.HapticFeedbackType
import com.example.ui.util.rememberHapticHelper
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

val MAIN_NAV_DESTINATIONS = listOf(
    NavDestination.HOME,
    NavDestination.SEARCH,
    NavDestination.LIBRARY,
    NavDestination.PLAYLISTS,
    NavDestination.SETTINGS
)

private const val VISUAL_DRAG_FACTOR = 0.88f

/**
 * Modern floating "Oval inside Oval" navigation bar:
 * - Outer Oval: Floating rounded container (glassmorphism aesthetic, subtle border, shadow).
 * - Inner Oval: Interactive selected indicator that:
 *     * Scales up by 15% on press/hold with a linear 150ms animation.
 *     * Follows finger with natural light resistance (visualX = dragStartX + totalFingerDeltaX * 0.88f).
 *     * Instant direction change on finger reversal without accumulated delay or lag.
 *     * Keeps selectedIndex unchanged during dragging.
 *     * Constrained strictly inside outer bounds [0, maxAllowedLeft].
 *     * Snaps to closest tab center on release with controlled bounce.
 *     * Strictly forbids vertical drag.
 * - All 5 tabs always display both Icon (1:1 ratio, 22dp, never scaled) and Title text simultaneously.
 * - Pure clean appearance without shine, glow, flash, ripple, or bouncy/elastic deformation.
 */
@Composable
fun ExpressiveFloatingNavigationBar(
    currentDestination: NavDestination,
    onNavigate: (NavDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHapticHelper()
    val destinations = MAIN_NAV_DESTINATIONS
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val selectedIndex = destinations.indexOf(currentDestination).coerceAtLeast(0)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 10.dp,
                    shape = RoundedCornerShape(32.dp),
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                    ambientColor = Color.Black.copy(alpha = 0.12f)
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(32.dp)
                )
                .clip(RoundedCornerShape(32.dp))
                .testTag("floating_navigation_bar"),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f),
            tonalElevation = 4.dp,
            shape = RoundedCornerShape(32.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 6.dp, vertical = 5.dp)
            ) {
                val totalWidthPx = constraints.maxWidth.toFloat()
                val tabCount = destinations.size
                val slotWidthPx = totalWidthPx / tabCount.toFloat()

                // Indicator oval dimensions
                val indicatorWidthPx = slotWidthPx * 0.92f
                val indicatorHeightDp = 48.dp

                val minAllowedLeft = 0f
                val maxAllowedLeft = (totalWidthPx - indicatorWidthPx).coerceAtLeast(0f)

                val indicatorOffsetXPx = remember { Animatable(0f) }
                var dragVisualXPx by remember { mutableFloatStateOf(0f) }
                var isPressed by remember { mutableStateOf(false) }
                var isDragging by remember { mutableStateOf(false) }

                // Linear 150ms press scale for ONLY the inner oval
                val capsuleScale by animateFloatAsState(
                    targetValue = if (isPressed || isDragging) 1.15f else 1.0f,
                    animationSpec = tween(
                        durationMillis = 150,
                        easing = LinearEasing
                    ),
                    label = "inner_oval_press_scale"
                )

                // Sync indicator position when selectedIndex changes outside of active drag
                LaunchedEffect(selectedIndex, slotWidthPx, indicatorWidthPx, isDragging) {
                    if (!isDragging) {
                        val targetCenterPx = (selectedIndex + 0.5f) * slotWidthPx
                        val targetLeftPx = targetCenterPx - (indicatorWidthPx / 2f)
                        val clampedTarget = targetLeftPx.coerceIn(minAllowedLeft, maxAllowedLeft)
                        indicatorOffsetXPx.animateTo(
                            targetValue = clampedTarget,
                            animationSpec = tween(durationMillis = 180, easing = LinearEasing)
                        )
                    }
                }

                // ==========================================
                // LAYER 1: THE INTERACTIVE INNER OVAL
                // ==========================================
                val indicatorWidthDp = with(density) { indicatorWidthPx.toDp() }

                Box(
                    modifier = Modifier
                        .offset {
                            val currentX = if (isDragging) dragVisualXPx else indicatorOffsetXPx.value
                            IntOffset(
                                x = currentX.roundToInt(),
                                y = 0
                            )
                        }
                        .width(indicatorWidthDp)
                        .height(indicatorHeightDp)
                        .align(Alignment.CenterStart)
                        .graphicsLayer {
                            scaleX = capsuleScale
                            scaleY = capsuleScale
                        }
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .pointerInput(destinations, slotWidthPx, indicatorWidthPx, totalWidthPx, selectedIndex) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                isPressed = true
                                var isDragStarted = false
                                // Fixed drag origin at the moment of touch down
                                val dragStartX = indicatorOffsetXPx.value
                                val fingerStartX = down.position.x
                                var currentVisualX = dragStartX
                                var lastHapticIndex = selectedIndex

                                try {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) {
                                            break
                                        }

                                        val totalFingerDeltaX = change.position.x - fingerStartX
                                        val totalFingerDeltaY = change.position.y - down.position.y

                                        if (!isDragStarted) {
                                            if (abs(totalFingerDeltaX) > viewConfiguration.touchSlop &&
                                                abs(totalFingerDeltaX) > abs(totalFingerDeltaY)
                                            ) {
                                                isDragStarted = true
                                                isDragging = true
                                                change.consume()
                                                // Formula: visualX = dragStartX + totalFingerDeltaX * 0.88f
                                                val visualDelta = totalFingerDeltaX * VISUAL_DRAG_FACTOR
                                                currentVisualX = (dragStartX + visualDelta).coerceIn(minAllowedLeft, maxAllowedLeft)
                                                dragVisualXPx = currentVisualX
                                                haptic.performHaptic(HapticFeedbackType.LIGHT_TICK)
                                            } else if (abs(totalFingerDeltaY) > viewConfiguration.touchSlop) {
                                                // Strictly ignore/cancel gesture if vertical swipe detected
                                                break
                                            }
                                        } else {
                                            change.consume()
                                            // Synchronous calculation based on finger origin:
                                            // Immediately reacts to direction changes without lag or accumulated error
                                            val visualDelta = totalFingerDeltaX * VISUAL_DRAG_FACTOR
                                            currentVisualX = (dragStartX + visualDelta).coerceIn(minAllowedLeft, maxAllowedLeft)
                                            dragVisualXPx = currentVisualX

                                            val hoveredIndex = ((currentVisualX + indicatorWidthPx / 2f) / slotWidthPx)
                                                .toInt()
                                                .coerceIn(0, tabCount - 1)
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
                                        // Take actual current visual X position of oval
                                        val finalCenter = currentVisualX + indicatorWidthPx / 2f
                                        val closestIndex = (finalCenter / slotWidthPx)
                                            .roundToInt()
                                            .coerceIn(0, tabCount - 1)

                                        if (closestIndex != selectedIndex) {
                                            haptic.performHaptic(HapticFeedbackType.LIGHT_TICK)
                                            onNavigate(destinations[closestIndex])
                                        }

                                        scope.launch {
                                            // Initialize Animatable directly to the final dragVisualX position
                                            indicatorOffsetXPx.snapTo(currentVisualX)
                                            val targetCenter = (closestIndex + 0.5f) * slotWidthPx
                                            val targetLeft = (targetCenter - indicatorWidthPx / 2f)
                                                .coerceIn(minAllowedLeft, maxAllowedLeft)
                                            indicatorOffsetXPx.animateTo(
                                                targetValue = targetLeft,
                                                animationSpec = spring(
                                                    dampingRatio = 0.80f,
                                                    stiffness = 550f
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        .testTag("nav_selected_indicator")
                )

                // ==========================================
                // LAYER 2: THE 5 TABS (ICONS & LABELS)
                // ==========================================
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    destinations.forEachIndexed { index, destination ->
                        val isSelected = selectedIndex == index
                        val contentColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            animationSpec = tween(durationMillis = 150, easing = LinearEasing),
                            label = "nav_content_color_${destination.route}"
                        )

                        val tabModifier = if (!isSelected) {
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(25.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    enabled = !isDragging,
                                    onClick = {
                                        haptic.performHaptic(HapticFeedbackType.LIGHT_TICK)
                                        onNavigate(destination)
                                    }
                                )
                                .testTag("nav_${destination.route}")
                        } else {
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("nav_${destination.route}")
                        }

                        Box(
                            modifier = tabModifier,
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                // Icon strictly 22dp with 1:1 aspect ratio, never scaled or stretched
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.title,
                                    tint = contentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                // Title text always visible for all 5 tabs
                                Text(
                                    text = destination.title,
                                    color = contentColor,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 10.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Expressive Navigation Rail for Foldables and Tablets.
 */
@Composable
fun ExpressiveNavigationRail(
    currentDestination: NavDestination,
    onNavigate: (NavDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHapticHelper()
    val destinations = MAIN_NAV_DESTINATIONS

    Surface(
        modifier = modifier
            .width(80.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
        ) {
            destinations.forEach { destination ->
                val isSelected = currentDestination == destination
                val containerColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        Color.Transparent
                    },
                    animationSpec = tween(durationMillis = 150, easing = LinearEasing),
                    label = "rail_container_${destination.route}"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(durationMillis = 150, easing = LinearEasing),
                    label = "rail_content_${destination.route}"
                )

                Surface(
                    modifier = Modifier
                        .size(width = 64.dp, height = 56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                haptic.performHaptic(HapticFeedbackType.LIGHT_TICK)
                                onNavigate(destination)
                            }
                        )
                        .testTag("rail_${destination.route}"),
                    color = containerColor,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.title,
                            tint = contentColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = destination.title,
                            color = contentColor,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 10.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
