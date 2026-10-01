# engine-a11y 暂存测试

本目录不属于任何 Gradle 源集。已编写 54 个 UT、5 个 MT，共 59 个带编号的测试函数；其中 **58 个用例的计划断言已完整写出，MT-AY-03 只覆盖回退及撤销后例外生效**，缺少执行器的例外写入/USER_UNDO 发射验证（见待确认 5）。没有运行 Gradle，也没有验证编译或测试通过。

所有状态机和编排器使用真实计划类；时间由 `TestClock` 控制，音量端口、持久化键值及 `A11yState` 为手写假实现。未创建产品类替身。12 个 XML 页面为手工合成数据，覆盖开屏 3、弹窗 3、陷阱 2、自动续费 1、正常页面 3；它们用于逻辑测试，不替代 RR-02/RR-03 的真实页面回归。资源由 JDK XML 解析器读为现有 `SnapshotNode`，不需要序列化库或插件。

## 文件

- `src/test/kotlin/com/sentinel/a11y/core/ForegroundTrackerTest.kt`
- `src/test/kotlin/com/sentinel/a11y/core/RuleClickerTest.kt`
- `src/test/kotlin/com/sentinel/a11y/core/LearningRecorderTest.kt`
- `src/test/kotlin/com/sentinel/a11y/core/HealthSignalsTest.kt`
- `src/test/kotlin/com/sentinel/a11y/core/RewardedHandlerTest.kt`
- `src/test/kotlin/com/sentinel/a11y/core/VolumeKeeperTest.kt`
- `src/test/kotlin/com/sentinel/a11y/core/JumpBackGuardTest.kt`
- `src/test/kotlin/com/sentinel/a11y/service/NodeViewAdapterTest.kt`
- `src/test/kotlin/com/sentinel/a11y/A11yBrainTest.kt`
- `src/test/kotlin/com/sentinel/a11y/fakes/TestSupport.kt`
- `src/test/resources/snapshots/{splash-1,splash-2,splash-3,popup-1,popup-2,popup-3,trap-1,trap-2,autorenew-1,normal-1,normal-2,normal-3}.xml`
- `README.md`

## 编号对照（59/59）

路径相对于本目录。MT-AY-03 的函数名明确表示“外部撤销写入后的状态”，不表示已测试 Receiver。

