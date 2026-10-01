package com.sentinel.app.di

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import com.sentinel.app.apps.AppDetailViewModel
import com.sentinel.app.apps.AppsViewModel
import com.sentinel.app.home.HomeViewModel
import com.sentinel.app.rules.RulesViewModel
import com.sentinel.data.db.SubscriptionDao
import com.sentinel.data.rules.SubscriptionUpdater
import com.sentinel.app.onboarding.AndroidSetupChecker
import com.sentinel.app.onboarding.OnboardingViewModel
import com.sentinel.app.onboarding.SetupChecker
import com.sentinel.system.shell.ShizukuGateway
import com.sentinel.system.shell.ShizukuState
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

const val PREFS_NAME = "sentinel"
const val PREF_ONBOARDING_DONE = "onboarding_done"

private const val PRIVATE_DNS_WARNING = "系统「私人 DNS」设为指定服务器，会让网络拦截失效，请改为「自动」或「关闭」"

/** 系统「私人 DNS」被设成指定服务器时返回警告文案，否则 null。 */
fun privateDnsWarning(context: Context): String? = try {
    val mode = Settings.Global.getString(context.contentResolver, "private_dns_mode")
    val host = Settings.Global.getString(context.contentResolver, "private_dns_specifier")
    if (mode == "hostname" && !host.isNullOrBlank()) PRIVATE_DNS_WARNING else null
} catch (e: Exception) {
    null
}

val appModule = module {
    single<SharedPreferences> { androidContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    single<SetupChecker> {
        // engine-system 的入口在主进程加载；取不到网关时 Shizuku 步骤显示「未完成」，不影响其他步骤
        val gateway = getOrNull<ShizukuGateway>()
        AndroidSetupChecker(
            androidContext(),
            shizukuReady = { gateway?.state() == ShizukuState.READY },
            requestShizuku = { gateway?.requestPermission() },
        )
    }
    factory { OnboardingViewModel(get(), get()) }
    factory {
        val context = androidContext()
        HomeViewModel(get(), get(), get(), get(), get(), get(), privateDnsWarning = { privateDnsWarning(context) })
    }
    factory { AppsViewModel(get(), get(), get()) }
    factory { (pkg: String) -> AppDetailViewModel(pkg, get(), get(), get()) }
    factory {
        val dao = get<SubscriptionDao>()
        val updater = get<SubscriptionUpdater>()
        RulesViewModel(
            subscriptions = dao.observeAll(),
            userRules = get(),
            saveSubscription = { dao.upsert(it) },
            updateAll = { updater.updateAll() },
            rebuildFromCache = { updater.rebuildFromCache() },
        )
    }
}
