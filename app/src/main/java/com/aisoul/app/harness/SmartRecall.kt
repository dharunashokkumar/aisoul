package com.aisoul.app.harness

import com.aisoul.app.providers.EmbeddingClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import kotlin.math.sqrt

/**
 * D-032 — recall v2: embedding similarity when the provider offers an
 * embedding API, keyword overlap (recall v1) forever as the floor. The
 * vector index is derived data (SPEC §3): rebuildable, invalidated per
 * entry by a hash of name+description and wholesale by a model switch.
 * Any failure on the embedding path falls back to keywords, silently.
 */
class SmartRecall(
    private val memories: MemoryStore,
    harnessRoot: File,
    private val json: Json,
    /** null = selected provider has no embedding api (or no key yet) */
    private val clientProvider: suspend () -> EmbeddingClient?,
) {

    private val indexFile = File(File(harnessRoot, "memories"), ".embeddings.json")

    @Serializable
    data class StoredVector(val hash: String, val vector: List<Float>)

    @Serializable
    data class EmbeddingIndex(val model: String = "", val entries: Map<String, StoredVector> = emptyMap())

    suspend fun recall(query: String, limit: Int = 5): List<Memory> {
        val keyword = runCatching { memories.recall(query, limit) }.getOrDefault(emptyList())
        val client = runCatching { clientProvider() }.getOrNull() ?: return keyword
        val semantic = runCatching {
            withTimeout(EMBED_TIMEOUT_MS) { semanticRecall(client, query, limit) }
        }.getOrNull()
        return if (semantic.isNullOrEmpty()) keyword else semantic
    }

    private suspend fun semanticRecall(client: EmbeddingClient, query: String, limit: Int): List<Memory> {
        val all = memories.list()
        if (all.isEmpty()) return emptyList()

        var index = readIndex()
        if (index.model != client.modelTag) index = EmbeddingIndex(client.modelTag)

        val texts = all.associate { it.slug to "${it.name} — ${it.description}" }
        val missing = all
            .filter { memory -> index.entries[memory.slug]?.hash != hashOf(texts.getValue(memory.slug)) }
            .take(MAX_BATCH)

        val vectors = client.embed(listOf(query) + missing.map { texts.getValue(it.slug) })
            ?: return emptyList()
        if (vectors.size != missing.size + 1) return emptyList()
        val queryVector = vectors[0]

        val live = all.map { it.slug }.toSet()
        val entries = index.entries.filterKeys { it in live }.toMutableMap()
        missing.forEachIndexed { at, memory ->
            entries[memory.slug] = StoredVector(hashOf(texts.getValue(memory.slug)), vectors[at + 1].toList())
        }
        if (entries != index.entries) writeIndex(EmbeddingIndex(client.modelTag, entries))

        return all
            .mapNotNull { memory ->
                val stored = entries[memory.slug] ?: return@mapNotNull null
                memory to cosine(queryVector, stored.vector.toFloatArray())
            }
            .filter { (_, score) -> score > MIN_SIMILARITY }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    private suspend fun readIndex(): EmbeddingIndex = withContext(Dispatchers.IO) {
        if (!indexFile.exists()) return@withContext EmbeddingIndex()
        runCatching {
            json.decodeFromString(EmbeddingIndex.serializer(), indexFile.readText())
        }.getOrElse { EmbeddingIndex() }
    }

    private suspend fun writeIndex(index: EmbeddingIndex) = withContext(Dispatchers.IO) {
        runCatching {
            indexFile.parentFile?.mkdirs()
            val temp = File(indexFile.parentFile, "${indexFile.name}.tmp")
            temp.writeText(json.encodeToString(EmbeddingIndex.serializer(), index))
            if (!temp.renameTo(indexFile)) {
                temp.delete()
                indexFile.writeText(json.encodeToString(EmbeddingIndex.serializer(), index))
            }
        }
    }

    private fun hashOf(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)

    companion object {
        private const val EMBED_TIMEOUT_MS = 8_000L
        private const val MAX_BATCH = 64
        private const val MIN_SIMILARITY = 0.2f

        internal fun cosine(a: FloatArray, b: FloatArray): Float {
            if (a.isEmpty() || a.size != b.size) return 0f
            var dot = 0f
            var normA = 0f
            var normB = 0f
            for (i in a.indices) {
                dot += a[i] * b[i]
                normA += a[i] * a[i]
                normB += b[i] * b[i]
            }
            if (normA == 0f || normB == 0f) return 0f
            return dot / (sqrt(normA) * sqrt(normB))
        }
    }
}
