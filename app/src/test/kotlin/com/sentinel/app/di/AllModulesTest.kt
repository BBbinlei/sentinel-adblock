package com.sentinel.app.di

import android.app.Application
import android.content.Context
import androidx.work.WorkerParameters
import com.sentinel.app.fakes.TestApplication
import com.sentinel.data.di.dataModule
import com.sentinel.data.module.ModuleEntry
import com.sentinel.data.module.ProcessKind
import java.util.ServiceLoader
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlin.test.*
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.koin.dsl.module
import org.koin.test.verify.verify
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import com.sentinel.vpn.dns.DnsCache
import com.sentinel.notify.core.DismissLearner
import com.sentinel.notify.di.NotifyRuntime
import com.sentinel.system.di.CurrentProfile
import com.sentinel.system.profile.ColorOsProfile
import com.sentinel.system.shell.ShizukuApi
import com.sentinel.system.shell.ShizukuGateway
import com.sentinel.system.ops.AppOpsSync
import com.sentinel.system.drift.DriftInspector
import com.sentinel.system.drift.SystemStatusReporter
import kotlinx.coroutines.flow.StateFlow
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [35])
class AllModulesTest {
    @Test fun UT_AP_1_01_data_and_all_discovered_entry_modules_verify_together() {
        val entries = ServiceLoader.load(ModuleEntry::class.java, ModuleEntry::class.java.classLoader).toList()
        assertTrue(entries.any { it.id == "data" }, "data must already be available at G3")
        val assembled = module { includes(dataModule, *entries.map { it.koinModule }.toTypedArray()) }
        // Only framework/runtime-supplied values are exempt, never application dependencies.
        assembled.verify(extraTypes = listOf(Context::class, Application::class,
            CoroutineScope::class, WorkerParameters::class, File::class),
            // These values are explicitly supplied by production module lambdas, not Koin get().
            injections = injectedParameters(
                definition<DnsCache>(Function0::class),
                definition<DismissLearner>(Function0::class),
                definition<NotifyRuntime>(String::class),
                definition<ShizukuGateway>(ShizukuApi::class, Function1::class),
                definition<CurrentProfile>(ColorOsProfile::class),
                definition<AppOpsSync>(ColorOsProfile::class, StateFlow::class),
                definition<DriftInspector>(ColorOsProfile::class, StateFlow::class),
                definition<SystemStatusReporter>(StateFlow::class),
            ))
    }
}

/** Build configuration excludes this category before G8; no @Ignore/Assume branch. */
interface G8Only

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [35])
@Category(G8Only::class)
class G8AllModulesTest {
    @Test fun UT_AP_1_04_g8_main_and_vpn_entry_sets_are_complete() {
        assertEquals(setOf("data", "a11y", "notify", "system", "guard"),
            ModuleLoader.load(ProcessKind.MAIN).map { it.id }.toSet())
        assertEquals(setOf("vpn"), ModuleLoader.load(ProcessKind.VPN).map { it.id }.toSet())
    }
}
