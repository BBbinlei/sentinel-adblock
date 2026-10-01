package com.sentinel.app.fakes

import com.sentinel.data.Clock
import com.sentinel.data.apps.AppRegistry
import com.sentinel.data.db.*
import com.sentinel.data.repo.*
import com.sentinel.rules.model.EventKind
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeClock(var time: Long = 1_800_000_000_000L) : Clock {
    override fun now() = time
}

/** Real repository façades, handwritten DAO doubles. Unknown DAO calls fail loudly. */
class FakeData(val clock: FakeClock = FakeClock()) {
    val globalRows = MutableStateFlow(GlobalStateEntity())
    val appsRows = MutableStateFlow<List<AppConfigEntity>>(emptyList())
    val eventRows = MutableStateFlow<List<EventEntity>>(emptyList())
    val statusRows = MutableStateFlow<List<EngineStatusEntity>>(emptyList())
    val overrideRows = MutableStateFlow<List<RuleOverrideEntity>>(emptyList())
    val signalRows = MutableStateFlow<List<SignalEntity>>(emptyList())
    val userRows = MutableStateFlow<List<UserRuleEntity>>(emptyList())
    val logRows = MutableStateFlow<List<OpLogEntity>>(emptyList())
    val calls = mutableListOf<DaoCall>()
    private var nextId = 1L

    data class DaoCall(val dao: String, val method: String, val args: List<Any?>)

    val global: GlobalStateRepository by lazy { repository() }
    val apps: AppConfigRepository by lazy { repository() }
    val events: EventRepository by lazy { repository() }
    val statuses: EngineStatusRepository by lazy { repository() }
    val overrides: OverrideRepository by lazy { repository() }
    val signals: SignalRepository by lazy { repository() }
    val users: UserRuleRepository by lazy { repository() }
    val logs: OpLogRepository by lazy { repository() }
    val registry: AppRegistry by lazy { repository() }

    private inline fun <reified T : Any> repository(): T = construct(T::class.java)

    // Constructor/DAO contracts are not specified by data/PLAN.md; see README A02–A03.
    private fun <T : Any> construct(type: Class<T>): T {
        val constructor = type.declaredConstructors.singleOrNull {
            !it.isSynthetic && it.parameterTypes.all(::supported)
        } ?: error("No unique DAO-based constructor for ${type.name}; reconcile README A02")
        constructor.isAccessible = true
        return type.cast(constructor.newInstance(*constructor.parameterTypes.map(::dependency).toTypedArray()))
    }

    private fun supported(type: Class<*>): Boolean =
        type == Clock::class.java || (type.isInterface && type.simpleName.endsWith("Dao")) ||
            type in setOf(GlobalStateRepository::class.java, AppConfigRepository::class.java,
                EventRepository::class.java, SignalRepository::class.java, OverrideRepository::class.java,
                OpLogRepository::class.java, UserRuleRepository::class.java)

