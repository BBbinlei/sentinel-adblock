package com.sentinel.app.di

import android.content.Context
import com.sentinel.app.MainActivity
import com.sentinel.app.SentinelApp
import com.sentinel.app.fakes.*
import com.sentinel.data.module.ProcessKind
import kotlin.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [35])
class ModuleLoaderTest : AppTest() {
    @Test fun UT_AP_1_02_filters_entries_using_declared_processes() {
        val loader = EntryClassLoader(MainEntry::class.java, VpnEntry::class.java, BothEntry::class.java)
        assertEquals(setOf("main-test", "both-test"), ModuleLoader.load(ProcessKind.MAIN, loader).map { it.id }.toSet())
        assertEquals(setOf("vpn-test", "both-test"), ModuleLoader.load(ProcessKind.VPN, loader).map { it.id }.toSet())
        ProcessKind.entries.forEach { process ->
            assertTrue(ModuleLoader.load(process, loader).all { process in it.processes })
        }
        // Available production entries, without demanding missing M4–M7 modules at G3.
        val vpn = ModuleLoader.load(ProcessKind.VPN)
        assertTrue(vpn.all { ProcessKind.VPN in it.processes })
        assertTrue(vpn.none { it.id in setOf("a11y", "notify", "system", "guard") })
        assertTrue(ModuleLoader.load(ProcessKind.MAIN).none { it.id == "vpn" })
    }

    @Test fun UT_AP_1_03_duplicate_ids_are_rejected() {
        val loader = EntryClassLoader(MainEntry::class.java, DuplicateEntry::class.java)
        assertFails { ModuleLoader.load(ProcessKind.MAIN, loader) }
    }

    @Test fun UT_AP_1_03_a_failed_start_does_not_prevent_later_entries_or_ui() {
        val loader = EntryClassLoader(FailingEntry::class.java, HealthyEntry::class.java)
        val thread = Thread.currentThread()
        val previous = thread.contextClassLoader
        EntryStarts.started.clear(); EntryStarts.data = data
        try {
            thread.contextClassLoader = loader
            // Runs the real production startup loop, not a test copy of it.
            val app = Robolectric.buildApplication(SentinelApp::class.java).create().get()
            assertEquals(listOf("failing", "healthy"), EntryStarts.started)
            app.getSharedPreferences("sentinel", Context.MODE_PRIVATE).edit()
                .putBoolean("onboarding_done", true).commit()
            val activity = Robolectric.buildActivity(MainActivity::class.java).setup()
            try {
                assertFalse(activity.get().isFinishing)
                assertNotNull(activity.get().window.decorView)
                assertEquals("哨兵", app.resources.getString(com.sentinel.app.R.string.app_name))
            } finally { activity.pause().stop().destroy() }
        } finally {
            thread.contextClassLoader = previous
            EntryStarts.started.clear()
        }
    }
}
