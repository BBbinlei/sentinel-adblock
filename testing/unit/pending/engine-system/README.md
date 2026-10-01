# engine-system 暂存测试

本目录未加入 Gradle 源集。已编写 Task SY 的 **28/28** 个编号（UT 25、MT 3），另有 1 个 FakeDevice 工具自检。没有产品类替身、跳过或空断言；尚未运行 Gradle或编译这些测试，不能视为 G5 通过。

## 文件与编号对照

下表文件均相对 `src/test/kotlin/com/sentinel/system/`；测试函数前另有原始编号注释。

| 编号 | 文件 | 测试函数 |
|---|---|---|
| MT-SY-01 | `SystemModuleTest.kt` | `MT_SY_01_purify_then_undo_restores_complete_device_snapshot` |
| MT-SY-02 | `SystemModuleTest.kt` | `MT_SY_02_upgrade_drift_is_found_and_reapplied` |
| MT-SY-03 | `SystemModuleTest.kt` | `MT_SY_03_disconnect_preserves_applied_and_reconnect_runs_pending` |
| UT-SY-1-01 | `profile/ProfileLoaderTest.kt` | `UT_SY_1_01_parse_all_profile_fields` |
| UT-SY-1-02 | `profile/ProfileLoaderTest.kt` | `UT_SY_1_02_select_longest_prefix_independent_of_order` |
| UT-SY-1-03 | `profile/ProfileLoaderTest.kt` | `UT_SY_1_03_unmatched_rom_returns_null` |
| UT-SY-1-04 | `profile/ProfileLoaderTest.kt` | `UT_SY_1_04_preserve_before_placeholders` |
| UT-SY-2-01 | `shell/ShizukuGatewayTest.kt` | `UT_SY_2_01_four_states_and_flow_initial_value` |
| UT-SY-2-02 | `shell/ShizukuGatewayTest.kt` | `UT_SY_2_02_non_ready_never_calls_binder` |
| UT-SY-2-03 | `shell/ShizukuGatewayTest.kt` | `UT_SY_2_03_decode_success_and_failure_json` |
| UT-SY-2-04 | `shell/ShizukuGatewayTest.kt` | `UT_SY_2_04_suspended_binding_times_out_at_15_seconds` |
| UT-SY-3-01 | `ops/OpExecutorTest.kt` | `UT_SY_3_01_unverified_never_executes` |
| UT-SY-3-02 | `ops/OpExecutorTest.kt` | `UT_SY_3_02_already_applied_only_probes` |
| UT-SY-3-03 | `ops/OpExecutorTest.kt` | `UT_SY_3_03_success_records_before_and_system_event` |
| UT-SY-3-04 | `ops/OpExecutorTest.kt` | `UT_SY_3_04_post_probe_mismatch_records_failure` |
| UT-SY-3-05 | `ops/OpExecutorTest.kt` | `UT_SY_3_05_undo_substitutes_original_before_value` |
| UT-SY-3-06 | `ops/OpExecutorTest.kt` | `UT_SY_3_06_apply_all_continues_after_failure` |
| UT-SY-3-07 | `ops/OpExecutorTest.kt` | `UT_SY_3_07_undo_all_only_successful_not_yet_undone` |
| UT-SY-4-01 | `ops/AppOpsSyncTest.kt` | `UT_SY_4_01_overlay_background_and_clipboard_commands_are_logged` |
| UT-SY-4-02 | `ops/AppOpsSyncTest.kt` | `UT_SY_4_02_turning_off_restores_each_original_mode` |
| UT-SY-4-03 | `ops/AppOpsSyncTest.kt` | `UT_SY_4_03_offline_queues_then_ready_clears_pending` |
| UT-SY-4-04 | `ops/AppOpsSyncTest.kt` | `UT_SY_4_04_missing_background_op_only_changes_overlay` |
| UT-SY-5-01 | `drift/DriftInspectorTest.kt` | `UT_SY_5_01_probe_mismatch_is_reported_as_drift` |
| UT-SY-5-02 | `drift/DriftInspectorTest.kt` | `UT_SY_5_02_undone_operations_are_not_probed` |
| UT-SY-5-03 | `drift/ShizukuOfflineTest.kt` | `UT_SY_5_03_offline_skips_inspection_and_reports_degraded` |
| UT-SY-5-04 | `drift/DriftInspectorTest.kt` | `UT_SY_5_04_reapply_only_selected_ids` |
| UT-SY-6-01 | `crash/DropboxParserTest.kt` | `UT_SY_6_01_parse_each_timestamp_and_process_package` |
| UT-SY-6-02 | `crash/DropboxParserTest.kt` | `UT_SY_6_02_no_crashes_returns_empty_list` |

其他文件：

