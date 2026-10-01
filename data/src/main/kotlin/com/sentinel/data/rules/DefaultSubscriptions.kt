package com.sentinel.data.rules

import com.sentinel.data.db.*
import com.sentinel.rules.model.*

object DefaultSubscriptions {
    // Project README URLs checked on 2026-10-02; GKD currently marks its official rules as unmaintained.
    val all: List<SubscriptionEntity> = listOf(
        SubscriptionEntity(1, "anti-AD", "https://raw.githubusercontent.com/privacy-protection-tools/anti-AD/master/anti-ad-adguard.txt", SubscriptionFormat.ADGUARD, RuleLevel.STRONG, DomainTag.AD),
        SubscriptionEntity(2, "AWAvenue", "https://raw.githubusercontent.com/TG-Twilight/AWAvenue-Ads-Rule/main/AWAvenue-Ads-Rule.txt", SubscriptionFormat.ADGUARD, RuleLevel.STRONG, DomainTag.AD),
        SubscriptionEntity(3, "GKD 官方订阅", "https://fastly.jsdelivr.net/npm/@gkd-kit/subscription", SubscriptionFormat.GKD, RuleLevel.STRONG, DomainTag.AD)
    )
}
