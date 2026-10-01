package com.sentinel.app.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.sentinel.data.db.SubscriptionEntity
import com.sentinel.data.db.SubscriptionFormat
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RulesScreen(modifier: Modifier = Modifier, viewModel: RulesViewModel = koinViewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var format by rememberSaveable { mutableStateOf(SubscriptionFormat.HOSTS) }

    LazyColumn(
        modifier.fillMaxSize().testTag("screen:rules"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.rules_subscriptions), style = MaterialTheme.typography.titleMedium)
                Button(
                    onClick = viewModel::updateNow,
                    enabled = !ui.updating,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("rules:update"),
                ) { Text(stringResource(if (ui.updating) R.string.rules_updating else R.string.rules_update_now)) }
                ui.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("rules:message")) }
            }
        }
        items(ui.subscriptions, key = { it.id }) { sub ->
            SubscriptionRow(sub) { on -> viewModel.toggle(sub.id, on) }
            HorizontalDivider()
        }

        item {
            Text(stringResource(R.string.rules_user), style = MaterialTheme.typography.titleMedium)
        }
        if (ui.userRules.isEmpty()) {
            item { Text(stringResource(R.string.rules_user_empty), style = MaterialTheme.typography.bodyMedium) }
        }
        items(ui.userRules, key = { it.id }) { rule ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(rule.id, style = MaterialTheme.typography.bodyMedium)
                    Text(rule.source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { viewModel.deleteUserRule(rule.id) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("rules:delete:${rule.id}")) {
                    Text(stringResource(R.string.rules_delete))
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.rules_add), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.rules_name)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth().testTag("rules:name"))
                OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.rules_url)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth().testTag("rules:url"))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val formats = SubscriptionFormat.entries
                    formats.forEachIndexed { index, f ->
                        SegmentedButton(
                            selected = format == f,
                            onClick = { format = f },
                            shape = SegmentedButtonDefaults.itemShape(index, formats.size),
                            modifier = Modifier.heightIn(min = 48.dp).testTag("rules:format:${f.name}"),
                        ) { Text(formatName(f)) }
                    }
                }
                OutlinedButton(
                    onClick = { viewModel.add(name, url, format); if (url.startsWith("https://")) { name = ""; url = "" } },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("rules:add"),
                ) { Text(stringResource(R.string.rules_add_button)) }
            }
        }
    }
}

@Composable
private fun SubscriptionRow(sub: SubscriptionEntity, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .toggleable(value = sub.enabled, role = Role.Switch, onValueChange = onToggle)
            .testTag("rules:sub:${sub.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(sub.name, style = MaterialTheme.typography.bodyLarge)
            val updated = sub.lastUpdatedAt?.let { SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(it)) }
                ?: stringResource(R.string.rules_never_updated)
            Text(stringResource(R.string.rules_sub_meta, sub.ruleCount, updated), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            sub.lastError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
        Switch(checked = sub.enabled, onCheckedChange = null)
    }
}

private fun formatName(f: SubscriptionFormat) = when (f) {
    SubscriptionFormat.HOSTS -> "Hosts"
    SubscriptionFormat.ADGUARD -> "AdGuard"
    SubscriptionFormat.GKD -> "GKD"
    SubscriptionFormat.SENTINEL_JSON -> "JSON"
}
