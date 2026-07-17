package com.aisoul.app.ui.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aisoul.app.AppLinks
import com.aisoul.app.BuildConfig
import com.aisoul.app.di.AppContainer
import com.aisoul.app.providers.ChatMessage
import com.aisoul.app.providers.Part
import com.aisoul.app.providers.Role
import com.aisoul.app.ui.common.AiSoulIcons
import com.aisoul.app.ui.common.GhostButton
import com.aisoul.app.ui.common.ShimmerText
import com.aisoul.app.ui.common.pressable
import com.aisoul.app.ui.common.staggeredEntrance
import com.aisoul.app.ui.theme.AccentIce
import com.aisoul.app.ui.theme.BorderStrong
import com.aisoul.app.ui.theme.BorderSubtle
import com.aisoul.app.ui.theme.Divider
import com.aisoul.app.ui.theme.LocalAiSoulTypography
import com.aisoul.app.ui.theme.Negative
import com.aisoul.app.ui.theme.Positive
import com.aisoul.app.ui.theme.RadiusButton
import com.aisoul.app.ui.theme.RadiusCard
import com.aisoul.app.ui.theme.RadiusChip
import com.aisoul.app.ui.theme.RadiusInput
import com.aisoul.app.ui.theme.Space
import com.aisoul.app.ui.theme.Surface1
import com.aisoul.app.ui.theme.Surface2
import com.aisoul.app.ui.theme.TextInverse
import com.aisoul.app.ui.theme.TextPrimary
import com.aisoul.app.ui.theme.TextSecondary
import com.aisoul.app.ui.theme.TextTertiary
import com.aisoul.app.ui.theme.aiSoulSpring
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** SPEC §5 — streaming chat + inline tool cards. Nothing the agent does is invisible. */
@Composable
fun ChatScreen(
    container: AppContainer,
    chatId: String?,
    initialPrompt: String,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val viewModel: ChatViewModel = viewModel(
        key = "chat-${chatId ?: "new"}",
        factory = ChatViewModel.factory(container, chatId),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val approval by viewModel.approval.collectAsStateWithLifecycle()
    val type = LocalAiSoulTypography.current
    val context = LocalContext.current
    var input by remember { mutableStateOf(initialPrompt) }
    var reportTarget by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    // SPEC §12 — agent-run progress notification; asked at first send, not at onboarding
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val itemCount = state.messages.size + (if (state.streamingText != null) 1 else 0)
    LaunchedEffect(itemCount, state.streamingText?.length, state.activeTool) {
        if (itemCount > 0) listState.scrollToItem(itemCount - 1)
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                Text(
                    text = "AISOUL",
                    style = type.overline,
                    color = TextTertiary,
                    modifier = Modifier.padding(start = Space.s4).weight(1f),
                )
                GhostButton(text = "history", onClick = onOpenHistory)
                GhostButton(text = "new", onClick = { viewModel.newChat() })
            }

            if (state.messages.isEmpty() && state.streamingText == null) {
                EmptyChat(modifier = Modifier.weight(1f))
            } else {
                val results = remember(state.messages) { resultsById(state.messages) }
                val lastAssistantIndex = state.messages.indexOfLast {
                    it.role == Role.ASSISTANT && it.text.isNotBlank()
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
                        MessageItem(
                            message = state.messages[index],
                            results = results,
                            showRetry = index == lastAssistantIndex && !state.isStreaming,
                            onRetry = { viewModel.retry() },
                            onReport = { reportTarget = it },
                        )
                    }
                    state.streamingText?.let { streaming ->
                        item(key = "streaming") {
                            Column(verticalArrangement = Arrangement.spacedBy(Space.s12)) {
                                if (streaming.isNotEmpty()) AssistantText(streaming)
                                when {
                                    state.activeTool != null -> ActiveToolChip(state.activeTool ?: "")
                                    streaming.isEmpty() -> ShimmerText("thinking…", style = type.caption)
                                }
                            }
                        }
                    }
                    state.error?.let { error ->
                        item(key = "error") {
                            Text(text = error, style = type.caption, color = Negative)
                        }
                    }
                }
            }

            if (state.messages.isEmpty() && state.error != null) {
                Text(
                    text = state.error ?: "",
                    style = type.caption,
                    color = Negative,
                    modifier = Modifier.padding(horizontal = Space.screen, vertical = Space.s8),
                )
            }

            InputBar(
                value = input,
                onValueChange = { input = it },
                isStreaming = state.isStreaming,
                onSend = {
                    ensureNotificationPermission()
                    viewModel.send(input)
                    input = ""
                },
                onStop = { viewModel.stop() },
            )
        }

        approval?.let { pending ->
            ApprovalSheet(
                approval = pending,
                onRespond = { approved, always -> viewModel.respondApproval(approved, always) },
            )
        }

        reportTarget?.let { reported ->
            ReportSheet(
                text = reported,
                onSend = {
                    val body = buildString {
                        append("reporting an ai response from aisoul ${BuildConfig.VERSION_NAME}.\n\n")
                        append("why i'm reporting it:\n(describe what's wrong here)\n\n")
                        append("--- the response ---\n")
                        append(reported)
                    }
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:")
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(AppLinks.DEVELOPER_EMAIL))
                        putExtra(Intent.EXTRA_SUBJECT, "aisoul — reported response")
                        putExtra(Intent.EXTRA_TEXT, body)
                    }
                    runCatching { context.startActivity(intent) }
                    reportTarget = null
                },
                onDismiss = { reportTarget = null },
            )
        }
    }
}

