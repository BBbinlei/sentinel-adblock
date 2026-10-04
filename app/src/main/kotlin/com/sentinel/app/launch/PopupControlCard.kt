package com.sentinel.app.launch

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
fun PopupControlCard(pkg: String) {
    if (pkg != YangshipinPopup.PACKAGE) return
    val context = LocalContext.current
    val control = koinInject<PopupRuleControl>()
    val rules by control.rules.collectAsState(initial = emptyList())
    val enabled = rules.any { it.id == YangshipinPopup.RULE_ID }
    var supported by remember { mutableStateOf(YangshipinPopup.supported(context)) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) supported = YangshipinPopup.supported(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Card(Modifier.fillMaxWidth().testTag("popup:card")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("首页广告弹窗", style = MaterialTheme.typography.titleMedium)
            Text("自动点广告弹窗的关闭按钮，需要开启哨兵无障碍并保持应用防护开启。快捷启动不依赖此功能。", style = MaterialTheme.typography.bodySmall)
            if (!supported) Text("当前版本不支持，需要重新验证")
            if (supported || enabled) Button(onClick = {
                busy = true
                scope.launch {
                    message = try {
                        withContext(Dispatchers.IO) { control.setEnabled(context, !enabled) }
                        if (enabled) "已关闭自动关闭规则" else "已启用，请保持无障碍和应用防护开启"
                    } catch (_: Exception) { "操作失败，请重试" }
                    busy = false
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("popup:toggle")) {
                Text(if (enabled) "关闭自动关闭" else "启用自动关闭首页广告")
            }
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("检查无障碍设置") }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
