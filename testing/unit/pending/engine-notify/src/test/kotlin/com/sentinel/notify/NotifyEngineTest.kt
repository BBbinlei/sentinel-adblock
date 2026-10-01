package com.sentinel.notify

import com.sentinel.data.db.RuleOrigin
import com.sentinel.notify.core.*
import com.sentinel.notify.fakes.*
import com.sentinel.rules.model.EventKind
import kotlin.test.*
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NotifyEngineTest {
    // MT-NT-01
    @Test fun MT_NT_01_marketing_returns_cancel_with_title_for_event_detail() = runTest {
        val clock = FakeClock()
        val state = FakeNotifyState()
        val engine = NotifyEngine(SELF_PKG, state, DismissLearner(MemoryLearnerStore()) { clock.now })
        val notification = PostedNotification(MARKET_PKG, "marketing", "限时福利", "今日活动", false, "market:7")
        val cancel = assertIs<NotifyAction.Cancel>(engine.onPosted(notification))
        assertEquals(NotifyAction.Cancel(notification.key, MARKET_PKG, "builtin:notify:$MARKET_PKG", notification.title), cancel)

        // PLAN Task 4 规定的消费方式；由测试驱动假邻居，非真实服务日志验证（见 README 待确认 1）。
        val events = FakeEventRepository()
        events.log(cancel.pkg, EventKind.NOTIFICATION_CANCELLED, cancel.ruleId, detail = cancel.title)
        val event = events.events.single()
        assertEquals(EventKind.NOTIFICATION_CANCELLED, event.kind)
        assertEquals(notification.title, event.detail)
        assertEquals(MARKET_PKG, event.pkg)
        assertEquals(cancel.ruleId, event.ruleId)

        assertNull(engine.onPosted(notification.copy(title = "更新完成", text = "应用已更新")))
        state.disabledRules[MARKET_PKG] = setOf(cancel.ruleId)
        assertNull(engine.onPosted(notification))
    }

    // MT-NT-02
    @Test fun MT_NT_02_learning_acceptance_updates_rules_and_cancels_same_channel() = runTest {
        val pkg = "example.reader"
        val channel = "marketing"
        val clock = FakeClock()
        val state = FakeNotifyState()
        val engine = NotifyEngine(SELF_PKG, state, DismissLearner(MemoryLearnerStore()) { clock.now })
        val notification = PostedNotification(pkg, channel, "每日资讯", "今日内容", false, "reader:1")
        val users = FakeUserRuleRepository()
        val updater = FakeSubscriptionUpdater(users, state)
        assertNull(engine.onPosted(notification))
        repeat(2) { assertNull(engine.onUserDismissed(pkg, channel)) }
        val ask = assertIs<NotifyAction.AskLearn>(engine.onUserDismissed(pkg, channel))
        assertEquals("learn:notify:$pkg:$channel", ask.rule.id)
        assertEquals(pkg, ask.rule.pkg)
        assertEquals(channel, ask.rule.channelId)
        assertEquals(emptyList(), ask.rule.keywords)
        assertEquals(emptyList(), users.added)
        assertEquals(0, updater.rebuildCalls)
        assertNull(engine.onPosted(notification))

        // NotifyEngine 没有接受入口；把 Task 4 的接受结果作为假数据层输入（见 README 待确认 2）。
        users.add(ask.rule, RuleOrigin.LEARNED)
        updater.rebuildFromCache()
        assertEquals(listOf(ask.rule to RuleOrigin.LEARNED), users.added)
        assertEquals(1, updater.rebuildCalls)
        assertEquals(listOf(ask.rule), state.userRules)
        val next = notification.copy(title = "普通资讯", text = "不含营销关键词", key = "reader:2")
        assertEquals(NotifyAction.Cancel(next.key, pkg, ask.rule.id, next.title), engine.onPosted(next))
        assertNull(engine.onPosted(next.copy(channelId = "downloads")))
        assertNull(engine.onPosted(next.copy(pkg = "example.other")))
    }
}
