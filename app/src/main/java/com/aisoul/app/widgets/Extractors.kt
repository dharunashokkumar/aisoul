package com.aisoul.app.widgets

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * IMPLEMENTATION §6 — minimal extractors, implemented by hand:
 * `$.a.b[0]` JSONPath subset, `regex:` with one capture group, `lines:N[-M]`.
 * Extraction failure returns null; the renderer shows "—", never garbage.
 */
object Extractors {

    private val json = Json { ignoreUnknownKeys = true }
    // Android's ICU regex rejects unescaped closing brackets that the desktop
    // JVM tolerates — keep them escaped or the class fails to load.
    private val JSONPATH = Regex("^\\$(\\.[A-Za-z0-9_-]+(\\[\\d+\\])?)+$")
    private val LINES = Regex("^lines:(\\d+)(?:-(\\d+))?$")
    private const val MAX_PATTERN = 200
    private const val MAX_INPUT = 64 * 1024

    /** null = fine; else a human-readable problem (used by the validator). */
    fun validate(extract: String): String? = when {
        extract.startsWith("$.") ->
            if (JSONPATH.matches(extract)) null else "jsonpath supports only \$.a.b[0] forms"
        extract.startsWith("regex:") -> {
            val pattern = extract.removePrefix("regex:")
            when {
                pattern.length > MAX_PATTERN -> "regex pattern too long"
                runCatching { Regex(pattern) }.isFailure -> "regex does not compile"
                runCatching { Regex(pattern) }.getOrNull()?.toPattern()?.matcher("")?.groupCount() == 0 ->
                    "regex needs one capture group"
                else -> null
            }
        }
        extract.startsWith("lines:") ->
            if (LINES.matches(extract)) null else "lines syntax is lines:N or lines:N-M"
        else -> "unknown extractor (use \$.path, regex:, or lines:)"
    }

    fun apply(extract: String?, raw: String): String? {
        if (extract == null) return raw.trim()
        val input = raw.take(MAX_INPUT)
        return when {
            extract.startsWith("$.") -> jsonPath(extract, input)
            extract.startsWith("regex:") -> regex(extract.removePrefix("regex:"), input)
            extract.startsWith("lines:") -> lines(extract, input)
            else -> null
        }
    }

    private fun jsonPath(path: String, input: String): String? {
        var node: JsonElement = runCatching { json.parseToJsonElement(input) }.getOrNull() ?: return null
        // tokens: ".name" or ".name[index]"
        val segments = Regex("\\.([A-Za-z0-9_-]+)(?:\\[(\\d+)\\])?").findAll(path)
        for (segment in segments) {
            val key = segment.groupValues[1]
            node = (node as? JsonObject)?.get(key) ?: return null
            val index = segment.groupValues[2]
            if (index.isNotEmpty()) {
                node = (node as? JsonArray)?.getOrNull(index.toInt()) ?: return null
            }
        }
        return when (node) {
            is JsonNull -> null
            is JsonPrimitive -> node.content
            else -> node.toString()
        }
    }

    private fun regex(pattern: String, input: String): String? {
        if (pattern.length > MAX_PATTERN) return null
        val compiled = runCatching { Regex(pattern) }.getOrNull() ?: return null
        return compiled.find(input)?.groupValues?.getOrNull(1)
    }

    private fun lines(extract: String, input: String): String? {
        val match = LINES.find(extract) ?: return null
        val from = match.groupValues[1].toIntOrNull() ?: return null
        val to = match.groupValues[2].toIntOrNull() ?: from
        if (from < 1 || to < from) return null
        val all = input.lines()
        if (from > all.size) return null
        return all.subList(from - 1, minOf(to, all.size)).joinToString("\n").trim()
    }
}
