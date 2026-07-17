package com.aisoul.app.distill

import com.aisoul.app.harness.MemoryStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * IMPLEMENTATION §7 — strict JSON output contract for the distill pass.
 * Malformed output → empty result; the harness is never corrupted by a bad
 * model reply. Closeout fields (D-020): log, activity, title — optional,
 * dropped independently when malformed. `cursor` is still parsed if a model
 * emits it but is ignored at apply time (D-034 — no forced continuity).
 */
@Serializable
data class MemoryOp(
    val op: String,
    val slug: String,
    val name: String = "",
    val description: String = "",
    val type: String = "user",
    val content: String = "",
)

@Serializable
private data class DistillEnvelope(
    val operations: List<MemoryOp> = emptyList(),
    val log: String? = null,
    val cursor: String? = null,
    val activity: String? = null,
    val title: String? = null,
    val summary: String? = null,
)

data class DistillResult(
    val operations: List<MemoryOp> = emptyList(),
    val log: String? = null,
    val cursor: String? = null,
    val activity: String? = null,
    val title: String? = null,
    /** META pass only: full replacement for SUMMARY.md */
    val summary: String? = null,
)

object DistillParser {

    private val json = Json { ignoreUnknownKeys = true }

    private val VALID_OPS = setOf("create", "update", "delete")

    fun parse(raw: String): List<MemoryOp> = parseResult(raw).operations

    fun parseResult(raw: String): DistillResult {
        val stripped = stripFences(raw)
        val envelope = runCatching { json.decodeFromString(DistillEnvelope.serializer(), stripped) }
            .getOrElse { return DistillResult() }
        val operations = envelope.operations.filter { op ->
            op.op in VALID_OPS &&
                MemoryStore.SLUG_PATTERN.matches(op.slug) &&
                when (op.op) {
                    "create" -> op.name.isNotBlank() && op.description.isNotBlank() && op.content.isNotBlank()
                    "update" -> op.name.isNotBlank() && op.description.isNotBlank()
                    else -> true
                }
        }
        return DistillResult(
            operations = operations,
            log = envelope.log?.takeIf { it.isNotBlank() },
            cursor = envelope.cursor?.takeIf { it.isNotBlank() },
            activity = envelope.activity?.takeIf { it.isNotBlank() }?.take(48),
            title = envelope.title?.takeIf { it.isNotBlank() }?.take(64),
            summary = envelope.summary?.takeIf { it.isNotBlank() },
        )
    }

    /** Models love to wrap JSON in markdown fences; tolerate it. */
    fun stripFences(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return extractObject(trimmed)
        val inner = trimmed
            .removePrefix("```json").removePrefix("```JSON").removePrefix("```")
            .removeSuffix("```")
            .trim()
        return extractObject(inner)
    }

    /** Tolerate prose around the object: take first '{' to last '}'. */
    private fun extractObject(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start in 0 until end) text.substring(start, end + 1) else text
    }
}
