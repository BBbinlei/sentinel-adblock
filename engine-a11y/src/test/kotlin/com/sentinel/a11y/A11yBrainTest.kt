package com.sentinel.a11y

import com.sentinel.a11y.core.*
import com.sentinel.a11y.fakes.*
import com.sentinel.rules.model.*
import com.sentinel.rules.ui.*
import kotlin.test.*
import org.junit.Test

class A11yBrainTest {
    private val clock = TestClock()
    private val state = FakeA11yState()
    private val volume = FakeVolume(8)
    private val store = MemoryStore()
    private val brain = A11yBrain(state, RewardedHandler(VolumeKeeper(volume, store), clock::read),
        clock::read, ignored = { setOf(SYSTEM_UI, IME, HOME, "com.sentinel.adblock") },
        launchers = { setOf(HOME) }, labelToPkg = { if (it == "阅读器") APP else null })

    private fun input(i: UiInput, root: NodeView? = null): List<A11yAction> {
        clock.now = when (i) {
            is UiInput.WindowChanged -> i.ts
            is UiInput.Interaction -> i.ts
            is UiInput.Clicked -> i.ts
        }
        return brain.onInput(i, root)
    }

    private fun start() {
        input(window(HOME, clock.now))
        input(window(APP, clock.now + 100, ACTIVITY))
    }

    @Test fun MT_AY_01_cold_launch_splash_returns_click_and_event_kind() {
        input(window(HOME, 100))
        val root = snapshot("splash-1")
        val actions = input(window(APP, 1_000, ACTIVITY), root)
        val click = assertIs<A11yAction.Click>(actions.single())
        assertSame(root.children.single(), click.node)
        assertEquals(APP, click.pkg)
        assertEquals("builtin:splash-skip", click.ruleId)
        assertEquals(EventKind.SPLASH_SKIPPED, click.kind)
    }

    @Test fun MT_AY_02_rewarded_flow_opens_window_mutes_closes_and_restores() {
        start()
        val entry = input(clicked(text = "看视频领奖励", ts = 2_000))
        assertEquals(listOf(A11yAction.OpenRewardWindow(APP)), entry)
        val ad = SnapshotNode(children = listOf(SnapshotNode(text = "奖励将于3秒后发放")))
        val waiting = input(window(APP, 2_100, "$APP.RewardedActivity"), ad)
        assertContains(waiting, A11yAction.SetMuted(true))
        assertEquals(0, volume.value)
        assertEquals(8, store.getInt("saved_music_volume"))
        val close = SnapshotNode(desc = "关闭", clickable = true)
        val countdown = SnapshotNode(children = listOf(SnapshotNode(text = "2秒"), close))
        assertTrue(input(window(APP, 2_500, "$APP.RewardedActivity"), countdown)
            .none { it is A11yAction.Click })
        val finishedPage = SnapshotNode(children = listOf(close))
        val closeAction = input(window(APP, 5_500, "$APP.RewardedActivity"), finishedPage)
            .filterIsInstance<A11yAction.Click>().single()
        assertSame(close, closeAction.node)
        assertEquals(APP, closeAction.pkg)
        input(window("com.example.other", 6_000))
        assertEquals(8, volume.value)
        assertNull(store.getInt("saved_music_volume"))
        assertEquals(listOf(0, 8), volume.writes)
        assertTrue(brain.onTick().isEmpty())
    }

    @Test fun MT_AY_03_jumpback_then_external_undo_exception_prevents_repeat() {
        start()
        val reverted = input(window(PINDUODUO, 3_000))
            .filterIsInstance<A11yAction.Revert>().single()
        assertEquals(RevertJump(APP, PINDUODUO, "launch"), reverted.jump)

        // 当前 A11yBrain 接口没有撤销入口。模拟外部撤销已经写入相邻 data 层后的快照。
        // 不伪造 ActionExecutor/Receiver，也不声称验证了例外写入及 USER_UNDO 发射；见 README 待确认 5。
        state.exceptions += reverted.jump.source to reverted.jump.target
        assertTrue(state.excepted(APP, PINDUODUO))
        input(window(HOME, 10_000))
        input(window(APP, 10_100, ACTIVITY))
        val repeated = input(window(PINDUODUO, 12_000))
        assertTrue(repeated.none { it is A11yAction.Revert })
        // 例外按源/目标隔离，不应停用所有跳转。
        input(window(HOME, 20_000))
        input(window(APP, 20_100, ACTIVITY))
        val differentTarget = input(window(TAOBAO, 22_000)).filterIsInstance<A11yAction.Revert>().single()
        assertEquals(RevertJump(APP, TAOBAO, "launch"), differentTarget.jump)
    }

    @Test fun MT_AY_04_manual_close_after_one_second_proposes_page_rule() {
        state.rules = UiRuleIndex.build(emptyList()) // 本窗口不存在自动点击，学习用户关闭动作。
        start()
        input(window(APP, 2_000, ACTIVITY), snapshot("popup-1"))
        val action = input(clicked(ts = 3_000), snapshot("normal-1"))
            .filterIsInstance<A11yAction.ProposeRule>().single()
        val rule = action.rule
        assertEquals(RuleScope.Page(APP, ACTIVITY), rule.scope)
        assertEquals("""[vid="close"]""", rule.selector)
        assertEquals(UiAction.CLICK, rule.action)
        assertEquals(UiPhase.ANYTIME, rule.phase)
        assertEquals("learned", rule.source)
        assertSame(snapshotNodeForClose, Selector.parse(rule.selector).findFirst(snapshotNodeForClose))
    }

    private val snapshotNodeForClose = SnapshotNode(text = "关闭", viewId = "$APP:id/close")

    @Test fun MT_AY_05_dependency_exception_is_isolated_and_brain_can_recover() {
        start()
        state.cfgFailure = IllegalStateException("configuration snapshot unavailable")
        // 异常发生在尚未收集任何动作的输入中，空列表符合模块测试与“保留已收集动作”的契约。
        val actions = input(window(APP, 2_000, ACTIVITY), snapshot("splash-1"))
        assertTrue(actions.isEmpty()) // 调用若抛异常，测试会直接失败。
        state.cfgFailure = null
        val recovered = input(window(APP, 3_100, ACTIVITY), snapshot("splash-1"))
            .filterIsInstance<A11yAction.Click>().single()
        assertEquals(EventKind.SPLASH_SKIPPED, recovered.kind)
        assertEquals("builtin:splash-skip", recovered.ruleId)
    }
}
