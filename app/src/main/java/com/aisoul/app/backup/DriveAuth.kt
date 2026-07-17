package com.aisoul.app.backup

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * SPEC §9 — Google sign-in exists solely as Drive authorization. The
 * AuthorizationClient flow (matched to the GCP Android OAuth client by
 * package name + signing SHA-1) hands back a short-lived access token for
 * `drive.file`; first grant resolves through a consent PendingIntent.
 * Background workers call [authorize] silently — if consent is needed there,
 * the backup is skipped and the UI shows "reconnect".
 */
class DriveAuth(private val context: Context) {

    sealed interface State {
        data class Ready(val accessToken: String) : State
        data class NeedsConsent(val pendingIntent: PendingIntent) : State
    }

    suspend fun authorize(): State = suspendCancellableCoroutine { cont ->
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
            .build()
        Identity.getAuthorizationClient(context)
            .authorize(request)
            .addOnSuccessListener { result ->
                val token = result.accessToken
                val pending = result.pendingIntent
                when {
                    result.hasResolution() && pending != null -> cont.resume(State.NeedsConsent(pending))
                    token != null -> cont.resume(State.Ready(token))
                    else -> cont.resumeWithException(IllegalStateException("drive authorization returned neither token nor consent"))
                }
            }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    /** completes the consent flow after the PendingIntent resolution returns */
    fun tokenFromConsentResult(intent: Intent?): String? = runCatching {
        Identity.getAuthorizationClient(context)
            .getAuthorizationResultFromIntent(intent)
            .accessToken
    }.getOrNull()

    companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    }
}
