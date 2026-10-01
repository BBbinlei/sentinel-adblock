package com.sentinel.vpn.tun

import android.content.Context
import android.net.ConnectivityManager
import android.system.OsConstants
import com.sentinel.vpn.packet.*
import java.net.*

fun interface PackageResolver { fun pkgFor(pkt: IpPacket): String? }
class ConnectionPackageResolver(private val context: Context) : PackageResolver {
    override fun pkgFor(pkt: IpPacket): String? = try {
        val protocol = when (pkt.proto) { Proto.UDP -> OsConstants.IPPROTO_UDP; Proto.TCP -> OsConstants.IPPROTO_TCP; else -> 0 }
        if (protocol == 0) null else {
            val uid = context.getSystemService(ConnectivityManager::class.java).getConnectionOwnerUid(protocol,
                InetSocketAddress(InetAddress.getByAddress(pkt.src), pkt.srcPort),
                InetSocketAddress(InetAddress.getByAddress(pkt.dst), pkt.dstPort))
            // Shared UIDs cannot identify a single app; use the documented default policy.
            context.packageManager.getPackagesForUid(uid)?.singleOrNull()
        }
    } catch (_: Exception) { null }
}
