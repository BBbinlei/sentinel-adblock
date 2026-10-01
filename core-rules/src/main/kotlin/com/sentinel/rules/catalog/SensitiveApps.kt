package com.sentinel.rules.catalog

object SensitiveApps {
    private val packages = setOf("com.eg.android.AlipayGphone", "com.tencent.mm", "cmb.pb", "com.icbc",
        "com.chinamworld.main", "com.android.bankabc", "com.chinamworld.bocmbci", "com.bankcomm.Bankcomm",
        "com.yitong.mbank.psbc", "com.unionpay", "com.jd.jrapp", "com.hexin.plat.android")
    private val labels = Regex("银行|证券|支付|钱包|保险|基金|信用卡")
    fun isSensitive(pkg: String, label: String?): Boolean = pkg in packages || (label != null && labels.containsMatchIn(label))
}
