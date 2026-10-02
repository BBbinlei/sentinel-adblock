package com.sentinel.app.system

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sentinel.app.R
import com.sentinel.system.ops.OpStatus
import com.sentinel.system.profile.OpKind
import com.sentinel.system.profile.ProfileOp
import org.koin.androidx.compose.koinViewModel

@Composable
fun SystemCleanupScreen(onOpenOpLog: () -> Unit, modifier: Modifier = Modifier, viewModel: SystemCleanupViewModel = koinViewModel()) {
    val items by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 卸载类操作必须二次确认；其他操作不弹确认，靠撤销兜底
    var confirming by remember { mutableStateOf<ProfileOp?>(null) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("screen:system-cleanup"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.cleanup_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.cleanup_hint), style = MaterialTheme.typography.bodyMedium)
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("cleanup:message")) }
        Button(onClick = viewModel::applyAll, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("cleanup:apply-all")) {
            Text(stringResource(R.string.cleanup_apply_all))
        }
        if (items.isEmpty()) Text(stringResource(R.string.cleanup_empty), style = MaterialTheme.typography.bodyLarge)

        items.forEach { item ->
            val op = item.op
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable {
                    when {
                        op.kind == OpKind.WIZARD -> viewModel.openSettings(op.id)?.let { runCatching { context.startActivity(it) } }
                        !item.canAuto -> Unit
                        op.kind == OpKind.UNINSTALL -> confirming = op
                        else -> viewModel.apply(op.id)
                    }
                }.testTag("cleanup:${op.id}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(op.title, style = MaterialTheme.typography.bodyLarge)
                    Text(statusText(item), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
        }

        OutlinedButton(onClick = viewModel::undoAll, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("cleanup:undo-all")) {
            Text(stringResource(R.string.cleanup_undo_all))
        }
        OutlinedButton(onClick = onOpenOpLog, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("cleanup:op-log")) {
            Text(stringResource(R.string.home_op_log))
        }
    }

    confirming?.let { op ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(stringResource(R.string.cleanup_confirm_title)) },
            text = { Text(stringResource(R.string.cleanup_confirm_body, op.title)) },
            confirmButton = {
                TextButton(onClick = { confirming = null; viewModel.apply(op.id) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("cleanup:confirm")) { Text(stringResource(R.string.cleanup_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("cleanup:cancel")) { Text(stringResource(R.string.cleanup_cancel)) }
            },
        )
    }
}

@Composable
private fun statusText(item: CleanupItem): String = when {
    !item.op.verified -> stringResource(R.string.cleanup_unverified)
    item.op.kind == OpKind.WIZARD -> stringResource(R.string.cleanup_wizard)
    item.status == OpStatus.APPLIED -> stringResource(R.string.cleanup_applied)
    item.status == OpStatus.NOT_APPLIED -> stringResource(if (item.op.kind == OpKind.UNINSTALL) R.string.cleanup_tap_uninstall else R.string.cleanup_tap_apply)
    else -> stringResource(R.string.cleanup_unknown)
}
