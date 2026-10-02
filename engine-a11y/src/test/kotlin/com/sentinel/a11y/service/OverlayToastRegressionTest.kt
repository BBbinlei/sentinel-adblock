package com.sentinel.a11y.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import org.robolectric.shadows.ShadowLog
import java.lang.reflect.Proxy
import kotlin.test.*

/** 只替换窗口边界，Handler/Runnable、视图创建及失败清理仍由生产代码执行。 */
class WindowProbe {
    private val delegate = RuntimeEnvironment.getApplication().getSystemService(WindowManager::class.java)
    val added = mutableListOf<View>()
    val removed = mutableListOf<View>()
    var reject = false
    val manager = Proxy.newProxyInstance(WindowManager::class.java.classLoader,
        arrayOf(WindowManager::class.java)) { _, method, args ->
        when (method.name) {
            "addView" -> {
                added += args!![0] as View
                if (reject) throw WindowManager.BadTokenException("injected expired accessibility token")
                null
            }
            "removeView", "removeViewImmediate" -> { removed += args!![0] as View; null }
            else -> method.invoke(delegate, *(args ?: emptyArray()))
        }
    } as WindowManager
}

class OverlayHostService : AccessibilityService() {
    lateinit var windows: WindowProbe
    override fun getSystemService(name: String): Any? =
        if (name == Context.WINDOW_SERVICE && ::windows.isInitialized) windows.manager else super.getSystemService(name)
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class OverlayToastRegressionTest {
    private fun overlay(windows: WindowProbe): OverlayToast {
        val service = Robolectric.buildService(OverlayHostService::class.java).create().get()
        service.windows = windows
        return OverlayToast(service)
    }

    @Test fun R09_01_undo_bad_token_is_caught_inside_main_runnable_and_can_retry() {
        val windows = WindowProbe().apply { reject = true }
        val overlay = overlay(windows)
        overlay.showUndo("A → B", 3_000) { error("undo must not run during display") }
        assertTrue(windows.added.isEmpty(), "addView must be queued")
        shadowOf(Looper.getMainLooper()).idle() // 未捕获的 BadTokenException 会让此测试失败。
        assertEquals(1, windows.added.size, ShadowLog.getLogs().joinToString { "${it.msg}: ${it.throwable?.message}" })
        assertEquals(windows.added, windows.removed)
        assertTrue(ShadowLog.getLogs().any { it.throwable is WindowManager.BadTokenException })
        assertNull(ReflectionHelpers.getField<View?>(overlay, "current"))
        windows.reject = false
        overlay.showUndo("retry", 3_000) {}
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, windows.added.size)
        assertSame(windows.added.last(), ReflectionHelpers.getField<View>(overlay, "current"))
        overlay.close()
    }

    @Test fun R09_02_rewarded_bad_token_is_caught_and_cleans_partial_view() {
        val windows = WindowProbe().apply { reject = true }
        val overlay = overlay(windows)
        overlay.showRewardedAsk { _, _ -> error("choice must not run during display") }
        assertTrue(windows.added.isEmpty())
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, windows.added.size, ShadowLog.getLogs().joinToString { "${it.msg}: ${it.throwable?.message}" })
        assertEquals(windows.added, windows.removed)
        assertTrue(ShadowLog.getLogs().any { it.throwable is WindowManager.BadTokenException })
        assertNull(ReflectionHelpers.getField<View?>(overlay, "current"))
        overlay.close()
    }

    @Test fun R09_03_close_rejects_queued_and_new_displays() {
        val windows = WindowProbe()
        val overlay = overlay(windows)
        overlay.showUndo("queued", 3_000) {}
        overlay.showRewardedAsk { _, _ -> }
        overlay.close()
        overlay.close()
        overlay.showUndo("after close", 3_000) {}
        overlay.showRewardedAsk { _, _ -> }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(windows.added.isEmpty())
        assertTrue(windows.removed.isEmpty())
        assertNull(ReflectionHelpers.getField<View?>(overlay, "current"))
    }

    @Test fun R09_04_service_disconnect_closes_overlay_and_rejects_new_tasks() {
        val windows = WindowProbe()
        shadowOf(RuntimeEnvironment.getApplication()).setSystemService(Context.WINDOW_SERVICE, windows.manager)
        val service = Robolectric.buildService(SentinelAccessibilityService::class.java).create().get()
        val overlay = OverlayToast(service)
        ReflectionHelpers.setField(service, "overlay", overlay)
        overlay.showUndo("active", 3_000) {}
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, windows.added.size, ShadowLog.getLogs().joinToString { "${it.msg}: ${it.throwable?.message}" })
        overlay.showRewardedAsk { _, _ -> } // 断开前已排队。
        service.onUnbind(null)
        overlay.showUndo("disconnected", 3_000) {}
        overlay.showRewardedAsk { _, _ -> }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, windows.added.size, ShadowLog.getLogs().joinToString { "${it.msg}: ${it.throwable?.message}" })
        assertEquals(windows.added, windows.removed)
        assertNull(ReflectionHelpers.getField<View?>(overlay, "current"))
    }
}