| 编号 | 文件 | 测试函数 |
|---|---|---|
| UT-AY-1-01 | `src/test/kotlin/com/sentinel/a11y/core/ForegroundTrackerTest.kt` | `UT_AY_1_01_launcher_origin` |
| UT-AY-1-02 | `src/test/kotlin/com/sentinel/a11y/core/ForegroundTrackerTest.kt` | `UT_AY_1_02_systemui_is_not_launcher` |
| UT-AY-1-03 | `src/test/kotlin/com/sentinel/a11y/core/ForegroundTrackerTest.kt` | `UT_AY_1_03_ime_does_not_change_foreground` |
| UT-AY-1-04 | `src/test/kotlin/com/sentinel/a11y/core/ForegroundTrackerTest.kt` | `UT_AY_1_04_clicked_is_also_interaction` |
| UT-AY-1-05 | `src/test/kotlin/com/sentinel/a11y/core/ForegroundTrackerTest.kt` | `UT_AY_1_05_launch_count_uses_pkg_and_time_window` |
| UT-AY-2-01 | `src/test/kotlin/com/sentinel/a11y/core/RuleClickerTest.kt` | `UT_AY_2_01_splash_click_in_launch_window` |
| UT-AY-2-02 | `src/test/kotlin/com/sentinel/a11y/core/RuleClickerTest.kt` | `UT_AY_2_02_launch_rules_do_not_run_outside_window` |
| UT-AY-2-03 | `src/test/kotlin/com/sentinel/a11y/core/RuleClickerTest.kt` | `UT_AY_2_03_splash_toggle_and_off_level` |
| UT-AY-2-04 | `src/test/kotlin/com/sentinel/a11y/core/RuleClickerTest.kt` | `UT_AY_2_04_trap_text_is_never_clicked` |
| UT-AY-2-05 | `src/test/kotlin/com/sentinel/a11y/core/RuleClickerTest.kt` | `UT_AY_2_05_disabled_rule_is_skipped_with_fallback` |
| UT-AY-2-06 | `src/test/kotlin/com/sentinel/a11y/core/RuleClickerTest.kt` | `UT_AY_2_06_normal_pages_have_no_action` |
| UT-AY-2-07 | `src/test/kotlin/com/sentinel/a11y/core/RuleClickerTest.kt` | `UT_AY_2_07_throttle_interval_limit_and_reset` |
| UT-AY-2-08 | `src/test/kotlin/com/sentinel/a11y/core/RuleClickerTest.kt` | `UT_AY_2_08_checked_autorenew_warns_only_with_matching_text` |
| UT-AY-3-01 | `src/test/kotlin/com/sentinel/a11y/core/LearningRecorderTest.kt` | `UT_AY_3_01_viewid_has_priority_and_is_stripped` |
| UT-AY-3-02 | `src/test/kotlin/com/sentinel/a11y/core/LearningRecorderTest.kt` | `UT_AY_3_02_without_viewid_uses_class_short_name_and_text` |
| UT-AY-3-03 | `src/test/kotlin/com/sentinel/a11y/core/LearningRecorderTest.kt` | `UT_AY_3_03_expired_window_does_not_propose` |
| UT-AY-3-04 | `src/test/kotlin/com/sentinel/a11y/core/LearningRecorderTest.kt` | `UT_AY_3_04_non_close_text_does_not_propose` |
| UT-AY-3-05 | `src/test/kotlin/com/sentinel/a11y/core/LearningRecorderTest.kt` | `UT_AY_3_05_auto_click_in_same_window_prevents_learning` |
| UT-AY-3-06 | `src/test/kotlin/com/sentinel/a11y/core/LearningRecorderTest.kt` | `UT_AY_3_06_generated_selectors_parse_and_match_clicked_node` |
| UT-AY-4-01 | `src/test/kotlin/com/sentinel/a11y/core/HealthSignalsTest.kt` | `UT_AY_4_01_chinese_crash_dialog_resolves_app_label` |
| UT-AY-4-02 | `src/test/kotlin/com/sentinel/a11y/core/HealthSignalsTest.kt` | `UT_AY_4_02_unknown_app_label_is_not_a_signal` |
| UT-AY-4-03 | `src/test/kotlin/com/sentinel/a11y/core/HealthSignalsTest.kt` | `UT_AY_4_03_third_launch_signals_once_per_ten_minutes_per_pkg_kind` |
| UT-AY-5-01 | `src/test/kotlin/com/sentinel/a11y/core/RewardedHandlerTest.kt` | `UT_AY_5_01_silent_countdown_close_and_restore` |
| UT-AY-5-02 | `src/test/kotlin/com/sentinel/a11y/core/RewardedHandlerTest.kt` | `UT_AY_5_02_leaving_app_mid_video_restores_volume` |
| UT-AY-5-03 | `src/test/kotlin/com/sentinel/a11y/core/RewardedHandlerTest.kt` | `UT_AY_5_03_timeout_after_ninety_seconds_restores_volume` |
| UT-AY-5-04 | `src/test/kotlin/com/sentinel/a11y/core/RewardedHandlerTest.kt` | `UT_AY_5_04_ask_waits_for_choice_before_muting` |
| UT-AY-5-05 | `src/test/kotlin/com/sentinel/a11y/core/RewardedHandlerTest.kt` | `UT_AY_5_05_block_mode_never_opens_reward_window` |
| UT-AY-5-06 | `src/test/kotlin/com/sentinel/a11y/core/VolumeKeeperTest.kt` | `UT_AY_5_06_process_restart_restores_persisted_volume` |
| UT-AY-5-07 | `src/test/kotlin/com/sentinel/a11y/core/VolumeKeeperTest.kt` | `UT_AY_5_07_repeated_restore_never_applies_stale_value` |
| UT-AY-6-01 | `src/test/kotlin/com/sentinel/a11y/core/JumpBackGuardTest.kt` | `UT_AY_6_01_unattended_launcher_jump_to_pinduoduo_reverts` |
| UT-AY-6-02 | `src/test/kotlin/com/sentinel/a11y/core/JumpBackGuardTest.kt` | `UT_AY_6_02_shake_hint_during_use_reverts_to_source` |
| UT-AY-6-03 | `src/test/kotlin/com/sentinel/a11y/core/JumpBackGuardTest.kt` | `UT_AY_6_03_click_after_launch_prevents_jumpback` |
| UT-AY-6-04 | `src/test/kotlin/com/sentinel/a11y/core/JumpBackGuardTest.kt` | `UT_AY_6_04_notification_bar_open_is_not_an_ad_jump` |
| UT-AY-6-05 | `src/test/kotlin/com/sentinel/a11y/core/JumpBackGuardTest.kt` | `UT_AY_6_05_wechat_link_has_user_interaction` |
| UT-AY-6-06 | `src/test/kotlin/com/sentinel/a11y/core/JumpBackGuardTest.kt` | `UT_AY_6_06_share_sheet_selection_is_intentional` |
| UT-AY-6-07 | `src/test/kotlin/com/sentinel/a11y/core/JumpBackGuardTest.kt` | `UT_AY_6_07_scan_then_open_has_user_interaction` |
| UT-AY-6-08 | `src/test/kotlin/com/sentinel/a11y/core/JumpBackGuardTest.kt` | `UT_AY_6_08_home_between_apps_breaks_direct_source` |
| UT-AY-6-09 | `src/test/kotlin/com/sentinel/a11y/core/JumpBackGuardTest.kt` | `UT_AY_6_09_recent_tasks_between_apps_breaks_direct_source` |
| UT-AY-6-10 | `src/test/kotlin/com/sentinel/a11y/core/JumpBackGuardTest.kt` | `UT_AY_6_10_alipay_authorization_is_not_an_ad_landing` |
| UT-AY-6-11 | `src/test/kotlin/com/sentinel/a11y/core/JumpBackGuardTest.kt` | `UT_AY_6_11_launcher_widget_opens_target_directly` |
| UT-AY-6-12 | `src/test/kotlin/com/sentinel/a11y/core/JumpBackGuardTest.kt` | `UT_AY_6_12_assistant_screen_is_not_launcher_origin` |
| UT-AY-6-13 | `src/test/kotlin/com/sentinel/a11y/core/JumpBackGuardTest.kt` | `UT_AY_6_13_voice_assistant_is_not_launcher_origin` |
| UT-AY-6-14 | `src/test/kotlin/com/sentinel/a11y/core/JumpBackGuardTest.kt` | `UT_AY_6_14_split_screen_start_from_systemui` |
| UT-AY-6-15 | `src/test/kotlin/com/sentinel/a11y/core/JumpBackGuardTest.kt` | `UT_AY_6_15_launcher_shortcut_opens_target_directly` |
| UT-AY-6-16 | `src/test/kotlin/com/sentinel/a11y/core/JumpBackGuardTest.kt` | `UT_AY_6_16_browser_link_has_user_interaction` |
| UT-AY-6-17 | `src/test/kotlin/com/sentinel/a11y/core/JumpBackGuardTest.kt` | `UT_AY_6_17_permission_dialog_during_launch_is_not_ad_jump` |
| UT-AY-6-18 | `src/test/kotlin/com/sentinel/a11y/core/JumpBackGuardTest.kt` | `UT_AY_6_18_same_package_page_change_is_not_jump` |
| UT-AY-6-19 | `src/test/kotlin/com/sentinel/a11y/core/JumpBackGuardTest.kt` | `UT_AY_6_19_ime_popup_is_not_jump` |
| UT-AY-6-20 | `src/test/kotlin/com/sentinel/a11y/core/JumpBackGuardTest.kt` | `UT_AY_6_20_incoming_call_is_not_ad_landing` |
| UT-AY-6-21 | `src/test/kotlin/com/sentinel/a11y/core/JumpBackGuardTest.kt` | `UT_AY_6_21_alarm_screen_is_not_ad_landing` |
| UT-AY-6-22 | `src/test/kotlin/com/sentinel/a11y/core/JumpBackGuardTest.kt` | `UT_AY_6_22_scroll_in_launch_window_prevents_jumpback` |
| UT-AY-6-23 | `src/test/kotlin/com/sentinel/a11y/core/JumpBackGuardTest.kt` | `UT_AY_6_23_saved_source_target_exception_prevents_both_reasons` |
| UT-AY-6-24 | `src/test/kotlin/com/sentinel/a11y/core/JumpBackGuardTest.kt` | `UT_AY_6_24_disabled_jumpback_rule_prevents_both_reasons` |
| UT-AY-8-01 | `src/test/kotlin/com/sentinel/a11y/service/NodeViewAdapterTest.kt` | `UT_AY_8_01_android_tree_maps_all_node_fields` |
| MT-AY-01 | `src/test/kotlin/com/sentinel/a11y/A11yBrainTest.kt` | `MT_AY_01_cold_launch_splash_returns_click_and_event_kind` |
| MT-AY-02 | `src/test/kotlin/com/sentinel/a11y/A11yBrainTest.kt` | `MT_AY_02_rewarded_flow_opens_window_mutes_closes_and_restores` |
| MT-AY-03 | `src/test/kotlin/com/sentinel/a11y/A11yBrainTest.kt` | `MT_AY_03_jumpback_then_external_undo_exception_prevents_repeat` |
| MT-AY-04 | `src/test/kotlin/com/sentinel/a11y/A11yBrainTest.kt` | `MT_AY_04_manual_close_after_one_second_proposes_page_rule` |
| MT-AY-05 | `src/test/kotlin/com/sentinel/a11y/A11yBrainTest.kt` | `MT_AY_05_dependency_exception_is_isolated_and_brain_can_recover` |

