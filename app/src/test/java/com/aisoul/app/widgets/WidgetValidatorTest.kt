package com.aisoul.app.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** SPEC §8 — the golden example must pass; everything malicious must not. */
class WidgetValidatorTest {

    private val serverStatus = """
        {
          "schema": 1,
          "id": "server-status",
          "title": "server status",
          "icon": "dns",
          "size": "medium",
          "refresh": { "on_open": true, "interval_min": 15 },
          "sources": {
            "web":  { "type": "http", "method": "GET", "url": "https://web-01.example.com/health", "extract": "$.status" },
            "db":   { "type": "tool", "command": "ping -c 1 -W 2 db.example.com", "extract": "regex:time=([0-9.]+)" },
            "todo": { "type": "file", "path": "notes/servers.md", "extract": "lines:1-3" }
          },
          "body": [
            { "type": "stat", "label": "web-01", "value": "{web}", "ok_when": "healthy" },
            { "type": "stat", "label": "db ping", "value": "{db} ms" },
            { "type": "list", "items_from": "todo" },
            { "type": "buttons", "items": [
              { "label": "diagnose", "action": { "type": "chat", "prompt": "my servers look slow" } },
              { "label": "refresh",  "action": { "type": "refresh" } }
            ]}
          ]
        }
    """.trimIndent()

    @Test
    fun `golden server-status spec validates with frozen capabilities`() {
        val result = WidgetValidator.validate(serverStatus)
        assertTrue((result as? WidgetValidator.Result.Invalid)?.problems.orEmpty().joinToString(), result is WidgetValidator.Result.Valid)
        val valid = result as WidgetValidator.Result.Valid
        assertEquals(listOf("https://web-01.example.com/health"), valid.capabilities.urls)
        assertEquals(listOf("ping -c 1 -W 2 db.example.com"), valid.capabilities.commands)
        assertEquals(listOf("notes/servers.md"), valid.capabilities.paths)
    }

    @Test
    fun `unknown top-level key is rejected outright`() {
        val result = WidgetValidator.validate("""{"schema":1,"id":"x","title":"x","body":[{"type":"text","text":"hi"}],"javascript":"alert(1)"}""")
        assertTrue(result is WidgetValidator.Result.Invalid)
    }

    @Test
    fun `unknown component type is rejected`() {
        val result = WidgetValidator.validate("""{"schema":1,"id":"x","title":"x","body":[{"type":"webview","text":"x"}]}""")
        assertTrue(result is WidgetValidator.Result.Invalid)
    }

    @Test
    fun `path traversal in file source is rejected`() {
        val result = WidgetValidator.validate(
            """{"schema":1,"id":"x","title":"x","sources":{"s":{"type":"file","path":"../../../etc/passwd"}},"body":[{"type":"list","items_from":"s"}]}""",
        )
        assertTrue(result is WidgetValidator.Result.Invalid)
    }

    @Test
    fun `non-http url scheme is rejected`() {
        val result = WidgetValidator.validate(
            """{"schema":1,"id":"x","title":"x","sources":{"s":{"type":"http","url":"ftp://example.com/x"}},"body":[{"type":"text","text":"{s}"}]}""",
        )
        assertTrue(result is WidgetValidator.Result.Invalid)
    }

    @Test
    fun `template referencing a missing source is rejected`() {
        val result = WidgetValidator.validate(
            """{"schema":1,"id":"x","title":"x","body":[{"type":"text","text":"{ghost}"}]}""",
        )
        assertTrue(result is WidgetValidator.Result.Invalid)
    }

    @Test
    fun `refresh interval below the 15-minute floor is rejected`() {
        val result = WidgetValidator.validate(
            """{"schema":1,"id":"x","title":"x","refresh":{"on_open":true,"interval_min":5},"body":[{"type":"text","text":"hi"}]}""",
        )
        assertTrue(result is WidgetValidator.Result.Invalid)
    }

    @Test
    fun `run action command is captured as a frozen capability`() {
        val result = WidgetValidator.validate(
            """{"schema":1,"id":"x","title":"x","body":[{"type":"buttons","items":[{"label":"go","action":{"type":"run","command":"ping -c 1 host"}}]}]}""",
        )
        val valid = result as WidgetValidator.Result.Valid
        assertEquals(listOf("ping -c 1 host"), valid.capabilities.commands)
    }
}
