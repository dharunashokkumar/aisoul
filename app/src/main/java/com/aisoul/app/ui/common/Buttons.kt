package com.aisoul.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.aisoul.app.ui.theme.AccentIce
import com.aisoul.app.ui.theme.BorderSubtle
import com.aisoul.app.ui.theme.LocalAiSoulTypography
import com.aisoul.app.ui.theme.RadiusButton
import com.aisoul.app.ui.theme.Surface2
import com.aisoul.app.ui.theme.TextInverse
import com.aisoul.app.ui.theme.TextPrimary
import com.aisoul.app.ui.theme.TextSecondary
import com.aisoul.app.ui.theme.fadeSpec

/** DESIGN.md §5 primary button — 56dp, accent fill, one per screen maximum. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val type = LocalAiSoulTypography.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RadiusButton)
            .background(AccentIce)
            .alpha(if (enabled) 1f else 0.5f)
            .pressable(enabled = enabled, pressedScale = 0.96f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = type.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(600)), color = TextInverse)
    }
}

/** DESIGN.md §5 secondary button — surface-2 fill, subtle border. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val type = LocalAiSoulTypography.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RadiusButton)
            .background(Surface2)
            .border(1.dp, BorderSubtle, RadiusButton)
            .alpha(if (enabled) 1f else 0.5f)
            .pressable(enabled = enabled, pressedScale = 0.96f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = type.body, color = TextPrimary)
    }
}

/** DESIGN.md §5 ghost button — no fill, brightens to text-primary on press. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = LocalAiSoulTypography.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val color by animateColorAsState(
        targetValue = if (pressed) TextPrimary else TextSecondary,
        animationSpec = fadeSpec(),
        label = "ghostColor",
    )
    Text(
        text = text,
        style = type.body,
        color = color,
        modifier = modifier
            .clip(RadiusButton)
            .pressable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}
