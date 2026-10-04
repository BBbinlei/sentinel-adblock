# 进度表

> 规则见 `docs/HANDOFF.md`。每个通道标题行的格式固定，监督脚本只解析「状态」：
> `## [通道] 状态: <待办|进行中|等待|完成|已合并> | 负责方: <codex|claude|用户> | 关卡: <Gx>`

（`core-rules`、`data` 直接在 `main` 上做，状态到 `完成` 即视为已合并；其余通道在各自 worktree 分支上做，由合并通道并入 `main` 后标 `已合并`。）

## [core-rules] 状态: 完成 | 负责方: codex | 关卡: G1

| Task | 状态 | 最后提交 |
|---|---|---|
| Task 1: 规则模型与域名解析器 | 完成 | 70291f1 |
| Task 2: 域名编译器与匹配器 | 完成 | c0c0b11 |
| Task 3: 内置目录 | 完成 | 8a9ff77 |
| Task 4: 选择器 | 完成 | d022343 |
| Task 5: UI 规则解析、索引与内置规则 | 完成 | 247887e |
| Task 6: 通知匹配 | 完成 | d6614a0 |

- 下一步：G1 已通过；本通道在 main 上完成，可启动 data（G2）。MT-CR 提交 8bc9562；RR-01 7caea70；RR-04 14ae1d5；RR-05 e162c6c。报告：testing/reports/G1-2026-10-02.md。
- 已知问题：无阻塞项。本次允许正常运行 Gradle，已通过 :core-rules:test（27 项）、:testing:rule-regression:testDebugUnitTest（8 项）及全量 test，失败/跳过均为 0；历史并发会话 120dea3、9d184cc 的沙箱阻塞记录已被实际结果取代，未改写其提交。现有 top-domains fixture 为 987 条，按当前用户指令保持只读，STANDARD/STRONG 命中均为 0。RR-05 使用学习风格合成样本，未执行真机学习录制。

## [data] 状态: 完成 | 负责方: codex | 关卡: G2

| Task | 状态 | 最后提交 |
|---|---|---|
| Task 1: 数据库、实体与生效配置 | 完成 | 2f71e04 |
| Task 2: 通用仓库 | 完成 | 7c96dd4 |
| Task 3: App 配置仓库与 App 登记 | 完成 | 1c6f438 |
| Task 4: 规则存储 | 完成 | e4ab2ad |
| Task 5: 规则构建与订阅更新 | 完成 | 6314051 |
| Task 6: Koin 模块与模块接线入口 | 完成 | 2c1b83e |

- 下一步：G2 已通过：data 46/46、./gradlew test 全量 81/81、check-merge.sh data 全部通过；报告 testing/reports/G2-2026-10-02.md。等待合并通道确认并登记 g2-frozen，再放行后续引擎开发。
- 已知问题：GKD 官方 README 标明规则暂时停止维护（2026-10-02 核对）；保留计划指定的官方订阅，离线内置规则仍可用，不阻塞 G2。

## [engine-vpn] 状态: 已合并 | 负责方: claude | 关卡: G3

| Task | 状态 | 最后提交 |
|---|---|---|
| Task 1: 报文解析与构造 | 完成（UT 7/7） | d7f1be5 |
| Task 2: DNS 报文与判定 | 完成（UT 12/12） | e939b70 |
| Task 3: 上游解析与缓存 | 完成（UT 5/5） | b31d7cd |
| Task 4: TUN 配置与报文循环 | 完成（UT 7/7） | 本提交 |
| Task 5: VpnController 与服务接线 | 完成（UT 7/7） | 本提交 |
| Task 6: 私人 DNS 检测与服务守护 | 完成（UT 3/3） | 本提交 |
| Task 7: 故障安全 | 完成（UT 5/5） | 本提交 |
| R01（Task 3/5）: DNS 上游连续传输失败退出 | 已修，48/48 通过 | 867bd0b |
| R01 离线补修（Task 5/6）: 底层网络可用时才累计失败，恢复清零 | 已修，48/48 通过；待真机；未做停后自动重启 | 本提交 |
| R02（Task 5/7）: 销毁先同步关闭 TUN，异步写库 | 已修，48/48 通过 | c6dfbb4 |
| R05（Task 5）: 禁用不兼容的 always-on | 已修，48/48 通过 | 215a842 |
| R06（Task 4/5）: 阻塞 TUN 与描述符关闭 | 已修，48/48 通过；待真机 | 9a21a00 |
| R15（Task 6）: 持久保存曾经运行的守护意图 | 已修，48/48 通过 | 本提交 |
| 测试补全 E：R01/R02/R05/R15 与 UT-VP-5-03 同步 | 完成（新增 10 项，58/58） | 本提交 |

