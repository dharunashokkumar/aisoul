package com.aisoul.app.providers

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * IMPLEMENTATION.md §3 — one internal schema; adapters translate to each
 * provider's wire format. These types also serialize to the chat JSONL files.
 */

@Serializable
enum class Role {
    @SerialName("user") USER,
    @SerialName("assistant") ASSISTANT,
}

@Serializable
sealed interface Part {

    @Serializable
    @SerialName("text")
    data class Text(val text: String) : Part

    @Serializable
    @SerialName("tool_call")
    data class ToolCall(val id: String, val name: String, val argsJson: String) : Part

    @Serializable
    @SerialName("tool_result")
    data class ToolResult(
        val toolCallId: String,
        val name: String,
        val content: String,
        val isError: Boolean = false,
        /** true for content fetched from the web — treated as data, not commands */
        val untrusted: Boolean = false,
    ) : Part
}

@Serializable
data class ChatMessage(
    val role: Role,
    val parts: List<Part>,
    val at: Long = System.currentTimeMillis(),
) {
    val text: String get() = parts.filterIsInstance<Part.Text>().joinToString("") { it.text }
}

data class ToolDef(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
)

data class ChatRequest(
    val model: String,
    val system: String?,
    val messages: List<ChatMessage>,
    val tools: List<ToolDef> = emptyList(),
    val maxTokens: Int = 4096,
)

sealed interface StreamEvent {
    data class TextDelta(val text: String) : StreamEvent
    data class ToolCallStart(val id: String, val name: String) : StreamEvent
    data class ToolCallArgsDelta(val id: String, val delta: String) : StreamEvent
    data class ToolCallEnd(val id: String) : StreamEvent
    data class Done(val stopReason: String?) : StreamEvent
}

/** Provider errors carry the provider's own message verbatim (surfaced in UI). */
class ProviderException(message: String) : Exception(message)
