package com.sentinel.vpn.dns

import com.sentinel.vpn.fakes.Wire
import kotlin.test.*
import org.junit.Test

class DnsMessageTest {
    @Test fun `UT-VP-2-08 question parsing offsets compression and lowercase`() {
        val q = Wire.query("ADS.Example.TEST", id = 0xbeef)
        assertEquals(DnsQuestion(0xbeef, "ads.example.test", 1), DnsMessage.parseQuestion(q, 0, q.size))
        val padded = byteArrayOf(9, 8, 7) + q + byteArrayOf(6)
        assertEquals(DnsQuestion(0xbeef, "ads.example.test", 1), DnsMessage.parseQuestion(padded, 3, q.size))
        val compressed = q.copyOfRange(0, 12) + byteArrayOf(0xc0.toByte(), 18, 0, 1, 0, 1) + q.copyOfRange(12, q.size - 4)
        assertEquals(DnsQuestion(0xbeef, "ads.example.test", 1), DnsMessage.parseQuestion(compressed, 0, compressed.size))
        val cycle = compressed.copyOf().also { it[13] = 12 }
        assertNull(DnsMessage.parseQuestion(cycle, 0, cycle.size))
        for (n in 0 until q.size) assertNull(DnsMessage.parseQuestion(q.copyOf(n), 0, n))
    }
    @Test fun `UT-VP-2-09 blocked answers by type and failure ID`() {
        for ((type, address) in listOf(1 to ByteArray(4), 28 to ByteArray(16), 65 to null)) {
            val q = Wire.query(type = type); val response = DnsMessage.blockedResponse(q)
            Wire.assertDnsAnswer(response, q, address)
        }
        val q = Wire.query(id = 0xdead); val fail = DnsMessage.servFail(q)
        assertEquals(0xdead, Wire.u16(fail, 0)); assertEquals(2, Wire.u16(fail, 2) and 15); assertEquals(0, Wire.u16(fail, 6))
        val answer = Wire.answer(q); val original = answer.copyOf(); val changed = DnsMessage.withId(answer, 0x4321)
        assertEquals(0x4321, Wire.u16(changed, 0)); assertContentEquals(original.copyOfRange(2, original.size), changed.copyOfRange(2, changed.size))
    }
}
