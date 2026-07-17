package com.aisoul.app.vault

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.aisoul.app.providers.ProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Keystore-wrapped provider keys, one per provider. Masked for display. */
class ProviderKeyStore(
    private val dataStore: DataStore<Preferences>,
    private val vault: KeyVault,
) {

    private fun keyFor(provider: ProviderType) = stringPreferencesKey("vault_key_${provider.id}")

    suspend fun setKey(provider: ProviderType, apiKey: String) {
        val wrapped = vault.wrap(apiKey.trim())
        dataStore.edit { it[keyFor(provider)] = wrapped }
    }

    suspend fun getKey(provider: ProviderType): String? =
        dataStore.data.first()[keyFor(provider)]?.let { runCatching { vault.unwrap(it) }.getOrNull() }

    suspend fun deleteKey(provider: ProviderType) {
        dataStore.edit { it.remove(keyFor(provider)) }
    }

    fun hasKey(provider: ProviderType): Flow<Boolean> =
        dataStore.data.map { it.contains(keyFor(provider)) }

    /** e.g. "sk-…f3a2" — never the full key, never logged. */
    suspend fun maskedKey(provider: ProviderType): String? {
        val key = getKey(provider) ?: return null
        if (key.length < 8) return "…"
        return "${key.take(3)}…${key.takeLast(4)}"
    }
}
