package com.sentinel.data.module

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import org.koin.core.Koin
import org.koin.core.module.Module

enum class ProcessKind { MAIN, VPN }

interface ModuleEntry {
    val id: String
    val processes: Set<ProcessKind>
    val koinModule: Module
    fun start(context: Context, scope: CoroutineScope, koin: Koin)
}
