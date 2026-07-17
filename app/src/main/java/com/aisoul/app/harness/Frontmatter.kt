package com.aisoul.app.harness

/**
 * IMPLEMENTATION §7 — simple YAML subset: `key: value` lines between `---`
 * fences, no nesting. That is all memory files need.
 */
object Frontmatter {

    data class Parsed(val fields: Map<String, String>, val body: String)

    fun parse(text: String): Parsed {
        val lines = text.lines()
        if (lines.firstOrNull()?.trim() != "---") return Parsed(emptyMap(), text)
        val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
        if (end < 0) return Parsed(emptyMap(), text)
        val fields = lines.subList(1, end + 1)
            .mapNotNull { line ->
                val split = line.indexOf(':')
                if (split <= 0) null
                else line.take(split).trim() to line.drop(split + 1).trim().removeSurrounding("\"")
            }
            .toMap()
        val body = lines.drop(end + 2).joinToString("\n").trim('\n')
        return Parsed(fields, body)
    }

    fun serialize(fields: Map<String, String>, body: String): String = buildString {
        append("---\n")
        fields.forEach { (key, value) -> append("$key: $value\n") }
        append("---\n\n")
        append(body.trimEnd())
        append("\n")
    }
}
