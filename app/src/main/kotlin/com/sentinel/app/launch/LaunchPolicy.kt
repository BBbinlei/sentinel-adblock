package com.sentinel.app.launch

/** Entries are bundled with the app; shortcut payloads contain only an ID. */
data class LaunchProfile(
    val id: String, val packageName: String, val label: String,
    val versionCode: Long, val versionName: String, val component: String,
    val uri: String?, val verified: Boolean, val scenario: String, val evidence: String,
)
data class InstalledTarget(val versionCode: Long, val versionName: String, val entryAvailable: Boolean)
enum class LaunchStatus { VERIFIED, PENDING, UNSUPPORTED }
sealed interface LaunchDecision {
    data class Entry(val profile: LaunchProfile) : LaunchDecision
    data class Normal(val packageName: String) : LaunchDecision
    data object Reject : LaunchDecision
}
class LaunchPolicy(private val profiles: List<LaunchProfile>) {
    fun byId(id: String?) = profiles.singleOrNull { it.id == id }
    fun forPackage(pkg: String) = profiles.singleOrNull { it.packageName == pkg }
    fun status(id: String, installed: InstalledTarget?): LaunchStatus {
        val p = byId(id) ?: return LaunchStatus.UNSUPPORTED
        if (installed == null || p.versionCode != installed.versionCode ||
            p.versionName != installed.versionName || !installed.entryAvailable) return LaunchStatus.UNSUPPORTED
        return if (p.verified) LaunchStatus.VERIFIED else LaunchStatus.PENDING
    }
    fun decide(id: String?, installed: InstalledTarget?): LaunchDecision {
        val p = byId(id) ?: return LaunchDecision.Reject
        return if (status(p.id, installed) == LaunchStatus.VERIFIED) LaunchDecision.Entry(p)
        else LaunchDecision.Normal(p.packageName)
    }
}
interface LaunchPort {
    fun installed(profile: LaunchProfile): InstalledTarget?
    fun startEntry(profile: LaunchProfile): Boolean
    fun startNormal(packageName: String): Boolean
}
enum class LaunchOutcome { STARTED, FALLBACK, UNAVAILABLE, REJECTED }
class LaunchController(private val policy: LaunchPolicy, private val port: LaunchPort) {
    fun launch(id: String?): LaunchOutcome {
        val p = policy.byId(id) ?: return LaunchOutcome.REJECTED
        return when (val decision = policy.decide(id, port.installed(p))) {
            is LaunchDecision.Entry -> if (port.startEntry(decision.profile)) LaunchOutcome.STARTED else normal(p.packageName)
            is LaunchDecision.Normal -> normal(decision.packageName)
            LaunchDecision.Reject -> LaunchOutcome.REJECTED
        }
    }
    private fun normal(pkg: String) = if (port.startNormal(pkg)) LaunchOutcome.FALLBACK else LaunchOutcome.UNAVAILABLE
}
enum class PinFeedback { REQUESTED, CONFIRMED, UNSUPPORTED, REJECTED, UNCONFIRMED }
fun pinFeedback(supported: Boolean, accepted: Boolean, pinned: Boolean, returning: Boolean = false) = when {
    !supported -> PinFeedback.UNSUPPORTED
    !accepted -> PinFeedback.REJECTED
    pinned -> PinFeedback.CONFIRMED
    returning -> PinFeedback.UNCONFIRMED
    else -> PinFeedback.REQUESTED
}
