package com.sentinel.data.repo

import com.sentinel.data.Clock
import com.sentinel.data.db.*
import kotlinx.coroutines.flow.*
import com.sentinel.data.contract.SignalContract
import com.sentinel.data.policy.EffectivePolicy
import android.util.Log
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class SignalRepository(private val db: SentinelDatabase, private val clock: Clock) {
    private val dao = db.signalDao()
    suspend fun emit(pkg: String, kind: SignalKind, ruleId: String? = null, detail: String? = null) {
        if (!pkg.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))) return
        try {
            val global = db.globalStateDao().get() ?: GlobalStateEntity()
            if (EffectivePolicy.resolve(db.appConfigDao().get(pkg), pkg, global, clock.now()).level == ProtectLevel.OFF) return
            check(kind in SignalContract.EMITTERS)
            dao.insert(SignalEntity(ts = clock.now(), pkg = pkg, kind = kind, ruleId = ruleId, detail = detail))
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            Log.w("SentinelData", "信号写入失败", e)
        }
    }
    fun observeUnhandled(): Flow<List<SignalEntity>> = dao.unhandled()
    suspend fun markHandled(ids: List<Long>) = dao.markHandled(ids)
    suspend fun countSince(pkg: String, kinds: Set<SignalKind>, since: Long): Int = dao.countSince(pkg, kinds, since)
}
