package com.aisoul.app.distill

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.aisoul.app.AiSoulApp
import com.aisoul.app.providers.ChatMessage
import com.aisoul.app.providers.ChatRequest
import com.aisoul.app.providers.Part
import com.aisoul.app.providers.ProviderType
import com.aisoul.app.providers.Role
import com.aisoul.app.providers.StreamEvent
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * SPEC §3 — the compounding loop. After a conversation idles or closes, a
 * cheap model distills durable facts into /harness/memories. create/update
 * apply silently (visible in the memory feed); delete queues for approval.
 */
class DistillWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val chatId = inputData.getString(KEY_CHAT_ID) ?: return Result.success()
        val container = (applicationContext as AiSoulApp).container

        val transcript = container.harness.readTranscript(chatId)
        if (transcript.count { it.role == Role.USER } < 1 || transcript.size < 2) {
            return Result.success()
        }

        val provider = container.settings.selectedProvider.first()
        val key = container.keys.getKey(provider)
            ?: if (provider == ProviderType.OPENAI_COMPAT) "" else return Result.success()
        val model = container.settings.distillModelFor(provider).first()
        val baseUrl = if (provider == ProviderType.OPENAI_COMPAT) {
            container.settings.compatBaseUrl.first()
        } else null
        val client = container.providerFactory.create(provider, key, baseUrl)

        val index = container.harness.readOrNull("MEMORY.md") ?: "# memory index\n"
        val userContent = buildString {
            append("existing memory index:\n")
            append(index)
            append("\n\nconversation:\n")
            append(renderTranscript(transcript))
        }

        val raw = runModel(client, model, DISTILL_SYSTEM, userContent)
            ?: return if (runAttemptCount < 2) Result.retry() else Result.success()
        if (raw.isBlank()) return Result.success()

        val result = DistillParser.parseResult(raw)
        applyOps(container, result.operations)

        // the closeout invariants (D-020): journal entry, cursor, activity row, title
        val label = result.activity ?: "session"
        result.log?.let { runCatching { container.harness.appendJournalEntry(label, it) } }
        result.cursor?.let { runCatching { container.harness.writeCursor(it) } }
        runCatching { container.harness.appendActivity(label, result.operations.size) }
        result.title?.let { runCatching { container.harness.setChatTitle(chatId, it) } }

        // META pass every 12 distills: make memory smarter, not bigger
        if (container.settings.bumpDistillCount() >= META_EVERY) {
            container.settings.resetDistillCount()
            runCatching { runMetaPass(container, client, model) }
        }
        return Result.success()
    }

    /** One streamed call; null = transient network failure (worth a retry). */
    private suspend fun runModel(
        client: com.aisoul.app.providers.ProviderClient,
        model: String,
        system: String,
        user: String,
    ): String? {
        val raw = StringBuilder()
        try {
            client.stream(
                ChatRequest(
                    model = model,
                    system = system,
                    messages = listOf(ChatMessage(Role.USER, listOf(Part.Text(user)))),
                    maxTokens = 2048,
                ),
            ).collect { event ->
                if (event is StreamEvent.TextDelta) raw.append(event.text)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: IOException) {
            return null
        } catch (e: Exception) {
            // provider refused (bad model id, quota, …) — drop silently, never corrupt
            return ""
        }
        return raw.toString()
    }

    private suspend fun applyOps(container: com.aisoul.app.di.AppContainer, ops: List<com.aisoul.app.distill.MemoryOp>) {
        ops.forEach { op ->
            when (op.op) {
                "create", "update" -> container.memories.upsert(
                    slug = op.slug,
                    name = op.name,
                    description = op.description,
                    type = op.type,
                    body = op.content.takeIf { it.isNotBlank() },
                )
                "delete" -> container.memories.queueDelete(op.slug, op.description)
            }
        }
    }

    private suspend fun runMetaPass(
        container: com.aisoul.app.di.AppContainer,
        client: com.aisoul.app.providers.ProviderClient,
        model: String,
    ) {
        val memories = container.memories.list()
        val input = buildString {
            append("memory files (slug · type · description):\n")
            memories.forEach { append("- ${it.slug} · ${it.type} · ${it.description}\n") }
            append("\ncurrent SUMMARY.md:\n")
            append(container.harness.readOrNull("SUMMARY.md") ?: "(none yet)")
            append("\n\nlast journal entry:\n")
            append(container.harness.lastJournalEntry() ?: "(none)")
        }
        val raw = runModel(client, model, META_SYSTEM, input) ?: return
        val result = DistillParser.parseResult(raw)
        applyOps(container, result.operations)
        result.summary?.let { container.harness.writeFile("SUMMARY.md", it.trim() + "\n") }
    }

    private fun renderTranscript(messages: List<ChatMessage>): String {
        val rendered = messages.joinToString("\n") { message ->
            val who = if (message.role == Role.USER) "user" else "assistant"
            "$who: ${message.text}"
        }
        // keep the tail; distill models don't need the whole epic
        return rendered.takeLast(8000)
    }

    companion object {
        private const val KEY_CHAT_ID = "chat_id"
        private const val META_EVERY = 12

        private val DISTILL_SYSTEM = """
            you close out one session for a personal ai: distill durable facts into memory files, then write the session handoff.

            output STRICT json, nothing else:
            {"operations":[{"op":"create|update|delete","slug":"kebab-case-slug","name":"short title","description":"one line used for recall","type":"user|preference|project|reference","content":"markdown body"}],
             "log":"the session journal entry, markdown",
             "cursor":"resume-here state for the next session",
             "activity":"3-5 word session label",
             "title":"short chat title"}

            memory rules:
            - only facts worth remembering for weeks: identity, preferences, ongoing projects, important references.
            - no session trivia, no restating the conversation.
            - 0 to 3 operations is typical. [] when nothing durable appeared.
            - if a slug in the existing index already covers the topic, use op "update" with that slug instead of creating a near-duplicate.
            - "delete" only when the conversation proves an existing memory wrong; put the reason in "description".

            handoff rules:
            - "log": what happened, what was decided, what's unresolved, anything new you read about the user. write it to your future self.
            - "cursor": live state only — last thing done, exact next step, open threads. lean; point at files, don't restate them.
            - omit any handoff field the session didn't earn (a two-line chat needs no cursor rewrite).
        """.trimIndent()

        private val META_SYSTEM = """
            you maintain a personal ai's long-term memory. make it smarter, not bigger.

            output STRICT json, nothing else:
            {"operations":[{"op":"update|delete","slug":"existing-slug","name":"...","description":"...","type":"...","content":"..."}],
             "summary":"full replacement for SUMMARY.md — the long arc of this person and ai, under 250 words"}

            rules:
            - merge near-duplicates: update the canonical slug, delete the redundant one (reason in description).
            - delete stale or proven-wrong memories (deletes are queued for the user to approve — be honest, not timid).
            - never invent facts; only reorganize what is already there.
            - [] and keeping the current summary are fine when memory is already tight.
        """.trimIndent()

        /** Enqueue (or re-slide) the distill for a conversation. */
        fun schedule(context: Context, chatId: String, delayMinutes: Long = 10) {
            val request = OneTimeWorkRequestBuilder<DistillWorker>()
                .setInputData(workDataOf(KEY_CHAT_ID to chatId))
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "distill-$chatId",
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
