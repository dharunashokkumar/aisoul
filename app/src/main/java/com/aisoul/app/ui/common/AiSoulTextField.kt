package com.aisoul.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.aisoul.app.ui.theme.AccentIce
import com.aisoul.app.ui.theme.BorderStrong
import com.aisoul.app.ui.theme.LocalAiSoulTypography
import com.aisoul.app.ui.theme.RadiusInput
import com.aisoul.app.ui.theme.Surface1
import com.aisoul.app.ui.theme.TextPrimary
import com.aisoul.app.ui.theme.TextTertiary
import com.aisoul.app.ui.theme.aiSoulSpring
import com.aisoul.app.ui.theme.fadeSpec

/**
 * DESIGN.md §5 input — surface-1 fill, 14dp radius, 56dp tall, no border at
 * rest, border-strong + accent caret when focused. The label springs from
 * placeholder position up to a caption.
 */
@Composable
fun AiSoulTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val type = LocalAiSoulTypography.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val floating = focused || value.isNotEmpty()

    val labelProgress by animateFloatAsState(
        targetValue = if (floating) 1f else 0f,
        animationSpec = aiSoulSpring(),
        label = "labelFloat",
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) BorderStrong else Color.Transparent,
        animationSpec = fadeSpec(),
        label = "inputBorder",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clip(RadiusInput)
            .background(Surface1)
            .border(1.dp, borderColor, RadiusInput),
    ) {
        Text(
            text = label,
            style = type.body,
            color = TextTertiary,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 16.dp)
                .graphicsLayer {
                    val scale = 1f - 0.2f * labelProgress
                    scaleX = scale
                    scaleY = scale
                    translationY = -14.dp.toPx() * labelProgress
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                },
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = type.body.copy(color = TextPrimary),
            cursorBrush = SolidColor(AccentIce),
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterStart)
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        )
    }
}
