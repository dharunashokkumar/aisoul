package com.aisoul.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownLiteTest {

    @Test
    fun `headings rules lists and paragraphs segment`() {
        val blocks = parseMarkdownLite(
            """
            ## plan

            first paragraph.

            - one
            - two

            ---

            1. alpha
            2. beta
            """.trimIndent(),
        )
        assertEquals(5, blocks.size)
        val heading = blocks[0] as MdBlock.Heading
        assertEquals(2, heading.level)
        assertEquals("plan", heading.text.text)
        assertEquals("first paragraph.", (blocks[1] as MdBlock.Paragraph).text.text)
        val bullets = blocks[2] as MdBlock.Bullets
        assertEquals(listOf("one", "two"), bullets.items.map { it.text })
        assertTrue(!bullets.ordered)
        assertTrue(blocks[3] is MdBlock.Rule)
        val ordered = blocks[4] as MdBlock.Bullets
        assertEquals(listOf("alpha", "beta"), ordered.items.map { it.text })
        assertTrue(ordered.ordered)
    }

    @Test
    fun `table with separator has header`() {
        val blocks = parseMarkdownLite(
            """
            | name | value |
            |------|-------|
            | a    | 1     |
            | b    | 2     |
            """.trimIndent(),
        )
        val table = blocks.single() as MdBlock.Table
        assertEquals(listOf("name", "value"), table.header.map { it.text })
        assertEquals(2, table.rows.size)
        assertEquals(listOf("b", "2"), table.rows[1].map { it.text })
    }

    @Test
    fun `table without separator renders headerless`() {
        val blocks = parseMarkdownLite("| a | 1 |\n| b | 2 |")
        val table = blocks.single() as MdBlock.Table
        assertTrue(table.header.isEmpty())
        assertEquals(2, table.rows.size)
    }

    @Test
    fun `quote and code fence survive`() {
        val blocks = parseMarkdownLite("> stay honest\n\n```sh\nls -la\n```")
        assertEquals("stay honest", (blocks[0] as MdBlock.Quote).text.text)
        val code = blocks[1] as MdBlock.Code
        assertEquals("sh", code.language)
        assertEquals("ls -la", code.code)
    }

    @Test
    fun `inline bold italic code strike annotate`() {
        val blocks = parseMarkdownLite("**bold** and *soft* and `mono` and ~~gone~~")
        val paragraph = blocks.single() as MdBlock.Paragraph
        assertEquals("bold and soft and mono and gone", paragraph.text.text)
        assertEquals(4, paragraph.text.spanStyles.size)
    }

    @Test
    fun `latex converts to unicode`() {
        assertEquals("√(16) = 4", latexToUnicode("\\sqrt{16} = 4"))
        assertEquals("1/2", latexToUnicode("\\frac{1}{2}"))
        assertEquals("π r²", latexToUnicode("\\pi r^2"))
        assertEquals("x ≤ y ≠ z", latexToUnicode("x \\leq y \\neq z"))
        assertEquals("H₂O", latexToUnicode("H_2O"))
    }

    @Test
    fun `dollar spans convert inside prose`() {
        assertEquals("the area is π r², roughly.", mathToUnicode("the area is $\\pi r^2$, roughly."))
        assertEquals("no math here", mathToUnicode("no math here"))
    }

    @Test
    fun `heading inside paragraph text does not trigger`() {
        val blocks = parseMarkdownLite("use the #tag convention")
        assertTrue(blocks.single() is MdBlock.Paragraph)
    }
}