- 下一步：本轮 E 部分 JVM 测试补全完成，保留 chan/tests-vpn-guard 等协调者合并，不 push。2026-10-02 验证：./gradlew :engine-vpn:testDebugUnitTest :guard:testDebugUnitTest 为 58/58、23/23；bash scripts/check-merge.sh engine-vpn 与 guard 全部通过；./gradlew test 264/264（失败/错误/跳过均 0），git diff --check 通过。只写 engine-vpn/src/test、guard/src/test 与 PROGRESS 对应两节，生产代码及依赖只读；未新增生产缺陷。R06 及以下设备事项待真机。
- 已知问题：
  - R01 离线计数已补 R01-03：未知/离线连续失败不关闭；两次在线失败→离线多次失败→恢复后重新累计三次；重复可用通知不清零。R01-01 通过真实本地 DoH HTTP 500 与 UDP 超时连续三次验证关闭先于 STOPPED 写库、首次 UPSTREAM_UNAVAILABLE 文案和 stopService；R01-02 覆盖 DoH/UDP 的 NOERROR、SERVFAIL、NXDOMAIN 都清零；R01-04 验证缓存无传输回调、不清除失败。服务层网络 capability 组合、多网络集合、初始回调/登记失败不在本次 Boolean 控制器用例内，原设备验证事项仍保留。
  - R01 离线补修自动重启未实现（按本轮指令允许的保守方案）：onUpstreamTransport→closeTun 将控制器永久 closed，stop→stopService→onDestroy 注销网络回调并取消 scope，batcher 也已关闭；服务停止后没有可接收恢复事件的存活观察者。安全重启须另行设计跨服务生命周期的观察与一次性重启标记，并处理 GlobalState.enabled=false、用户关闭、onRevoke 与恢复同时发生的竞争；本轮不保留已销毁服务的回调、不重新开启已关闭控制器、不增加持续存活组件。仅离线导致的 DNS 失败现在不会停 VPN，网络恢复继续使用原 TUN；真实在线上游故障仍退出，需要用户重新开启。
  - R01 离线补修待真机：飞行模式/电梯断网/网络切换及 Wi-Fi 与蜂窝并存时，TUN 保留、恢复后解析与保护继续、上游确实连续不可用仍恢复直连；未执行设备验证。网络状态只由服务的非 VPN 网络回调传入纯 Boolean，控制器不引用 Android 网络类型，未知保守视为不可用。使用独立底层网络回调保留已有默认网络私人 DNS 检测语义，按 [Android NetworkCallback 文档](https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback) 使用 onCapabilitiesChanged 参数而不在回调内同步查询；回调自动提供初始状态，以网络集合避免一个网络丢失覆盖另一个的可用状态。首次 STOPPED 原因原本已正确保存，无需改动文案或停止路径。
  - R15 已补 R15-01～03：反射调用私有生产 SettingsChecker，使用真实 Room 状态及 SharedPreferences；覆盖两引擎 RUNNING→STOPPED/DEGRADED 后仍 wanted、重建 checker 从已提交标记恢复并实际触发 ServiceWatchdog 提醒、空/NOT_SETUP 从未开启不提醒、历史只增不删、两引擎互不影响、已有 STOPPED/DEGRADED 首次迁移。反射限于测试，不新增生产访问器；Robolectric 重建 checker 不等于真实 :vpn 进程重启。短暂 RUNNING 持续订阅、跨进程/磁盘失败与真实关闭后通知仍待设备或后续扩展验证。
  - 历史生产修复范围：只改 engine-vpn 生产代码/PLAN 与本节；当时所有测试（含暂存）、data/core-rules/app/依赖版本/模块列表零改动。R07 按指令未修，需要真实转发路径，仍由用户另行处理。本轮测试授权独立覆盖上述历史限制。
  - R06 待补测试/待真机：测试模块补设备用例：空闲 TUN 无空转、空闲阻塞读时 stop/onRevoke/onDestroy/重建能解除读等待且循环结束，反复启停无线程/描述符残留；JVM 现有 UT-VP-4-07 与退出路径通过，只能验证假阻塞流，不能验证 Android TUN。Builder 已 setBlocking(true)，AutoCloseInputStream/AutoCloseOutputStream 共享同一个 PFD，close 幂等关闭 PFD 后关闭流；静态核对 Android AutoClose→ParcelFileDescriptor.close→IoUtils.close→IoBridge.closeAndSignalBlockedThreads 的唤醒路径（[AOSP PFD](https://android.googlesource.com/platform/frameworks/base/+/5301928/core/java/android/os/ParcelFileDescriptor.java)、[AOSP IoUtils](https://android.googlesource.com/platform/prebuilts/fullsdk/sources/+/refs/heads/androidx-constraintlayout-release/android-35/libcore/io/IoUtils.java)、[AOSP IoBridge](https://android.googlesource.com/platform/prebuilts/fullsdk/sources/+/refs/heads/androidx-media-release/android-34/libcore/io/IoBridge.java)）；未运行真机，不能宣称实际解除读等待已验证。
  - R05 已补 R05-01：按 XML namespace 解析 engine-vpn 源 Manifest，精确定位 SentinelVpnService 自身的 SUPPORTS_ALWAYS_ON meta-data 并断言 value=false（缺失、重复或放在其他服务均失败）。本轮未把源文件断言描述为真机验证；系统设置隐藏 always-on/锁定入口及排除应用直连待真机。
  - R02 已补 R02-01：真实 Android 主 Looper 上执行 SentinelVpnService.onDestroy；Main 协程 onPrivateDns 持有 lifecycle、状态 DAO 的 DEGRADED 写入通过 CompletableDeferred 挂起时，销毁返回且 gate 未释放，TUN 同步关闭、PacketLoop Job 已不活跃并最终 join 完成，随后异步写 STOPPED。EOF 可先于 cancel 完成 Job，因此断言停止/完成行为而非要求终态必须 Cancelled。R02-02 覆盖重复 closeTun/revoke/stop 幂等关闭、首次 UPSTREAM_UNAVAILABLE 原因保留。启动/重建并发和异步写库失败属于原记录扩展项，本轮未验证；实际 ANR/网络恢复待真机。
  - 测试基线与同步修正：本轮基线 engine-vpn 47/48，guard 18/18；UT-VP-5-03 在 runCurrent 后真实 IO 尚未加载新 matcher，查询仍被旧规则拦截。测试夹具保存现有 DecisionSource，测试在有界 IO 等待新规则实际可见后执行原有 DNS 应答和 TUN 不重建断言；未改生产或放宽断言。所有业务时间继续用注入 Clock，无 Thread.sleep、跳过或恒真断言；网络超时/挂死保护为有界运行限制。
  - 基线首次 :engine-vpn:testDebugUnitTest 为 47/48：UT-VP-5-03 第 34 行应收到上游地址 1.2.3.4/TTL 120，却收到旧规则拦截地址 0.0.0.0/TTL 60；未改源码的 --rerun-tasks 重跑 48/48。loadRules 在真实 Dispatchers.IO 上运行，runCurrent 不等待真实 IO，存在既有调度竞争；本次不扩大范围修复，测试模块需补确定性热更新等待。R01 修改后 48/48，测试/断言未动。
  - 历史记录：原启动命令含损坏的绝对路径，按目标模块执行 :engine-vpn:assembleDebug；worktree 无 SDK 配置，通过 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools 运行。暂存 WireFixtures.kt 的 EffectiveConfig 导入包为 policy，冻结实现实际在 db，仅修正 import，未改断言。

- Task 4 接线说明：暂存 TunFakes 同时使用 Task 5 的 TunFactory/TunHandle，因此提前落成这两个既定接口于 service/TunFactory.kt；未改测试。

- Task 5 构建补充：测试夹具直接使用 Room，按暂存 README 添加 testImplementation(libs.room.runtime)，版本目录未改。

- Task 5 测试最小修正：EventBatcherTest 缺 ExperimentalCoroutinesApi import；VpnControllerTest 缺 runCurrent 扩展 import，分别补充，不改断言。EventBatcher 增加可选 scope/Clock 与 close，默认兼容既定构造，服务传入自身作用域和 Clock，避免定时任务泄漏。
- 计时说明（Task 5/7，MT-VP-01）：data 的 AppConfigRepository.observeExcluded 内含每 60s 的 ticks；暂存测试在 runTest 里等待真实 IO 时，调度器会不断执行这些 ticks，使虚拟时间飞快前进，导致 UT-VP-5-04/5-07、MT-VP-01 失败（重试风暴窗口、奖励窗口、暂停到期均按虚拟 Clock 计算）。控制器对 observeExcluded 加 flowOn(Dispatchers.Unconfined)，让 ticks 定时器走真实时间、下游 2 秒防抖仍在注入的 scope 上；生产行为不变。data 未改。
- 接手说明：由 Claude 接手（Codex 额度耗尽）；Task 5 的 Codex 未提交半成品已核对并完成，worktree 以 local.properties（gitignore）指向 SDK。
- Task 7 测试最小修正：VpnFailSafeTest、VpnModuleTest 缺 runCurrent 扩展 import，已补，不改断言。

## [engine-system] 状态: 进行中 | 负责方: claude | 关卡: G5

| Task | 状态 | 最后提交 |
|---|---|---|
| Task 1: 配置档案 | 完成 | 24fe2a1 |
| Task 2: Shizuku 网关 | 完成 | 669bbb3 |
| Task 3: 操作执行与撤销 | 完成 | 9d7ae3c |
| Task 4: 按 App 权限同步 | 完成 | 077f268 |
| Task 5: 巡检与状态上报 | 完成 | (见 git log) |
| Task 6: 崩溃日志采集 | 完成 | (见 git log) |
| Task 7: 接线 | 完成 | (见 git log) |
| R03: AppOps 档案验证 | 已修（6 项夹具冲突已由 D 修复） | a6d68af |
| R04: 复核失败后的恢复 | 已修（现有测试 28/28） | 7e86218 |
| R17: 动态 AppOps 漂移恢复 | 已修（本轮 3 项新增测试通过） | 5fc59da |
| 测试 Task D: 验证声明夹具与边界 | 完成（模块 34/34） | 033d6ef |
| 测试 Task R17: 动态漂移与首次恢复值 | 完成（AppOpsSyncTest 11/11） | a3e449b |
| 测试 Task C/RR-06: 生产档案体检 | 完成（ProfileHealthTest 2/2） | 本提交 |

- 下一步：本轮 D/R17/C 测试补全全部完成，chan/fix-appops 已满足用户指定的三项合并前检查，可以合并 main；本轮不自行合并、不 push。实机核实、DI-31/DI-32 仍等用户，因此完整 G5 保持进行中，不把本次 JVM 验证视为真机验收。
- 测试补全 Task D（2026-10-02）：修改前实跑 29 项，23 通过、6 失败，恰为用户列出的夹具冲突；已只给 UT-SY-4-01～04、MT-SY-01/03 的合成档案声明所需 verifiedAppOps，保留全部原断言。新增 UT-SY-4-05/06：null/空集合在 READY、离线、再次 READY 时无命令、状态/配置不变、pending 保留；UT-SY-4-07：仅声明 READ_CLIPBOARD 时只探测/执行该项、其余两项保持 pending；UT-SY-4-08：历史成功日志也不能绕过 null/空集合，开启及关闭开关均不探测或恢复；UT-SY-1-05：旧 JSON 默认空集合。测试与共享构造适配同步回 pending/engine-system。运行 ./gradlew :engine-system:testDebugUnitTest -q：34 通过、0 失败、0 错误、0 跳过。
- 已知问题（本轮 Task D）：未暴露新的生产缺陷；下文 R03 的 6 项失败表为历史记录，本轮已消除。docs/TEST_MODULE_REQUESTS.md 当前没有 D 节，按本次用户明确列出的 D 范围执行；生产代码和档案只读。
- 测试补全 Task R17（2026-10-02）：新增 R17_01，三个动态 AppOps 在稳定状态只 probe，不新增日志；连续两次系统漂移后断言 probe→再次 probe→set→复核的完整命令顺序、目标模式、pending=0，全部 9 条日志仍保留第一次 allow/foreground 值，关闭开关恢复完整初始快照。R17_02 覆盖 probe 失败/UNKNOWN 与离线不重执行、pending 保留，重连且 probe 恢复后重执行仍使用首次基线。R17_03 使用 TemporaryFolder 持久撤销文件，重建执行器后漂移重执行、再次重建后 undoAll 恢复首次 foreground；重复 undoAll 不发命令，下一轮开启采用新 default 基线。固定 Clock，无 sleep；同步回 pending。运行 AppOpsSyncTest：11 通过、0 失败、0 错误、0 跳过。
- 已知问题（本轮 Task R17）：未暴露生产缺陷。新增测试验证同步复核路径，未执行真机测试；历史 R17 待补列表中的单项撤销、重执行复核失败等扩展路径不在本次明确任务范围内，未宣称全部覆盖。
- 测试补全 Task C/RR-06（2026-10-02）：新增 testing/rule-regression 的 ProfileHealthTest，直接枚举生产 assets/profiles/*.json，目录/JSON 文件/操作列表为空都会失败。RR_06a_to_e 合并验证 PLAN 的可解析、无重复 id、非 WIZARD 命令完整和正则可编译、套路覆盖 {1,2,3,4,5,6,7,8,10,21}、卸载可选及 WIZARD intent；RR_06f 用真实 OpExecutor、固定 Clock、内存 Room 和记录命令的假 Shell，断言当前全部 verified=false/verifiedAppOps 为空、每项返回 NotVerified、无 Shell 命令、无操作日志和 SYSTEM_OP 事件。当前 1 份生产档案、18 项操作；没有新增或修改依赖。
- 最终验证（2026-10-02，本轮）：./gradlew :engine-system:testDebugUnitTest 37/37；bash scripts/check-merge.sh engine-system 全部通过；./gradlew test 全量 259/259（core-rules 27、data 46、a11y 59、notify 14、system 37、vpn 48、guard 18、rule-regression 10，其中 RR-06 2）。三项命令均退出 0；JUnit XML 失败/错误/跳过均 0。D/R17 改动同步到 pending/engine-system，原六项断言保持不变；git diff --check 通过，仅修改授权路径，无生产代码/档案/冻结项变动。
- 已知问题（本轮最终）：没有新暴露的生产缺陷，没有本次指定范围内未完成项。当前 assets 全为 WIZARD，因此 RR-06b 的非 WIZARD 命令检查分支要待真实档案含命令项才有实际样本；未伪造生产数据来制造覆盖。实机测试按 HANDOFF 不自行执行，仍等用户；下文 RR-06 缺失、R03 六项失败及 R17 待补为历史接力记录，已由上面的本轮结果更新。
- 已知问题：RR-06 的 ProfileHealthTest 在 testing/ 中不存在，本通道未自写；Task 7 用 CurrentProfile 持有者提供「当前档案 ColorOsProfile?」（Koin 不支持可空绑定），ROM 版本经反射读 ro.build.version.oplusrom，读不到则无档案（所有操作不执行）；UT-SY-6 两项通过，但 dropbox_sample.txt 为合成样本非实机采集，待实机核实后替换；DropboxCrashWorker 首次运行以当前时间为 lastChecked 基线（不回放历史崩溃）；UT-SY-5 四项通过（DriftInspector 另暴露 driftedCount 供上报，reapply 在非 READY 时返回空 map）；暂存 TestSupport 缺少 DriftInspector 的跨包 import，补足 import 以编译，断言不变；UT-SY-4 四项通过；恢复使用原 AppOps 模式，离线不发命令并保留待同步意图；backgroundPopupOp 为空时不推断 OEM 操作名；UT-SY-3 七项及 FakeDevice 自检通过；TestSupport 的混合 arrayOf 显式声明 <Any> 以消除 Kotlin 2.4 编译错误；新增本模块 AtomicFile 撤销定义持久化，data 保持只读；TestSupport 仅适配冻结 data 的四个仓库构造参数，未改断言；未来 appSync/inspector 辅助方法暂缓导入；测试增加版本目录已有 libs.room.runtime 引用；UT-SY-2 四项通过；AIDL 已启用；FakeShizukuApi 从暂存 TestSupport 原样提取以避免引用尚未实施的 Task 3–5 类型；Shizuku Provider/绑定依据官方 API 文档及已安装 13.1.5 签名；Task 1 UT-SY-1 四项已通过；新增已冻结版本的 libs.serialization.json 引用；coloros-unverified.json 使用不可匹配的占位 ROM 前缀与通用设置入口，18 项全部 WIZARD/verified=false，无臆造设备键或包名；提供的基线任务路径无效，改用 :engine-system:assembleDebug；worktree 无 local.properties，使用已安装 SDK 的 ANDROID_HOME 环境变量，不写越界配置。实机核实报告不存在，全部档案操作 verified=false。

- 修复复核 R03（2026-10-02，本轮特殊裁定）：报告属实，已增加 ColorOsProfile.verifiedAppOps（默认空集合，旧 JSON 兼容）；仅从当前档案明确声明的已核实名称生成 ProfileOp，verified 来自该集合。SYSTEM_ALERT_WINDOW、READ_CLIPBOARD、backgroundPopupOp 均受门控，档案 null/集合为空时不发任何 AppOps 命令（含日志恢复）；用户配置不变，未核实意图仍计入 pending，已有成功日志也不能消除未核实 pending。关闭开关后的未核实历史恢复保留为 pending。未修改任何档案验证标记、测试或暂存测试。修改前模块基线成功；修改后 XML 实计 29 项：23 通过、6 预期失败、0 跳过，其他测试没有失败。
- 已知问题（R03，待补测试）：测试模块需更新下表的无验证声明执行假设，并补旧 JSON 缺 verifiedAppOps 默认为空、profile=null/空集合（含历史日志）不发命令、仅执行集合中核实的三个 AppOps、开启/关闭及 READY/离线时 pending 保留。当前档案没有任何已核实 AppOps，真机 AppOpsSync 应什么也不执行。待真机：DI-31/DI-32 和未核实意图上报，本轮未执行设备操作。
- 已知问题（R03，预期失败断言）：以下测试均创建未声明 verifiedAppOps 的档案，却假设会执行 AppOps；现按总约束返回 0/不发命令。这是用户已裁定保留的断言冲突，不修改或跳过测试。表中行号为 XML 堆栈的实际断言位置（不是 Gradle 摘要的协程方法起始行）。

| 测试类与方法 | 实际失败断言 | 原因 |
|---|---|---|
| AppOpsSyncTest.UT_SY_4_01_overlay_background_and_clipboard_commands_are_logged | AppOpsSyncTest.kt:25，assertTrue(sync.syncOnce() > 0) | 未核实，实际 syncOnce()=0 |
| AppOpsSyncTest.UT_SY_4_02_turning_off_restores_each_original_mode | AppOpsSyncTest.kt:54，assertTrue(device.commands.contains("appops set $pkg SYSTEM_ALERT_WINDOW allow")) | 初次未执行，无 allow 恢复命令；设备快照相等的前置断言通过 |
| AppOpsSyncTest.UT_SY_4_03_offline_queues_then_ready_clears_pending | AppOpsSyncTest.kt:73，assertTrue(sync.syncOnce() > 0) | READY 不代表已核实，实际仍为 0，意图保留 pending |
| AppOpsSyncTest.UT_SY_4_04_missing_background_op_only_changes_overlay | AppOpsSyncTest.kt:84，assertEquals(listOf("appops set $pkg SYSTEM_ALERT_WINDOW deny"), device.commands.filter { it.startsWith("appops set") }) | 预期一个 deny 命令，实际空列表 |
| SystemModuleTest.MT_SY_01_purify_then_undo_restores_complete_device_snapshot | SystemModuleTest.kt:56，assertTrue(sync.syncOnce() > 0) | 静态已核实操作的前置断言通过，动态 AppOps 未核实，返回 0 |
| SystemModuleTest.MT_SY_03_disconnect_preserves_applied_and_reconnect_runs_pending | SystemModuleTest.kt:116，assertTrue(sync.syncOnce() > 0) | 重连不授权未核实 AppOps，实际仍为 0 |
- 修复复核 R04（2026-10-02）：报告属实，已仅修改 OpExecutor。沿用 AtomicFile，在修改前保存操作定义、before 和时间到 system-undo.json.recovery；区分未改变、可能已改变、已确认改变。复核失败时，只有成功 probe 与 before 完全一致才认定未改变；否则尽力回滚。回滚成功标 undone；失败保留恢复记录，undo/undoAll 不以 success 决定恢复资格；未完成恢复时禁止覆盖同项 before。命令/复核异常、取消、日志保存失败也尝试恢复；日志尚未落库的记录由 undoAll 恢复。保持原失败日志 success=false 和 Failed 结果。验证：现有 engine-system 全部 28/28、OpExecutorTest 7/7 通过，check-merge.sh engine-system 全部通过；没有新增/修改/删除测试文件，data 保持只读。
- 已知问题（R04，待补测试）：现有断言只验证兼容性，没有覆盖新增恢复路径。测试模块需补修改成功后 probe 失败/异常且回滚成功、回滚失败后 undo/undoAll 重试（包括重建执行器读取恢复文件）、非零命令返回但实际已改变、命令取消、日志落库前中断/写入失败、旧撤销文件兼容、已 undone 的恢复记录不重复执行、同项多次修改保持撤销顺序。历史 success=false 且无恢复记录的日志无法判断是否曾修改，保守维持不可恢复，不自动臆测迁移。待真机：Shizuku 修改后断开、重新激活后恢复及重启恢复；本次未执行设备操作。
- 修复复核 R17（2026-10-02）：报告属实，已选择同步时 probe 复核方案。AppOpsSync 不再用成功日志及相同命令跳过已执行项；只遍历 R03 允许生成的已核实操作，有历史成功记录时经 OpExecutor.status：APPLIED 只 probe；NOT_APPLIED 才交由 apply 再探测后执行；UNKNOWN 不执行、保留 pending。Shizuku 离线不探测，所有待复核意图暂计 pending。OpExecutor 在同项动态 appop:* 重执行时，从最早尚未撤销的成功日志取得首次恢复基线，用于新日志、持久恢复记录及失败回滚，防止漂移值覆盖首次模式；关闭开关和 undoAll 仍恢复首次模式，已撤销的历史轮次不参与新基线。沿用 R04 待恢复记录阻止覆盖规则，不改 DriftInspector 的档案巡检路径。修改后模块测试 29 项：23 通过、6 项与 R03 同名同断言的预期失败、0 跳过，没有新增失败；OpExecutorTest 7/7 通过。
- 已知问题（R17，待补测试）：测试模块需补明确验证的动态 AppOps：成功日志存在但实际状态正常时只 probe、不重执行；漂移时只在确认后重执行；probe UNKNOWN/失败、profile=null/空集合/未核实/离线不重执行且 pending 保留；首次 foreground/allow 后漂移为不同模式，经多次重执行、关闭开关、单项撤销或 undoAll 均还原首次模式；执行器重建后基线保留；已全部撤销后再开启以新现场值建立基线；漂移重执行复核失败沿用首次恢复记录，不破坏 R04 恢复资格。当前实现只在既有同步触发时复核动态项，未增加每日动态巡检（采用用户允许的同步复核方案）。待真机：系统恢复 AppOps 后再次同步、Shizuku 断开/重连及首次模式恢复。本轮未执行设备操作；当前生产档案没有任何已核实 AppOps，因此同步不执行。
- 前轮最终验证（2026-10-02，历史，早于本轮 R03/R17 修复）：使用 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools，依次运行 `./gradlew :engine-system:testDebugUnitTest`、`bash scripts/check-merge.sh engine-system`、`./gradlew test`，全部成功。模块 28 项，失败/跳过均为 0；git diff --check 通过；对比修复前提交，测试（含 testing 暂存测试）、冻结目录和其他模块均无改动，PROGRESS 仅 engine-system 节变化。R03/R17 未修原因、R04 待补测试和待真机均已登记；本轮不等于 G5 真机关卡完成。
- 本轮最终验证（2026-10-02，R03/R17 修复后）：已运行 `./gradlew :engine-system:testDebugUnitTest`（29 项，23 通过、上表 6 项预期失败、0 跳过）、`bash scripts/check-merge.sh engine-system`（第 1～5 项静态检查通过，仅测试项因同样 6 项失败返回 1）、`./gradlew test`（因同样 6 项失败返回 1）。为完成其他模块检查，另运行 `./gradlew test --continue`，所有测试 XML 合计 249 项：243 通过、仅上述 6 项失败、0 跳过；core-rules 27、data 46、engine-a11y 59、engine-notify 14、engine-vpn 48、guard 18、rule-regression 8 全通过，app 没有已落地单元测试（NO-SOURCE，不算测试通过）。精确比对失败名称和断言位置，没有预期之外的失败。git diff --check 通过；相对修复前 da82eb2 仅 3 个 engine-system 生产文件及本节变化，测试/暂存测试、档案、冻结目录和其他模块均未修改。R03/R17 新路径没有新增测试覆盖，待测试模块补测；待真机，不能据此宣称 G5 完成。

## [engine-notify] 状态: 已合并 | 负责方: claude | 关卡: G6

| Task | 状态 | 最后提交 |
|---|---|---|
| Task 1: 判定与内置规则 | 完成 | 404115d |
| Task 2: 划除学习 | 完成 | f5f8a4b |
| Task 3: 编排器 NotifyEngine | 完成 | 25a23ca |
| Task 4: 服务接线 | 完成 | b80bc69 |

- 下一步：Task 1–4 已逐项迁入测试、确认缺少实现时失败、实现并通过；模块 14/14 测试、assembleDebug 已通过。claude 接手复核：14/14 测试重跑通过、./gradlew test 与 check-merge.sh engine-notify 全部通过，报告见 testing/reports/G6-2026-10-02.md；DI-07、DI-41 待真机。
- 已知问题：
  - 暂存测试仅修本模块副本的编译问题，未改断言内容或暂存源：NotifyFakes.kt 的 EffectiveConfig 导入从 policy 改为冻结实现所在的 db；NotifyEngineTest.kt 第 59 行为 listOf 补 Pair<Rule, RuleOrigin> 类型，解决 TYPE_INFERENCE_ONLY_INPUT_TYPES_ERROR；ListenerMappingTest.kt 用公开 StatusBarNotification 构造器补 score=0、去掉未公开构造器的 overrideGroupKey 参数，并把未公开 UserHandle.of(0) 换成 getUserHandleForUid(10_001)（仍为用户 0）。
  - 共享测试辅助文件同时依赖 Task 2/3 接口，因此 Task 1 先声明 PLAN 指定的 LearnerStore / NotifyState，行为实现仍按 Task 顺序。Task 2 在本模块 build.gradle.kts 添加已有 libs.serialization.json 引用用于 JSON 持久化，未新增或升级版本。
  - MT-NT-01 的事件写入、MT-NT-02 的接受/重建步骤由测试调用假邻居，未覆盖真实 listener/receiver 接线；按用户要求未另加用例，实际接线已实施，真实系统行为留给 DI-07、DI-41（等用户、待真机）。
  - 实机核实报告尚不存在，保留 PLAN 六个内置包名，等用户真机核对。学习通知已声明 POST_NOTIFICATIONS；Android 13+ 运行时授权需由 app 引导完成并在真机核验。
  - 用户给出的基线命令含另一项目的绝对路径及拼写错误，按目标执行 :engine-notify:assembleDebug。SDK 以 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools 指定，未写 local.properties 或修改冻结配置。

## [engine-a11y] 状态: 已合并 | 负责方: claude | 关卡: G4

| Task | 状态 | 最后提交 |
|---|---|---|
| Task 1: 前台跟踪与启动识别 | 完成 | f9283a6 |
| Task 2: 规则点击（开屏 / 弹窗 / 自动续费） | 完成 | ca57f7e |
| Task 3: 学习模式候选规则 | 完成 | a2aeef8 |
| Task 4: 误伤探测信号 | 完成 | 65ebc76 |
| Task 5: 激励视频处理与音量保护 | 完成 | 15aa811 |
| Task 6: 跳转回退 | 完成 | 1948c8a |
| Task 7: 编排器 A11yBrain | 完成 | 09ab19b |
| Task 8: 服务接线、悬浮提示与通知动作 | 完成 | 0857679, 0b6093e |
| R08: 回调内提取事件数据 | 已修 | 37b3cd5 |
| R09: 悬浮窗异常与退出保护 | 已修 | 052f3a7 |
| R10: 中断来源切断直接跳转 | 已修 | a68dff6 |
| R11: 删除旧坐标后备点击 | 已修 | 56b1f0f |
| R12: 撤销各步独立容错 | 已修 | 4638eea |
| 测试 R08: 回归补全 | 完成（2/2） | f39145c |
| 测试 R09: 回归补全 | 完成（4/4） | ae67e1e |
| 测试 R10: 回归补全 | 完成（3/3） | 1169469 |
| 测试 R11: 回归补全 | 完成（3/3） | 8d98dd4 |
| 测试 R12: 回归补全 | 完成（5/5） | ccf9edf |

- 下一步：本任务 R08～R12 与 MT-AY-03 执行器缺口已补齐，保留 chan/tests-a11y-system 等待合并，不 push。G4 真机部分未执行，通道保持进行中：DI-05、DI-21～25 待真机，录制真实快照后补 RR-02/RR-03。
- 已知问题：
  - 暂存测试对 `EffectiveConfig` 的包名假设（`com.sentinel.data.policy`）与 data 实际不符，实际在 `com.sentinel.data.db`。编译性修正：所有拷入的测试文件把 import 改为 `com.sentinel.data.db.EffectiveConfig`（断言未改）。
  - 暂存 TestSupport.kt 引用 Task 5/7 才存在的类型（VolumePort、KeyValueStore、A11yState）。Task 1–4 期间拷入的是截去 FakeVolume/MemoryStore/FakeA11yState 的版本，Task 5/7 补回，最终版与暂存版仅有上述 import 差异。
  - 设计取舍：激励关闭点击后保持静音至离开 App 或 3 秒收尾 tick；自动续费提醒每包 10 分钟去重；学习模式「窗口出现时间」按 Activity 变化/弹窗窗口记，内容变化不刷新。
  - MT-AY-03 执行器缺口已由 R12_MT_AY_03 补齐（见下述 R12 测试补全），暂存副本未改。
  - R08 测试补全（2026-10-02）：新增 2 项全部通过。真实回调先取 source、包名和 Activity；协程排队期间替换事件仍点击原页面节点并记录原包/规则；点击事件 recycle、source/文字替换后仍按原文字开奖励窗口，排队后不再访问事件。ServiceTestData 通过已有运行时 Room 建内存库；因可写边界不含 build.gradle，仅建库/SQL 接口使用反射，不改依赖。Android 11/12 真正系统事件回收仍待真机。
  - R09 测试补全（2026-10-02）：新增 4 项全部通过。showUndo/showRewardedAsk 的主线程 addView 抛 BadTokenException 被捕获，清理局部 current 和移除视图；失败后重试；close 前排队及 close 后新展示被拒绝；真实服务 onUnbind 关闭提示。窗口 token 撤销及 ColorOS 服务断开仍待真机。
  - R10 测试补全（2026-10-02）：新增 3 项全部通过。A→Home→B 不产生 Transition，B 的 fromLauncher/startedAt/启动计数重记；直接 A→B 仍产生 Transition 并回退；Home→A 重启计数与 copy 中断标记。仍保留忽略窗口一律打断的保守处理：输入法消失后同包继续使用时，中断标记保留至下次启动，可能漏拦一次跨包跳转。SystemUI/IME 中转及桌面反复启动的冷启动信号未在本轮新增测试中单独验证；真实事件顺序仍待真机。
  - R11 测试补全（2026-10-02）：新增 3 项全部通过。旧节点及祖先 ACTION_CLICK 均失败且当前窗口已换包时，dispatchGesture 调用为零；正常节点与祖先点击保持有效。按 R11 要求删除坐标后备，无法 ACTION_CLICK 的节点不再自动点击；敏感 App 的实机切换仍待真机。
  - R12 测试补全（2026-10-02）：新增 5 项全部通过。通过 SQLite trigger 注入例外/信号写入故障，断言目标恢复和其余写入仍完成；目标启动失败不影响例外/USER_UNDO；临时例外在恢复前生效、超过 TTL/重新预取 false 后仍阻止回退，并按源/目标隔离、仅本进程有效；R12_MT_AY_03 经真实 ActionExecutor→悬浮撤销按钮→UndoJump 验证例外落库、USER_UNDO（ruleId=null）、目标启动及再次跳转不回退。后台恢复目标能力仍待真机。
- 测试补全最终验证（2026-10-02）：新增 17/17，通过 `./gradlew :engine-a11y:testDebugUnitTest :engine-system:testDebugUnitTest`（engine-a11y 76/76，engine-system 35/35）；两个模块的 `bash scripts/check-merge.sh` 全部通过；`./gradlew test` 全项目 272/272（Android debug 245 + core-rules 27），失败/错误/跳过均 0。新增测试未暴露生产缺陷；本次授权项无未完成。只新增测试及更新本节，生产代码、已有测试、依赖版本未改；测试用临时文件由 TemporaryFolder 清理。

## [guard] 状态: 已合并 | 负责方: claude | 关卡: G7

| Task | 状态 | 最后提交 |
|---|---|---|
| Task 1: 决策策略 | 完成（UT-GD-1-01～10 通过） | feat(guard): decision policy |
| Task 2: 运行时执行、通知与观察期评估 | 完成（UT-GD-2-01～06、MT-GD-01～02 通过） | feat(guard): runtime runner, notifications and observation check |
| R14: 按本轮起点评估观察期 | 已修 | 本提交 |
| 测试补全 E：R14 本轮信号与时间边界 | 完成（新增 5 项，23/23） | 本提交 |

- 下一步：本轮 E 部分 R14 测试补全完成，保留 chan/tests-vpn-guard 等协调者合并，不 push。2026-10-02 验证：./gradlew :engine-vpn:testDebugUnitTest :guard:testDebugUnitTest 为 58/58、23/23；bash scripts/check-merge.sh engine-vpn 与 guard 全部通过；./gradlew test 264/264（失败/错误/跳过均 0），git diff --check 通过。未发现新增生产缺陷，生产代码只读。G7 仍为进行中：暂存 MT-CT-01～04 的接线修复不在本轮授权的 E 部分范围，历史报告见 testing/reports/G7-2026-10-02.md；后续测试模块应按 docs/TEST_MODULE_REQUESTS.md B 部分已给出的裁定修正夹具并执行契约测试，再标完成。
- 已知问题：(1) RETRY_STORM 无 ruleId 时 PLAN 无分支，实现为 NoRuleFound；(2) 通知依赖 app 申请 POST_NOTIFICATIONS；(3) guard 暂存测试未改，仅增加 testImplementation(libs.room.runtime)；(4) testing/rule-regression/build.gradle.kts 已按协调者授权增加 room.runtime、koin.android、koin.test、work.testing 的 testImplementation（无版本改动）；(5) MT-CT 适配（编译性）：TunFactory/TunHandle 导入、buildBroadcastReceiver 替换、手工构造 VpnController、A11yState/DecisionSource 绑定，仅在补丁中，未提交；(6) MT-CT 剩余阻塞见 G7 报告第 3 节（快照预取、NotifyState、EnabledServicesChecker 不可获取；CT-A7 歧义）。
  - R14 已补 R14-01～05：真实 Room 与 ObservationWorker，可控 Clock；USER_UNDO/TEMP_ALLOW 各自只延期一次，已处理信号仍留库但第二轮计数为 0；到期前 1ms 不结束、无新信号到期结束；第一轮起点等于 firstSeenAt，起点前 1ms 排除、等于起点计入；延迟执行从实际延期时刻开始，新轮同样验证毫秒边界与新信号再延期；其他 SignalKind 不延期。新增 5/5、guard 全量 23/23 均通过，无跳过或生产改动。
  - R13 未修（用户明确排除）：保留 GuardRunner.undo 的 remove→pin 行为及暂存 UT-AP-4-02 断言；非原子中断窗口仍由用户另行处理。

## [app] 状态: 完成 | 负责方: claude | 关卡: G3(app 部分) / G8

| Task | 状态 |
|---|---|
| Task 1: 应用骨架与全局接线 | 完成 |
| Task 2: 最小首页 | 完成 |
| Task 3: 引导向导 | 完成 |
| Task 4: 完整首页、引擎日志、系统净化与撤销记录 | 完成 |
| Task 5: 应用页与应用详情 | 完成 |
| Task 6: 规则页 | 完成 |
| Task 7: 快捷开关 | 完成 |
| Task 8: 应用启动图标（三渲二盾牌之眼，自适应 + 单色主题层） | 完成（待真机看桌面效果） |

- 验证（非真机部分）：`./gradlew :app:testDebugUnitTest --rerun` 37/37 通过（UT-AP-1～7、MT-AP-01～05，含 G8 专属的 UT-AP-1-04）；全仓 337 个测试 0 失败 0 跳过；`./gradlew assembleDebug` 成功。
- 测试夹具已修复（FakeData 改用 Robolectric 内存 Room 数据库等，见 `docs/TEST_MODULE_REQUESTS.md` A 部分）。测试暴露并已修复的 app 缺陷：二级页面隐藏底部导航后没有可见的返回入口（MT-AP-03/05），已加统一的 ≥48dp 返回按钮。
- 下一步：**待真机** DI-51（首页开启后网络层拦截）、DI-52、DI-61；首个可用版本的真机验证。
- 已知问题：
  1. 向导里与 ColorOS 路径相关的图示是通用矢量图，未按 DI-21 记录的真实路径绘制（真机核实后再换）。
  2. 系统净化页在当前系统版本没有档案时显示「暂无可用方案」；engine-system 的档案全部是 verified=false，所以现在页面没有可执行项，要等实机核实报告。
  3. POST_NOTIFICATIONS（Android 13+）：已在 MainActivity 首次启动时请求一次（用户拒绝后不再反复弹）；拒绝时拦截本身不受影响，但撤销提醒、学习确认通知不会出现。**待真机**确认弹窗时机与 ColorOS 行为。

## [merge] 状态: 待办 | 负责方: claude | 关卡: —

- 职责：当某个通道状态为 `完成` 时，在 `main` 上按顺序（notify → system → a11y → vpn → guard → app）合并；每次先在该通道 worktree 里运行 `scripts/check-merge.sh <模块>`，通过后合并，再在 `main` 跑 `./gradlew assembleDebug test`，并把该通道状态改为 `已合并`。
- 下一步：等待第一个通道完成。
- 已知问题：无

## [survey] 状态: 待办 | 负责方: 用户 | 关卡: —

- 职责：M0 Task 1 实机核实。用户连接手机后运行 `docs/device-survey/scripts/survey.sh`；报告写入 `docs/device-survey/find-x7-ultra-survey.md`。
- 下一步：等用户。
- 已知问题：无

## [httpdns-baidu] 状态: 完成 | 负责方: Codex | 关卡: core-rules 测试

| Task | 状态 | 最后提交 |
|---|---|---|
| Task 1: 补百度 HTTPDNS IP | 完成 | 8e51c5a |
| Task 2: 变更登记 | 完成 | 48b2d58 |
| Task 3: 回归测试与关卡验证 | 完成 | 本提交 |

- 范围：`chan/httpdns-baidu`，仅修改 core-rules、本节、CHANGE_REQUESTS 及直接相关 testing 断言；不 push、不改依赖、不改引擎。
- 基线：`./gradlew :core-rules:test` 27/27、`./gradlew :engine-vpn:testDebugUnitTest` 58/58，失败/错误/跳过均 0。
- Task 1：按原有 IPv4 顺序新增 `180.76.76.76/32`、`180.76.76.112/32`。CatalogTest 的固定数量断言 59→61，先运行目录测试确认数量断言失败，再补 IP；保留全部已有断言。APK 原始字符串 `allstrings.txt` 第 1651581 行的百度 TurboNet `bdns.customize_http_dns_server_url_prefix` 明确为 `https://180.76.76.112/v2/0010`，第 1351065～1351067 行还有 v6 服务 URL；`.200` 仅孤立字符串，未加入。
- Task 1 验证：`./gradlew :core-rules:test` 27/27 通过，失败/错误/跳过均 0；`git diff --check` 通过。
- Task 2：新增 `docs/CHANGE_REQUESTS.md`，以 CR-2026-10-02-HTTPDNS-BAIDU 登记两个 /32、兼容性、原始字符串与源码证据、用户书面批准及 UDP/53 实际影响；`git diff --check` 通过。
- Task 3：CatalogTest 新增两个用例，验证 `.76`/`.112` 命中，`.77`/`.113`/证据不足的 `.200` 不命中，以及全列表按地址字节和前缀去重、所有 IPv4/IPv6 主机位均为 0。既有数量断言已在 Task 1 精确同步为 61。核对 rule-regression：只引用域名规则，没有 CIDR 固定数量/内容断言；engine-vpn 和 testing/unit/pending/engine-vpn 的 TunSpecTest 均动态生成完整预期路由集并做精确相等断言，不需修改，未放宽或删除断言。
- 最终验证（2026-10-02）：`./gradlew :core-rules:test` 29/29；`./gradlew :engine-vpn:testDebugUnitTest` 58/58；`bash scripts/check-merge.sh core-rules` 全部通过；测试 XML 失败/错误/跳过均 0，`git diff --check` 通过。按本次写入边界将关卡结果记录在本节，未额外创建 testing 报告或修改其他通道。
- 下一步：本通道三项已完成，保留 worktree 和分支，交合并通道审查；不 push。下面 UDP/53 语义差异与 SDK 回落效果仍须后续引擎/设备工作确认。
- 已知问题：背景中“普通 UDP/53 不受影响”与当前源码不符。PacketLoop 的 DNS 分支还要求目的地址属于 TunSpec.DNS_SERVERS（`10.111.0.2`、`fd11:1::2`）；TunSpec 为每个 HTTPDNS CIDR 添加路由，因此新增 IP 的 TCP（含 HTTPS）会收到 RST，UDP（含直连该 IP 的 53 端口）会收到 ICMP 不可达。虚拟 DNS 的 UDP/53 处理保持不变。未改只读 engine-vpn；SDK 是否回落系统 DNS、是否减少开屏广告仍需设备验证，不能由目录测试证明。

## [apk-ad-scan] 状态: 完成 | 负责方: Codex | 关卡: 自测通过

| Task | 状态 | 最后提交 |
|---|---|---|
| Task 1: 标准库 APK/strings 扫描工具 | 完成 | c98d10b |
| Task 2: 百度网盘域名包及人工清单对照 | 完成 | b67fb84 |
| Task 3: 合成样本自测 | 完成 | 本提交 |

- 基线：chan/apk-ad-scan 工作区干净；scripts/apk-ad-scan 不存在，无已有模块测试可跑；没有运行或改动 Gradle 模块。
- Task 1：scan.py 解析所有根目录 classes*.dex 的 string_ids，支持 strings 文本、三类 TAB 清单和 --format domains；内置 12 家 SDK 数据表和离线 TLD 快照。已有种子/人工确认之外的新增特征用本机 dexdump 核对了 Vlion、Octopus、美数的引用类；未核实的 Mintegral/倍孜/趣盟只列疑似，不猜测专用域。
- 验证：Python 3.9.6；Task 1 临时 smoke assertions 通过（URL/裸域名、大小写、标识符过滤、百度主域保护、后缀伪装）；真实 APK 的标准 DEX 读取成功，确认 28 个域名。scan.py --help、git diff --check 通过；正式自测在 Task 3 落地。
- Task 2：对只读 base.apk 的 42 个 DEX 运行工具，生成 docs/device-survey/ad-domain-packs/com.baidu.netdisk.md 与 com.baidu.netdisk.domains.txt；28 个确认、4 个疑似、1798 个第一方/不能拦候选。人工清单实际 13 项，全部覆盖，新增 15 项、缺少 0 项。报告记录 APK SHA-256、逐域名分类依据、首个 DEX string_id、全部对照和复现命令。
- Task 2 验证：scan.py <base.apk>、scan.py <base.apk> --format domains 均退出 0；回读确认纯清单 28 行排序去重，与 Markdown 确认章节一致，不含网盘主站或 bcebos；git diff --check 通过。首次输出因目录未创建失败，补建授权目录后重跑成功，没有保留部分结果。
- Task 3：scripts/apk-ad-scan/tests/sample-strings.txt 为手写小样本；run_tests.py 只用标准库、合成多 DEX APK（临时目录在 tests 内且自动清理），不访问真实 APK。覆盖三类分类/全部 SDK 表条目、第一方及 bcebos 保护、后缀标签边界/精确主机不扩张、URL 路径与凭据/端口/转义/大小写/IDNA、去重/排序/文本和纯域名 CLI、ULEB128 与 DEX 长度字节、损坏/缺失输入/无 DEX/无效参数、失败无部分清单及空确认集。
- Task 3 验证（2026-10-02）：python3 scripts/apk-ad-scan/tests/run_tests.py 退出 0，全部自测通过、没有跳过；git diff --check 通过。逐任务提交，仅写授权路径；PROGRESS 原有各通道内容未改；无遗留临时文件，无依赖版本或 Gradle 模块变更。
- 下一步：本临时通道三项任务已完成，保留 chan/apk-ad-scan 和 本地 worktree wt-apkscan 供协调者合并；不 push，不自动装载规则。后续按设计另做真实流量/冷启动验证与 HTTPDNS 处理，不属于本通道。
- 已知问题：静态字符串不证明实时请求或拦截效果；不扫描资源/native/动态拼接/split APK，不处理 HTTPDNS 或 pan.baidu.com 自营广告。公共 TLD 语法仍可能接受同形代码标识符（不会因此进入确认清单）；离线材料不足的三家 SDK 没有已核实专用域名。报告按本任务写入范围保存到 docs/device-survey/ad-domain-packs，不写 testing/reports。

## 公开发布（2026-10-04）
- 仓库：https://github.com/BBbinlei/sentinel-adblock（公开，GPL-3.0）；Release v0.1.0 附签名 APK；介绍页 http://binlei.site/sentinel-adblock/（Pages，源 main:/docs）。
- 签名密钥在仓库外 ~/.sentinel-release/，后续版本必须用同一密钥才能覆盖升级。

## [yangshipin-cainiao-survey] 状态: 完成 | 负责方: Codex | 关卡: 静态研究（非 DI/AC）

| Task | 状态 | 最后提交 |
|---|---|---|
| Task 1: 手机已安装央视频、菜鸟广告机制静态逆向 | 完成 | 本提交 |

- 授权：2026-10-04 用户要求研究手机上央视频与菜鸟的广告机制。写入本通道 `docs/device-survey/yangshipin-cainiao/` 及本节；生产代码、运行规则、依赖版本未改，不 push。
- 现场：ADB 已连接 OCE_AN50；央视频 3.5.3.26910 / 305030、菜鸟 8.11.923 / 475，两个包各只返回 base.apk。从手机读取原 APK，记录 SHA-256，扫描全部根 DEX 字符串并反汇编关键类。
- 发现：央视频 ZSplash→CMG fetch/show、CMG 正式广告池/配置/上报/缓存地址和 CMS 启动素材链路；菜鸟 MTOP 广告 API、穿山甲/优量汇/美数/Ubix SDK 加载适配及竞价胜负通知、预加载；关闭摇一摇桥接入口解析 isShakeClose 并清理预加载开屏数据。详细证据和推断边界见本通道 README.md。
- 验证：先运行 scripts/apk-ad-scan/tests/run_tests.py 全部通过，无跳过；关键调用回读定位、APK 指纹/版本记录、git diff --check。未改生产实现，无需 Gradle 测试。临时 APK/Manifest 删除，保留脱敏文本证据及提取脚本。
- 下一步：本次静态研究完成；实时请求、广告选择/频率、设置页与拦截兼容性未测，不宣称已验证可安全拦截。DI/AC 仍等用户，不自动装载规则。
- 已知问题：现有扫描器不识别 CMG，还会误把部分 MTOP 方法名当疑似域名；报告按字节码解释，不将扫描结果直接当阻断清单。动态热补丁/native/广告插件内部代码未完整覆盖。

### [yangshipin-cainiao-survey] Task 2：免开屏入口可行性补充（2026-10-04）

- 完成静态补充，报告为 docs/device-survey/yangshipin-cainiao/bypass-feasibility.md。重新 pull 两个 APK，SHA-256 与 Task 1 一致；保存五个菜鸟方法、四个央视频方法与关键 Manifest 组件声明，保留方法名/指令偏移。
- 菜鸟 HomePageActivity 明确 exported=true，有 home_page 深链；WelcomeActivity 内有开屏请求，是直接进首页候选，但未验证 Application/父类/热启动是否再次触发广告。isHotLaunch 的 true 分支仍请求广告，不当作免广告参数。
- 央视频 parseIntent 的 from=push_* / third_* 会设置 needHideADSplash，initSplash 的 true 分支隐藏开屏并进首页；HomeActivity 非导出，需追踪导出的 OpenActivity 能否转发所需 extra。SplashActivity 新 Intent 仅写 ImUrFather，不据此假设外部 extra 自动传递。
- 修正用户前提：仓库百度网盘报告没有证实免开屏成功，冷启动缺对照、热启动 filterad 无效。下一步优先菜鸟候选对照验证、央视频路由追踪；未执行 DI/AC 或设置修改，尚无实机通过的方案。仅静态材料，无生产修改或新增测试需求；临时 APK/完整转储已清理，git diff --check 通过，不 push。

## [launch-shortcuts] 状态: 完成 | 负责方: Codex | 关卡: 用户授权实现与实机验证

| Task | 状态 | 最后提交 |
|---|---|---|
| Task 1: 研究与冷/热启动对照 | 完成 | b34efbf |
| Task 2: app 入口、快捷方式及首页弹窗控制 | 完成 | 1ed126a |
| Task 3: 同签名 APK、覆盖安装与功能验证 | 完成 | 本提交 |
| Task 4: 原央视频桌面点击路径追加要求 | 完成 | 本提交 |

- 授权：用户明确要求在 app 集成并实机验证；本次覆盖旧 app 角色边界和不得自主实机步骤，仍冻结 data/core-rules/引擎及依赖，不 push/不公开发布。在附加 worktree launch-shortcuts、codex/launch-shortcuts 开发。
- 基线：先运行现有 :app:testDebugUnitTest，37 tests，失败/错误/跳过 0。
- Task 1：菜鸟 475/8.11.923 三组普通启动都有商业广告，三组直接 HomePageActivity 均进入首页，未回欢迎/广告 Activity；冷启动通过。精确央视频 OpenActivity+from=third_h5 路由按代码追踪证实可转发 from，但三组候选仍出现首页广告弹窗，因此入口不开放。后台样本单独记录，不扩张承诺。报告 docs/device-survey/yangshipin-cainiao/launch-entry-validation.md。
- 范围补充：用户要求一并处理央视频首页弹窗；将利用已有界面规则契约自动点实测关闭控件，这项依赖无障碍，与标准快捷启动分开。当前菜鸟快递列表为空，用户明确选择稍后自查物流详情。
- 下一步：完成 app 的固定目录/版本与组件校验/失效回退/桌面请求与确认分离/界面按钮测试；再签名构建、验证手机真实快捷入口与首页弹窗关闭，完成回归。
- 已知问题：测试进程曾因 locale 类路径编码报 ClassNotFound，UTF-8 环境重跑恢复执行；新快捷方式图标 fake 缺少模拟图标导致一例失败，后续已修复并重跑通过，未跳过失败。央视频候选存在商业弹窗，不标成功。

### [launch-shortcuts] Task 2 完成

- app 内固定两应用目录、精确版本/导出启用组件检查；未知 ID 拒绝；失效回退普通启动；采用目标图标的 ShortcutManager 固定快捷方式；请求与成功回调分离，取消无回调时显示未确认。详情页只有已验证菜鸟显示创建按钮，央视频待验证。
- 追加央视频首页广告关闭控制：实测 modal 层级+精确 iv_close ID 的 Page/ANYTIME 规则，用现有 UserRuleRepository/SubscriptionUpdater 接口安装、撤销；版本变化删除本规则。此项需现有无障碍与防护开启，不影响快捷启动独立性；尚待真机自动关闭验证。
- 验证：app 57/57；全项目 ./gradlew test 共 360/360，失败/错误/跳过 0；新增真实脱敏 XML fixture 确认只选关闭按钮，不选广告跳转节点。原始 37 基线保留；先跑新增用例失败再实现，组件禁用读不到与图标 fake/启动 DB 隔离失败均已修复，未跳过失败。check-merge 采用既有 CR-2026-10-02-HTTPDNS-BAIDU 的 ALLOW_FROZEN=1；本次相对起点没有修改冻结项。
- 下一步：0.1.1 / code 2 同签名构建与安装，华为桌面验证和两应用功能回归；快递详情按用户要求留待其补验。

### [launch-shortcuts] Task 3 完成，原图标追加要求待决定

- 用户实测原央视频入口仍有开屏后，明确接受“快捷入口＋弹窗自动关闭”；据已有三组商业全屏对照，开放精确版本央视频全屏开屏绕过入口，详情页明确首页弹窗仍需无障碍关闭、可能短暂显示，不把原候选弹窗失败改写为纯入口免全部广告。
- 0.1.2 / code 3 已覆盖安装，原设置/历史保留。手机原安装实际为开发签名，与发布密钥不同；签名核对后生成同签名 phone-upgrade 包用于覆盖，另生成既有 release 签名包用于交付。详见 testing/reports/launch-shortcuts-2026-10-04.md，两份最终 APK 在主目录 deliverables/launch-shortcuts/；旧 0.1.1 临时交付包已清理。
- 两个华为桌面固定快捷方式均经系统确认并在 dumpsys shortcut 中 pinned。真实桌面冷启动菜鸟进入空包裹列表，扫码页可打开（未拿真实条码识别），物流详情用户稍后自查；央视频快捷入口进入首页，直播/点播看见播放画面。无障碍日志两次 POPUP_CLOSED，首页可操作；日志未显示 ruleId，不独占归因新规则。
- 验证：最终 app 58/58，全项目 361/361，失败/错误/跳过 0；assembleRelease 和 check-merge app 成功。未改冻结模块/契约/依赖，不 push、不公开发布。
- 追加要求：用户希望点“原央视频图标”自动走快捷路径。原启动后约 .3 秒深链转接实测仍有商业全屏广告，未集成失败的自动转接。已提出同名称/同位置快捷图标替代、原图标移至另一页的桌面方案；等待用户选择，未擅自移动图标或禁用组件。
- 已知证据边界：旧冷启动没有逐次保存保护状态，后来发现菜鸟两规则自动降级停用；报告已撤回“保护完全一致”表述，补做对照不得把无广告正常入口标为新通过。原始商业广告证据保留，后台不扩大承诺。
- 下一步：根据用户对桌面方案的选择处理 Task 4；若坚持原 launcher 组件自动改路，在当前不修改目标 APK/组件的边界下无法交付已验证方案，明确保留失败证据。

- 收尾复核：13:05:48 快捷路径启动央视频后有新 POPUP_CLOSED，首页可操作；系统无障碍 Enabled/Bound 均存在，首页状态仍显示已停止，现有状态上报不一致未修复，冻结引擎未改。记录 final-bound-service.txt / latest-events.txt / final-home.txt。补充菜鸟对照正常与候选均无商业开屏，本轮只标待验证，不作为新成功证据。

### [launch-shortcuts] Task 4 进行中：真正原图标转接

- 用户坚持点真正的原图标；临时 shell IActivityController 在启动前拒绝原 SplashActivity，再启动固定 OpenActivity 深链。普通 MAIN/LAUNCHER 与真实原桌面图标都成功进入 HomeActivity，无全屏开屏；首页弹窗仍单独处理。此前“只能换图标”的结论撤回。
- 仅 app 增加 Shizuku UserService，复用现有 13.1.5 依赖版本，精确 Android API 31 / 央视频 305030 与启动组件校验；未知应用、深链、内部启动及版本失配放行，不改目标 APK/组件或冻结引擎。新功能依赖 Shizuku 服务授权，服务死亡后普通启动，手机重启后需重启 Shizuku。
- 本轮先跑已有 app 58 用例通过，再实现。初步 app 63 用例、assembleRelease 通过；补充权限/界面与服务协议验证后继续全项目测试和实机安装。当前 ADB 连接丢失，已请求恢复连接，未将离线测试当实机完成。

### [launch-shortcuts] Task 4 完成

- app 内实现固定版本/API31 原央视频图标转接；复用 Shizuku 13.1.5，不修改目标 APK/组件/数据或冻结模块。修复实机发现的 UserServiceArgs 缺少 processNameSuffix 的连接失败，追加序列化回归；am 错误即使退出0也触发回退。
- 0.1.3 / code4 最终包同证书覆盖安装 Success，原设置保留。真实原图标三组 OFF/ON 对照、各启动间隔至少70秒：OFF全有商业全屏，ON取消Splash转固定Open，无全屏开屏；首页弹窗约数秒后自动关闭，POPUP_CLOSED证据与首页截图一致。直播/点播播放正常；原图标位置保留，转接当前开启。
- app70 / 全项目373 全通过，失败/错误/跳过0；assembleRelease、check-merge app及签名核对通过。报告 testing/reports/original-icon-2026-10-04.md，最终两种签名APK在主目录deliverables/launch-shortcuts/。
- 限制：新增转接需要 Shizuku，手机重启后需重新启动 Shizuku；只验证Huawei API31和央视频305030。首页弹窗仍可短暂出现；单次后台样本不扩张承诺；菜鸟物流详情按用户要求其自行补验。前述“当前边界无法原图标转接”的推断被新的启动前实测推翻。
- 下一步：本功能完成。用户最新明确授权更新GitHub和下载链接，追加Task5发布通道；此授权覆盖先前不push/不Release/不网站发布的限制，不扩展代码范围。

## [release-0.1.3] 状态: 完成 | 负责方: Codex | 关卡: 用户授权公开发布

| Task | 状态 | 最后提交 |
|---|---|---|
| Task 5: GitHub更新、v0.1.3下载和介绍页更新 | 完成 | de648a2 |
| Task 6: 公开附件与线上页面回读验收 | 完成 | 本提交 |

- 用户2026-10-04最新明确要求从现在更新GitHub和新下载链接，覆盖本任务旧“不push/不Release/不网站发布”限制。发布正式签名包与当前开发签名安装兼容包，分别标注用途；不改既有签名密钥。
- README与Pages下载页更新版本、校验值、原图标启用方式、Shizuku与无障碍的不同用途及已验证范围；APK不提交源码仓库，作为Release附件。
- 下一步：推送main，发布v0.1.3，回读Release附件及Pages线上新链接。

### [release-0.1.3] Task 6 完成

- main已快进到launch-shortcuts并推送GitHub；v0.1.3正式Release已发布并设为latest，附件sentinel-adblock.apk、sentinel-0.1.3-phone-upgrade.apk、SHA256SUMS.txt。创建Release时GitHub拒绝短SHA，改用完整de648a2edb944f6203e4589f35b0f615a50f2f29后成功，未产生错误Release。
- 从公开下载URL重新下载两份APK，SHA-256分别9ee476ea7c3d8e0388e1d9a44913c8b9d4b1cbf42a05d4e4d5540b7744e9dcbe / ef0cd482cd8ff5bb9fbf61ac839246b9091c845ec7ce079d172e6547e9fec22f，与签名交付一致；手机已安装文件与兼容包逐字节哈希一致。
- Pages部署built，回读 http://binlei.site/sentinel-adblock/ 已含v0.1.3正式和开发签名下载链接、验证范围及新校验值；仓库latest API也返回v0.1.3。
- 发布前检查未跟踪签名密钥/APK/local.properties/私密凭据；保留其他用户未提交文件，不打包上传。临时APK、手机探针与截图副本清理，保留已脱敏验收证据。
- 下一步：公开交付完成；手机原图标转接保持开启，重启手机后按提示重新启动Shizuku。其他手机及目标应用升级需重新验证，不自动扩展支持。
