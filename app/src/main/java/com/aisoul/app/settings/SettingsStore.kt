package com.aisoul.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.aisoul.app.providers.ProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsStore(private val dataStore: DataStore<Preferences>) {

    private companion object {
        val SELECTED_PROVIDER = stringPreferencesKey("selected_provider")
        val COMPAT_BASE_URL = stringPreferencesKey("compat_base_url")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val DISTILL_COUNT = intPreferencesKey("distill_count")
        fun modelKey(provider: ProviderType) = stringPreferencesKey("model_${provider.id}")
        fun distillModelKey(provider: ProviderType) = stringPreferencesKey("distill_model_${provider.id}")
    }

    val selectedProvider: Flow<ProviderType> =
        dataStore.data.map { ProviderType.fromId(it[SELECTED_PROVIDER]) }

    suspend fun setSelectedProvider(provider: ProviderType) {
        dataStore.edit { it[SELECTED_PROVIDER] = provider.id }
    }

    fun modelFor(provider: ProviderType): Flow<String> =
        dataStore.data.map { it[modelKey(provider)]?.takeIf(String::isNotBlank) ?: provider.defaultModel }

    suspend fun setModel(provider: ProviderType, model: String) {
        dataStore.edit { it[modelKey(provider)] = model.trim() }
    }

    val compatBaseUrl: Flow<String> =
        dataStore.data.map { it[COMPAT_BASE_URL] ?: "" }

    suspend fun setCompatBaseUrl(url: String) {
        dataStore.edit { it[COMPAT_BASE_URL] = url.trim().trimEnd('/') }
    }

    val onboarded: Flow<Boolean> =
        dataStore.data.map { it[ONBOARDED] ?: false }

    suspend fun setOnboarded() {
        dataStore.edit { it[ONBOARDED] = true }
    }

    /** counts distills since the last META pass (D-020); returns the new count */
    suspend fun bumpDistillCount(): Int {
        var count = 0
        dataStore.edit {
            count = (it[DISTILL_COUNT] ?: 0) + 1
            it[DISTILL_COUNT] = count
        }
        return count
    }

    suspend fun resetDistillCount() {
        dataStore.edit { it[DISTILL_COUNT] = 0 }
    }

    /** distill model: user-set, else provider default, else the chat model */
    fun distillModelFor(provider: ProviderType): Flow<String> =
        dataStore.data.map {
            it[distillModelKey(provider)]?.takeIf(String::isNotBlank)
                ?: provider.defaultDistillModel.ifBlank {
                    it[modelKey(provider)]?.takeIf(String::isNotBlank) ?: provider.defaultModel
                }
        }
}
