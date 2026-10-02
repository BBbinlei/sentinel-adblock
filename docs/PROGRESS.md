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

- 下一步：G3 引擎部分已通过：engine-vpn 48/48（46 UT + 2 MT）、./gradlew test 全量通过、check-merge.sh engine-vpn 全部通过；报告 testing/reports/G3-2026-10-02.md。待合并通道合并；DI-01～03、DI-11～14、DI-51 待真机（等用户）。
- 已知问题：原启动命令含损坏的绝对路径，按目标模块执行 :engine-vpn:assembleDebug；worktree 无 SDK 配置，通过 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools 运行。暂存 WireFixtures.kt 的 EffectiveConfig 导入包为 policy，冻结实现实际在 db，仅修正 import，未改断言。

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
| R03: AppOps 档案验证 | 未修（现有断言冲突） | 本提交 |

- 下一步：等待实机核实报告以填写 verified=true 的档案并执行 DI-31/DI-32；测试模块需补 RR-06（testing/rule-regression ProfileHealthTest）。Task 1–7、UT-SY 25 项、MT-SY 3 项均已通过，G5 报告见 testing/reports/G5-2026-10-02.md；状态保持进行中，不改完成。接手说明：Codex 额度耗尽，Claude 接手本通道（负责方 claude）。
- 已知问题：RR-06 的 ProfileHealthTest 在 testing/ 中不存在，本通道未自写；Task 7 用 CurrentProfile 持有者提供「当前档案 ColorOsProfile?」（Koin 不支持可空绑定），ROM 版本经反射读 ro.build.version.oplusrom，读不到则无档案（所有操作不执行）；UT-SY-6 两项通过，但 dropbox_sample.txt 为合成样本非实机采集，待实机核实后替换；DropboxCrashWorker 首次运行以当前时间为 lastChecked 基线（不回放历史崩溃）；UT-SY-5 四项通过（DriftInspector 另暴露 driftedCount 供上报，reapply 在非 READY 时返回空 map）；暂存 TestSupport 缺少 DriftInspector 的跨包 import，补足 import 以编译，断言不变；UT-SY-4 四项通过；恢复使用原 AppOps 模式，离线不发命令并保留待同步意图；backgroundPopupOp 为空时不推断 OEM 操作名；UT-SY-3 七项及 FakeDevice 自检通过；TestSupport 的混合 arrayOf 显式声明 <Any> 以消除 Kotlin 2.4 编译错误；新增本模块 AtomicFile 撤销定义持久化，data 保持只读；TestSupport 仅适配冻结 data 的四个仓库构造参数，未改断言；未来 appSync/inspector 辅助方法暂缓导入；测试增加版本目录已有 libs.room.runtime 引用；UT-SY-2 四项通过；AIDL 已启用；FakeShizukuApi 从暂存 TestSupport 原样提取以避免引用尚未实施的 Task 3–5 类型；Shizuku Provider/绑定依据官方 API 文档及已安装 13.1.5 签名；Task 1 UT-SY-1 四项已通过；新增已冻结版本的 libs.serialization.json 引用；coloros-unverified.json 使用不可匹配的占位 ROM 前缀与通用设置入口，18 项全部 WIZARD/verified=false，无臆造设备键或包名；提供的基线任务路径无效，改用 :engine-system:assembleDebug；worktree 无 local.properties，使用已安装 SDK 的 ANDROID_HOME 环境变量，不写越界配置。实机核实报告不存在，全部档案操作 verified=false。

- 修复复核 R03（2026-10-02）：报告属实，当前 AppOpsSync 在无匹配档案时仍生成 verified=true 的操作。未修改实现：用户要求“修法必然让现有测试失败时保留原行为”。`AppOpsSyncTest.UT_SY_4_01` 在无验证声明的 `ColorOsProfile("TEST", backgroundOp, emptyList())` 上断言 syncOnce()>0、deny/ignore 和 3 条成功日志；UT_SY_4_02 断言这三项的恢复命令；UT_SY_4_03 断言 READY 后 syncOnce()>0、pending=0 和 deny；UT_SY_4_04 断言无验证声明时发出 SYSTEM_ALERT_WINDOW deny。`SystemModuleTest.MT_SY_01/03` 同样断言无 AppOps 验证声明时执行并生成 6/3 条成功日志。默认空 verifiedAppOps 并严格门控必然违反这些断言，不能用 TEST ROM 特判或默认授权绕过。基线 :engine-system:testDebugUnitTest 28/28 通过，测试文件未改。
- 已知问题（R03，待补测试）：测试模块需协调上述冲突，并覆盖旧 JSON 缺字段默认为空、profile=null/空验证集合不发任何命令、仅执行明确验证的 AppOps、未验证开关意图保留及 pending 数量。实机核实报告不存在，全部档案 verified=false；修复落地后的预期是真机 AppOpsSync 不执行任何操作，但当前因断言冲突尚未达到该行为。待真机：DI-31/DI-32 和未验证意图上报。

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

