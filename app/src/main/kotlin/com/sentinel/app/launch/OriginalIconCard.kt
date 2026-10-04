package com.sentinel.app.launch

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

@Composable
fun OriginalIconCard(pkg: String) {
    if (pkg != OriginalEntryPolicy.PKG) return
    val context = LocalContext.current
    val control = remember { OriginalIconControl.get(context) }
    val state by control.state.collectAsState()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) control.refresh() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val supported = android.os.Build.VERSION.SDK_INT == 31 &&
        LaunchShortcuts(context).status(LaunchCatalog.policy.byId("yangshipin")!!) == LaunchStatus.VERIFIED
    OriginalIconContent(state, supported, control::enable, control::disable)
}
@Composable
internal fun OriginalIconContent(state: String, supported: Boolean, enable: () -> Unit, disable: () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("original:card")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("原图标自动转接", style = MaterialTheme.typography.titleMedium)
            Text(state, Modifier.testTag("original:state"))
            Text("点击原央视频图标，在开屏页面启动前转入已验证的首页路径。需要 Shizuku 运行并授权；手机重启后需重新启动 Shizuku。首页弹窗请启用下方自动关闭。首轮仅验证华为 Android 12。", style = MaterialTheme.typography.bodySmall)
            if (supported) Button(enable, Modifier.fillMaxWidth().testTag("original:enable")) { Text("启用原图标转接 / 授权") }
            OutlinedButton(disable, Modifier.fillMaxWidth().testTag("original:disable")) { Text("关闭原图标转接") }
        }
    }
}
