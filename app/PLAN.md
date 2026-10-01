# app Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用 Compose 实现引导向导、首页、系统净化、应用、规则页面和快捷开关，并完成全局接线。

**Architecture:** Android application，包 `com.sentinel.app`。每个页面 = `Screen`（无状态 Composable）+ `ViewModel`（只依赖 data 仓库和各模块暴露的少量入口）。ViewModel 暴露 `StateFlow<UiState>`。

**Tech Stack:** Jetpack Compose、Material 3（dynamic color）、Navigation Compose、Lifecycle ViewModel、Koin（`koin-androidx-compose`）。

**Spec:** `docs/superpowers/specs/2026-10-01-adblock-sentinel-design.md`（4 节）；总调度见 `MASTER_PLAN.md`（M3 做 Task 1–2，M8 做 Task 3–7）。

**Tests:** 全部测试定义在 `testing/unit/PLAN.md` 的「Task AP」（UT-AP-*、MT-AP-*）与 `testing/device-integration/PLAN.md`（DI-51、DI-52、DI-61）。Task 1–2 完成后参与关卡 **G3**；全部完成后执行关卡 **G8**。

## Global Constraints

见 `MASTER_PLAN.md`。本模块额外约束：
- 文案全部放 `strings.xml`（简体中文）。
- 所有可点击元素最小触控尺寸 48dp；字体放大 1.3 倍时不出现截断与重叠。
- 「临时放行」从应用列表起最多 2 次点击完成（1 次打开详情 + 1 次点击按钮），无确认弹窗。
- 高级选项默认折叠。
- 卸载类系统操作必须二次确认；其他操作不弹确认（靠撤销兜底）。

## 每个任务的通用步骤

按该任务列出的测试编号在 `app/src/test/` 下编写测试 → 运行确认失败 → 实现 → 重跑确认通过 → 提交。运行命令：`./gradlew :app:testDebugUnitTest --tests "<该任务测试类>"`。

---

### Task 1: 应用骨架与全局接线

**Files:**
- Modify: `app/src/main/kotlin/com/sentinel/app/SentinelApp.kt`
- Create: `app/src/main/kotlin/com/sentinel/app/MainActivity.kt`
- Create: `app/src/main/kotlin/com/sentinel/app/ui/theme/Theme.kt`
- Create: `app/src/main/kotlin/com/sentinel/app/ui/nav/SentinelNavHost.kt`
- Create: `app/src/main/kotlin/com/sentinel/app/di/AppModule.kt`
- Create: `app/src/main/kotlin/com/sentinel/app/di/ModuleLoader.kt`
- Create: `app/proguard-rules.pro`（保留 `ModuleEntry` 的所有实现类）

**Tests:** UT-AP-1-01～03（UT-AP-1-04 在 M8 全部入口到齐后补跑）

**Interfaces:**
- Consumes: `dataModule`、`ModuleEntry`、`ProcessKind`（data）。**不直接引用任何引擎或 guard 模块的类**。
- Produces: 路由常量 `Routes.ONBOARDING`、`HOME`、`APPS`、`APP_DETAIL/{pkg}`、`RULES`、`SYSTEM_CLEANUP`、`ENGINE_LOG/{engine}`、`OP_LOG`；`SentinelTheme { }`；`object VpnStarter { fun intentToPrepare(ctx): Intent?; fun start(ctx) }`；
  ```kotlin
  object ModuleLoader { fun load(process: ProcessKind, loader: ClassLoader = ...): List<ModuleEntry> }   // ServiceLoader 发现，按 processes 过滤，id 重复则抛异常
  ```

`SentinelApp.onCreate`：
1. 用 `Application.getProcessName()` 判断进程（以 `:vpn` 结尾为 `VPN`，否则 `MAIN`）；
2. `entries = ModuleLoader.load(process)`；`startKoin { modules(dataModule + entries.map { it.koinModule }) }`；
3. 对每个入口调用 `start(context, appScope, koin)`（单个入口失败只记日志，不影响其他入口与界面）；
4. 仅 `MAIN` 进程：首次运行时 `AppRegistry.syncInstalled(initial = true)`，之后每次启动 `syncInstalled(initial = false)`。

app **不再**创建通知渠道、不再启动 `GuardRunner`/`AppOpsSync`/`SystemStatusReporter`、不再注册周期任务——这些全部由各模块的 `ModuleEntry.start` 负责（见 `docs/CONTRACTS.md` C5）。因此 guard 模块在 M7 之前不存在入口也不影响 app 启动。`:vpn` 进程只启动 `data` 与 `vpn` 入口对应的内容。界面是 3 个底部页签（首页 / 应用 / 规则），本任务先放占位页。

