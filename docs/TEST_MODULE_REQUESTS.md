# 给测试模块的修复清单

> 实现已全部合并（引擎、guard）或在分支上完成（app），但暂存测试与生产代码有若干对不上的地方。生产代码按 `docs/CONTRACTS.md` 与各 `PLAN.md` 实现，**不为迁就测试而改生产代码**；以下问题请测试模块一侧修复。每条都附了定位信息和我已经确认过的事实。

## A. app 测试夹具（`testing/unit/pending/app/`，实现在分支 `chan/app`）

| # | 问题 | 影响 | 事实与建议 |
|---|---|---|---|
| A1 | `fakes/FakeData.kt` 的 `construct()` 假设仓库用 DAO 构造（`README A02`），实际 data 里 `GlobalStateRepository`、`AppConfigRepository`、`AppRegistry`、`OverrideRepository`、`SignalRepository` 是用 `SentinelDatabase` + `Clock` 构造的，夹具抛 `No unique DAO-based constructor` | UT-AP-2-01～03、4-01、5-*、7-*、UT-AP-1-03、整个 `ComposeAppTest` | 建议改成 Robolectric 内存 Room 数据库（`Room.inMemoryDatabaseBuilder`），夹具的 `*Rows` 改为直接写库；或为这些仓库提供以 DAO 构造的测试专用工厂 |
| A2 | `di/AllModulesTest.kt` 的 Koin `verify` 的 `extraTypes` 缺少 `File::class`；`RuleStore(File, ...)` 无法解析 | UT-AP-1-01 | `extraTypes` 加入 `File::class`（data 的 `UT-DA-6-01` 就是这样通过的） |
| A3 | `tile/PauseTileServiceTest.kt` 直接调用 `service.onDestroy()`，Robolectric 4.17 里 `ShadowTileService` 不是 `ShadowService` 的子类，抛 `ClassCastException` | UT-AP-7-01、7-02 | 不要直接调用 `onDestroy()`；或升级 Robolectric 到已修复的版本（需走版本变更流程，不在本清单内擅自改） |
| A4 | `fakes/ComposeAppTest.kt` 构造 `GuardRunner(signals, events, overrides, apps, notifier, clock)` 传 6 个参数，guard 实际是 5 个（无 `clock`） | 整个 `ComposeAppTest`（UT-AP-4-02、全部 MT-AP） | 改成 5 个参数 |
| A5 | `di/ModuleLoaderTest.kt` 用了 `Robolectric.buildApplication`，4.17 里不存在 | UT-AP-1-03 | `chan/app` 里已做最小改动（`ReflectionHelpers` 调 `Application.attach` 再 `onCreate`），断言未变，请把暂存版同步 |

## B. 跨模块契约测试 MT-CT-01～04（`testing/unit/pending/contract/`）

生产代码合并后，用现有接线跑了一遍，4 个全部失败；改动已存为 `testing/reports/G7-contract-wiring-attempt.diff`（含 4 处纯编译/接线适配）。失败原因是**测试对生产接线的假设不成立**，不是契约被违反：

| 编号 | 失败位置 | 原因 | 建议 |
|---|---|---|---|
| MT-CT-01 | `ContractTest.kt:81` | `A11yBrain` 读 `A11yRuntime.state`，配置快照只在 `SentinelAccessibilityService` 里 `prefetch()` 后才有；直接驱动时该 App 看起来是 OFF，不会开奖励窗口 | 夹具在驱动前先填充 `A11yRuntime.state` 的配置快照（或经真实服务入口驱动） |
| MT-CT-03 | `ContractTest.kt:289` | 同上；另外 `NotifyState` 只在 `NotifyRuntime.engineFor()` 内部私有构造，`koin.get<NotifyState>()` 不可能成功 | 经 `NotifyRuntime` 的真实入口取得，不要从 Koin 取 |
| MT-CT-04 | `ContractTest.kt:324` | 唯一的 `EnabledServicesChecker` 是 `SentinelVpnService` 里的私有类 `SettingsChecker` | 用测试自己实现的 `EnabledServicesChecker`（接口是公开的，PLAN 里也只要求接口） |
| MT-CT-02 | `ContractTest.kt:189` | 测试要求 `A11yBrain` 对 OFF 的 App 自己不返回信号；实际由 data 的 `SignalRepository.emit` 过滤。**已裁定**：见 `docs/CONTRACTS.md` C2，契约只要求「OFF 的 App 最终不落库」 | 把断言改成：OFF 的 App 经 `SignalRepository.emit` 后信号表里没有行；不要断言 `A11yBrain` 的返回值 |

## C. 缺失或有缺口的测试

