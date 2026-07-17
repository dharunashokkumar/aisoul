package com.aisoul.app.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aisoul.app.di.AppContainer
import com.aisoul.app.providers.ProviderType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SetupUiState(
    val provider: ProviderType = ProviderType.ANTHROPIC,
    val apiKey: String = "",
    val model: String = "",
    val baseUrl: String = "",
    val validating: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
    val hasStoredKey: Boolean = false,
)

class SetupViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state

    init {
        viewModelScope.launch {
            val provider = container.settings.selectedProvider.first()
            _state.update {
                it.copy(
                    provider = provider,
                    model = container.settings.modelFor(provider).first(),
                    baseUrl = container.settings.compatBaseUrl.first(),
                    hasStoredKey = container.keys.getKey(provider) != null,
                )
            }
        }
    }

    fun selectProvider(provider: ProviderType) {
        _state.update { it.copy(provider = provider, error = null) }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    model = container.settings.modelFor(provider).first(),
                    hasStoredKey = container.keys.getKey(provider) != null,
                )
            }
        }
    }

    fun setApiKey(value: String) = _state.update { it.copy(apiKey = value, error = null) }
    fun setModel(value: String) = _state.update { it.copy(model = value, error = null) }
    fun setBaseUrl(value: String) = _state.update { it.copy(baseUrl = value, error = null) }

    fun validateAndSave() {
        val s = _state.value
        if (s.validating) return

        val isCompat = s.provider == ProviderType.OPENAI_COMPAT
        if (isCompat && s.baseUrl.isBlank()) {
            _state.update { it.copy(error = "a base url is needed — e.g. https://openrouter.ai/api/v1") }
            return
        }
        if (isCompat && s.model.isBlank()) {
            _state.update { it.copy(error = "a model id is needed for custom servers") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(validating = true, error = null) }
            // a blank key falls back to the stored one (editing model only)
            val key = s.apiKey.trim().ifBlank { container.keys.getKey(s.provider) ?: "" }
            if (key.isBlank() && !isCompat) {
                _state.update { it.copy(validating = false, error = "paste your api key first") }
                return@launch
            }
            val baseUrl = if (isCompat) s.baseUrl.trim().trimEnd('/') else null
            val client = container.providerFactory.create(s.provider, key, baseUrl)
            val result = client.validateKey()
            result.fold(
                onSuccess = {
                    if (key.isNotBlank()) container.keys.setKey(s.provider, key)
                    container.settings.setSelectedProvider(s.provider)
                    container.settings.setModel(s.provider, s.model.ifBlank { s.provider.defaultModel })
                    if (isCompat) container.settings.setCompatBaseUrl(s.baseUrl)
                    _state.update { it.copy(validating = false, done = true) }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            validating = false,
                            error = e.message ?: "couldn't reach ${s.provider.display}. check your connection.",
                        )
                    }
                },
            )
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SetupViewModel(container) }
        }
    }
}
