package com.sentinel.app.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.sentinel.app.R
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = koinViewModel(),
    checker: SetupChecker = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val vm by rememberUpdatedState(viewModel)

    // 从系统设置页回来时重新判定每一步
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { vm.onResume() } }
    LaunchedEffect(state.finished) { if (state.finished) onFinished() }

    val step = state.current
    val (title, body, image) = stepContent(step)
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).testTag("screen:onboarding"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.onboarding_progress, step.ordinal + 1, SetupStep.entries.size),
            style = MaterialTheme.typography.labelLarge,
        )
        Image(painterResource(image), contentDescription = null)
        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("onboarding:title"))
        Text(stringResource(body), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        if (step in state.done) {
            Text(stringResource(R.string.onboarding_step_done), color = MaterialTheme.colorScheme.primary)
            Button(onClick = viewModel::next, modifier = Modifier.fillMaxWidth().height(48.dp).testTag("onboarding:next")) {
                Text(stringResource(R.string.onboarding_next))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = viewModel::skip, modifier = Modifier.weight(1f).height(48.dp).testTag("onboarding:skip")) {
                    Text(stringResource(R.string.onboarding_skip))
                }
                Button(
                    onClick = { runCatching { context.startActivity(checker.settingsIntent(step)) } },
                    modifier = Modifier.weight(1f).height(48.dp).testTag("onboarding:go"),
                ) { Text(stringResource(R.string.onboarding_go)) }
            }
        }
    }
}

private data class StepContent(val title: Int, val body: Int, val image: Int)

private fun stepContent(step: SetupStep): StepContent = when (step) {
    SetupStep.SHIZUKU -> StepContent(R.string.step_shizuku_title, R.string.step_shizuku_body, R.drawable.onboarding_shizuku)
    SetupStep.VPN -> StepContent(R.string.step_vpn_title, R.string.step_vpn_body, R.drawable.onboarding_vpn)
    SetupStep.ACCESSIBILITY -> StepContent(R.string.step_a11y_title, R.string.step_a11y_body, R.drawable.onboarding_accessibility)
    SetupStep.NOTIFICATION -> StepContent(R.string.step_notify_title, R.string.step_notify_body, R.drawable.onboarding_notification)
    SetupStep.BATTERY -> StepContent(R.string.step_battery_title, R.string.step_battery_body, R.drawable.onboarding_battery)
}
