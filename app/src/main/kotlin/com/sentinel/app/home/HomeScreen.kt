package com.sentinel.app.home

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.clickable
import com.sentinel.app.R
import com.sentinel.app.VpnStarter
import com.sentinel.data.db.EngineId
import com.sentinel.data.db.EngineState
import com.sentinel.app.onboarding.SetupChecker
import com.sentinel.app.onboarding.SetupStep
import com.sentinel.guard.runtime.GuardRunner
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun HomeScreen(
    onOpenSystem: () -> Unit = {},
    onOpenEngineLog: (String) -> Unit = {},
    onOpenOpLog: () -> Unit = {},
    onOpenSetup: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
    guard: GuardRunner = koinInject(),
    setupChecker: SetupChecker = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var missingSetup by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentVm by rememberUpdatedState(viewModel)

    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            currentVm.refresh()
            missingSetup = SetupStep.entries.count { !setupChecker.isDone(it) }
        }
    }

    val authorize = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            currentVm.onShieldTapped()
            VpnStarter.start(context)
        }
    }

    fun onShield() {
        if (state.shield == ShieldState.PROTECTING) {
            viewModel.onShieldTapped()
            return
        }
        val prepare = VpnStarter.intentToPrepare(context)
        if (prepare != null) authorize.launch(prepare) else {
            viewModel.onShieldTapped()
            VpnStarter.start(context)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("screen:home"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { Shield(state.shield, onClick = ::onShield) }
        item {
            Text(
                text = stringResource(R.string.home_today_blocked, state.todayBlocked),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("home:today-blocked"),
            )
        }
        if (state.warnings.isNotEmpty()) {
            items(state.warnings) { warning -> WarningCard(warning) }
        }
        if (missingSetup > 0) {
            item {
                Card(Modifier.fillMaxWidth().clickable(onClick = onOpenSetup).testTag("home:setup-todo")) {
                    Row(Modifier.heightIn(min = 48.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.home_setup_todo, missingSetup), modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.home_setup_go), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        items(state.guardAlerts, key = { it.pkg }) { alert ->
            GuardAlertCard(alert, onUndo = { scope.launch { guard.undo(alert.pkg, alert.ruleIds) } })
        }
        item { EnginesCard(state.engines, onOpenEngineLog) }
        item {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenSystem, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("home:system-cleanup")) {
                    Text(stringResource(R.string.home_system_cleanup))
                }
                OutlinedButton(onClick = onOpenOpLog, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("home:op-log")) {
                    Text(stringResource(R.string.home_op_log))
                }
            }
        }
    }
}

@Composable
private fun Shield(shield: ShieldState, onClick: () -> Unit) {
    val (label, hint) = when (shield) {
        ShieldState.PROTECTING -> stringResource(R.string.home_shield_on) to stringResource(R.string.home_shield_tap_off)
        ShieldState.PAUSED -> stringResource(R.string.home_shield_paused) to stringResource(R.string.home_shield_tap_on)
        ShieldState.OFF -> stringResource(R.string.home_shield_off) to stringResource(R.string.home_shield_tap_on)
    }
    val color = if (shield == ShieldState.PROTECTING) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(4.dp, color),
        modifier = Modifier
            .size(176.dp)
            .testTag("home:shield")
            .semantics { role = Role.Button }
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = MaterialTheme.typography.headlineMedium, color = color)
                Spacer(Modifier.size(4.dp))
                Text(hint, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun WarningCard(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth().testTag("home:warning"),
    ) {
        Text(text, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun GuardAlertCard(alert: GuardAlert, onUndo: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth().testTag("home:guard-alert:${alert.pkg}"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.home_guard_alert, alert.label, alert.ruleIds.size),
                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Text(alert.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
            OutlinedButton(onClick = onUndo, modifier = Modifier.heightIn(min = 48.dp).testTag("home:guard-undo:${alert.pkg}")) {
                Text(stringResource(R.string.home_guard_undo))
            }
        }
    }
}

@Composable
private fun EnginesCard(engines: List<EngineRow>, onOpenLog: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().testTag("home:engines")) {
        Column(Modifier.padding(vertical = 8.dp)) {
            engines.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .clickable { onOpenLog(row.engine.name) }.padding(horizontal = 16.dp)
                        .testTag("home:engine:${row.engine.name}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(engineName(row.engine), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stateName(row.state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (row.state == EngineState.RUNNING) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.sizeIn(minHeight = 48.dp).padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun engineName(engine: EngineId) = stringResource(
    when (engine) {
        EngineId.VPN -> R.string.engine_vpn
        EngineId.A11Y -> R.string.engine_a11y
        EngineId.NOTIFY -> R.string.engine_notify
        EngineId.SYSTEM -> R.string.engine_system
    },
)

@Composable
private fun stateName(state: EngineState) = stringResource(
    when (state) {
        EngineState.RUNNING -> R.string.state_running
        EngineState.STOPPED -> R.string.state_stopped
        EngineState.DEGRADED -> R.string.state_degraded
        EngineState.NOT_SETUP -> R.string.state_not_setup
    },
)
