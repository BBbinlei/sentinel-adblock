当前只读沙箱禁止写入文件，因此指定的 `REVIEW.md` **尚未生成**。以下为完整报告；未修改仓库、提交代码、运行 Gradle 或设备操作。

**复核基线：** `main`，提交 `1c8758f23dd92a2edfd0c6d20e9fe3f77748d89f`。已阅读要求的约束、契约、接力文档及五个模块的 README、PLAN、全部 `src/main`。结论来自静态调用链和 Android 官方资料，未把历史测试结果当成本次验证。

## 阻塞项

### R01：DNS 上游故障会使正常域名持续解析失败

**位置：** `engine-vpn/src/main/kotlin/com/sentinel/vpn/dns/UpstreamResolver.kt:62`；`engine-vpn/src/main/kotlin/com/sentinel/vpn/tun/PacketLoop.kt:53`。

**问题：** DoH 和 UDP 都失败时返回 SERVFAIL，报文循环继续运行，控制器不知道上游已经不可用，也不会关闭 TUN。系统 DNS 仍指向虚拟 DNS，故障直接影响正常应用联网。

**触发场景：** 当前网络能使用运营商 DNS，但阻断 `223.5.5.5` 的 HTTPS 和 UDP/53。未缓存的正常域名全部收到 SERVFAIL，VPN 仍显示运行，应用无法建立新连接。

**建议修法：** 将上游传输失败与有效 DNS 错误应答区分，传输全部失败时通知控制器关闭 TUN、恢复系统网络。PLAN 中“都失败返回 SERVFAIL”也需要同步修正，否则与 Global Constraints 冲突。

### R02：服务销毁时存在主线程与生命周期锁死锁

**位置：** `engine-vpn/src/main/kotlin/com/sentinel/vpn/service/SentinelVpnService.kt:151`；`engine-vpn/src/main/kotlin/com/sentinel/vpn/service/VpnController.kt:62`、`:140`。

**问题：** `onDestroy()` 在主线程 `runBlocking` 等待 `stop()` 获取 `lifecycle`。同一把锁可以由服务主线程协程持有，并在 `status.report()` 等数据库操作上挂起；该协程恢复需要主线程，而主线程正在阻塞等待它释放锁。

**触发场景：** VPN 已运行，网络回调调用 `onPrivateDns()`，持锁后等待 Room 写状态；此时服务被停止并进入 `onDestroy()`。销毁线程与锁持有者互相等待，产生 ANR，锁内的 TUN 关闭尚未执行。

**建议修法：** 生命周期回调不要阻塞等待主线程协程。把 TUN 释放设计成不依赖挂起操作的幂等收尾步骤；状态写库、事件刷新随后异步完成。

### R03：AppOpsSync 绕过档案验证，自行制造 `verified=true`

**位置：** `engine-system/src/main/kotlin/com/sentinel/system/ops/AppOpsSync.kt:47`、`:84`。

**问题：** 按 App 的操作不是从已验证档案取得，而是现场构造 `ProfileOp(verified=true)`。即使 `profile == null`，仍会执行 `SYSTEM_ALERT_WINDOW` 和 `READ_CLIPBOARD` 操作；`backgroundPopupOp` 也只校验名称格式，没有对应验证标记。

**触发场景：** 当前 ROM 没有匹配档案，Shizuku 为 READY；某应用开启“禁止读取剪贴板”。代码仍执行 `appops set <pkg> READ_CLIPBOARD ignore`，绕过“只执行配置档案中 verified=true 的项”的限制。

**建议修法：** 从匹配档案的已验证能力生成操作；没有验证记录时保留待处理意图或提供设置引导。不得由执行器调用方自行声明已验证。

### R04：系统修改成功、复核失败后，撤销入口会拒绝恢复

**位置：** `engine-system/src/main/kotlin/com/sentinel/system/ops/OpExecutor.kt:76`、`:90`、`:106`。

**问题：** 修改命令成功后，如果再次探测失败，日志记录 `success=false`，直接返回 Failed。该路径不回滚，而 `undo()`、`undoAll()` 又只接受 `success=true` 的记录，导致已经发生的修改失去正常撤销入口。

