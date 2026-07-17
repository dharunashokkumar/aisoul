package com.aisoul.app.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ExtractorsTest {

    @Test
    fun `jsonpath walks objects and arrays`() {
        assertEquals("42", Extractors.apply("$.a.b[0]", """{"a":{"b":[42,7]}}"""))
        assertEquals("healthy", Extractors.apply("$.status", """{"status":"healthy"}"""))
        assertNull(Extractors.apply("$.missing.key", """{"status":"ok"}"""))
        assertNull(Extractors.apply("$.a[5]", """{"a":[1]}"""))
    }

    @Test
    fun `regex returns the first capture group`() {
        assertEquals(
            "12.3",
            Extractors.apply("regex:time=([0-9.]+)", "64 bytes from x: icmp_seq=1 time=12.3 ms"),
        )
        assertNull(Extractors.apply("regex:time=([0-9.]+)", "no match here"))
    }

    @Test
    fun `lines slices one-indexed inclusive ranges`() {
        val input = "one\ntwo\nthree\nfour"
        assertEquals("one\ntwo", Extractors.apply("lines:1-2", input))
        assertEquals("three", Extractors.apply("lines:3", input))
        assertEquals("one\ntwo\nthree\nfour", Extractors.apply("lines:1-99", input))
        assertNull(Extractors.apply("lines:9", input))
    }

    @Test
    fun `no extractor passes raw through trimmed`() {
        assertEquals("hello", Extractors.apply(null, "  hello\n"))
    }

    @Test
    fun `validate rejects malformed extractors`() {
        assertNull(Extractors.validate("$.a.b[0]"))
        assertNull(Extractors.validate("regex:time=([0-9.]+)"))
        assertNull(Extractors.validate("lines:1-3"))
        assertNotNull(Extractors.validate("$.a[*]"))
        assertNotNull(Extractors.validate("regex:no group"))
        assertNotNull(Extractors.validate("lines:abc"))
        assertNotNull(Extractors.validate("xpath://a"))
    }
}
