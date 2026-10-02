> 2026-10-02 更新：A1～A5 已在 chan/app 修复并同步至 app/src/test；夹具使用真实内存 Room 和真实仓库，native graphics 测量中文，启动测试执行真实 MainActivity。增加 MT-AP-03 日志返回导航回归，当前 35 个 @Test（含排除的 UT-AP-1-04）。执行结果与未决项以 docs/PROGRESS.md [app] 为准。以下接口假设与静态检查记录保留为原暂存测试的历史说明。

# app 暂存测试

本目录尚未加入任何 Gradle 源集。覆盖 `testing/unit/PLAN.md`「Task AP」全部 **30/30** 编号：25 个 UT、5 个 MT，共 **34 个 `@Test` 函数**。UT-AP-1-04 只在 G8 执行。没有产品代码、同名产品替身、空测试、`@Ignore` 或 `Assume`。

**当前只能确认代码已写齐、语法已检查，不能确认搬迁即编译通过。** app/data/guard/system 产品代码未实现，而且计划没有给出所有构造器、DAO、导航参数和 UI 定位信息。下列 20 条接口假设是明确的适配清单；实现方需采纳，或同步修改测试。尤其是假仓库边界和 RulesViewModel 注入方式，见「待确认」。

## 文件清单

所有 Kotlin 文件均位于 `src/test/kotlin/com/sentinel/app/`：

| 类型 | 文件 |
|---|---|
| 骨架 | `di/AllModulesTest.kt`、`di/ModuleLoaderTest.kt` |
| 首页 | `home/HomeViewModelTest.kt`、`home/HomeGuardAlertTest.kt` |
| 向导 | `onboarding/OnboardingViewModelTest.kt` |
| 系统净化 | `system/SystemCleanupViewModelTest.kt` |
| 应用 | `apps/AppsViewModelTest.kt`、`apps/AppDetailViewModelTest.kt` |
| 规则 | `rules/RulesViewModelTest.kt` |
| 磁贴 | `tile/PauseTileServiceTest.kt` |
| 模块级 | `AppFlowsTest.kt`、`LayoutRobustnessTest.kt` |
| 共享测试工具 | `fakes/FakeData.kt`、`fakes/FakeSystem.kt`、`fakes/FakeSetupChecker.kt`、`fakes/TestSupport.kt`、`fakes/ComposeAppTest.kt`、`fakes/ModuleFixtures.kt` |

共 18 个 Kotlin 文件及本 README。数据均在内存中；ServiceLoader 样本通过专用 ClassLoader 和内存 URL 提供，**不**在默认 `META-INF/services` 注册假入口。因此不需要 `src/test/resources/` 文件，也不会污染真实入口发现测试。

## 编号 → 文件 → 测试函数

以下文件路径均相对于 `src/test/kotlin/com/sentinel/app/`。同一编号需要多个独立状态时列出全部函数。