/**
 * SPEC §5 / §12 — Play AI-content compliance: report any AI response. The
 * sheet shows the exact text that will ride in the email; nothing is sent
 * until the user sends it from their own mail app.
 */
@Composable
private fun ReportSheet(text: String, onSend: () -> Unit, onDismiss: () -> Unit) {
    val type = LocalAiSoulTypography.current
    Box(modifier = Modifier.fillMaxSize().background(com.aisoul.app.ui.theme.Scrim)) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .staggeredEntrance(0)
                .clip(com.aisoul.app.ui.theme.RadiusSheet)
                .background(Surface2)
                .navigationBarsPadding()
                .padding(Space.card),
        ) {
            Text(text = "REPORT", style = type.overline, color = TextTertiary)
            Spacer(Modifier.height(Space.s8))
            Text(text = "report this response", style = type.title, color = TextPrimary)
            Spacer(Modifier.height(Space.s8))
            Text(
                text = "this opens an email to the developer with exactly the text below — nothing else from your harness.",
                style = type.caption,
                color = TextSecondary,
            )
            Spacer(Modifier.height(Space.s16))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .clip(RadiusInput)
                    .background(Surface1)
                    .padding(Space.s16)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(text = text, style = type.code, color = TextSecondary)
            }
            Spacer(Modifier.height(Space.s24))
            Row(modifier = Modifier.fillMaxWidth()) {
                com.aisoul.app.ui.common.SecondaryButton(
                    text = "cancel",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Space.s12))
                com.aisoul.app.ui.common.PrimaryButton(
                    text = "report via email",
                    onClick = onSend,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** toolCallId → result, across the whole transcript */
private fun resultsById(messages: List<ChatMessage>): Map<String, Part.ToolResult> =
    messages.flatMap { it.parts }.filterIsInstance<Part.ToolResult>().associateBy { it.toolCallId }

@Composable
private fun EmptyChat(modifier: Modifier = Modifier) {
    val type = LocalAiSoulTypography.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screen),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "you're in.",
            style = type.headline,
            color = TextPrimary,
            modifier = Modifier.staggeredEntrance(0),
        )
        Spacer(Modifier.height(Space.s12))
        Text(
            text = "say something. every word stays in files on this phone.",
            style = type.body,
            color = TextSecondary,
            modifier = Modifier.staggeredEntrance(1),
        )
    }
}

@Composable
private fun MessageItem(
    message: ChatMessage,
    results: Map<String, Part.ToolResult>,
    showRetry: Boolean,
    onRetry: () -> Unit,
    onReport: (String) -> Unit,
) {
    val type = LocalAiSoulTypography.current
    if (message.role == Role.USER) {
        // tool results ride in user-role messages; their cards render with the call
        if (message.text.isBlank()) return
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
        Column(verticalArrangement = Arrangement.spacedBy(Space.s12)) {
            message.parts.forEach { part ->
                when (part) {
                    is Part.Text -> if (part.text.isNotBlank()) AssistantText(part.text)
                    is Part.ToolCall -> ToolCallCard(part, results[part.id])
                    is Part.ToolResult -> Unit
                }
            }
            // D-028 — every AI message carries its own actions; no hidden gestures
            if (message.text.isNotBlank()) {
                MessageActions(
                    text = message.text,
                    showRetry = showRetry,
                    onRetry = onRetry,
                    onReport = { onReport(message.text) },
                )
            }
        }
    }
}

/** copy · retry · report — quiet icons under each AI message (D-028) */
@Composable
private fun MessageActions(
    text: String,
    showRetry: Boolean,
    onRetry: () -> Unit,
    onReport: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val view = LocalView.current
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s4)) {
        MessageAction(icon = AiSoulIcons.Copy, label = "copy") {
            clipboard.setText(AnnotatedString(text))
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
        if (showRetry) {
            MessageAction(icon = AiSoulIcons.Retry, label = "retry", onClick = onRetry)
        }
        MessageAction(icon = AiSoulIcons.Flag, label = "report", onClick = onReport)
    }
}

