# guard 暂存测试

状态：只存放测试源文件，尚未迁入任何 Gradle 测试源集。产品实现未完成；本次未运行 Gradle，也未验证编译或执行结果。

## 编号对照

已编写 18 / 18 个计划编号；每个编号都有实际断言。编号覆盖表示代码已写，不表示测试已通过。

| 编号 | 文件（相对此目录） | 测试函数 |
|---|---|---|
| MT-GD-01 | `src/test/kotlin/com/sentinel/guard/GuardModuleTest.kt` | `MT_GD_01_degrade_undo_pin_and_retry_closed_loop` |
| MT-GD-02 | `src/test/kotlin/com/sentinel/guard/GuardModuleTest.kt` | `MT_GD_02_new_app_stays_observing_until_worker_evaluates` |
| UT-GD-1-01 | `src/test/kotlin/com/sentinel/guard/policy/GuardPolicyTest.kt` | `UT_GD_1_01_retry_storm_disables_explicit_rule` |
| UT-GD-1-02 | `src/test/kotlin/com/sentinel/guard/policy/GuardPolicyTest.kt` | `UT_GD_1_02_undo_with_rule_does_not_require_second_signal` |
| UT-GD-1-03 | `src/test/kotlin/com/sentinel/guard/policy/GuardPolicyTest.kt` | `UT_GD_1_03_repeated_undo_disables_recent_hits` |
| UT-GD-1-04 | `src/test/kotlin/com/sentinel/guard/policy/GuardPolicyTest.kt` | `UT_GD_1_04_repeated_undo_without_hits_reports_no_rule` |
| UT-GD-1-05 | `src/test/kotlin/com/sentinel/guard/policy/GuardPolicyTest.kt` | `UT_GD_1_05_first_undo_without_rule_is_ignored` |
| UT-GD-1-06 | `src/test/kotlin/com/sentinel/guard/policy/GuardPolicyTest.kt` | `UT_GD_1_06_temp_allow_disables_recent_hits` |
| UT-GD-1-07 | `src/test/kotlin/com/sentinel/guard/policy/GuardPolicyTest.kt` | `UT_GD_1_07_all_crash_kinds_without_hits_report_no_rule` |
| UT-GD-1-08 | `src/test/kotlin/com/sentinel/guard/policy/GuardPolicyTest.kt` | `UT_GD_1_08_hit_window_covers_every_signal_kind` |
| UT-GD-1-09 | `src/test/kotlin/com/sentinel/guard/policy/GuardPolicyTest.kt` | `UT_GD_1_09_recent_hits_are_distinct` |
| UT-GD-1-10 | `src/test/kotlin/com/sentinel/guard/policy/GuardPolicyTest.kt` | `UT_GD_1_10_observation_extends_only_with_undo_or_allow` |
| UT-GD-2-01 | `src/test/kotlin/com/sentinel/guard/runtime/GuardRunnerTest.kt` | `UT_GD_2_01_retry_storm_disables_and_marks_handled` |
| UT-GD-2-02 | `src/test/kotlin/com/sentinel/guard/runtime/GuardRunnerTest.kt` | `UT_GD_2_02_pinned_rule_produces_no_rule_notice` |
| UT-GD-2-03 | `src/test/kotlin/com/sentinel/guard/runtime/GuardRunnerTest.kt` | `UT_GD_2_03_one_notification_failure_does_not_stop_collection` |
| UT-GD-2-04 | `src/test/kotlin/com/sentinel/guard/runtime/GuardRunnerTest.kt` | `UT_GD_2_04_undo_removes_disabled_state_and_pins_rules` |
| UT-GD-2-05 | `src/test/kotlin/com/sentinel/guard/runtime/ObservationWorkerTest.kt` | `UT_GD_2_05_temp_allow_during_observation_extends_three_days` |
| UT-GD-2-06 | `src/test/kotlin/com/sentinel/guard/runtime/ObservationWorkerTest.kt` | `UT_GD_2_06_clean_observation_ends_without_extension_notice` |

## 文件与测试边界

四个测试文件严格沿用 `testing/unit/PLAN.md` Task GD 的名称。共享辅助文件为 `src/test/kotlin/com/sentinel/guard/fakes/GuardFixture.kt`，提供内存 Room、可控 `Clock`、真实 data 仓库及手写 `RecordingNotifier`。不继承或伪造产品仓库，不定义产品同名类。