## 搬迁与配置

在项目根目录执行（先查看目标目录，避免覆盖后续开发者已写的同名测试）：

```sh
cp -R testing/unit/pending/engine-a11y/src/test engine-a11y/src/
```

README 不搬入源集。`engine-a11y/build.gradle.kts` 已声明 `libs.kotlin.test.junit`、`libs.junit4`、`libs.robolectric`，且已开启 `testOptions { unitTests.isIncludeAndroidResources = true }`。这些测试使用 JUnit4 `org.junit.Test`，无需改为 JUnit5、无需添加任何依赖或版本、无需修改当前构建配置。适配器用 `@Config(sdk = [30], manifest = Config.NONE)`，不启动真实服务或 Koin；SDK 30 满足 minSdk，并避免为纯树映射绑定 compileSdk 37 的 Robolectric 镜像。将来运行 Robolectric 时需环境已具备对应 SDK 镜像；本次没有联网下载。

模块 API 落地并确认下述假设后，开发者按计划在本地执行（**本次未执行**）：

```sh
./gradlew :engine-a11y:testDebugUnitTest
```

先观察缺失实现/行为造成的失败，再实现至通过；不要把暂存目录注册为额外源集，不使用 `@Ignore`。UT-AY-8-01 使用 Robolectric 的 `ShadowAccessibilityNodeInfo.addChild` 构造两层子树；平台节点和影子节点均只存在 JVM 测试中。

