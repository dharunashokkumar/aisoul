package com.aisoul.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aisoul.app.di.AppContainer
import com.aisoul.app.providers.ChatMessage
import com.aisoul.app.providers.ChatRequest
import com.aisoul.app.providers.Part
import com.aisoul.app.providers.ProviderType
import com.aisoul.app.providers.Role
import com.aisoul.app.providers.StreamEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class OnboardingPhase { INTERVIEW, DRAFTING, REVEAL }

data class OnboardingUiState(
    val phase: OnboardingPhase = OnboardingPhase.INTERVIEW,
    /** interview messages, bootstrap hidden */
    val messages: List<ChatMessage> = emptyList(),
    val streamingText: String? = null,
    val isStreaming: Boolean = false,
    val soulDraft: String = "",
    val userDraft: String = "",
    val error: String? = null,
    val done: Boolean = false,
    /** true once enough answers exist to offer wrapping up */
    val canWrapUp: Boolean = false,
)

/**
 * SPEC §4 — the soul interview. The AI itself (with the fresh key) asks
 * 5–7 questions, then drafts SOUL.md and USER.md; the user reviews both
 * before anything is written. Skippable at every point.
 */
class OnboardingViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state

    private var turnJob: Job? = null
    private val partial = StringBuilder()

    /** hidden opener that puts the model in interviewer position */
    private val bootstrap = ChatMessage(
        Role.USER,
        listOf(Part.Text("(they just opened the app for the first time. begin.)")),
    )

    private var transcript: List<ChatMessage> = listOf(bootstrap)

    init {
        runTurn()
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _state.value.isStreaming) return
        val message = ChatMessage(Role.USER, listOf(Part.Text(trimmed)))
        transcript = transcript + message
        _state.update {
            it.copy(
                messages = it.messages + message,
                canWrapUp = it.messages.count { m -> m.role == Role.USER } >= 2,
            )
        }
        runTurn()
    }

    fun skip() {
        turnJob?.cancel()
        viewModelScope.launch {
            container.settings.setOnboarded()
            _state.update { it.copy(done = true) }
        }
    }

    fun wrapUp() {
        if (_state.value.phase != OnboardingPhase.INTERVIEW) return
        turnJob?.cancel()
        startDrafting()
    }

    fun redoInterview() {
        turnJob?.cancel()
        partial.setLength(0)
        transcript = listOf(bootstrap)
        _state.value = OnboardingUiState()
        runTurn()
    }

    fun updateSoulDraft(text: String) = _state.update { it.copy(soulDraft = text) }
    fun updateUserDraft(text: String) = _state.update { it.copy(userDraft = text) }

    fun keepFiles() {
        val s = _state.value
        if (s.soulDraft.isBlank() || s.userDraft.isBlank()) return
        viewModelScope.launch {
            container.harness.soulWritten(s.soulDraft, s.userDraft)
            container.settings.setOnboarded()
            _state.update { it.copy(done = true) }
        }
    }

    fun retryDrafting() = startDrafting()

    private fun runTurn() {
        partial.setLength(0)
        _state.update { it.copy(isStreaming = true, streamingText = "", error = null) }
        turnJob = viewModelScope.launch {
            val client = client() ?: return@launch
            client.stream(
                ChatRequest(
                    model = model(),
                    system = INTERVIEW_SYSTEM,
                    messages = transcript,
                    maxTokens = 1024,
                ),
            )
                .catch { e ->
                    _state.update {
                        it.copy(
                            isStreaming = false,
                            streamingText = null,
                            error = e.message ?: "couldn't reach your provider.",
                        )
                    }
                }
                .collect { event ->
                    when (event) {
                        is StreamEvent.TextDelta -> {
                            partial.append(event.text)
                            _state.update { it.copy(streamingText = partial.toString()) }
                        }
                        is StreamEvent.Done -> finishAssistantTurn()
                        else -> Unit
                    }
                }
        }
    }

    private fun finishAssistantTurn() {
        val text = partial.toString().trim()
        partial.setLength(0)
        if (text.isEmpty()) {
            _state.update { it.copy(isStreaming = false, streamingText = null) }
            return
        }
        val message = ChatMessage(Role.ASSISTANT, listOf(Part.Text(text)))
        transcript = transcript + message
        _state.update {
            it.copy(messages = it.messages + message, streamingText = null, isStreaming = false)
        }
        if (text.lowercase().contains(COMPLETION_MARKER)) startDrafting()
    }

    private fun startDrafting() {
        _state.update { it.copy(phase = OnboardingPhase.DRAFTING, error = null) }
        viewModelScope.launch {
            val client = client() ?: return@launch
            val rendered = transcript.drop(1).joinToString("\n") { message ->
                val who = if (message.role == Role.USER) "them" else "interviewer"
                "$who: ${message.text}"
            }
            val raw = StringBuilder()
            runCatching {
                client.stream(
                    ChatRequest(
                        model = model(),
                        system = DRAFT_SYSTEM,
                        messages = listOf(ChatMessage(Role.USER, listOf(Part.Text(rendered)))),
                        maxTokens = 2048,
                    ),
                ).collect { event ->
                    if (event is StreamEvent.TextDelta) raw.append(event.text)
                }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "drafting failed.") }
                return@launch
            }

            val draft = parseDraft(raw.toString())
            if (draft == null) {
                _state.update { it.copy(error = "couldn't draft your files. try again or skip.") }
            } else {
                _state.update {
                    it.copy(
                        phase = OnboardingPhase.REVEAL,
                        soulDraft = draft.soul.trim(),
                        userDraft = draft.user.trim(),
                        error = null,
                    )
                }
            }
        }
    }

    private suspend fun client() = run {
        val provider = container.settings.selectedProvider.first()
        val key = container.keys.getKey(provider)
        if (key == null && provider != ProviderType.OPENAI_COMPAT) {
            _state.update { it.copy(isStreaming = false, error = "no api key. go back and set one.") }
            null
        } else {
            val baseUrl = if (provider == ProviderType.OPENAI_COMPAT) {
                container.settings.compatBaseUrl.first()
            } else null
            container.providerFactory.create(provider, key ?: "", baseUrl)
        }
    }

    private suspend fun model(): String =
        container.settings.modelFor(container.settings.selectedProvider.first()).first()

    @Serializable
    private data class SoulDraft(val soul: String, val user: String)

    private fun parseDraft(raw: String): SoulDraft? {
        val stripped = com.aisoul.app.distill.DistillParser.stripFences(raw)
        return runCatching { draftJson.decodeFromString(SoulDraft.serializer(), stripped) }
            .getOrNull()
            ?.takeIf { it.soul.isNotBlank() && it.user.isNotBlank() }
    }

    companion object {
        private val draftJson = Json { ignoreUnknownKeys = true }

        const val COMPLETION_MARKER = "that's all i need"

        private val INTERVIEW_SYSTEM = """
            you are aisoul, meeting your person for the first time. you live on their phone as plain files they can read and edit. conduct a short, warm first-meeting interview — one question per message, 5 to 7 questions total.

            cover, in your own words, one at a time:
            - what to call them
            - what they do and where their days go
            - what they want your help with most
            - how you should talk to them (terse or chatty, direct or gentle)
            - anything you should never do or bring up
            and if the conversation invites it: what a good day looks like, or one thing they're trying to change.

            rules:
            - one question at a time. each message under 40 words.
            - acknowledge answers in a few words; never parrot them back.
            - lowercase, no lists, no emoji, no exclamation marks.
            - if they answer curtly twice in a row, wrap up early.
            - when you have enough, say exactly: "$COMPLETION_MARKER. let me write down who i am to you." and nothing after it.
        """.trimIndent()

        private val DRAFT_SYSTEM = """
            from this first-meeting interview transcript, write the two files that will define you.

            output STRICT json, nothing else: {"soul":"...","user":"..."}

            "soul" = SOUL.md — who you (the ai) are for this person: tone, personality, boundaries, how to behave, what never to do. write as instructions to yourself. start with "# soul".
            "user" = USER.md — who they are: what to call them, what they do, what they want help with, preferences. start with "# user".

            rules: markdown with lowercase headers. each file under 200 words. no emoji. honest and specific — only what the interview actually said, no invented details.
        """.trimIndent()
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { OnboardingViewModel(container) }
        }
    }
}
