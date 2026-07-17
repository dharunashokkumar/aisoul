package com.aisoul.app.ui.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aisoul.app.di.AppContainer
import com.aisoul.app.harness.Memory
import com.aisoul.app.harness.PendingDelete
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MemoryUiState(
    val memories: List<Memory> = emptyList(),
    val pending: List<PendingDelete> = emptyList(),
    val loaded: Boolean = false,
)

/** SPEC §3 — the user can always see exactly what the AI knows. */
class MemoryViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(MemoryUiState())
    val state: StateFlow<MemoryUiState> = _state

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    memories = container.memories.list(),
                    pending = container.memories.pendingDeletes(),
                    loaded = true,
                )
            }
        }
    }

    fun forget(slug: String) {
        viewModelScope.launch {
            container.memories.delete(slug)
            reload()
        }
    }

    fun keepPending(slug: String) {
        viewModelScope.launch {
            container.memories.removePending(slug)
            reload()
        }
    }

    fun forgetEverything() {
        viewModelScope.launch {
            container.memories.deleteAll()
            reload()
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { MemoryViewModel(container) }
        }
    }
}
