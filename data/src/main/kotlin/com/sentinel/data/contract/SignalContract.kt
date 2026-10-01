package com.sentinel.data.contract

import com.sentinel.data.db.*

object SignalContract {
    val EMITTERS: Map<SignalKind, EngineId?> = mapOf(
        SignalKind.USER_UNDO to EngineId.A11Y, SignalKind.TEMP_ALLOW to null,
        SignalKind.RETRY_STORM to EngineId.VPN, SignalKind.CRASH_DIALOG to EngineId.A11Y,
        SignalKind.COLD_START_LOOP to EngineId.A11Y, SignalKind.DROPBOX_CRASH to EngineId.SYSTEM)
}
