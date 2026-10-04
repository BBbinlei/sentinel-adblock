package com.sentinel.app.launch

import android.content.ComponentName
import android.content.Intent
import com.sentinel.app.fakes.TestApplication
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [31])
class OriginalEntryPolicyTest {
    private fun original() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(ComponentName(OriginalEntryPolicy.PKG, OriginalEntryPolicy.SPLASH))
    private fun matches(i: Intent? = original(), pkg: String = OriginalEntryPolicy.PKG, sdk: Int = 31,
        version: Long = 305030, name: String = "3.5.3.26910", available: Boolean = true) =
        OriginalEntryPolicy.matches(i, pkg, sdk, version, name, available)
    @Test fun verifiedOriginalLauncherIsIntercepted() { assertTrue(matches()) }
    @Test fun upgradedOrUnavailableTargetPassesThrough() {
        assertFalse(matches(version = 305031)); assertFalse(matches(name = "3.5.4")); assertFalse(matches(available = false))
    }
    @Test fun otherAppsAndUnknownIntentPassThrough() {
        assertFalse(matches(pkg = "com.cainiao.wireless")); assertFalse(matches(i = null))
        assertFalse(matches(i = original().setComponent(ComponentName("other", OriginalEntryPolicy.SPLASH))))
    }
    @Test fun deepLinksAndInternalStartsCannotLoop() {
        assertFalse(matches(i = original().setAction(Intent.ACTION_VIEW)))
        assertFalse(matches(i = Intent().setComponent(ComponentName(OriginalEntryPolicy.PKG, OriginalEntryPolicy.SPLASH))))
        assertFalse(matches(i = original().setComponent(ComponentName(OriginalEntryPolicy.PKG, OriginalEntryPolicy.OPEN))))
    }
    @Test fun unverifiedAndroidVersionPassesThrough() { assertFalse(matches(sdk = 35)) }
}
