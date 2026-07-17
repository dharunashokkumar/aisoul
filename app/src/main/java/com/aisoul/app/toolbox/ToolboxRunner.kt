package com.aisoul.app.toolbox

import android.content.Context
import android.system.Os
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * SPEC §7 — the sandboxed toolbox. Static binaries ship as fake native libs
 * (read-only, exec allowed); filesDir/bin holds symlinks at them (the W^X
 * check applies to the target). Everything runs as the app's own UID inside
 * /harness/workspace. Known limit (D-019): destroyForcibly kills the shell,
 * not orphaned grandchildren.
 */
class ToolboxRunner(context: Context, harnessRoot: File) {

    data class ExecResult(
        val exitCode: Int,
        val output: String,
        val timedOut: Boolean,
        val truncated: Boolean,
        val durationMs: Long,
    ) {
        val failed: Boolean get() = exitCode != 0 || timedOut
    }

    private val nativeDir = File(context.applicationInfo.nativeLibraryDir)
    private val binDir = File(context.filesDir, "bin")
    private val etcDir = File(context.filesDir, "etc")
    private val tmpDir = File(context.cacheDir, "tmp")
    private val workspace = File(harnessRoot, "workspace")
    private val caBundle = File(etcDir, "ca-bundle.pem")
    private val bootstrapMutex = Mutex()

    val available: Boolean get() = File(nativeDir, "libbusybox.so").exists()

    /** busybox applets we expose — the fixed developer toolbox, nothing more. */
    private val applets = listOf(
        "sh", "ash", "ls", "cat", "grep", "egrep", "sed", "awk", "head", "tail",
        "wc", "sort", "uniq", "cut", "tr", "find", "xargs", "mkdir", "rmdir",
        "rm", "cp", "mv", "touch", "ln", "chmod", "stat", "du", "df", "date",
        "echo", "printf", "env", "pwd", "which", "basename", "dirname", "tar",
        "gzip", "gunzip", "unzip", "md5sum", "sha256sum", "base64", "hexdump",
        "od", "diff", "cmp", "tee", "seq", "sleep", "ps", "uname", "id",
        "hostname", "wget", "nslookup", "nc",
    )

    suspend fun ensureBootstrapped() = bootstrapMutex.withLock {
        withContext(Dispatchers.IO) {
            if (!available) return@withContext
            workspace.mkdirs()
            tmpDir.mkdirs()
            // nativeLibraryDir moves on every install/update, dangling the
            // persisted symlinks — so "bootstrapped" means sh resolves to
            // *this* install's busybox, not a marker file.
            val busybox = File(nativeDir, "libbusybox.so").absolutePath
            val shLinked = runCatching {
                Os.readlink(File(binDir, "sh").absolutePath) == busybox
            }.getOrDefault(false)
            if (shLinked) return@withContext

            binDir.mkdirs()
            applets.forEach { applet -> relink(File(binDir, applet), busybox) }
            File(nativeDir, "libjq.so").takeIf { it.exists() }?.let {
                relink(File(binDir, "jq"), it.absolutePath)
            }
            File(nativeDir, "libcurl_exe.so").takeIf { it.exists() }?.let {
                relink(File(binDir, "curl"), it.absolutePath)
            }
            buildCaBundle()
        }
    }

    private fun relink(link: File, target: String) {
        // Os.remove clears dangling links too (File.exists() is false for those)
        runCatching { Os.remove(link.absolutePath) }
        runCatching { Os.symlink(target, link.absolutePath) }
    }

    /**
     * Static curl can't read Android's hash-named cert dir; concatenate the
     * system CAs into one PEM bundle it can (D-019).
     */
    private fun buildCaBundle() {
        runCatching {
            etcDir.mkdirs()
            val certs = File("/system/etc/security/cacerts").listFiles().orEmpty()
            caBundle.bufferedWriter().use { out ->
                certs.forEach { cert ->
                    var inBlock = false
                    cert.forEachLine { line ->
                        if (line.startsWith("-----BEGIN CERTIFICATE-----")) inBlock = true
                        if (inBlock) out.appendLine(line)
                        if (line.startsWith("-----END CERTIFICATE-----")) inBlock = false
                    }
                }
            }
        }
    }

    private fun getCommandPrefix(): String {
        val busybox = File(nativeDir, "libbusybox.so").absolutePath
        val jq = File(nativeDir, "libjq.so").absolutePath
        val curl = File(nativeDir, "libcurl_exe.so").absolutePath
        return buildString {
            append("busybox() { \"$busybox\" \"\\$@\"; }\n")
            applets.forEach { applet ->
                append("$applet() { \"$busybox\" $applet \"\\$@\"; }\n")
            }
            if (File(jq).exists()) {
                append("jq() { \"$jq\" \"\\$@\"; }\n")
            }
            if (File(curl).exists()) {
                append("curl() { \"$curl\" \"\\$@\"; }\n")
            }
        }
    }

    suspend fun run(
        command: String,
        timeoutMs: Long = 30_000,
        capBytes: Int = 64 * 1024,
    ): ExecResult = withContext(Dispatchers.IO) {
        ensureBootstrapped()
        if (!available) {
            return@withContext ExecResult(127, "toolbox unavailable on this device abi", false, false, 0)
        }
        val start = System.currentTimeMillis()
        val busybox = File(nativeDir, "libbusybox.so").absolutePath
        val fullCommand = getCommandPrefix() + command
        val builder = ProcessBuilder(busybox, "ash", "-c", fullCommand)
            .directory(workspace.also { it.mkdirs() })
            .redirectErrorStream(true)
        builder.environment().apply {
            put("PATH", "${binDir.absolutePath}:/system/bin")
            put("HOME", workspace.absolutePath)
            put("TMPDIR", tmpDir.absolutePath)
            put("TERM", "dumb")
            put("LANG", "C.UTF-8")
            if (caBundle.exists()) {
                put("CURL_CA_BUNDLE", caBundle.absolutePath)
                put("SSL_CERT_FILE", caBundle.absolutePath)
            }
        }

        val process = builder.start()
        process.outputStream.close()
        val output = ByteArrayOutputStream()
        var truncated = false
        try {
            coroutineScope {
                val reader = launch {
                    runInterruptible {
                        process.inputStream.use { stream ->
                            val buf = ByteArray(8 * 1024)
                            while (true) {
                                val n = stream.read(buf)
                                if (n < 0) break
                                val room = capBytes - output.size()
                                if (room > 0) output.write(buf, 0, minOf(n, room))
                                else truncated = true
                            }
                        }
                    }
                }
                val finished = runInterruptible { process.waitFor(timeoutMs, TimeUnit.MILLISECONDS) }
                if (!finished) {
                    process.destroyForcibly()
                    process.waitFor()
                    reader.join()
                    return@coroutineScope ExecResult(
                        exitCode = 124,
                        output = output.toString(Charsets.UTF_8.name()) +
                            "\n[timed out after ${timeoutMs / 1000}s — process killed]",
                        timedOut = true,
                        truncated = truncated,
                        durationMs = System.currentTimeMillis() - start,
                    )
                }
                reader.join()
                var text = output.toString(Charsets.UTF_8.name())
                if (truncated) text += "\n[output truncated at ${capBytes / 1024} KB]"
                ExecResult(
                    exitCode = process.exitValue(),
                    output = text,
                    timedOut = false,
                    truncated = truncated,
                    durationMs = System.currentTimeMillis() - start,
                )
            }
        } catch (e: CancellationException) {
            // cancel always kills the process (IMPLEMENTATION §4)
            process.destroyForcibly()
            throw e
        }
    }
}
