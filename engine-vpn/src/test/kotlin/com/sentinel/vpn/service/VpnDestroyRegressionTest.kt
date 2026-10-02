package com.sentinel.vpn.service

import android.os.Looper
import com.sentinel.data.db.*
import com.sentinel.data.repo.EngineStatusRepository
import com.sentinel.vpn.fakes.withController
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class VpnDestroyRegressionTest {
    private class GateDao(private val delegate: EngineStatusDao) : EngineStatusDao by delegate {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        override suspend fun upsert(entity: EngineStatusEntity) {
            if (entity.state == EngineState.DEGRADED) {
                entered.complete(Unit); release.await()
            }
            delegate.upsert(entity)
        }
    }

    @Test(timeout = 10000) fun `R02-01 onDestroy returns on Main and closes TUN while lifecycle write is suspended`() = runTest {
        lateinit var dao: GateDao
        withController(statusFactory = { data ->
            dao = GateDao(data.database.engineStatusDao()); EngineStatusRepository(dao, data.clock)
        }) {
            start()
            val service = Robolectric.buildService(SentinelVpnService::class.java).create().get()
            ReflectionHelpers.setField(service, "controller", controller)
            val loop = ReflectionHelpers.getField<Job>(controller, "loop")
            val health = scope.launch(Dispatchers.Main) { controller.onPrivateDns("test private DNS warning") }
            test.runCurrent(); assertTrue(dao.entered.isCompleted)
            assertFalse(health.isCompleted); assertFalse(factory.current.closed)
            try {
                withContext(Dispatchers.Main) {
                    assertSame(Looper.getMainLooper(), Looper.myLooper())
                    service.onDestroy()
                }
                // This line is reachable with the lock still held: no timing guess or sleep.
                assertFalse(dao.release.isCompleted); assertFalse(health.isCompleted)
                assertTrue(factory.current.closed, "onDestroy must synchronously close TUN")
                // EOF may complete the IO job before cancel arrives; both must leave it inactive.
                assertFalse(loop.isActive, "packet loop must be stopped or cancelling")
                assertEquals(EngineState.RUNNING, data.vpnStatus()?.state)
                assertNull(ReflectionHelpers.getField<VpnController?>(service, "controller"))
            } finally { dao.release.complete(Unit) }
            health.join(); loop.join(); assertTrue(loop.isCompleted)
            withContext(Dispatchers.IO) { withTimeout(5000) { while (stops.get() == 0) yield() } }
            assertEquals(EngineState.STOPPED, data.vpnStatus()?.state)
            assertEquals("服务已停止", data.vpnStatus()?.message)
        }
    }

    @Test fun `R02-02 closeTun and repeated stop preserve first upstream stop reason`() = runTest { withController {
        start(); controller.onUnderlyingNetwork(true)
        repeat(3) { controller.onUpstreamTransport(false) }
        assertTrue(factory.current.closed)
        assertFalse(controller.closeTun("服务已停止"))
        controller.onRevoked(); test.runCurrent(); controller.stop("later cleanup")
        assertEquals(EngineState.STOPPED, controller.state.value)
        assertEquals("DNS 上游连续不可用，网络拦截已停止，网络已恢复直连", data.vpnStatus()?.message)
        assertEquals(1, factory.operations.count { it == "close:1" })
    } }
}