- `src/test/kotlin/com/sentinel/system/fakes/TestSupport.kt`：ScriptedShell、FakeShizukuApi、内存 data 环境、档案操作工厂；不定义任何同名产品类。
- `src/test/kotlin/com/sentinel/system/fakes/FakeDevice.kt`：内存 settings、包安装/启用状态、appops；完整状态快照；断开与命令失败注入。
- `src/test/kotlin/com/sentinel/system/fakes/FakeDeviceTest.kt`：命令格式、失败不修改状态、四类状态往返自检，不计入 28 个计划编号。
- `src/test/resources/profile_sample.json`：合成档案，不含实机验证声明，不应复制到产品 assets。
- `src/test/resources/dropbox_sample.txt`：合成 DropBox 输出，**不是实机采集**，待替换说明见下文。

## 搬迁与测试配置

从项目根目录执行计划要求的命令：

```sh
cp -R testing/unit/pending/engine-system/src/test engine-system/src/
```

搬迁前核对目标目录同名文件，避免覆盖后续开发的测试。本目录暂不参与编译，也没有改动任何构建文件。

`engine-system/build.gradle.kts` 已声明 JUnit4、kotlin.test.junit、Robolectric、coroutines-test 等，也已启用 `unitTests.isIncludeAndroidResources`。本组测试不需要新增库版本、不需要 MockK/Mockito、不需要网络或设备。内存 data 使用 Room 标准 API：data 的 Room 依赖目前是 `implementation`，不会导出到 engine-system 的测试编译类路径，搬迁时需添加已存在版本目录中的依赖：

```kotlin
dependencies {
    testImplementation(libs.room.runtime)
}
```

不需要 `room.testing` 或 `androidx.test.core`：直接用 `Room.inMemoryDatabaseBuilder` 和 `RuntimeEnvironment.getApplication()`。`SentinelDatabase_Impl` 应由 data 模块现有 KSP 配置生成，不在测试里仿造。

ShizukuGatewayTest 引用计划指定的 AIDL `IShellService.Stub`，产品开发 Task 2 时需要生成该接口；若尚未启用 AIDL，在现有 android 块中设置：

```kotlin
android {
    buildFeatures { aidl = true }
}
```

以上配置仅为将来的迁移说明，本次未修改 build.gradle.kts。产品和 data 实现到位后，TDD 可运行（**本次未运行**）：

```sh
./gradlew :engine-system:testDebugUnitTest --tests 'com.sentinel.system.*'
```

纯解析与 FakeDevice 自检用 JUnit4；访问 Room 或 AIDL Binder 的测试用 Robolectric API 30。内存数据库每用例创建/关闭，查询/事务执行器直接执行；Main 由 StandardTestDispatcher 替代并在结束后恢复。协程用 runTest/backgroundScope，超时用虚拟时间，无 Thread.sleep。崩溃解析用 try/finally 恢复默认时区。UT 不启动 Koin，MT 使用真实 OpExecutor、AppOpsSync、DriftInspector，系统边界为 FakeDevice；不覆盖 RR-06、DI-31、DI-32、Task 7 的 app 入口测试或 DropBox Worker 行为。

## 接口假设（11 条）

计划明确给出的 API（ProfileOp、ColorOsProfile、ProfileLoader、Shell、ShizukuApi、ShizukuGateway、OpExecutor 及各方法）直接引用，不改签名。下列计划未写死的内容需要实现方按此落实，或在搬迁时同步改测试。测试中没有反射寻找方法、没有伪造产品实现。

