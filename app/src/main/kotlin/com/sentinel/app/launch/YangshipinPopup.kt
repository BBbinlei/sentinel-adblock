package com.sentinel.app.launch

import android.content.Context
import android.content.pm.PackageManager
import com.sentinel.data.db.RuleOrigin
import com.sentinel.data.repo.UserRuleRepository
import com.sentinel.data.rules.SubscriptionUpdater
import com.sentinel.rules.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Independent from launcher shortcuts: closing this popup requires the existing accessibility engine. */
object YangshipinPopup {
    const val PACKAGE = "com.cctv.yangshipin.app.androidp"
    const val RULE_ID = "sentinel:yangshipin:home-popup:305030"
    const val ACTIVITY = "com.tencent.videolite.android.ui.HomeActivity"
    // Observed modal hierarchy. It excludes fl_click / iv_image (the ad navigation targets).
    val rule = UiRule(RULE_ID, RuleScope.Page(PACKAGE, ACTIVITY),
        """FrameLayout > RelativeLayout > LinearLayout > ImageView[id="com.cctv.yangshipin.app.androidp:id/iv_close"][clickable=true]""",
        UiAction.CLICK, UiPhase.ANYTIME, "央视频首页广告关闭 · 华为实测 3.5.3.26910")
    fun supported(code: Long, name: String) = code == 305030L && name == "3.5.3.26910"
    fun supported(context: Context) = try {
        val pkg = context.packageManager.getPackageInfo(PACKAGE, 0)
        supported(pkg.longVersionCode, pkg.versionName.orEmpty())
    } catch (_: PackageManager.NameNotFoundException) { false }
}

class PopupRuleControl(private val users: UserRuleRepository, private val updater: SubscriptionUpdater) {
    val rules get() = users.observeAll()
    suspend fun setEnabled(context: Context, enabled: Boolean) {
        if (enabled) {
            check(YangshipinPopup.supported(context)) { "此版本尚未验证" }
            users.add(YangshipinPopup.rule, RuleOrigin.MANUAL)
        } else users.delete(YangshipinPopup.RULE_ID)
        updater.rebuildFromCache()
        context.getSharedPreferences("launch", Context.MODE_PRIVATE).edit().putBoolean("popupEnabled", enabled).apply()
    }
    suspend fun removeUnsupported(context: Context) {
        if (!context.getSharedPreferences("launch", Context.MODE_PRIVATE).getBoolean("popupEnabled", false)) return
        if (!YangshipinPopup.supported(context) && rules.first().any { it.id == YangshipinPopup.RULE_ID }) {
            users.delete(YangshipinPopup.RULE_ID)
            updater.rebuildFromCache()
        }
    }
}

/** Package upgrades invalidate the app-owned rule even while Sentinel is already running. */
class PopupPackageReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: android.content.Intent) {
        if (intent.data?.schemeSpecificPart != YangshipinPopup.PACKAGE) return
        val pending = goAsync()
        val control = org.koin.core.context.GlobalContext.getOrNull()?.getOrNull<PopupRuleControl>()
        if (control == null) { pending.finish(); return }
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try { control.removeUnsupported(context.applicationContext) }
            catch (e: Exception) { android.util.Log.w("SentinelPopup", "首页弹窗规则更新失败", e) }
            finally { pending.finish() }
        }
    }
}
