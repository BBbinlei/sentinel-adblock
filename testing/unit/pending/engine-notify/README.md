# engine-notify 暂存测试

测试依据：`testing/unit/PLAN.md` Task NT、`engine-notify/PLAN.md` Task 1–4、`engine-notify/README.md`、`data/PLAN.md`、core-rules 已实现的 `NotifyRule` / `NotifyMatcher` / `EventKind`。共暂存 **14/14 个编号**，对应 14 个 JUnit4 测试函数；12 个 UT 均写入完整断言，2 个 MT 的服务接线断言存在下述接口边界。尚未编译或执行，不代表 G6 已通过。

## 编号对照

路径均相对 `src/test/kotlin/com/sentinel/notify/`。

| 编号 | 文件 | 测试函数 |
|---|---|---|
| UT-NT-1-01 | `core/NotifyFilterTest.kt` | `UT_NT_1_01_marketing_matches_built_in_rule` |
| UT-NT-1-02 | `core/NotifyFilterTest.kt` | `UT_NT_1_02_update_completed_is_not_marketing` |
| UT-NT-1-03 | `core/NotifyFilterTest.kt` | `UT_NT_1_03_ongoing_notification_is_untouched` |
| UT-NT-1-04 | `core/NotifyFilterTest.kt` | `UT_NT_1_04_off_or_notify_disabled_is_untouched` |
| UT-NT-1-05 | `core/NotifyFilterTest.kt` | `UT_NT_1_05_disabled_and_invalid_rules_are_skipped_in_order` |
| UT-NT-1-06 | `core/NotifyFilterTest.kt` | `UT_NT_1_06_own_notification_is_untouched` |
| UT-NT-2-01 | `core/DismissLearnerTest.kt` | `UT_NT_2_01_third_dismissal_returns_channel_rule` |
| UT-NT-2-02 | `core/DismissLearnerTest.kt` | `UT_NT_2_02_old_dismissal_expires_but_recent_one_remains` |
| UT-NT-2-03 | `core/DismissLearnerTest.kt` | `UT_NT_2_03_rejection_suppresses_for_thirty_days_then_can_ask_again` |
| UT-NT-2-04 | `core/DismissLearnerTest.kt` | `UT_NT_2_04_recreation_preserves_counts_and_rejection` |
| UT-NT-2-05 | `core/DismissLearnerTest.kt` | `UT_NT_2_05_null_channel_never_produces_or_increments_channel_rule` |
| UT-NT-4-01 | `service/ListenerMappingTest.kt` | `UT_NT_4_01_to_posted_maps_extras_flags_channel_package_and_key` |
| MT-NT-01 | `NotifyEngineTest.kt` | `MT_NT_01_marketing_returns_cancel_with_title_for_event_detail` |
| MT-NT-02 | `NotifyEngineTest.kt` | `MT_NT_02_learning_acceptance_updates_rules_and_cancels_same_channel` |

共享假实现：`fakes/NotifyFakes.kt`，包含可控时钟、内存 LearnerStore、NotifyState、用户规则仓库、重建器及事件记录器。后面三者是测试假邻居，仅提供用到的 PLAN 方法形状，不继承 data 的 concrete class，不冒充产品类。规则匹配、过滤、学习及编排均使用真实产品类。无需测试资源文件。

## 搬迁方法

在项目根目录运行：

```sh
cp -R testing/unit/pending/engine-notify/src/test engine-notify/src/
```

搬迁前核对目标尚无同名测试，避免覆盖开发者新增文件；当前目录不在 Gradle 源集中，不参与编译。

`engine-notify/build.gradle.kts` 已有 `testImplementation(libs.kotlin.test.junit)`、`libs.junit4`、`libs.robolectric`、`libs.coroutines.test`，并启用了 `testOptions { unitTests.isIncludeAndroidResources = true }`；不需要新增依赖或改构建配置。映射测试使用 `RobolectricTestRunner`、`@Config(sdk = [30])` 和 `RuntimeEnvironment`，不要求额外 AndroidX Test 库。普通核心测试使用 JUnit4，两个 MT 用 `runTest` 调用 suspend 假数据层 API。

未来产品接口准备好后，先运行下列命令确认失败，再按 TDD 实现；此次没有运行 Gradle，也没有下载 Robolectric SDK。

