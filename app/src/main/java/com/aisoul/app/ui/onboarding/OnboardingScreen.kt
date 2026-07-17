package com.aisoul.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aisoul.app.di.AppContainer
import com.aisoul.app.providers.Role
import com.aisoul.app.ui.common.AiSoulIcons
import com.aisoul.app.ui.common.GhostButton
import com.aisoul.app.ui.common.PrimaryButton
import com.aisoul.app.ui.common.hapticConfirm
import com.aisoul.app.ui.common.pressable
import com.aisoul.app.ui.common.staggeredEntrance
import com.aisoul.app.ui.theme.AccentIce
import com.aisoul.app.ui.theme.BorderSubtle
import com.aisoul.app.ui.theme.LocalAiSoulTypography
import com.aisoul.app.ui.theme.Negative
import com.aisoul.app.ui.theme.RadiusButton
import com.aisoul.app.ui.theme.RadiusCard
import com.aisoul.app.ui.theme.RadiusInput
import com.aisoul.app.ui.theme.Space
import com.aisoul.app.ui.theme.Surface1
import com.aisoul.app.ui.theme.Surface2
import com.aisoul.app.ui.theme.TextInverse
import com.aisoul.app.ui.theme.TextPrimary
import com.aisoul.app.ui.theme.TextSecondary
import com.aisoul.app.ui.theme.TextTertiary
import com.aisoul.app.ui.theme.fadeSpec

/**
 * SPEC §4 steps 3–4 — the interview and the soul reveal, one route with
 * three phases. The reveal is where the product's honesty lands: these
 * files are all it knows; they're yours.
 */
@Composable
fun OnboardingScreen(
    container: AppContainer,
    onDone: () -> Unit,
) {
    val viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val view = LocalView.current

    LaunchedEffect(state.done) {
        if (state.done) {
            view.hapticConfirm()
            onDone()
        }
    }

    AnimatedContent(
        targetState = state.phase,
        transitionSpec = { fadeIn(fadeSpec()) togetherWith fadeOut(fadeSpec()) },
        label = "onboardingPhase",
    ) { phase ->
        when (phase) {
            OnboardingPhase.INTERVIEW -> InterviewPhase(state, viewModel)
            OnboardingPhase.DRAFTING -> DraftingPhase(state, viewModel)
            OnboardingPhase.REVEAL -> RevealPhase(state, viewModel)
        }
    }
}