| 编号 | 状态 | 说明 |
|---|---|---|
| RR-02、RR-03 | 没有暂存测试，且依赖真机录制的页面快照 | `testing/rule-regression/fixtures/snapshots` 目前只有空的 `expected.json`；需先用 engine-a11y debug 源集里的 `SnapshotDumper` 在真机录快照，再补测试 |
| RR-06 | 没有 `ProfileHealthTest` | 依赖 engine-system 的配置档案；档案要等实机核实报告才能填 `verified=true` |
| MT-AY-03 | 暂存测试只验证到「外部撤销之后例外生效」 | `UndoJump` 写跳转例外 + 发 `USER_UNDO` 信号这一半只有代码，没有测试验证 |
| 无 | `AndroidGuardNotifier`、`GuardActionReceiver` 没有任何暂存测试 | 通知与撤销按钮的入口目前未被自动化覆盖 |

## D. AppOps 总约束修复要求改的测试（分支 `chan/fix-appops`，尚未合并）

独立复核指出 `AppOpsSync` 现场构造 `verified=true` 绕过了「只执行档案中 verified=true 的项」。已按总约束修复（提交 `a6d68af`、`5fc59da`）：只有档案的 `verifiedAppOps` 明确声明的 AppOps 才会执行，档案为 null 或集合为空时不执行，意图保留在 pending。目前档案没有任何已核实项，所以真机上 AppOpsSync 什么也不执行（预期行为）。

修复后以下 6 个现有测试按预期失败，因为它们假设「没有已核实档案也会执行 AppOps」。**请改成：测试夹具里的档案声明 `verifiedAppOps`，再断言原有行为；并补一个「档案未核实/为 null → 不执行、pending 不丢」的用例。** 改好后合并 `chan/fix-appops`。

| 测试 | 失败断言 |
|---|---|
| `AppOpsSyncTest.UT_SY_4_01` | 第 25 行 `syncOnce() > 0`，实际 0 |
| `AppOpsSyncTest.UT_SY_4_02` | 第 54 行应有恢复 `SYSTEM_ALERT_WINDOW allow` 的命令，实际没有 |
| `AppOpsSyncTest.UT_SY_4_03` | 第 73 行 READY 后 `syncOnce() > 0`，实际 0 |
| `AppOpsSyncTest.UT_SY_4_04` | 第 84 行应有一个 overlay deny 命令，实际为空 |
| `SystemModuleTest.MT_SY_01` | 第 56 行 `syncOnce() > 0`，实际 0 |
| `SystemModuleTest.MT_SY_03` | 第 116 行重连后 `syncOnce() > 0`，实际 0 |

## E. 独立复核后新增、没有任何测试覆盖的修复

复核修复（Codex 实现）都没有新增测试，请补：

| 项 | 需要的用例 |
|---|---|
| R01 / R01 离线修正 | 上游连续 3 次传输失败关 TUN 并上报带原因的 STOPPED；有效应答（含 SERVFAIL/NXDOMAIN）重置计数；底层网络不可用时失败不计数；网络恢复时清零 |
| R02 | `onDestroy` 不阻塞主线程；`lifecycle` 锁被持有时销毁仍能先关 TUN |
| R04 | 修改成功但复核失败 → 回滚；回滚失败后记录仍可 `undo`/`undoAll`；日志落库前中断的恢复信息不丢 |
| R05 | Manifest 含 `SUPPORTS_ALWAYS_ON=false` |
| R06 | 阻塞读下关闭描述符能让读退出（需真机） |
| R08–R12 | 事件在回调返回前取出 source；悬浮窗异常被吞；经过桌面的独立启动不被当成跳转；失败后不再做坐标点击；撤销三步互不影响 |
| R14 | 延长一次后同一条信号不再被计入；无新信号时观察期按时结束 |
| R15 | 引擎正常 STOPPED 后守护仍视为「用户想要」 |
| R17 | 动态 AppOps 漂移后被复核并重新执行 |

## F. 已知不修的复核项

| 项 | 原因 |
|---|---|
| R07 HttpDNS IP 拒绝不尊重观察期/规则停用 | 需要真正的转发路径或重建 TUN，是设计问题，待设计 |
| R13 guard 撤销 remove+pin 非原子 | 修法（直接 pin）会让 `UT-AP-4-02` 的断言「先 remove 再 pin」失败；等测试模块确认该断言是否必须 |
| R16 DropBox 崩溃时间被替换为采集时间 | `SignalRepository.emit` 不接受时间参数，需改已冻结的 data |
| R01 之后的自动重启 | 现有停止流程会销毁服务并注销回调，自动重启风险较大；停止后需用户手动重新开启 |

## 修好之后

1. 把修好的测试放回 `testing/unit/pending/<模块>/`。
2. 在 `chan/app` 里把全部暂存 UT-AP / MT-AP 复制进 `app/src/test/`，跑 `./gradlew :app:testDebugUnitTest`。
3. 契约测试：在 guard 的 worktree 或 main 上应用 `G7-contract-wiring-attempt.diff` 后的版本，跑 `./gradlew :testing:rule-regression:testDebugUnitTest --tests 'com.sentinel.regression.contract.*'`，4 个全过才能把 `[guard]` 标「完成」（G7）。
