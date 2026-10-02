package com.sentinel.app.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sentinel.data.Clock
import com.sentinel.data.db.AppConfigEntity
import com.sentinel.data.db.GlobalStateEntity
import com.sentinel.data.db.ProtectLevel
import com.sentinel.data.db.RewardedMode
import com.sentinel.data.policy.EffectivePolicy
import com.sentinel.data.repo.AppConfigRepository
import com.sentinel.data.repo.EventRepository
import com.sentinel.rules.model.EventKind
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppToggle { SPLASH, SHAKE, JUMP_BACK, NOTIFY, LIMIT_OVERLAY, DENY_CLIPBOARD }

data class AppDetailUiState(
    val label: String,
    val level: ProtectLevel,
    val observingDaysLeft: Int?,
    val wouldBlock: List<String>,
    val config: AppConfigEntity,
    val tempAllowedUntil: Long?,
)

private const val DAY_MS = 86_400_000L
private const val WOULD_BLOCK_LIMIT = 30

class AppDetailViewModel(
    private val pkg: String,
    private val apps: AppConfigRepository,
    events: EventRepository,
    private val clock: Clock,
) : ViewModel() {
    private fun empty() = AppDetailUiState(
        label = pkg, level = ProtectLevel.STANDARD, observingDaysLeft = null, wouldBlock = emptyList(),
        config = AppConfigEntity(pkg = pkg, label = pkg, level = null, sensitive = false, firstSeenAt = 0, observationEndsAt = 0),
        tempAllowedUntil = null,
    )

    val state: StateFlow<AppDetailUiState> = combine(
        apps.observe(pkg), events.observeRecent(setOf(EventKind.WOULD_BLOCK), 500),
    ) { cfg, recent ->
        if (cfg == null) return@combine empty()
        val now = clock.now()
        val remaining = cfg.observationEndsAt - now
        // 观察期只在用户没有手动设置级别时才有意义
        val daysLeft = if (cfg.level == null && remaining > 0) ((remaining + DAY_MS - 1) / DAY_MS).toInt() else null
        val wouldBlock = recent
            .filter { it.pkg == pkg && it.ts >= now - 3 * DAY_MS }
            .mapNotNull { it.detail }
            .distinct()
            .take(WOULD_BLOCK_LIMIT)
        AppDetailUiState(
            label = cfg.label,
            level = EffectivePolicy.resolve(cfg, pkg, GlobalStateEntity(), now).level,
            observingDaysLeft = daysLeft,
            wouldBlock = wouldBlock,
            config = cfg,
            tempAllowedUntil = cfg.tempAllowUntil?.takeIf { it > now },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), empty())

    fun setLevel(level: ProtectLevel) { viewModelScope.launch { apps.setLevel(pkg, level) } }

    fun setRewarded(mode: RewardedMode) { viewModelScope.launch { apps.setRewarded(pkg, mode) } }

    fun toggle(field: AppToggle, on: Boolean) {
        viewModelScope.launch {
            apps.setToggles(pkg) { cfg ->
                when (field) {
                    AppToggle.SPLASH -> cfg.copy(splash = on)
                    AppToggle.SHAKE -> cfg.copy(shake = on)
                    AppToggle.JUMP_BACK -> cfg.copy(jumpBack = on)
                    AppToggle.NOTIFY -> cfg.copy(notify = on)
                    AppToggle.LIMIT_OVERLAY -> cfg.copy(limitOverlay = on)
                    AppToggle.DENY_CLIPBOARD -> cfg.copy(denyClipboard = on)
                }
            }
        }
    }

    /** 一键临时放行 24 小时（data 层同时发出 TEMP_ALLOW 信号）。 */
    fun tempAllow() { viewModelScope.launch { apps.tempAllow(pkg) } }
}
