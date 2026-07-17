package com.aisoul.app.agent

import com.aisoul.app.providers.ChatMessage
import com.aisoul.app.providers.ChatRequest
import com.aisoul.app.providers.Part
import com.aisoul.app.providers.ProviderClient
import com.aisoul.app.providers.Role
import com.aisoul.app.providers.StreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

/**
 * SPEC §5 — the loop: model responds, possibly with tool calls → permission
 * gate → tools execute → results return to the model → repeat until final
 * text. Max 20 iterations per turn; hard cancel always available (cancelling
 * the collecting coroutine kills streams and processes).
 */
class AgentRuntime(
    private val registry: ToolRegistry,
    private val gate: PermissionGate,
) {

    sealed interface Event {
        data class TextDelta(val text: String) : Event

        /** a full message to append to the transcript (caller persists it) */
        data class Message(val message: ChatMessage) : Event
        data class ToolStarted(val id: String, val name: String, val input: String) : Event
        data class ToolFinished(val id: String, val name: String, val isError: Boolean) : Event
        data class TurnDone(val stopReason: String?) : Event
    }

    fun runTurn(
        client: ProviderClient,
        model: String,
        system: String,
        history: List<ChatMessage>,
        maxTokens: Int = 4096,
    ): Flow<Event> = flow {
        val conversation = history.toMutableList()

        repeat(MAX_ITERATIONS) {
            val text = StringBuilder()
            val calls = LinkedHashMap<String, PendingCall>()
            var stopReason: String? = null

            client.stream(
                ChatRequest(
                    model = model,
                    system = system,
                    messages = conversation,
                    tools = registry.defs(),
                    maxTokens = maxTokens,
                ),
            ).collect { event ->
                when (event) {
                    is StreamEvent.TextDelta -> {
                        text.append(event.text)
                        emit(Event.TextDelta(event.text))
                    }
                    is StreamEvent.ToolCallStart -> calls[event.id] = PendingCall(event.id, event.name)
                    is StreamEvent.ToolCallArgsDelta -> calls[event.id]?.args?.append(event.delta)
                    is StreamEvent.ToolCallEnd -> Unit
                    is StreamEvent.Done -> stopReason = event.stopReason
                }
            }

            val parts = buildList {
                if (text.isNotBlank()) add(Part.Text(text.toString()))
                calls.values.forEach { call ->
                    add(Part.ToolCall(call.id, call.name, call.args.toString().ifBlank { "{}" }))
                }
            }
            if (parts.isEmpty()) {
                emit(Event.TurnDone(stopReason))
                return@flow
            }

            val assistant = ChatMessage(Role.ASSISTANT, parts)
            conversation += assistant
            emit(Event.Message(assistant))

            if (calls.isEmpty()) {
                emit(Event.TurnDone(stopReason))
                return@flow
            }

            val results = mutableListOf<Part>()
            for (call in calls.values) {
                val args = parseArgs(call.args.toString())
                emit(Event.ToolStarted(call.id, call.name, displayInput(call.name, args)))
                val outcome = executeCall(call.name, args)
                results += Part.ToolResult(
                    toolCallId = call.id,
                    name = call.name,
                    content = outcome.content,
                    isError = outcome.isError,
                    untrusted = outcome.untrusted,
                )
                emit(Event.ToolFinished(call.id, call.name, outcome.isError))
            }
            val resultsMessage = ChatMessage(Role.USER, results)
            conversation += resultsMessage
            emit(Event.Message(resultsMessage))
        }

        // iteration budget exhausted — tell the model's side of the story honestly
        emit(Event.TurnDone("max_iterations"))
    }

    private suspend fun executeCall(name: String, args: JsonObject): ToolOutcome {
        val tool = registry.get(name)
            ?: return ToolOutcome("unknown tool: $name", isError = true)
        val action = runCatching { tool.gateAction(args) }.getOrNull()
        if (action != null && !gate.check(action)) {
            return ToolOutcome("the user declined this action", isError = true)
        }
        return runCatching {
            withTimeoutOrNull(TOOL_TIMEOUT_MS) { tool.execute(args) }
                ?: ToolOutcome("tool timed out", isError = true)
        }.getOrElse { e ->
            if (e is kotlinx.coroutines.CancellationException) throw e
            ToolOutcome("tool failed: ${e.message ?: e.javaClass.simpleName}", isError = true)
        }
    }

    private fun parseArgs(raw: String): JsonObject =
        runCatching { lenientJson.parseToJsonElement(raw.ifBlank { "{}" }) as? JsonObject }
            .getOrNull() ?: buildJsonObject {}

    /** the exact input, humanized per tool — shown on the inline tool card */
    private fun displayInput(name: String, args: JsonObject): String = when (name) {
        "run_command" -> args.string("command")
        "fetch" -> "${args.string("method").ifBlank { "GET" }.uppercase()} ${args.string("url")}"
        "read_file", "list_files", "write_file" -> args.string("path")
        "remember" -> args.string("slug")
        "propose_widget" -> (args["spec"] as? JsonObject)?.let { spec -> spec.string("title") }.orEmpty()
        else -> args.toString().take(120)
    }.ifBlank { "…" }

    private fun JsonObject.string(name: String): String =
        (this[name] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private class PendingCall(val id: String, val name: String, val args: StringBuilder = StringBuilder())

    private companion object {
        const val MAX_ITERATIONS = 20
        const val TOOL_TIMEOUT_MS = 90_000L
        val lenientJson = Json { ignoreUnknownKeys = true }
    }
}
