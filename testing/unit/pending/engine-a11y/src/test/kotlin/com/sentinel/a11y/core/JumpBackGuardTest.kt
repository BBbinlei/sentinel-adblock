package com.sentinel.a11y.core

import com.sentinel.a11y.fakes.*
import com.sentinel.data.db.ProtectLevel
import com.sentinel.data.policy.EffectiveConfig
import kotlin.test.*
import org.junit.Test

class JumpBackGuardTest {
    private val clock = TestClock()
    private val tracker = tracker()
    private val guard = JumpBackGuard(clock::read)
    private var cfg: EffectiveConfig = config()
    private val exceptions = mutableSetOf<Pair<String, String>>()
    private var disabled = emptySet<String>()

    private fun launch(pkg: String = APP, origin: String = HOME) {
        tracker.onInput(window(origin, 100))
        tracker.onInput(window(pkg, 1_000))
        clock.now = 1_000
    }

    // Guard 的输入要求是“直接切换”的 Transition；此处在 Tracker 重置源 launch 前判定。
    // origin 显式传入桌面/最近任务时，是该场景的直接来源；见 README 待确认 1～3。
    private fun transition(target: String, ts: Long = 3_000, source: String = APP): RevertJump? {
        clock.now = ts
        return guard.onTransition(Transition(source, target, ts), tracker,
            cfgOf = { pkg -> cfg.copy(pkg = pkg) }, disabledOf = { disabled },
            excepted = { src, tgt -> (src to tgt) in exceptions })
    }

    @Test fun UT_AY_6_01_unattended_launcher_jump_to_pinduoduo_reverts() {
        launch()
        assertEquals(RevertJump(APP, PINDUODUO, "launch"), transition(PINDUODUO))
        assertEquals(RevertJump(APP, PINDUODUO, "launch"), transition(PINDUODUO, 6_000))
        assertNull(transition(PINDUODUO, 6_001))
        cfg = config(jumpBack = false)
        assertNull(transition(PINDUODUO))
        cfg = config(level = ProtectLevel.OFF)
        assertNull(transition(PINDUODUO))
    }

    @Test fun UT_AY_6_02_shake_hint_during_use_reverts_to_source() {
        launch()
        clock.now = 20_000 // 已超过启动窗口，必须由 shake 触发。
        guard.onShakeHintSeen(APP)
        assertEquals(RevertJump(APP, TAOBAO, "shake"), transition(TAOBAO, 22_000))
        assertEquals(RevertJump(APP, TAOBAO, "shake"), transition(TAOBAO, 23_000))
        assertNull(transition(TAOBAO, 23_001))
        cfg = config(shake = false)
        assertNull(transition(TAOBAO, 22_000))
        cfg = config(level = ProtectLevel.OFF)
        assertNull(transition(TAOBAO, 22_000))
        cfg = config()
        tracker.onInput(UiInput.Interaction(APP, 20_500))
        assertNull(transition(TAOBAO, 22_000))
    }

    @Test fun UT_AY_6_03_click_after_launch_prevents_jumpback() {
        launch()
        tracker.onInput(clicked(ts = 2_000, text = "打开商品"))
        assertNull(transition(TAOBAO))
    }

    @Test fun UT_AY_6_04_notification_bar_open_is_not_an_ad_jump() {
        launch(pkg = TAOBAO, origin = SYSTEM_UI)
        assertFalse(assertNotNull(tracker.launch).fromLauncher)
        assertNull(transition(TAOBAO, source = SYSTEM_UI))
    }

    @Test fun UT_AY_6_05_wechat_link_has_user_interaction() {
        val wechat = "com.tencent.mm"
        launch(pkg = wechat)
        tracker.onInput(clicked(pkg = wechat, ts = 2_000, text = "拼多多链接"))
        assertNull(transition(PINDUODUO, source = wechat))
    }

    @Test fun UT_AY_6_06_share_sheet_selection_is_intentional() {
        launch(origin = "com.example.gallery")
        tracker.onInput(UiInput.Interaction(APP, 2_000))
        assertNull(transition(TAOBAO))
    }

    @Test fun UT_AY_6_07_scan_then_open_has_user_interaction() {
        launch()
        tracker.onInput(UiInput.Interaction(APP, 2_000))
        assertNull(transition(TAOBAO))
    }

