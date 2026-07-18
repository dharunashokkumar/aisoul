package com.aisoul.app.ui.terminal

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aisoul.app.di.AppContainer
import com.aisoul.app.ui.common.AiSoulIcons
import com.aisoul.app.ui.common.GhostButton
import com.aisoul.app.ui.common.TopBar
import com.aisoul.app.ui.common.pressable
import com.aisoul.app.ui.theme.AccentIce
import com.aisoul.app.ui.theme.LocalAiSoulTypography
import com.aisoul.app.ui.theme.Negative
import com.aisoul.app.ui.theme.RadiusButton
import com.aisoul.app.ui.theme.RadiusInput
import com.aisoul.app.ui.theme.Space
import com.aisoul.app.ui.theme.Surface1
import com.aisoul.app.ui.theme.Surface2
import com.aisoul.app.ui.theme.TextInverse
import com.aisoul.app.ui.theme.TextPrimary
import com.aisoul.app.ui.theme.TextSecondary
import com.aisoul.app.ui.theme.TextTertiary
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TerminalEntry(val command: String, val output: String, val running: Boolean)

class TerminalViewModel(
    private val container: AppContainer,
    initialCommand: String?,
) : ViewModel() {

    private val _entries = MutableStateFlow<List<TerminalEntry>>(emptyList())
    val entries: StateFlow<List<TerminalEntry>> = _entries

    private var job: Job? = null

    init {
        if (!initialCommand.isNullOrBlank()) run(initialCommand)
    }

    fun run(command: String) {
        val trimmed = command.trim()
        if (trimmed.isEmpty() || _entries.value.lastOrNull()?.running == true) return
        _entries.update { it + TerminalEntry(trimmed, "", true) }
        job = viewModelScope.launch {
            // manual terminal gets a longer leash than the agent (user is present)
            val result = runCatching { container.toolbox.run(trimmed, timeoutMs = 60_000) }
            val output = result.fold(
                onSuccess = { it.output.ifBlank { "(no output)" } + "\n[exit ${it.exitCode}]" },
                onFailure = { "[cancelled]" },
            )
            _entries.update { list ->
                list.dropLast(1) + list.last().copy(output = output, running = false)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _entries.update { list ->
            if (list.lastOrNull()?.running == true) {
                list.dropLast(1) + list.last().copy(output = "[cancelled]", running = false)
            } else list
        }
    }

    fun clear() {
        cancel()
        _entries.value = emptyList()
    }

    companion object {
        fun factory(container: AppContainer, initialCommand: String?): ViewModelProvider.Factory =
            viewModelFactory { initializer { TerminalViewModel(container, initialCommand) } }
    }
}

/** SPEC §7 — the manual terminal: same toolbox the agent gets, nothing more. */
@Composable
fun TerminalScreen(
    container: AppContainer,
    initialCommand: String?,
    onBack: () -> Unit,
) {
    val viewModel: TerminalViewModel = viewModel(
        factory = TerminalViewModel.factory(container, initialCommand),
    )
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val type = LocalAiSoulTypography.current
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val running = entries.lastOrNull()?.running == true

    LaunchedEffect(entries.size, entries.lastOrNull()?.output?.length) {
        if (entries.isNotEmpty()) listState.scrollToItem(entries.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        TopBar(label = "TERMINAL", onBack = onBack) {
            GhostButton(text = "clear", onClick = { viewModel.clear() })
        }

        if (entries.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = Space.screen),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(text = "the toolbox.", style = type.headline, color = TextPrimary)
                Spacer(Modifier.height(Space.s12))
                Text(
                    text = "busybox, curl, jq, ping. sandboxed to the harness workspace — exactly what the agent gets, nothing more.",
                    style = type.body,
                    color = TextSecondary,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = Space.screen,
                    vertical = Space.s16,
                ),
                verticalArrangement = Arrangement.spacedBy(Space.s16),
            ) {
                items(entries.size) { index ->
                    val entry = entries[index]
                    Column {
                        Text(text = "$ ${entry.command}", style = type.code, color = TextPrimary)
                        Spacer(Modifier.height(Space.s4))
                        Text(
                            text = if (entry.running) "…" else entry.output,
                            style = type.code,
                            color = if (entry.output.startsWith("[cancelled]")) Negative else TextSecondary,
                        )
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
                    Text(text = "$ command", style = type.code, color = TextTertiary)
                }
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    textStyle = type.code.copy(color = TextPrimary),
                    cursorBrush = SolidColor(AccentIce),
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(Space.s8))
            val canRun = input.isNotBlank() && !running
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RadiusButton)
                    .background(
                        when {
                            running -> Surface2
                            canRun -> AccentIce
                            else -> Surface1
                        },
                    )
                    .pressable(enabled = canRun || running) {
                        if (running) viewModel.cancel() else {
                            viewModel.run(input)
                            input = ""
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (running) AiSoulIcons.Stop else AiSoulIcons.ArrowUp,
                    contentDescription = if (running) "cancel" else "run",
                    tint = when {
                        running -> TextPrimary
                        canRun -> TextInverse
                        else -> TextTertiary
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
