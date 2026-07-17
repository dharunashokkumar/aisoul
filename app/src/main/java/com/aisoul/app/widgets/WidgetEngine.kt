package com.aisoul.app.widgets

import com.aisoul.app.harness.HarnessStore
import com.aisoul.app.harness.MemoryStore
import com.aisoul.app.toolbox.ToolboxRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Executes a widget's frozen sources (SPEC §8 security model): only specs
 * whose approval hash matches ever reach this code, and only the URLs /
 * commands / paths inside them can execute — never the chat permission flow.
 */
class WidgetEngine(
    private val harness: HarnessStore,
    private val memories: MemoryStore,
    private val toolbox: ToolboxRunner,
    http: OkHttpClient,
) {

    private val client = http.newBuilder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Refreshes every source; on failure keeps the previous value (honesty > blankness). */
    suspend fun refresh(installed: WidgetStore.Installed, store: WidgetStore): Map<String, String> {
        val spec = installed.spec ?: return emptyMap()
        if (installed.state != WidgetStore.State.ACTIVE) return emptyMap()
        val previous = store.readValues(spec.id)?.values ?: emptyMap()
        val values = mutableMapOf<String, String>()
        spec.sources.forEach { (name, source) ->
            val raw = runCatching { fetchRaw(source) }.getOrNull()
            val extracted = raw?.let { Extractors.apply(source.extract, it) }?.take(500)
            values[name] = extracted ?: previous[name] ?: "—"
            extracted?.trim()?.toDoubleOrNull()?.let { number ->
                store.appendHistory(spec.id, name, number)
            }
        }
        store.writeValues(spec.id, values)
        return values
    }

    private suspend fun fetchRaw(source: SourceSpec): String? = when (source.type) {
        "static" -> source.value

        "http" -> {
            val url = source.url ?: return null
            val builder = Request.Builder().url(url)
            if (source.method == "POST") builder.post(ByteArray(0).toRequestBody(null))
            // blocking OkHttp call — the dashboard refreshes from the main thread
            withContext(Dispatchers.IO) {
                client.newCall(builder.build()).execute().use { response ->
                    response.body?.byteStream()?.readNBytesCapped(128 * 1024)?.toString(Charsets.UTF_8)
                }
            }
        }

        "tool" -> {
            val command = source.command ?: return null
            val result = toolbox.run(command, timeoutMs = 15_000, capBytes = 16 * 1024)
            if (result.timedOut) null else result.output
        }

        "file" -> {
            val path = source.path?.replace("{today}", today()) ?: return null
            harness.readOrNull(path)
        }

        "countdown" -> {
            val target = runCatching { LocalDate.parse(source.date) }.getOrNull() ?: return null
            val days = ChronoUnit.DAYS.between(LocalDate.now(), target)
            when {
                days == 0L -> "today"
                days == 1L -> "tomorrow"
                days > 1L -> "in $days days"
                days == -1L -> "yesterday"
                else -> "${-days} days ago"
            }
        }

        "memory" -> {
            val all = memories.list()
            buildJsonObject {
                put("count", all.size)
                put("latest", all.firstOrNull()?.let { "learned: ${it.name}" } ?: "nothing yet")
            }.toString()
        }

        else -> null
    }

    private fun java.io.InputStream.readNBytesCapped(cap: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8 * 1024)
        while (out.size() < cap) {
            val n = read(buf, 0, minOf(buf.size, cap - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}
