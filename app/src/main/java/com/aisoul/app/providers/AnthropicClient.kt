package com.aisoul.app.providers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Anthropic Messages API adapter (tool_use / tool_result blocks, SSE). */
class AnthropicClient(
    private val http: OkHttpClient,
    private val apiKey: String,
    private val baseUrl: String = ProviderType.ANTHROPIC.defaultBaseUrl,
) : ProviderClient {

    private companion object {
        const val VERSION = "2023-06-01"
        val JSON_MEDIA = "application/json".toMediaType()
    }

    private fun request(path: String, body: String? = null): Request {
        val builder = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .header("x-api-key", apiKey)
            .header("anthropic-version", VERSION)
        if (body != null) builder.post(body.toRequestBody(JSON_MEDIA))
        return builder.build()
    }

    override suspend fun validateKey(): Result<Unit> =
        validationGet(http) { request("/v1/models") }

    override fun stream(req: ChatRequest): Flow<StreamEvent> {
        val body = buildJsonObject {
            put("model", req.model)
            put("max_tokens", req.maxTokens)
            put("stream", true)
            req.system?.let { put("system", it) }
            put("messages", buildJsonArray {
                req.messages.forEach { message ->
                    add(buildJsonObject {
                        put("role", if (message.role == Role.USER) "user" else "assistant")
                        put("content", buildJsonArray {
                            message.parts.forEach { part ->
                                add(part.toAnthropicBlock())
                            }
                        })
                    })
                }
            })
            if (req.tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    req.tools.forEach { tool ->
                        add(buildJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("input_schema", tool.inputSchema)
                        })
                    }
                })
            }
        }
        return sseFlow(http, { request("/v1/messages", body.toString()) }, Handler())
    }

    private fun Part.toAnthropicBlock(): JsonObject = when (this) {
        is Part.Text -> buildJsonObject {
            put("type", "text")
            put("text", text)
        }
        is Part.ToolCall -> buildJsonObject {
            put("type", "tool_use")
            put("id", id)
            put("name", name)
            put("input", runCatching { Json.parseToJsonElement(argsJson).jsonObject }
                .getOrElse { buildJsonObject {} })
        }
        is Part.ToolResult -> buildJsonObject {
            put("type", "tool_result")
            put("tool_use_id", toolCallId)
            put("content", content)
            if (isError) put("is_error", true)
        }
    }

    private class Handler : SseHandler {
        // content block index -> tool_use id (text blocks map to null)
        private val toolIdByIndex = HashMap<Int, String>()
        private var stopReason: String? = null
        private var done = false

        override suspend fun onEvent(collector: FlowCollector<StreamEvent>, event: SseEvent) {
            val root = runCatching { WireJson.parseToJsonElement(event.data).jsonObject }
                .getOrNull() ?: return
            when (event.event ?: root.strOrNull("type")) {
                "content_block_start" -> {
                    val index = root.strOrNull("index")?.toIntOrNull() ?: return
                    val block = root.objOrNull("content_block") ?: return
                    if (block.strOrNull("type") == "tool_use") {
                        val id = block.strOrNull("id") ?: return
                        val name = block.strOrNull("name") ?: return
                        toolIdByIndex[index] = id
                        collector.emit(StreamEvent.ToolCallStart(id, name))
                    }
                }
                "content_block_delta" -> {
                    val delta = root.objOrNull("delta") ?: return
                    when (delta.strOrNull("type")) {
                        "text_delta" -> delta.strOrNull("text")
                            ?.let { collector.emit(StreamEvent.TextDelta(it)) }
                        "input_json_delta" -> {
                            val index = root.strOrNull("index")?.toIntOrNull() ?: return
                            val id = toolIdByIndex[index] ?: return
                            delta.strOrNull("partial_json")
                                ?.let { collector.emit(StreamEvent.ToolCallArgsDelta(id, it)) }
                        }
                    }
                }
                "content_block_stop" -> {
                    val index = root.strOrNull("index")?.toIntOrNull() ?: return
                    toolIdByIndex.remove(index)?.let { collector.emit(StreamEvent.ToolCallEnd(it)) }
                }
                "message_delta" -> {
                    stopReason = root.objOrNull("delta")?.strOrNull("stop_reason") ?: stopReason
                }
                "message_stop" -> {
                    done = true
                    collector.emit(StreamEvent.Done(stopReason))
                }
                "error" -> {
                    val message = root.objOrNull("error")?.strOrNull("message") ?: "provider error"
                    throw ProviderException(message)
                }
            }
        }

        override suspend fun onStreamEnd(collector: FlowCollector<StreamEvent>) {
            if (!done) collector.emit(StreamEvent.Done(stopReason))
        }
    }
}
