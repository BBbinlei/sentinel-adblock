package com.sentinel.app.fakes

import com.sentinel.system.profile.*
import com.sentinel.system.shell.*
import com.sentinel.system.ops.OpExecutor

class FakeShell : Shell {
    val commands = mutableListOf<String>()
    private val applied = mutableSetOf<String>()
    override suspend fun exec(cmd: String): ExecResult {
        commands += cmd
        val parts = cmd.split(' ', limit = 2)
        return when (parts[0]) {
            "probe" -> ExecResult(0, if (parts[1] in applied) "off" else "on", "")
            "apply" -> { applied += parts[1]; ExecResult(0, "", "") }
            "restore" -> { applied -= parts[1]; ExecResult(0, "", "") }
            else -> error("Unexpected test command: $cmd")
        }
    }
}

fun profileOp(id: String, kind: OpKind = OpKind.SETTING, optional: Boolean = false,
    verified: Boolean = true) = ProfileOp(id = id, trick = 1, title = "测试操作 $id",
    kind = kind, verified = verified, optional = optional, probe = "probe $id",
    apply = "apply $id", revert = "restore $id", appliedRegex = "^off$")

class FakeSystem(data: FakeData) {
    val shell = FakeShell()
    val normal = profileOp("normal")
    val optional = profileOp("optional", optional = true)
    val uninstall = profileOp("uninstall", kind = OpKind.UNINSTALL)
    val unverified = profileOp("unverified", verified = false)
    val profile = ColorOsProfile("test", ops = listOf(normal, optional, uninstall, unverified))
    val executor = OpExecutor(shell, data.logs, data.events)
}
