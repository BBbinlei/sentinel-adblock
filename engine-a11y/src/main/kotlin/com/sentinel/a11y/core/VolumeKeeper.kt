package com.sentinel.a11y.core

interface VolumePort { fun get(): Int; fun set(v: Int) }   // STREAM_MUSIC
interface KeyValueStore { fun getInt(k: String): Int?; fun putInt(k: String, v: Int); fun remove(k: String) }

/** 静音前先把原音量写入持久存档；即使进程被杀，下次启动也能恢复。 */
class VolumeKeeper(private val port: VolumePort, private val store: KeyValueStore) {
    fun muteAndSave() {
        // 已有存档时（上一次还没恢复）不覆盖，否则会把静音后的 0 存成「原音量」。
        if (store.getInt(KEY) == null) store.putInt(KEY, port.get())
        port.set(0)
    }

    fun restore() {
        val saved = store.getInt(KEY) ?: return
        port.set(saved)
        store.remove(KEY)
    }

    fun restoreIfPending() = restore()

    companion object { const val KEY = "saved_music_volume" }
}
