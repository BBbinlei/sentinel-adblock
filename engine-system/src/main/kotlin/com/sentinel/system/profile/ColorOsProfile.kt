package com.sentinel.system.profile

import kotlinx.serialization.Serializable

@Serializable enum class OpKind { SETTING, APPOP, DISABLE, UNINSTALL, WIZARD }
@Serializable data class SettingsIntent(val action: String? = null, val pkg: String? = null, val cls: String? = null)
@Serializable data class ProfileOp(
    val id: String, val trick: Int, val title: String, val kind: OpKind,
    val verified: Boolean, val apply: String? = null, val revert: String? = null,
    val probe: String? = null, val appliedRegex: String? = null, val intent: SettingsIntent? = null,
    val optional: Boolean = false,
)
@Serializable data class ColorOsProfile(
    val romPrefix: String, val backgroundPopupOp: String? = null, val ops: List<ProfileOp>,
)
