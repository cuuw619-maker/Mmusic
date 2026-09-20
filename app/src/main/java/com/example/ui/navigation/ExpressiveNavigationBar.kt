package com.example.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.util.HapticFeedbackType
import com.example.ui.util.rememberHapticHelper
import kotlinx.coroutines.launch
import kotlin.math.abs

val MAIN_NAV_DESTINATIONS = listOf(
    NavDestination.HOME,
    NavDestination.SEARCH,
    NavDestination.LIBRARY,
    NavDestination.PLAYLISTS,
    NavDestination.SETTINGS
)

/**
 * Material 3 Expressive Floating Navigation Bar with:
 * 1. Physical Drag Support (Horizontal ONLY, Vertical strictly 0)
 * 2. Speed formula: 0.75x speed for 1-step (Главная → Поиск) up to 1.0x speed for 4-step (Главная → Настройки)
 * 3. Single continuous moving oval, NO shine / glow / ripple / flash / morph
 * 4. Geometrically fixed icons and labels always visible for all 5 tabs
 */
@Composable
fun ExpressiveFloatingNavigationBar(
    currentDestination: NavDestination,
    onNavigate: (NavDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHapticHelper()
    val destinations = MAIN_NAV_DESTINATIONS
    val activeIndex = destinations.indexOf(currentDestination).let { if (it >= 0) it else 0 }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(32.dp))
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(32.dp),
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                )
                .testTag("floating_navigation_bar"),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.98f),
            tonalElevation = 6.dp,
            shape = RoundedCornerShape(32.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .padding(horizontal = 6.dp, vertical = 6.dp)
                    .fillMaxWidth()
            ) {
                val totalWidth = maxWidth
                val numItems = destinations.size
                val itemWidth = totalWidth / numItems
                val indicatorWidth = (itemWidth * 0.90f).coerceIn(48.dp, 72.dp)
                val targetOffsetX = itemWidth * activeIndex + (itemWidth - indicatorWidth) / 2

                var isDragging by remember { mutableStateOf(false) }
                val indicatorOffsetAnim = remember { Animatable(targetOffsetX.value) }

                // Speed calculation helper
                fun calculateDuration(fromX: Float, toX: Float, fromIdx: Int, toIdx: Int): Int {
                    val distanceDp = abs(toX - fromX)
                    val stepCount = abs(toIdx - fromIdx).coerceIn(1, 4)
                    // Speed multiplier: 0.75 for 1-step, 1.0 for 4-steps
                    val speedFactor = 0.75f + 0.25f * ((stepCount - 1) / 3.0f)
                    val baseSpeedDpPerSec = 560f
                    val effectiveSpeed = baseSpeedDpPerSec * speedFactor
                    val timeSec = distanceDp / effectiveSpeed
                    return (timeSec * 1000f).toInt().coerceIn(150, 750)
                }

                LaunchedEffect(targetOffsetX.value) {
                    if (!isDragging) {
                        val currentVal = indicatorOffsetAnim.value
                        val targetVal = targetOffsetX.value
                        val distance = abs(targetVal - currentVal)
                        if (distance > 0.5f) {
                            val currentApproxIdx = (currentVal / itemWidth.value).toInt().coerceIn(0, numItems - 1)
                            val durationMs = calculateDuration(currentVal, targetVal, currentApproxIdx, activeIndex)
                            indicatorOffsetAnim.animateTo(
                                targetValue = targetVal,
                                animationSpec = tween(
                                    durationMillis = durationMs,
                                    easing = FastOutSlowInEasing
                                )
                            )
                        }
                    }
                }

                // LAYER 1: Background Single Moving Oval (Pure solid color, draggable)
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffsetAnim.value.dp)
                        .width(indicatorWidth)
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                )

                // Drag gesture detector over the navigation bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    isDragging = true
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    scope.launch {
                                        val maxOffset = (totalWidth - indicatorWidth).value.coerceAtLeast(0f)
                                        val newOffset = (indicatorOffsetAnim.value + dragAmount.toDp().value)
                                            .coerceIn(0f, maxOffset)
                                        indicatorOffsetAnim.snapTo(newOffset)
                                    }
                                },
                                onDragEnd = {
                                    isDragging = false
                                    val centerCurrentX = indicatorOffsetAnim.value + (indicatorWidth.value / 2f)
                                    val targetIndex = (centerCurrentX / itemWidth.value)
                                        .toInt()
                                        .coerceIn(0, numItems - 1)
                                    val targetDest = destinations[targetIndex]
                                    if (targetDest != currentDestination) {
                                        haptic.performHaptic(HapticFeedbackType.LIGHT_TICK)
                                        onNavigate(targetDest)
                                    } else {
                                        // Snap back to current tab center
                                        val snapTarget = itemWidth.value * activeIndex + (itemWidth.value - indicatorWidth.value) / 2f
                                        val duration = calculateDuration(indicatorOffsetAnim.value, snapTarget, targetIndex, activeIndex)
                                        scope.launch {
                                            indicatorOffsetAnim.animateTo(
                                                targetValue = snapTarget,
                                                animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)
                                            )
                                        }
                                    }
                                },
                                onDragCancel = {
                                    isDragging = false
                                    val snapTarget = itemWidth.value * activeIndex + (itemWidth.value - indicatorWidth.value) / 2f
                                    val duration = calculateDuration(indicatorOffsetAnim.value, snapTarget, activeIndex, activeIndex)
                                    scope.launch {
                                        indicatorOffsetAnim.animateTo(
                                            targetValue = snapTarget,
                                            animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)
                                        )
                                    }
                                }
                            )
                        }
                )

                // LAYER 2: Foreground Items (Always visible icons & titles, NO ripple flash)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    destinations.forEachIndexed { index, destination ->
                        val isSelected = currentDestination == destination

                        val contentColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                            label = "nav_content_color_$index"
                        )

                        val interactionSource = remember { MutableInteractionSource() }

                        Box(
                            modifier = Modifier
                                .width(itemWidth)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(26.dp))
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null, // No ripple, no flash, no highlight
                                    onClick = {
                                        if (!isSelected) {
                                            haptic.performHaptic(HapticFeedbackType.LIGHT_TICK)
                                            onNavigate(destination)
                                        }
                                    }
                                )
                                .testTag("nav_${destination.route}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                // Strictly fixed 1:1 aspect ratio Icon
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.title,
                                    tint = contentColor,
                                    modifier = Modifier.size(22.dp)
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                // Always visible title text
                                Text(
                                    text = destination.title,
                                    color = contentColor,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 10.sp
                                    ),
                                    maxLines = 1
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
 * Icons strictly maintain fixed dimensions and 1:1 aspect ratio.
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
            .fillMaxHeight()
            .width(88.dp)
            .testTag("tablet_navigation_rail"),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
        ) {
            destinations.forEach { destination ->
                val isSelected = currentDestination == destination

                val containerColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                    label = "rail_bg_${destination.route}"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                    label = "rail_content_${destination.route}"
                )

                val interactionSource = remember { MutableInteractionSource() }

                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(containerColor)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = {
                                if (!isSelected) {
                                    haptic.performHaptic(HapticFeedbackType.LIGHT_TICK)
                                    onNavigate(destination)
                                }
                            }
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag("nav_rail_${destination.route}"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.title,
                        tint = contentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = destination.title,
                        color = contentColor,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 10.sp
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}
