package com.mthtamim.phonelinkflows

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * v2.9: optional Shizuku bridge. When the user runs the Shizuku app and
 * grants PhoneLink permission, the shell_cmd action gains system-level
 * reach (secure settings, service control) without root.
 */
object ShizukuHelper {

    fun available(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) { false }

    fun granted(): Boolean = try {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) { false }

    fun request() {
        try {
            if (Shizuku.pingBinder()) Shizuku.requestPermission(9201)
        } catch (_: Throwable) { }
    }

    /** Why the last run() failed (v2.14) — surfaced to the user
     *  instead of failing silently. Null when the last run worked. */
    @Volatile var lastError: String? = null
        private set

    /** Runs a shell command via Shizuku; returns trimmed stdout ("" on failure). */
    fun run(command: String): String {
        lastError = null
        if (!granted()) {
            lastError = "not granted"
            return ""
        }
        return try {
            val proc = newProcess(arrayOf("sh", "-c", command))
            val out = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            out.trim()
        } catch (t: Throwable) {
            val cause = (t as? java.lang.reflect.InvocationTargetException)
                ?.targetException ?: t
            lastError = cause.javaClass.simpleName + ": " +
                (cause.message ?: "").take(120)
            ""
        }
    }

    /** A harmless real command, proving the whole shell path works. */
    fun selfTest(): Boolean = run("echo phonelink-ok") == "phonelink-ok"

    /** v2.24.0 (Lab merge): exit code of a shell command, for
     *  callers that care about success, not output (answer/end
     *  call keyevents). -1 when ungranted, timed out, or failed;
     *  lastError explains why. Logic copied from Lab's LabShizuku.
     *  v1.4.16: delegates to runCodeOut — its old exitValue()
     *  call threw "process hasn't exited" on this phone (see
     *  runCodeOut). Timeout kept at 10 s as before. */
    fun runCode(command: String): Int = runCodeOut(command, 10).first

    /** v1.4.13: exit code AND stdout of a shell command, for
     *  callers that must tell success from failure (Update's
     *  pm install: a failed install prints "Failure [...]" on
     *  stdout and exits non-zero without throwing, so run()
     *  alone cannot see it). -1 when ungranted, timed out, or
     *  failed; lastError explains why. run() is unchanged;
     *  runCode() delegates to this (v1.4.16).
     *
     *  v1.4.16: the exit code is read from a sentinel line the
     *  shell itself prints ("; echo MARKER:$?") — NEVER from
     *  Process.exitValue(). On this phone exitValue() throws
     *  "IllegalArgumentException: process hasn't exited" even
     *  after waitFor(timeout) returns true: Shizuku's remote
     *  process reports completion (streams closed) before the
     *  exit code itself arrives, so every call that trusted
     *  exitValue() failed — that is why App Hide hid 0 apps in
     *  v1.4.14 even though Shizuku was granted and pm was never
     *  the problem. The same fix also covers Update's pm
     *  install check, which uses runCodeOut too.
     *
     *  v1.4.17: waitFor(timeout) itself can throw the same
     *  "process hasn't exited" (Lark Player still failed that
     *  way on v1.4.16 while another app the same minute reached
     *  pm's own error), so the wait is now an isAlive() poll:
     *  isAlive()'s default implementation catches that
     *  exception and only reports true, so it cannot throw it. */
    fun runCodeOut(command: String, timeoutSec: Long = 60): Pair<Int, String> {
        lastError = null
        if (!granted()) {
            lastError = "not granted"
            return -1 to ""
        }
        return try {
            val marker = "__AH_EXIT_7f3a__:"
            val proc = newProcess(arrayOf("sh", "-c",
                "$command; echo $marker\$?"))
            val out = proc.inputStream.bufferedReader().readText()
            val deadline = System.currentTimeMillis() + timeoutSec * 1000
            while (proc.isAlive && System.currentTimeMillis() < deadline) {
                Thread.sleep(100)
            }
            val finished = !proc.isAlive
            if (!finished) {
                proc.destroyForcibly()
                lastError = "timeout"
                -1 to ""
            } else {
                val idx = out.lastIndexOf(marker)
                if (idx < 0) {
                    lastError = "no exit marker"
                    -1 to out.trim()
                } else {
                    val code = out.substring(idx + marker.length)
                        .trim().takeWhile { it.isDigit() }
                        .toIntOrNull() ?: -1
                    code to out.substring(0, idx).trim()
                }
            }
        } catch (t: Throwable) {
            val cause = (t as? java.lang.reflect.InvocationTargetException)
                ?.targetException ?: t
            lastError = cause.javaClass.simpleName + ": " +
                (cause.message ?: "").take(120)
            -1 to ""
        }
    }

    /** v2.24.0 (Lab merge): streaming shell process for the Flow
     *  logcat watcher (run() buffers all output, which never ends
     *  for logcat). Null when Shizuku is not granted. Logic copied
     *  from Lab's LabShizuku. */
    fun stream(command: String): Process? {
        if (!granted()) {
            lastError = "not granted"
            return null
        }
        return try {
            newProcess(arrayOf("sh", "-c", command))
        } catch (t: Throwable) {
            val cause = (t as? java.lang.reflect.InvocationTargetException)
                ?.targetException ?: t
            lastError = cause.javaClass.simpleName + ": " +
                (cause.message ?: "").take(120)
            null
        }
    }

    /** v2.16: dumpsys battery, trimmed to the useful lines — a
     *  supplement to BatteryInfo (which needs no root at all). */
    fun batteryDump(): String {
        if (!granted()) return ""
        val out = run("dumpsys battery")
        if (out.isEmpty() || lastError != null) return ""
        val keep = listOf("level:", "scale:", "health:", "temperature:",
            "voltage:", "status:", "present:", "AC powered:",
            "USB powered:", "Wireless powered:", "Charge counter:",
            "current now:")
        val lines = out.lines().map { it.trim() }
            .filter { l -> keep.any { l.startsWith(it) } }
        return if (lines.isEmpty()) ""
            else "Shizuku dumpsys battery:\n" + lines.joinToString("\n")
    }

    /** Shizuku.newProcess is private in API 13.1.5 (verified in the
     *  AAR: private static, (String[], String[], String)), which the
     *  Kotlin compiler refuses to call directly; reach it reflectively. */
    private fun newProcess(cmd: Array<String>): Process {
        val types = arrayOf<Class<*>>(Array<String>::class.java,
            Array<String>::class.java, String::class.java)
        val m = try {
            Shizuku::class.java.getDeclaredMethod("newProcess", *types)
        } catch (e: NoSuchMethodException) {
            Shizuku::class.java.getMethod("newProcess", *types)
        }
        m.isAccessible = true
        val args: Array<Any?> = arrayOf(cmd, null, null)
        return m.invoke(null, *args) as Process
    }
}
