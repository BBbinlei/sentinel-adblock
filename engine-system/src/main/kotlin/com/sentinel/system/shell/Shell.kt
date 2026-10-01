package com.sentinel.system.shell

import kotlinx.serialization.Serializable

const val COMMAND_TIMEOUT_MS = 15_000L
@Serializable data class ExecResult(val exitCode: Int, val stdout: String, val stderr: String) {
    val ok get() = exitCode == 0
}
interface Shell { suspend fun exec(cmd: String): ExecResult }
enum class ShizukuState { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, READY }
interface ShizukuApi {
    fun pingBinder(): Boolean
    fun checkSelfPermission(): Boolean
    fun isInstalled(): Boolean
    fun requestPermission(requestCode: Int) = Unit
    fun listen(onChanged: () -> Unit): () -> Unit = {}
}
