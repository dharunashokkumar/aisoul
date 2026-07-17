package com.aisoul.app.ui.setup

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aisoul.app.di.AppContainer
import com.aisoul.app.providers.ProviderType
import com.aisoul.app.ui.common.AiSoulIcons
import com.aisoul.app.ui.common.AiSoulTextField
import com.aisoul.app.ui.common.GhostButton
import com.aisoul.app.ui.common.PrimaryButton
import com.aisoul.app.ui.common.hapticConfirm
import com.aisoul.app.ui.common.hapticReject
import com.aisoul.app.ui.common.pressable
import com.aisoul.app.ui.common.staggeredEntrance
import com.aisoul.app.ui.theme.AccentIce
import com.aisoul.app.ui.theme.BorderSubtle
import com.aisoul.app.ui.theme.LocalAiSoulTypography
import com.aisoul.app.ui.theme.Negative
import com.aisoul.app.ui.theme.RadiusCard
import com.aisoul.app.ui.theme.Space
import com.aisoul.app.ui.theme.Surface1
import com.aisoul.app.ui.theme.Surface2
import com.aisoul.app.ui.theme.TextPrimary
import com.aisoul.app.ui.theme.TextSecondary
import com.aisoul.app.ui.theme.TextTertiary
import com.aisoul.app.ui.theme.fadeSpec

/**
 * SPEC §4 step 2 — provider setup. Hero: "bring your own key."
 * The full soul interview follows in M1; this screen is the front door.
 */
@Composable
fun SetupScreen(
    container: AppContainer,
    onDone: () -> Unit,
) {
    val viewModel: SetupViewModel = viewModel(factory = SetupViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val type = LocalAiSoulTypography.current
    val context = LocalContext.current
    val view = LocalView.current

    LaunchedEffect(state.done) {
        if (state.done) {
            view.hapticConfirm()
            onDone()
        }
    }
    LaunchedEffect(state.error) {
        if (state.error != null) view.hapticReject()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.screen),
    ) {
        Spacer(Modifier.height(Space.hero))

        Text(
            text = "PROVIDER SETUP",
            style = type.overline,
            color = TextTertiary,
            modifier = Modifier.staggeredEntrance(0),
        )
        Spacer(Modifier.height(Space.s12))
        Text(
            text = "bring your own key.",
            style = type.display,
            color = TextPrimary,
            modifier = Modifier.staggeredEntrance(1),
        )
        Spacer(Modifier.height(Space.s16))
        Text(
            text = "aisoul talks to the provider you choose, with your key. no account, no middleman, nothing leaves this phone except your own requests.",
            style = type.body,
            color = TextSecondary,
            modifier = Modifier.staggeredEntrance(2),
        )

        Spacer(Modifier.height(Space.stack))

        Column(
            modifier = Modifier
                .staggeredEntrance(3)
                .clip(RadiusCard)
                .background(Surface1)
                .border(1.dp, BorderSubtle, RadiusCard)
                .padding(Space.s8),
            verticalArrangement = Arrangement.spacedBy(Space.s4),
        ) {
            ProviderType.entries.forEach { provider ->
                ProviderRow(
                    provider = provider,
                    selected = provider == state.provider,
                    onSelect = { viewModel.selectProvider(provider) },
                )
            }
        }

        Spacer(Modifier.height(Space.stack))

        Column(
            modifier = Modifier.staggeredEntrance(4),
            verticalArrangement = Arrangement.spacedBy(Space.s12),
        ) {
            AiSoulTextField(
                value = state.apiKey,
                onValueChange = viewModel::setApiKey,
                label = when {
                    state.provider == ProviderType.OPENAI_COMPAT -> "api key (optional for lan servers)"
                    state.hasStoredKey -> "api key (saved — paste to replace)"
                    else -> "api key"
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            AiSoulTextField(
                value = state.model,
                onValueChange = viewModel::setModel,
                label = if (state.provider.defaultModel.isBlank()) "model id" else "model — ${state.provider.defaultModel}",
            )
            AnimatedVisibility(
                visible = state.provider == ProviderType.OPENAI_COMPAT,
                enter = fadeIn(fadeSpec()) + expandVertically(),
                exit = fadeOut(fadeSpec()) + shrinkVertically(),
            ) {
                AiSoulTextField(
                    value = state.baseUrl,
                    onValueChange = viewModel::setBaseUrl,
                    label = "base url — https://openrouter.ai/api/v1",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
        }

        Spacer(Modifier.height(Space.s16))

        GhostButton(
            text = "where do i get a key?",
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.provider.keyUrl)))
            },
        )

        state.error?.let { error ->
            Spacer(Modifier.height(Space.s16))
            Text(text = error, style = type.caption, color = Negative)
        }

        Spacer(Modifier.height(Space.stack))

        PrimaryButton(
            text = if (state.validating) "checking your key…" else "validate & save",
            enabled = !state.validating,
            onClick = viewModel::validateAndSave,
        )

        Spacer(Modifier.height(Space.s32))
    }
}

@Composable
private fun ProviderRow(
    provider: ProviderType,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val type = LocalAiSoulTypography.current
    val background by animateColorAsState(
        targetValue = if (selected) Surface2 else Surface1,
        animationSpec = fadeSpec(),
        label = "providerRowBg",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RadiusCard)
            .background(background)
            .pressable(onClick = onSelect)
            .padding(horizontal = Space.s16, vertical = Space.s16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = provider.display,
            style = type.body,
            color = if (selected) TextPrimary else TextSecondary,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = AiSoulIcons.Check,
                contentDescription = "selected",
                tint = AccentIce,
                modifier = Modifier.size(18.dp),
            )
        } else {
            Box(Modifier.size(18.dp))
        }
    }
}
