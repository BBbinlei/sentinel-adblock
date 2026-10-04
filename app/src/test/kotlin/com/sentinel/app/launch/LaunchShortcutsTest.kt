package com.sentinel.app.launch

import android.content.pm.*
import com.sentinel.app.fakes.TestApplication
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [35])
class LaunchShortcutsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val profile get() = LaunchCatalog.policy.byId("cainiao")!!
    private fun install() {
        val app = ApplicationInfo().apply { packageName = profile.packageName; enabled = true }
        val activity = ActivityInfo().apply { name = profile.component; packageName = profile.packageName
            applicationInfo = app; enabled = true; exported = true }
        shadowOf(context.packageManager).setApplicationIcon(profile.packageName, android.graphics.drawable.ColorDrawable(android.graphics.Color.BLUE))
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = profile.packageName; versionName = profile.versionName
            setLongVersionCode(profile.versionCode); applicationInfo = app; activities = arrayOf(activity)
        })
    }
    @Test fun unsupportedDesktopProducesNoPinnedEntry() {
        install()
        val sm = context.getSystemService(ShortcutManager::class.java)
        shadowOf(sm).setIsRequestPinShortcutSupported(false)
        assertEquals(PinFeedback.UNSUPPORTED, LaunchShortcuts(context).request(profile))
        assertTrue(sm.pinnedShortcuts.isEmpty())
    }
    @Test fun supportedDesktopPinsOnlyWrapperWithCatalogId() {
        install()
        val sm = context.getSystemService(ShortcutManager::class.java)
        shadowOf(sm).setIsRequestPinShortcutSupported(true)
        assertEquals(PinFeedback.CONFIRMED, LaunchShortcuts(context).request(profile))
        val info = sm.pinnedShortcuts.single()
        assertEquals("菜鸟·快捷启动", info.shortLabel.toString())
        assertEquals(ShortcutLaunchActivity::class.java.name, info.intent!!.component!!.className)
        assertEquals(profile.id, info.intent!!.getStringExtra(LAUNCH_ID))
        assertNull(info.intent!!.data)
        assertEquals(setOf(LAUNCH_ID), info.intent!!.extras!!.keySet())
    }
    @Test fun alteredUnknownOrMismatchedProfilesCannotPin() {
        install()
        val sm = context.getSystemService(ShortcutManager::class.java)
        shadowOf(sm).setIsRequestPinShortcutSupported(true)
        val service = LaunchShortcuts(context)
        assertEquals(PinFeedback.REJECTED, service.request(profile.copy(component = "evil")))
        assertEquals(PinFeedback.REJECTED, service.request(profile.copy(id = "unknown")))
        shadowOf(context.packageManager).removePackage(profile.packageName)
        assertEquals(PinFeedback.REJECTED, service.request(profile))
        assertTrue(sm.pinnedShortcuts.isEmpty())
    }
}