@Composable
private fun MessageAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RadiusChip)
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = TextTertiary,
            modifier = Modifier.size(15.dp),
        )
    }
}

/**
 * SPEC §5 / D-028 — which tool, exact input, live status. Auto-expands while
 * running so work-in-progress is watchable, collapses once done.
 */
@Composable
private fun ToolCallCard(call: Part.ToolCall, result: Part.ToolResult?) {
    val type = LocalAiSoulTypography.current
    val running = result == null
    var expanded by remember(running) { mutableStateOf(running) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = aiSoulSpring(),
        label = "chevron",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RadiusInput)
            .background(Surface1)
            .border(1.dp, BorderSubtle, RadiusInput)
            .animateContentSize(animationSpec = aiSoulSpring()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressable { expanded = !expanded }
                .padding(horizontal = Space.s16, vertical = Space.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RadiusChip)
                    .background(
                        when {
                            result == null -> AccentIce
                            result.isError -> Negative
                            else -> Positive
                        },
                    ),
            )
            Spacer(Modifier.width(Space.s12))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = call.name.uppercase(), style = type.overline, color = TextTertiary)
                Spacer(Modifier.height(Space.s4))
                Text(
                    text = displayInput(call),
                    style = type.code,
                    color = TextSecondary,
                    maxLines = 2,
                )
                if (running) {
                    Spacer(Modifier.height(Space.s4))
                    ShimmerText("running…", style = type.caption)
                }
            }
            Icon(
                imageVector = AiSoulIcons.Chevron,
                contentDescription = if (expanded) "collapse" else "expand",
                tint = TextTertiary,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = chevronRotation },
            )
        }
        if (expanded) {
            SectionDivider()
            Text(text = "INPUT", style = type.overline, color = TextTertiary,
                modifier = Modifier.padding(start = Space.s16, top = Space.s12))
            Text(
                text = prettyArgs(call.argsJson),
                style = type.code,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = Space.s16, vertical = Space.s8),
            )
            if (result != null) {
                SectionDivider()
                Text(text = "OUTPUT", style = type.overline, color = TextTertiary,
                    modifier = Modifier.padding(start = Space.s16, top = Space.s12))
                Text(
                    text = result.content.take(4000).ifBlank { "(no output)" },
                    style = type.code,
                    color = if (result.isError) Negative else TextSecondary,
                    modifier = Modifier
                        .padding(horizontal = Space.s16)
                        .padding(top = Space.s8, bottom = Space.s12),
                )
            } else {
                Spacer(Modifier.height(Space.s8))
            }
        }
    }
}

@Composable
private fun SectionDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Divider),
    )
}

