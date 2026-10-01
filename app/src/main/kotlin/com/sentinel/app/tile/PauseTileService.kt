package com.sentinel.app.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.sentinel.data.Clock
import com.sentinel.data.contract.DataContract
import com.sentinel.data.repo.GlobalStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/** 快捷开关：点一下暂停 5 分钟，再点一下恢复。 */
class PauseTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var listening: Job? = null

    private val global: GlobalStateRepository get() = GlobalContext.get().get()
    private val clock: Clock get() = GlobalContext.get().get()

    override fun onStartListening() {
        super.onStartListening()
        listening?.cancel()
        listening = scope.launch {
            val ticks = flow { while (true) { emit(Unit); delay(1_000) } }
            combine(global.observe(), ticks) { state, _ -> state }.collect { state ->
                val remaining = (state.pausedUntil ?: 0L) - clock.now()
                val paused = state.enabled && remaining > 0
                qsTile?.apply {
                    this.state = if (paused) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
                    subtitle = if (paused) {
                        val seconds = (remaining + 999) / 1_000
                        "已暂停，%02d:%02d 后恢复".format(seconds / 60, seconds % 60)
                    } else "防护中"
                    updateTile()
                }
            }
        }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            try {
                val state = global.get()
                if ((state.pausedUntil ?: 0L) > clock.now()) global.resume() else global.pauseFor(DataContract.PAUSE_MS)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("SentinelTile", "切换暂停失败", e)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
