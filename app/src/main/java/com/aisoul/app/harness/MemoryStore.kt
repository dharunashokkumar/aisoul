package com.aisoul.app.harness

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * SPEC §3 — one markdown file per durable fact under /harness/memories, with
 * frontmatter so recall can rank by description without loading bodies.
 * MEMORY.md is a derived index; the files are authoritative.
 */
data class Memory(
    val slug: String,
    val name: String,
    val description: String,
    val type: String,
    val body: String,
    val modifiedAt: Long,
)

@Serializable
data class PendingDelete(val slug: String, val reason: String = "")

class MemoryStore(private val root: File, private val json: Json) {

    /** fires after any write — slides the backup debounce (IMPLEMENTATION §8) */
    var onMutation: (() -> Unit)? = null

    private val memoriesDir get() = File(root, "memories")
    private val indexFile get() = File(root, "MEMORY.md")
    private val pendingFile get() = File(root, ".pending-deletes.json")

    companion object {
        val SLUG_PATTERN = Regex("^[a-z0-9][a-z0-9-]{0,63}$")
        val MEMORY_TYPES = setOf("user", "preference", "project", "reference")

        private val STOPWORDS = setOf(
            "the", "and", "for", "you", "your", "with", "this", "that", "have",
            "are", "was", "what", "how", "can", "about", "from", "not", "but",
        )

        fun tokenize(text: String): Set<String> =
            text.lowercase()
                .split(Regex("[^a-z0-9]+"))
                .filter { it.length >= 3 && it !in STOPWORDS }
                .toSet()
    }

    suspend fun list(): List<Memory> = withContext(Dispatchers.IO) {
        memoriesDir.listFiles { file -> file.extension == "md" }
            .orEmpty()
            .mapNotNull { file ->
                val parsed = Frontmatter.parse(runCatching { file.readText() }.getOrNull() ?: return@mapNotNull null)
                Memory(
                    slug = file.nameWithoutExtension,
                    name = parsed.fields["name"] ?: file.nameWithoutExtension,
                    description = parsed.fields["description"] ?: "",
                    type = parsed.fields["type"] ?: "user",
                    body = parsed.body,
                    modifiedAt = file.lastModified(),
                )
            }
            .sortedByDescending { it.modifiedAt }
    }

    suspend fun upsert(slug: String, name: String, description: String, type: String, body: String?) =
        withContext(Dispatchers.IO) {
            if (!SLUG_PATTERN.matches(slug)) return@withContext
            memoriesDir.mkdirs()
            val file = File(memoriesDir, "$slug.md")
            val existingBody = if (file.exists()) Frontmatter.parse(file.readText()).body else ""
            val content = Frontmatter.serialize(
                fields = mapOf(
                    "name" to name,
                    "description" to description,
                    "type" to (type.takeIf { it in MEMORY_TYPES } ?: "user"),
                ),
                body = body?.takeIf { it.isNotBlank() } ?: existingBody,
            )
            writeAtomic(file, content)
            rebuildIndex()
        }

    suspend fun delete(slug: String) = withContext(Dispatchers.IO) {
        File(memoriesDir, "$slug.md").delete()
        removePending(slug)
        rebuildIndex()
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        memoriesDir.listFiles()?.forEach { it.delete() }
        pendingFile.delete()
        rebuildIndex()
    }

    /** Recall v1 (IMPLEMENTATION §7): keyword overlap on name+description, top N bodies. */
    suspend fun recall(query: String, limit: Int = 5): List<Memory> {
        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) return emptyList()
        return list()
            .map { memory ->
                val memoryTokens = tokenize("${memory.name} ${memory.description}")
                memory to queryTokens.count { it in memoryTokens }
            }
            .filter { (_, score) -> score > 0 }
            .sortedWith(compareByDescending<Pair<Memory, Int>> { it.second }.thenByDescending { it.first.modifiedAt })
            .take(limit)
            .map { it.first }
    }

    suspend fun rebuildIndex() = withContext(Dispatchers.IO) {
        val lines = list().sortedBy { it.slug }.joinToString("\n") { memory ->
            "- [${memory.name}](memories/${memory.slug}.md) — ${memory.description}"
        }
        writeAtomic(indexFile, "# memory index\n\n$lines\n")
    }

    // ---- pending deletes (SPEC §3: delete ops queue for user approval) ----

    suspend fun pendingDeletes(): List<PendingDelete> = withContext(Dispatchers.IO) {
        if (!pendingFile.exists()) return@withContext emptyList()
        runCatching {
            json.decodeFromString(ListSerializer(PendingDelete.serializer()), pendingFile.readText())
        }.getOrElse { emptyList() }
    }

    suspend fun queueDelete(slug: String, reason: String) = withContext(Dispatchers.IO) {
        if (!File(memoriesDir, "$slug.md").exists()) return@withContext
        val pending = pendingDeletes().filterNot { it.slug == slug } + PendingDelete(slug, reason)
        writeAtomic(pendingFile, json.encodeToString(ListSerializer(PendingDelete.serializer()), pending))
    }

    suspend fun removePending(slug: String) = withContext(Dispatchers.IO) {
        val pending = pendingDeletes().filterNot { it.slug == slug }
        if (pending.isEmpty()) pendingFile.delete()
        else writeAtomic(pendingFile, json.encodeToString(ListSerializer(PendingDelete.serializer()), pending))
    }

    private fun writeAtomic(target: File, content: String) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeText(content)
        if (!temp.renameTo(target)) {
            temp.delete()
            target.writeText(content)
        }
        onMutation?.invoke()
    }
}
