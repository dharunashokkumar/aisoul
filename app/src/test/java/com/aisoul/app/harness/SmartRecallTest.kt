package com.aisoul.app.harness

import com.aisoul.app.providers.EmbeddingClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** D-032 — the full smart-recall pipeline on the JVM with a fake embedder. */
class SmartRecallTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var root: File
    private lateinit var memories: MemoryStore

    /** deterministic 2-d "embeddings": axis 0 = coffee-ness, axis 1 = training-ness */
    private class FakeEmbedder : EmbeddingClient {
        override val modelTag = "fake/v1"
        var calls = 0
        override suspend fun embed(texts: List<String>): List<FloatArray> {
            calls++
            return texts.map { text ->
                floatArrayOf(
                    if ("espresso" in text || "coffee" in text) 1f else 0.05f,
                    if ("marathon" in text || "training" in text) 1f else 0.05f,
                )
            }
        }
    }

    @Before
    fun setUp() = runBlocking {
        root = temp.newFolder("harness")
        memories = MemoryStore(root, json)
        memories.upsert("espresso-order", "espresso order", "always a double espresso, no sugar", "user", "double, no sugar.")
        memories.upsert("marathon-plan", "marathon training", "runs long on sundays for the marathon", "project", "sunday long runs.")
    }

    @Test
    fun `semantic recall ranks by similarity, not word overlap`() = runBlocking {
        val recall = SmartRecall(memories, root, json) { FakeEmbedder() }
        // "coffee" shares zero words with the memory name/description
        val hits = recall.recall("coffee tomorrow morning")
        assertTrue(hits.isNotEmpty())
        assertEquals("espresso-order", hits.first().slug)
    }

    @Test
    fun `no embedding client falls back to keywords`() = runBlocking {
        val recall = SmartRecall(memories, root, json) { null }
        val hits = recall.recall("marathon next month")
        assertEquals(listOf("marathon-plan"), hits.map { it.slug })
    }

    @Test
    fun `failing embedder falls back to keywords`() = runBlocking {
        val broken = object : EmbeddingClient {
            override val modelTag = "fake/broken"
            override suspend fun embed(texts: List<String>): List<FloatArray>? = null
        }
        val recall = SmartRecall(memories, root, json) { broken }
        val hits = recall.recall("marathon next month")
        assertEquals(listOf("marathon-plan"), hits.map { it.slug })
    }

    @Test
    fun `vectors are cached — second recall embeds only the query`() = runBlocking {
        val embedder = FakeEmbedder()
        val recall = SmartRecall(memories, root, json) { embedder }
        recall.recall("coffee")
        val indexFile = File(File(root, "memories"), ".embeddings.json")
        assertTrue(indexFile.exists())
        val before = indexFile.readText()
        recall.recall("coffee again")
        // index unchanged: nothing re-embedded, nothing rewritten
        assertEquals(before, indexFile.readText())
        assertEquals(2, embedder.calls)
    }

    @Test
    fun `cosine sanity`() {
        val a = floatArrayOf(1f, 0f)
        assertEquals(1f, SmartRecall.cosine(a, floatArrayOf(1f, 0f)), 1e-6f)
        assertEquals(0f, SmartRecall.cosine(a, floatArrayOf(0f, 1f)), 1e-6f)
        assertEquals(0f, SmartRecall.cosine(a, floatArrayOf(0f, 0f)), 1e-6f)
        assertEquals(0f, SmartRecall.cosine(a, floatArrayOf(1f, 0f, 1f)), 1e-6f)
    }
}