## 接口假设（4 条）

1. **data 枚举包名**：假设 `ProtectLevel`、`RewardedMode`、`SignalKind` 位于 `com.sentinel.data.db`。依据 `data/PLAN.md` Task 1 的 `db/Entities.kt` 文件与 Interfaces 枚举定义；计划未逐个指定枚举包名。测试只引用这些枚举，不推测 DAO 或仓库构造器。
2. **EffectiveConfig 包名**：假设 `EffectiveConfig` 位于 `com.sentinel.data.policy`。依据 `data/PLAN.md` Task 1 的 `policy/EffectivePolicy.kt` 和纯配置计算职责；Interfaces 没写清它是在此处还是 `db/Entities.kt` 中。构造参数严格采用计划列出的 11 项，全部传值，不假设任何默认值。
3. **适配器构造签名**：假设 `com.sentinel.a11y.service.NodeViewAdapter(node: AccessibilityNodeInfo) : NodeView`，可直接构造并读取 NodeView 的全部字段。依据 `engine-a11y/PLAN.md` Task 8 的同名文件和 `UT-AY-8-01` 树映射要求；计划没有 Produces 签名。未假设 `toNodeView()`、`adapt()`、`vid` 等额外 API。如果实现选择工厂/扩展函数，必须同步更新这一处测试。
4. **激励关闭动作映射**：假设 `RewardedAction.ClickClose` 在编排器出口表现为 `A11yAction.Click`。依据 Task 5 的 ClickClose 和 Task 7 的 A11yAction 集合（其中没有单独的激励关闭动作）。MT-AY-02 只断言节点及包名，不推测此点击的 ruleId/kind；静音时按 Task 7 的注释断言 `SetMuted(true)`。

实现方需采用上述约定，或同步修改测试；本次不保证尚不存在的产品代码编译通过。其他 core 类的构造器/方法参数完全照 engine-a11y PLAN；core-rules 的规则、索引、选择器和快照均按当前真实代码引用。

## 待确认（7 项）

