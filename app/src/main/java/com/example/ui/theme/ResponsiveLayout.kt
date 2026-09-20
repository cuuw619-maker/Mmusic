package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class WindowType {
    COMPACT,    // Typical phone portrait (< 600dp)
    MEDIUM,     // Foldables unfolded, small tablets, large phone landscape (600dp - 840dp)
    EXPANDED    // Large tablets, desktop/DeX (>= 840dp)
}

enum class OrientationType {
    PORTRAIT,
    LANDSCAPE,
    SQUARE
}

data class ResponsiveDimensions(
    val availableWidth: Dp,
    val availableHeight: Dp,
    val windowType: WindowType,
    val orientation: OrientationType,
    val isTablet: Boolean,
    val isLandscape: Boolean,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val contentMaxWidth: Dp,
    val miniPlayerHeight: Dp,
    val bottomBarHeight: Dp,
    val primaryControlSize: Dp,
    val secondaryControlSize: Dp,
    val isDualPaneAvailable: Boolean
) {
    /**
     * Dynamically computes album artwork size bounded by min and max constraints
     */
    fun calculateArtworkSize(): Dp {
        val targetSize = if (isLandscape) {
            (availableHeight * 0.58f).coerceIn(160.dp, 360.dp)
        } else if (isTablet) {
            (availableWidth * 0.42f).coerceIn(220.dp, 440.dp)
        } else {
            // Phone portrait
            (availableWidth * 0.78f).coerceIn(180.dp, 380.dp)
        }
        return targetSize
    }
}

val LocalResponsiveDimensions = compositionLocalOf {
    ResponsiveDimensions(
        availableWidth = 360.dp,
        availableHeight = 640.dp,
        windowType = WindowType.COMPACT,
        orientation = OrientationType.PORTRAIT,
        isTablet = false,
        isLandscape = false,
        horizontalPadding = 16.dp,
        verticalPadding = 16.dp,
        contentMaxWidth = 600.dp,
        miniPlayerHeight = 68.dp,
        bottomBarHeight = 72.dp,
        primaryControlSize = 64.dp,
        secondaryControlSize = 48.dp,
        isDualPaneAvailable = false
    )
}

@Composable
fun ProvideResponsiveLayout(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints {
        ProvideResponsiveLayout(
            availableWidth = maxWidth,
            availableHeight = maxHeight,
            content = content
        )
    }
}

@Composable
fun ProvideResponsiveLayout(
    availableWidth: Dp,
    availableHeight: Dp,
    content: @Composable () -> Unit
) {
    val windowType = when {
        availableWidth < 600.dp -> WindowType.COMPACT
        availableWidth < 840.dp -> WindowType.MEDIUM
        else -> WindowType.EXPANDED
    }

    val aspectRatio = availableWidth / availableHeight
    val orientation = when {
        aspectRatio > 1.2f -> OrientationType.LANDSCAPE
        aspectRatio < 0.85f -> OrientationType.PORTRAIT
        else -> OrientationType.SQUARE
    }

    val isTablet = windowType != WindowType.COMPACT
    val isLandscape = orientation == OrientationType.LANDSCAPE

    val horizontalPadding = when (windowType) {
        WindowType.COMPACT -> 16.dp
        WindowType.MEDIUM -> 24.dp
        WindowType.EXPANDED -> 32.dp
    }

    val verticalPadding = when (windowType) {
        WindowType.COMPACT -> 12.dp
        WindowType.MEDIUM -> 16.dp
        WindowType.EXPANDED -> 20.dp
    }

    val contentMaxWidth = when (windowType) {
        WindowType.COMPACT -> Dp.Unspecified
        WindowType.MEDIUM -> 720.dp
        WindowType.EXPANDED -> 1040.dp
    }

    val isDualPaneAvailable = (windowType == WindowType.EXPANDED) || (isTablet && isLandscape)

    val dimensions = remember(availableWidth, availableHeight, windowType, orientation) {
        ResponsiveDimensions(
            availableWidth = availableWidth,
            availableHeight = availableHeight,
            windowType = windowType,
            orientation = orientation,
            isTablet = isTablet,
            isLandscape = isLandscape,
            horizontalPadding = horizontalPadding,
            verticalPadding = verticalPadding,
            contentMaxWidth = contentMaxWidth,
            miniPlayerHeight = if (isTablet) 76.dp else 68.dp,
            bottomBarHeight = if (isTablet) 80.dp else 72.dp,
            primaryControlSize = if (isTablet) 72.dp else 64.dp,
            secondaryControlSize = if (isTablet) 52.dp else 48.dp,
            isDualPaneAvailable = isDualPaneAvailable
        )
    }

    CompositionLocalProvider(LocalResponsiveDimensions provides dimensions) {
        content()
    }
}