**触发场景：** 禁止剪贴板的命令执行成功，紧接着 Shizuku 断开，复核命令失败。日志被记为失败；Shizuku 恢复后，点击撤销仍返回 false，应用继续无法读取剪贴板。

**建议修法：** 区分“未修改”“可能已修改”“已确认修改”。修改前持久保存恢复信息；复核失败时尝试回滚，并让待恢复记录可以再次撤销，不能用复核结果决定是否允许恢复。

### R05：允许系统 VPN 锁定模式，会切断排除应用的网络

**位置：** `engine-vpn/src/main/AndroidManifest.xml:10`；`engine-vpn/src/main/kotlin/com/sentinel/vpn/tun/TunSpec.kt:14`。

**问题：** Manifest 没有退出 always-on 支持，但本项目依赖排除应用和非 VPN 流量直接联网。系统“阻止不使用 VPN 的连接”会阻断这些直连流量；关闭 TUN 也不能保证恢复直连。Android 官方明确说明，锁定模式下排除名单中的应用失去网络。[官方说明](https://developer.android.com/develop/connectivity/vpn#blocked-connections)

**触发场景：** 用户在系统设置开启始终开启 VPN，并开启“阻止不使用 VPN 的连接”。银行、支付及用户排除的应用仍被加入 disallowed 集合，却因此无法联网。

**建议修法：** 当前架构应声明 `android.net.VpnService.SUPPORTS_ALWAYS_ON=false`，禁用不兼容的系统设置入口；不能仅依赖故障时关闭 TUN。

## 重要项

### R06：真机 TUN 无报文时会持续空转

**位置：** `engine-vpn/src/main/kotlin/com/sentinel/vpn/service/SentinelVpnService.kt:45`；`engine-vpn/src/main/kotlin/com/sentinel/vpn/tun/PacketLoop.kt:28`。

**问题：** Builder 未调用 `setBlocking(true)`；真实 TUN 默认非阻塞。Android 的 `FileInputStream` 底层在 EAGAIN 时返回 0，随后 `IpPacket.parse(..., 0)` 返回 null，循环立即再次读取，没有等待。

**触发场景：** VPN 启动后手机暂时没有 DNS 或 HttpDNS 请求。循环不断读取空接口，占用 CPU、耗电；普通内存流或阻塞流无法体现这个问题。[Builder 文档](https://developer.android.com/reference/android/net/VpnService.Builder#setBlocking(boolean))、[Android IoBridge 源码](https://android.googlesource.com/platform/prebuilts/fullsdk/sources/+/refs/heads/androidx-media-release/android-34/libcore/io/IoBridge.java)

**建议修法：** 使用与当前读取方式匹配的阻塞接口，并确认关闭描述符能解除等待；或者使用可取消的 `poll` 等待可读事件。

### R07：HttpDNS 快速拒绝不尊重规则失效、观察期和规则停用

**位置：** `engine-vpn/src/main/kotlin/com/sentinel/vpn/tun/PacketLoop.kt:57`。

**问题：** 命中 CIDR 后无条件返回 RST/ICMP，不读取 matcher、EffectiveConfig 或 disabledRules。DNS 分支的宽松判定无法影响该路径。

**触发场景：** 新应用仍在观察期，请求目录中的 HttpDNS IP，连接实际被拒绝；或者域名规则加载失败，状态显示“暂停网络拦截”，HttpDNS IP 仍被拒绝。guard 停用该应用的 HTTPDNS 域名规则也不能解除 IP 拒绝。

**建议修法：** 让 IP 拒绝受同一生效策略控制。需要放行时必须具备真实转发路径，或重建 TUN 移除对应路由／排除应用，不能只丢弃报文。

### R08：异步协程读取已经返回给系统的 AccessibilityEvent

**位置：** `engine-a11y/src/main/kotlin/com/sentinel/a11y/service/SentinelAccessibilityService.kt:150`。

**问题：** `dispatchWindow()` 把整个 `event` 捕获进 IO 协程，之后才读取 `event.source`。事件属于调用方，回调返回后不能继续使用。[官方生命周期约束](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#onAccessibilityEvent(android.view.accessibility.AccessibilityEvent))

**触发场景：** 在支持的 Android 11/12 上，工作协程排队期间回调返回，事件被回收或复用。协程读到空 source 或另一事件的 source，导致漏处理或使用错误节点。

**建议修法：** 在回调返回前提取需要的数据和 source；若必须保留整个事件，应复制事件并管理副本生命周期。

### R09：悬浮窗的主线程 Runnable 不在异常保护范围内

**位置：** `engine-a11y/src/main/kotlin/com/sentinel/a11y/service/OverlayToast.kt:53`、`:67`、`:39`。

**问题：** `showUndo()`、`showRewardedAsk()` 只负责 `Handler.post`。真正创建和添加窗口发生在稍后的主线程 Runnable，未捕获异常；ActionExecutor 外层的 try/catch 无法保护它。

**触发场景：** 悬浮窗任务已排队，系统撤销无障碍窗口 token；Runnable 执行 `wm.addView()` 时抛出 `BadTokenException`。异常直接逃出主线程，主进程崩溃。

**建议修法：** 在 Runnable 内捕获窗口操作异常，检查服务连接状态；服务退出后拒绝新展示任务，并在失败时清理局部状态。

### R10：经过桌面的独立启动会被误判为广告跳转

**位置：** `engine-a11y/src/main/kotlin/com/sentinel/a11y/core/ForegroundTracker.kt:40`、`:58`；`engine-a11y/src/main/kotlin/com/sentinel/a11y/core/A11yBrain.kt:109`。

**问题：** 桌面事件仅改变 `originIsLauncher`，保留旧 `currentPkg` 和旧 launch。之后启动另一应用时，仍生成“旧应用 → 新应用”的 Transition；JumpBackGuard 使用旧快照判定，无法知道中间经过桌面。

**触发场景：** 从桌面启动 A，1 秒后按 Home，再从桌面启动淘宝，整个过程小于 5 秒，A 内没有点击。代码生成 A→淘宝，并按 A 的启动窗口把用户带回 A。

**建议修法：** 在经过桌面或其他打断来源时切断直接跳转关系；不要向 JumpBackGuard 传递跨越桌面的 Transition，同时重新记录再次启动的信息。

### R11：节点点击失败后，坐标点击可能落到敏感应用

**位置：** `engine-a11y/src/main/kotlin/com/sentinel/a11y/service/ActionExecutor.kt:68`。

**问题：** ACTION_CLICK 失败后直接使用旧节点坐标执行全局手势，没有重新检查当前窗口包名、节点有效性或当前应用的排除状态。先前匹配时的配置检查不能保护这个后备路径。

**触发场景：** A 的关闭节点被选中，用户随后切到银行应用。旧节点 ACTION_CLICK 返回 false，执行器仍点击旧坐标中心；该坐标现在对应银行页面的按钮。

**建议修法：** 删除无法确认目标的坐标后备点击。若保留，执行前重新取得并验证当前窗口、目标节点和应用策略；校验失败即放弃点击。

### R12：写跳转例外失败，会阻止用户完成撤销

**位置：** `engine-a11y/src/main/kotlin/com/sentinel/a11y/service/A11yActionReceiver.kt:52`。

**问题：** `addException()` 位于打开目标应用之前，且没有独立异常保护。其数据库写入失败会直接退出 UndoJump，既不打开目标应用，也不继续发 USER_UNDO。

**触发场景：** 用户点击“撤销”，跳转例外表因磁盘满或数据库故障写入失败。外层只记录“撤销失败”，用户仍停留在刚被强制返回的源应用。

**建议修法：** 分别保护例外写入、信号写入和目标恢复。例外无法保存时仍执行本次恢复；并提供本次临时例外，避免恢复后立即再次回退。

### R13：guard 撤销不是原子操作，中断会丢失钉住状态

**位置：** `guard/src/main/kotlin/com/sentinel/guard/runtime/GuardRunner.kt:37`；`data/src/main/kotlin/com/sentinel/data/repo/OverrideRepository.kt:20`。

**问题：** `remove()` 与 `pin()` 是两个独立提交。前者删除已有记录，后者失败或进程中断时，用户的钉住意图丢失；重复撤销还会先删除已经存在的 PINNED。

**触发场景：** remove 提交后，主进程被杀或 pin 写入失败。重启后该规则没有 PINNED 记录，后续误伤信号可以再次将它停用。

**建议修法：** 直接调用现有 `pin()`；其 upsert 已能用 PINNED 覆盖 DISABLED，无需先删除。多个写入只有需要整体原子性时才增加事务。

**边界说明：** 单次撤销和 disable 都成功完成时，最终会收敛为 PINNED；这里报告的是可持久留下错误状态的中断窗口，而非声称所有并发都会产生错误最终状态。

### R14：一个历史信号会无限延长观察期

**位置：** `guard/src/main/kotlin/com/sentinel/guard/runtime/ObservationWorker.kt:24`。

**问题：** 每次评估都从固定的 `firstSeenAt` 统计信号，没有推进已评估边界。第一次用于延长的 USER_UNDO/TEMP_ALLOW，在下一轮仍被计入。

**触发场景：** 第 1 天发生一次临时放行；第 3 天延长至第 6 天。之后没有新信号，第 6 天仍统计到同一记录，再延长至第 9 天，如此重复，应用一直不进入网络拦截。

**建议修法：** 持久记录每轮观察起点或上次评估边界，只用本轮新增信号决定是否继续延长。

此行为也来自 PLAN 的 `[firstSeenAt, now]` 定义，属于计划本身的逻辑缺口，需要同步修正文档。

### R15：守护只看当前 RUNNING，无法识别“曾经运行但已停止”

**位置：** `engine-vpn/src/main/kotlin/com/sentinel/vpn/service/SentinelVpnService.kt:88`。

**问题：** `wanted` 只包含当前状态为 RUNNING 的引擎。正常 `onUnbind`／`onListenerDisconnected` 已写 STOPPED 后，该引擎就被当作用户不需要，与 C4 的“曾经为 RUNNING”不一致。

**触发场景：** 用户开启过无障碍，随后系统关闭服务并触发 STOPPED 上报。下一次守护检查将 A11Y 排除在 wanted 外，不会发重新开启提醒。

**建议修法：** 独立持久保存用户期望或曾经运行标记；不要从当前运行状态推导历史意图。DEGRADED 也不能被解释为用户不需要该引擎。

### R16：DropBox 崩溃时间被替换成采集时间，guard 回溯错误

**位置：** `engine-system/src/main/kotlin/com/sentinel/system/crash/DropboxCrashWorker.kt:41`；`data/src/main/kotlin/com/sentinel/data/repo/SignalRepository.kt:20`；`guard/src/main/kotlin/com/sentinel/guard/runtime/GuardRunner.kt:44`。

**问题：** Worker 解析出了 `e.ts`，但发信号时没有保存它；SignalRepository 使用当前时间。guard 从信号时间向前回溯 2 分钟，而周期采集可能延迟近 30 分钟。

**触发场景：** 10:00 某规则命中，10:01 应用崩溃，10:30 才采集到记录。guard 查询 10:28 以后的命中，找不到真正相关的规则；若期间有其他命中，还可能停用无关规则。

**建议修法：** 保留事件发生时间与采集时间，guard 用发生时间回溯，并限制命中查询的时间上界。

### R17：动态 AppOps 操作发生漂移后，所有恢复路径都会漏掉它

**位置：** `engine-system/src/main/kotlin/com/sentinel/system/ops/AppOpsSync.kt:62`；`engine-system/src/main/kotlin/com/sentinel/system/drift/DriftInspector.kt:22`。

**问题：** AppOpsSync 把成功日志及相同命令当作当前仍生效，不再探测；DriftInspector 仅遍历 `profile.ops`，不会检查动态生成的 `appop:*` 操作。

**触发场景：** READ_CLIPBOARD 已设置为 ignore 并写成功日志，系统后来恢复为 allow。即使再次触发同步，active 日志仍使它跳过 apply；每日巡检也不会发现该项，界面开关保持开启但实际限制已失效。

**建议修法：** 同步时探测实际状态，并将动态 AppOps 纳入巡检或同步复核。只有确认发生漂移才重新执行，同时保留第一次修改前的恢复状态。

## 已检查、未列为缺陷的重点

- **engine-notify：已检查，未发现问题。** 当前取消和学习路径尊重 OFF、自身通知及 ongoing；回调有异常保护，规则订阅使用 ruleVersion。
- **奖励窗口 C1：** 生产调用中只有 a11y 开窗、vpn 读取；TTL 和释放标签引用 data contract，过期按 `until > now` 判定。
- **信号 C2：** guard 穷举处理现有全部 SignalKind；SignalRepository 过滤无效包名及 OFF，并保护写入失败。RETRY_STORM 无 ruleId 返回 NoRuleFound 是合理保守处理：当前 VPN 发射路径要求存在命中 ruleId，没有证据表明应据此停用其他规则。
- **`flowOn(Dispatchers.Unconfined)`：** 会改变上游执行线程，因此“生产行为不变”表述不准确。但当前路径具有 `distinctUntilChanged`、防抖、生命周期 Mutex 和 TunSpec 去重，没有发现它导致取消失效、失控背压或重复建立相同 TUN 的证据，不列为缺陷。[Unconfined 文档](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-dispatchers/-unconfined.html)
- **TUN 正常替换及 PacketLoop 普通异常：** 替换路径会关闭旧 handle；读写异常或正常 EOF 会进入停止路径。未发现正常替换遗漏关闭旧 TUN。销毁时的锁问题见 R02。
- **私人 DNS：** 警告通过控制器上报带 message 的 DEGRADED，恢复时重新评估规则状态；未发现该检测主动改变系统私人 DNS 设置。
- **正常停止状态：** a11y、notify 正常退出写 STOPPED，没有写回 NOT_SETUP；守护对历史意图的判断问题见 R15。
- **系统 REAPPLY：** Receiver→DriftInspector→OpExecutor.applyAll→apply 仍经过 verified 检查；绕过验证的是 R03 的操作生成路径。
- **设置键原本不存在：** 对标准 `settings put <namespace> <key> {before}` 撤销模板，`before == "null"` 会转换为 `settings delete`，已检查该路径，未发现问题。
- **前台服务声明：** specialUse 类型、对应权限和 subtype 均已声明；未发现这些声明本身违反 Android 14/15/16 要求。已确认的平台兼容问题见 R05。

## 按严重程度排序的摘要

| 编号 | 严重程度 | 模块 | 发现 |
|---|---|---|---|
| R01 | 阻塞 | engine-vpn | 上游故障后保留 TUN，正常域名持续 SERVFAIL |
| R02 | 阻塞 | engine-vpn | onDestroy 阻塞主线程，与生命周期锁形成死锁 |
| R03 | 阻塞 | engine-system | 动态 AppOps 自行声明 verified，绕过档案验证 |
| R04 | 阻塞 | engine-system | 修改成功但复核失败的操作无法正常撤销 |
| R05 | 阻塞 | engine-vpn | 系统锁定模式切断排除应用网络 |
| R06 | 重要 | engine-vpn | 非阻塞 TUN 在空闲时持续占用 CPU |
| R07 | 重要 | engine-vpn | HttpDNS IP 拒绝绕过观察期及宽松策略 |
| R08 | 重要 | engine-a11y | 回调返回后异步使用 AccessibilityEvent |
| R09 | 重要 | engine-a11y | 悬浮窗 Runnable 异常可导致主进程崩溃 |
| R10 | 重要 | engine-a11y | 经过桌面的独立启动被误判为跳转 |
| R11 | 重要 | engine-a11y | 旧坐标手势可能误点敏感应用 |
| R12 | 重要 | engine-a11y | 例外写入失败阻止本次撤销恢复 |
| R13 | 重要 | guard | remove＋pin 中断后丢失钉住状态 |
| R14 | 重要 | guard | 同一历史信号导致观察期无限延长 |
| R15 | 重要 | engine-vpn | 当前状态无法表达“曾经运行”的守护意图 |
| R16 | 重要 | engine-system / guard | 崩溃采集时间导致回溯错位 |
| R17 | 重要 | engine-system | 动态 AppOps 漂移无法被同步或巡检发现 |