| 编号 | 文件 | 测试函数 |
|---|---|---|
| UT-AP-1-01 | `di/AllModulesTest.kt` | `AllModulesTest.UT_AP_1_01_data_and_all_discovered_entry_modules_verify_together` |
| UT-AP-1-02 | `di/ModuleLoaderTest.kt` | `UT_AP_1_02_filters_entries_using_declared_processes` |
| UT-AP-1-03 | `di/ModuleLoaderTest.kt` | `UT_AP_1_03_duplicate_ids_are_rejected`；`UT_AP_1_03_a_failed_start_does_not_prevent_later_entries_or_ui` |
| UT-AP-1-04 | `di/AllModulesTest.kt` | `G8AllModulesTest.UT_AP_1_04_g8_main_and_vpn_entry_sets_are_complete` |
| UT-AP-2-01 | `home/HomeViewModelTest.kt` | `UT_AP_2_01_shield_follows_enabled_pause_and_vpn` |
| UT-AP-2-02 | `home/HomeViewModelTest.kt` | `UT_AP_2_02_shield_writes_disable_or_enable_and_resume` |
| UT-AP-2-03 | `home/HomeViewModelTest.kt` | `UT_AP_2_03_degraded_message_is_warning` |
| UT-AP-3-01 | `onboarding/OnboardingViewModelTest.kt` | `UT_AP_3_01_resume_checks_all_and_selects_first_missing` |
| UT-AP-3-02 | `onboarding/OnboardingViewModelTest.kt` | `UT_AP_3_02_skip_advances_without_completing` |
| UT-AP-3-03 | `onboarding/OnboardingViewModelTest.kt` | `UT_AP_3_03_all_done_finishes_and_persists` |
| UT-AP-4-01 | `home/HomeGuardAlertTest.kt` | `UT_AP_4_01_recent_disabled_rules_are_grouped_by_app` |
| UT-AP-4-02 | `home/HomeGuardAlertTest.kt` | `UT_AP_4_02_undo_button_routes_to_guard_undo_for_the_alert` |
| UT-AP-4-03 | `home/HomeGuardAlertTest.kt` | `UT_AP_4_03_private_dns_and_shizuku_warnings_are_both_present` |
| UT-AP-4-04 | `system/SystemCleanupViewModelTest.kt` | `UT_AP_4_04_unverified_cannot_auto_apply` |
| UT-AP-4-05 | `system/SystemCleanupViewModelTest.kt` | `UT_AP_4_05_apply_all_skips_optional_uninstall_and_unverified` |
| UT-AP-5-01 | `apps/AppsViewModelTest.kt` | `UT_AP_5_01_sort_by_seven_day_counts_and_search_both_fields` |
| UT-AP-5-02 | `apps/AppsViewModelTest.kt` | `UT_AP_5_02_sensitive_app_is_default_allowed` |
| UT-AP-5-03 | `apps/AppDetailViewModelTest.kt` | `UT_AP_5_03_each_level_is_written_to_repository` |
| UT-AP-5-04 | `apps/AppDetailViewModelTest.kt` | `UT_AP_5_04_observation_days_round_up` |
| UT-AP-5-05 | `apps/AppDetailViewModelTest.kt` | `UT_AP_5_05_would_block_is_recent_details_for_this_package_deduplicated` |
| UT-AP-6-01 | `rules/RulesViewModelTest.kt` | `UT_AP_6_01_only_https_is_saved` |
| UT-AP-6-02 | `rules/RulesViewModelTest.kt` | `UT_AP_6_02_updating_stays_true_until_report_then_shows_counts` |
| UT-AP-6-03 | `rules/RulesViewModelTest.kt` | `UT_AP_6_03_delete_user_rule_rebuilds_after_deletion` |
| UT-AP-7-01 | `tile/PauseTileServiceTest.kt` | `UT_AP_7_01_click_pauses_five_minutes_then_resumes` |
| UT-AP-7-02 | `tile/PauseTileServiceTest.kt` | `UT_AP_7_02_tile_state_tracks_repository_changes` |
| MT-AP-01 | `AppFlowsTest.kt` | `MT_AP_01_temp_allow_takes_two_clicks_without_confirmation` |
| MT-AP-02 | `LayoutRobustnessTest.kt` | `MT_AP_02_360dp_large_font_has_no_overflow_or_ellipsis`；`MT_AP_02_dark_mode_renders_home_detail_and_cleanup` |
| MT-AP-03 | `AppFlowsTest.kt` | `MT_AP_03_unfinished_setup_starts_onboarding`；`MT_AP_03_finished_setup_starts_home` |
| MT-AP-04 | `AppFlowsTest.kt` | `MT_AP_04_uninstall_requires_confirmation_and_cancel_has_no_effect`；`MT_AP_04_other_operations_execute_without_confirmation` |
| MT-AP-05 | `LayoutRobustnessTest.kt` | `MT_AP_05_every_click_target_is_at_least_48dp` |

## 搬迁与测试配置

从项目根目录执行：

```sh
cp -R testing/unit/pending/app/src/test app/src/
```

