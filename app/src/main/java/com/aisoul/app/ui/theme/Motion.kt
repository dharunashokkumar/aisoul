package com.aisoul.app.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

// DESIGN.md §4 — one spring for the whole app. Nothing else.
fun <T> aiSoulSpring(): SpringSpec<T> = spring(dampingRatio = 0.75f, stiffness = 380f)

// Where a duration is unavoidable (fades): 220ms, cubic-bezier(0.2, 0, 0, 1)
val FadeEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
fun <T> fadeSpec(): TweenSpec<T> = tween(durationMillis = 220, easing = FadeEasing)

// Reduced motion: replace all movement with 150ms fades
fun <T> reducedMotionFade(): TweenSpec<T> = tween(durationMillis = 150, easing = FadeEasing)

const val StaggerStepMillis = 40L
const val CountUpMillis = 700

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}
