package com.aisoul.app.ui.chat

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.aisoul.app.ui.theme.Surface2

/**
 * Just enough markdown for chat: fenced code blocks, inline code, bold.
 * A full renderer is deliberately out of scope — quiet text is the design.
 */
sealed interface MdBlock {
    data class Paragraph(val text: AnnotatedString) : MdBlock
    data class Code(val language: String?, val code: String) : MdBlock
}

fun parseMarkdownLite(text: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = text.lines()
    val paragraph = StringBuilder()
    var codeLanguage: String? = null
    val code = StringBuilder()
    var inCode = false

    fun flushParagraph() {
        val content = paragraph.toString().trim()
        if (content.isNotEmpty()) blocks += MdBlock.Paragraph(annotateInline(content))
        paragraph.setLength(0)
    }

    lines.forEach { line ->
        when {
            line.trimStart().startsWith("```") -> {
                if (inCode) {
                    blocks += MdBlock.Code(codeLanguage, code.toString().trimEnd('\n'))
                    code.setLength(0)
                    inCode = false
                } else {
                    flushParagraph()
                    codeLanguage = line.trim().removePrefix("```").trim().ifEmpty { null }
                    inCode = true
                }
            }
            inCode -> code.append(line).append('\n')
            line.isBlank() -> flushParagraph()
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(line)
            }
        }
    }
    if (inCode) blocks += MdBlock.Code(codeLanguage, code.toString().trimEnd('\n'))
    flushParagraph()
    return blocks
}

private val inlinePattern = Regex("(\\*\\*(?<bold>[^*]+)\\*\\*)|(`(?<code>[^`]+)`)")

private fun annotateInline(text: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    inlinePattern.findAll(text).forEach { match ->
        if (match.range.first > index) append(text.substring(index, match.range.first))
        val bold = match.groups["bold"]?.value
        val code = match.groups["code"]?.value
        when {
            bold != null -> withStyle(SpanStyle(fontWeight = FontWeight(600))) { append(bold) }
            code != null -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = Surface2),
            ) { append(code) }
        }
        index = match.range.last + 1
    }
    if (index < text.length) append(text.substring(index))
}

private inline fun androidx.compose.ui.text.AnnotatedString.Builder.withStyle(
    style: SpanStyle,
    block: androidx.compose.ui.text.AnnotatedString.Builder.() -> Unit,
) {
    val start = length
    block()
    addStyle(style, start, length)
}
