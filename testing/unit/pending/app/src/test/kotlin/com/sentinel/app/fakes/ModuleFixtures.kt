package com.sentinel.app.fakes

import android.content.Context
import android.content.SharedPreferences
import com.sentinel.app.home.HomeViewModel
import com.sentinel.app.onboarding.OnboardingViewModel
import com.sentinel.app.onboarding.SetupChecker
import org.robolectric.RuntimeEnvironment
import com.sentinel.data.apps.AppRegistry
import com.sentinel.data.module.ModuleEntry
import com.sentinel.data.module.ProcessKind
import java.io.ByteArrayInputStream
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.dsl.module

/** A private in-memory ServiceLoader resource. Never registered on the test classpath. */
class EntryClassLoader(private vararg val types: Class<out ModuleEntry>) : ClassLoader(ModuleEntry::class.java.classLoader) {
    override fun getResources(name: String): java.util.Enumeration<URL> {
        if (name != "META-INF/services/${ModuleEntry::class.java.name}") return super.getResources(name)
        val bytes = types.joinToString("\n", postfix = "\n") { it.name }.toByteArray()
        val url = URL(null, "fixture:module-entries", object : URLStreamHandler() {
            override fun openConnection(u: URL) = object : URLConnection(u) {
                override fun connect() = Unit
                override fun getInputStream() = ByteArrayInputStream(bytes)
            }
        })
        return Collections.enumeration(listOf(url))
    }
}

object EntryStarts {
    val started = mutableListOf<String>()
    lateinit var data: FakeData
}

class MainEntry : ModuleEntry {
    override val id = "main-test"
    override val processes = setOf(ProcessKind.MAIN)
    override val koinModule: Module = module {}
    override fun start(context: Context, scope: CoroutineScope, koin: Koin) { EntryStarts.started += id }
}
class VpnEntry : ModuleEntry {
    override val id = "vpn-test"
    override val processes = setOf(ProcessKind.VPN)
    override val koinModule: Module = module {}
    override fun start(context: Context, scope: CoroutineScope, koin: Koin) { EntryStarts.started += id }
}
class BothEntry : ModuleEntry {
    override val id = "both-test"
    override val processes = ProcessKind.entries.toSet()
    override val koinModule: Module = module {}
    override fun start(context: Context, scope: CoroutineScope, koin: Koin) { EntryStarts.started += id }
}
class DuplicateEntry : ModuleEntry {
    override val id = "main-test"
    override val processes = setOf(ProcessKind.MAIN)
    override val koinModule: Module = module {}
    override fun start(context: Context, scope: CoroutineScope, koin: Koin) = Unit
}
class FailingEntry : ModuleEntry {
    override val id = "failing"
    override val processes = setOf(ProcessKind.MAIN)
    override val koinModule: Module = module { single<AppRegistry> { EntryStarts.data.registry } }
    override fun start(context: Context, scope: CoroutineScope, koin: Koin) {
        EntryStarts.started += id
        error("injected startup failure")
    }
}
class HealthyEntry : ModuleEntry {
    override val id = "healthy"
    override val processes = setOf(ProcessKind.MAIN)
    override val koinModule: Module = module {
        single<SharedPreferences> {
            RuntimeEnvironment.getApplication().getSharedPreferences("sentinel", Context.MODE_PRIVATE)
        }
        single<SetupChecker> { FakeSetupChecker() }
        factory {
            val d = EntryStarts.data
            HomeViewModel(d.global, d.events, d.statuses, d.overrides, d.apps, d.clock,
                privateDnsWarning = { null })
        }
        factory { OnboardingViewModel(get(), get()) }
    }
    override fun start(context: Context, scope: CoroutineScope, koin: Koin) { EntryStarts.started += id }
}
