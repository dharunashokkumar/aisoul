package com.aisoul.app.ui.common

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.aisoul.app.ui.theme.CountUpMillis
import com.aisoul.app.ui.theme.FadeEasing
import com.aisoul.app.ui.theme.rememberReducedMotion

/**
 * DESIGN.md §4 — big numbers never just appear; they count up from the
 * previous value over ~700ms.
 */
@Composable
fun CountUpText(
    value: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    format: (Int) -> String = { it.toString() },
) {
    val reduced = rememberReducedMotion()
    val animated by animateIntAsState(
        targetValue = value,
        animationSpec = tween(if (reduced) 0 else CountUpMillis, easing = FadeEasing),
        label = "countUp",
    )
    BasicText(text = format(animated), style = style.copy(color = color), modifier = modifier)
}
