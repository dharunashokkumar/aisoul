package com.aisoul.app.providers

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.BufferedSource

internal data class SseEvent(val event: String?, val data: String)

/** Minimal SSE line parser over an OkHttp body source. */
internal inline fun BufferedSource.forEachSseEvent(action: (SseEvent) -> Unit) {
    var event: String? = null
    val data = StringBuilder()
    while (true) {
        val line = readUtf8Line() ?: break
        when {
            line.isEmpty() -> {
                if (data.isNotEmpty()) action(SseEvent(event, data.toString()))
                event = null
                data.setLength(0)
            }
            line.startsWith("event:") -> event = line.removePrefix("event:").trim()
            line.startsWith("data:") -> {
                if (data.isNotEmpty()) data.append('\n')
                data.append(line.removePrefix("data:").trim())
            }
            // comments (:) and id fields are ignored
        }
    }
    if (data.isNotEmpty()) action(SseEvent(event, data.toString()))
}

internal val WireJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = false
}

internal fun JsonElement.objOrNull(key: String): JsonObject? =
    (this as? JsonObject)?.get(key) as? JsonObject

internal fun JsonElement.strOrNull(key: String): String? =
    ((this as? JsonObject)?.get(key) as? JsonPrimitive)
        // JsonNull.content is the string "null" — a wire `"content": null` must read as absent
        ?.takeUnless { it is JsonNull }
        ?.content

/** Best-effort extraction of a human-readable error message from a provider body. */
internal fun extractErrorMessage(body: String?, httpCode: Int): String {
    if (body.isNullOrBlank()) return "http $httpCode"
    return runCatching {
        val root = WireJson.parseToJsonElement(body)
        val obj = if (root is kotlinx.serialization.json.JsonArray && root.isNotEmpty()) {
            root.first().jsonObject
        } else {
            root.jsonObject
        }
        val error = obj["error"]
        when (error) {
            is JsonObject -> error.strOrNull("message") ?: error.toString()
            null -> obj.strOrNull("message") ?: body.take(300)
            else -> error.jsonPrimitive.content
        }
    }.getOrElse { body.take(300) } + " (http $httpCode)"
}
