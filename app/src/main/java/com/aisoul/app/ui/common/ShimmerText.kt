@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.aisoul.app.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.aisoul.app.ui.theme.TextPrimary
import com.aisoul.app.ui.theme.TextTertiary
import com.aisoul.app.ui.theme.rememberReducedMotion

/**
 * D-028 — the "working" shine: a soft highlight sweeping through quiet text
 * while the model thinks or a tool runs. Reduced motion → plain tertiary.
 */
@Composable
fun ShimmerText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    if (rememberReducedMotion()) {
        Text(text = text, style = style, color = TextTertiary, modifier = modifier)
        return
    }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerSweep",
    )
    val window = 340f
    val head = sweep * (window * 2f) - window
    val brush = Brush.linearGradient(
        colors = listOf(TextTertiary, TextPrimary, TextTertiary),
        start = Offset(head, 0f),
        end = Offset(head + window, 0f),
    )
    Text(
        text = buildAnnotatedString { withStyle(SpanStyle(brush = brush)) { append(text) } },
        style = style,
        modifier = modifier,
    )
}
