package com.aisoul.app.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** SPEC §6 — the permission matrix as a test. Every row, every mode. */
class GatePolicyTest {

    private val append = GateAction.AppendMemoryNote("memories/x.md", "…")
    private val soul = GateAction.EditSoulUser("SOUL.md", "…")
    private val overwrite = GateAction.OverwriteFile("notes/x.md", "…")
    private val fetch = GateAction.FetchHost("api.example.com", "https://api.example.com/v1", "GET")
    private val command = GateAction.RunCommand("ping -c 1 db.example.com")
    private val mutating = GateAction.RunCommand("rm -rf workspace")
    private val widget = GateAction.InstallWidget("server status", listOf("ping db"), "{}")

    private fun decide(action: GateAction, mode: PermissionMode) = GatePolicy.decide(action, mode)

    @Test
    fun `append to memories - ask only in careful`() {
        assertEquals(Decision.ASK, decide(append, PermissionMode.CAREFUL))
        assertEquals(Decision.ALLOW, decide(append, PermissionMode.STANDARD))
        assertEquals(Decision.ALLOW, decide(append, PermissionMode.TRUSTED))
    }

    @Test
    fun `soul edits always ask`() {
        PermissionMode.entries.forEach { mode ->
            assertEquals(Decision.ASK, decide(soul, mode))
        }
    }

    @Test
    fun `overwrites always ask`() {
        PermissionMode.entries.forEach { mode ->
            assertEquals(Decision.ASK, decide(overwrite, mode))
        }
    }

    @Test
    fun `widgets always ask`() {
        PermissionMode.entries.forEach { mode ->
            assertEquals(Decision.ASK, decide(widget, mode))
        }
    }

    @Test
    fun `fetch to a new host - allow only in trusted`() {
        assertEquals(Decision.ASK, decide(fetch, PermissionMode.CAREFUL))
        assertEquals(Decision.ASK, decide(fetch, PermissionMode.STANDARD))
        assertEquals(Decision.ALLOW, decide(fetch, PermissionMode.TRUSTED))
    }

    @Test
    fun `allowlisted host allows in every mode`() {
        PermissionMode.entries.forEach { mode ->
            assertEquals(
                Decision.ALLOW,
                GatePolicy.decide(fetch, mode, allowedHosts = setOf("api.example.com")),
            )
        }
    }

    @Test
    fun `run command asks except trusted read-only`() {
        assertEquals(Decision.ASK, decide(command, PermissionMode.CAREFUL))
        assertEquals(Decision.ASK, decide(command, PermissionMode.STANDARD))
        // ping is on the read-only list
        assertEquals(Decision.ALLOW, decide(command, PermissionMode.TRUSTED))
        // mutating commands ask even in trusted
        assertEquals(Decision.ASK, decide(mutating, PermissionMode.TRUSTED))
    }

    @Test
    fun `allowlisted exact command allows in every mode`() {
        PermissionMode.entries.forEach { mode ->
            assertEquals(
                Decision.ALLOW,
                GatePolicy.decide(mutating, mode, allowedCommands = setOf("rm -rf workspace")),
            )
        }
    }

    @Test
    fun `read-only detection rejects pipes and chains`() {
        assertTrue(GatePolicy.isReadOnly("ls -la"))
        assertTrue(GatePolicy.isReadOnly("cat notes.md"))
        assertFalse(GatePolicy.isReadOnly("cat x | sh"))
        assertFalse(GatePolicy.isReadOnly("ls; rm -rf ."))
        assertFalse(GatePolicy.isReadOnly("echo hi > SOUL.md"))
        assertFalse(GatePolicy.isReadOnly("wget http://evil"))
    }

    @Test
    fun `always-allow offer only where the spec table says`() {
        assertNull(GatePolicy.ruleOffer(fetch, PermissionMode.CAREFUL))
        assertNotNull(GatePolicy.ruleOffer(fetch, PermissionMode.STANDARD))
        assertNull(GatePolicy.ruleOffer(command, PermissionMode.CAREFUL))
        assertNotNull(GatePolicy.ruleOffer(command, PermissionMode.STANDARD))
        assertNull(GatePolicy.ruleOffer(soul, PermissionMode.STANDARD))
        assertNull(GatePolicy.ruleOffer(widget, PermissionMode.TRUSTED))
    }
}