- [ ] **Step 1:** 编写 UT-AP-1-01～03。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现骨架。
- [ ] **Step 4:** 重跑，期望 PASS；`./gradlew :app:assembleDebug` 成功。
- [ ] **Step 5:** 提交 `feat(app): skeleton, theme, navigation and module loader`。

### Task 2: 最小首页（M3 可用版本）

**Files:**
- Create: `app/src/main/kotlin/com/sentinel/app/home/HomeViewModel.kt`
- Create: `app/src/main/kotlin/com/sentinel/app/home/HomeScreen.kt`

**Tests:** UT-AP-2-01～03

**Interfaces:**
- Consumes: `GlobalStateRepository`、`EventRepository.observeTodayCount`、`EngineStatusRepository.observeAll`（data）；`VpnStarter`（Task 1）。
- Produces:
  ```kotlin
  enum class ShieldState { PROTECTING, PAUSED, OFF }
  data class EngineRow(val engine: EngineId, val state: EngineState, val message: String?)
  data class HomeUiState(val shield: ShieldState, val todayBlocked: Int, val engines: List<EngineRow>,
                         val warnings: List<String>, val guardAlerts: List<GuardAlert>)   // GuardAlert 在 Task 4 定义，本任务为空列表
  class HomeViewModel : ViewModel { val state: StateFlow<HomeUiState>; fun onShieldTapped() }
  ```

`onShieldTapped`：PROTECTING → `setEnabled(false)`；OFF/PAUSED → `setEnabled(true)` + `resume()`，需要时由界面发起 VPN 授权。界面：大盾牌 + 今日拦截 + 四行引擎状态。

- [ ] **Step 1:** 编写 UT-AP-2-01～03。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(app): minimal home screen`。

### Task 3: 引导向导

**Files:**
- Create: `app/src/main/kotlin/com/sentinel/app/onboarding/SetupChecker.kt`
- Create: `app/src/main/kotlin/com/sentinel/app/onboarding/OnboardingViewModel.kt`
- Create: `app/src/main/kotlin/com/sentinel/app/onboarding/OnboardingScreen.kt`
- Create: `app/src/main/res/drawable/onboarding_*.xml`（5 张步骤图示，矢量图；无障碍步骤的图示按 DI-21 记录的 ColorOS 实际路径绘制）

**Tests:** UT-AP-3-01～03

**Interfaces:**
- Consumes: `ShizukuGateway.state/requestPermission`（engine-system）；`VpnStarter`（Task 1）。
- Produces:
  ```kotlin
  enum class SetupStep { SHIZUKU, VPN, ACCESSIBILITY, NOTIFICATION, BATTERY }
  interface SetupChecker { fun isDone(step: SetupStep): Boolean; fun settingsIntent(step: SetupStep): Intent }
  data class OnboardingUiState(val current: SetupStep, val done: Set<SetupStep>, val finished: Boolean)
  class OnboardingViewModel : ViewModel { val state: StateFlow<OnboardingUiState>; fun onResume(); fun next(); fun skip() }
  ```

完成判定：
| 步骤 | 判定 |
|---|---|
| SHIZUKU | `ShizukuGateway.state() == READY` |
| VPN | `VpnService.prepare(ctx) == null` |
| ACCESSIBILITY | `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` 含本服务组件 |
| NOTIFICATION | `NotificationManagerCompat.getEnabledListenerPackages` 含自身 |
| BATTERY | `PowerManager.isIgnoringBatteryOptimizations` |

VPN 步骤说明中建议开启「始终开启的 VPN」，并明确提示**不要**开启「屏蔽未使用 VPN 的连接」。向导完成或全部跳过后写入 `onboarding_done`（SharedPreferences），之后启动直接进首页；首页对未完成的步骤显示「去完成」。界面：顶部进度点 ●○○○○、图示、说明、「去设置」「跳过」。

- [ ] **Step 1:** 编写 UT-AP-3-01～03。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(app): onboarding wizard`。

### Task 4: 完整首页、引擎日志、系统净化与撤销记录

**Files:**
- Modify: `app/src/main/kotlin/com/sentinel/app/home/HomeViewModel.kt`、`HomeScreen.kt`
- Create: `app/src/main/kotlin/com/sentinel/app/log/EngineLogViewModel.kt`、`EngineLogScreen.kt`
- Create: `app/src/main/kotlin/com/sentinel/app/system/SystemCleanupViewModel.kt`、`SystemCleanupScreen.kt`
- Create: `app/src/main/kotlin/com/sentinel/app/system/OpLogViewModel.kt`、`OpLogScreen.kt`

**Tests:** UT-AP-4-01～05

