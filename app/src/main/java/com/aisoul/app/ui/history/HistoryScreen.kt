package com.aisoul.app.ui.history

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.aisoul.app.di.AppContainer
import com.aisoul.app.harness.HarnessStore
import com.aisoul.app.ui.common.AiSoulIcons
import com.aisoul.app.ui.common.GhostButton
import com.aisoul.app.ui.common.hapticReject
import com.aisoul.app.ui.common.pressable
import com.aisoul.app.ui.common.staggeredEntrance
import com.aisoul.app.ui.common.TopBar
import com.aisoul.app.ui.theme.BorderSubtle
import com.aisoul.app.ui.theme.LocalAiSoulTypography
import com.aisoul.app.ui.theme.Negative
import com.aisoul.app.ui.theme.RadiusCard
import com.aisoul.app.ui.theme.Space
import com.aisoul.app.ui.theme.Surface1
import com.aisoul.app.ui.theme.TextPrimary
import com.aisoul.app.ui.theme.TextSecondary
import com.aisoul.app.ui.theme.TextTertiary
import kotlinx.coroutines.launch

/** D-021 — every conversation, newest first. Files under /harness/chats. */
@Composable
fun HistoryScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
) {
    val type = LocalAiSoulTypography.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var chats by remember { mutableStateOf<List<HarnessStore.ChatSummary>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var confirmingId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reload) {
        chats = container.harness.listChats()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        TopBar(label = "HISTORY", onBack = onBack)

        val list = chats
        when {
            list == null -> Unit
            list.isEmpty() -> Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = Space.screen),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(text = "nothing yet.", style = type.headline, color = TextPrimary)
                Spacer(Modifier.height(Space.s12))
                Text(
                    text = "every conversation lands here, as a file you own.",
                    style = type.body,
                    color = TextSecondary,
                )
            }
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = Space.screen, vertical = Space.s16),
                verticalArrangement = Arrangement.spacedBy(Space.s12),
            ) {
                items(list.size) { index ->
                    val chat = list[index]
                    Column(
                        modifier = Modifier
                            .staggeredEntrance(minOf(index, 9))
                            .fillMaxWidth()
                            .clip(RadiusCard)
                            .background(Surface1)
                            .border(1.dp, BorderSubtle, RadiusCard)
                            .pressable { onOpenChat(chat.chatId) }
                            .padding(Space.s16),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = chat.title,
                                    style = type.body,
                                    color = TextPrimary,
                                    maxLines = 1,
                                )
                                Spacer(Modifier.height(Space.s4))
                                Text(
                                    text = "${chat.messageCount} messages · ${relativeTime(chat.modifiedAt)}",
                                    style = type.caption,
                                    color = TextTertiary,
                                )
                            }
                            if (confirmingId != chat.chatId) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RadiusCard)
                                        .pressable { confirmingId = chat.chatId },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = AiSoulIcons.Trash,
                                        contentDescription = "delete chat",
                                        tint = TextTertiary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                        if (confirmingId == chat.chatId) {
                            Spacer(Modifier.height(Space.s8))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "delete this chat?",
                                    style = type.caption,
                                    color = Negative,
                                    modifier = Modifier.weight(1f),
                                )
                                GhostButton(text = "keep", onClick = { confirmingId = null })
                                Spacer(Modifier.width(Space.s4))
                                DeleteButton {
                                    view.hapticReject()
                                    confirmingId = null
                                    scope.launch {
                                        runCatching { container.harness.deleteChat(chat.chatId) }
                                        reload++
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteButton(onClick: () -> Unit) {
    val type = LocalAiSoulTypography.current
    Text(
        text = "delete",
        style = type.body,
        color = Negative,
        modifier = Modifier
            .clip(RadiusCard)
            .pressable(onClick = onClick)
            .padding(horizontal = Space.s12, vertical = Space.s8),
    )
}

private fun relativeTime(at: Long): String {
    val minutes = (System.currentTimeMillis() - at) / 60_000
    return when {
        minutes < 2 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 24 * 60 -> "${minutes / 60}h ago"
        else -> "${minutes / (24 * 60)}d ago"
    }
}
