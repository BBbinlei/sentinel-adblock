package com.sentinel.vpn.tun

import com.sentinel.rules.catalog.HttpDnsCatalog
import com.sentinel.rules.domain.DomainMatcher
import com.sentinel.vpn.decide.*
import com.sentinel.vpn.dns.*
import com.sentinel.vpn.packet.*
import java.io.*
import java.net.InetAddress
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*

interface DecisionSource { fun matcher(): DomainMatcher?; fun context(pkg: String?): DnsContext }
sealed interface LoopEvent {
    data class Dns(val pkg: String?, val domain: String, val decision: DnsDecision) : LoopEvent
    data class HttpDnsRejected(val pkg: String?, val ip: String) : LoopEvent
}
class PacketLoop(private val input: InputStream, private val output: OutputStream,
    private val resolver: UpstreamResolver, private val decisions: DecisionSource,
    private val pkgs: PackageResolver, private val onEvent: (LoopEvent) -> Unit) {
    private val writes = Mutex()
    private val virtualDns = TunSpec.DNS_SERVERS.map { InetAddress.getByName(it).address }
    suspend fun run() = withContext(Dispatchers.IO) {
        coroutineScope {
            val queries = Semaphore(8)
            val buf = ByteArray(65535)
            while (isActive) {
                val n = input.read(buf)
                if (n < 0) break
                val pkt = IpPacket.parse(buf, n) ?: continue
                queries.acquire()
                launch {
                    try {
                        val reply = try { process(pkt) } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            null // A malformed query or failed dependency only drops this packet.
                        }
                        if (reply != null) writes.withLock { output.write(reply) }
                    } finally { queries.release() }
                }
            }
        }
    }
    private fun event(e: LoopEvent) { try { onEvent(e) } catch (e: Exception) { if (e is CancellationException) throw e } }
    private suspend fun process(pkt: IpPacket): ByteArray? {
        val dns = virtualDns.any { it.contentEquals(pkt.dst) }
        if (dns && pkt.proto == Proto.TCP) return PacketBuilder.tcpRst(pkt)
        if (dns && pkt.proto == Proto.UDP && pkt.dstPort == 53) {
            val query = pkt.raw.copyOfRange(pkt.payloadOffset, pkt.payloadOffset + pkt.payloadLength)
            val question = DnsMessage.parseQuestion(query, 0, query.size) ?: return null
            val pkg = pkgs.pkgFor(pkt)
            val decision = DnsDecider.decide(question.name, decisions.matcher(), decisions.context(pkg))
            val answer = if (decision.verdict == DnsVerdict.BLOCK) DnsMessage.blockedResponse(query) else resolver.resolve(query)
            event(LoopEvent.Dns(pkg, question.name, decision))
            return PacketBuilder.udpReply(pkt, answer)
        }
        if (HttpDnsCatalog.cidrs.any { it.contains(pkt.dst) }) {
            val reply = when (pkt.proto) {
                Proto.TCP -> PacketBuilder.tcpRst(pkt)
                Proto.UDP -> PacketBuilder.icmpPortUnreachable(pkt)
                else -> return null
            }
            event(LoopEvent.HttpDnsRejected(pkgs.pkgFor(pkt), requireNotNull(InetAddress.getByAddress(pkt.dst).hostAddress)))
            return reply
        }
        return null
    }
}
