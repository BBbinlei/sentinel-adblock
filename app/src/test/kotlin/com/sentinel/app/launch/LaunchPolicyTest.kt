package com.sentinel.app.launch

import org.junit.Test
import kotlin.test.*

class LaunchPolicyTest {
    private val profile = LaunchProfile("cainiao", "com.cainiao.wireless", "菜鸟", 475, "8.11.923", "Home", null, true, "冷启动", "三组对照")
    private val installed = InstalledTarget(475, "8.11.923", true)
    @Test fun exactVersionAndAvailableComponentRequired() {
        val policy = LaunchPolicy(listOf(profile))
        assertEquals(LaunchStatus.VERIFIED, policy.status(profile.id, installed))
        assertEquals(LaunchStatus.UNSUPPORTED, policy.status(profile.id, installed.copy(versionCode = 476)))
        assertEquals(LaunchStatus.UNSUPPORTED, policy.status(profile.id, installed.copy(versionName = "different")))
        assertEquals(LaunchStatus.UNSUPPORTED, policy.status(profile.id, installed.copy(entryAvailable = false)))
        assertEquals(LaunchStatus.UNSUPPORTED, policy.status(profile.id, null))
    }
    @Test fun pendingNeverOffersCreationOrCandidate() {
        val policy = LaunchPolicy(listOf(profile.copy(verified = false)))
        assertEquals(LaunchStatus.PENDING, policy.status(profile.id, installed))
        assertIs<LaunchDecision.Normal>(policy.decide(profile.id, installed))
    }
    @Test fun versionOrComponentFailureFallsBackToKnownPackage() {
        val policy = LaunchPolicy(listOf(profile))
        assertEquals(LaunchDecision.Normal(profile.packageName), policy.decide(profile.id, installed.copy(entryAvailable = false)))
        assertEquals(LaunchDecision.Normal(profile.packageName), policy.decide(profile.id, installed.copy(versionCode = 476)))
        assertIs<LaunchDecision.Entry>(policy.decide(profile.id, installed))
    }
    @Test fun unknownIdsCannotSupplyExternalIntent() {
        val policy = LaunchPolicy(listOf(profile))
        assertEquals(LaunchDecision.Reject, policy.decide("intent://evil", installed))
        assertNull(policy.forPackage("unknown"))
    }
    @Test fun startFailureFallsBackOnceAndUnknownNeverLaunches() {
        val port = FakeLaunchPort().apply { failEntry = true }
        val controller = LaunchController(LaunchPolicy(listOf(profile)), port)
        assertEquals(LaunchOutcome.FALLBACK, controller.launch(profile.id))
        assertEquals(listOf("entry", "normal"), port.calls)
        port.calls.clear()
        assertEquals(LaunchOutcome.REJECTED, controller.launch("unknown"))
        assertTrue(port.calls.isEmpty())
    }
    private inner class FakeLaunchPort : LaunchPort {
        val calls = mutableListOf<String>(); var failEntry = false
        override fun installed(profile: LaunchProfile) = installed
        override fun startEntry(profile: LaunchProfile): Boolean { calls += "entry"; return !failEntry }
        override fun startNormal(packageName: String): Boolean { calls += "normal"; return true }
    }
}
