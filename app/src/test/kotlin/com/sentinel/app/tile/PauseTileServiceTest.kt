package com.sentinel.app.tile

import android.service.quicksettings.Tile
import com.sentinel.app.fakes.*
import com.sentinel.data.Clock
import com.sentinel.data.db.GlobalStateEntity
import com.sentinel.data.repo.GlobalStateRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class PauseTileServiceTest : AppTest() {
    private fun service(): PauseTileService {
        startKoin { modules(module {
            single<GlobalStateRepository> { data.global }
            single<Clock> { data.clock }
        }) }
        return Robolectric.buildService(PauseTileService::class.java).create().get()
    }

    @Test fun UT_AP_7_01_click_pauses_five_minutes_then_resumes() = runTest(main.dispatcher) {
        val service = service()
        try {
            service.onStartListening(); service.onClick(); runCurrent()
            assertEquals(data.clock.now() + 300_000L, data.globalRows.value.pausedUntil)
            assertTrue(data.globalRows.value.enabled)
            service.onClick(); runCurrent()
            assertNull(data.globalRows.value.pausedUntil)
            assertTrue(data.globalRows.value.enabled)
        } finally { service.onStopListening() }
    }

    @Test fun UT_AP_7_02_tile_state_tracks_repository_changes() = runTest(main.dispatcher) {
        val service = service()
        try {
            service.onStartListening(); runCurrent()
            assertEquals(Tile.STATE_ACTIVE, service.qsTile.state)
            assertEquals("防护中", service.qsTile.subtitle.toString())
            data.globalRows.value = GlobalStateEntity(pausedUntil = data.clock.now() + 300_000)
            runCurrent()
            assertEquals(Tile.STATE_INACTIVE, service.qsTile.state)
            assertEquals("已暂停，05:00 后恢复", service.qsTile.subtitle.toString())
            data.globalRows.value = GlobalStateEntity()
            runCurrent()
            assertEquals(Tile.STATE_ACTIVE, service.qsTile.state)
            assertEquals("防护中", service.qsTile.subtitle.toString())
            service.onStopListening()
            data.globalRows.value = GlobalStateEntity(pausedUntil = data.clock.now() + 300_000)
            data.clock.time += 1_000
            advanceTimeBy(1_000); runCurrent()
            assertEquals(Tile.STATE_ACTIVE, service.qsTile.state)
            assertEquals("防护中", service.qsTile.subtitle.toString(), "Stopped listener must not update the tile")
        } finally { service.onStopListening() }
    }
}
