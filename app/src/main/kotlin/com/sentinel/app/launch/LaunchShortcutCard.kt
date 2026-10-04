package com.sentinel.app.launch

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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

@Composable
fun LaunchShortcutCard(pkg: String) {
    val profile = LaunchCatalog.policy.forPackage(pkg) ?: return
    val context = LocalContext.current
    val manager = remember(context) { LaunchShortcuts(context) }
    var status by remember { mutableStateOf(manager.status(profile)) }
    var feedback by rememberSaveable { mutableStateOf<PinFeedback?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, manager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                status = manager.status(profile)
                if (feedback == PinFeedback.REQUESTED || feedback == PinFeedback.UNCONFIRMED) {
                    feedback = if (manager.pinned(profile)) PinFeedback.CONFIRMED else PinFeedback.UNCONFIRMED
                }
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchShortcutContent(profile, status, feedback, busy) {
        busy = true
        scope.launch {
            feedback = withContext(Dispatchers.IO) { manager.request(profile) }
            status = manager.status(profile)
            busy = false
        }
    }
}

@Composable
internal fun LaunchShortcutContent(profile: LaunchProfile, status: LaunchStatus,
    feedback: PinFeedback? = null, busy: Boolean = false, onCreate: () -> Unit = {}) {
    Card(Modifier.fillMaxWidth().testTag("launch:card")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("启动快捷方式", style = MaterialTheme.typography.titleMedium)
            Text(when (status) {
                LaunchStatus.VERIFIED -> "已验证"
                LaunchStatus.PENDING -> "待验证"
                LaunchStatus.UNSUPPORTED -> "当前版本不支持"
            }, modifier = Modifier.testTag("launch:status"))
            Text(when (status) {
                LaunchStatus.VERIFIED -> "已验证 ${profile.versionName} 的${profile.scenario}。从新快捷方式启动，原应用图标保留。后台切回不在本次保证范围内。"
                LaunchStatus.PENDING -> "尚未证实此入口能绕过商业开屏广告，暂不开放添加。${profile.evidence}"
                LaunchStatus.UNSUPPORTED -> "目标应用未安装、版本不匹配或入口不可用，需要重新验证。已添加的快捷方式将尝试普通启动。"
            }, style = MaterialTheme.typography.bodySmall)
            if (status == LaunchStatus.VERIFIED) {
                profile.notice?.let { Text(it, modifier = Modifier.testTag("launch:notice"), style = MaterialTheme.typography.bodySmall) }
                Button(onClick = onCreate, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("launch:create")) { Text("添加到桌面") }
            }
            feedback?.let {
                Text(when (it) {
                    PinFeedback.REQUESTED -> "已提交桌面创建请求，请在桌面提示中确认。尚未确认添加成功。"
                    PinFeedback.CONFIRMED -> "已确认添加到桌面"
                    PinFeedback.UNSUPPORTED -> "当前桌面不支持固定快捷方式"
                    PinFeedback.REJECTED -> "创建请求未被接受，请检查应用版本及桌面设置"
                    PinFeedback.UNCONFIRMED -> "尚未确认添加成功；若已取消，请重新添加。"
                }, modifier = Modifier.testTag("launch:feedback"), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
