package com.sentinel.a11y.service

import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityRecord
import com.sentinel.a11y.di.A11yRuntime
import com.sentinel.a11y.fakes.*
import com.sentinel.data.db.EventDao
import com.sentinel.data.db.RewardWindowDao
import com.sentinel.rules.model.EventKind
import com.sentinel.rules.model.RuleScope
import com.sentinel.rules.ui.UiRuleIndex
import kotlinx.coroutines.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers
import kotlin.test.*

/** 模拟系统在回调返回后替换 source；记录 getter 次数以验证事件所有权。 */
@Implements(AccessibilityRecord::class)
class ReusedEventSourceShadow {
    var suppliedSource: AccessibilityNodeInfo? = null
    var reads = 0
    @Implementation fun getSource(): AccessibilityNodeInfo? { reads++; return suppliedSource }
}

@Config(shadows = [ReusedEventSourceShadow::class])
class AccessibilityEventOwnershipTest : ServiceTestData() {
    @After fun stopRuntime() { stopKoin() }

    private fun event(type: Int, node: AccessibilityNodeInfo): Pair<AccessibilityEvent, ReusedEventSourceShadow> {
        val event = AccessibilityEvent.obtain(type).apply { packageName = APP; className = ACTIVITY }
        // obtain 可能复用上一用例 recycle 后的对象，影子计数也要开始新一轮生命周期。
        val shadow = Shadow.extract<ReusedEventSourceShadow>(event).apply { suppliedSource = node; reads = 0 }
        return event to shadow
    }

    private fun node(text: String) = AccessibilityNodeInfo.obtain(View(context)).apply {
        packageName = APP
        className = "android.widget.Button"
        this.text = text
        isClickable = true
    }

    @Test fun R08_01_window_callback_captures_source_package_and_activity_before_event_reuse() = runTest {
        startKoin { modules(module { single<A11yRuntime> { runtime } }) }
        val service = Robolectric.buildService(SentinelAccessibilityService::class.java).create().get()
        val queued = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        ReflectionHelpers.getField<CoroutineScope>(service, "scope").cancel()
        ReflectionHelpers.setField(service, "scope", queued)
        ReflectionHelpers.setField(runtime, "index", UiRuleIndex.build(listOf(
            rule(id = "R08:original-page", scope = RuleScope.Page(APP, ACTIVITY)))))
        val brain = brain()
        val overlay = OverlayToast(service)
        ReflectionHelpers.setField(service, "brain", brain)
        ReflectionHelpers.setField(service, "executor", ActionExecutor(service, runtime, overlay, queued, {}, {}))
        val original = node("关闭")
        val replacement = node("正常页面")
        shadowOf(original).setOnPerformActionListener { _, _ -> true }
        shadowOf(service).setRootInActiveWindow(replacement)
        val (event, source) = event(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, original)
        try {
            service.onAccessibilityEvent(event)
            assertEquals(1, source.reads, "source must be read before the callback returns")
            assertTrue(shadowOf(original).performedActions.isEmpty(), "the IO coroutine is still queued")
            event.packageName = TAOBAO
            event.className = "$TAOBAO.ReplacementActivity"
            source.suppliedSource = replacement
            queued.coroutineContext[Job]!!.children.toList().joinAll()
            assertEquals(1, source.reads, "queued work must not read the reused event")
            assertEquals(listOf(AccessibilityNodeInfo.ACTION_CLICK), shadowOf(original).performedActions)
            assertTrue(shadowOf(replacement).performedActions.isEmpty())
            val logged = dao<EventDao>("eventDao").all().single()
            assertEquals(APP, logged.pkg)
            assertEquals("R08:original-page", logged.ruleId)
            assertEquals(EventKind.POPUP_CLOSED, logged.kind)
        } finally { queued.cancel(); overlay.close() }
    }

    @Test fun R08_02_click_callback_captures_node_text_before_event_recycle_and_replacement() = runTest {
        startKoin { modules(module { single<A11yRuntime> { runtime } }) }
        val service = Robolectric.buildService(SentinelAccessibilityService::class.java).create().get()
        val queued = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        ReflectionHelpers.getField<CoroutineScope>(service, "scope").cancel()
        ReflectionHelpers.setField(service, "scope", queued)
        val overlay = OverlayToast(service)
        ReflectionHelpers.setField(service, "brain", brain())
        ReflectionHelpers.setField(service, "executor", ActionExecutor(service, runtime, overlay, queued, {}, {}))
        val original = node("看视频领奖励")
        val (event, source) = event(AccessibilityEvent.TYPE_VIEW_CLICKED, original)
        try {
            service.onAccessibilityEvent(event)
            assertEquals(1, source.reads)
            assertTrue(dao<RewardWindowDao>("rewardWindowDao").all().isEmpty())
            event.recycle()
            source.suppliedSource = node("普通按钮")
            original.text = "已替换的节点文字"
            queued.coroutineContext[Job]!!.children.toList().joinAll()
            assertEquals(1, source.reads)
            assertEquals(APP, dao<RewardWindowDao>("rewardWindowDao").all().single().pkg)
        } finally { queued.cancel(); overlay.close() }
    }
}
