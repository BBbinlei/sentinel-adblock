package com.sentinel.app.di

import android.app.Application
import android.content.Context
import androidx.work.WorkerParameters
import com.sentinel.app.fakes.TestApplication
import com.sentinel.data.di.dataModule
import com.sentinel.data.module.ModuleEntry
import com.sentinel.data.module.ProcessKind
import java.util.ServiceLoader
import kotlinx.coroutines.CoroutineScope
import kotlin.test.*
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.koin.dsl.module
import org.koin.test.verify.verify
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
            CoroutineScope::class, WorkerParameters::class))
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
