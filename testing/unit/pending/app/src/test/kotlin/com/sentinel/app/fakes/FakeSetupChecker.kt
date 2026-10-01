package com.sentinel.app.fakes

import android.content.Intent
import com.sentinel.app.onboarding.SetupChecker
import com.sentinel.app.onboarding.SetupStep

class FakeSetupChecker : SetupChecker {
    val completed = mutableSetOf<SetupStep>()
    override fun isDone(step: SetupStep) = step in completed
    override fun settingsIntent(step: SetupStep) = Intent("test.setup.${step.name}")
}

