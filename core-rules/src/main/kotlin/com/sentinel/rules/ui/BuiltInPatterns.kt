package com.sentinel.rules.ui

object BuiltInPatterns {
    val rewardEntry = Regex("看.{0,4}视频|领.{0,4}奖励|双倍|翻倍")
    val shakeHint = Regex("摇一摇|扭一扭|摇动手机|转动手机")
    val closeLike = Regex("^(关闭|×|✕|跳过|不感兴趣|以后再说|暂不)$")
    val autoRenew = Regex("自动续费|连续包月")
}
