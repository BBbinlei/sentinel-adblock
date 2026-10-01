package com.sentinel.vpn.service

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.sentinel.data.Clock
import com.sentinel.data.apps.AppRegistry
import com.sentinel.data.apps.PackageWatcher
import com.sentinel.data.repo.*
import com.sentinel.data.rules.RuleStore
import com.sentinel.vpn.dns.DohUdpResolver
import com.sentinel.vpn.dns.DnsCache
import com.sentinel.vpn.tun.ConnectionPackageResolver
import com.sentinel.vpn.tun.TunSpec
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.koin.core.Koin
import org.koin.core.context.GlobalContext

/** 薄封装：所有状态机逻辑在 [VpnController]。 */
class SentinelVpnService : VpnService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: VpnController? = null
    private var watcher: PackageWatcher? = null
    private var todayBlocked = 0
    private var warning: String? = null

    private inner class ServiceTunFactory : TunFactory {
        override fun establish(spec: TunSpec): TunHandle? {
            val builder = Builder().setSession("哨兵").setMtu(spec.mtu)
            spec.addresses.forEach { (ip, prefix) -> builder.addAddress(ip, prefix) }
            spec.dnsServers.forEach { builder.addDnsServer(it) }
            spec.routes.forEach { (ip, prefix) -> builder.addRoute(ip, prefix) }
            spec.disallowed.forEach { pkg ->
                try { builder.addDisallowedApplication(pkg) } catch (_: Exception) { /* 未安装的包不需要排除 */ }
            }
            val fd = builder.establish() ?: return null
            return PfdHandle(fd)
        }
    }

    private class PfdHandle(private val fd: ParcelFileDescriptor) : TunHandle {
        override val input = FileInputStream(fd.fileDescriptor)
        override val output = FileOutputStream(fd.fileDescriptor)
        override fun close() { try { fd.close() } catch (_: Exception) { } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val koin = GlobalContext.getOrNull()
        if (koin == null) { stopSelf(); return START_NOT_STICKY }
        startForeground(VpnNotification.NOTIFICATION_ID, VpnNotification.build(this, todayBlocked, warning))
        when (intent?.action) {
            VpnActions.PAUSE -> scope.launch {
                try { koin.get<GlobalStateRepository>().pauseFor() }
                catch (e: Exception) { currentCoroutineContext().ensureActive(); Log.w("SentinelVpn", "暂停失败", e) }
            }
            else -> if (controller == null) begin(koin)
        }
        return START_STICKY
    }

    private fun begin(koin: Koin) {
        val clock = koin.get<Clock>()
        val resolver = DohUdpResolver("https://223.5.5.5/dns-query", InetSocketAddress("223.5.5.5", 53),
            { protect(it) }, { protect(it) }, koin.get<DnsCache>())
        val c = VpnController(scope, ServiceTunFactory(), packageName, koin.get(), koin.get(), koin.get(), koin.get(),
            koin.get<RuleStore>(), koin.get(), koin.get(), koin.get(), resolver, ConnectionPackageResolver(this), clock,
            { scope.launch { stopSelf() } })
        controller = c
        scope.launch {
            try { watcher = PackageWatcher(this@SentinelVpnService, koin.get<AppRegistry>(), scope).also { it.start() } }
            catch (e: Exception) { currentCoroutineContext().ensureActive(); Log.w("SentinelVpn", "App 监听启动失败", e) }
            c.start()
        }
        scope.launch {
            koin.get<EventRepository>().observeTodayCount().collect {
                todayBlocked = it
                getSystemService(NotificationManager::class.java).notify(VpnNotification.NOTIFICATION_ID,
                    VpnNotification.build(this@SentinelVpnService, it, warning))
            }
        }
    }

    override fun onDestroy() {
        // 任何退出路径都先关闭 TUN。
        val c = controller; controller = null
        runCatching { watcher?.stop() }
        if (c != null) runBlocking { c.stop("服务已停止") }
        scope.cancel()
        super.onDestroy()
    }
}
