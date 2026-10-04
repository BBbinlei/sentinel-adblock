package com.sentinel.app.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sentinel.app.R
import com.sentinel.data.db.ProtectLevel
import com.sentinel.data.db.RewardedMode
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AppDetailScreen(pkg: String, modifier: Modifier = Modifier,
    viewModel: AppDetailViewModel = koinViewModel { parametersOf(pkg) }) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    var advanced by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("screen:app-detail"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(ui.label, style = MaterialTheme.typography.headlineSmall)
            Text(pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // 首屏可见：一键临时放行（无确认弹窗，靠 24 小时自动恢复兜底）
        Button(
            onClick = viewModel::tempAllow,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("app:temp-allow"),
        ) { Text(stringResource(R.string.detail_temp_allow)) }
        ui.tempAllowedUntil?.let {
            Text(stringResource(R.string.detail_temp_allowed_until, SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(it))),
                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }

        Text(stringResource(R.string.detail_level), style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val levels = ProtectLevel.entries
            levels.forEachIndexed { index, level ->
                SegmentedButton(
                    selected = ui.level == level,
                    onClick = { viewModel.setLevel(level) },
                    shape = SegmentedButtonDefaults.itemShape(index, levels.size),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("app:level:${level.name}"),
                ) { Text(levelName(level)) }
            }
        }

        ui.observingDaysLeft?.let { days ->
            Card(Modifier.fillMaxWidth().testTag("app-detail:observing")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.detail_observing, days), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.detail_observing_hint), style = MaterialTheme.typography.bodyMedium)
                    if (ui.wouldBlock.isEmpty()) {
                        Text(stringResource(R.string.detail_would_block_empty), style = MaterialTheme.typography.bodySmall)
                    } else {
                        ui.wouldBlock.forEach { domain -> Text(domain, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }

        com.sentinel.app.launch.OriginalIconCard(pkg)
        com.sentinel.app.launch.LaunchShortcutCard(pkg)
        com.sentinel.app.launch.PopupControlCard(pkg)

        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { advanced = !advanced }.testTag("app-detail:advanced"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.detail_advanced), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(stringResource(if (advanced) R.string.detail_collapse else R.string.detail_expand),
                color = MaterialTheme.colorScheme.primary)
        }
        if (advanced) {
            val cfg = ui.config
            ToggleRow(R.string.toggle_splash, cfg.splash, "SPLASH") { viewModel.toggle(AppToggle.SPLASH, it) }
            ToggleRow(R.string.toggle_shake, cfg.shake, "SHAKE") { viewModel.toggle(AppToggle.SHAKE, it) }
            ToggleRow(R.string.toggle_jump_back, cfg.jumpBack, "JUMP_BACK") { viewModel.toggle(AppToggle.JUMP_BACK, it) }
            ToggleRow(R.string.toggle_notify, cfg.notify, "NOTIFY") { viewModel.toggle(AppToggle.NOTIFY, it) }
            ToggleRow(R.string.toggle_limit_overlay, cfg.limitOverlay, "LIMIT_OVERLAY") { viewModel.toggle(AppToggle.LIMIT_OVERLAY, it) }
            ToggleRow(R.string.toggle_deny_clipboard, cfg.denyClipboard, "DENY_CLIPBOARD") { viewModel.toggle(AppToggle.DENY_CLIPBOARD, it) }

            Text(stringResource(R.string.detail_rewarded), style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val modes = RewardedMode.entries
                modes.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = cfg.rewarded == mode,
                        onClick = { viewModel.setRewarded(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("app:rewarded:${mode.name}"),
                    ) { Text(stringResource(rewardedName(mode))) }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: Int, checked: Boolean, key: String, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .testTag("app:toggle:$key"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

private fun rewardedName(mode: RewardedMode): Int = when (mode) {
    RewardedMode.SILENT -> R.string.rewarded_silent
    RewardedMode.BLOCK -> R.string.rewarded_block
    RewardedMode.ASK -> R.string.rewarded_ask
}