现有 `app/build.gradle.kts` 已启用 Compose、`unitTests.isIncludeAndroidResources = true`，并声明 JUnit4、kotlin.test、Robolectric、coroutines-test、Koin test、Compose ui-test 和 debug ui-test manifest。本批不需要新增依赖或版本，也不需要 Room testing、work-testing、mockito/mockk。测试使用 SDK 35 的 Robolectric 环境，避免真实 Application 在每个测试前自动启动生产模块。

为了落实「UT-AP-1-04 仅 G8 执行」，搬迁时在 `android.testOptions` 中增加以下配置（**本次没有改动 build.gradle.kts**）。这是按里程碑选择测试，不是忽略失败测试：

```kotlin
val sentinelG8 = providers.gradleProperty("sentinelG8").orNull == "true"
android {
    testOptions {
        unitTests.all {
            it.useJUnit {
                if (!sentinelG8) excludeCategories("com.sentinel.app.di.G8Only")
            }
        }
    }
}
```

之后由实现方按 TDD 执行：

```sh
# G3：仅骨架和最小首页，尚未实现的完整 UI 不在 G3 范围内
./gradlew :app:testDebugUnitTest \
  --tests 'com.sentinel.app.di.AllModulesTest' \
  --tests 'com.sentinel.app.di.ModuleLoaderTest' \
  --tests 'com.sentinel.app.home.HomeViewModelTest'

# G8：所有 app 测试，含完整入口集合
./gradlew :app:testDebugUnitTest -PsentinelG8=true
```

注意：Gradle 的 `--tests` 只选择执行，不隔离编译；提前搬迁全部测试会要求全部被引用的类已经声明。M3 尚无 Task 3–7 API 时应先只复制对应任务的文件和所需工具，其余仍暂存，或先由实现方补齐计划中的接口声明。不能靠忽略测试解决编译错误。

## 接口假设（20 条）

以下只补充计划未写死的接口，**没有在测试中定义这些产品类**。已有签名仍以 PLAN 为准，例如 `ModuleLoader.load`、仓库公开方法、`ProfileOp`/`OpExecutor`、`SetupChecker` 和所有 ViewModel 操作方法。

### A01 — 模型包名

依据：`data/PLAN.md` Task 1 把枚举与实体放在 `db/Entities.kt`，但代码块未写 package。推断 `ProtectLevel`、`RewardedMode`、`SubscriptionFormat`、`EngineId`、`EngineState`、`OverrideState`、`SignalKind`、`RuleOrigin` 和所有 Entity 均属于 `com.sentinel.data.db`。core-rules 的 `EventKind`、`Rule`、`NotifyRule` 等已核对真实 `com.sentinel.rules.model` 代码。

### A02 — 仓库与 AppRegistry 的构造入口

依据：`data/PLAN.md` Task 2 写「对应 DAO（或 SentinelDatabase）+ Clock」，Task 3 未给 AppRegistry 构造签名。推断这些类存在**唯一一个**非 synthetic 的 DAO 构造入口，参数可为 `Clock`、名字以 `Dao` 结尾的接口，以及 FakeData 支持的公开仓库类型。AppRegistry 同样采用 DAO/仓库/Clock 注入；不要求特定参数顺序。

`FakeData` 用反射调用这个真实构造器，只为避开未定义的构造参数顺序；不绕过构造、不修改对象字段、不继承 final 类。若最终只提供 Database/Context/File/Scope 等构造入口，必须相应适配 FakeData，当前会明确报错，不会自动伪造产品行为。

### A03 — 手写 DAO 的最小合同

依据：`data/PLAN.md` Task 1 只指定 `Daos.kt`，未定义 DAO 类名或方法。推断以下接口在 `com.sentinel.data.db`；DAO 的 Flow 返回**原始实体**，仓库负责公开 API 所需的转换。FakeData 的动态代理只是手写内存实现，未知调用必定抛异常。没有宽泛的 `else -> null/Unit` 容错。

