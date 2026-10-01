package com.sentinel.data.contract

import com.sentinel.rules.model.DomainTag

object RewardWindowContract {
    const val TTL_MS = 60_000L
    val RELEASED_TAGS: Set<DomainTag> = setOf(DomainTag.AD_SDK)
}
