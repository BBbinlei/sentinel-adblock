package com.sentinel.a11y.di

import android.content.Context
import android.util.Log
import com.sentinel.a11y.service.A11yNotifications
import com.sentinel.data.module.ModuleEntry
import com.sentinel.data.module.ProcessKind
import com.sentinel.data.repo.GlobalStateRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.core.Koin

class A11yEntry : ModuleEntry {
    override val id = "a11y"
    override val processes = setOf(ProcessKind.MAIN)
    override val koinModule = a11yModule

    override fun start(context: Context, scope: CoroutineScope, koin: Koin) {
        A11yNotifications.ensureChannel(context)
        scope.launch(Dispatchers.IO) {
            try {
                koin.get<GlobalStateRepository>().observe().map { it.ruleVersion }.distinctUntilChanged().collect {
                    try { koin.get<A11yRuntime>().reloadIndex() }
                    catch (e: Exception) {
                        currentCoroutineContext().ensureActive()
                        Log.w(A11yRuntime.TAG, "界面规则加载失败", e)
                    }
                }
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                Log.w(A11yRuntime.TAG, "界面规则订阅失败", e)
            }
        }
    }
}
