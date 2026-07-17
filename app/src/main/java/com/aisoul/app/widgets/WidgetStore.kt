package com.aisoul.app.widgets

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * SPEC §8 — widgets are just files under /harness/widgets. The approval
 * registry (.approvals.json) freezes a SHA-256 of the exact spec text at
 * human approval; any edit — by AI or user — invalidates the hash and the
 * widget refuses to execute until re-approved. Frozen means frozen.
 */
class WidgetStore(private val root: File, private val json: Json) {

    private val dir get() = File(root, "widgets")
    private val approvalsFile get() = File(dir, ".approvals.json")
    private val stateDir get() = File(dir, ".state")
    private val historyDir get() = File(dir, ".history")
    private val proposalsDir get() = File(dir, ".proposals")

    private val pretty = Json { prettyPrint = true; encodeDefaults = false }

    @Serializable
    data class ApprovalEntry(val hash: String, val approvedAt: Long, val born: Boolean = false)

    @Serializable
    data class CachedValues(val at: Long = 0, val values: Map<String, String> = emptyMap())

    enum class State { ACTIVE, NEEDS_APPROVAL, INVALID }

    data class Installed(
        val id: String,
        val spec: WidgetSpec?,
        val capabilities: WidgetCapabilities?,
        val state: State,
        val problems: List<String> = emptyList(),
        val needsBirth: Boolean = false,
        val approvedAt: Long = 0,
    )

    suspend fun list(): List<Installed> = withContext(Dispatchers.IO) {
        val registry = readRegistry()
        dir.listFiles { file -> file.extension == "json" && !file.name.startsWith(".") }
            .orEmpty()
            .map { file ->
                val id = file.nameWithoutExtension
                val text = runCatching { file.readText() }.getOrNull() ?: ""
                val entry = registry[id]
                val result = runCatching { WidgetValidator.validate(text) }
                    .getOrElse { e ->
                        WidgetValidator.Result.Invalid(listOf("validator failed: ${e.message?.take(160) ?: e.javaClass.simpleName}"))
                    }
                when (result) {
                    is WidgetValidator.Result.Invalid -> Installed(id, null, null, State.INVALID, result.problems)
                    is WidgetValidator.Result.Valid -> {
                        val approved = entry != null && entry.hash == sha256(text)
                        Installed(
                            id = id,
                            spec = result.spec,
                            capabilities = result.capabilities,
                            state = if (approved) State.ACTIVE else State.NEEDS_APPROVAL,
                            needsBirth = approved && !entry.born,
                            approvedAt = entry?.approvedAt ?: 0,
                        )
                    }
                }
            }
            .sortedBy { it.approvedAt }
    }

