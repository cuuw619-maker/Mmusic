package com.example.ui.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

enum class HapticFeedbackType {
    LIGHT_TICK,
    MEDIUM_TICK,
    HEAVY_TICK,
    CONFIRM,
    GESTURE_THRESHOLD
}

class HapticHelper(private val view: View) {
    fun performHaptic(type: HapticFeedbackType) {
        val constant = when (type) {
            HapticFeedbackType.LIGHT_TICK -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    HapticFeedbackConstants.SEGMENT_TICK
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    HapticFeedbackConstants.KEYBOARD_TAP
                } else {
                    HapticFeedbackConstants.VIRTUAL_KEY
                }
            }
            HapticFeedbackType.MEDIUM_TICK -> {
                HapticFeedbackConstants.KEYBOARD_TAP
            }
            HapticFeedbackType.HEAVY_TICK -> {
                HapticFeedbackConstants.LONG_PRESS
            }
            HapticFeedbackType.CONFIRM -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.CONFIRM
                } else {
                    HapticFeedbackConstants.VIRTUAL_KEY
                }
            }
            HapticFeedbackType.GESTURE_THRESHOLD -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
                } else {
                    HapticFeedbackConstants.CLOCK_TICK
                }
            }
        }
        view.performHapticFeedback(constant)
    }
}

@Composable
fun rememberHapticHelper(): HapticHelper {
    val view = LocalView.current
    return remember(view) { HapticHelper(view) }
}
