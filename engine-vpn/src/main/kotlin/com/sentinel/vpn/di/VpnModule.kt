package com.sentinel.vpn.di

import com.sentinel.data.Clock
import com.sentinel.vpn.dns.DnsCache
import org.koin.dsl.module

/** 只在 :vpn 进程加载；主进程不加载。 */
val vpnModule = module {
    single { DnsCache(clock = get<Clock>()::now) }
}
