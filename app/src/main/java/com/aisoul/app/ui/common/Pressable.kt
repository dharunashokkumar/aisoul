package com.aisoul.app.ui.common

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import com.aisoul.app.ui.theme.aiSoulSpring

/**
 * DESIGN.md §4 — any tap-down scales to 0.97 and springs back; every tap
 * fires CONTEXT_CLICK. Ripple is suppressed: the scale IS the feedback.
 */
fun Modifier.pressable(
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = aiSoulSpring(),
        label = "pressScale",
    )
    val view = LocalView.current
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
        ) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            onClick()
        }
}

fun android.view.View.hapticConfirm() = performHapticFeedback(HapticFeedbackConstants.CONFIRM)
fun android.view.View.hapticReject() = performHapticFeedback(HapticFeedbackConstants.REJECT)
fun android.view.View.hapticTick() = performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
