package com.aisoul.app.agent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * IMPLEMENTATION §4 — the gate is a suspend point: check() either returns
 * instantly (allowed / rule matched) or suspends while the approval sheet is
 * shown. The chat UI observes [pending] and answers via [respond].
 */
class PermissionGate(private val store: PermissionStore) {

    data class PendingApproval(
        val action: GateAction,
        /** label for the "always allow" toggle, null when the mode doesn't offer one */
        val ruleOffer: String?,
    )

    private val _pending = MutableStateFlow<PendingApproval?>(null)
    val pending: StateFlow<PendingApproval?> = _pending

    private var response: CompletableDeferred<Answer>? = null
    private val oneAtATime = Mutex()

    private data class Answer(val approved: Boolean, val alwaysAllow: Boolean)

    /** true = proceed. May suspend for as long as the human takes. */
    suspend fun check(action: GateAction): Boolean = oneAtATime.withLock {
        val mode = store.mode.first()
        val decision = GatePolicy.decide(
            action = action,
            mode = mode,
            allowedHosts = store.allowedHosts.first(),
            allowedCommands = store.allowedCommands.first(),
        )
        if (decision == Decision.ALLOW) return true

        val deferred = CompletableDeferred<Answer>()
        response = deferred
        _pending.value = PendingApproval(action, GatePolicy.ruleOffer(action, mode))
        try {
            val answer = deferred.await()
            if (answer.approved && answer.alwaysAllow) {
                when (action) {
                    is GateAction.FetchHost -> store.allowHost(action.host)
                    is GateAction.RunCommand -> store.allowCommand(action.command)
                    else -> Unit
                }
            }
            answer.approved
        } finally {
            _pending.value = null
            response = null
        }
    }

    fun respond(approved: Boolean, alwaysAllow: Boolean = false) {
        response?.complete(Answer(approved, alwaysAllow))
    }
}
