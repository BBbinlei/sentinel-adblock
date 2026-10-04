package com.sentinel.app.launch

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.sentinel.app.fakes.TestApplication
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [35])
class AndroidLaunchPortTest {
    private val profile = LaunchProfile("test", "com.test", "测试", 9, "9.1", "com.test.Home", null, true, "冷启动", "证据")
    private val context get() = RuntimeEnvironment.getApplication()
    private fun install(exported: Boolean = true) {
        val app = ApplicationInfo().apply { packageName = profile.packageName; enabled = true }
        val activity = ActivityInfo().apply { name = profile.component; packageName = profile.packageName
            applicationInfo = app; enabled = true; this.exported = exported }
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = profile.packageName; versionName = profile.versionName
            setLongVersionCode(profile.versionCode); applicationInfo = app; activities = arrayOf(activity)
        })
    }
    @Test fun readsExactVersionAndExportedEnabledState() {
        install()
        val port = AndroidLaunchPort(context)
        assertEquals(InstalledTarget(9, "9.1", true), port.installed(profile))
        context.packageManager.setComponentEnabledSetting(ComponentName(profile.packageName, profile.component),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0)
        assertFalse(port.installed(profile)!!.entryAvailable)
    }
    @Test fun nonExportedOrMissingEntryIsUnavailable() {
        install(exported = false)
        assertFalse(AndroidLaunchPort(context).installed(profile)!!.entryAvailable)
        assertNull(AndroidLaunchPort(context).installed(profile.copy(packageName = "absent")))
    }
    @Test fun externalExtrasCannotTurnWrapperIntoArbitraryIntent() {
        val intent = Intent(context, ShortcutLaunchActivity::class.java)
            .putExtra(LAUNCH_ID, "unknown").putExtra("component", "com.test.Home")
            .setData(android.net.Uri.parse("https://example.org"))
        val activity = Robolectric.buildActivity(ShortcutLaunchActivity::class.java, intent).create().get()
        assertTrue(activity.isFinishing)
        assertNull(shadowOf(context).nextStartedActivity)
    }
    @Test fun deepLinkIntentUsesOnlyCatalogFields() {
        val intent = entryIntent(profile.copy(uri = "test://home?from=third_h5"))
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(ComponentName(profile.packageName, profile.component), intent.component)
        assertEquals("test://home?from=third_h5", intent.dataString)
        assertNull(intent.extras)
    }
}
