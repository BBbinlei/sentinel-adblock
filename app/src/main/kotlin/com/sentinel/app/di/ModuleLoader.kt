package com.sentinel.app.di

import com.sentinel.data.module.ModuleEntry
import com.sentinel.data.module.ProcessKind
import java.util.ServiceConfigurationError
import java.util.ServiceLoader

/** 用 ServiceLoader 发现各模块的 [ModuleEntry]，app 代码里不列举任何模块。 */
object ModuleLoader {
    fun load(process: ProcessKind, loader: ClassLoader? = ModuleEntry::class.java.classLoader): List<ModuleEntry> {
        val all = mutableListOf<ModuleEntry>()
        val iterator = ServiceLoader.load(ModuleEntry::class.java, loader).iterator()
        while (true) {
            try {
                if (!iterator.hasNext()) break
                all += iterator.next()
            } catch (e: ServiceConfigurationError) {
                // 单个入口加载失败只会让该模块缺席，不能让 app 起不来
                android.util.Log.w("SentinelApp", "模块入口加载失败", e)
            }
        }
        val duplicated = all.groupBy { it.id }.filterValues { it.size > 1 }.keys
        check(duplicated.isEmpty()) { "ModuleEntry id 重复: $duplicated" }
        return all.filter { process in it.processes }
    }
}
