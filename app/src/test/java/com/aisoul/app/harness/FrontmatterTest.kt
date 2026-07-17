package com.aisoul.app.harness

import org.junit.Assert.assertEquals
import org.junit.Test

class FrontmatterTest {

    @Test
    fun `round trips fields and body`() {
        val original = Frontmatter.serialize(
            fields = mapOf("name" to "server setup", "description" to "two vps hosts", "type" to "project"),
            body = "# notes\n\nweb-01 and db-01.",
        )
        val parsed = Frontmatter.parse(original)
        assertEquals("server setup", parsed.fields["name"])
        assertEquals("two vps hosts", parsed.fields["description"])
        assertEquals("project", parsed.fields["type"])
        assertEquals("# notes\n\nweb-01 and db-01.", parsed.body)
    }

    @Test
    fun `text without frontmatter is all body`() {
        val parsed = Frontmatter.parse("plain text\nno fences")
        assertEquals(0, parsed.fields.size)
        assertEquals("plain text\nno fences", parsed.body)
    }

    @Test
    fun `unclosed fence is treated as body`() {
        val parsed = Frontmatter.parse("---\nname: x\nno closing fence")
        assertEquals(0, parsed.fields.size)
    }

    @Test
    fun `value colons survive`() {
        val parsed = Frontmatter.parse("---\ndescription: dashboard at https://example.com:8080\n---\n\nbody")
        assertEquals("dashboard at https://example.com:8080", parsed.fields["description"])
    }
}
