package com.sentinel.system.shell

import androidx.annotation.Keep
import kotlinx.serialization.json.Json
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Keep
class ShellUserService : IShellService.Stub() {
    override fun exec(cmd: String): String {
        val readers = Executors.newFixedThreadPool(2)
        var process: Process? = null
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(COMMAND_TIMEOUT_MS)
        fun remaining() = (deadline - System.nanoTime()).coerceAtLeast(1)
        val result = try {
            val child = ProcessBuilder("sh", "-c", cmd).start()
            process = child
            val out = readers.submit<String> { child.inputStream.bufferedReader().use { it.readText() } }
            val err = readers.submit<String> { child.errorStream.bufferedReader().use { it.readText() } }
            if (!child.waitFor(remaining(), TimeUnit.NANOSECONDS)) ExecResult(-1, "", "command timed out")
            else ExecResult(child.exitValue(), out.get(remaining(), TimeUnit.NANOSECONDS), err.get(remaining(), TimeUnit.NANOSECONDS))
        } catch (e: Exception) {
            ExecResult(-1, "", e.message ?: "shell failure")
        } finally {
            process?.destroyForcibly()
            readers.shutdownNow()
        }
        return Json.encodeToString(result)
    }
}