| DAO | 推断的查询签名 | 推断的写入签名 |
|---|---|---|
| `GlobalStateDao` | `observe(): Flow<GlobalStateEntity>`；`suspend get(): GlobalStateEntity` | `suspend upsert(e: GlobalStateEntity): Unit` 或等价 `update` |
| `AppConfigDao` | `observeAll(): Flow<List<AppConfigEntity>>`；`observe(pkg): Flow<AppConfigEntity?>`；`suspend get(pkg): AppConfigEntity?`；`suspend getAll(): List<AppConfigEntity>`；`suspend observationDue(now: Long): List<AppConfigEntity>` | `suspend upsert(e): Unit` 或 `update`；`suspend delete(pkg): Unit` |
| `EventDao` | `observeTodayCount(since: Long): Flow<Int>`；`observeCountByPkg(since: Long): Flow<Map<String, Int>>`；`observeRecent(kinds: Collection<EventKind>, limit: Int): Flow<List<EventEntity>>`；`suspend rulesHitSince(pkg, since): List<String>` | `suspend insert(e: EventEntity): Long` |
| `EngineStatusDao` | `observeAll(): Flow<List<EngineStatusEntity>>` | `suspend upsert(e: EngineStatusEntity): Unit` |
| `RuleOverrideDao` | `observeDisabled(): Flow<List<RuleOverrideEntity>>`；`observeRecent(since: Long): Flow<List<RuleOverrideEntity>>`；`suspend get(pkg, ruleId): RuleOverrideEntity?` | `suspend upsert(e): Unit`；`suspend remove(pkg, ruleId): Unit` |
| `SignalDao` | `observeUnhandled(): Flow<List<SignalEntity>>`；`suspend countSince(pkg, kinds: Collection<SignalKind>, since: Long): Int` | `suspend insert(e: SignalEntity): Long` |
| `UserRuleDao` | `observeAll(): Flow<List<UserRuleEntity>>` | `suspend upsert(e): Unit` 或 `insert(e): Unit`；`suspend delete(id: String): Unit` |
| `OpLogDao` | `observeAll(): Flow<List<OpLogEntity>>`；`suspend latestApplied(opId): OpLogEntity?`；`suspend get(id: Long): OpLogEntity?` | `suspend insert(e: OpLogEntity): Long`；`suspend markUndone(id: Long): Unit` |

表中 `pkg`/`ruleId`/`opId` 都是 String，省略的实体类型即所在 DAO 的实体；`since` 为 Long。代理剥离 Kotlin suspend 的 `Continuation` 参数，立即返回内存结果；写操作替换 StateFlow 值，触发真实仓库和 ViewModel 的收集。测试未用到的 DAO 方法不预先实现。

### A04 — HomeViewModel 构造器与私人 DNS 来源

依据：`app/PLAN.md` Task 2/4 只给无参数的类概要及 Consumes，UT-AP-4-03 要求私人 DNS 警告，但未给来源。推断显式注入：

```kotlin
HomeViewModel(
    global: GlobalStateRepository, events: EventRepository, statuses: EngineStatusRepository,
    overrides: OverrideRepository, apps: AppConfigRepository, clock: Clock,
    privateDnsWarning: () -> String?
)
```

测试以 lambda 提供警告文本，断言其进入 warnings；不访问设备 DNS 服务。私人 DNS 文案由来源返回，未把测试样本「私人 DNS 会让拦截失效」声明为固定产品文案。

### A05 — OnboardingViewModel 构造器

依据：`app/PLAN.md` Task 3 的 SetupChecker 与 SharedPreferences 行为。推断 `OnboardingViewModel(checker: SetupChecker, preferences: SharedPreferences)`。`onResume()` 检测到全部步骤完成时直接更新 finished 并持久化；不要求再点 next。

### A06 — AppsViewModel 构造器

依据：`app/PLAN.md` Task 5 的 Consumes 与 7 天窗口。推断 `AppsViewModel(apps: AppConfigRepository, events: EventRepository, clock: Clock)`。

### A07 — AppDetailViewModel 构造器

