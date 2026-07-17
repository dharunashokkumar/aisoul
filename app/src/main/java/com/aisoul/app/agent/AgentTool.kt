package com.aisoul.app.agent

import kotlinx.serialization.json.JsonObject

/** One entry in the agent's tool registry (SPEC §6). */
interface AgentTool {
    val name: String
    val description: String
    val inputSchema: JsonObject

    /**
     * The gated action this call performs, or null for ungated reads.
     * Called before execute; the permission gate decides allow/ask from it.
     */
    fun gateAction(args: JsonObject): GateAction?

    suspend fun execute(args: JsonObject): ToolOutcome
}

data class ToolOutcome(
    val content: String,
    val isError: Boolean = false,
    /** true for web content — rides into Part.ToolResult.untrusted */
    val untrusted: Boolean = false,
)

/**
 * Everything the gate can be asked about. Each carries the EXACT input —
 * approval prompts never show a summary alone (SPEC §6).
 */
sealed interface GateAction {
    data class AppendMemoryNote(val path: String, val preview: String) : GateAction
    data class EditSoulUser(val path: String, val preview: String) : GateAction
    data class OverwriteFile(val path: String, val preview: String) : GateAction
    data class FetchHost(val host: String, val url: String, val method: String) : GateAction
    data class RunCommand(val command: String) : GateAction
    data class InstallWidget(
        val title: String,
        /** plain-language capability lines, e.g. "GET https://…/health every 15 min" */
        val capabilities: List<String>,
        val specJson: String,
    ) : GateAction
}
