package com.sentinel.system.fakes

import com.sentinel.system.shell.ExecResult
import com.sentinel.system.shell.Shell
import com.sentinel.system.shell.ShizukuState
import kotlinx.coroutines.flow.MutableStateFlow

class FakeDevice : Shell {
    val state = MutableStateFlow(ShizukuState.READY)
    val settings = linkedMapOf<Pair<String, String>, String>()
    val enabledPackages = linkedMapOf<String, Boolean>()
    val installedPackages = linkedMapOf<String, Boolean>()
    val appops = linkedMapOf<Pair<String, String>, String>()
    val commands = mutableListOf<String>()
    val executedCommands = mutableListOf<String>()
    val failures = mutableSetOf<String>()

    data class Snapshot(
        val settings: Map<Pair<String, String>, String>,
        val enabledPackages: Map<String, Boolean>,
        val installedPackages: Map<String, Boolean>,
        val appops: Map<Pair<String, String>, String>,
    )
    fun snapshot() = Snapshot(settings.toMap(), enabledPackages.toMap(), installedPackages.toMap(), appops.toMap())

    override suspend fun exec(cmd: String): ExecResult {
        commands += cmd
        if (state.value != ShizukuState.READY) return ExecResult(-2, "", "shizuku not ready")
        executedCommands += cmd
        if (cmd in failures) return ExecResult(1, "", "injected command failure")
        // ponytail: 只支持本测试使用的无引号命令；增加带空格的值时换用 shell tokenizer。
        val words = cmd.trim().split(Regex("\\s+"))
        fun ok(output: String = "") = ExecResult(0, output, "")
        fun invalid() = ExecResult(127, "", "unsupported fake command: $cmd")
        return when {
            words.size == 4 && words.take(2) == listOf("settings", "get") ->
                ok((settings[words[2] to words[3]] ?: "null") + "\n")
            words.size == 5 && words.take(2) == listOf("settings", "put") -> {
                settings[words[2] to words[3]] = words[4]; ok()
            }
            words.size == 4 && words.take(2) == listOf("settings", "delete") -> {
                settings.remove(words[2] to words[3]); ok()
            }
            words.size == 4 && words.take(2) == listOf("appops", "get") ->
                ok("${words[3]}: ${appops[words[2] to words[3]] ?: "default"}\n")
            words.size == 5 && words.take(2) == listOf("appops", "set") -> {
                appops[words[2] to words[3]] = words[4]; ok()
            }
            words.take(3) == listOf("pm", "list", "packages") -> {
                val flags = words.drop(3).filter { it.startsWith("-") }
                val filter = words.drop(3).firstOrNull { !it.startsWith("-") }
                if (flags.any { it !in setOf("-d", "-e", "-u") }) return invalid()
                ok(installedPackages.keys.filter { pkg ->
                    (installedPackages[pkg] == true || "-u" in flags) &&
                        ("-d" !in flags || enabledPackages[pkg] == false) &&
                        ("-e" !in flags || enabledPackages[pkg] == true) &&
                        (filter == null || pkg.contains(filter))
                }.sorted().joinToString("\n", postfix = "\n") { "package:$it" }.trimStart('\n'))
            }
            words.take(2) == listOf("pm", "disable-user") || words.take(2) == listOf("pm", "enable") -> {
                val pkg = words.last()
                if (installedPackages[pkg] != true) return ExecResult(1, "", "Unknown package: $pkg")
                val enabled = words[1] == "enable"
                enabledPackages[pkg] = enabled
                ok("Package $pkg new state: ${if (enabled) "enabled" else "disabled-user"}\n")
            }
            words.take(2) == listOf("pm", "uninstall") -> {
                val pkg = words.last()
                if (installedPackages[pkg] != true) return ExecResult(1, "Failure [not installed]", "")
                installedPackages[pkg] = false; ok("Success\n")
            }
            words.take(2) == listOf("pm", "install-existing") || words.take(3) == listOf("cmd", "package", "install-existing") -> {
                val pkg = words.last()
                if (pkg !in installedPackages) return ExecResult(1, "", "Unknown package: $pkg")
                installedPackages[pkg] = true; ok("Package $pkg installed for user: 0\n")
            }
            else -> invalid()
        }
    }
}
