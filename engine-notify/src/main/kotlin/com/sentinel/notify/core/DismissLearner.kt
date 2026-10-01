package com.sentinel.notify.core

import com.sentinel.rules.model.NotifyRule
import kotlinx.serialization.json.*

interface LearnerStore {
    fun load(): String?
    fun save(json: String)
}

class DismissLearner(private val store: LearnerStore, private val clock: () -> Long) {
    private data class History(val times: List<Long>, val rejectedUntil: Long)
    private val histories = mutableMapOf<Pair<String, String>, History>()

    init {
        store.load()?.let { json ->
            Json.parseToJsonElement(json).jsonArray.forEach { item ->
                val row = item.jsonObject
                histories[row.getValue("pkg").jsonPrimitive.content to row.getValue("channel").jsonPrimitive.content] =
                    History(row.getValue("times").jsonArray.map { it.jsonPrimitive.long }.takeLast(THRESHOLD),
                        row.getValue("rejectedUntil").jsonPrimitive.long)
            }
        }
    }

    @Synchronized fun onUserDismissed(pkg: String, channelId: String?): NotifyRule? {
        if (pkg.isBlank() || channelId.isNullOrBlank()) return null
        val now = clock()
        val key = pkg to channelId
        val previous = histories[key] ?: History(emptyList(), 0)
        val history = previous.copy(times = (previous.times.filter { it >= now - WINDOW_MS } + now).takeLast(THRESHOLD))
        histories[key] = history
        persist(now)
        return if (history.times.size >= THRESHOLD && history.rejectedUntil <= now)
            NotifyRule("learn:notify:$pkg:$channelId", pkg, channelId, emptyList(), "learned") else null
    }

    @Synchronized fun onRejected(pkg: String, channelId: String?) {
        if (pkg.isBlank() || channelId.isNullOrBlank()) return
        val now = clock()
        val key = pkg to channelId
        histories[key] = History(emptyList(), Math.addExact(now, REJECT_MS))
        persist(now)
    }

    private fun persist(now: Long) {
        histories.entries.removeAll { (_, history) ->
            history.rejectedUntil <= now && history.times.none { it >= now - WINDOW_MS }
        }
        store.save(buildJsonArray {
            histories.forEach { (key, history) -> add(buildJsonObject {
                put("pkg", key.first); put("channel", key.second)
                put("times", JsonArray(history.times.map(::JsonPrimitive)))
                put("rejectedUntil", history.rejectedUntil)
            }) }
        }.toString())
    }

    private companion object {
        const val THRESHOLD = 3
        const val WINDOW_MS = 7 * 86_400_000L
        const val REJECT_MS = 30 * 86_400_000L
    }
}
