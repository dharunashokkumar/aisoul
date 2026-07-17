package com.aisoul.app.providers

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Test

class SseTest {

    @Test
    fun `parses events split across data lines`() {
        val raw = "event: message_start\ndata: {\"a\":1}\n\ndata: [DONE]\n\n"
        val events = mutableListOf<SseEvent>()
        Buffer().writeUtf8(raw).forEachSseEvent { events.add(it) }
        assertEquals(2, events.size)
        assertEquals("message_start", events[0].event)
        assertEquals("{\"a\":1}", events[0].data)
        assertEquals("[DONE]", events[1].data)
    }

    @Test
    fun `flushes trailing event without blank line`() {
        val events = mutableListOf<SseEvent>()
        Buffer().writeUtf8("data: tail").forEachSseEvent { events.add(it) }
        assertEquals(1, events.size)
        assertEquals("tail", events[0].data)
    }

    @Test
    fun `extracts openai style error message`() {
        val body = "{\"error\":{\"message\":\"invalid api key\",\"type\":\"auth\"}}"
        assertEquals("invalid api key (http 401)", extractErrorMessage(body, 401))
    }

    @Test
    fun `extracts gemini array style error message`() {
        val body = "[{\"error\":{\"message\":\"API key not valid\"}}]"
        assertEquals("API key not valid (http 400)", extractErrorMessage(body, 400))
    }

    @Test
    fun `falls back to raw body`() {
        assertEquals("plain failure (http 500)", extractErrorMessage("plain failure", 500))
    }
}
