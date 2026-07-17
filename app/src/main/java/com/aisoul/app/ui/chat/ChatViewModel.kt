package com.aisoul.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aisoul.app.agent.AgentRuntime
import com.aisoul.app.agent.AgentTurnService
import com.aisoul.app.agent.PermissionGate
import com.aisoul.app.di.AppContainer
import com.aisoul.app.providers.ChatMessage
import com.aisoul.app.providers.Part
import com.aisoul.app.providers.ProviderType
import com.aisoul.app.providers.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    /** assistant text currently arriving; null when idle */
    val streamingText: String? = null,
    val isStreaming: Boolean = false,
    /** "run_command · ping example.com" while a tool runs */
    val activeTool: String? = null,
    val error: String? = null,
    val providerName: String = "",
    val modelName: String = "",
)

/**
 * SPEC §5 — the full loop since M2: user message → system prompt from the
 * harness → AgentRuntime (streaming, tool calls through the permission gate)
 * → every message appended to /harness/chats/<id>.jsonl.
 */
class ChatViewModel(
    private val container: AppContainer,
    initialChatId: String?,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state

    /** non-null while the gate waits on the human — drives the approval sheet */
    val approval: StateFlow<PermissionGate.PendingApproval?> = container.gate.pending

    private var chatId: String = initialChatId ?: newChatId()
    private var turnJob: Job? = null
    private val partial = StringBuilder()

    init {
        viewModelScope.launch {
            val provider = container.settings.selectedProvider.first()
            _state.update {
                it.copy(
                    providerName = provider.display,
                    modelName = container.settings.modelFor(provider).first(),
                )
            }
            if (initialChatId != null) {
                val transcript = container.harness.readTranscript(initialChatId)
                if (transcript.isNotEmpty()) _state.update { it.copy(messages = transcript) }
            }
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _state.value.isStreaming) return

        val userMessage = ChatMessage(Role.USER, listOf(Part.Text(trimmed)))
        _state.update {
            it.copy(
                messages = it.messages + userMessage,
                error = null,
                isStreaming = true,
                streamingText = "",
            )
        }
        persist(userMessage)
        partial.setLength(0)

        turnJob = viewModelScope.launch {
            val provider = container.settings.selectedProvider.first()
            val key = container.keys.getKey(provider)
            if (key == null && provider != ProviderType.OPENAI_COMPAT) {
                _state.update {
                    it.copy(isStreaming = false, streamingText = null, error = "no api key. set one in settings.")
                }
                return@launch
            }
            val model = container.settings.modelFor(provider).first()
            val baseUrl = if (provider == ProviderType.OPENAI_COMPAT) {
                container.settings.compatBaseUrl.first()
            } else null
            val client = container.providerFactory.create(provider, key ?: "", baseUrl)
            val recalled = runCatching { container.recall.recall(trimmed) }.getOrDefault(emptyList())
            val system = container.harness.systemPrompt(recalled)
            _state.update { it.copy(providerName = provider.display, modelName = model) }

            // O-6 — the turn survives app switching; stopped in finalizeTurn()
            AgentTurnService.start(container.appContext)
            container.agent
                .runTurn(client, model, system, _state.value.messages)
                .catch { e ->
                    finalizeTurn()
                    _state.update {
                        it.copy(error = e.message ?: "couldn't reach ${provider.display}. check your connection.")
                    }
                }
                .collect { event ->
                    when (event) {
                        is AgentRuntime.Event.TextDelta -> {
                            partial.append(event.text)
                            _state.update { it.copy(streamingText = partial.toString()) }
                        }
                        is AgentRuntime.Event.Message -> {
                            partial.setLength(0)
                            _state.update {
                                it.copy(messages = it.messages + event.message, streamingText = "")
                            }
                            persist(event.message)
                        }
                        is AgentRuntime.Event.ToolStarted -> _state.update {
                            it.copy(activeTool = "${event.name} · ${event.input}")
                        }
                        is AgentRuntime.Event.ToolFinished -> _state.update { it.copy(activeTool = null) }
                        is AgentRuntime.Event.TurnDone -> finalizeTurn()
                    }
                }
        }
    }

    fun stop() {
        turnJob?.cancel()
        finalizeTurn()
    }

    /**
     * D-028 — regenerate the last exchange: truncate back to the most recent
     * real user message (not tool results), rewrite the transcript file, and
     * send that message again.
     */
    fun retry() {
        if (_state.value.isStreaming) return
        val messages = _state.value.messages
        val lastUserIndex = messages.indexOfLast { message ->
            message.role == Role.USER &&
                message.parts.none { it is Part.ToolResult } &&
                message.text.isNotBlank()
        }
        if (lastUserIndex < 0) return
        val prompt = messages[lastUserIndex].text
        val kept = messages.take(lastUserIndex)
        _state.update { it.copy(messages = kept, error = null) }
        viewModelScope.launch {
            runCatching { container.harness.rewriteTranscript(chatId, kept) }
            send(prompt)
        }
    }

    fun newChat() {
        turnJob?.cancel()
        AgentTurnService.stop(container.appContext)
        partial.setLength(0)
        // the closed conversation distills right away (SPEC §3)
        if (_state.value.messages.isNotEmpty()) container.scheduleDistill(chatId, delayMinutes = 0)
        chatId = newChatId()
        _state.update {
            it.copy(messages = emptyList(), streamingText = null, isStreaming = false, activeTool = null, error = null)
        }
    }

    fun respondApproval(approved: Boolean, alwaysAllow: Boolean) {
        container.gate.respond(approved, alwaysAllow)
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    override fun onCleared() {
        AgentTurnService.stop(container.appContext)
        super.onCleared()
    }

    private fun finalizeTurn() {
        AgentTurnService.stop(container.appContext)
        if (!_state.value.isStreaming) return
        // stream cut mid-text (stop/error): keep what arrived
        val text = partial.toString()
        partial.setLength(0)
        if (text.isNotBlank()) {
            val assistantMessage = ChatMessage(Role.ASSISTANT, listOf(Part.Text(text)))
            _state.update { it.copy(messages = it.messages + assistantMessage) }
            persist(assistantMessage)
        }
        _state.update { it.copy(streamingText = null, isStreaming = false, activeTool = null) }
        if (_state.value.messages.any { it.role == Role.ASSISTANT }) {
            // idle distill: re-slides 10 minutes out on every finished turn
            container.scheduleDistill(chatId)
        }
    }

    private fun persist(message: ChatMessage) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { container.harness.appendChatMessage(chatId, message) }
        }
    }

    private fun newChatId(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    companion object {
        fun factory(container: AppContainer, chatId: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer { ChatViewModel(container, chatId) }
        }
    }
}