```sh
./gradlew :engine-notify:testDebugUnitTest
```

## 接口假设

共 **4 条**；实现方要么按此实现，要么同步修改测试。

1. **data 类型所在包**：`ProtectLevel`、`RewardedMode`、`RuleOrigin` 位于 `com.sentinel.data.db`；`EffectiveConfig` 位于 `com.sentinel.data.policy`。依据 data Task 1 的 `db/Entities.kt` 与 `policy/EffectivePolicy.kt` 文件分工；PLAN 未逐个指定类型包。`EffectiveConfig` 全部 10 个参数均显式传入，不假设任何构造默认值。
2. **通知核心类型所在包**：`PostedNotification`、`LearnerStore`、`DismissLearner`、`NotifyState`、`NotifyAction`、`NotifyEngine`、`BuiltInNotifyRules`、`NotifyFilter` 均在 `com.sentinel.notify.core`。依据 engine-notify Task 1–3 的 `core/*.kt` 文件路径；PLAN 未单列每个声明的完整包名。所有构造参数按计划顺序或完整命名参数传入，不添加入口或重载。
3. **映射扩展所在包**：顶层 `StatusBarNotification.toPosted()` 位于 `com.sentinel.notify.service`。依据 Task 4 的服务文件路径及 Produces；PLAN 写明扩展签名，但未写所在文件/包。测试在该包直接调用扩展，不依赖 Kotlin 生成的文件类名。
4. **内置规则的渠道范围**：内置 `NotifyRule.channelId == null`，对该包所有渠道按关键词判定。依据 Global Constraints、Task 1「每个内置包一条」和 README「标题或正文含关键词」；没有指定内置渠道白名单。测试不假设内置/学习规则 `source` 的文本内容；也不假设学习存储 JSON 的格式。

时间通过 `DismissLearner(store, clock: () -> Long)` 注入，无真实等待。过期用例取「超过 7 天 1 毫秒」，拒绝用例取「第 29 天」与「超过 30 天 1 毫秒」，不额外规定恰好截止时刻的开闭区间。拒绝期间是否累计新事件未写死，期满后提供三个新事件再断言可生成。

## 待确认

1. **MT-NT-01 的事件断言与测试对象不匹配**：清单要求「Cancel 动作，事件 detail 为标题」，但 Task 3 的 `NotifyEngine` 只返回动作，无 EventRepository 依赖；写日志由 Task 4 服务负责。当前测试完整断言真实 Cancel 的 key/pkg/ruleId/title，再按 Task 4 的规定由测试调用假 EventRepository 并断言 kind/detail/pkg/ruleId。这只能验证动作数据可供正确记录，**不能证明真实服务已经写日志**。实现时需提供可单测的动作执行接线接口或补充服务接线测试，才能闭合此断言；此处没有推断或添加新产品 API。
2. **MT-NT-02 没有接受学习入口**：清单要求「接受 → 写用户规则并触发重建」，Task 3 只有 `onLearnRejected`，接受由 Task 4 的 `NotifyLearnReceiver` 处理。data 仓库是 concrete class，DAO 签名及 receiver 注入入口均未定义，无法严格按现有签名把假仓库接进真实 receiver。当前测试先由真实 Engine + Learner 返回 AskLearn，然后测试按计划调用假仓库 `add(rule, LEARNED)` 和假更新器 `rebuildFromCache()`，断言存储/重建输入，并让同一个真实 Engine 读取更新后的假 NotifyState，验证同渠道新通知 Cancel、其他包/渠道不取消。**接受动作对真实 receiver 的接线尚未覆盖**；假仓库调用的断言不是产品 receiver 已接线的证据。开发时须明确 receiver 接线的可测试入口并补充实际调用链验证，不可仅凭当前测试把该接线判为完成。
3. **内置包名可能调整**：计划说以 M0 实机核实报告为准，而本次不连接手机。测试暂按计划的六个包精确断言；若调度方确认包名变化，需同步修改 UT-NT-1-01 的包列表。未擅自改变计划。

本目录仅涵盖 Task NT 的 UT/MT 编号。真实监听服务、通知划除原因、系统授权与通知发送归 DI-07 / DI-41，不在此次单元级和模块级测试范围内。