依据：`app/PLAN.md` Task 5 只写 `pkg: String`。推断在该参数后显式注入：`AppDetailViewModel(pkg: String, apps: AppConfigRepository, events: EventRepository, clock: Clock)`。公开操作方法及 state 数据类严格使用计划签名。

### A08 — SystemCleanupViewModel 构造器

依据：`app/PLAN.md` Task 4 的 Consumes。推断 `SystemCleanupViewModel(profile: ColorOsProfile, executor: OpExecutor)`；状态由真实 `OpExecutor.status` 初始化，并在执行完成后刷新。测试 profile 非空；产品对不存在匹配档案的处理不在本批清单中。

### A09 — RulesViewModel 的可控调用入口

依据：`app/PLAN.md` Task 6 写订阅 DAO/Updater，但没有构造器；`SubscriptionUpdater` 是 final class，其构造依赖也未写全。为避免继承或伪造它，推断最小调用注入：

```kotlin
RulesViewModel(
    subscriptions: Flow<List<SubscriptionEntity>>, userRules: UserRuleRepository,
    saveSubscription: suspend (SubscriptionEntity) -> Unit,
    updateAll: suspend () -> UpdateReport,
    rebuildFromCache: suspend () -> Long
)
```

生产接线可传 DAO 的公开 Flow/保存操作和已有 updater 的方法调用，例如 `{ updater.updateAll() }`、`updater::rebuildFromCache`。测试通过 CompletableDeferred 保持更新挂起，直接检查 updating；通过重建回调验证删除先发生。**这是推断的构造器，不是计划已经写明的产品 API。** 若实现方坚持直接接收 Updater，需改为手写其底层依赖并同步改本文件。

### A10 — RulesUiState 的字段

依据：`app/PLAN.md` Task 6 只写类型名，UT-AP-6-02 明确 updating 与结果文案。推断至少存在 `val updating: Boolean`、`val message: String?`。测试只使用这两个字段，不推断其他字段或数据类构造器。

### A11 — GuardRunner 构造器

依据：`guard/PLAN.md` Task 2 用 `...` 省略构造参数。推断 `GuardRunner(signals: SignalRepository, events: EventRepository, overrides: OverrideRepository, apps: AppConfigRepository, notifier: GuardNotifier, clock: Clock)`。使用真实 runner 的既有 `undo(pkg, ruleIds)`；通知依赖为无副作用的手写 GuardNotifier。

### A12 — 首次启动偏好文件

依据：`app/PLAN.md` Task 3 明确键 `onboarding_done`，没有文件名。推断 `context.getSharedPreferences("sentinel", Context.MODE_PRIVATE)`；向导和导航使用同一个文件，Koin 可解析相同 SharedPreferences。

### A13 — ModuleLoader 默认 ClassLoader

依据：`app/PLAN.md` Task 1 给 `loader: ClassLoader = ...`。推断默认取 `Thread.currentThread().contextClassLoader`（空时可回退 ModuleEntry 的 loader）。显式 loader 的过滤/重复测试不依赖此默认；真实 SentinelApp 启动隔离测试依赖该默认，从而能传入内存入口。若最终默认固定为自身 loader，要同步改启动测试注入点，不能把生产启动循环复制到测试中。

### A14 — 导航 composable 的参数与路由表示

依据：Task 1 给 `ui/nav/SentinelNavHost.kt` 与 Routes，但未给函数参数。推断 `@Composable fun SentinelNavHost(navController: NavHostController = rememberNavController())`，Routes 属于 `com.sentinel.app.ui.nav`，各常量是 String；APP_DETAIL/ENGINE_LOG 含字面占位符 `{pkg}`/`{engine}`。测试传入真实 rememberNavController，只在布局遍历时主动切换真实页面；临时放行流程仍用真实按钮导航。首次目的地由 onboarding_done 判定，不能由测试传入 startDestination 替代。

### A15 — 导航/磁贴的 Koin 解析与撤销接线

