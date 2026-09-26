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

private const val VISUAL_DRAG_FACTOR = 0.94f

/**
 * Modern floating "Oval inside Oval" navigation bar:
 * - Outer Oval: Floating rounded container (glassmorphism aesthetic, subtle border, shadow).
 * - Inner Oval: Interactive selected indicator that:
 *     * Scales up by 15% on press/hold with a linear 150ms animation.
 *     * Follows finger with natural light resistance (visualX = dragStartX + totalFingerDeltaX * 0.88f).
 *     * Instant direction change on finger reversal without accumulated delay or lag.
 *     * Keeps selectedIndex unchanged during dragging.
 *     * Constrained strictly inside outer bounds [0, maxAllowedLeft].
 *     * On release: iterates through exact tab centers to find closestIndex, then snaps with a controlled subtle bounce.
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

                // Linear 150ms press scale for ONLY the inner oval (scales 1.0 -> 1.15)
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
                // LAYER 1: THE INTERACTIVE INNER OVAL (Visual indicator)
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

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("nav_${destination.route}"),
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

                // ==========================================
                // LAYER 3: STATIONARY TOUCH LAYER (FULL BAR)
                // Handles direct finger-tracking drag of selected oval & direct tab taps.
                // Coordinates are directly in stationary container space [0, totalWidthPx].
                // ==========================================
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(destinations, slotWidthPx, indicatorWidthPx, totalWidthPx, selectedIndex) {
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
                                        if (!change.pressed) {
                                            break
                                        }

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
                                                // newX = dragStartOvalX + (currentFingerX - dragStartFingerX) * 0.94f
                                                val newX = dragStartOvalX + totalFingerDeltaX * VISUAL_DRAG_FACTOR
                                                currentVisualX = newX.coerceIn(minAllowedLeft, maxAllowedLeft)
                                                dragVisualXPx = currentVisualX
                                                haptic.performHaptic(HapticFeedbackType.LIGHT_TICK)
                                            } else if (abs(totalFingerDeltaY) > viewConfiguration.touchSlop) {
                                                break
                                            }
                                        } else {
                                            change.consume()
                                            // Synchronous calculation from finger position without accumulated lag
                                            val newX = dragStartOvalX + totalFingerDeltaX * VISUAL_DRAG_FACTOR
                                            currentVisualX = newX.coerceIn(minAllowedLeft, maxAllowedLeft)
                                            dragVisualXPx = currentVisualX

                                            val currentCenter = currentVisualX + indicatorWidthPx / 2f
                                            val hoveredIndex = destinations.indices.minByOrNull { i ->
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
                                        // Take actual current center of oval on release
                                        val finalCenter = currentVisualX + indicatorWidthPx / 2f
                                        // Compare exact distances to ALL tab centers
                                        val closestIndex = destinations.indices.minByOrNull { i ->
                                            val tabCenter = (i + 0.5f) * slotWidthPx
                                            abs(tabCenter - finalCenter)
                                        } ?: selectedIndex

                                        if (closestIndex != selectedIndex) {
                                            haptic.performHaptic(HapticFeedbackType.LIGHT_TICK)
                                            onNavigate(destinations[closestIndex])
                                        }

                                        scope.launch {
                                            indicatorOffsetXPx.snapTo(currentVisualX)
                                            val targetCenter = (closestIndex + 0.5f) * slotWidthPx
                                            val targetLeft = (targetCenter - indicatorWidthPx / 2f)
                                                .coerceIn(minAllowedLeft, maxAllowedLeft)
                                            // Controlled subtle bounce upon settling into target center
                                            indicatorOffsetXPx.animateTo(
                                                targetValue = targetLeft,
                                                animationSpec = spring(
                                                    dampingRatio = 0.82f,
                                                    stiffness = 500f
                                                )
                                            )
                                        }
                                    } else {
                                        // If released without drag: it's a direct tab tap
                                        val tappedIndex = (down.position.x / slotWidthPx).toInt()
                                            .coerceIn(0, destinations.size - 1)
                                        if (tappedIndex != selectedIndex) {
                                            haptic.performHaptic(HapticFeedbackType.LIGHT_TICK)
                                            onNavigate(destinations[tappedIndex])
                                        }
                                    }
                                }
                            }
                        }
                )
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
