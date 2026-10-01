package com.sentinel.vpn.tun

import com.sentinel.rules.catalog.HttpDnsCatalog
import com.sentinel.vpn.fakes.Wire
import kotlin.test.*
import org.junit.Test

class TunSpecTest {
    @Test fun `UT-VP-4-01 routes include virtual DNS and every HttpDNS CIDR`() {
        val spec = TunSpec.build("com.sentinel.adblock", setOf("bank.app", "off.app"))
        assertEquals(setOf("10.111.0.1" to 32, "fd11:1::1" to 128), spec.addresses.toSet())
        assertEquals(setOf("10.111.0.2", "fd11:1::2"), spec.dnsServers.toSet()); assertEquals(1500, spec.mtu)
        assertEquals(setOf("com.sentinel.adblock", "bank.app", "off.app"), spec.disallowed)
        val routes = spec.routes.map { (address, prefix) -> Wire.ip(address).toList() to prefix }.toSet()
        val expected = HttpDnsCatalog.cidrs.map { it.address.toList() to it.prefix }.toSet() +
            setOf(Wire.ip("10.111.0.2").toList() to 32, Wire.ip("fd11:1::2").toList() to 128)
        assertEquals(expected, routes) // 不允许默认路由接管正常业务流量。
    }
}
