package com.aisoul.app.widgets

import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * SPEC §8 security model — schema-validated on parse; anything off refuses to
 * render. Also extracts the capability set frozen at approval.
 */
object WidgetValidator {

    sealed interface Result {
        data class Valid(val spec: WidgetSpec, val capabilities: WidgetCapabilities) : Result
        data class Invalid(val problems: List<String>) : Result
    }

    private val strict = Json { ignoreUnknownKeys = false }

    private val SIZES = setOf("small", "medium", "large")
    private val SOURCE_TYPES = setOf("static", "http", "tool", "file", "countdown", "memory")
    private val COMPONENT_TYPES = setOf("text", "stat", "list", "progress", "sparkline", "buttons", "divider")
    private val ACTION_TYPES = setOf("chat", "run", "url", "refresh", "screen")
    private val SCREENS = setOf("chat", "memory", "files", "terminal")
    private val TEXT_STYLES = setOf("title", "body", "caption")
    private val ID_PATTERN = Regex("^[a-z0-9][a-z0-9-]{0,63}$")
    private val SOURCE_NAME = Regex("^[a-z0-9_]{1,24}$")
    // Android's ICU regex rejects unescaped closing braces/brackets that the
    // desktop JVM tolerates — keep them escaped or the class fails to load.
    private val TEMPLATE = Regex("\\{([a-z0-9_]+)\\}")
    private val DATE_PATTERN = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    fun validate(rawJson: String): Result {
        val spec = runCatching { strict.decodeFromString(WidgetSpec.serializer(), rawJson) }
            .getOrElse { e ->
                return Result.Invalid(listOf("does not parse as a widget spec: ${e.message?.take(160)}"))
            }
        return validate(spec)
    }

    fun validate(spec: WidgetSpec): Result {
        val problems = mutableListOf<String>()
        val urls = mutableListOf<String>()
        val commands = mutableListOf<String>()
        val paths = mutableListOf<String>()

        if (spec.schema != 1) problems += "unknown schema version ${spec.schema}"
        if (!ID_PATTERN.matches(spec.id)) problems += "id must be a kebab-case slug"
        if (spec.title.isBlank() || spec.title.length > 40) problems += "title must be 1–40 chars"
        if (spec.size !in SIZES) problems += "size must be small|medium|large"
        if (spec.refresh.interval_min != 0 && spec.refresh.interval_min !in 15..1440) {
            problems += "interval_min must be 0 or 15–1440"
        }
        if (spec.sources.size > 6) problems += "at most 6 sources"
        if (spec.body.isEmpty() || spec.body.size > 10) problems += "body needs 1–10 components"

        spec.sources.forEach { (name, source) ->
            if (!SOURCE_NAME.matches(name)) problems += "source name '$name' invalid"
            when (source.type) {
                "static" -> if (source.value == null) problems += "source '$name': static needs value"
                "http" -> {
                    val url = source.url
                    val parsed = url?.toHttpUrlOrNull()
                    when {
                        url == null -> problems += "source '$name': http needs url"
                        parsed == null || parsed.scheme !in setOf("http", "https") ->
                            problems += "source '$name': url must be http(s)"
                        parsed.username.isNotEmpty() -> problems += "source '$name': no credentials in urls"
                        else -> urls += url
                    }
                    if (source.method !in setOf("GET", "POST")) problems += "source '$name': method GET or POST"
                }
                "tool" -> {
                    val command = source.command?.trim()
                    if (command.isNullOrBlank() || command.length > 200) {
                        problems += "source '$name': tool needs a command (≤200 chars)"
                    } else commands += command
                }
                "file" -> {
                    val path = source.path
                    if (path == null || path.startsWith("/") || path.contains("..") ||
                        path.split('/').any { it.startsWith(".") }
                    ) {
                        problems += "source '$name': file needs a relative harness path"
                    } else paths += path
                }
                "countdown" -> if (source.date == null || !DATE_PATTERN.matches(source.date)) {
                    problems += "source '$name': countdown needs date yyyy-mm-dd"
                }
                "memory" -> Unit
                !in SOURCE_TYPES -> problems += "source '$name': unknown type '${source.type}'"
            }
            source.extract?.let { extract ->
                if (Extractors.validate(extract) != null) {
                    problems += "source '$name': ${Extractors.validate(extract)}"
                }
            }
        }

        fun checkTemplates(text: String?, where: String) {
            text ?: return
            TEMPLATE.findAll(text).forEach { match ->
                val name = match.groupValues[1]
                if (name != "today" && name !in spec.sources.keys) {
                    problems += "$where references unknown source '{$name}'"
                }
            }
        }

        fun checkAction(action: ActionSpec?, where: String) {
            action ?: return
            when (action.type) {
                "chat" -> if (action.prompt != null && action.prompt.length > 500) {
                    problems += "$where: chat prompt too long"
                }
                "run" -> {
                    val command = action.command?.trim()
                    if (command.isNullOrBlank() || command.length > 200) {
                        problems += "$where: run needs a command (≤200 chars)"
                    } else commands += command
                }
                "url" -> {
                    val parsed = action.url?.toHttpUrlOrNull()
                    if (parsed == null || parsed.scheme !in setOf("http", "https")) {
                        problems += "$where: url action must be http(s)"
                    } else urls += action.url
                }
                "refresh" -> Unit
                "screen" -> if (action.screen !in SCREENS) {
                    problems += "$where: screen must be one of $SCREENS"
                }
                !in ACTION_TYPES -> problems += "$where: unknown action '${action.type}'"
            }
        }

        spec.body.forEachIndexed { index, component ->
            val where = "component ${index + 1} (${component.type})"
            when (component.type) {
                "text" -> {
                    if (component.text == null) problems += "$where needs text"
                    if (component.style != null && component.style !in TEXT_STYLES) {
                        problems += "$where: style must be title|body|caption"
                    }
                }
                "stat" -> if (component.label == null || component.value == null) {
                    problems += "$where needs label and value"
                }
                "list" -> if (component.items_from == null || component.items_from !in spec.sources.keys) {
                    problems += "$where: items_from must name a source"
                }
                "progress" -> if (component.value == null) problems += "$where needs value"
                "sparkline" -> if (component.source == null || component.source !in spec.sources.keys) {
                    problems += "$where: source must name a source"
                }
                "buttons" -> {
                    if (component.items.isEmpty() || component.items.size > 3) {
                        problems += "$where: 1–3 buttons"
                    }
                    component.items.forEach { button ->
                        if (button.label.isBlank()) problems += "$where: button needs a label"
                        checkAction(button.action, where)
                    }
                }
                "divider" -> Unit
                !in COMPONENT_TYPES -> problems += "$where: unknown type"
            }
            checkTemplates(component.text, where)
            checkTemplates(component.value, where)
        }

        checkAction(spec.tap, "tap")

        return if (problems.isEmpty()) {
            Result.Valid(spec, WidgetCapabilities(urls.distinct(), commands.distinct(), paths.distinct()))
        } else Result.Invalid(problems)
    }
}
