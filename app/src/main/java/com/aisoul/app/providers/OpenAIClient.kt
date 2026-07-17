package com.aisoul.app.providers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * OpenAI Chat Completions adapter. Chat Completions (not Responses) is
 * deliberate: it is the de-facto compat standard, so this same class serves
 * OpenRouter / Ollama / LM Studio via a custom base URL (D-007).
 */
class OpenAIClient(
    private val http: OkHttpClient,
    private val apiKey: String,
    private val baseUrl: String = ProviderType.OPENAI.defaultBaseUrl,
) : ProviderClient {

    private companion object {
        val JSON_MEDIA = "application/json".toMediaType()
    }

    private fun request(path: String, body: String? = null): Request {
        val builder = Request.Builder().url(baseUrl.trimEnd('/') + path)
        // LAN compat servers (ollama, lm studio) often need no key
        if (apiKey.isNotBlank()) builder.header("Authorization", "Bearer $apiKey")
        if (body != null) builder.post(body.toRequestBody(JSON_MEDIA))
        return builder.build()
    }

    override suspend fun validateKey(): Result<Unit> =
        validationGet(http) { request("/models") }

    override fun stream(req: ChatRequest): Flow<StreamEvent> {
        val body = buildJsonObject {
            put("model", req.model)
            put("stream", true)
            // no token cap: max_tokens vs max_completion_tokens drifts by model;
            // provider defaults are fine and the field split would break compat
            put("messages", buildJsonArray {
                req.system?.let { system ->
                    add(buildJsonObject {
                        put("role", "system")
                        put("content", system)
                    })
                }
                req.messages.forEach { message -> appendOpenAIMessages(message) }
            })
            if (req.tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    req.tools.forEach { tool ->
                        add(buildJsonObject {
                            put("type", "function")
                            put("function", buildJsonObject {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("parameters", tool.inputSchema)
                            })
                        })
                    }
                })
            }
        }
        return sseFlow(http, { request("/chat/completions", body.toString()) }, Handler())
    }

    /** One internal message can expand to several wire messages (tool results). */
    private fun kotlinx.serialization.json.JsonArrayBuilder.appendOpenAIMessages(message: ChatMessage) {
        val text = message.parts.filterIsInstance<Part.Text>().joinToString("") { it.text }
        val toolCalls = message.parts.filterIsInstance<Part.ToolCall>()
        val toolResults = message.parts.filterIsInstance<Part.ToolResult>()

        if (message.role == Role.ASSISTANT) {
            add(buildJsonObject {
                put("role", "assistant")
                if (text.isNotEmpty()) put("content", text) else put("content", JsonNull)
                if (toolCalls.isNotEmpty()) {
                    put("tool_calls", buildJsonArray {
                        toolCalls.forEach { call ->
                            add(buildJsonObject {
                                put("id", call.id)
                                put("type", "function")
                                put("function", buildJsonObject {
                                    put("name", call.name)
                                    put("arguments", call.argsJson)
                                })
                            })
                        }
                    })
                }
            })
        } else {
            toolResults.forEach { result ->
                add(buildJsonObject {
                    put("role", "tool")
                    put("tool_call_id", result.toolCallId)
                    put("content", result.content)
                })
            }
            if (text.isNotEmpty() || toolResults.isEmpty()) {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", text)
                })
            }
        }
    }

    private class Handler : SseHandler {
        // tool_calls arrive keyed by index; ids only on the first chunk
        private val toolIdByIndex = HashMap<Int, String>()
        private var finishReason: String? = null
        private var done = false

        override suspend fun onEvent(collector: FlowCollector<StreamEvent>, event: SseEvent) {
            if (event.data == "[DONE]") {
                done = true
                toolIdByIndex.values.forEach { collector.emit(StreamEvent.ToolCallEnd(it)) }
                collector.emit(StreamEvent.Done(finishReason))
                return
            }
            val root = runCatching { WireJson.parseToJsonElement(event.data).jsonObject }
                .getOrNull() ?: return
            root.objOrNull("error")?.let {
                throw ProviderException(it.strOrNull("message") ?: "provider error")
            }
            val choice = (root["choices"] as? JsonArray)?.firstOrNull()?.jsonObject ?: return
            choice.strOrNull("finish_reason")?.let { finishReason = it }
            val delta = choice.objOrNull("delta") ?: return
            delta.strOrNull("content")?.let {
                if (it.isNotEmpty()) collector.emit(StreamEvent.TextDelta(it))
            }
            (delta["tool_calls"] as? JsonArray)?.forEach { element ->
                val call = element.jsonObject
                val index = call.strOrNull("index")?.toIntOrNull() ?: 0
                val function = call.objOrNull("function")
                val id = call.strOrNull("id")
                if (id != null && !toolIdByIndex.containsKey(index)) {
                    toolIdByIndex[index] = id
                    collector.emit(
                        StreamEvent.ToolCallStart(id, function?.strOrNull("name") ?: ""),
                    )
                }
                val args = function?.strOrNull("arguments")
                val knownId = toolIdByIndex[index]
                if (!args.isNullOrEmpty() && knownId != null) {
                    collector.emit(StreamEvent.ToolCallArgsDelta(knownId, args))
                }
            }
        }

        override suspend fun onStreamEnd(collector: FlowCollector<StreamEvent>) {
            if (!done) collector.emit(StreamEvent.Done(finishReason))
        }
    }
}
