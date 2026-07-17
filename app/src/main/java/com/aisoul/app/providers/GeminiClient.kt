package com.aisoul.app.providers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

/** Google Gemini generateContent adapter (functionDeclarations, SSE). */
class GeminiClient(
    private val http: OkHttpClient,
    private val apiKey: String,
    private val baseUrl: String = ProviderType.GEMINI.defaultBaseUrl,
) : ProviderClient {

    private companion object {
        val JSON_MEDIA = "application/json".toMediaType()
    }

    private fun request(path: String, body: String? = null): Request {
        val builder = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .header("x-goog-api-key", apiKey)
        if (body != null) builder.post(body.toRequestBody(JSON_MEDIA))
        return builder.build()
    }

    override suspend fun validateKey(): Result<Unit> =
        validationGet(http) { request("/v1beta/models") }

    override fun stream(req: ChatRequest): Flow<StreamEvent> {
        val body = buildJsonObject {
            req.system?.let { system ->
                put("system_instruction", buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", system) })
                    })
                })
            }
            put("contents", buildJsonArray {
                req.messages.forEach { message ->
                    add(buildJsonObject {
                        put("role", if (message.role == Role.USER) "user" else "model")
                        put("parts", buildJsonArray {
                            message.parts.forEach { part ->
                                add(part.toGeminiPart())
                            }
                        })
                    })
                }
            })
            if (req.tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    add(buildJsonObject {
                        put("functionDeclarations", buildJsonArray {
                            req.tools.forEach { tool ->
                                add(buildJsonObject {
                                    put("name", tool.name)
                                    put("description", tool.description)
                                    put("parameters", tool.inputSchema)
                                })
                            }
                        })
                    })
                })
            }
            put("generationConfig", buildJsonObject {
                put("maxOutputTokens", req.maxTokens)
            })
        }
        val path = "/v1beta/models/${req.model}:streamGenerateContent?alt=sse"
        return sseFlow(http, { request(path, body.toString()) }, Handler())
    }

    private fun Part.toGeminiPart() = when (this) {
        is Part.Text -> buildJsonObject { put("text", text) }
        is Part.ToolCall -> buildJsonObject {
            put("functionCall", buildJsonObject {
                put("name", name)
                put("args", runCatching { Json.parseToJsonElement(argsJson).jsonObject }
                    .getOrElse { buildJsonObject {} })
            })
        }
        is Part.ToolResult -> buildJsonObject {
            put("functionResponse", buildJsonObject {
                put("name", name)
                put("response", buildJsonObject { put("output", content) })
            })
        }
    }

    private class Handler : SseHandler {
        private var finishReason: String? = null

        override suspend fun onEvent(collector: FlowCollector<StreamEvent>, event: SseEvent) {
            val root = runCatching { WireJson.parseToJsonElement(event.data).jsonObject }
                .getOrNull() ?: return
            root.objOrNull("error")?.let {
                throw ProviderException(it.strOrNull("message") ?: "provider error")
            }
            val candidate = (root["candidates"] as? JsonArray)?.firstOrNull()?.jsonObject ?: return
            candidate.strOrNull("finishReason")?.let { finishReason = it }
            val parts = (candidate.objOrNull("content")?.get("parts") as? JsonArray) ?: return
            parts.forEach { element ->
                val part = element.jsonObject
                part.strOrNull("text")?.let { collector.emit(StreamEvent.TextDelta(it)) }
                part.objOrNull("functionCall")?.let { call ->
                    // gemini delivers complete calls, not deltas
                    val id = "gemini_${UUID.randomUUID().toString().take(8)}"
                    val name = call.strOrNull("name") ?: return@let
                    val args = call["args"]?.toString() ?: "{}"
                    collector.emit(StreamEvent.ToolCallStart(id, name))
                    collector.emit(StreamEvent.ToolCallArgsDelta(id, args))
                    collector.emit(StreamEvent.ToolCallEnd(id))
                }
            }
        }

        override suspend fun onStreamEnd(collector: FlowCollector<StreamEvent>) {
            collector.emit(StreamEvent.Done(finishReason))
        }
    }
}
