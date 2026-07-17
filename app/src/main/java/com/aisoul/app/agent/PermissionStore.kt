package com.aisoul.app.agent

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class PermissionMode(val id: String, val display: String, val blurb: String) {
    CAREFUL("careful", "careful", "asks before it touches anything"),
    STANDARD("standard", "standard", "asks for commands, web, and edits"),
    TRUSTED("trusted", "trusted", "asks only for edits and widgets");

    companion object {
        fun fromId(id: String?): PermissionMode = entries.firstOrNull { it.id == id } ?: STANDARD
    }
}

/** SPEC §10 — mode + the revocable "always allow" rule lists. */
class PermissionStore(private val dataStore: DataStore<Preferences>) {

    private companion object {
        val MODE = stringPreferencesKey("permission_mode")
        val HOSTS = stringSetPreferencesKey("allowed_hosts")
        val COMMANDS = stringSetPreferencesKey("allowed_commands")
    }

    val mode: Flow<PermissionMode> = dataStore.data.map { PermissionMode.fromId(it[MODE]) }
    val allowedHosts: Flow<Set<String>> = dataStore.data.map { it[HOSTS] ?: emptySet() }
    val allowedCommands: Flow<Set<String>> = dataStore.data.map { it[COMMANDS] ?: emptySet() }

    suspend fun setMode(mode: PermissionMode) {
        dataStore.edit { it[MODE] = mode.id }
    }

    suspend fun allowHost(host: String) {
        dataStore.edit { it[HOSTS] = (it[HOSTS] ?: emptySet()) + host.lowercase() }
    }

    suspend fun revokeHost(host: String) {
        dataStore.edit { it[HOSTS] = (it[HOSTS] ?: emptySet()) - host }
    }

    suspend fun allowCommand(command: String) {
        dataStore.edit { it[COMMANDS] = (it[COMMANDS] ?: emptySet()) + command.trim() }
    }

    suspend fun revokeCommand(command: String) {
        dataStore.edit { it[COMMANDS] = (it[COMMANDS] ?: emptySet()) - command }
    }
}
