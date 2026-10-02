package com.sentinel.vpn.service

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.provider.Settings
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.sentinel.data.Clock
import com.sentinel.data.apps.AppRegistry
import com.sentinel.data.apps.PackageWatcher
import com.sentinel.data.repo.*
import com.sentinel.data.rules.RuleStore
import com.sentinel.data.db.EngineId
import com.sentinel.data.db.EngineState
import com.sentinel.data.db.EngineStatusEntity
import com.sentinel.vpn.health.*
import com.sentinel.vpn.dns.DohUdpResolver
import com.sentinel.vpn.dns.DnsCache
import com.sentinel.vpn.tun.ConnectionPackageResolver
import com.sentinel.vpn.tun.TunSpec
import java.net.InetSocketAddress
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
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
            val builder = Builder().setSession("哨兵").setMtu(spec.mtu).setBlocking(true)
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

    private class PfdHandle(fd: ParcelFileDescriptor) : TunHandle {
        override val input = ParcelFileDescriptor.AutoCloseInputStream(fd)
        override val output = ParcelFileDescriptor.AutoCloseOutputStream(fd)
        // AutoClose 关闭同一个 PFD；Android 的异步关闭会唤醒阻塞读，无重复描述符残留。
        @Synchronized override fun close() { try { input.close() } finally { output.close() } }
    }

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private class SettingsChecker(private val context: Context, private val history: SharedPreferences) : EnabledServicesChecker {
        fun remember(states: Map<EngineId, EngineStatusEntity>) {
            // C4: STOPPED 表示曾经运行；兼容升级前已停止或已降级的引擎，标记只增不删。
            val seen = states.filter { (id, status) ->
                status.state != EngineState.NOT_SETUP && !history.getBoolean(id.name, false)
            }.keys
            if (seen.isEmpty()) return
            val edit = history.edit()
            seen.forEach { edit.putBoolean(it.name, true) }
            if (!edit.commit()) Log.w("SentinelVpn", "服务运行历史保存失败")
        }
        private fun listed(key: String) = Settings.Secure.getString(context.contentResolver, key)
            .orEmpty().split(':').any { it.startsWith(context.packageName + "/") }
        override fun accessibilityEnabled() = listed(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        override fun notificationListenerEnabled() = listed("enabled_notification_listeners")
        override fun userWantsA11y() = history.getBoolean(EngineId.A11Y.name, false)
        override fun userWantsNotify() = history.getBoolean(EngineId.NOTIFY.name, false)
    }

    private fun startHealth(koin: Koin, c: VpnController) {
        val manager = getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
                val warning = PrivateDnsDetector.evaluate(lp.isPrivateDnsActive, lp.privateDnsServerName)
                scope.launch { c.onPrivateDns(warning) }
            }
        }
        try { manager.registerDefaultNetworkCallback(callback); networkCallback = callback }
        catch (e: Exception) { Log.w("SentinelVpn", "网络回调登记失败", e) }
        val status = koin.get<EngineStatusRepository>()
        scope.launch(Dispatchers.IO) {
            // 仅由 :vpn 进程维护本地历史；跨进程状态仍从 Room 获取。
            val checker = SettingsChecker(this@SentinelVpnService, getSharedPreferences("vpn_engine_history", MODE_PRIVATE))
            launch {
                try { status.observeAll().collect { checker.remember(it) } }
                catch (e: Exception) { currentCoroutineContext().ensureActive(); Log.w("SentinelVpn", "服务运行历史订阅失败", e) }
            }
            while (true) {
                try {
                    checker.remember(status.observeAll().first())
                    ServiceWatchdog(checker, status, ::notifyLost).checkOnce()
                } catch (e: Exception) { currentCoroutineContext().ensureActive(); Log.w("SentinelVpn", "守护检查失败", e) }
                delay(5 * 60_000L)
            }
        }
    }

    private fun notifyLost(engine: EngineId) {
        val text = if (engine == EngineId.A11Y) "无障碍服务已被关闭，请重新开启" else "通知使用权已被关闭，请重新开启"
        getSystemService(NotificationManager::class.java).notify(1000 + engine.ordinal + 2,
            Notification.Builder(this, VpnNotification.CHANNEL_ID).setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("哨兵").setContentText(text).setAutoCancel(true).build())
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
            { protect(it) }, { protect(it) }, koin.get<DnsCache>(),
            { success -> controller?.onUpstreamTransport(success) })
        val c = VpnController(scope, ServiceTunFactory(), packageName, koin.get(), koin.get(), koin.get(), koin.get(),
            koin.get<RuleStore>(), koin.get(), koin.get(), koin.get(), resolver, ConnectionPackageResolver(this), clock,
            { scope.launch { stopSelf() } })
        controller = c
        startHealth(koin, c)
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

    override fun onRevoke() {
        controller?.onRevoked() ?: stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        // 任何退出路径都先关闭 TUN。
        val c = controller; controller = null
        val wasActive = c?.closeTun("服务已停止") == true
        runCatching { watcher?.stop() }
        networkCallback?.let { runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) } }
        scope.cancel()
        // 独立于已取消的服务 scope；不阻塞主线程，控制器保留首次停止原因。
        if (c != null && (wasActive || c.state.value.let { it == EngineState.RUNNING || it == EngineState.DEGRADED })) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { c.stop("服务已停止") }
        }
        super.onDestroy()
    }
}
