package com.sentinel.app.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sentinel.data.db.SubscriptionEntity
import com.sentinel.data.db.SubscriptionFormat
import com.sentinel.data.repo.UserRuleRepository
import com.sentinel.data.rules.UpdateReport
import com.sentinel.rules.model.DomainTag
import com.sentinel.rules.model.Rule
import com.sentinel.rules.model.RuleLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URI

data class RulesUiState(
    val subscriptions: List<SubscriptionEntity>,
    val userRules: List<Rule>,
    val updating: Boolean,
    val message: String?,
)

class RulesViewModel(
    subscriptions: Flow<List<SubscriptionEntity>>,
    private val userRules: UserRuleRepository,
    private val saveSubscription: suspend (SubscriptionEntity) -> Unit,
    private val updateAll: suspend () -> UpdateReport,
    private val rebuildFromCache: suspend () -> Long,
) : ViewModel() {
    private val updating = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val latest = MutableStateFlow<List<SubscriptionEntity>>(emptyList())

    val state: StateFlow<RulesUiState> = combine(
        subscriptions, userRules.observeAll(), updating, message,
    ) { subs, rules, busy, msg ->
        latest.value = subs
        RulesUiState(subs, rules, busy, msg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RulesUiState(emptyList(), emptyList(), false, null))

    fun toggle(id: Long, on: Boolean) {
        val sub = latest.value.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            runCatching { saveSubscription(sub.copy(enabled = on)); rebuildFromCache() }
                .onFailure { rethrowCancellation(it); message.value = "操作失败，请稍后重试" }
        }
    }

    /** 只接受带主机名的 https 地址，其他一律拒绝，永远不会走到保存。 */
    fun add(name: String, url: String, format: SubscriptionFormat) {
        val cleanName = name.trim()
        val cleanUrl = url.trim()
        if (cleanName.isEmpty() || !isHttps(cleanUrl)) {
            message.value = "订阅地址必须是 https 开头的完整地址"
            return
        }
        viewModelScope.launch {
            runCatching {
                // 用户自己加的订阅按「强力」级别生效，不影响标准级别的 App
                saveSubscription(SubscriptionEntity(name = cleanName, url = cleanUrl, format = format,
                    level = RuleLevel.STRONG, tag = DomainTag.AD))
            }.onFailure { rethrowCancellation(it); message.value = "保存失败，请稍后重试" }
                .onSuccess { updateNow() }
        }
    }

    fun updateNow() {
        if (updating.value) return
        updating.value = true
        viewModelScope.launch {
            try {
                val report = updateAll()
                message.value = "更新 ${report.updated} 个，失败 ${report.failed} 个"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message.value = "更新失败，请检查网络"
            } finally {
                updating.value = false
            }
        }
    }

    fun deleteUserRule(id: String) {
        viewModelScope.launch {
            runCatching { userRules.delete(id); rebuildFromCache() }
                .onFailure { rethrowCancellation(it); message.value = "删除失败，请稍后重试" }
        }
    }

    private fun isHttps(url: String): Boolean = try {
        val uri = URI(url)
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
    } catch (e: Exception) {
        false
    }

    private fun rethrowCancellation(t: Throwable) { if (t is CancellationException) throw t }
}
