package com.sentinel.system.shell

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ShizukuGateway(private val api: ShizukuApi, private val binder: suspend () -> IShellService) : Shell {
    private val mutableState = MutableStateFlow(readState())
    val stateFlow: StateFlow<ShizukuState> = mutableState.asStateFlow()
    private val stopListening = api.listen { state() }

    private fun readState(): ShizukuState = try {
        when {
            !api.isInstalled() -> ShizukuState.NOT_INSTALLED
            !api.pingBinder() -> ShizukuState.NOT_RUNNING
            !api.checkSelfPermission() -> ShizukuState.NO_PERMISSION
            else -> ShizukuState.READY
        }
    } catch (_: Exception) { ShizukuState.NOT_RUNNING }

    fun state(): ShizukuState = readState().also { mutableState.value = it }
    fun requestPermission(requestCode: Int = 7001) {
        if (state() == ShizukuState.NO_PERMISSION) runCatching { api.requestPermission(requestCode) }
    }
    fun close() = stopListening()

    override suspend fun exec(cmd: String): ExecResult {
        if (state() != ShizukuState.READY) return ExecResult(-2, "", "shizuku not ready")
        return try {
            val started = System.nanoTime()
            val service = withTimeout(COMMAND_TIMEOUT_MS) { binder() }
            val remaining = COMMAND_TIMEOUT_MS - (System.nanoTime() - started) / 1_000_000
            if (remaining <= 0) return ExecResult(-1, "", "command timed out")
            withContext(Dispatchers.IO) {
                withTimeout(remaining) {
                    Json.decodeFromString<ExecResult>(runInterruptible { service.exec(cmd) })
                }
            }
        } catch (_: TimeoutCancellationException) {
            ExecResult(-1, "", "command timed out")
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            state()
            ExecResult(-1, "", e.message ?: "shizuku failure")
        }
    }
}

class AndroidShizukuApi(private val context: Context) : ShizukuApi {
    override fun isInstalled(): Boolean = try {
        context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0); true
    } catch (_: PackageManager.NameNotFoundException) { false }
    override fun pingBinder() = Shizuku.pingBinder()
    override fun checkSelfPermission() = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    override fun requestPermission(requestCode: Int) = Shizuku.requestPermission(requestCode)
    override fun listen(onChanged: () -> Unit): () -> Unit {
        val received = Shizuku.OnBinderReceivedListener { onChanged() }
        val dead = Shizuku.OnBinderDeadListener { onChanged() }
        val permission = Shizuku.OnRequestPermissionResultListener { _, _ -> onChanged() }
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(dead)
        Shizuku.addRequestPermissionResultListener(permission)
        return {
            Shizuku.removeBinderReceivedListener(received)
            Shizuku.removeBinderDeadListener(dead)
            Shizuku.removeRequestPermissionResultListener(permission)
            Unit
        }
    }
}

suspend fun bindShell(context: Context): IShellService = suspendCancellableCoroutine { continuation ->
    val args = Shizuku.UserServiceArgs(ComponentName(context, ShellUserService::class.java))
        .tag("sentinel-shell").version(1).daemon(false)
    val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (continuation.isActive) continuation.resume(IShellService.Stub.asInterface(service))
            runCatching { Shizuku.unbindUserService(args, this, false) }
        }
        override fun onServiceDisconnected(name: ComponentName) {
            if (continuation.isActive) continuation.resumeWithException(IllegalStateException("shell disconnected"))
        }
    }
    continuation.invokeOnCancellation { runCatching { Shizuku.unbindUserService(args, connection, false) } }
    try { Shizuku.bindUserService(args, connection) }
    catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
}
