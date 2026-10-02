package com.sentinel.a11y.service

import android.graphics.Rect
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import com.sentinel.a11y.core.A11yAction
import com.sentinel.a11y.fakes.APP
import com.sentinel.rules.model.EventKind
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import kotlin.test.*

class ActionExecutorRegressionTest : ServiceTestData() {
    private fun node() = AccessibilityNodeInfo.obtain(View(context)).apply {
        packageName = APP
        isClickable = true
        setBoundsInScreen(Rect(10, 20, 50, 80))
    }

    @Test fun R11_01_failed_node_and_ancestor_clicks_never_dispatch_coordinate_gesture() = runTest {
        val service = Robolectric.buildService(OverlayHostService::class.java).create().get()
        val node = node()
        val parent = node()
        shadowOf(parent).addChild(node)
        shadowOf(node).setOnPerformActionListener { _, _ -> false }
        shadowOf(parent).setOnPerformActionListener { _, _ -> false }
        val serviceShadow = shadowOf(service)
        serviceShadow.setCanDispatchGestures(true)
        // 原节点仍属于 A，当前前台已切到另一应用。
        serviceShadow.setRootInActiveWindow(AccessibilityNodeInfo.obtain(View(context)).apply {
            packageName = "com.example.bank"
        })
        var automaticClicks = 0
        val overlay = OverlayToast(service)
        val executor = ActionExecutor(service, runtime, overlay, this, {}, { automaticClicks++ })
        executor.execute(listOf(A11yAction.Click(NodeViewAdapter(node), APP, "R11:close", EventKind.POPUP_CLOSED)))
        assertEquals(1, automaticClicks)
        assertEquals(listOf(AccessibilityNodeInfo.ACTION_CLICK), shadowOf(node).performedActions)
        assertEquals(listOf(AccessibilityNodeInfo.ACTION_CLICK), shadowOf(parent).performedActions)
        assertTrue(serviceShadow.gesturesDispatched.isEmpty(), "failed stale nodes must never trigger dispatchGesture")
        overlay.close()
    }

    @Test fun R11_02_successful_node_click_does_not_click_ancestor() = runTest {
        val service = Robolectric.buildService(OverlayHostService::class.java).create().get()
        val node = node()
        val parent = node()
        shadowOf(parent).addChild(node)
        shadowOf(node).setOnPerformActionListener { _, _ -> true }
        val overlay = OverlayToast(service)
        ActionExecutor(service, runtime, overlay, this, {}, {}).execute(listOf(
            A11yAction.Click(NodeViewAdapter(node), APP, "R11:close", EventKind.POPUP_CLOSED)))
        assertEquals(listOf(AccessibilityNodeInfo.ACTION_CLICK), shadowOf(node).performedActions)
        assertTrue(shadowOf(parent).performedActions.isEmpty())
        assertTrue(shadowOf(service).gesturesDispatched.isEmpty())
        overlay.close()
    }

    @Test fun R11_03_failed_node_can_still_use_successful_ancestor_ACTION_CLICK() = runTest {
        val service = Robolectric.buildService(OverlayHostService::class.java).create().get()
        val node = node()
        val parent = node()
        shadowOf(parent).addChild(node)
        shadowOf(node).setOnPerformActionListener { _, _ -> false }
        shadowOf(parent).setOnPerformActionListener { _, _ -> true }
        val overlay = OverlayToast(service)
        ActionExecutor(service, runtime, overlay, this, {}, {}).execute(listOf(
            A11yAction.Click(NodeViewAdapter(node), APP, "R11:close", EventKind.POPUP_CLOSED)))
        assertEquals(listOf(AccessibilityNodeInfo.ACTION_CLICK), shadowOf(node).performedActions)
        assertEquals(listOf(AccessibilityNodeInfo.ACTION_CLICK), shadowOf(parent).performedActions)
        assertTrue(shadowOf(service).gesturesDispatched.isEmpty())
        overlay.close()
    }
}
