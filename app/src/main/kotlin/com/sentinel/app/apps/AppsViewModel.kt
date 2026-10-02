package com.sentinel.app.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sentinel.data.Clock
import com.sentinel.data.db.GlobalStateEntity
import com.sentinel.data.db.ProtectLevel
import com.sentinel.data.policy.EffectivePolicy
import com.sentinel.data.repo.AppConfigRepository
import com.sentinel.data.repo.EventRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AppRow(val pkg: String, val label: String, val blocked7d: Int, val level: ProtectLevel, val defaultAllowed: Boolean)

private const val SEVEN_DAYS_MS = 7 * 86_400_000L

class AppsViewModel(apps: AppConfigRepository, events: EventRepository, private val clock: Clock) : ViewModel() {
    private val query = MutableStateFlow("")

    val state: StateFlow<List<AppRow>> = combine(
        apps.observeAll(), events.observeCountByPkg(clock.now() - SEVEN_DAYS_MS), query,
    ) { configs, counts, q ->
        val now = clock.now()
        val key = q.trim()
        configs
            .filter { key.isEmpty() || it.label.contains(key, ignoreCase = true) || it.pkg.contains(key, ignoreCase = true) }
            .map { cfg ->
                AppRow(
                    pkg = cfg.pkg,
                    label = cfg.label,
                    blocked7d = counts[cfg.pkg] ?: 0,
                    // 只看这个 App 自己的设置，不受全局暂停影响
                    level = EffectivePolicy.resolve(cfg, cfg.pkg, GlobalStateEntity(), now).level,
                    defaultAllowed = cfg.level == null && cfg.sensitive,
                )
            }
            .sortedWith(compareByDescending<AppRow> { it.blocked7d }.thenBy { it.label })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun search(q: String) { query.value = q }
}