@Composable
private fun InterviewPhase(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    val type = LocalAiSoulTypography.current
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val itemCount = state.messages.size + (if (state.streamingText != null) 1 else 0)
    LaunchedEffect(itemCount, state.streamingText?.length) {
        if (itemCount > 0) listState.scrollToItem(itemCount - 1)
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
                .padding(horizontal = Space.s12, vertical = Space.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "FIRST MEETING",
                style = type.overline,
                color = TextTertiary,
                modifier = Modifier.padding(start = Space.s12).weight(1f),
            )
            if (state.canWrapUp) {
                GhostButton(text = "that's enough", onClick = { viewModel.wrapUp() })
            }
            GhostButton(text = "skip", onClick = { viewModel.skip() })
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Space.screen, vertical = Space.s16),
            verticalArrangement = Arrangement.spacedBy(Space.s24),
        ) {
            items(state.messages.size) { index ->
                val message = state.messages[index]
                if (message.role == Role.USER) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 300.dp)
                                .clip(RadiusCard)
                                .background(Surface2)
                                .padding(horizontal = Space.s16, vertical = Space.s12),
                        ) {
                            Text(text = message.text, style = type.body, color = TextPrimary)
                        }
                    }
                } else {
                    Text(text = message.text, style = type.body, color = TextPrimary)
                }
            }
            state.streamingText?.let { streaming ->
                item(key = "streaming") {
                    if (streaming.isEmpty()) {
                        Text(text = "thinking…", style = type.caption, color = TextTertiary)
                    } else {
                        Text(text = streaming, style = type.body, color = TextPrimary)
                    }
                }
            }
            state.error?.let { error ->
                item(key = "error") {
                    Column {
                        Text(text = error, style = type.caption, color = Negative)
                        Spacer(Modifier.height(Space.s8))
                        GhostButton(text = "skip the interview", onClick = { viewModel.skip() })
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.screen, vertical = Space.s12),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RadiusInput)
                    .background(Surface1)
                    .padding(horizontal = Space.s16, vertical = Space.s16),
            ) {
                if (input.isEmpty()) {
                    Text(text = "answer in your own words", style = type.body, color = TextTertiary)
                }
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    textStyle = type.body.copy(color = TextPrimary),
                    cursorBrush = SolidColor(AccentIce),
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.padding(start = Space.s8))
            val canSend = input.isNotBlank() && !state.isStreaming
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RadiusButton)
                    .background(if (canSend) AccentIce else Surface1)
                    .pressable(enabled = canSend) {
                        viewModel.send(input)
                        input = ""
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AiSoulIcons.ArrowUp,
                    contentDescription = "send",
                    tint = if (canSend) TextInverse else TextTertiary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun DraftingPhase(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    val type = LocalAiSoulTypography.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = Space.screen),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "writing down who i am to you.",
            style = type.headline,
            color = TextPrimary,
            modifier = Modifier.staggeredEntrance(0),
        )
        Spacer(Modifier.height(Space.s12))
        Text(
            text = "two small files. you'll see both before they're saved.",
            style = type.body,
            color = TextSecondary,
            modifier = Modifier.staggeredEntrance(1),
        )
        state.error?.let { error ->
            Spacer(Modifier.height(Space.s24))
            Text(text = error, style = type.caption, color = Negative)
            Spacer(Modifier.height(Space.s12))
            Row {
                GhostButton(text = "try again", onClick = { viewModel.retryDrafting() })
                GhostButton(text = "skip", onClick = { viewModel.skip() })
            }
        }
    }
}

@Composable
private fun RevealPhase(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    val type = LocalAiSoulTypography.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.screen),
    ) {
        Spacer(Modifier.height(Space.s48))
        Text(
            text = "YOUR FILES",
            style = type.overline,
            color = TextTertiary,
            modifier = Modifier.staggeredEntrance(0),
        )
        Spacer(Modifier.height(Space.s12))
        Text(
            text = "this is all it knows.",
            style = type.headline,
            color = TextPrimary,
            modifier = Modifier.staggeredEntrance(1),
        )
        Spacer(Modifier.height(Space.s12))
        Text(
            text = "edit anything. these two files live on your phone and you can rewrite them any time.",
            style = type.body,
            color = TextSecondary,
            modifier = Modifier.staggeredEntrance(2),
        )

        Spacer(Modifier.height(Space.stack))
        DraftEditor(
            label = "SOUL.MD",
            value = state.soulDraft,
            onValueChange = viewModel::updateSoulDraft,
            modifier = Modifier.staggeredEntrance(3),
        )
        Spacer(Modifier.height(Space.s24))
        DraftEditor(
            label = "USER.MD",
            value = state.userDraft,
            onValueChange = viewModel::updateUserDraft,
            modifier = Modifier.staggeredEntrance(4),
        )

        Spacer(Modifier.height(Space.stack))
        PrimaryButton(text = "keep these files", onClick = { viewModel.keepFiles() })
        Spacer(Modifier.height(Space.s8))
        GhostButton(text = "redo the interview", onClick = { viewModel.redoInterview() })
        Spacer(Modifier.height(Space.s32))
    }
}

@Composable
private fun DraftEditor(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = LocalAiSoulTypography.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RadiusCard)
            .background(Surface1)
            .border(1.dp, BorderSubtle, RadiusCard)
            .padding(Space.card),
    ) {
        Text(text = label, style = type.overline, color = TextTertiary)
        Spacer(Modifier.height(Space.s12))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = type.body.copy(color = TextPrimary),
            cursorBrush = SolidColor(AccentIce),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
