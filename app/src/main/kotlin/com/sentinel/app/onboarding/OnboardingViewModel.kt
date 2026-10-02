package com.sentinel.app.onboarding

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import com.sentinel.app.di.PREF_ONBOARDING_DONE
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OnboardingUiState(val current: SetupStep, val done: Set<SetupStep>, val finished: Boolean)

class OnboardingViewModel(
    private val checker: SetupChecker,
    private val prefs: SharedPreferences,
) : ViewModel() {
    private val skipped = mutableSetOf<SetupStep>()
    private val _state = MutableStateFlow(OnboardingUiState(SetupStep.entries.first(), emptySet(), false))
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    /** 每次回到向导（比如从系统设置页返回）都重新判定所有步骤。 */
    fun onResume() = recompute()

    fun next() = recompute()

    fun skip() {
        skipped += _state.value.current
        recompute()
    }

    private fun recompute() {
        val done = SetupStep.entries.filter { checker.isDone(it) }.toSet()
        val pending = SetupStep.entries.filter { it !in done && it !in skipped }
        val finished = pending.isEmpty()
        if (finished) prefs.edit().putBoolean(PREF_ONBOARDING_DONE, true).apply()
        _state.value = OnboardingUiState(pending.firstOrNull() ?: SetupStep.entries.last(), done, finished)
    }
}
