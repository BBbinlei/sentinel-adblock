package com.sentinel.vpn.health

import kotlin.test.*
import org.junit.Test

class PrivateDnsDetectorTest {
    @Test fun `UT-VP-6-01 only active named private DNS warns`() {
        val message = "系统「私人 DNS」设为指定服务器，会让网络拦截失效，请改为「自动」或「关闭」"
        assertEquals(message, PrivateDnsDetector.evaluate(true, "dns.example.test"))
        assertNull(PrivateDnsDetector.evaluate(true, null)); assertNull(PrivateDnsDetector.evaluate(false, null))
        assertNull(PrivateDnsDetector.evaluate(false, "dns.example.test"))
    }
}
