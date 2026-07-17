package com.aisoul.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.glance.appwidget.updateAll
import com.aisoul.app.di.AppContainer
import com.aisoul.app.widgets.ActionSpec
import com.aisoul.app.widgets.ButtonSpec
import com.aisoul.app.widgets.ComponentSpec
import com.aisoul.app.widgets.RefreshSpec
import com.aisoul.app.widgets.SourceSpec
import com.aisoul.app.widgets.WidgetSpec
import com.aisoul.app.widgets.WidgetStore
import com.aisoul.app.widgets.launcher.AiSoulGlanceWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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

    /** D-030 — AI-proposed specs waiting for a human decision */
    private val _proposals = MutableStateFlow<List<WidgetStore.Proposal>>(emptyList())
    val proposals: StateFlow<List<WidgetStore.Proposal>> = _proposals

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
            _proposals.value = runCatching { container.widgets.listProposals() }.getOrElse { emptyList() }
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

    fun approveProposal(id: String) {
        viewModelScope.launch {
            container.widgets.approveProposal(id)
            load()
        }
    }

    fun dismissProposal(id: String) {
        viewModelScope.launch {
            container.widgets.dismissProposal(id)
            _proposals.update { list -> list.filterNot { it.id == id } }
        }
    }

    // ---- D-031: the add-widget gallery — golden specs the user fills in ----

    /** returns an error line, or null and installs */
    fun addCountdown(title: String, date: String): String? {
        val cleanTitle = title.trim().lowercase()
        if (cleanTitle.isEmpty()) return "give it a title"
        if (cleanTitle.length > 40) return "keep the title under 40 chars"
        val cleanDate = date.trim()
        runCatching { LocalDate.parse(cleanDate, DateTimeFormatter.ISO_LOCAL_DATE) }
            .getOrElse { return "date must be yyyy-mm-dd" }
        val spec = WidgetSpec(
            id = slugOf(cleanTitle, fallback = "countdown"),
            title = cleanTitle,
            size = "small",
            refresh = RefreshSpec(on_open = true, interval_min = 0),
            sources = mapOf("left" to SourceSpec(type = "countdown", date = cleanDate)),
            body = listOf(ComponentSpec(type = "stat", label = cleanTitle, value = "{left}")),
        )
        install(spec)
        return null
    }

    /** returns an error line, or null and installs */
    fun addHabit(name: String): String? {
        val clean = name.trim().lowercase()
        if (clean.isEmpty()) return "name the habit"
        if (clean.length > 40) return "keep the name under 40 chars"
        val id = slugOf(clean, fallback = "habit")
        val logPath = "notes/habit-$id.md"
        val spec = WidgetSpec(
            id = id,
            title = clean,
            size = "medium",
            refresh = RefreshSpec(on_open = true, interval_min = 0),
            sources = mapOf("log" to SourceSpec(type = "file", path = logPath, extract = "lines:1-5")),
            body = listOf(
                ComponentSpec(
                    type = "list",
                    items_from = "log",
                    empty = "not logged yet — tap log when it's done.",
                ),
                ComponentSpec(
                    type = "buttons",
                    items = listOf(
                        ButtonSpec(
                            label = "log today",
                            action = ActionSpec(
                                type = "chat",
                                prompt = "i just did \"$clean\". add a line \"<today's date> — done\" at the top of $logPath, keeping older lines below, then give me one short line of acknowledgment.",
                            ),
                        ),
                    ),
                ),
            ),
        )
        install(spec)
        return null
    }

    private fun install(spec: WidgetSpec) {
        viewModelScope.launch {
            // tapping "add" IS the approval — same freeze as any other install
            container.widgets.installApproved(spec)
            load()
        }
    }

    private fun slugOf(text: String, fallback: String): String =
        text.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(40)
            .ifEmpty { fallback }

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
        // D-033 — keep any home-screen widget in step with the dashboard
        runCatching { AiSoulGlanceWidget().updateAll(container.appContext) }
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