依据：app 架构采用 Koin 与页面 ViewModel，但 AppModule 的具体定义未写明。推断页面按 ViewModel 类型解析，详情用 `parametersOf(pkg)`，磁贴解析 GlobalStateRepository/Clock。测试注册真实 ViewModel 工厂与真实仓库，不加载会启动引擎的入口。首页提醒按钮通过实际导航/页面接线调用 Koin 内的 GuardRunner.undo，参数为该 GuardAlert.pkg 与全部 ruleIds；测试检查 remove + pin 的真实效果，未新增一个 HomeViewModel.undo 方法。

### A16 — 未指定的 UI testTag

依据：`app/PLAN.md` 给页面/行为，未给任何 testTag。推断以下稳定定位（测试没有固定 Screen 的参数签名）：

| testTag | 位置 |
|---|---|
| `screen:home` | 首页根区域 |
| `screen:onboarding` | 向导根区域 |
| `screen:app-detail` | 应用详情根区域 |
| `screen:system-cleanup` | 系统净化根区域 |
| `nav:apps` | 底部「应用」入口 |
| `app:<pkg>` | 应用列表里该应用的可点击行 |
| `home:system-cleanup` | 首页进入系统净化的可点击入口 |
| `home:guard-undo:<pkg>` | 该应用的提醒卡撤销按钮 |
| `app-detail:advanced` | 高级选项展开/收起按钮 |
| `cleanup:<opId>` | 对该项执行操作的按钮（不是整行或状态文字） |
| `cleanup:confirm`、`cleanup:cancel` | 卸载确认框的确认/取消按钮 |
| `temp-allow:confirmation` | 禁止出现的临时放行确认框标签；同时还断言 isDialog 数量为 0 |

「一键临时放行」来自 PLAN，测试用子串匹配以兼容 README 的「App 打不开？一键临时放行」。卸载确认文案没有规定，不推断具体文字；以真实 Dialog semantics + testTag 定位。

### A17 — 磁贴状态映射

依据：Task 7 规定状态随暂停变化、副标题 mm:ss，未写 Tile.STATE 值。推断防护中是 `STATE_ACTIVE`，暂停是 `STATE_INACTIVE`，五分钟时副标题是 `已暂停，05:00 后恢复`（前导零），恢复后为 `防护中`。不推断全局 enabled=false 时的文案。

### A18 — 合并提醒的代表字段

依据：UT-AP-4-01 要求按 App 合并，GuardAlert 包含单个 reason/at，但没有选择规则。推断 label 来自 AppConfigEntity，ruleIds 合并，at 取该 App 最新记录时间，reason 取最新记录对应的原因。测试同一个 App 的两个新原因相同，避免强加多原因拼接文案；已 PINNED 的记录不再展示。

### A19 — 时间窗口与计数口径

依据：UT-AP-5-01/04/05 和 data EffectivePolicy。推断 7 天为 `7 * 86_400_000` ms、3 天为 `3 * 86_400_000` ms，观察剩余天数正数向上取整，observationEndsAt=0 时为 null；WOULD_BLOCK 详情按当前 pkg、时间窗口筛选，忽略 null detail，保留实际域名 detail 而非 ruleId。FakeData 的拦截计数只计真实拦截事件（不含 WOULD_BLOCK 等非拦截事件）；测试使用 DNS_BLOCKED/POPUP_CLOSED，不依赖其他类别的计数解释。恰好到期、边界时间是否严格大于尚未规定，未额外断言。

### A20 — HTTPS 输入边界

依据：UT-AP-6-01「只接受 https」。推断有效地址还必须有非空 host，因此 `https://` 也拒绝，HTTP/FTP/file/无 scheme/httpsx 都不调用保存回调。不规定抛异常还是通过 state 提示错误，测试只检查未保存；不推断 SubscriptionEntity 的新订阅 level/tag 默认值。

## 待确认

