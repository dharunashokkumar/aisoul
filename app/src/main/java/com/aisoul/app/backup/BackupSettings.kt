package com.aisoul.app.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.aisoul.app.vault.KeyVault
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * SPEC §10 backup settings. The passphrase is a secret like a provider key:
 * Keystore-wrapped into the vault DataStore (background backups need it),
 * never in any archive, never logged. Everything else is plain preferences.
 */
class BackupSettings(
    private val prefs: DataStore<Preferences>,
    private val vaultPrefs: DataStore<Preferences>,
    private val vault: KeyVault,
) {

    private companion object {
        val DRIVE_ENABLED = booleanPreferencesKey("backup_drive_enabled")
        val WIFI_ONLY = booleanPreferencesKey("backup_wifi_only")
        val ACCOUNT_EMAIL = stringPreferencesKey("backup_account_email")
        val LAST_BACKUP_AT = longPreferencesKey("backup_last_at")
        val LAST_RESULT = stringPreferencesKey("backup_last_result")
        val NUDGE_DISMISSED = booleanPreferencesKey("backup_nudge_dismissed")
        val PASSPHRASE = stringPreferencesKey("vault_backup_passphrase")
    }

    val driveEnabled: Flow<Boolean> = prefs.data.map { it[DRIVE_ENABLED] ?: false }
    val wifiOnly: Flow<Boolean> = prefs.data.map { it[WIFI_ONLY] ?: true }
    val accountEmail: Flow<String?> = prefs.data.map { it[ACCOUNT_EMAIL] }
    val lastBackupAt: Flow<Long> = prefs.data.map { it[LAST_BACKUP_AT] ?: 0L }
    val lastResult: Flow<String?> = prefs.data.map { it[LAST_RESULT] }
    val nudgeDismissed: Flow<Boolean> = prefs.data.map { it[NUDGE_DISMISSED] ?: false }

    suspend fun setDriveEnabled(enabled: Boolean) {
        prefs.edit {
            it[DRIVE_ENABLED] = enabled
            if (!enabled) it.remove(ACCOUNT_EMAIL)
        }
    }

    suspend fun setWifiOnly(wifiOnly: Boolean) {
        prefs.edit { it[WIFI_ONLY] = wifiOnly }
    }

    suspend fun setAccountEmail(email: String?) {
        prefs.edit { preferences ->
            email?.let { preferences[ACCOUNT_EMAIL] = it } ?: preferences.remove(ACCOUNT_EMAIL)
        }
    }

    suspend fun recordBackup(result: String, succeeded: Boolean) {
        prefs.edit {
            if (succeeded) it[LAST_BACKUP_AT] = System.currentTimeMillis()
            it[LAST_RESULT] = result
        }
    }

    suspend fun dismissNudge() {
        prefs.edit { it[NUDGE_DISMISSED] = true }
    }

    suspend fun setPassphrase(passphrase: String) {
        val wrapped = vault.wrap(passphrase)
        vaultPrefs.edit { it[PASSPHRASE] = wrapped }
    }

    suspend fun passphrase(): String? =
        vaultPrefs.data.first()[PASSPHRASE]?.let { runCatching { vault.unwrap(it) }.getOrNull() }

    val hasPassphrase: Flow<Boolean> = vaultPrefs.data.map { it.contains(PASSPHRASE) }
}