- 下一步：等用户真机：DI-05、DI-21～25，用 SnapshotDumper 录制快照后补 RR-02/RR-03；G4 非真机部分已通过，见 testing/reports/G4-*.md。
- 已知问题：
  - 暂存测试对 `EffectiveConfig` 的包名假设（`com.sentinel.data.policy`）与 data 实际不符，实际在 `com.sentinel.data.db`。编译性修正：所有拷入的测试文件把 import 改为 `com.sentinel.data.db.EffectiveConfig`（断言未改）。
  - 暂存 TestSupport.kt 引用 Task 5/7 才存在的类型（VolumePort、KeyValueStore、A11yState）。Task 1–4 期间拷入的是截去 FakeVolume/MemoryStore/FakeA11yState 的版本，Task 5/7 补回，最终版与暂存版仅有上述 import 差异。
  - 设计取舍：激励关闭点击后保持静音至离开 App 或 3 秒收尾 tick；自动续费提醒每包 10 分钟去重；学习模式「窗口出现时间」按 Activity 变化/弹窗窗口记，内容变化不刷新。
  - MT-AY-03 暂存测试未覆盖执行器写例外/USER_UNDO（见暂存 README 待确认 5）。

## [guard] 状态: 进行中 | 负责方: claude | 关卡: G7

| Task | 状态 | 最后提交 |
|---|---|---|
| Task 1: 决策策略 | 完成（UT-GD-1-01～10 通过） | feat(guard): decision policy |
| Task 2: 运行时执行、通知与观察期评估 | 完成（UT-GD-2-01～06、MT-GD-01～02 通过） | feat(guard): runtime runner, notifications and observation check |

- 下一步：guard 自身（Task 1–2、UT/MT-GD）已通过；G7 仍 `进行中`，因 MT-CT-01～04 实跑 4/4 未通过——生产代码未按 CT-A2 暴露快照（详见 `testing/reports/G7-2026-10-02.md` 第 3 节与适配补丁 `testing/reports/G7-contract-wiring-attempt.diff`）。需要用户/协调者裁定：(a) 授权在 engine-vpn/engine-notify/engine-a11y 暴露只读访问器（DecisionSource、NotifyState、EnabledServicesChecker 生产实现、快照预取），或 (b) 由测试模块负责人重写 `testing/unit/pending/contract` 夹具的快照获取方式；并裁定 CT-A7（OFF 过滤在 Brain 还是 SignalRepository）。裁定后按补丁搬入 `testing/rule-regression/src/test` 重跑 `./gradlew :testing:rule-regression:testDebugUnitTest --tests 'com.sentinel.regression.contract.*'`，全部通过再改 `完成`。
- 已知问题：(1) RETRY_STORM 无 ruleId 时 PLAN 无分支，实现为 NoRuleFound；(2) 通知依赖 app 申请 POST_NOTIFICATIONS；(3) guard 暂存测试未改，仅增加 testImplementation(libs.room.runtime)；(4) testing/rule-regression/build.gradle.kts 已按协调者授权增加 room.runtime、koin.android、koin.test、work.testing 的 testImplementation（无版本改动）；(5) MT-CT 适配（编译性）：TunFactory/TunHandle 导入、buildBroadcastReceiver 替换、手工构造 VpnController、A11yState/DecisionSource 绑定，仅在补丁中，未提交；(6) MT-CT 剩余阻塞见 G7 报告第 3 节（快照预取、NotifyState、EnabledServicesChecker 不可获取；CT-A7 歧义）。

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
