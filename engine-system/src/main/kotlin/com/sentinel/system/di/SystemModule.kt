package com.sentinel.system.di

import android.content.Context
import com.sentinel.system.drift.DriftInspector
import com.sentinel.system.drift.SystemStatusReporter
import com.sentinel.system.ops.AppOpsSync
import com.sentinel.system.ops.OpExecutor
import com.sentinel.system.profile.ColorOsProfile
import com.sentinel.system.profile.ProfileLoader
import com.sentinel.system.shell.*
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.io.File

/** Koin 不支持可空类型的绑定，用持有者提供「当前档案 ColorOsProfile?」。 */
class CurrentProfile(val profile: ColorOsProfile?)

private fun romVersion(): String = runCatching {
    val cls = Class.forName("android.os.SystemProperties")
    cls.getMethod("get", String::class.java, String::class.java).invoke(null, "ro.build.version.oplusrom", "") as String
}.getOrDefault("")

internal fun loadProfile(context: Context): ColorOsProfile? = runCatching {
    val assets = context.assets
    val profiles = (assets.list("profiles") ?: emptyArray()).filter { it.endsWith(".json") }
        .mapNotNull { name -> runCatching { ProfileLoader.parse(assets.open("profiles/$name").bufferedReader().readText()) }.getOrNull() }
    ProfileLoader.select(romVersion(), profiles)
}.getOrNull()

val systemModule = module {
    single { ShizukuGateway(AndroidShizukuApi(androidContext())) { bindShell(androidContext()) } }
    single<Shell> { get<ShizukuGateway>() }
    single { CurrentProfile(loadProfile(androidContext())) }
    single { OpExecutor(get<ShizukuGateway>(), get(), get(), undoFile = File(androidContext().filesDir, "system-undo.json")) }
    single { AppOpsSync(get<ShizukuGateway>(), get(), get<CurrentProfile>().profile, get(), get(), get<ShizukuGateway>().stateFlow) }
    single { DriftInspector(get(), get<CurrentProfile>().profile, get(), get<ShizukuGateway>().stateFlow) }
    single { SystemStatusReporter(get(), get<ShizukuGateway>().stateFlow, get<DriftInspector>().driftedCount, get<AppOpsSync>().pendingCount) }
}