@Composable
private fun ActiveToolChip(label: String) {
    val type = LocalAiSoulTypography.current
    Row(
        modifier = Modifier
            .clip(RadiusChip)
            .background(Surface1)
            .border(1.dp, BorderSubtle, RadiusChip)
            .padding(horizontal = Space.s12, vertical = Space.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShimmerText(text = label, style = type.caption, modifier = Modifier.widthIn(max = 280.dp))
    }
}

private val displayJson = Json { ignoreUnknownKeys = true }
private val prettyJson = Json { prettyPrint = true }

private fun prettyArgs(argsJson: String): String =
    runCatching {
        prettyJson.encodeToString(
            kotlinx.serialization.json.JsonElement.serializer(),
            displayJson.parseToJsonElement(argsJson.ifBlank { "{}" }),
        )
    }.getOrDefault(argsJson).take(2000)

private fun displayInput(call: Part.ToolCall): String {
    val args = runCatching {
        displayJson.parseToJsonElement(call.argsJson.ifBlank { "{}" }) as? JsonObject
    }.getOrNull() ?: return call.argsJson.take(120)

    fun str(name: String) = (args[name] as? JsonPrimitive)?.contentOrNull.orEmpty()
    return when (call.name) {
        "run_command" -> str("command")
        "fetch" -> "${str("method").ifBlank { "GET" }.uppercase()} ${str("url")}"
        "read_file", "list_files", "write_file" -> str("path")
        "remember" -> str("slug")
        "propose_widget" -> (args["spec"] as? JsonObject)
            ?.let { (it["title"] as? JsonPrimitive)?.contentOrNull }.orEmpty()
        else -> call.argsJson.take(120)
    }.ifBlank { "…" }
}

/** D-028 — renders the MarkdownLite block tree with type-role styles only. */
@Composable
private fun AssistantText(text: String) {
    val type = LocalAiSoulTypography.current
    val clipboard = LocalClipboardManager.current
    val view = LocalView.current
    val blocks = remember(text) { parseMarkdownLite(text) }
    Column(verticalArrangement = Arrangement.spacedBy(Space.s12)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Paragraph -> Text(
                    text = block.text,
                    style = type.body,
                    color = TextPrimary,
                )
                is MdBlock.Heading -> if (block.level <= 2) {
                    Text(
                        text = block.text,
                        style = type.title,
                        color = TextPrimary,
                        modifier = Modifier.padding(top = Space.s8),
                    )
                } else {
                    Text(
                        text = block.text.text.uppercase(),
                        style = type.overline,
                        color = TextTertiary,
                        modifier = Modifier.padding(top = Space.s8),
                    )
                }
                is MdBlock.Rule -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Space.s4)
                        .height(1.dp)
                        .background(Divider),
                )
                is MdBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(Space.s4)) {
                    block.items.forEachIndexed { index, item ->
                        Row {
                            Text(
                                text = if (block.ordered) "${index + 1}." else "·",
                                style = type.body,
                                color = TextTertiary,
                                modifier = Modifier.width(24.dp),
                            )
                            Text(text = item, style = type.body, color = TextPrimary, modifier = Modifier.weight(1f))
                        }
                    }
                }
                is MdBlock.Quote -> Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(BorderStrong),
                    )
                    Spacer(Modifier.width(Space.s12))
                    Text(text = block.text, style = type.body, color = TextSecondary)
                }
                is MdBlock.Table -> TableBlock(block)
                is MdBlock.Code -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RadiusInput)
                        .background(Surface1)
                        .border(1.dp, BorderSubtle, RadiusInput),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = Space.s16, end = Space.s4, top = Space.s4),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = block.language ?: "code",
                            style = type.overline,
                            color = TextTertiary,
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RadiusInput)
                                .pressable {
                                    clipboard.setText(AnnotatedString(block.code))
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = AiSoulIcons.Copy,
                                contentDescription = "copy code",
                                tint = TextTertiary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Text(
                        text = block.code,
                        style = type.code,
                        color = TextSecondary,
                        modifier = Modifier
                            .padding(horizontal = Space.s16)
                            .padding(bottom = Space.s12, top = Space.s4),
                    )
                }
            }
        }
    }
}

@Composable
private fun TableBlock(table: MdBlock.Table) {
    val type = LocalAiSoulTypography.current
    val columns = maxOf(
        table.header.size,
        table.rows.maxOfOrNull { it.size } ?: 0,
    )
    if (columns == 0) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RadiusInput)
            .background(Surface1)
            .border(1.dp, BorderSubtle, RadiusInput),
    ) {
        if (table.header.isNotEmpty()) {
            Row(modifier = Modifier.padding(horizontal = Space.s12, vertical = Space.s8)) {
                repeat(columns) { column ->
                    Text(
                        text = table.header.getOrNull(column) ?: AnnotatedString(""),
                        style = type.caption,
                        color = TextSecondary,
                        modifier = Modifier.weight(1f).padding(horizontal = Space.s4),
                    )
                }
            }
            SectionDivider()
        }
        table.rows.forEachIndexed { rowIndex, row ->
            Row(modifier = Modifier.padding(horizontal = Space.s12, vertical = Space.s8)) {
                repeat(columns) { column ->
                    Text(
                        text = row.getOrNull(column) ?: AnnotatedString(""),
                        style = type.caption,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f).padding(horizontal = Space.s4),
                    )
                }
            }
            if (rowIndex < table.rows.lastIndex) SectionDivider()
        }
    }
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    isStreaming: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val type = LocalAiSoulTypography.current
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
            if (value.isEmpty()) {
                Text(text = "talk to your ai", style = type.body, color = TextTertiary)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = type.body.copy(color = TextPrimary),
                cursorBrush = SolidColor(AccentIce),
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.padding(start = Space.s8))
        val canSend = value.isNotBlank() && !isStreaming
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RadiusButton)
                .background(
                    when {
                        isStreaming -> Surface2
                        canSend -> AccentIce
                        else -> Surface1
                    },
                )
                .pressable(enabled = canSend || isStreaming) {
                    if (isStreaming) onStop() else onSend()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isStreaming) AiSoulIcons.Stop else AiSoulIcons.ArrowUp,
                contentDescription = if (isStreaming) "stop" else "send",
                tint = when {
                    isStreaming -> TextPrimary
                    canSend -> TextInverse
                    else -> TextTertiary
                },
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
