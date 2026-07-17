package com.aisoul.app.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * IMPLEMENTATION §8 — zip of the whole /harness tree, entries sorted by path
 * so identical trees produce identical archives. `workspace/` (the toolbox
 * sandbox) and `*.tmp` never travel; entry mtimes are kept so the restore
 * preview can say how fresh a backup is.
 */
data class ArchivePreview(
    val fileCount: Int,
    val totalBytes: Long,
    val newestAt: Long,
    /** top-level dir (or "·" for root files) → file count */
    val tree: Map<String, Int>,
)

class Archiver {

    class BadZipException(message: String) : Exception(message)

    fun zipTree(root: File): ByteArray {
        val files = root.walkTopDown()
            .filter { it.isFile }
            .map { it to it.relativeTo(root).invariantSeparatorsPath }
            .filterNot { (_, rel) -> excluded(rel) }
            .sortedBy { (_, rel) -> rel }
            .toList()
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            files.forEach { (file, rel) ->
                val entry = ZipEntry(rel)
                entry.time = file.lastModified()
                zip.putNextEntry(entry)
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    fun preview(zipBytes: ByteArray): ArchivePreview {
        var count = 0
        var bytes = 0L
        var newest = 0L
        val tree = sortedMapOf<String, Int>()
        eachEntry(zipBytes) { entry, content ->
            count++
            bytes += content.size
            if (entry.time > newest) newest = entry.time
            val top = entry.name.substringBefore('/', "·").ifEmpty { "·" }
            val key = if (entry.name.contains('/')) top else "·"
            tree[key] = (tree[key] ?: 0) + 1
        }
        if (count == 0) throw BadZipException("archive is empty")
        return ArchivePreview(fileCount = count, totalBytes = bytes, newestAt = newest, tree = tree)
    }

    /**
     * IMPLEMENTATION §8 restore — extract beside the live tree, then swap
     * atomically: harness → harness.pre-restore (one generation kept),
     * fresh tree → harness. API keys are not in archives by design.
     */
    fun restore(root: File, zipBytes: ByteArray) {
        val fresh = File(root.parentFile, "${root.name}.restore-tmp")
        fresh.deleteRecursively()
        extractTo(zipBytes, fresh)

        val previous = File(root.parentFile, "${root.name}.pre-restore")
        previous.deleteRecursively()
        if (root.exists() && !root.renameTo(previous)) {
            fresh.deleteRecursively()
            throw BadZipException("couldn't set the current harness aside")
        }
        if (!fresh.renameTo(root)) {
            // put the old tree back; nothing is lost
            previous.renameTo(root)
            fresh.deleteRecursively()
            throw BadZipException("couldn't move the restored harness into place")
        }
    }

    private fun extractTo(zipBytes: ByteArray, target: File) {
        target.mkdirs()
        val targetCanonical = target.canonicalPath + File.separator
        eachEntry(zipBytes) { entry, content ->
            val file = File(target, entry.name)
            // zip-slip guard: every entry must land inside the target
            if (!file.canonicalPath.startsWith(targetCanonical)) {
                throw BadZipException("archive entry escapes the harness: ${entry.name}")
            }
            file.parentFile?.mkdirs()
            file.writeBytes(content)
            if (entry.time > 0) file.setLastModified(entry.time)
        }
    }

    private inline fun eachEntry(zipBytes: ByteArray, block: (ZipEntry, ByteArray) -> Unit) {
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) block(entry, zip.readBytes())
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun excluded(relPath: String): Boolean =
        relPath.startsWith("workspace/") || relPath.endsWith(".tmp")
}
