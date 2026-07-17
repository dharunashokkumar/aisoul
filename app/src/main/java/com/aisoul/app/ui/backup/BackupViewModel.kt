package com.aisoul.app.ui.backup

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aisoul.app.backup.ArchivePreview
import com.aisoul.app.backup.BackupCrypto
import com.aisoul.app.backup.BackupWorker
import com.aisoul.app.backup.DriveArchive
import com.aisoul.app.backup.DriveAuth
import com.aisoul.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** a decrypted archive waiting for the typed confirmation (SPEC §9 restore) */
data class RestoreCandidate(
    val sourceLabel: String,
    val zipBytes: ByteArray,
    val preview: ArchivePreview,
)

/** an encrypted blob waiting for its passphrase */
data class PassphrasePrompt(
    val sourceLabel: String,
    val blob: ByteArray,
    val error: String? = null,
)

data class BackupUiState(
    val driveEnabled: Boolean = false,
    val accountEmail: String? = null,
    val wifiOnly: Boolean = true,
    val hasPassphrase: Boolean = false,
    val lastBackupAt: Long = 0L,
    val lastResult: String? = null,
    /** non-null while something long runs — shown as the working line */
    val busy: String? = null,
    /** null until loaded; empty = connected but no archives yet */
    val archives: List<DriveArchive>? = null,
    val needsReconnect: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val passphrasePrompt: PassphrasePrompt? = null,
    val restoreCandidate: RestoreCandidate? = null,
    val restored: Boolean = false,
)

/**
 * SPEC §9 / §10 backup — Drive connect/disconnect, passphrase, manual
 * backup, archive list + restore, SAF export/import. Consent and document
 * pickers resolve in the screen; results come back through here.
 */
class BackupViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state

    /** consent PendingIntent the screen must launch, one-shot */
    private val _consent = MutableStateFlow<PendingIntent?>(null)
    val consent: StateFlow<PendingIntent?> = _consent

    private var afterConsent: (suspend (token: String) -> Unit)? = null

    init {
        val settings = container.backupSettings
        settings.driveEnabled.onEach { enabled ->
            _state.update { it.copy(driveEnabled = enabled) }
            if (enabled && _state.value.archives == null) loadArchives()
        }.launchIn(viewModelScope)
        settings.accountEmail.onEach { email -> _state.update { it.copy(accountEmail = email) } }.launchIn(viewModelScope)
        settings.wifiOnly.onEach { wifi -> _state.update { it.copy(wifiOnly = wifi) } }.launchIn(viewModelScope)
        settings.hasPassphrase.onEach { has -> _state.update { it.copy(hasPassphrase = has) } }.launchIn(viewModelScope)
        settings.lastBackupAt.onEach { at -> _state.update { it.copy(lastBackupAt = at) } }.launchIn(viewModelScope)
        settings.lastResult.onEach { result -> _state.update { it.copy(lastResult = result) } }.launchIn(viewModelScope)
    }

    // ---- drive ----

    fun connect() = withToken("connecting to drive…") { token ->
        val email = container.backup.accountEmail(token)
        container.backupSettings.setAccountEmail(email)
        container.backupSettings.setDriveEnabled(true)
        BackupWorker.schedulePeriodic(container.appContext, container.backupSettings.wifiOnly.first())
        _state.update { it.copy(notice = "drive connected.", needsReconnect = false) }
        loadArchives()
    }

    fun disconnect() {
        viewModelScope.launch {
            container.backupSettings.setDriveEnabled(false)
            BackupWorker.cancelPeriodic(container.appContext)
            _state.update { it.copy(archives = null, notice = "drive disconnected. your archives stay in your drive.") }
        }
    }

    fun backupNow() {
        if (!_state.value.hasPassphrase) {
            _state.update { it.copy(error = "set a backup passphrase first.") }
            return
        }
        withToken("backing up…") { token ->
            val summary = container.backup.backupToDrive(token)
            _state.update { it.copy(notice = summary) }
            loadArchives()
        }
    }

    /** silent: a stale grant shows "reconnect" instead of popping consent on open */
    fun loadArchives() = withToken(null, silent = true) { token ->
        val archives = container.backup.listDriveArchives(token)
        _state.update { it.copy(archives = archives, needsReconnect = false) }
    }

    fun setWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch {
            container.backupSettings.setWifiOnly(wifiOnly)
            if (container.backupSettings.driveEnabled.first()) {
                BackupWorker.schedulePeriodic(container.appContext, wifiOnly)
            }
        }
    }

    fun onConsentResult(intent: Intent?) {
        val action = afterConsent ?: return
        afterConsent = null
        val token = container.driveAuth.tokenFromConsentResult(intent)
        viewModelScope.launch {
            if (token == null) {
                _state.update { it.copy(busy = null, error = "google didn't authorize drive access.") }
            } else {
                runCatching { action(token) }
                    .onFailure { e -> _state.update { s -> s.copy(error = e.message ?: "something failed") } }
                _state.update { it.copy(busy = null) }
            }
        }
    }

    // ---- passphrase ----

    fun setPassphrase(passphrase: String, again: String) {
        viewModelScope.launch {
            when {
                passphrase.length < 8 ->
                    _state.update { it.copy(error = "passphrase needs at least 8 characters.") }
                passphrase != again ->
                    _state.update { it.copy(error = "the two passphrases don't match.") }
                else -> {
                    container.backupSettings.setPassphrase(passphrase)
                    _state.update { it.copy(notice = "passphrase set. lose it, lose the backups — write it down.") }
                }
            }
        }
    }

    // ---- restore, from either source ----

    fun restoreFromDrive(archive: DriveArchive) = withToken("downloading ${archive.name}…") { token ->
        val blob = container.backup.downloadArchive(token, archive.id)
        _state.update { it.copy(passphrasePrompt = PassphrasePrompt(archive.name, blob)) }
    }

    fun importFrom(uri: Uri) {
        run("reading archive…") {
            val blob = container.backup.readBlob(uri)
            _state.update { it.copy(passphrasePrompt = PassphrasePrompt("imported file", blob)) }
        }
    }

    fun submitRestorePassphrase(passphrase: String) {
        val prompt = _state.value.passphrasePrompt ?: return
        run("decrypting…") {
            try {
                val zip = container.backup.decrypt(prompt.blob, passphrase)
                val preview = container.backup.preview(zip)
                _state.update {
                    it.copy(
                        passphrasePrompt = null,
                        restoreCandidate = RestoreCandidate(prompt.sourceLabel, zip, preview),
                    )
                }
            } catch (e: BackupCrypto.WrongPassphraseException) {
                _state.update { it.copy(passphrasePrompt = prompt.copy(error = "wrong passphrase.")) }
            } catch (e: BackupCrypto.BadArchiveException) {
                _state.update { it.copy(passphrasePrompt = null, error = e.message) }
            }
        }
    }

    fun confirmRestore() {
        val candidate = _state.value.restoreCandidate ?: return
        run("restoring…") {
            container.backup.restore(candidate.zipBytes)
            _state.update { it.copy(restoreCandidate = null, restored = true) }
        }
    }

    fun dismissRestore() = _state.update { it.copy(passphrasePrompt = null, restoreCandidate = null) }

    // ---- SAF export ----

    fun exportTo(uri: Uri) {
        if (!_state.value.hasPassphrase) {
            _state.update { it.copy(error = "set a backup passphrase first.") }
            return
        }
        run("exporting…") {
            container.backup.exportTo(uri)
            _state.update { it.copy(notice = "archive exported.") }
        }
    }

    fun exportName(): String = container.backup.archiveName()

    fun dismissMessage() = _state.update { it.copy(error = null, notice = null) }

    // ---- plumbing ----

    /** runs [action] with a fresh access token, routing through consent when Google asks */
    private fun withToken(busyLabel: String?, silent: Boolean = false, action: suspend (token: String) -> Unit) {
        viewModelScope.launch {
            busyLabel?.let { label -> _state.update { it.copy(busy = label, error = null) } }
            when (val auth = runCatching { container.driveAuth.authorize() }.getOrElse { e ->
                _state.update { it.copy(busy = null, error = if (silent) null else e.message ?: "couldn't reach google.") }
                return@launch
            }) {
                is DriveAuth.State.Ready -> {
                    runCatching { action(auth.accessToken) }
                        .onFailure { e -> _state.update { s -> s.copy(error = e.message ?: "something failed") } }
                    _state.update { it.copy(busy = null) }
                }
                is DriveAuth.State.NeedsConsent -> {
                    if (silent) {
                        _state.update { it.copy(busy = null, needsReconnect = true) }
                    } else {
                        afterConsent = action
                        _consent.value = auth.pendingIntent
                    }
                }
            }
        }
    }

    fun consentLaunched() {
        _consent.value = null
    }

    private fun run(busyLabel: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = busyLabel, error = null) }
            runCatching { action() }
                .onFailure { e -> _state.update { s -> s.copy(error = e.message ?: "something failed") } }
            _state.update { it.copy(busy = null) }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { BackupViewModel(container) }
        }
    }
}
