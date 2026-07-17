package com.aisoul.app.ui.memory

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aisoul.app.di.AppContainer
import com.aisoul.app.ui.common.AiSoulIcons
import com.aisoul.app.ui.common.AiSoulTextField
import com.aisoul.app.ui.common.CountUpText
import com.aisoul.app.ui.common.GhostButton
import com.aisoul.app.ui.common.SecondaryButton
import com.aisoul.app.ui.common.hapticReject
import com.aisoul.app.ui.common.pressable
import com.aisoul.app.ui.common.staggeredEntrance
import com.aisoul.app.ui.theme.BorderSubtle
import com.aisoul.app.ui.theme.LocalAiSoulTypography
import com.aisoul.app.ui.theme.Negative
import com.aisoul.app.ui.theme.RadiusCard
import com.aisoul.app.ui.theme.RadiusChip
import com.aisoul.app.ui.theme.Space
import com.aisoul.app.ui.theme.Surface1
import com.aisoul.app.ui.theme.Surface2
import com.aisoul.app.ui.theme.TextPrimary
import com.aisoul.app.ui.theme.TextSecondary
import com.aisoul.app.ui.theme.TextTertiary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** SPEC §3 — the memory feed: what was learned, when, each deletable. */
@Composable
fun MemoryScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenFile: (String) -> Unit,
) {
    val viewModel: MemoryViewModel = viewModel(factory = MemoryViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val type = LocalAiSoulTypography.current
    val view = LocalView.current
    var confirmText by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = Space.screen)
                .padding(top = Space.s8)
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

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Space.screen, vertical = Space.s16),
        ) {
            item {
                Column(modifier = Modifier.staggeredEntrance(0)) {
                    Text(text = "MEMORY", style = type.overline, color = TextTertiary)
                    Spacer(Modifier.height(Space.s12))
                    CountUpText(
                        value = state.memories.size,
                        style = type.dataHero,
                        color = TextPrimary,
                    )
                    Text(
                        text = if (state.memories.size == 1) "thing it knows about you" else "things it knows about you",
                        style = type.body,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(Space.stack))
                }
            }

            if (state.pending.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RadiusCard)
                            .background(Surface1)
                            .border(1.dp, BorderSubtle, RadiusCard)
                            .padding(Space.card),
                    ) {
                        Text(text = "WANTS TO FORGET", style = type.overline, color = TextTertiary)
                        Spacer(Modifier.height(Space.s12))
                        state.pending.forEach { pending ->
                            Column(modifier = Modifier.padding(vertical = Space.s8)) {
                                Text(text = pending.slug, style = type.body, color = TextPrimary)
                                if (pending.reason.isNotBlank()) {
                                    Text(text = pending.reason, style = type.caption, color = TextSecondary)
                                }
                                Row {
                                    GhostButton(text = "forget it", onClick = { viewModel.forget(pending.slug) })
                                    GhostButton(text = "keep it", onClick = { viewModel.keepPending(pending.slug) })
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(Space.stack))
                }
            }

            if (state.loaded && state.memories.isEmpty()) {
                item {
                    Column(modifier = Modifier.staggeredEntrance(1)) {
                        Text(text = "nothing here yet", style = type.headline, color = TextPrimary)
                        Spacer(Modifier.height(Space.s8))
                        Text(
                            text = "talk for a while. durable things end up here on their own.",
                            style = type.body,
                            color = TextSecondary,
                        )
                    }
                }
            }

            items(state.memories.size) { index ->
                val memory = state.memories[index]
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RadiusCard)
                        .pressable(onClick = { onOpenFile("memories/${memory.slug}.md") })
                        .padding(vertical = Space.s12),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = memory.name,
                            style = type.body,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            modifier = Modifier
                                .clip(RadiusChip)
                                .background(Surface2)
                                .padding(horizontal = Space.s12, vertical = Space.s4),
                        ) {
                            Text(text = memory.type, style = type.caption, color = TextSecondary)
                        }
                    }
                    if (memory.description.isNotBlank()) {
                        Text(text = memory.description, style = type.caption, color = TextSecondary)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "learned ${relativeDay(memory.modifiedAt)}",
                            style = type.caption,
                            color = TextTertiary,
                            modifier = Modifier.weight(1f),
                        )
                        GhostButton(text = "forget", onClick = {
                            view.hapticReject()
                            viewModel.forget(memory.slug)
                        })
                    }
                }
            }

            if (state.memories.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(Space.stack))
                    if (!confirming) {
                        SecondaryButton(text = "forget everything", onClick = { confirming = true })
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(Space.s12)) {
                            Text(
                                text = "type forget to erase all ${state.memories.size} memories. there is no undo.",
                                style = type.caption,
                                color = Negative,
                            )
                            AiSoulTextField(
                                value = confirmText,
                                onValueChange = { confirmText = it },
                                label = "type forget",
                            )
                            SecondaryButton(
                                text = "erase ${state.memories.size} memories",
                                enabled = confirmText.trim().lowercase() == "forget",
                                onClick = {
                                    view.hapticReject()
                                    viewModel.forgetEverything()
                                    confirming = false
                                    confirmText = ""
                                },
                            )
                            GhostButton(text = "never mind", onClick = {
                                confirming = false
                                confirmText = ""
                            })
                        }
                    }
                    Spacer(Modifier.height(Space.s48))
                }
            }
        }
    }
}

private fun relativeDay(timestamp: Long): String {
    val days = ((System.currentTimeMillis() - timestamp) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "today"
        days == 1 -> "yesterday"
        days < 30 -> "$days days ago"
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestamp))
    }
}
