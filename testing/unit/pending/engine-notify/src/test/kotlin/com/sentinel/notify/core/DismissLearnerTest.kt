package com.sentinel.notify.core

import com.sentinel.notify.fakes.DAY_MS
import com.sentinel.notify.fakes.FakeClock
import com.sentinel.notify.fakes.MemoryLearnerStore
import kotlin.test.*
import org.junit.Test

class DismissLearnerTest {
    private val pkg = "example.reader"
    private val channel = "marketing"

    // UT-NT-2-01
    @Test fun UT_NT_2_01_third_dismissal_returns_channel_rule() {
        val clock = FakeClock()
        val learner = DismissLearner(MemoryLearnerStore()) { clock.now }
        assertNull(learner.onUserDismissed(pkg, channel))
        clock.advance(1)
        assertNull(learner.onUserDismissed(pkg, channel))
        // 同渠道名的另一 App、同 App 的另一渠道不能合并计数。
        assertNull(learner.onUserDismissed("example.other", channel))
        assertNull(learner.onUserDismissed(pkg, "downloads"))
        clock.advance(1)
        val candidate = assertNotNull(learner.onUserDismissed(pkg, channel))
        assertEquals("learn:notify:$pkg:$channel", candidate.id)
        assertEquals(pkg, candidate.pkg)
        assertEquals(channel, candidate.channelId)
        assertEquals(emptyList(), candidate.keywords)
    }

    // UT-NT-2-02
    @Test fun UT_NT_2_02_old_dismissal_expires_but_recent_one_remains() {
        val clock = FakeClock()
        val learner = DismissLearner(MemoryLearnerStore()) { clock.now }
        assertNull(learner.onUserDismissed(pkg, channel))
        clock.advance(2 * DAY_MS)
        assertNull(learner.onUserDismissed(pkg, channel))
        clock.advance(5 * DAY_MS + 1)
        // 第一条已超过 7 天，第二条仍在窗口内；加本次只有两条。
        assertNull(learner.onUserDismissed(pkg, channel))
        clock.advance(1)
        assertNotNull(learner.onUserDismissed(pkg, channel))
    }

    // UT-NT-2-03
    @Test fun UT_NT_2_03_rejection_suppresses_for_thirty_days_then_can_ask_again() {
        val clock = FakeClock()
        val learner = DismissLearner(MemoryLearnerStore()) { clock.now }
        repeat(2) { assertNull(learner.onUserDismissed(pkg, channel)) }
        assertNotNull(learner.onUserDismissed(pkg, channel))
        learner.onRejected(pkg, channel)
        repeat(3) { assertNull(learner.onUserDismissed(pkg, channel)) }
        clock.advance(29 * DAY_MS)
        repeat(3) { assertNull(learner.onUserDismissed(pkg, channel)) }
        clock.advance(DAY_MS + 1)
        // 不约束拒绝期间是否继续计数；提供三个新事件，必须重新具备候选资格。
        learner.onUserDismissed(pkg, channel)
        learner.onUserDismissed(pkg, channel)
        val candidate = assertNotNull(learner.onUserDismissed(pkg, channel))
        assertEquals("learn:notify:$pkg:$channel", candidate.id)
    }

    // UT-NT-2-04
    @Test fun UT_NT_2_04_recreation_preserves_counts_and_rejection() {
        val clock = FakeClock()
        val store = MemoryLearnerStore()
        val first = DismissLearner(store) { clock.now }
        repeat(2) { assertNull(first.onUserDismissed(pkg, channel)) }
        assertFalse(assertNotNull(store.load()).isBlank())
        val restored = DismissLearner(store) { clock.now }
        assertNotNull(restored.onUserDismissed(pkg, channel))
        restored.onRejected(pkg, channel)
        val afterRejection = DismissLearner(store) { clock.now }
        repeat(3) { assertNull(afterRejection.onUserDismissed(pkg, channel)) }
    }

    // UT-NT-2-05
    @Test fun UT_NT_2_05_null_channel_never_produces_or_increments_channel_rule() {
        val clock = FakeClock()
        val learner = DismissLearner(MemoryLearnerStore()) { clock.now }
        repeat(4) { assertNull(learner.onUserDismissed(pkg, null)) }
        repeat(2) { assertNull(learner.onUserDismissed(pkg, channel)) }
        val candidate = assertNotNull(learner.onUserDismissed(pkg, channel))
        assertEquals(channel, candidate.channelId)
        assertEquals(emptyList(), candidate.keywords)
    }
}
