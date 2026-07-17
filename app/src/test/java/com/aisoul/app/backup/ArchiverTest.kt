package com.aisoul.app.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiverTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val archiver = Archiver()

    private fun seedHarness(): File {
        val root = temp.newFolder("harness")
        File(root, "SOUL.md").writeText("# soul\n")
        File(root, "USER.md").writeText("# user\n")
        File(root, "memories").mkdirs()
        File(root, "memories/coffee.md").writeText("likes coffee\n")
        File(root, "chats").mkdirs()
        File(root, "chats/20260717.jsonl").writeText("{}\n")
        File(root, "widgets").mkdirs()
        File(root, "widgets/.approvals.json").writeText("{}")
        // never travels:
        File(root, "workspace").mkdirs()
        File(root, "workspace/scratch.txt").writeText("temp junk")
        File(root, "SOUL.md.tmp").writeText("half-written")
        return root
    }

    @Test
    fun `zip round trips the tree and skips workspace and tmp files`() {
        val root = seedHarness()
        val zip = archiver.zipTree(root)

        val target = temp.newFolder("restored")
        // restore() swaps directories; extraction is what we verify here
        archiver.restore(target, zip)

        assertEquals("# soul\n", File(target, "SOUL.md").readText())
        assertEquals("likes coffee\n", File(target, "memories/coffee.md").readText())
        assertEquals("{}", File(target, "widgets/.approvals.json").readText())
        assertFalse(File(target, "workspace/scratch.txt").exists())
        assertFalse(File(target, "SOUL.md.tmp").exists())
    }

    @Test
    fun `identical trees produce identical archives`() {
        val root = seedHarness()
        assertArrayEquals(archiver.zipTree(root), archiver.zipTree(root))
    }

    @Test
    fun `preview reports counts and tree shape`() {
        val zip = archiver.zipTree(seedHarness())
        val preview = archiver.preview(zip)
        assertEquals(5, preview.fileCount) // SOUL, USER, memory, chat, approvals
        assertEquals(2, preview.tree["·"]) // root files
        assertEquals(1, preview.tree["memories"])
        assertTrue(preview.newestAt > 0)
    }

    @Test
    fun `restore keeps one pre-restore generation`() {
        val root = seedHarness()
        val zip = archiver.zipTree(root)
        File(root, "SOUL.md").writeText("# mutated after backup\n")

        archiver.restore(root, zip)

        assertEquals("# soul\n", File(root, "SOUL.md").readText())
        val previous = File(root.parentFile, "${root.name}.pre-restore")
        assertEquals("# mutated after backup\n", File(previous, "SOUL.md").readText())
    }

    @Test
    fun `zip-slip entries are rejected`() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("../evil.txt"))
            zip.write("pwned".toByteArray())
            zip.closeEntry()
        }
        val root = temp.newFolder("victim")
        assertThrows(Archiver.BadZipException::class.java) {
            archiver.restore(root, out.toByteArray())
        }
        assertFalse(File(root.parentFile.parentFile, "evil.txt").exists())
    }

    @Test
    fun `empty archive is rejected by preview`() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("dir/"))
            zip.closeEntry()
        }
        assertThrows(Archiver.BadZipException::class.java) {
            archiver.preview(out.toByteArray())
        }
    }

    @Test
    fun `full pipeline zip encrypt decrypt restore`() {
        val crypto = BackupCrypto(FakeKdf())
        val root = seedHarness()
        val blob = crypto.encrypt(archiver.zipTree(root), "passphrase")

        File(root, "USER.md").writeText("# drifted\n")
        archiver.restore(root, crypto.decrypt(blob, "passphrase"))

        assertEquals("# user\n", File(root, "USER.md").readText())
    }
}