1. **桌面既被忽略，又要记录启动来源**：Global Constraints 要忽略桌面，而 Task 1 说忽略包不更新上一个前台包，同时要求上一个包为桌面时 `fromLauncher=true`。按最合理理解：桌面不作为受处理的前台，但仍作为独立的来源标记。测试把 HOME 同时放入 ignored 和 launchers，要求桌面启动能识别；未要求桌面本身产生 Transition。实现方需明确该优先关系。
2. **Tracker 重置 launch 的时机**：Task 1 在前台包变化时重置 launch，Task 6 却要源 App 的 launch，Task 7 要先 Tracker 再 Guard。若 Guard 收到的是已经重置为目标的 Tracker，启动回退必然失效。JumpBackGuard 单测传入真实 Tracker 的源状态及显式 Transition，在更新目标前判定；MT-AY-03 则通过真实 A11yBrain 的输入顺序验证整体行为，保留此冲突造成的失败。需要实现方明确保存源启动信息/调整判定时序；测试没有发明额外访问器。
3. **中间桌面/最近任务与直接来源**：UT-AY-6-08/09 要求经过 HOME/systemui 后不回退，但 Task 1 忽略这两者、不更新上一个前台包，可能把下一次切换错误拼成 A→淘宝。两条 Guard 单测按 Task 6“必须直接切换”输入 HOME→淘宝/systemui→淘宝，并验证 null；没有声称能验证 Tracker 与服务如何生成该来源。Task 7/8 需要补明确的中间窗口处理规则，尤其是从已有 App 经通知栏或最近任务打开其他 App；只靠当前公开字段无法区分这些路径。
4. **“vid 截取”的含义**：UT-AY-8-01 写 vid 截取，但现有 NodeView 只有 viewId；真实 `core-rules/ui/Selector.kt` 的 `[vid=...]` 必须从包含 `:id/` 的完整 viewId 截取。测试断言适配器保留完整 `viewIdResourceName`，再用真实 Selector 验证 `[vid="close"]` 匹配。不增加虚构的 `vid` 属性；若让适配器直接截短，现有选择器将无法匹配。
5. **MT-AY-03 无法完整验证撤销执行器**：Task 7 的公开接口只有 `onInput`、`onTick`、`onRewardedChoice`，没有撤销入口；Task 8 只列了 ActionExecutor/Receiver 文件和行为，未给构造参数或可注入仓库接口，data 仓库也是未明确构造器的具体类。不能靠合法的当前接口驱动真实执行器并捕获 `JumpExceptionRepository.add` 和 `SignalRepository.emit(..., USER_UNDO, ruleId=null)`。现有测试验证真实 Brain 产生 Revert，再模拟相邻数据层已有例外，验证同目标不再回退且其他目标仍回退；**例外写入与 USER_UNDO 信号发射的真实执行路径尚未写出可运行断言**。需补执行器/撤销入口与依赖注入签名后，将此编号扩展为真实执行器 + 假仓库测试；不在测试中写一套假撤销实现冒充验证。
6. **激励关闭后同包恢复**：UT-AY-5-01 写关闭后恢复，但 Task 5 的明示恢复入口只有 `onForeground(其他包)` 和超时；未说明 ClickClose 之后何时结束等待、同包返回原页面怎么识别。当前 UT-AY-5-01 与 MT-AY-02 在 ClickClose 后模拟离开 App，并通过已给签名验证恢复；UT-AY-5-02 单独覆盖倒计时尚未结束就离开的恢复。需要确认同包关闭完成的结束条件，补充后同步收紧静默全流程断言。90 秒超时只断言 89,999ms 还等待、90,001ms 已恢复，不推断刚好 90,000ms 的处理。
7. **异常隔离空列表与已收集动作**：MT-AY-05 写返回空列表，Task 7 说保留已收集动作。当前制造配置快照异常，选择尚未收集任何动作的路径，断言空列表且不抛异常，再验证正常输入可以恢复；不要求晚发生的异常抹掉已有动作。后续若需要已收集动作的专门断言，应明确可注入失败点。

## 本次检查

静态核对 59 个计划编号，每个对应一个 `@Test` 函数；9 个测试类、1 个共享工具文件、12 个可解析 XML 资源；已从本地缓存的 Robolectric 4.17 JAR 核实 `ShadowAccessibilityNodeInfo.addChild(AccessibilityNodeInfo)` 存在。没有空测试、恒真断言、跳过注解、mockk/mockito、真实网络/手机调用或 sleep。断言完整性为 58/59，MT-AY-03 缺少的执行器断言已明确列出；其余待确认项按上面的解释编写，不代表已经消除了计划矛盾。