策略测试直接调用 `GuardPolicy`；运行时通过真实 `guardModule` 取得 `GuardRunner`。单条异常由通知假实现精确抛出一次，再检查该信号已处理、后续信号仍完成停用与通知。Worker 用真实 `ObservationWorker.doWork()`；模块测试走真实事件、信号、override、配置仓库。测试数据内联，无外部资源文件。

## 搬迁与测试配置

在实现 guard 时执行：

```sh
cp -R testing/unit/pending/guard/src/test guard/src/
```

现有 `guard/build.gradle.kts` 已声明 JUnit4、kotlin.test.junit、Robolectric、coroutines-test、Koin test、work-testing，且已开启 `unitTests.isIncludeAndroidResources`。本套测试还直接使用 Room API；data 把 Room 作为 `implementation`，不能依赖其在 guard 测试编译类路径上的传递可见性。搬迁时在 guard 的 dependencies 加入已有版本目录别名：

```kotlin
testImplementation(libs.room.runtime)
```

不新增库版本。本次仅把配置说明写在这里，未修改构建文件。测试固定 Robolectric SDK 30、`Config.NONE`，不需要手机或应用 Manifest。之后按 TDD 首先运行：

```sh
./gradlew :guard:testDebugUnitTest
```

## 接口假设（5 条）

实现方需要接受这些假设，或同步改动辅助测试文件；不能把此 README 当成产品接口已经确定的证据。

1. **GD-A1：类型包。** data Task 1 的 `Files` 把实体/枚举放在 `data/db/Entities.kt`，因此假设该节的实体、枚举和 `EffectiveConfig` 都在 `com.sentinel.data.db`；`EffectivePolicy` 位于 `com.sentinel.data.policy`。后者文件路径已给定，前者包声明尚未写明。guard 的 `Decision` 与 `GuardPolicy` 同在 `com.sentinel.guard.policy`，依据 guard Task 1 的唯一产品文件路径。
2. **GD-A2：可覆盖的 DI 实例。** 根据 data Task 6 的 `dataModule` 与 guard Task 2 的 `guardModule`，假设模块通过 Koin 提供计划内仓库和 `GuardRunner`，并从容器解析 `SentinelDatabase`、`Clock`、`GuardNotifier`。测试最后覆盖这三项为内存库/时钟/假通知，不假设省略号里的仓库或 Runner 构造参数。
3. **GD-A3：Worker 构造。** guard Task 2 只写 `class ObservationWorker : CoroutineWorker`，未给构造签名。假设可用标准 `(Context, WorkerParameters)` 构造，依赖从已启动的 Koin 取得；`TestListenableWorkerBuilder` 使用这个入口。若采用构造注入，需在测试 builder 指定 `WorkerFactory`，不改变测试断言。
4. **GD-A4：协程与时钟。** 根据 testing README 第 5 节和 `GuardRunner.start(scope)`，假设 Runner 的信号收集继承传入 scope 的调度器，Worker 使用容器内 `Clock`。Room 使用直接 query/transaction executor；测试调用 `runCurrent()` 处理就绪任务，不用 `Thread.sleep`，不把业务时钟推进交给真实时间。
5. **GD-A5：DROPBOX_CRASH 文案。** guard Task 1 列出六类信号、五条 reason 文案，但未逐一标明 DROPBOX 的 reason；按崩溃旁证的含义取 `"应用崩溃"`。策略测试对此作精确断言。

## 待确认

- guard Task 2 的 Runner 构造参数和 Worker 注入方式省略；GD-A2/A3 是搬迁前需要对齐的接线项，不保证任意一种实现都可以不改辅助文件。未为这些空白另造产品 API。
- guard Task 2 用 `countSince` 统计观察期 `[firstSeenAt, now]`，但 data 的签名只有下界 `since`。本套按正常数据时间戳不超过当前 `Clock` 的理解实现，没有虚构带 `until` 的重载；若要覆盖未来时间戳，需要先明确数据层约束。
- `RETRY_STORM.ruleId == null` 没有决策表分支，也不属于 Task GD 的编号清单；本套未自定该行为。实现方应明确它是无效信号还是 `NoRuleFound`。
- `DROPBOX_CRASH` 的 reason 按 GD-A5 理解；如需不同文案，应同步修改相应断言。

## 本次检查

仅静态核对编号、函数和文件对应关系、写入路径、禁止项及测试源码内容。没有运行 Gradle，没有设备、网络、提交或产品代码变更；失败用例没有被忽略或替换为空断言。
