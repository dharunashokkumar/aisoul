package com.aisoul.app.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.aisoul.app.ui.theme.StaggerStepMillis
import com.aisoul.app.ui.theme.aiSoulSpring
import com.aisoul.app.ui.theme.fadeSpec
import com.aisoul.app.ui.theme.reducedMotionFade
import com.aisoul.app.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * DESIGN.md §4 screen enter: each section fades from opacity 0 /
 * translateY 16dp to rest, 40ms stagger between siblings, once per entry.
 * Under reduced motion, movement is replaced by a 150ms fade.
 */
fun Modifier.staggeredEntrance(index: Int): Modifier = composed {
    val reduced = rememberReducedMotion()
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(if (reduced) 0f else 1f) }
    val density = LocalDensity.current
    val offsetPx = with(density) { 16.dp.toPx() }

    LaunchedEffect(Unit) {
        delay(index * StaggerStepMillis)
        launch { alpha.animateTo(1f, if (reduced) reducedMotionFade() else fadeSpec()) }
        if (!reduced) launch { offsetY.animateTo(0f, aiSoulSpring()) }
    }

    graphicsLayer {
        this.alpha = alpha.value
        translationY = offsetY.value * offsetPx
    }
}
