package com.sentinel.app.system

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sentinel.app.R
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OpLogScreen(modifier: Modifier = Modifier, viewModel: OpLogViewModel = koinViewModel()) {
    val logs by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize().testTag("screen:op-log")) {
        Text(stringResource(R.string.oplog_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
        if (logs.isEmpty()) Text(stringResource(R.string.oplog_empty), modifier = Modifier.padding(horizontal = 16.dp))
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(logs, key = { it.id }) { entry ->
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.opId, style = MaterialTheme.typography.bodyLarge)
                        Text(SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(entry.ts)), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(when {
                            !entry.success -> R.string.oplog_failed
                            entry.undone -> R.string.oplog_undone
                            else -> R.string.oplog_applied
                        }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (entry.success && !entry.undone) {
                        OutlinedButton(onClick = { viewModel.undo(entry.id) }, modifier = Modifier.heightIn(min = 48.dp).testTag("oplog:undo:${entry.id}")) {
                            Text(stringResource(R.string.oplog_undo))
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
