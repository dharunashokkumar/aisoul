package com.aisoul.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aisoul.app.di.AppContainer
import com.aisoul.app.widgets.WidgetStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WidgetUi(
    val installed: WidgetStore.Installed,
    val values: Map<String, String> = emptyMap(),
    val histories: Map<String, List<Double>> = emptyMap(),
    val lastRefreshedAt: Long? = null,
)

/**
 * SPEC §8 — the dashboard is the app's home. Renders last-known values
 * instantly, then runs on-open refreshes and updates in place.
 */
class DashboardViewModel(private val container: AppContainer) : ViewModel() {

    private val _widgets = MutableStateFlow<List<WidgetUi>?>(null)
    val widgets: StateFlow<List<WidgetUi>?> = _widgets

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            // a broken widget subsystem must degrade to an empty dashboard, never kill the app
            val installed = runCatching { container.widgets.list() }.getOrElse { emptyList() }
            _widgets.value = installed.map { widget ->
                val cached = widget.spec?.let { container.widgets.readValues(widget.id) }
                WidgetUi(
                    installed = widget,
                    values = cached?.values ?: emptyMap(),
                    histories = histories(widget),
                    lastRefreshedAt = cached?.at,
                )
            }
            installed
                .filter { it.state == WidgetStore.State.ACTIVE }
                .filter { it.spec?.refresh?.on_open == true && it.spec.sources.isNotEmpty() }
                .forEach { widget -> launch { refreshNow(widget) } }
        }
    }

    fun refresh(id: String) {
        viewModelScope.launch {
            _widgets.value?.firstOrNull { it.installed.id == id }?.let { refreshNow(it.installed) }
        }
    }

    fun approveCurrent(id: String) {
        viewModelScope.launch {
            container.widgets.approveCurrent(id)
            load()
        }
    }

    fun remove(id: String) {
        viewModelScope.launch {
            container.widgets.remove(id)
            load()
        }
    }

    /** widget birth happened — never repeat the theater */
    fun markBorn(id: String) {
        viewModelScope.launch { container.widgets.markBorn(id) }
    }

    private suspend fun refreshNow(installed: WidgetStore.Installed) {
        val values = runCatching {
            container.widgetEngine.refresh(installed, container.widgets)
        }.getOrNull() ?: return
        val newHistories = histories(installed)
        _widgets.update { list ->
            list?.map { ui ->
                if (ui.installed.id == installed.id) {
                    ui.copy(values = values, histories = newHistories, lastRefreshedAt = System.currentTimeMillis())
                } else ui
            }
        }
    }

    private suspend fun histories(widget: WidgetStore.Installed): Map<String, List<Double>> =
        widget.spec?.body
            ?.filter { it.type == "sparkline" }
            ?.mapNotNull { it.source }
            ?.distinct()
            ?.associateWith { source -> container.widgets.readHistory(widget.id, source) }
            ?: emptyMap()

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { DashboardViewModel(container) }
        }
    }
}
