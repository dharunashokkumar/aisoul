package com.aisoul.app.ui.chat

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.aisoul.app.ui.theme.Surface2

/**
 * D-028 — just enough markdown for chat, hand-rolled: headings, lists,
 * tables, rules, quotes, fenced + inline code, bold/italic/strike, and a
 * light latex→unicode pass for `$…$` spans. Still no library; quiet text
 * is the design.
 */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: AnnotatedString) : MdBlock
    data class Paragraph(val text: AnnotatedString) : MdBlock
    data class Bullets(val items: List<AnnotatedString>, val ordered: Boolean) : MdBlock
    data class Table(val header: List<AnnotatedString>, val rows: List<List<AnnotatedString>>) : MdBlock
    data class Quote(val text: AnnotatedString) : MdBlock
    data class Code(val language: String?, val code: String) : MdBlock
    data object Rule : MdBlock
}

// Android's ICU regex rejects unescaped `}`/`]` — always escape closers here.
private val headingLine = Regex("^(#{1,6})\\s+(.*)$")
private val ruleLine = Regex("^\\s{0,3}(-{3,}|\\*{3,}|_{3,})\\s*$")
private val bulletLine = Regex("^\\s{0,6}[-*•]\\s+(.*)$")
private val orderedLine = Regex("^\\s{0,6}\\d{1,3}[.)]\\s+(.*)$")
private val tableSeparatorCell = Regex("^:?-{2,}:?$")

fun parseMarkdownLite(text: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()
    val code = StringBuilder()
    var codeLanguage: String? = null
    var inCode = false
    val bulletItems = mutableListOf<AnnotatedString>()
    var bulletsOrdered = false
    val tableLines = mutableListOf<String>()
    val quote = StringBuilder()

    fun flushParagraph() {
        val content = paragraph.toString().trim()
        if (content.isNotEmpty()) blocks += MdBlock.Paragraph(annotateInline(content))
        paragraph.setLength(0)
    }

    fun flushBullets() {
        if (bulletItems.isNotEmpty()) {
            blocks += MdBlock.Bullets(bulletItems.toList(), bulletsOrdered)
            bulletItems.clear()
        }
    }

    fun flushTable() {
        if (tableLines.isEmpty()) return
        val parsed = parseTable(tableLines)
        if (parsed != null) blocks += parsed
        else tableLines.forEach { line -> blocks += MdBlock.Paragraph(annotateInline(line)) }
        tableLines.clear()
    }

    fun flushQuote() {
        val content = quote.toString().trim()
        if (content.isNotEmpty()) blocks += MdBlock.Quote(annotateInline(content))
        quote.setLength(0)
    }

    fun flushAll() {
        flushParagraph(); flushBullets(); flushTable(); flushQuote()
    }

    text.lines().forEach { line ->
        val trimmed = line.trim()
        when {
            trimmed.startsWith("```") -> {
                if (inCode) {
                    blocks += MdBlock.Code(codeLanguage, code.toString().trimEnd('\n'))
                    code.setLength(0)
                    inCode = false
                } else {
                    flushAll()
                    codeLanguage = trimmed.removePrefix("```").trim().ifEmpty { null }
                    inCode = true
                }
            }
            inCode -> code.append(line).append('\n')
            line.isBlank() -> flushAll()
            headingLine.matches(trimmed) -> {
                flushAll()
                val match = headingLine.find(trimmed)!!
                blocks += MdBlock.Heading(match.groupValues[1].length, annotateInline(match.groupValues[2]))
            }
            ruleLine.matches(line) -> {
                flushAll()
                blocks += MdBlock.Rule
            }
            trimmed.startsWith("|") -> {
                flushParagraph(); flushBullets(); flushQuote()
                tableLines += trimmed
            }
            bulletLine.matches(line) -> {
                flushParagraph(); flushTable(); flushQuote()
                if (bulletItems.isNotEmpty() && bulletsOrdered) flushBullets()
                bulletsOrdered = false
                bulletItems += annotateInline(bulletLine.find(line)!!.groupValues[1])
            }
            orderedLine.matches(line) -> {
                flushParagraph(); flushTable(); flushQuote()
                if (bulletItems.isNotEmpty() && !bulletsOrdered) flushBullets()
                bulletsOrdered = true
                bulletItems += annotateInline(orderedLine.find(line)!!.groupValues[1])
            }
            trimmed.startsWith(">") -> {
                flushParagraph(); flushBullets(); flushTable()
                if (quote.isNotEmpty()) quote.append('\n')
                quote.append(trimmed.removePrefix(">").trim())
            }
            else -> {
                flushBullets(); flushTable(); flushQuote()
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(line)
            }
        }
    }
    if (inCode) blocks += MdBlock.Code(codeLanguage, code.toString().trimEnd('\n'))
    flushAll()
    return blocks
}

private fun parseTable(lines: List<String>): MdBlock.Table? {
    fun cells(row: String): List<String> =
        row.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }

    if (lines.isEmpty()) return null
    val separatorIndex = lines.indexOfFirst { line ->
        val cs = cells(line)
        cs.isNotEmpty() && cs.all { tableSeparatorCell.matches(it.replace(" ", "")) }
    }
    val header: List<AnnotatedString>
    val rowLines: List<String>
    if (separatorIndex == 1) {
        header = cells(lines[0]).map(::annotateInline)
        rowLines = lines.drop(2)
    } else {
        header = emptyList()
        rowLines = lines.filterIndexed { index, _ -> index != separatorIndex }
    }
    val rows = rowLines.map { line -> cells(line).map(::annotateInline) }
    if (header.isEmpty() && rows.isEmpty()) return null
    return MdBlock.Table(header, rows)
}

