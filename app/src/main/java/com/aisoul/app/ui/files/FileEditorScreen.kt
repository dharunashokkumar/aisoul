package com.aisoul.app.ui.files

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.aisoul.app.di.AppContainer
import com.aisoul.app.ui.common.AiSoulIcons
import com.aisoul.app.ui.common.PrimaryButton
import com.aisoul.app.ui.common.hapticConfirm
import com.aisoul.app.ui.common.pressable
import com.aisoul.app.ui.theme.AccentIce
import com.aisoul.app.ui.theme.LocalAiSoulTypography
import com.aisoul.app.ui.theme.RadiusCard
import com.aisoul.app.ui.theme.Space
import com.aisoul.app.ui.theme.TextPrimary
import com.aisoul.app.ui.theme.TextSecondary
import com.aisoul.app.ui.theme.TextTertiary
import kotlinx.coroutines.launch

private const val MAX_EDIT_BYTES = 256 * 1024

/** Plain-text editor for one harness file. Atomic save, no drama. */
@Composable
fun FileEditorScreen(
    container: AppContainer,
    path: String,
    onBack: () -> Unit,
) {
    val type = LocalAiSoulTypography.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var text by remember { mutableStateOf<String?>(null) }
    var original by remember { mutableStateOf("") }
    var tooLarge by remember { mutableStateOf(false) }

    LaunchedEffect(path) {
        val content = container.harness.readOrNull(path) ?: ""
        if (content.length > MAX_EDIT_BYTES) {
            tooLarge = true
            text = content.take(4096)
        } else {
            text = content
        }
        original = content
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.screen)
                .padding(top = Space.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RadiusCard)
                    .pressable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AiSoulIcons.Back,
                    contentDescription = "back",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.padding(start = Space.s8))
            Text(
                text = path.lowercase(),
                style = type.caption,
                color = TextTertiary,
            )
        }

        val current = text
        if (current == null) {
            Box(Modifier.fillMaxSize())
        } else if (tooLarge) {
            Column(modifier = Modifier.padding(Space.screen)) {
                Text(text = "too large to edit here", style = type.headline, color = TextPrimary)
                Spacer(Modifier.height(Space.s8))
                Text(
                    text = "this file is over 256 kb. it stays safe on disk.",
                    style = type.body,
                    color = TextSecondary,
                )
            }
        } else {
            BasicTextField(
                value = current,
                onValueChange = { text = it },
                textStyle = if (path.endsWith(".md")) {
                    type.body.copy(color = TextPrimary)
                } else {
                    type.code.copy(color = TextPrimary)
                },
                cursorBrush = SolidColor(AccentIce),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.screen, vertical = Space.s16),
            )
            if (current != original) {
                PrimaryButton(
                    text = "save changes",
                    onClick = {
                        scope.launch {
                            container.harness.writeFile(path, current)
                            original = current
                            view.hapticConfirm()
                        }
                    },
                    modifier = Modifier.padding(horizontal = Space.screen, vertical = Space.s12),
                )
            }
        }
    }
}
