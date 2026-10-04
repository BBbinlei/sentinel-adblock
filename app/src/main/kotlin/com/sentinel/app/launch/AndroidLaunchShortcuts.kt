package com.sentinel.app.launch

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.widget.Toast

const val LAUNCH_ID = "launch_entry_id"
private fun shortcutId(id: String) = "sentinel-launch-$id"

class AndroidLaunchPort(private val context: Context) : LaunchPort {
    override fun installed(profile: LaunchProfile): InstalledTarget? = try {
        val pm = context.packageManager
        val pkg = pm.getPackageInfo(profile.packageName, 0)
        val activity = pm.getActivityInfo(ComponentName(profile.packageName, profile.component), PackageManager.MATCH_DISABLED_COMPONENTS)
        val permissionAllowed = activity.permission == null ||
            pm.checkPermission(activity.permission, context.packageName) == PackageManager.PERMISSION_GRANTED
        val componentSetting = pm.getComponentEnabledSetting(ComponentName(profile.packageName, profile.component))
        val enabled = when (componentSetting) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> activity.enabled
            else -> false
        }
        InstalledTarget(pkg.longVersionCode, pkg.versionName.orEmpty(),
            activity.exported && enabled && activity.applicationInfo.enabled && permissionAllowed)
    } catch (_: PackageManager.NameNotFoundException) { null }

    override fun startEntry(profile: LaunchProfile): Boolean = start(entryIntent(profile))
    override fun startNormal(packageName: String): Boolean =
        context.packageManager.getLaunchIntentForPackage(packageName)?.let(::start) ?: false
    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (_: RuntimeException) { false }
}
internal fun entryIntent(profile: LaunchProfile) = Intent(
    if (profile.uri == null) Intent.ACTION_MAIN else Intent.ACTION_VIEW,
).apply {
    component = ComponentName(profile.packageName, profile.component)
    profile.uri?.let { data = Uri.parse(it); addCategory(Intent.CATEGORY_BROWSABLE) }
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
}

class LaunchShortcuts(private val context: Context) {
    private val policy = LaunchCatalog.policy
    private val port = AndroidLaunchPort(context)
    private val manager get() = context.getSystemService(ShortcutManager::class.java)
    fun status(profile: LaunchProfile) = policy.status(profile.id, port.installed(profile))
    fun pinned(profile: LaunchProfile) = try {
        manager?.pinnedShortcuts?.any { it.id == shortcutId(profile.id) && it.isEnabled } == true
    } catch (_: RuntimeException) { false }
    /** Call off the main thread while the requesting activity remains foreground. */
    fun request(profile: LaunchProfile): PinFeedback {
        if (policy.byId(profile.id) != profile || status(profile) != LaunchStatus.VERIFIED) return PinFeedback.REJECTED
        return try {
            val sm = manager ?: return PinFeedback.UNSUPPORTED
            if (!sm.isRequestPinShortcutSupported) return PinFeedback.UNSUPPORTED
            val drawable = context.packageManager.getApplicationIcon(profile.packageName)
            val bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, 192, 192); drawable.draw(Canvas(bitmap))
            val launch = Intent(context, ShortcutLaunchActivity::class.java)
                .setAction(Intent.ACTION_VIEW).putExtra(LAUNCH_ID, profile.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            val info = ShortcutInfo.Builder(context, shortcutId(profile.id))
                .setShortLabel("${profile.label}·快捷启动")
                .setLongLabel("${profile.label}·快捷启动")
                .setIcon(Icon.createWithBitmap(bitmap)).setIntent(launch).build()
            val callback = PendingIntent.getBroadcast(context, profile.id.hashCode(),
                Intent(context, ShortcutPinnedReceiver::class.java).putExtra(LAUNCH_ID, profile.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val accepted = sm.requestPinShortcut(info, callback.intentSender)
            pinFeedback(true, accepted, pinned(profile))
        } catch (_: PackageManager.NameNotFoundException) { PinFeedback.REJECTED }
        catch (_: RuntimeException) { PinFeedback.REJECTED }
    }
}

class ShortcutPinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val profile = LaunchCatalog.policy.byId(intent.getStringExtra(LAUNCH_ID)) ?: return
        if (LaunchShortcuts(context).pinned(profile)) {
            Toast.makeText(context, "${profile.label}·快捷启动已添加到桌面", Toast.LENGTH_LONG).show()
        }
    }
}

/** External callers can supply an ID, never a component, URI or arbitrary extra. */
class ShortcutLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val result = LaunchController(LaunchCatalog.policy, AndroidLaunchPort(this))
            .launch(intent.getStringExtra(LAUNCH_ID))
        val message = when (result) {
            LaunchOutcome.STARTED -> null
            LaunchOutcome.FALLBACK -> "快捷入口需要重新验证，已使用普通启动"
            LaunchOutcome.UNAVAILABLE -> "无法启动目标应用，请检查是否已安装或启用"
            LaunchOutcome.REJECTED -> "未知启动入口"
        }
        message?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        finish()
    }
}