// ---- inline spans ----

private val inlinePattern = Regex(
    "(`(?<code>[^`\\n]+)`)" +
        "|(\\*\\*(?<bold>[^*\\n]+)\\*\\*)" +
        "|(\\*(?<italic>[^*\\n]+)\\*)" +
        "|(~~(?<strike>[^~\\n]+)~~)",
)

private fun annotateInline(raw: String): AnnotatedString {
    val text = mathToUnicode(raw)
    return buildAnnotatedString {
        var index = 0
        inlinePattern.findAll(text).forEach { match ->
            if (match.range.first > index) append(text.substring(index, match.range.first))
            val code = match.groups["code"]?.value
            val bold = match.groups["bold"]?.value
            val italic = match.groups["italic"]?.value
            val strike = match.groups["strike"]?.value
            when {
                code != null -> withStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace, background = Surface2),
                ) { append(code) }
                bold != null -> withStyle(SpanStyle(fontWeight = FontWeight(600))) { append(bold) }
                italic != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(italic) }
                strike != null -> withStyle(
                    SpanStyle(textDecoration = TextDecoration.LineThrough),
                ) { append(strike) }
            }
            index = match.range.last + 1
        }
        if (index < text.length) append(text.substring(index))
    }
}

private inline fun androidx.compose.ui.text.AnnotatedString.Builder.withStyle(
    style: SpanStyle,
    block: androidx.compose.ui.text.AnnotatedString.Builder.() -> Unit,
) {
    val start = length
    block()
    addStyle(style, start, length)
}

// ---- latex → unicode (D-028): a safety net for models that emit $…$ ----

private val mathSpan = Regex("\\$\\$?([^$\\n]+?)\\$\\$?|\\\\\\(([^\\n]*?)\\\\\\)")
private val sqrtCmd = Regex("\\\\sqrt\\{([^{}]*)\\}")
private val fracCmd = Regex("\\\\frac\\{([^{}]*)\\}\\{([^{}]*)\\}")
private val supBraced = Regex("\\^\\{([0-9n+\\-]+)\\}")
private val supSingle = Regex("\\^([0-9n])")
private val subBraced = Regex("_\\{([0-9]+)\\}")
private val subSingle = Regex("_([0-9])")
private val leftoverCmd = Regex("\\\\([a-zA-Z]+)")

private val symbolCommands = listOf(
    "sqrt" to "√", "pi" to "π", "theta" to "θ", "alpha" to "α", "beta" to "β",
    "gamma" to "γ", "lambda" to "λ", "mu" to "μ", "sigma" to "σ", "omega" to "ω",
    "Delta" to "Δ", "Sigma" to "Σ", "times" to "×", "cdot" to "·", "div" to "÷",
    "pm" to "±", "leq" to "≤", "le" to "≤", "geq" to "≥", "ge" to "≥",
    "neq" to "≠", "ne" to "≠", "approx" to "≈", "infty" to "∞", "degree" to "°",
    "rightarrow" to "→", "to" to "→", "left" to "", "right" to "",
)

private const val SUPERSCRIPTS = "⁰¹²³⁴⁵⁶⁷⁸⁹"
private const val SUBSCRIPTS = "₀₁₂₃₄₅₆₇₈₉"

private fun superscript(chars: String): String = chars.map { c ->
    when (c) {
        in '0'..'9' -> SUPERSCRIPTS[c - '0']
        'n' -> 'ⁿ'
        '+' -> '⁺'
        '-' -> '⁻'
        else -> c
    }
}.joinToString("")

private fun subscript(chars: String): String =
    chars.map { c -> if (c in '0'..'9') SUBSCRIPTS[c - '0'] else c }.joinToString("")

/** visible for tests */
internal fun latexToUnicode(inner: String): String {
    var s = inner
    s = sqrtCmd.replace(s) { m -> "√(${m.groupValues[1]})" }
    s = fracCmd.replace(s) { m -> "${m.groupValues[1]}/${m.groupValues[2]}" }
    symbolCommands.forEach { (cmd, symbol) -> s = s.replace("\\$cmd", symbol) }
    s = s.replace("^\\circ", "°").replace("^{\\circ}", "°")
    s = supBraced.replace(s) { m -> superscript(m.groupValues[1]) }
    s = supSingle.replace(s) { m -> superscript(m.groupValues[1]) }
    s = subBraced.replace(s) { m -> subscript(m.groupValues[1]) }
    s = subSingle.replace(s) { m -> subscript(m.groupValues[1]) }
    s = leftoverCmd.replace(s) { m -> m.groupValues[1] }
    return s.replace("{", "").replace("}", "").trim()
}

/** visible for tests */
internal fun mathToUnicode(text: String): String {
    if ('$' !in text && "\\(" !in text) return text
    return mathSpan.replace(text) { match ->
        val inner = match.groupValues[1].ifEmpty { match.groupValues[2] }
        latexToUnicode(inner)
    }
}
