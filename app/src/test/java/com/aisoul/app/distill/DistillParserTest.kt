package com.aisoul.app.distill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DistillParserTest {

    @Test
    fun `parses plain operations`() {
        val ops = DistillParser.parse(
            """{"operations":[{"op":"create","slug":"morning-runs","name":"morning runs","description":"runs at 6am most days","type":"user","content":"runs at 6am."}]}""",
        )
        assertEquals(1, ops.size)
        assertEquals("create", ops[0].op)
        assertEquals("morning-runs", ops[0].slug)
    }

    @Test
    fun `tolerates markdown fences and prose`() {
        val raw = "sure, here you go:\n```json\n{\"operations\":[{\"op\":\"update\",\"slug\":\"job\",\"name\":\"job\",\"description\":\"now a staff engineer\"}]}\n```"
        val ops = DistillParser.parse(raw)
        assertEquals(1, ops.size)
        assertEquals("update", ops[0].op)
    }

    @Test
    fun `rejects invalid slugs and unknown ops`() {
        val ops = DistillParser.parse(
            """{"operations":[
                {"op":"create","slug":"Bad Slug!","name":"x","description":"y","content":"z"},
                {"op":"explode","slug":"fine-slug","name":"x","description":"y","content":"z"}
            ]}""",
        )
        assertTrue(ops.isEmpty())
    }

    @Test
    fun `create without content is dropped`() {
        val ops = DistillParser.parse(
            """{"operations":[{"op":"create","slug":"empty-one","name":"x","description":"y"}]}""",
        )
        assertTrue(ops.isEmpty())
    }

    @Test
    fun `malformed json yields empty`() {
        assertTrue(DistillParser.parse("i could not find anything durable.").isEmpty())
        assertTrue(DistillParser.parse("{oops").isEmpty())
    }

    @Test
    fun `v2 envelope carries the closeout fields`() {
        val result = DistillParser.parseResult(
            """{"operations":[],"log":"we set up the server widget.","cursor":"next: verify ping works","activity":"server widget setup","title":"server monitoring"}""",
        )
        assertTrue(result.operations.isEmpty())
        assertEquals("we set up the server widget.", result.log)
        assertEquals("next: verify ping works", result.cursor)
        assertEquals("server widget setup", result.activity)
        assertEquals("server monitoring", result.title)
    }

    @Test
    fun `v1 output still parses with null closeout fields`() {
        val result = DistillParser.parseResult(
            """{"operations":[{"op":"create","slug":"a-fact","name":"a","description":"b","content":"c"}]}""",
        )
        assertEquals(1, result.operations.size)
        assertTrue(result.log == null && result.cursor == null && result.title == null)
    }

    @Test
    fun `blank closeout fields become null`() {
        val result = DistillParser.parseResult(
            """{"operations":[],"log":"","cursor":"  ","activity":"","title":""}""",
        )
        assertTrue(result.log == null && result.cursor == null && result.activity == null && result.title == null)
    }
}
