package com.sentinel.vpn.health

object PrivateDnsDetector {
    const val WARNING = "系统「私人 DNS」设为指定服务器，会让网络拦截失效，请改为「自动」或「关闭」"
    fun evaluate(active: Boolean, serverName: String?): String? = if (active && serverName != null) WARNING else null
}
