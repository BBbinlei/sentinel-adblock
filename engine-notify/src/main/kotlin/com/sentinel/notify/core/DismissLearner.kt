package com.sentinel.notify.core

interface LearnerStore {
    fun load(): String?
    fun save(json: String)
}
