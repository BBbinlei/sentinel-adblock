package com.sentinel.a11y.core

import com.sentinel.a11y.fakes.*
import com.sentinel.data.db.RewardedMode
import com.sentinel.rules.ui.SnapshotNode
import kotlin.test.*
import org.junit.Test

class RewardedHandlerTest {
    private val clock = TestClock()
    private val volume = FakeVolume(7)
    private val store = MemoryStore()
    private val handler = RewardedHandler(VolumeKeeper(volume, store), clock::read)
    private val close = SnapshotNode(desc = "CLOSE", clickable = true)
    private fun page(countdown: String? = null) = SnapshotNode(children =
        if (countdown == null) listOf(close) else listOf(SnapshotNode(text = countdown), close))

    @Test fun UT_AY_5_01_silent_countdown_close_and_restore() {
        assertEquals(RewardedAction.OpenRewardWindow,
            handler.onClicked(clicked(text = "看视频领奖励"), RewardedMode.SILENT))
        handler.onRewardedPage(APP, RewardedMode.SILENT)
        assertEquals(0, volume.value)
        assertEquals(7, store.getInt("saved_music_volume"))
        for (countdown in listOf("奖励将于3秒后发放", "3 s", "2S", "1 秒")) {
            assertNull(handler.onContent(APP, page(countdown)))
            assertEquals(0, volume.value)
        }
        assertNull(handler.onContent("com.example.other", page()))
        assertNull(handler.onContent(APP, SnapshotNode(text = "恭喜获得奖励")))
        assertSame(close, assertIs<RewardedAction.ClickClose>(handler.onContent(APP, page())).node)
        // 核心只返回点击动作；模拟执行关闭后离开广告所在 App，触发明确的恢复入口。
        assertEquals(RewardedAction.Finished, handler.onForeground("com.example.other"))
        assertEquals(7, volume.value)
        assertNull(store.getInt("saved_music_volume"))
    }

    @Test fun UT_AY_5_02_leaving_app_mid_video_restores_volume() {
        handler.onRewardedPage(APP, RewardedMode.SILENT)
        assertNull(handler.onForeground(APP))
        assertEquals(0, volume.value)
        assertEquals(RewardedAction.Finished, handler.onForeground("com.example.other"))
        assertEquals(7, volume.value)
        assertNull(store.getInt("saved_music_volume"))
        assertNull(handler.onTick())
    }

    @Test fun UT_AY_5_03_timeout_after_ninety_seconds_restores_volume() {
        handler.onRewardedPage(APP, RewardedMode.SILENT)
        clock.now += 89_999
        assertNull(handler.onTick())
        assertEquals(0, volume.value)
        clock.now += 2
        assertEquals(RewardedAction.Finished, handler.onTick())
        assertEquals(7, volume.value)
        assertNull(store.getInt("saved_music_volume"))
        assertNull(handler.onTick())
    }

    @Test fun UT_AY_5_04_ask_waits_for_choice_before_muting() {
        assertEquals(RewardedAction.OpenRewardWindow,
            handler.onClicked(clicked(text = "看视频领奖励"), RewardedMode.ASK))
        assertEquals(RewardedAction.AskUser, handler.onRewardedPage(APP, RewardedMode.ASK))
        assertEquals(7, volume.value)
        assertTrue(volume.writes.isEmpty())
        assertNull(store.getInt("saved_music_volume"))
        assertNull(handler.onContent(APP, page()))
        handler.onUserChoice(false)
        assertEquals(7, volume.value)
        assertNull(handler.onContent(APP, page()))
        assertEquals(RewardedAction.AskUser, handler.onRewardedPage(APP, RewardedMode.ASK))
        handler.onUserChoice(true)
        assertEquals(0, volume.value)
        assertSame(close, assertIs<RewardedAction.ClickClose>(handler.onContent(APP, page())).node)
        handler.onForeground("com.example.other")
        assertEquals(7, volume.value)
    }

    @Test fun UT_AY_5_05_block_mode_never_opens_reward_window() {
        assertNull(handler.onClicked(clicked(text = "看视频领奖励"), RewardedMode.BLOCK))
        assertNull(handler.onRewardedPage(APP, RewardedMode.BLOCK))
        assertNull(handler.onContent(APP, page()))
        assertEquals(7, volume.value)
        assertTrue(volume.writes.isEmpty())
        assertTrue(store.values.isEmpty())
        assertNull(handler.onClicked(clicked(text = "搜索"), RewardedMode.SILENT))
    }
}