1. **假仓库要求与 final 类冲突。** `testing/unit/PLAN.md` 要求 ViewModel 依赖一律假实现，但 `data/PLAN.md` 明确用 Kotlin `class`，不能直接手写仓库子类。本批采用「真实仓库 façade + 手写内存 DAO」，额外执行了仓库层逻辑，不是严格的纯 ViewModel 单元边界。没有假定仓库需要 open，也没有改产品接口。若必须隔离到纯 ViewModel，需先由接口拥有方定义可替代边界，再同步测试；不能在测试中定义同名仓库。
2. **RulesViewModel 的具体注入方式。** A09 采用公开 updater 方法的函数调用入口，避免要求 final Updater 可继承。实现方需要接受这个构造器，或提供能够控制挂起更新的底层假依赖。未经协调不能声称只按原计划类概要实现即可编译。
3. **app 不引用引擎/guard 类与 Task 3/4 Consumes 冲突。** MASTER_PLAN/Task 1 禁止直接引用引擎/guard 类，但 Task 3/4 和 UT-AP-4-02 又明确要求 ShizukuGateway、GuardRunner/OpExecutor 等。本测试暂按局部 Consumes 使用计划中的真实 GuardRunner/OpExecutor；生产桥接边界需明确。UT-AP-4-02 因缺少公开 VM 撤销方法，通过真实 UI 接线检查 remove+pin；这是组件/模块组合范围，需确认测试归类。
4. **data 在 VPN 进程的表述冲突。** app Task 1 文字说 VPN 启动 data/vpn 内容；data Task 6 写 DataEntry.processes={MAIN}，UT-AP-1-04 又要求 VPN 入口集合仅 {vpn}。按明确集合实现：VPN 仍加载 dataModule 提供依赖，但不启动 DataEntry。
5. **Application/AppRegistry 接口不一致。** app Task 1 写 `syncInstalled(initial = ...)`，data Task 3 唯一明确签名需要 `apps: List<InstalledApp>`。本批不新增该重载；启动测试要求实现方取应用列表后调用已规定的方法。默认 loader（A13）、首次运行偏好、AppModule 实际装配也需与启动实现核对。
6. **M3 编译范围与提前搬迁全量测试。** 所有文件搬进同一 test 源集会一起编译，G3 使用 --tests 不会避开尚不存在的完整 UI 类。只能阶段搬迁或先声明全部接口，不能跳过失败测试。G8 category 配置见上文。

## 检查记录与边界

- 已对照 Task AP 的表格逐号检查：30 个唯一编号，34 个测试函数；README 的每条映射可定位到具体文件/函数。
- 使用本机已缓存的 Kotlin 2.4.20 PSI parser 做**仅语法解析**；未做类型检查、未编译测试、未运行 Gradle。18 个 Kotlin 文件无语法错误。
- 使用本机缓存的 Compose 1.12.1、Koin 4.2.2、Robolectric 4.17 的 class 签名核对 DeviceConfigurationOverride、TextLayoutResult 获取、SemanticsNode.touchBoundsInRoot、Koin verify 和 TileService shadow；未联网、未下载新依赖。
- 布局测试实际运行真实导航/真实 ViewModel/真实页面，使用 360dp×800dp、1.3 倍字体、深色配置。获取 TextLayoutResult 检查 hasVisualOverflow 与每一行的 ellipsis；遍历垂直滚动容器，超过 50 屏仍未到末尾直接失败。触控检查使用 touchBoundsInRoot 而非图标视觉尺寸，以每个节点的真实 density 转换 dp，覆盖各路由、详情高级项和卸载弹窗；滚动检查采用 25% 重叠视口，暂时被视口裁掉的控件片段不当作完整触控目标，每页必须实际测到完整控件。文本另检查父布局造成的横向裁剪。
- 启动隔离测试执行真实 SentinelApp 与 MainActivity，不在测试中复制启动循环；服务测试执行真实 PauseTileService 的 Robolectric shadow，不绑定真实 QS 服务。
- 假时钟控制时间，coroutines-test 控制调度，更新中状态由 CompletableDeferred 控制；没有 Thread.sleep、真实文件数据库、网络请求、Shizuku/VPN/手机连接或许可操作。
- 因未运行测试，布局/启动/方法调用断言的通过情况均未验证；本批交付的是后续 TDD 的测试代码，不是 G3/G8 通过报告。
