package com.sentinel.app.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sentinel.data.Clock
import com.sentinel.data.db.EngineId
import com.sentinel.data.db.EngineState
import com.sentinel.data.db.OverrideState
import com.sentinel.data.repo.AppConfigRepository
import com.sentinel.data.repo.EngineStatusRepository
import com.sentinel.data.repo.EventRepository
import com.sentinel.data.repo.GlobalStateRepository
import com.sentinel.data.repo.OverrideRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ShieldState { PROTECTING, PAUSED, OFF }

data class EngineRow(val engine: EngineId, val state: EngineState, val message: String?)

/** guard 自动停用规则后在首页的提醒（Task 4 填充）。 */
data class GuardAlert(val pkg: String, val label: String, val ruleIds: List<String>, val reason: String, val at: Long)

data class HomeUiState(
    val shield: ShieldState,
    val todayBlocked: Int,
    val engines: List<EngineRow>,
    val warnings: List<String>,
    val guardAlerts: List<GuardAlert>,
)

private const val TICK_MS = 10_000L
private const val DAY_MS = 86_400_000L

class HomeViewModel(
    private val global: GlobalStateRepository,
    events: EventRepository,
    statuses: EngineStatusRepository,
    overrides: OverrideRepository,
    apps: AppConfigRepository,
    private val clock: Clock,
    private val privateDnsWarning: () -> String?,
) : ViewModel() {
    private val refresh = MutableStateFlow(0)
    private val ticks = flow { while (true) { emit(Unit); delay(TICK_MS) } }
    private val refreshTick = combine(refresh, ticks) { _, _ -> Unit }

    /** 最近 24 小时 guard 自动停用的规则，按 App 合并成一条提醒。 */
    private val alerts = combine(overrides.observeRecent(clock.now() - DAY_MS), apps.observeAll()) { rows, configs ->
        val labels = configs.associate { it.pkg to it.label }
        rows.filter { it.state == OverrideState.DISABLED }.groupBy { it.pkg }.map { (pkg, group) ->
            val latest = group.maxBy { it.createdAt }
            GuardAlert(pkg, labels[pkg] ?: pkg, group.map { it.ruleId }.distinct(), latest.reason, latest.createdAt)
        }.sortedByDescending { it.at }
    }

    val state: StateFlow<HomeUiState> = combine(
        global.observe(), statuses.observeAll(), events.observeTodayCount(), alerts, refreshTick,
    ) { globalState, statusMap, today, guardAlerts, _ ->
        val now = clock.now()
        val vpn = statusMap[EngineId.VPN]?.state ?: EngineState.NOT_SETUP
        val paused = (globalState.pausedUntil ?: 0L) > now
        val shield = when {
            !globalState.enabled -> ShieldState.OFF
            paused -> ShieldState.PAUSED
            vpn == EngineState.RUNNING || vpn == EngineState.DEGRADED -> ShieldState.PROTECTING
            else -> ShieldState.OFF
        }
        val engines = EngineId.entries.map { id ->
            val row = statusMap[id]
            EngineRow(id, row?.state ?: EngineState.NOT_SETUP, row?.message)
        }
        val warnings = buildList {
            engines.filter { it.state == EngineState.DEGRADED }.forEach { it.message?.let(::add) }
            privateDnsWarning()?.let(::add)
        }.distinct()
        HomeUiState(shield, today, engines, warnings, guardAlerts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(ShieldState.OFF, 0, emptyList(), emptyList(), emptyList()))

    /** 回到前台时调用，重新读取系统「私人 DNS」等不会触发数据库变化的状态。 */
    fun refresh() { refresh.value += 1 }

    /** 保护中 → 关闭；关闭或暂停中 → 开启并取消暂停。 */
    fun onShieldTapped() {
        viewModelScope.launch {
            if (state.value.shield == ShieldState.PROTECTING) {
                global.setEnabled(false)
            } else {
                global.setEnabled(true)
                global.resume()
            }
        }
    }
}
