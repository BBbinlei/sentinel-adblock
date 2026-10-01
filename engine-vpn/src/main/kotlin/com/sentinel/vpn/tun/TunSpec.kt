package com.sentinel.vpn.tun

import com.sentinel.rules.catalog.HttpDnsCatalog
import java.net.InetAddress

data class TunSpec(val addresses: List<Pair<String, Int>>, val dnsServers: List<String>,
    val routes: List<Pair<String, Int>>, val disallowed: Set<String>, val mtu: Int) {
    companion object {
        val DNS_SERVERS = listOf("10.111.0.2", "fd11:1::2")
        fun build(selfPkg: String, excluded: Set<String>) = TunSpec(
            listOf("10.111.0.1" to 32, "fd11:1::1" to 128), DNS_SERVERS,
            listOf(DNS_SERVERS[0] to 32, DNS_SERVERS[1] to 128) + HttpDnsCatalog.cidrs.map {
                requireNotNull(InetAddress.getByAddress(it.address).hostAddress) to it.prefix
            }, excluded + selfPkg, 1500)
    }
}
