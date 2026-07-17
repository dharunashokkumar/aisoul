package com.aisoul.app.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * D-032 — provider embedding APIs for smarter recall. Only what recall
 * needs: a batch of short texts in, one vector each out, null on ANY
 * failure (the caller falls back to keyword recall, silently).
 */
interface EmbeddingClient {
    /** stored with the vectors; a different tag invalidates the index */
    val modelTag: String

    suspend fun embed(texts: List<String>): List<FloatArray>?
}

private val lenient = Json { ignoreUnknownKeys = true }
private val jsonMedia = "application/json".toMediaType()

/** POST {base}/embeddings — also the openai-compatible shape. */
class OpenAiEmbeddingClient(
    http: OkHttpClient,
    private val apiKey: String,
    private val baseUrl: String = "https://api.openai.com/v1",
    private val model: String = "text-embedding-3-small",
) : EmbeddingClient {

    private val client = http.newBuilder().callTimeout(15, TimeUnit.SECONDS).build()

    override val modelTag: String get() = "openai/$model"

    override suspend fun embed(texts: List<String>): List<FloatArray>? = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildJsonObject {
                put("model", model)
                putJsonArray("input") { texts.forEach { add(it) } }
            }
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/embeddings")
                .header("Authorization", "Bearer $apiKey")
                .post(body.toString().toRequestBody(jsonMedia))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val root = lenient.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
                val data = root["data"]?.jsonArray ?: return@use null
                val vectors = arrayOfNulls<FloatArray>(texts.size)
                data.forEach { item ->
                    val obj = item.jsonObject
                    val index = (obj["index"] as? JsonPrimitive)?.int ?: return@use null
                    vectors[index] = obj["embedding"]?.jsonArray?.toFloatArray() ?: return@use null
                }
                if (vectors.any { it == null }) null else vectors.filterNotNull()
            }
        }.getOrNull()
    }
}

/** POST models/{model}:batchEmbedContents on generativelanguage. */
class GeminiEmbeddingClient(
    http: OkHttpClient,
    private val apiKey: String,
    private val model: String = "text-embedding-004",
    private val baseUrl: String = "https://generativelanguage.googleapis.com",
) : EmbeddingClient {

    private val client = http.newBuilder().callTimeout(15, TimeUnit.SECONDS).build()

    override val modelTag: String get() = "gemini/$model"

    override suspend fun embed(texts: List<String>): List<FloatArray>? = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildJsonObject {
                putJsonArray("requests") {
                    texts.forEach { text ->
                        add(
                            buildJsonObject {
                                put("model", "models/$model")
                                putJsonObject("content") {
                                    putJsonArray("parts") {
                                        add(buildJsonObject { put("text", text) })
                                    }
                                }
                            },
                        )
                    }
                }
            }
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/v1beta/models/$model:batchEmbedContents")
                .header("x-goog-api-key", apiKey)
                .post(body.toString().toRequestBody(jsonMedia))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val root = lenient.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
                val embeddings = root["embeddings"]?.jsonArray ?: return@use null
                if (embeddings.size != texts.size) return@use null
                embeddings.map { item ->
                    (item.jsonObject["values"]?.jsonArray ?: return@use null).toFloatArray()
                }
            }
        }.getOrNull()
    }
}

private fun JsonArray.toFloatArray(): FloatArray =
    FloatArray(size) { index -> (this[index] as JsonPrimitive).float }
