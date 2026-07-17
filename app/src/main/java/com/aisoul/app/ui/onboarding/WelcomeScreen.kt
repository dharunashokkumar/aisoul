package com.aisoul.app.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aisoul.app.ui.common.PrimaryButton
import com.aisoul.app.ui.common.staggeredEntrance
import com.aisoul.app.ui.theme.LocalAiSoulTypography
import com.aisoul.app.ui.theme.Space
import com.aisoul.app.ui.theme.TextPrimary
import com.aisoul.app.ui.theme.TextSecondary
import com.aisoul.app.ui.theme.TextTertiary

/** SPEC §4 step 1 — one screen, one headline, one button. */
@Composable
fun WelcomeScreen(onBegin: () -> Unit) {
    val type = LocalAiSoulTypography.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = Space.screen),
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            text = "AISOUL",
            style = type.overline,
            color = TextTertiary,
            modifier = Modifier.staggeredEntrance(0),
        )
        Spacer(Modifier.height(Space.s16))
        Text(
            text = "an ai that grows around you",
            style = type.display,
            color = TextPrimary,
            modifier = Modifier.staggeredEntrance(1),
        )
        Spacer(Modifier.height(Space.s16))
        Text(
            text = "no account. no cloud. your keys, your files, your phone.",
            style = type.body,
            color = TextSecondary,
            modifier = Modifier.staggeredEntrance(2),
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton(text = "begin", onClick = onBegin, modifier = Modifier.staggeredEntrance(3))
        Spacer(Modifier.height(Space.s32))
    }
}
