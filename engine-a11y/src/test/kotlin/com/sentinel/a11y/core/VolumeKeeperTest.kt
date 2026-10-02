package com.sentinel.a11y.core

import com.sentinel.a11y.fakes.*
import kotlin.test.*
import org.junit.Test

class VolumeKeeperTest {
    @Test fun UT_AY_5_06_process_restart_restores_persisted_volume() {
        val volume = FakeVolume(9)
        val store = MemoryStore()
        VolumeKeeper(volume, store).muteAndSave()
        assertEquals(0, volume.value)
        assertEquals(9, store.getInt("saved_music_volume"))
        // 丢弃旧实例，唯一跨进程死亡保留的数据来自 KeyValueStore。
        val restarted = VolumeKeeper(volume, store)
        restarted.restoreIfPending()
        assertEquals(9, volume.value)
        assertNull(store.getInt("saved_music_volume"))
        volume.value = 4
        restarted.restoreIfPending()
        assertEquals(4, volume.value)
        assertEquals(listOf(0, 9), volume.writes)
    }

    @Test fun UT_AY_5_07_repeated_restore_never_applies_stale_value() {
        val volume = FakeVolume(7)
        val store = MemoryStore()
        val keeper = VolumeKeeper(volume, store)
        keeper.muteAndSave()
        keeper.restore()
        assertEquals(7, volume.value)
        assertNull(store.getInt("saved_music_volume"))
        volume.value = 3 // 用户自行调节音量。
        keeper.restore()
        assertEquals(3, volume.value)
        assertEquals(listOf(0, 7), volume.writes)
        keeper.muteAndSave()
        keeper.restore()
        assertEquals(3, volume.value)
        assertEquals(listOf(0, 7, 0, 3), volume.writes)
    }
}
