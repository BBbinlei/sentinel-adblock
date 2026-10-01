package com.sentinel.rules.domain

object NeverBlockList {
    private val suffixes = setOf("alipay.com", "alipayobjects.com", "tenpay.com", "wechatpay.cn",
        "unionpay.com", "95516.com", "cmbchina.com", "icbc.com.cn", "ccb.com", "abchina.com",
        "boc.cn", "bankcomm.com", "psbc.com", "12306.cn", "gov.cn")
    fun contains(domain: String): Boolean {
        var suffix = domain.trim().lowercase().trimEnd('.')
        while (true) {
            if (suffix in suffixes) return true
            val dot = suffix.indexOf('.')
            if (dot < 0) return false
            suffix = suffix.substring(dot + 1)
        }
    }
}
