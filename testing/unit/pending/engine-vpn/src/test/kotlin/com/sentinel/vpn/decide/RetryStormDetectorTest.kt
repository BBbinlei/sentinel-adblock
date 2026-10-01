package com.sentinel.vpn.decide

import kotlin.test.*
import org.junit.Test

class RetryStormDetectorTest {
    @Test fun `UT-VP-2-10 threshold and pair isolation`() {
        val detector = RetryStormDetector { 100_000L }
        repeat(9) { assertFalse(detector.record("a", "r")) }
        assertFalse(detector.record("b", "r")); assertFalse(detector.record("a", "other"))
        assertTrue(detector.record("a", "r")); assertFalse(detector.record("a", "r"))
    }
    @Test fun `UT-VP-2-11 cooldown expires after ten minutes`() {
        var now = 100_000L; val detector = RetryStormDetector { now }
        repeat(9) { assertFalse(detector.record("a", "r")) }; assertTrue(detector.record("a", "r"))
        now += 599_999L; repeat(10) { assertFalse(detector.record("a", "r")) }
        now++
        assertTrue(detector.record("a", "r")); assertFalse(detector.record("a", "r"))
    }
    @Test fun `UT-VP-2-12 records outside rolling minute do not count`() {
        var now = 100_000L; val detector = RetryStormDetector { now }
        repeat(9) { assertFalse(detector.record("a", "r")) }
        now += 60_001L
        repeat(9) { assertFalse(detector.record("a", "r")) }
        assertTrue(detector.record("a", "r"))
    }
}
