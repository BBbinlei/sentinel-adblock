package com.sentinel.app.apps

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sentinel.app.R
import com.sentinel.data.db.ProtectLevel
import org.koin.androidx.compose.koinViewModel

@Composable
fun AppsScreen(onOpen: (String) -> Unit, modifier: Modifier = Modifier, viewModel: AppsViewModel = koinViewModel()) {
    val rows by viewModel.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    Column(modifier.fillMaxSize().testTag("screen:apps")) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; viewModel.search(it) },
            label = { Text(stringResource(R.string.apps_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("apps:search"),
        )
        if (rows.isEmpty()) {
            Text(stringResource(R.string.apps_empty), modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge)
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            items(rows, key = { it.pkg }) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                        .clickable { onOpen(row.pkg) }.padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("app:${row.pkg}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(row.label, style = MaterialTheme.typography.bodyLarge)
                        Text(row.pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (row.defaultAllowed) {
                            Text(stringResource(R.string.apps_default_allowed), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(stringResource(R.string.apps_blocked_7d, row.blocked7d), style = MaterialTheme.typography.bodyMedium)
                        Text(levelName(row.level), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun levelName(level: ProtectLevel): String = stringResource(
    when (level) {
        ProtectLevel.OFF -> R.string.level_off
        ProtectLevel.STANDARD -> R.string.level_standard
        ProtectLevel.STRONG -> R.string.level_strong
    },
)
