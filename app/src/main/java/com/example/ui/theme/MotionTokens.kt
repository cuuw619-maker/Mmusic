package com.example.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Unified Motion Tokens for 2026 Material 3 Expressive motion system.
 * Fast, predictable, physics-driven, and interruptible.
 */
object MotionTokens {
    const val DurationInstant = 100
    const val DurationFast = 200
    const val DurationNormal = 350
    const val DurationSlow = 500
    const val DurationEmphasis = 650

    val EasingStandard = FastOutSlowInEasing
    val EasingLinear = LinearEasing

    fun <T> fastTween(): TweenSpec<T> = tween(durationMillis = DurationFast, easing = EasingStandard)
    fun <T> normalTween(): TweenSpec<T> = tween(durationMillis = DurationNormal, easing = EasingStandard)
    fun <T> slowTween(): TweenSpec<T> = tween(durationMillis = DurationSlow, easing = EasingStandard)
}

object SpringTokens {
    /** Bouncy spring for playful micro-interactions (buttons, pills, indicators) */
    fun <T> bouncy(): SpringSpec<T> = spring(
        dampingRatio = 0.65f,
        stiffness = Spring.StiffnessMediumLow
    )

    /** Expressive spring for page/sheet transitions */
    fun <T> expressive(): SpringSpec<T> = spring(
        dampingRatio = 0.78f,
        stiffness = Spring.StiffnessLow
    )

    /** Snappy spring for quick responsive feedback (toggles, sliders) */
    fun <T> snappy(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    /** Soft gentle spring for smooth volume/alpha fades */
    fun <T> soft(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    )
}

data class MotionConfig(
    val animationsEnabled: Boolean = true,
    val animationScale: Float = 1.0f,
    val reducedMotion: Boolean = false,
    val hapticEnabled: Boolean = true
) {
    fun scaleDuration(baseDurationMs: Int): Int {
        if (!animationsEnabled || reducedMotion) return 0
        return (baseDurationMs * animationScale).toInt()
    }
}

val LocalMotionConfig = staticCompositionLocalOf { MotionConfig() }
