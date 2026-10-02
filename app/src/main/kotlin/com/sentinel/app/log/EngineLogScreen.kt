package com.sentinel.app.log

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sentinel.app.R
import com.sentinel.data.db.EngineId
import com.sentinel.rules.model.EventKind
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EngineLogScreen(engine: EngineId, modifier: Modifier = Modifier,
    viewModel: EngineLogViewModel = koinViewModel { parametersOf(engine) }) {
    val events by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Column(modifier.fillMaxSize().testTag("screen:engine-log")) {
        Text(stringResource(R.string.log_title, engineLabel(engine)), style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp))
        if (events.isEmpty()) Text(stringResource(R.string.log_empty), modifier = Modifier.padding(horizontal = 16.dp))
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(events, key = { it.id }) { event ->
                val canOpen = engine == EngineId.NOTIFY && event.kind == EventKind.NOTIFICATION_CANCELLED && event.pkg != null
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .let { m ->
                            if (canOpen) m.clickable {
                                context.packageManager.getLaunchIntentForPackage(event.pkg!!)
                                    ?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                            } else m
                        },
                ) {
                    Text(event.detail ?: event.kind.name, style = MaterialTheme.typography.bodyMedium)
                    Text("${SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(Date(event.ts))} · ${event.pkg ?: "-"}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun engineLabel(engine: EngineId) = stringResource(
    when (engine) {
        EngineId.VPN -> R.string.engine_vpn
        EngineId.A11Y -> R.string.engine_a11y
        EngineId.NOTIFY -> R.string.engine_notify
        EngineId.SYSTEM -> R.string.engine_system
    },
)
