package com.sentinel.app.di

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import com.sentinel.app.home.HomeViewModel
import com.sentinel.app.onboarding.AndroidSetupChecker
import com.sentinel.app.onboarding.OnboardingViewModel
import com.sentinel.app.onboarding.SetupChecker
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
    // Shizuku 就绪状态在 engine-system 合并进 main 后接上（M5），此前该步骤始终显示「未完成」
    single<SetupChecker> { AndroidSetupChecker(androidContext(), shizukuReady = { false }) }
    factory { OnboardingViewModel(get(), get()) }
    factory {
        val context = androidContext()
        HomeViewModel(get(), get(), get(), get(), get(), get(), privateDnsWarning = { privateDnsWarning(context) })
    }
}