    @Test fun UT_AY_6_08_home_between_apps_breaks_direct_source() {
        launch()
        assertNull(tracker.onInput(window(HOME, 2_000)))
        assertNull(transition(TAOBAO, source = HOME))
    }

    @Test fun UT_AY_6_09_recent_tasks_between_apps_breaks_direct_source() {
        launch()
        assertNull(tracker.onInput(window(SYSTEM_UI, 2_000)))
        assertNull(transition(TAOBAO, source = SYSTEM_UI))
    }

    @Test fun UT_AY_6_10_alipay_authorization_is_not_an_ad_landing() {
        launch()
        assertNull(transition("com.eg.android.AlipayGphone"))
    }

    @Test fun UT_AY_6_11_launcher_widget_opens_target_directly() {
        launch(pkg = TAOBAO)
        assertEquals(TAOBAO, tracker.launch?.pkg)
        assertNull(transition(TAOBAO, source = HOME))
    }

    @Test fun UT_AY_6_12_assistant_screen_is_not_launcher_origin() {
        val assistant = "com.coloros.assistantscreen"
        launch(origin = assistant)
        assertFalse(assertNotNull(tracker.launch).fromLauncher)
        assertNull(transition(TAOBAO))
    }

    @Test fun UT_AY_6_13_voice_assistant_is_not_launcher_origin() {
        launch(origin = "com.example.voiceassistant")
        assertFalse(assertNotNull(tracker.launch).fromLauncher)
        assertNull(transition(TAOBAO))
    }

    @Test fun UT_AY_6_14_split_screen_start_from_systemui() {
        launch(origin = SYSTEM_UI)
        assertFalse(assertNotNull(tracker.launch).fromLauncher)
        assertNull(transition(TAOBAO))
    }

    @Test fun UT_AY_6_15_launcher_shortcut_opens_target_directly() {
        launch(pkg = TAOBAO)
        assertNull(transition(TAOBAO, source = HOME))
    }

    @Test fun UT_AY_6_16_browser_link_has_user_interaction() {
        val browser = "com.example.browser"
        launch(pkg = browser)
        tracker.onInput(clicked(pkg = browser, ts = 2_000, text = "淘宝链接"))
        assertNull(transition(TAOBAO, source = browser))
    }

    @Test fun UT_AY_6_17_permission_dialog_during_launch_is_not_ad_jump() {
        launch()
        val original = tracker.launch
        assertNull(tracker.onInput(window(APP, 2_000, activity = null)))
        assertEquals(original, tracker.launch)
        assertNull(transition("com.android.permissioncontroller"))
    }

    @Test fun UT_AY_6_18_same_package_page_change_is_not_jump() {
        launch()
        val original = tracker.launch
        assertNull(tracker.onInput(window(APP, 2_000, "$APP.DetailActivity")))
        assertEquals(original, tracker.launch)
        assertNull(transition(APP))
    }

    @Test fun UT_AY_6_19_ime_popup_is_not_jump() {
        launch()
        assertNull(tracker.onInput(window(IME, 2_000)))
        assertEquals(APP, tracker.currentPkg)
        assertNull(transition(IME))
    }

    @Test fun UT_AY_6_20_incoming_call_is_not_ad_landing() {
        launch()
        assertNull(transition("com.android.incallui"))
    }

    @Test fun UT_AY_6_21_alarm_screen_is_not_ad_landing() {
        launch()
        assertNull(transition("com.android.deskclock"))
    }

    @Test fun UT_AY_6_22_scroll_in_launch_window_prevents_jumpback() {
        launch()
        tracker.onInput(UiInput.Interaction(APP, 2_000)) // 服务将滚动适配为 Interaction。
        assertNull(transition(TAOBAO, 5_000))
    }

    @Test fun UT_AY_6_23_saved_source_target_exception_prevents_both_reasons() {
        launch()
        exceptions += APP to TAOBAO
        assertNull(transition(TAOBAO))
        clock.now = 20_000
        guard.onShakeHintSeen(APP)
        assertNull(transition(TAOBAO, 21_000))
    }

    @Test fun UT_AY_6_24_disabled_jumpback_rule_prevents_both_reasons() {
        launch()
        disabled = setOf("builtin:jumpback")
        assertNull(transition(TAOBAO))
        clock.now = 20_000
        guard.onShakeHintSeen(APP)
        assertNull(transition(TAOBAO, 21_000))
    }
}