**Interfaces:**
- Consumes: `OverrideRepository.observeRecent`、`EventRepository.observeRecent`、`OpLogRepository`（data）；`GuardRunner.undo`（guard）；`OpExecutor`、`ColorOsProfile`、`ShizukuGateway`（engine-system）。
- Produces:
  ```kotlin
  data class GuardAlert(val pkg: String, val label: String, val ruleIds: List<String>, val reason: String, val at: Long)
  data class CleanupItem(val op: ProfileOp, val status: OpStatus, val canAuto: Boolean)
  class SystemCleanupViewModel : ViewModel { val state: StateFlow<List<CleanupItem>>; fun applyAll(); fun apply(id: String)
      fun openSettings(id: String): Intent?; fun undo(logId: Long); fun undoAll() }
  ```

引擎日志对应的事件类型：
| 引擎 | 事件 |
|---|---|
| VPN | `DNS_BLOCKED`、`HTTPDNS_REJECTED`、`WOULD_BLOCK` |
| A11Y | `SPLASH_SKIPPED`、`POPUP_CLOSED`、`REWARDED_SILENCED`、`JUMP_REVERTED`、`AUTO_RENEW_WARNED` |
| NOTIFY | `NOTIFICATION_CANCELLED`（点击可打开对应 App） |
| SYSTEM | `SYSTEM_OP` |

`applyAll` 跳过 optional 项与 UNINSTALL 项；卸载项点击时弹二次确认对话框。

- [ ] **Step 1:** 编写 UT-AP-4-01～05。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(app): full home, logs, system cleanup and op log`。

### Task 5: 应用页与应用详情

**Files:**
- Create: `app/src/main/kotlin/com/sentinel/app/apps/AppsViewModel.kt`、`AppsScreen.kt`
- Create: `app/src/main/kotlin/com/sentinel/app/apps/AppDetailViewModel.kt`、`AppDetailScreen.kt`

**Tests:** UT-AP-5-01～05

**Interfaces:**
- Consumes: `AppConfigRepository`、`EventRepository.observeCountByPkg/observeRecent`（data）；`EffectivePolicy`。
- Produces:
  ```kotlin
  data class AppRow(val pkg: String, val label: String, val blocked7d: Int, val level: ProtectLevel, val defaultAllowed: Boolean)
  class AppsViewModel : ViewModel { val state: StateFlow<List<AppRow>>; fun search(q: String) }   // 按 blocked7d 降序
  data class AppDetailUiState(val label: String, val level: ProtectLevel, val observingDaysLeft: Int?,
      val wouldBlock: List<String>, val config: AppConfigEntity, val tempAllowedUntil: Long?)
  class AppDetailViewModel(pkg: String) : ViewModel { val state: StateFlow<AppDetailUiState>
      fun setLevel(l: ProtectLevel); fun setRewarded(m: RewardedMode); fun toggle(field: AppToggle, on: Boolean); fun tempAllow() }
  enum class AppToggle { SPLASH, SHAKE, JUMP_BACK, NOTIFY, LIMIT_OVERLAY, DENY_CLIPBOARD }
  ```
  三档级别用 `SegmentedButton`；高级区默认折叠；「一键临时放行」按钮放在详情页首屏可见位置。

- [ ] **Step 1:** 编写 UT-AP-5-01～05。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(app): apps list and detail`。

### Task 6: 规则页

**Files:**
- Create: `app/src/main/kotlin/com/sentinel/app/rules/RulesViewModel.kt`、`RulesScreen.kt`

**Tests:** UT-AP-6-01～03

**Interfaces:**
- Consumes: 订阅 DAO 的 Flow、`UserRuleRepository`、`SubscriptionUpdater`（data）。
- Produces: `class RulesViewModel : ViewModel { val state: StateFlow<RulesUiState>; fun toggle(id: Long, on: Boolean); fun add(name: String, url: String, format: SubscriptionFormat); fun updateNow(); fun deleteUserRule(id: String) }`

- [ ] **Step 1:** 编写 UT-AP-6-01～03。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(app): rules screen`。

### Task 7: 快捷开关

**Files:**
- Create: `app/src/main/kotlin/com/sentinel/app/tile/PauseTileService.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Tests:** UT-AP-7-01～02

**Interfaces:**
- Consumes: `GlobalStateRepository.pauseFor/resume/observe`。

行为：点击时，未暂停 → `pauseFor(300_000)`，已暂停 → `resume()`；磁贴副标题显示「已暂停，mm:ss 后恢复」或「防护中」。

- [ ] **Step 1:** 编写 UT-AP-7-01～02。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(app): pause quick settings tile`。

### 模块完成 → 关卡 G8

- [ ] 按 `testing/unit/PLAN.md` 编写 MT-AP-01～05；执行 `testing/README.md` 中 G8 的全部项目（含 DI-52 全流程走查、DI-61 故障演练），写关卡报告。
