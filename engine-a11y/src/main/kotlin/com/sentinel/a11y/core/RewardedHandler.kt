package com.sentinel.a11y.core

import com.sentinel.data.db.RewardedMode
import com.sentinel.rules.ui.BuiltInPatterns
import com.sentinel.rules.ui.NodeView

sealed interface RewardedAction {
    data object OpenRewardWindow : RewardedAction
    data object AskUser : RewardedAction
    data class ClickClose(val node: NodeView) : RewardedAction
    data object Finished : RewardedAction
}

class RewardedHandler(private val keeper: VolumeKeeper, private val clock: () -> Long) {
    private enum class Phase { IDLE, ASKING, WAITING, CLOSED }
    private var phase = Phase.IDLE
    private var pkg: String? = null
    private var since = 0L

    /** 当前是否正在处理激励视频（询问、静音等待或关闭后收尾）。 */
    val active: Boolean get() = phase != Phase.IDLE

    fun onClicked(c: UiInput.Clicked, mode: RewardedMode): RewardedAction? {
        if (mode == RewardedMode.BLOCK) return null
        val hit = c.text?.let(BuiltInPatterns.rewardEntry::containsMatchIn) == true ||
            c.desc?.let(BuiltInPatterns.rewardEntry::containsMatchIn) == true
        return if (hit) RewardedAction.OpenRewardWindow else null
    }

    fun onRewardedPage(pkg: String, mode: RewardedMode): RewardedAction? {
        if (mode == RewardedMode.BLOCK) return null
        if (phase != Phase.IDLE) return null // 内容反复变化时不重复静音/询问。
        return when (mode) {
            RewardedMode.SILENT -> { startSilence(pkg); null }
            RewardedMode.ASK -> { phase = Phase.ASKING; this.pkg = pkg; since = clock(); RewardedAction.AskUser }
            RewardedMode.BLOCK -> null
        }
    }

    fun onUserChoice(silent: Boolean) {
        if (phase != Phase.ASKING) return
        if (silent) startSilence(requireNotNull(pkg)) else reset()
    }

    fun onContent(pkg: String, root: NodeView): RewardedAction? {
        if (phase != Phase.WAITING || pkg != this.pkg) return null
        if (hasCountdown(root)) return null
        val close = findClose(root) ?: return null
        phase = Phase.CLOSED
        since = clock()
        return RewardedAction.ClickClose(close)
    }

    fun onForeground(pkg: String): RewardedAction? {
        if (phase == Phase.IDLE || pkg == this.pkg) return null
        return finish()
    }

    fun onTick(): RewardedAction? {
        val elapsed = clock() - since
        return when (phase) {
            Phase.WAITING -> if (elapsed > TIMEOUT_MS) finish() else null
            Phase.CLOSED -> if (elapsed >= SETTLE_MS) finish() else null
            Phase.ASKING -> { if (elapsed > TIMEOUT_MS) reset(); null }
            Phase.IDLE -> null
        }
    }

    private fun startSilence(pkg: String) {
        keeper.muteAndSave()
        phase = Phase.WAITING
        this.pkg = pkg
        since = clock()
    }

    private fun finish(): RewardedAction? {
        val muted = phase == Phase.WAITING || phase == Phase.CLOSED
        reset()
        if (!muted) return null
        keeper.restore()
        return RewardedAction.Finished
    }

    private fun reset() { phase = Phase.IDLE; pkg = null }

    private fun hasCountdown(n: NodeView): Boolean =
        n.text?.let(COUNTDOWN::containsMatchIn) == true || n.children.any { hasCountdown(it) }

    private fun findClose(n: NodeView): NodeView? {
        val hit = n.text?.let(BuiltInPatterns.closeLike::containsMatchIn) == true ||
            n.desc?.let { BuiltInPatterns.closeLike.containsMatchIn(it) || CLOSE_DESC.matches(it) } == true
        if (hit) return n
        for (c in n.children) findClose(c)?.let { return it }
        return null
    }

    companion object {
        const val TIMEOUT_MS = 90_000L
        const val SETTLE_MS = 3_000L
        private val COUNTDOWN = Regex("\\d+\\s*[sS秒]")
        private val CLOSE_DESC = Regex("关闭|close", RegexOption.IGNORE_CASE)
    }
}