    /** Called only AFTER the human approved the spec (the gate enforces that). */
    suspend fun installApproved(spec: WidgetSpec, born: Boolean = false): Unit = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val text = pretty.encodeToString(WidgetSpec.serializer(), spec)
        writeAtomic(File(dir, "${spec.id}.json"), text)
        updateRegistry { registry ->
            registry[spec.id] = ApprovalEntry(sha256(text), System.currentTimeMillis(), born)
        }
    }

    /** Re-approve the file as it currently is (after user edits). */
    suspend fun approveCurrent(id: String): Unit = withContext(Dispatchers.IO) {
        val file = File(dir, "$id.json")
        if (!file.exists()) return@withContext
        val text = file.readText()
        updateRegistry { registry ->
            val born = registry[id]?.born ?: true
            registry[id] = ApprovalEntry(sha256(text), System.currentTimeMillis(), born)
        }
    }

    suspend fun remove(id: String): Unit = withContext(Dispatchers.IO) {
        File(dir, "$id.json").delete()
        File(stateDir, "$id.json").delete()
        historyDir.listFiles { file -> file.name.startsWith("$id--") }?.forEach { it.delete() }
        updateRegistry { it.remove(id) }
    }

    suspend fun markBorn(id: String): Unit = withContext(Dispatchers.IO) {
        updateRegistry { registry ->
            registry[id]?.let { registry[id] = it.copy(born = true) }
        }
    }

    // ---- proposal inbox (D-030): specs wait here, execute never ----

    data class Proposal(
        val id: String,
        val spec: WidgetSpec,
        /** plain-language capability lines shown on the inbox card */
        val capabilities: List<String>,
    )

    /** Validated spec text in, proposal file out. Nothing executes from here. */
    suspend fun saveProposal(specJson: String): Unit = withContext(Dispatchers.IO) {
        val result = runCatching { WidgetValidator.validate(specJson) }.getOrNull()
        if (result !is WidgetValidator.Result.Valid) return@withContext
        proposalsDir.mkdirs()
        writeAtomic(File(proposalsDir, "${result.spec.id}.json"), specJson)
    }

    suspend fun listProposals(): List<Proposal> = withContext(Dispatchers.IO) {
        proposalsDir.listFiles { file -> file.extension == "json" }
            .orEmpty()
            .sortedBy { it.lastModified() }
            .mapNotNull { file ->
                val text = runCatching { file.readText() }.getOrNull() ?: return@mapNotNull null
                when (val result = runCatching { WidgetValidator.validate(text) }.getOrNull()) {
                    is WidgetValidator.Result.Valid -> Proposal(
                        id = file.nameWithoutExtension,
                        spec = result.spec,
                        capabilities = result.capabilities.summaryLines(result.spec.refresh),
                    )
                    else -> {
                        file.delete() // a proposal that stopped validating is dead weight
                        null
                    }
                }
            }
    }

    /** THE approval moment — the human tapped approve on the dashboard card. */
    suspend fun approveProposal(id: String): Unit = withContext(Dispatchers.IO) {
        val file = File(proposalsDir, "$id.json")
        val text = runCatching { file.readText() }.getOrNull() ?: return@withContext
        val result = runCatching { WidgetValidator.validate(text) }.getOrNull()
        if (result is WidgetValidator.Result.Valid) installApproved(result.spec)
        file.delete()
    }

    suspend fun dismissProposal(id: String): Unit = withContext(Dispatchers.IO) {
        File(proposalsDir, "$id.json").delete()
    }

    // ---- last-known values (dashboard renders instantly + honestly) ----

    suspend fun readValues(id: String): CachedValues? = withContext(Dispatchers.IO) {
        val file = File(stateDir, "$id.json")
        if (!file.exists()) return@withContext null
        runCatching { json.decodeFromString(CachedValues.serializer(), file.readText()) }.getOrNull()
    }

    suspend fun writeValues(id: String, values: Map<String, String>): Unit = withContext(Dispatchers.IO) {
        stateDir.mkdirs()
        val cached = CachedValues(System.currentTimeMillis(), values)
        writeAtomic(File(stateDir, "$id.json"), json.encodeToString(CachedValues.serializer(), cached))
    }

    // ---- sparkline history: last 96 numeric samples per source ----

    suspend fun appendHistory(id: String, source: String, value: Double): Unit = withContext(Dispatchers.IO) {
        historyDir.mkdirs()
        val file = File(historyDir, "$id--$source.json")
        val samples = readHistory(id, source) + value
        val kept = samples.takeLast(96)
        writeAtomic(file, kept.joinToString(",", prefix = "[", postfix = "]"))
    }

    suspend fun readHistory(id: String, source: String): List<Double> = withContext(Dispatchers.IO) {
        val file = File(historyDir, "$id--$source.json")
        if (!file.exists()) return@withContext emptyList()
        runCatching {
            file.readText().trim().removeSurrounding("[", "]")
                .split(',').filter { it.isNotBlank() }.map { it.trim().toDouble() }
        }.getOrElse { emptyList() }
    }

    // ---- the three SPEC §4 defaults — ordinary DSL files, pre-approved ----

    suspend fun ensureDefaults(): Unit = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val existing = dir.listFiles { file -> file.extension == "json" && !file.name.startsWith(".") }
        if (!existing.isNullOrEmpty() || readRegistry().isNotEmpty()) return@withContext
        defaults().forEach { spec -> installApproved(spec, born = true) }
    }

    private fun defaults(): List<WidgetSpec> = listOf(
        WidgetSpec(
            id = "talk",
            title = "talk",
            size = "small",
            refresh = RefreshSpec(on_open = false, interval_min = 0),
            body = listOf(
                ComponentSpec(type = "text", text = "talk to your ai", style = "title"),
                ComponentSpec(type = "text", text = "the conversation is the whole point. tap anywhere.", style = "caption"),
            ),
            tap = ActionSpec(type = "screen", screen = "chat"),
        ),
        WidgetSpec(
            id = "today",
            title = "today",
            size = "medium",
            refresh = RefreshSpec(on_open = true, interval_min = 0),
            sources = mapOf(
                "note" to SourceSpec(type = "file", path = "notes/{today}.md", extract = "lines:1-6"),
            ),
            body = listOf(
                ComponentSpec(
                    type = "list",
                    items_from = "note",
                    empty = "no note today yet — the day is unwritten.",
                ),
            ),
            tap = ActionSpec(type = "screen", screen = "files"),
        ),
        WidgetSpec(
            id = "memory",
            title = "memory",
            size = "medium",
            refresh = RefreshSpec(on_open = true, interval_min = 0),
            sources = mapOf(
                "count" to SourceSpec(type = "memory", extract = "$.count"),
                "latest" to SourceSpec(type = "memory", extract = "$.latest"),
            ),
            body = listOf(
                ComponentSpec(type = "stat", label = "things it knows", value = "{count}"),
                ComponentSpec(type = "text", text = "{latest}", style = "caption"),
            ),
            tap = ActionSpec(type = "screen", screen = "memory"),
        ),
    )

    private fun readRegistry(): MutableMap<String, ApprovalEntry> {
        if (!approvalsFile.exists()) return mutableMapOf()
        return runCatching {
            json.decodeFromString(
                MapSerializer(String.serializer(), ApprovalEntry.serializer()),
                approvalsFile.readText(),
            ).toMutableMap()
        }.getOrElse { mutableMapOf() }
    }

    private fun updateRegistry(mutate: (MutableMap<String, ApprovalEntry>) -> Unit) {
        val registry = readRegistry()
        mutate(registry)
        dir.mkdirs()
        writeAtomic(
            approvalsFile,
            json.encodeToString(MapSerializer(String.serializer(), ApprovalEntry.serializer()), registry),
        )
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun writeAtomic(target: File, content: String) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeText(content)
        if (!temp.renameTo(target)) {
            temp.delete()
            target.writeText(content)
        }
    }
}