    private fun dependency(type: Class<*>): Any = when (type) {
        Clock::class.java -> clock
        GlobalStateRepository::class.java -> global
        AppConfigRepository::class.java -> apps
        EventRepository::class.java -> events
        SignalRepository::class.java -> signals
        OverrideRepository::class.java -> overrides
        OpLogRepository::class.java -> logs
        UserRuleRepository::class.java -> users
        else -> Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, raw ->
            when (method.name) {
                "toString" -> "Fake${type.simpleName}"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === raw?.firstOrNull()
                else -> {
                    val args = raw.orEmpty().filterNot { it is Continuation<*> }
                    calls += DaoCall(type.simpleName, method.name, args)
                    answer(type.simpleName, method, args)
                }
            }
        }
    }

    private fun answer(dao: String, method: Method, a: List<Any?>): Any? {
        val name = method.name
        return when (dao) {
            "GlobalStateDao" -> when (name) {
                "observe" -> globalRows
                "get" -> globalRows.value
                "upsert", "update" -> { globalRows.value = a[0] as GlobalStateEntity; Unit }
                else -> unknown(dao, name)
            }
            "AppConfigDao" -> when (name) {
                "observeAll" -> appsRows
                "observe" -> appsRows.map { rows -> rows.find { it.pkg == a[0] } }
                "get" -> appsRows.value.find { it.pkg == a[0] }
                "getAll" -> appsRows.value
                "upsert", "update" -> { val row = a[0] as AppConfigEntity
                    appsRows.value = appsRows.value.filterNot { it.pkg == row.pkg } + row; Unit }
                "delete" -> { appsRows.value = appsRows.value.filterNot { it.pkg == a[0] }; Unit }
                "observationDue" -> appsRows.value.filter {
                    it.level == null && it.observationEndsAt in 1..(a[0] as Long) }
                else -> unknown(dao, name)
            }
            "EventDao" -> when (name) {
                "insert" -> { val row = a[0] as EventEntity; val id = nextId++
                    eventRows.value += row.copy(id = id); id }
                "observeTodayCount" -> eventRows.map { rows -> rows.count { it.ts >= a[0] as Long } }
                "observeCountByPkg" -> eventRows.map { rows -> rows.filter {
                    it.ts >= a[0] as Long && it.kind in blockedKinds && it.pkg != null
                }.groupingBy { it.pkg!! }.eachCount() }
                "observeRecent" -> eventRows.map { rows -> rows.filter {
                    it.kind in (a[0] as Collection<*>)
                }.sortedByDescending { it.ts }.take(a[1] as Int) }
                "rulesHitSince" -> eventRows.value.filter {
                    it.pkg == a[0] && it.ts >= a[1] as Long
                }.mapNotNull { it.ruleId }.distinct()
                else -> unknown(dao, name)
            }
            "EngineStatusDao" -> when (name) {
                "observeAll" -> statusRows
                "upsert" -> { val row = a[0] as EngineStatusEntity
                    statusRows.value = statusRows.value.filterNot { it.engine == row.engine } + row; Unit }
                else -> unknown(dao, name)
            }
            "RuleOverrideDao" -> when (name) {
                "observeDisabled" -> overrideRows.map { rows -> rows.filter { it.state == OverrideState.DISABLED } }
                "observeRecent" -> overrideRows.map { rows -> rows.filter {
                    it.createdAt >= a[0] as Long && it.state == OverrideState.DISABLED } }
                "get" -> overrideRows.value.find { it.pkg == a[0] && it.ruleId == a[1] }
                "upsert" -> { val row = a[0] as RuleOverrideEntity
                    overrideRows.value = overrideRows.value.filterNot {
                        it.pkg == row.pkg && it.ruleId == row.ruleId } + row; Unit }
                "remove" -> { overrideRows.value = overrideRows.value.filterNot {
                    it.pkg == a[0] && it.ruleId == a[1] }; Unit }
                else -> unknown(dao, name)
            }
            "SignalDao" -> when (name) {
                "insert" -> { val id = nextId++
                    signalRows.value += (a[0] as SignalEntity).copy(id = id); id }
                "observeUnhandled" -> signalRows.map { rows -> rows.filterNot { it.handled } }
                "countSince" -> signalRows.value.count {
                    it.pkg == a[0] && it.kind in (a[1] as Collection<*>) && it.ts >= a[2] as Long }
                else -> unknown(dao, name)
            }
            "UserRuleDao" -> when (name) {
                "observeAll" -> userRows
                "upsert", "insert" -> { val row = a[0] as UserRuleEntity
                    userRows.value = userRows.value.filterNot { it.id == row.id } + row; Unit }
                "delete" -> { userRows.value = userRows.value.filterNot { it.id == a[0] }; Unit }
                else -> unknown(dao, name)
            }
            "OpLogDao" -> when (name) {
                "observeAll" -> logRows
                "insert" -> { val id = nextId++; logRows.value += (a[0] as OpLogEntity).copy(id = id); id }
                "latestApplied" -> logRows.value.lastOrNull { it.opId == a[0] && it.success && !it.undone }
                "get" -> logRows.value.find { it.id == a[0] }
                "markUndone" -> { logRows.value = logRows.value.map {
                    if (it.id == a[0]) it.copy(undone = true) else it }; Unit }
                else -> unknown(dao, name)
            }
            else -> unknown(dao, name)
        }
    }

    private fun unknown(dao: String, method: String): Nothing =
        error("Unspecified DAO call $dao.$method; reconcile README A03, never return a default")

    fun app(pkg: String = "com.example.reader", label: String = "阅读器", sensitive: Boolean = false,
        observationEndsAt: Long = 0) = AppConfigEntity(pkg = pkg, label = label, level = null,
        sensitive = sensitive, firstSeenAt = clock.now(), observationEndsAt = observationEndsAt)

    companion object {
        val blockedKinds = setOf(EventKind.DNS_BLOCKED, EventKind.HTTPDNS_REJECTED,
            EventKind.SPLASH_SKIPPED, EventKind.POPUP_CLOSED, EventKind.NOTIFICATION_CANCELLED,
            EventKind.JUMP_REVERTED)
    }
}
