package com.sentinel.vpn.di

import android.content.Context
import com.sentinel.data.module.ModuleEntry
import com.sentinel.data.module.ProcessKind
import com.sentinel.vpn.service.VpnNotification
import kotlinx.coroutines.CoroutineScope
import org.koin.core.Koin

class VpnEntry : ModuleEntry {
    override val id = "vpn"
    override val processes = setOf(ProcessKind.VPN)
    override val koinModule = vpnModule
    override fun start(context: Context, scope: CoroutineScope, koin: Koin) {
        VpnNotification.ensureChannel(context)
    }
}
