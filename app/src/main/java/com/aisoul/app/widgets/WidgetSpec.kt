package com.aisoul.app.widgets

import kotlinx.serialization.Serializable

/**
 * SPEC §8 — the widget DSL. Declarative JSON, never code. Decoded with
 * ignoreUnknownKeys = false: an unknown key is an invalid spec is a quiet
 * error card, never a partial render.
 */
@Serializable
data class WidgetSpec(
    val schema: Int = 1,
    val id: String,
    val title: String,
    val icon: String? = null,
    val size: String = "medium", // small | medium | large
    val refresh: RefreshSpec = RefreshSpec(),
    val sources: Map<String, SourceSpec> = emptyMap(),
    val body: List<ComponentSpec>,
    /** whole-card tap action (D-022) — the talk widget is a door */
    val tap: ActionSpec? = null,
)

@Serializable
data class RefreshSpec(
    val on_open: Boolean = true,
    /** 0 = never in background; else 15..1440 (WorkManager floor) */
    val interval_min: Int = 0,
)

@Serializable
data class SourceSpec(
    val type: String, // static | http | tool | file | countdown | memory
    val method: String = "GET",
    val url: String? = null,
    val command: String? = null,
    val path: String? = null,
    val value: String? = null,
    val date: String? = null, // countdown target, yyyy-mm-dd
    val extract: String? = null, // $.a.b[0] | regex:(...) | lines:1-3
)

@Serializable
data class ComponentSpec(
    val type: String, // text | stat | list | progress | sparkline | buttons | divider
    val text: String? = null,
    val style: String? = null, // text: title | body | caption
    val label: String? = null,
    val value: String? = null,
    val ok_when: String? = null,
    val items_from: String? = null,
    val empty: String? = null,
    val source: String? = null, // sparkline
    val max: String? = null, // progress denominator
    val items: List<ButtonSpec> = emptyList(),
)

@Serializable
data class ButtonSpec(val label: String, val action: ActionSpec)

@Serializable
data class ActionSpec(
    val type: String, // chat | run | url | refresh | screen
    val prompt: String? = null,
    val command: String? = null,
    val url: String? = null,
    val screen: String? = null, // chat | memory | files | terminal
)

/** The frozen capability set — the ONLY things a widget may ever execute. */
data class WidgetCapabilities(
    val urls: List<String>,
    val commands: List<String>,
    val paths: List<String>,
) {
    /** plain-language lines for the approval sheet (SPEC §8) */
    fun summaryLines(refresh: RefreshSpec): List<String> = buildList {
        val cadence = when {
            refresh.interval_min > 0 -> "every ${refresh.interval_min} min"
            refresh.on_open -> "when the dashboard opens"
            else -> "only when tapped"
        }
        urls.forEach { add("fetch $it $cadence") }
        commands.forEach { add("run `$it` $cadence") }
        paths.forEach { add("read $it") }
        if (isEmpty()) add("nothing — static content only")
    }
}
