package com.sentinel.rules.catalog

object JumpTargetCatalog {
    private val packages = setOf("com.taobao.taobao", "com.tmall.wireless", "com.taobao.litetao",
        "com.xunmeng.pinduoduo", "com.jingdong.app.mall", "com.achievo.vipshop", "com.taobao.idlefish",
        "com.ss.android.ugc.aweme", "com.ss.android.ugc.aweme.lite", "com.smile.gifmaker",
        "com.kuaishou.nebula", "com.sankuai.meituan", "me.ele", "com.UCMobile", "com.baidu.searchbox",
        "com.heytap.market", "com.oppo.market", "com.nearme.instant.platform", "com.nearme.gamecenter")
    fun isAdLanding(pkg: String): Boolean = pkg in packages
}
