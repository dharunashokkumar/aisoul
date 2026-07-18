package com.aisoul.app.toolbox

import android.content.Context
import android.system.Os
import android.util.Base64
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
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * SPEC §7 — the sandboxed toolbox. Static binaries ship as fake native libs
 * (read-only, exec allowed); filesDir/bin holds symlinks at them. Everything
 * runs as the app's own UID inside /harness/workspace. Known limit (D-019):
 * destroyForcibly kills the shell, not orphaned grandchildren.
 *
 * **Nothing here is assumed — it is probed (D-036, D-037).** A bundled static
 * binary can be perfectly valid and still be unrunnable: Android's seccomp
 * filter kills syscalls it doesn't whitelist with SIGSYS (exit 159, "Bad system
 * call"), and it kills *per binary* — on an Android 14 device curl survives
 * while busybox and jq do not. Busybox has a second trap: it dispatches on
 * `basename(argv[0])`, which ProcessBuilder forces to the exec'd path, so it is
 * only reachable through an applet-named symlink.
 *
 * So bootstrap runs each candidate once and records what actually worked. The
 * agent is then told the truth ([capabilitySummary]) instead of discovering it
 * one failed turn at a time.
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

    /** which shell survives on this device. */
    private enum class Mode(val label: String) {
        /** busybox ash, reached through an applet-named symlink. */
        BUSYBOX("busybox"),

        /** android's /system/bin/sh, busybox applets still first on PATH. */
        SYSTEM_SH_BUSYBOX("system-sh+busybox"),

        /** android's /system/bin/sh with android's own toybox utilities. */
        SYSTEM_SH("system-sh"),
    }

    /** how far a bundled binary got. */
    private enum class Reach {
        /** blocked or absent — the symlink is removed so `command -v` agrees. */
        DEAD,

        /** runs, but only from nativeLibraryDir — exposed as a shell function. */
        FUNCTION,

        /** runs through its symlink too, so it sits on PATH like a normal tool. */
        PATH,
    }

    private data class Caps(val mode: Mode, val curl: Reach, val jq: Reach)

    private val nativeDir = File(context.applicationInfo.nativeLibraryDir)
    private val binDir = File(context.filesDir, "bin")

    /** busybox applet symlinks — kept off PATH when busybox can't run, so its
     *  dead `ls` never shadows android's working one. */
    private val appletDir = File(binDir, "busybox")
    private val toolDir = File(binDir, "tools")
    private val etcDir = File(context.filesDir, "etc")
    private val tmpDir = File(context.cacheDir, "tmp")
    private val workspace = File(harnessRoot, "workspace")
    private val caBundle = File(etcDir, "ca-bundle.pem")
    private val curlRc = File(etcDir, ".curlrc")
    private val bootstrapMutex = Mutex()

    private val busybox = File(nativeDir, "libbusybox.so")
    private val jq = File(nativeDir, "libjq.so")
    private val curl = File(nativeDir, "libcurl_exe.so")
    private val systemSh = File("/system/bin/sh")

    @Volatile
    private var caps: Caps? = null

    @Volatile
    private var caCerts = 0

    /** a shell always exists — android's own is the floor. */
    val available: Boolean get() = systemSh.canExecute() || busybox.exists()

    /**
     * One line of truth for the agent's tool description — what this device
     * actually runs, and what it doesn't, so no turn is spent finding out.
     */
    val capabilitySummary: String
        get() {
            val c = caps ?: return "posix shell + text utilities, curl, jq"
            val have = mutableListOf<String>()
            have += if (c.mode == Mode.SYSTEM_SH) {
                "android's own toybox utilities (sh, ls, cat, grep, sed, awk, find, tar, …)"
            } else {
                "busybox applets (sh, ls, cat, grep, sed, awk, find, tar, wget, nc, …)"
            }
            if (c.curl != Reach.DEAD) have += "curl (https works; hostnames resolve over DoH)"
            if (c.jq != Reach.DEAD) have += "jq"
            have += "ping"
            val missing = mutableListOf<String>()
            if (c.mode == Mode.SYSTEM_SH) missing += "busybox"
            if (c.jq == Reach.DEAD) missing += "jq (parse json with the fetch tool or awk instead)"
            if (c.curl == Reach.DEAD) missing += "curl (use the fetch tool for the network)"
            missing += "python, node, package managers"
            return have.joinToString(", ") + ". not available: " + missing.joinToString(", ")
        }

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
            if (caps != null) return@withContext
            workspace.mkdirs()
            tmpDir.mkdirs()
            // Clear every plain entry left in bin/ by an older layout — they
            // would sit on PATH as dangling names, and one of them is a stale
            // "busybox" symlink squatting on the applet directory's name.
            binDir.mkdirs()
            binDir.listFiles()?.forEach { stale ->
                if (!stale.isDirectory) runCatching { Os.remove(stale.absolutePath) }
            }
            appletDir.mkdirs()
            toolDir.mkdirs()
            // nativeLibraryDir moves on every install/update, dangling the
            // persisted symlinks — relink every process start rather than
            // trusting a marker file. busybox dispatches on basename(argv[0]),
            // so the applet name *is* the entry point.
            if (busybox.exists()) {
                (applets + "busybox").forEach { relink(File(appletDir, it), busybox.absolutePath) }
            }
            // an older build could leave an empty bundle here, which fails
            // every handshake — size is the tell, so rebuild on anything short
            caCerts = if (caBundle.length() >= MIN_BUNDLE_BYTES) 1 else buildCaBundle()
            val mode = probeMode()
            caps = Caps(
                mode = mode,
                curl = probeTool(mode, curl, "curl"),
                jq = probeTool(mode, jq, "jq"),
            )
            writeCurlRc()
        }
    }

    /**
     * Run a trivial command under each shell, best first, and keep the one that
     * works. The probe has to fork+exec (`ls`), not just hit a builtin — the
     * failures being dodged land on the child, and a shell killed by SIGSYS
     * loses its buffered stdout, which is why a broken toolbox reported exit
     * 159 with no output at all.
     */
    private suspend fun probeMode(): Mode {
        val candidates = buildList {
            if (busybox.exists()) {
                add(Mode.BUSYBOX)
                if (systemSh.canExecute()) add(Mode.SYSTEM_SH_BUSYBOX)
            }
            add(Mode.SYSTEM_SH)
        }
        val probe = "echo p1 && ls / >/dev/null 2>&1 && echo p2"
        for (candidate in candidates) {
            val result = probeRun(candidate, probe) ?: continue
            if (result.exitCode == 0 && result.output.contains("p1") && result.output.contains("p2")) {
                return candidate
            }
        }
        return candidates.last()
    }

    /**
     * `--version` is the cheapest thing that proves a binary can actually
     * execute here: a seccomp-blocked one dies on it exactly as it would on
     * real work. Prefer the symlink (so the tool lands on PATH and `command -v`
     * finds it); fall back to a shell function bound to nativeLibraryDir.
     */
    private suspend fun probeTool(mode: Mode, binary: File, name: String): Reach {
        val link = File(toolDir, name)
        if (!binary.exists() || !runs(mode, binary.absolutePath)) {
            runCatching { Os.remove(link.absolutePath) }
            return Reach.DEAD
        }
        relink(link, binary.absolutePath)
        if (runs(mode, link.absolutePath)) return Reach.PATH
        runCatching { Os.remove(link.absolutePath) }
        return Reach.FUNCTION
    }

    private suspend fun runs(mode: Mode, path: String): Boolean =
        probeRun(mode, "\"$path\" --version >/dev/null 2>&1")?.exitCode == 0

    private suspend fun probeRun(mode: Mode, command: String): ExecResult? = runCatching {
        exec(mode, command, timeoutMs = 5_000, capBytes = 4 * 1024, withPrefix = false)
    }.getOrElse { if (it is CancellationException) throw it else null }

    private fun relink(link: File, target: String) {
        // Os.remove clears dangling links too (File.exists() is false for those)
        runCatching { Os.remove(link.absolutePath) }
        runCatching { Os.symlink(target, link.absolutePath) }
    }

    /**
     * Static curl can't read Android's hash-named cert dirs, and as of Android
     * 14 the roots live in the conscrypt APEX rather than /system/etc/security
     * — scanning a fixed directory wrote an *empty* bundle there, which fails
     * every handshake (D-037). Ask the platform's own trust store instead and
     * PEM-encode what it hands back; that works on every version.
     */
    private fun buildCaBundle(): Int = runCatching {
        etcDir.mkdirs()
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        val roots = factory.trustManagers
            .filterIsInstance<X509TrustManager>()
            .flatMap { it.acceptedIssuers.asList() }
        var written = 0
        val staging = File(etcDir, "ca-bundle.pem.tmp")
        staging.bufferedWriter().use { out ->
            roots.forEach { root ->
                val der = runCatching { root.encoded }.getOrNull() ?: return@forEach
                out.appendLine("-----BEGIN CERTIFICATE-----")
                Base64.encodeToString(der, Base64.NO_WRAP).chunked(64).forEach(out::appendLine)
                out.appendLine("-----END CERTIFICATE-----")
                written++
            }
        }
        if (written > 0 && staging.renameTo(caBundle)) written else 0.also { staging.delete() }
    }.getOrDefault(0)

    /**
     * Android has no /etc/resolv.conf, so a static curl cannot resolve a
     * hostname at all — every request returns http_code 000 (D-037). DNS over
     * HTTPS fixes it because the resolver is addressed by IP literal, so it
     * needs no DNS to bootstrap. Written as a config file, not baked into the
     * call, so any command can override it with its own --doh-url.
     */
    private fun writeCurlRc() {
        runCatching {
            etcDir.mkdirs()
            curlRc.writeText("--doh-url $DOH_URL\n")
        }
    }

    /** shell functions for binaries that run but can't sit on PATH. */
    private fun commandPrefix(caps: Caps): String = buildString {
        if (caps.curl == Reach.FUNCTION) append("curl() { \"${curl.absolutePath}\" \"\$@\"; }\n")
        if (caps.jq == Reach.FUNCTION) append("jq() { \"${jq.absolutePath}\" \"\$@\"; }\n")
    }

    private fun shellArgv(mode: Mode, command: String): List<String> = when (mode) {
        // argv[0] must basename to an applet, so go through the symlink
        Mode.BUSYBOX -> listOf(File(appletDir, "sh").absolutePath, "-c", command)
        else -> listOf(systemSh.absolutePath, "-c", command)
    }

    private fun pathFor(mode: Mode): String = buildString {
        if (mode != Mode.SYSTEM_SH) append("${appletDir.absolutePath}:")
        append("${toolDir.absolutePath}:/system/bin:/system/xbin")
    }

    suspend fun run(
        command: String,
        timeoutMs: Long = 30_000,
        capBytes: Int = 64 * 1024,
    ): ExecResult {
        ensureBootstrapped()
        val active = caps
        return runCatching {
            exec(active?.mode ?: Mode.SYSTEM_SH, command, timeoutMs, capBytes, withPrefix = true)
        }.getOrElse { e ->
            if (e is CancellationException) throw e
            ExecResult(127, "toolbox unavailable: ${e.message ?: e.javaClass.simpleName}", false, false, 0)
        }
    }

    private suspend fun exec(
        mode: Mode,
        command: String,
        timeoutMs: Long,
        capBytes: Int,
        withPrefix: Boolean,
    ): ExecResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val prefix = if (withPrefix) caps?.let { commandPrefix(it) }.orEmpty() else ""
        val builder = ProcessBuilder(shellArgv(mode, prefix + command))
            .directory(workspace.also { it.mkdirs() })
            .redirectErrorStream(true)
        builder.environment().apply {
            put("PATH", pathFor(mode))
            put("HOME", workspace.absolutePath)
            put("TMPDIR", tmpDir.absolutePath)
            put("TERM", "dumb")
            put("LANG", "C.UTF-8")
            put("AISOUL_TOOLBOX", mode.label)
            put("CURL_HOME", etcDir.absolutePath)
            if (caCerts > 0 && caBundle.exists()) {
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
                val exitCode = process.exitValue()
                if (exitCode == SIGSYS_EXIT) {
                    text += "\n[SIGSYS — android's seccomp filter killed that binary; " +
                        "it cannot run on this device, don't retry it]"
                }
                ExecResult(
                    exitCode = exitCode,
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

    private companion object {
        /** 128 + SIGSYS(31) — "Bad system call". */
        const val SIGSYS_EXIT = 159

        /** below this the bundle is empty or truncated, so rebuild it. */
        const val MIN_BUNDLE_BYTES = 4096L

        const val DOH_URL = "https://1.1.1.1/dns-query"
    }
}