1. **data 枚举包名**：按 `data/PLAN.md` Task 1 的 `db/Entities.kt` 文件位置，推断 `EngineId`、`EngineState` 等实体枚举位于 `com.sentinel.data.db`。实体类也按该路径引用。
2. **仓库构造参数**：Task 2 允许“对应 DAO（或 SentinelDatabase）+ Clock”，推断 `OpLogRepository(db: SentinelDatabase, clock: Clock)`、`EventRepository(db: SentinelDatabase, clock: Clock)`、`EngineStatusRepository(db: SentinelDatabase, clock: Clock)`、`AppConfigRepository(db: SentinelDatabase, clock: Clock)` 均可用。最后一个的额外依赖未列在签名中，按“所有仓库”约定选择数据库形式；若需要 global/signals 参数，只调整共享 TestSupport。
3. **AppOpsSync 构造参数**：按 engine-system Task 4 Consumes 与 Shizuku 就绪规则，推断 `AppOpsSync(shell: Shell, executor: OpExecutor, profile: ColorOsProfile?, configs: AppConfigRepository, log: OpLogRepository, shizukuState: StateFlow<ShizukuState>)`，按此顺序传入；不依赖未定义的 DAO。这里的 profile 传当前选中的值。
4. **DriftInspector 构造参数**：按 Task 5 Consumes，推断 `DriftInspector(executor: OpExecutor, profile: ColorOsProfile?, log: OpLogRepository, shizukuState: StateFlow<ShizukuState>)`，顺序固定；探测通过 executor.status，重执行通过 executor.apply。
5. **SystemStatusReporter 构造参数**：按 Task 5 状态规则，推断 `SystemStatusReporter(status: EngineStatusRepository, shizukuState: StateFlow<ShizukuState>, driftedCount: StateFlow<Int>, pendingCount: StateFlow<Int>)`。计数作为输入；漂移和待处理分别独立验证，未强制两者重叠时如何去重。测试只调用已声明的 start(scope)，未新增产品方法。
6. **stateFlow 初值**：Task 2 声明 state()/stateFlow 但没有初始化规则，推断构造后或调用 state() 后 stateFlow.value 与当前 state() 一致。四种状态通过独立实例测试，不要求未声明的 refresh 方法。
7. **超时边界**：结合 Global Constraints 的单条命令 15s，推断 Gateway 的整个 exec（含挂起的 binder 获取）受 15,000ms 超时控制并返回 exitCode=-1；挂起阶段沿用调用协程的时钟，才能使用 coroutines-test 虚拟时间。不推断 stderr 文案。
8. **探测输出换行**：按 Task 3 “probe 得到 before / 匹配 appliedRegex / 替换 {before}”，推断探测值与单行正则匹配前去除 Shell 行尾换行；日志断言用 trim()，不限定日志存原始输出还是去换行输出。撤销 settings 时必须得到原值 7，而不是带换行的命令。
9. **syncOnce / pendingCount 含义**：Task 4 未说明返回 Int 的单位；只要求离线 syncOnce 返回 0，就绪有修改时返回正数；pendingCount 在离线有未同步意图时 >0、同步完成后为 0，不规定按 App 还是按权限计数。无后台弹窗 OP 时不编造默认 OP。
10. **CrashEntry.ts 的单位与时区**：Task 6 与 Worker 的 lastChecked 比较，以及 data 的毫秒 Clock，推断 ts 为 epoch 毫秒。无时区的 dumpsys 日期按 JVM 默认时区解析；测试固定 UTC 后恢复，不限定条目返回顺序。
11. **撤销与生成权限项**：Task 3 undo(logId) 与 Task 4 消费 OpExecutor 的要求，推断 AppOpsSync 使用传入的同一个 OpExecutor，使它能撤销生成的 `appop:<OP>:<pkg>` 日志，并从 appops 探测输出还原原模式。所有撤销测试保持同一个 executor 实例；不要求额外未声明的 registerOp/getLog API。跨实例/重启后的操作定义恢复见待确认。

## 待确认

1. **UT 的依赖替身边界矛盾**：testing/unit/PLAN.md 要求 UT 依赖一律用假实现，但 data/PLAN.md 把仓库声明为具体 class，没有开放继承或仓库接口，DAO 的函数签名也未给出。不能在测试中冒充同名产品类。本组 UT 对系统边界使用手写 Shell/ShizukuApi，对 data 采用隔离的内存 Room + 真实仓库（与 MT 已明确允许的方式一致），因此严格的 UT 仓库隔离仍未满足。实现方若提供可替换接口，需把 TestSupport 换成手写仓库/DAO 假实现；否则需要确认此边界例外。不用开放继承、MockK/Mockito 或未声明 DAO 方法偷偷绕过。
2. **缺少实机 DropBox 样本**：仓库目前只有只读采集脚本，没有脱敏实机输出。受本次“不连接手机”约束，提供标准格式合成文本，覆盖两条不同时间、不同 Process 包名；UT-SY-6-01 已有真实字段断言，但尚未满足 PLAN 的实机样本来源要求。取得脱敏 dumpsys 输出后替换资源并同步真实时间/包名断言；也需确认设备输出时区与 parser 处理方式。
3. **持久化撤销的信息不足**：OpLogEntity 没有 revert / appliedRegex / kind，而 OpExecutor 的固定构造参数没有当前档案。计划没有解释新实例如何从日志找到 revert。测试按同一个 executor 应用后撤销的最小可行理解编写；不宣称重启后撤销已覆盖。需要产品开发明确持久化映射或命令恢复方式，不能仅凭这些测试将内存缓存当成完整方案。
4. **Gateway 超时的测试边界**：IShellService.exec 是同步 AIDL 方法，不能使用 delay 模拟阻塞 Binder。UT-SY-2-04 将注入的挂起 binder 获取作为可控超时点；未验证 UserService 内实际 ProcessBuilder 的 15s 超时，也未验证取消真实阻塞 Binder。若超时仅定义在 UserService，需要同步收紧 Gateway 约定和该测试，真实 Shell 执行仍归设备边界。

MT-SY-03 的“待处理”按 Task 4 已给出的 App 权限同步队列解释：先应用一个档案项，再断开并打开 App 权限开关，断开期间不发命令，重新 syncOnce 后执行这些待处理权限；没有假设 OpExecutor 本身拥有未声明的档案操作持久队列。MT-SY-01 在 undoAll 前关闭配置意图，避免后续同步再次应用权限；不启动无限订阅任务，也不声称自动重连队列调度已覆盖。

## 当前验证范围

已完成离线静态核对：28/28 编号唯一且与 README 表一致；8 个计划测试文件名齐全；11 个 Kotlin 文件通过本地 Kotlin 2.4.20 PSI 语法解析（不做类型检查）；示例 JSON 有效；没有 @Ignore、空断言、Thread.sleep 或 MockK/Mockito 引用。临时语法检查工具已删除。产品代码与 data 尚未实现，不做编译通过或行为通过承诺。暂存目录中的真实断言将在迁移后作为 TDD 红灯起点。
