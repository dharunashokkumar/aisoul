package com.aisoul.app.agent

enum class Decision { ALLOW, ASK }

/**
 * SPEC §6 — the permission matrix, pure and unit-testable. User-made
 * "always allow" rules apply in every mode: they are explicit and revocable,
 * which is the whole point of the rule list.
 */
object GatePolicy {

    /** trusted mode: run without asking only when the first word is read-only. */
    val READ_ONLY_COMMANDS = setOf(
        "ls", "cat", "grep", "egrep", "head", "tail", "wc", "sort", "uniq", "cut",
        "tr", "find", "stat", "du", "df", "date", "echo", "printf", "env", "pwd",
        "which", "basename", "dirname", "md5sum", "sha256sum", "base64", "hexdump",
        "od", "diff", "cmp", "seq", "ps", "uname", "id", "hostname", "ping",
        "nslookup", "jq",
    )

    fun decide(
        action: GateAction,
        mode: PermissionMode,
        allowedHosts: Set<String> = emptySet(),
        allowedCommands: Set<String> = emptySet(),
    ): Decision = when (action) {
        is GateAction.AppendMemoryNote ->
            if (mode == PermissionMode.CAREFUL) Decision.ASK else Decision.ALLOW

        is GateAction.EditSoulUser -> Decision.ASK
        is GateAction.OverwriteFile -> Decision.ASK
        is GateAction.InstallWidget -> Decision.ASK

        is GateAction.FetchHost -> when {
            action.host.lowercase() in allowedHosts -> Decision.ALLOW
            mode == PermissionMode.TRUSTED -> Decision.ALLOW
            else -> Decision.ASK
        }

        is GateAction.RunCommand -> when {
            action.command.trim() in allowedCommands -> Decision.ALLOW
            mode == PermissionMode.TRUSTED && isReadOnly(action.command) -> Decision.ALLOW
            else -> Decision.ASK
        }
    }

    /** Whether the approval sheet offers an "always allow" rule (SPEC §6). */
    fun ruleOffer(action: GateAction, mode: PermissionMode): String? = when {
        action is GateAction.FetchHost && mode != PermissionMode.CAREFUL ->
            "always allow ${action.host}"
        action is GateAction.RunCommand && mode != PermissionMode.CAREFUL ->
            "always allow this exact command"
        else -> null
    }

    fun isReadOnly(command: String): Boolean {
        val trimmed = command.trim()
        // pipes, redirects, chaining: anything compound is not read-only
        if (trimmed.any { it in "|;&><`$" } || trimmed.contains("\n")) return false
        return trimmed.substringBefore(' ') in READ_ONLY_COMMANDS
    }
}
