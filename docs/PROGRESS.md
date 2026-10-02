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

## [engine-vpn] 状态: 完成 | 负责方: codex | 关卡: G3

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
| R02（Task 5/7）: 销毁先同步关闭 TUN，异步写库 | 已修，48/48 通过 | c6dfbb4 |
| R05（Task 5）: 禁用不兼容的 always-on | 已修，48/48 通过 | 215a842 |
| R06（Task 4/5）: 阻塞 TUN 与描述符关闭 | 已修，48/48 通过；待真机 | 9a21a00 |
| R15（Task 6）: 持久保存曾经运行的守护意图 | 已修，48/48 通过 | 本提交 |

- 下一步：本次 R01/R02/R05/R06/R15 五项修复完成，留在 chan/fix-vpn 等合并，不 push；测试模块补下列回归用例，DI-01～03、DI-11～14、DI-51 及本次设备事项待真机。最终 :engine-vpn:testDebugUnitTest 48/48（失败/错误/跳过 0）、bash scripts/check-merge.sh engine-vpn 全部通过、./gradlew test BUILD SUCCESSFUL（249 项，失败/错误/跳过均 0）；本次报告只写本节，未越界写 testing/reports。
- 已知问题：
  - R15 待补测试：测试模块补 RUNNING→STOPPED 后仍提醒、RUNNING→DEGRADED 后仍为 wanted、重启 :vpn 后从 SharedPreferences 恢复、已有 STOPPED/DEGRADED 行的首次迁移、空状态/NOT_SETUP 从未开启不提醒、状态行删除或 NOT_SETUP 不抹去已保存历史、两个引擎标记互不影响、短暂 RUNNING 的持续订阅捕获；EnabledServicesChecker 接口未改，现有 48/48 通过。历史由 :vpn 独占本地持久保存，跨进程输入仍仅 Room；按 C4 将既有 STOPPED/DEGRADED 作为运行历史证据（保守迁移），标记只增不删，不推断用户永久关闭意图。磁盘写入失败仅日志提醒，持久性/真实关闭后通知待真机。
  - 范围确认：只改 engine-vpn 生产代码/PLAN 与本节；所有测试（含暂存）、data/core-rules/app/依赖版本/模块列表零改动。R07 按指令未修，需要真实转发路径，仍由用户另行处理。
  - R06 待补测试/待真机：测试模块补设备用例：空闲 TUN 无空转、空闲阻塞读时 stop/onRevoke/onDestroy/重建能解除读等待且循环结束，反复启停无线程/描述符残留；JVM 现有 UT-VP-4-07 与退出路径通过，只能验证假阻塞流，不能验证 Android TUN。Builder 已 setBlocking(true)，AutoCloseInputStream/AutoCloseOutputStream 共享同一个 PFD，close 幂等关闭 PFD 后关闭流；静态核对 Android AutoClose→ParcelFileDescriptor.close→IoUtils.close→IoBridge.closeAndSignalBlockedThreads 的唤醒路径（[AOSP PFD](https://android.googlesource.com/platform/frameworks/base/+/5301928/core/java/android/os/ParcelFileDescriptor.java)、[AOSP IoUtils](https://android.googlesource.com/platform/prebuilts/fullsdk/sources/+/refs/heads/androidx-constraintlayout-release/android-35/libcore/io/IoUtils.java)、[AOSP IoBridge](https://android.googlesource.com/platform/prebuilts/fullsdk/sources/+/refs/heads/androidx-media-release/android-34/libcore/io/IoBridge.java)）；未运行真机，不能宣称实际解除读等待已验证。
  - R05 待补测试：测试模块补最终合并 Manifest 中 VpnService 的 SUPPORTS_ALWAYS_ON=false；已核对源 Manifest、本模块与 app 的 Debug 合并产物均保留 false，48/48 通过。系统设置不再提供 always-on/锁定入口及排除应用保持直连待真机；本次只改 Manifest，不改向导。
  - R02 待补测试：测试模块补 Main 协程持有 lifecycle、Room 写入挂起时调用 onDestroy，断言主线程立即返回、TUN 已关且循环取消；重复销毁/撤销/stop 幂等，首次停止原因不被销毁覆盖，启动与重建挂起时销毁后不能建立新 TUN；异步状态写入失败不影响释放网络。现有 48/48 通过，未新增或修改测试；ANR 与真实服务销毁恢复网络待真机。
  - R01 待补测试：测试模块补连续 3 次 DoH/UDP 全失败触发 TUN 关闭、带原因 STOPPED、停止服务及关闭先于写库；单次失败不关闭、有效 SERVFAIL/NXDOMAIN 重置计数、缓存命中不掩盖传输失败、并发查询计数。真实网络恢复待真机。现有 UT-VP-3-03 单次失败 SERVFAIL 断言保留；解析器以独立传输结果回调通知控制器，未通过 RCODE 推断传输失败。
  - 基线首次 :engine-vpn:testDebugUnitTest 为 47/48：UT-VP-5-03 第 34 行应收到上游地址 1.2.3.4/TTL 120，却收到旧规则拦截地址 0.0.0.0/TTL 60；未改源码的 --rerun-tasks 重跑 48/48。loadRules 在真实 Dispatchers.IO 上运行，runCurrent 不等待真实 IO，存在既有调度竞争；本次不扩大范围修复，测试模块需补确定性热更新等待。R01 修改后 48/48，测试/断言未动。
  - 历史记录：原启动命令含损坏的绝对路径，按目标模块执行 :engine-vpn:assembleDebug；worktree 无 SDK 配置，通过 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools 运行。暂存 WireFixtures.kt 的 EffectiveConfig 导入包为 policy，冻结实现实际在 db，仅修正 import，未改断言。

- Task 4 接线说明：暂存 TunFakes 同时使用 Task 5 的 TunFactory/TunHandle，因此提前落成这两个既定接口于 service/TunFactory.kt；未改测试。

- Task 5 构建补充：测试夹具直接使用 Room，按暂存 README 添加 testImplementation(libs.room.runtime)，版本目录未改。

- Task 5 测试最小修正：EventBatcherTest 缺 ExperimentalCoroutinesApi import；VpnControllerTest 缺 runCurrent 扩展 import，分别补充，不改断言。EventBatcher 增加可选 scope/Clock 与 close，默认兼容既定构造，服务传入自身作用域和 Clock，避免定时任务泄漏。
- 计时说明（Task 5/7，MT-VP-01）：data 的 AppConfigRepository.observeExcluded 内含每 60s 的 ticks；暂存测试在 runTest 里等待真实 IO 时，调度器会不断执行这些 ticks，使虚拟时间飞快前进，导致 UT-VP-5-04/5-07、MT-VP-01 失败（重试风暴窗口、奖励窗口、暂停到期均按虚拟 Clock 计算）。控制器对 observeExcluded 加 flowOn(Dispatchers.Unconfined)，让 ticks 定时器走真实时间、下游 2 秒防抖仍在注入的 scope 上；生产行为不变。data 未改。
- 接手说明：由 Claude 接手（Codex 额度耗尽）；Task 5 的 Codex 未提交半成品已核对并完成，worktree 以 local.properties（gitignore）指向 SDK。
- Task 7 测试最小修正：VpnFailSafeTest、VpnModuleTest 缺 runCurrent 扩展 import，已补，不改断言。

## [engine-system] 状态: 进行中 | 负责方: codex | 关卡: G5

| Task | 状态 | 最后提交 |
|---|---|---|
| Task 1: 配置档案 | 完成 | 24fe2a1 |
| Task 2: Shizuku 网关 | 完成 | 669bbb3 |
| Task 3: 操作执行与撤销 | 完成 | 9d7ae3c |
| Task 4: 按 App 权限同步 | 完成 | 077f268 |
| Task 5: 巡检与状态上报 | 完成 | (见 git log) |
| Task 6: 崩溃日志采集 | 完成 | (见 git log) |
| Task 7: 接线 | 完成 | (见 git log) |
| R03: AppOps 档案验证 | 已修（6 项预期测试冲突） | a6d68af |
| R04: 复核失败后的恢复 | 已修（现有测试 28/28） | 7e86218 |
| R17: 动态 AppOps 漂移恢复 | 已修（新增路径待测试模块覆盖） | 本提交 |

- 下一步：R03/R17 已修；测试模块需更新下列 6 项无验证声明的旧断言并补新增路径，更新后重新验证再由用户决定合并。实机核实报告、DI-31/DI-32、RR-06 仍待补；所有档案保持 verified=false，G5 保持进行中。本分支不合并 main、不 push。
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

## [engine-a11y] 状态: 进行中 | 负责方: codex | 关卡: G4

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

- 下一步：本轮 R08～R12 已修，2026-10-02 本地验证完成：:engine-a11y:testDebugUnitTest 59/59、bash scripts/check-merge.sh engine-a11y 全部通过、./gradlew test 全量 249/249（失败/错误/跳过均 0）；等待修复分支合并与测试模块补用例，未 push。G4 真机部分未执行，通道保持进行中：DI-05、DI-21～25 待真机，录制真实快照后补 RR-02/RR-03；既有非真机关卡报告见 testing/reports/G4-*.md。
- 已知问题：
  - 暂存测试对 `EffectiveConfig` 的包名假设（`com.sentinel.data.policy`）与 data 实际不符，实际在 `com.sentinel.data.db`。编译性修正：所有拷入的测试文件把 import 改为 `com.sentinel.data.db.EffectiveConfig`（断言未改）。
  - 暂存 TestSupport.kt 引用 Task 5/7 才存在的类型（VolumePort、KeyValueStore、A11yState）。Task 1–4 期间拷入的是截去 FakeVolume/MemoryStore/FakeA11yState 的版本，Task 5/7 补回，最终版与暂存版仅有上述 import 差异。
  - 设计取舍：激励关闭点击后保持静音至离开 App 或 3 秒收尾 tick；自动续费提醒每包 10 分钟去重；学习模式「窗口出现时间」按 Activity 变化/弹窗窗口记，内容变化不刷新。
  - MT-AY-03 暂存测试未覆盖执行器写例外/USER_UNDO（见暂存 README 待确认 5）。
  - R08 待补测试：由测试模块覆盖回调返回后事件回收/复用、IO 排队时仍使用原包名、Activity 和 source 节点；本次仅把 source 读取移到回调内，不保留 AccessibilityEvent。Android 11/12 事件生命周期待真机。
  - R09 待补测试：由测试模块覆盖 showUndo/showRewardedAsk 的主线程 addView 抛 BadTokenException、部分添加失败后的移除与 current 清理、close 前后排队/新展示不执行、重复退出和展示失败后重试；本次异常保护位于 Runnable 内，shutdown 永久关闭 OverlayToast。窗口 token 撤销及 ColorOS 服务断开待真机。
  - R10 待补测试：由测试模块覆盖 A→Home→B 不返回 Transition/不回退、A→Home→A 重新记录 fromLauncher/startedAt/启动计数、A→SystemUI/IME→B 不回退、copy 保留中断标记、桌面反复启动仍上报冷启动循环、直接 A→广告 App 仍回退。保留 UT_AY_1_03 与 UT_AY_6_19 的输入法前台/launch 断言；保守处理所有 ignored 窗口，输入法消失后同包继续使用也保留中断标记至下次实际启动，可能漏拦一次跨包跳转。真实桌面/最近任务/输入法事件顺序待真机。
  - R11 待补测试：由测试模块覆盖目标节点及祖先 ACTION_CLICK 全失败后不调用 dispatchGesture、切换到银行/OFF App 后旧节点失败不触发全局点击、正常节点/祖先点击仍执行。本次按用户 R11 指令覆盖原 PLAN Task 8 的坐标后备行为，代价是无法 ACTION_CLICK 的节点不再自动点击；敏感 App 切换场景待真机。
  - R12 待补测试：由测试模块覆盖 addException 写库失败仍发 USER_UNDO/打开目标、信号失败仍恢复、恢复失败不影响前两步、写库前已生效的源/目标临时例外、超过缓存 TTL 或刷新为 false 后临时例外仍有效、其他源/目标不受影响、进程重启后失败例外消失。临时例外仅存在单例 A11yRuntime 内存中，写库失败后仅本进程有效；后台恢复目标 App 能力待真机。

## [guard] 状态: 进行中 | 负责方: codex | 关卡: G7

| Task | 状态 | 最后提交 |
|---|---|---|
| Task 1: 决策策略 | 完成（UT-GD-1-01～10 通过） | feat(guard): decision policy |
| Task 2: 运行时执行、通知与观察期评估 | 完成（UT-GD-2-01～06、MT-GD-01～02 通过） | feat(guard): runtime runner, notifications and observation check |
| R14: 按本轮起点评估观察期 | 已修 | 本提交 |

- 下一步：R14 已修并同步 PLAN；2026-10-02 本地验证完成：:guard:testDebugUnitTest 18/18、bash scripts/check-merge.sh guard 全部通过、./gradlew test 全量 249/249（失败/错误/跳过均 0）；等待修复分支合并与测试模块补用例，未 push。以下是既有 G7 接力问题（当前全量只含已落地测试，未搬入暂存契约测试）：guard 自身（Task 1–2、UT/MT-GD）已通过；G7 仍 `进行中`，因 MT-CT-01～04 实跑 4/4 未通过——生产代码未按 CT-A2 暴露快照（详见 `testing/reports/G7-2026-10-02.md` 第 3 节与适配补丁 `testing/reports/G7-contract-wiring-attempt.diff`）。需要用户/协调者裁定：(a) 授权在 engine-vpn/engine-notify/engine-a11y 暴露只读访问器（DecisionSource、NotifyState、EnabledServicesChecker 生产实现、快照预取），或 (b) 由测试模块负责人重写 `testing/unit/pending/contract` 夹具的快照获取方式；并裁定 CT-A7（OFF 过滤在 Brain 还是 SignalRepository）。裁定后按补丁搬入 `testing/rule-regression/src/test` 重跑 `./gradlew :testing:rule-regression:testDebugUnitTest --tests 'com.sentinel.regression.contract.*'`，全部通过再改 `完成`。
- 已知问题：(1) RETRY_STORM 无 ruleId 时 PLAN 无分支，实现为 NoRuleFound；(2) 通知依赖 app 申请 POST_NOTIFICATIONS；(3) guard 暂存测试未改，仅增加 testImplementation(libs.room.runtime)；(4) testing/rule-regression/build.gradle.kts 已按协调者授权增加 room.runtime、koin.android、koin.test、work.testing 的 testImplementation（无版本改动）；(5) MT-CT 适配（编译性）：TunFactory/TunHandle 导入、buildBroadcastReceiver 替换、手工构造 VpnController、A11yState/DecisionSource 绑定，仅在补丁中，未提交；(6) MT-CT 剩余阻塞见 G7 报告第 3 节（快照预取、NotifyState、EnabledServicesChecker 不可获取；CT-A7 歧义）。
  - R14 待补测试：由测试模块覆盖第一轮起点等于 firstSeenAt、一个 USER_UNDO/TEMP_ALLOW 只触发一次延期且下一轮无新信号时结束、新一轮新增信号再延期、Worker 延迟执行后以实际延期时刻为起点、COUNTED 之外的信号不延期、起点毫秒边界。本次只按 observationEndsAt - EXTEND_MS 推导起点，未新增持久状态。
  - R13 未修（用户明确排除）：保留 GuardRunner.undo 的 remove→pin 行为及暂存 UT-AP-4-02 断言；非原子中断窗口仍由用户另行处理。

## [app] 状态: 待办 | 负责方: claude | 关卡: G3(Task 1–2) / G8

| Task | 状态 | 最后提交 |
|---|---|---|
| Task 1: 应用骨架与全局接线 | 待办 | — |
| Task 2: 最小首页（M3 可用版本） | 待办 | — |
| Task 3: 引导向导 | 待办 | — |
| Task 4: 完整首页、引擎日志、系统净化与撤销记录 | 待办 | — |
| Task 5: 应用页与应用详情 | 待办 | — |
| Task 6: 规则页 | 待办 | — |
| Task 7: 快捷开关 | 待办 | — |

- 下一步：Task 1–2（M3）；Task 5、6、7 只依赖 data，可提前做；Task 3 等 engine-system 合并，Task 4 等 guard 合并（此时把状态改为 `等待`）。测试已预先写好，暂存在 `testing/unit/pending/app/`（对照表与接口假设见其 README.md）：各 Task 的 Step 1 改为把对应测试搬入 `app/src/test/`，不重写；接口与暂存测试不一致时，按 README「接口假设」实现或同步修改测试并在提交说明中写明。
- 已知问题：无

## [merge] 状态: 待办 | 负责方: claude | 关卡: —

- 职责：当某个通道状态为 `完成` 时，在 `main` 上按顺序（notify → system → a11y → vpn → guard → app）合并；每次先在该通道 worktree 里运行 `scripts/check-merge.sh <模块>`，通过后合并，再在 `main` 跑 `./gradlew assembleDebug test`，并把该通道状态改为 `已合并`。
- 下一步：等待第一个通道完成。
- 已知问题：无

## [survey] 状态: 待办 | 负责方: 用户 | 关卡: —

- 职责：M0 Task 1 实机核实。用户连接手机后运行 `docs/device-survey/scripts/survey.sh`；报告写入 `docs/device-survey/find-x7-ultra-survey.md`。
- 下一步：等用户。
- 已知问题：无
