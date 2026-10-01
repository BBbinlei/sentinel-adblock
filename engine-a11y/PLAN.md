# engine-a11y Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 基于 AccessibilityService 实现开屏跳过、弹窗关闭、激励视频静默播完、跳转回退、学习模式与误伤探测。

**Architecture:** Android library，包 `com.sentinel.a11y`。所有判断逻辑在 `core` 包内，是纯 Kotlin 的状态机或函数（输入为 `NodeView` 与事件数据类），由 `A11yBrain` 统一编排；`service` 包把 `AccessibilityEvent`/`AccessibilityNodeInfo` 适配成这些输入，并执行 `A11yBrain` 返回的动作（点击、启动 Activity、显示悬浮提示、写数据）。

**Tech Stack:** AccessibilityService、AudioManager、Kotlin 协程、Koin。

**Spec:** `docs/superpowers/specs/2026-10-01-adblock-sentinel-design.md`（5.3、6 节）；总调度见 `MASTER_PLAN.md`「设计补充」第 1、2、7、8 条。

**Tests:** 全部测试定义在 `testing/unit/PLAN.md` 的「Task AY」（UT-AY-*、MT-AY-*）、`testing/rule-regression/PLAN.md`（RR-02、RR-03）、`testing/device-integration/PLAN.md`（DI-05、DI-21～25）。模块完成后执行关卡 **G4**。调试版快照录制工具 `SnapshotDumper` 的定义见 `testing/rule-regression/PLAN.md` Task 1。

## Global Constraints

见 `MASTER_PLAN.md`。本模块固定值：
- 启动窗口 5000ms；同一规则每次启动最多点击 3 次，两次间隔 ≥1000ms；内容变化防抖 100ms。
- 激励视频：静默超时 90s；奖励窗口 60s。
- 摇一摇判定：摇一摇提示出现后 3000ms 内发生的跳转。
- 学习模式：弹窗出现后 3000ms 内的用户关闭点击。
- 冷启动循环：60s 内 ≥3 次，同一 App 10 分钟内只上报一次。
- 跳转回退事件的 ruleId 固定为 `builtin:jumpback`；悬浮提示显示 3000ms。
- 忽略的包：自身、`com.android.systemui`、当前输入法、桌面（通过 `ACTION_MAIN + CATEGORY_HOME` 查询得到的包集合）。
- 陷阱词：`下载|安装|确认|立即|打开|去看看`。

## 每个任务的通用步骤

按该任务列出的测试编号在 `engine-a11y/src/test/` 下编写测试 → 运行确认失败 → 实现 → 重跑确认通过 → 提交。运行命令：`./gradlew :engine-a11y:testDebugUnitTest --tests "<该任务测试类>"`。

---

### Task 1: 前台跟踪与启动识别

**Files:**
- Create: `engine-a11y/src/main/kotlin/com/sentinel/a11y/core/ForegroundTracker.kt`

**Tests:** UT-AY-1-01～05

**Interfaces:**
- Produces:
  ```kotlin
  sealed interface UiInput {
      data class WindowChanged(val pkg: String, val activity: String?, val ts: Long) : UiInput   // activity 为 null 表示弹窗/非 Activity 窗口
      data class Interaction(val pkg: String, val ts: Long) : UiInput                           // 点击/长按/滚动
      data class Clicked(val pkg: String, val text: String?, val desc: String?, val viewId: String?, val className: String?, val ts: Long) : UiInput
  }
  data class LaunchInfo(val pkg: String, val startedAt: Long, val fromLauncher: Boolean)
  data class Transition(val from: String, val to: String, val ts: Long)
  class ForegroundTracker(ignored: () -> Set<String>, launchers: () -> Set<String>) {
      fun onInput(i: UiInput): Transition?          // 前台包变化时返回
      val currentPkg: String?; val currentActivity: String?
      val launch: LaunchInfo?                       // 当前前台 App 的启动信息
      fun lastInteractionAt(pkg: String): Long?
      fun lastWindowAt(pkg: String): Long?
      fun launchesWithin(pkg: String, windowMs: Long, now: Long): Int
  }
  ```

规则：切换到忽略包不算前台变化，也不更新「上一个前台包」；新前台包的上一个前台包是桌面时 `fromLauncher = true`；`launch` 在前台包变化时重置；`Clicked` 同时计为 `Interaction`。

- [ ] **Step 1:** 编写 UT-AY-1-01～05。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(engine-a11y): foreground tracker`。

### Task 2: 规则点击（开屏 / 弹窗 / 自动续费）

**Files:**
- Create: `engine-a11y/src/main/kotlin/com/sentinel/a11y/core/RuleClicker.kt`
- Create: `engine-a11y/src/main/kotlin/com/sentinel/a11y/core/ClickThrottle.kt`

**Tests:** UT-AY-2-01～08

**Interfaces:**
- Consumes: `UiRuleIndex`、`CompiledUiRule`、`NodeView`、`UiAction`、`UiPhase`、`BuiltInPatterns`、`EventKind`（core-rules）；`EffectiveConfig`、`ProtectLevel`（data）。
- Produces:
  ```kotlin
  sealed interface ClickDecision {
      data class Click(val node: NodeView, val ruleId: String, val kind: EventKind) : ClickDecision   // SPLASH_SKIPPED / POPUP_CLOSED
      data class WarnAutoRenew(val ruleId: String) : ClickDecision
      data class RewardedPage(val ruleId: String) : ClickDecision
  }
  class ClickThrottle(clock: () -> Long) { fun allow(launchKey: String, ruleId: String): Boolean; fun reset(launchKey: String) }
  object RuleClicker {
      fun decide(root: NodeView, pkg: String, activity: String?, inLaunchWindow: Boolean, cfg: EffectiveConfig,
                 disabled: Set<String>, index: UiRuleIndex, throttle: ClickThrottle, launchKey: String): ClickDecision?
  }
  ```

判定：`cfg.level == OFF` → null；按 `index.lookup` 的顺序遍历，跳过 `disabled` 中的规则；LAUNCH 阶段的规则只在 `inLaunchWindow && cfg.splash` 时生效（命中记 SPLASH_SKIPPED，ANYTIME 命中记 POPUP_CLOSED）；CLICK 命中、且节点文字不匹配陷阱词时返回 `Click`（受 `ClickThrottle` 约束）；REWARDED_HANDLE 命中返回 `RewardedPage`；NOTIFY_AUTORENEW 命中、且树中存在匹配 `BuiltInPatterns.autoRenew` 的文字时返回 `WarnAutoRenew`。

- [ ] **Step 1:** 编写 UT-AY-2-01～08。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(engine-a11y): rule clicker`。

### Task 3: 学习模式候选规则

**Files:**
- Create: `engine-a11y/src/main/kotlin/com/sentinel/a11y/core/LearningRecorder.kt`

**Tests:** UT-AY-3-01～06

**Interfaces:**
- Consumes: `UiInput.Clicked`（Task 1）；`UiRule`、`RuleScope`、`BuiltInPatterns.closeLike`（core-rules）。
- Produces: `class LearningRecorder(clock: () -> Long) { fun onClicked(c: UiInput.Clicked, activity: String?, windowAppearedAt: Long?, autoClickedInWindow: Boolean): UiRule? }`

规则：文字或描述匹配 `closeLike`、`c.ts - windowAppearedAt <= 3000`、本窗口未发生过自动点击 → 生成规则：
- 作用域：`Page(pkg, activity)`；activity 为 null 时用 `App(pkg)`；
- 选择器：优先 `[vid="<viewId 去掉前缀>"]`，否则 `<类名简称>[text="<文字>"]`，再否则 `[desc="<描述>"]`；
- 动作 CLICK、阶段 ANYTIME；id = `learn:<pkg>:<选择器 SHA-1 前 8 位>`；source = `learned`。

- [ ] **Step 1:** 编写 UT-AY-3-01～06。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(engine-a11y): learning recorder`。

### Task 4: 误伤探测信号

**Files:**
- Create: `engine-a11y/src/main/kotlin/com/sentinel/a11y/core/HealthSignals.kt`

**Tests:** UT-AY-4-01～03

**Interfaces:**
- Consumes: `SignalKind`（data）。
- Produces:
  ```kotlin
  data class HealthSignal(val pkg: String, val kind: SignalKind, val detail: String?)
  class HealthSignals(clock: () -> Long, labelToPkg: (String) -> String?) {
      fun onWindowText(windowPkg: String, texts: List<String>): HealthSignal?   // 崩溃弹窗
      fun onLaunch(pkg: String, launchesIn60s: Int): HealthSignal?              // 冷启动循环
  }
  ```

规则：`windowPkg == "android"` 且文字匹配 `(.+?)(已停止运行|屡次停止运行|没有响应|已停止)` 时，用捕获的应用名经 `labelToPkg` 找到包名 → `CRASH_DIALOG`；`launchesIn60s >= 3` → `COLD_START_LOOP`；同一 (pkg, kind) 10 分钟内只返回一次。

- [ ] **Step 1:** 编写 UT-AY-4-01～03。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(engine-a11y): health signals`。

### Task 5: 激励视频处理与音量保护

**Files:**
- Create: `engine-a11y/src/main/kotlin/com/sentinel/a11y/core/RewardedHandler.kt`
- Create: `engine-a11y/src/main/kotlin/com/sentinel/a11y/core/VolumeKeeper.kt`

**Tests:** UT-AY-5-01～07

**Interfaces:**
- Consumes: `UiInput.Clicked`（Task 1）；`BuiltInPatterns.rewardEntry/closeLike`（core-rules）；`RewardedMode`（data）。
- Produces:
  ```kotlin
  interface VolumePort { fun get(): Int; fun set(v: Int) }                     // STREAM_MUSIC
  interface KeyValueStore { fun getInt(k: String): Int?; fun putInt(k: String, v: Int); fun remove(k: String) }
  class VolumeKeeper(port: VolumePort, store: KeyValueStore) { fun muteAndSave(); fun restore(); fun restoreIfPending() }
  sealed interface RewardedAction {
      data object OpenRewardWindow : RewardedAction
      data object AskUser : RewardedAction
      data class ClickClose(val node: NodeView) : RewardedAction
      data object Finished : RewardedAction
  }
  class RewardedHandler(keeper: VolumeKeeper, clock: () -> Long) {
      fun onClicked(c: UiInput.Clicked, mode: RewardedMode): RewardedAction?        // 匹配 rewardEntry 且 mode != BLOCK → OpenRewardWindow
      fun onRewardedPage(pkg: String, mode: RewardedMode): RewardedAction?          // SILENT → 静音并进入等待；ASK → AskUser
      fun onUserChoice(silent: Boolean)                                             // ASK 小窗的选择
      fun onContent(pkg: String, root: NodeView): RewardedAction?                   // 等待中：没有倒计时文字且找到关闭节点 → ClickClose
      fun onForeground(pkg: String): RewardedAction?                                // 离开该 App → restore → Finished
      fun onTick(): RewardedAction?                                                 // 超过 90s → restore → Finished
  }
  ```

倒计时文字：`\d+\s*[sS秒]`。关闭节点：文字/描述匹配 `closeLike`，或描述为 `关闭|close`（不区分大小写）。`VolumeKeeper` 的存档键为 `saved_music_volume`；`restore()` 后删除存档；`restoreIfPending()` 在有存档时恢复。

- [ ] **Step 1:** 编写 UT-AY-5-01～07。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(engine-a11y): rewarded handler and volume keeper`。

### Task 6: 跳转回退

**Files:**
- Create: `engine-a11y/src/main/kotlin/com/sentinel/a11y/core/JumpBackGuard.kt`

**Tests:** UT-AY-6-01～24

**Interfaces:**
- Consumes: `ForegroundTracker`、`Transition`、`LaunchInfo`（Task 1）；`JumpTargetCatalog`（core-rules）；`EffectiveConfig`（data）。
- Produces:
  ```kotlin
  data class RevertJump(val source: String, val target: String, val reason: String)   // reason: "launch" | "shake"
  class JumpBackGuard(clock: () -> Long) {
      fun onShakeHintSeen(pkg: String)
      fun onTransition(t: Transition, tracker: ForegroundTracker, cfgOf: (String) -> EffectiveConfig,
                       disabledOf: (String) -> Set<String>, excepted: (String, String) -> Boolean): RevertJump?
  }
  ```

判定（`t.from` 为源、`t.to` 为目标，必须是直接切换）：源的生效级别为 OFF、`builtin:jumpback` 在源的停用集合中、或 `excepted(源, 目标)` → null。否则满足以下任一即回退：
- **launch**：`cfg.jumpBack`，且 `tracker.launch` 属于源、`fromLauncher`、`t.ts - startedAt <= 5000`、启动后源内无交互、`JumpTargetCatalog.isAdLanding(目标)`。
- **shake**：`cfg.shake`，且源在 `t.ts` 前 3000ms 内出现过摇一摇提示，此后源内无交互。

- [ ] **Step 1:** 编写 UT-AY-6-01～24。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(engine-a11y): jump-back guard`。

### Task 7: 编排器 A11yBrain

**Files:**
- Create: `engine-a11y/src/main/kotlin/com/sentinel/a11y/core/A11yBrain.kt`

**Tests:** MT-AY-01～05

**Interfaces:**
- Consumes: Task 1–6。
- Produces:
  ```kotlin
  sealed interface A11yAction {
      data class Click(val node: NodeView, val pkg: String, val ruleId: String, val kind: EventKind) : A11yAction
      data class Revert(val jump: RevertJump) : A11yAction
      data class OpenRewardWindow(val pkg: String) : A11yAction
      data class AskRewarded(val pkg: String) : A11yAction
      data class SetMuted(val muted: Boolean) : A11yAction          // 由 VolumeKeeper 实际执行，此动作仅用于记录事件 REWARDED_SILENCED
      data class WarnAutoRenew(val pkg: String) : A11yAction
      data class ProposeRule(val rule: UiRule) : A11yAction
      data class Signal(val signal: HealthSignal) : A11yAction
  }
  interface A11yState {            // 由服务从数据层快照提供
      fun cfg(pkg: String): EffectiveConfig; fun disabled(pkg: String): Set<String>
      fun excepted(src: String, tgt: String): Boolean; fun index(): UiRuleIndex
  }
  class A11yBrain(state: A11yState, rewarded: RewardedHandler, clock: () -> Long,
                  ignored: () -> Set<String>, launchers: () -> Set<String>, labelToPkg: (String) -> String?) {
      fun onInput(i: UiInput, root: NodeView?): List<A11yAction>
      fun onTick(): List<A11yAction>
      fun onRewardedChoice(silent: Boolean)
  }
  ```

行为：`onInput` 依次交给 Tracker → JumpBackGuard（Transition 时）→ HealthSignals → RewardedHandler → RuleClicker → LearningRecorder，汇总动作；`root` 中出现 `shakeHint` 文字时调用 `onShakeHintSeen`。**任一子组件抛异常时，捕获后返回已收集的动作，不向外抛出。**

- [ ] **Step 1:** 编写 MT-AY-01～05。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(engine-a11y): a11y brain orchestrator`。

### Task 8: 服务接线、悬浮提示与通知动作

**Files:**
- Create: `engine-a11y/src/main/kotlin/com/sentinel/a11y/service/SentinelAccessibilityService.kt`
- Create: `engine-a11y/src/main/kotlin/com/sentinel/a11y/service/NodeViewAdapter.kt`
- Create: `engine-a11y/src/main/kotlin/com/sentinel/a11y/service/ActionExecutor.kt`（执行 `A11yAction`）
- Create: `engine-a11y/src/main/kotlin/com/sentinel/a11y/service/OverlayToast.kt`（撤销提示、激励询问小窗，`TYPE_ACCESSIBILITY_OVERLAY`）
- Create: `engine-a11y/src/main/kotlin/com/sentinel/a11y/service/A11yNotifications.kt`（学习确认、自动续费提醒）
- Create: `engine-a11y/src/main/kotlin/com/sentinel/a11y/service/A11yActionReceiver.kt`
- Create: `engine-a11y/src/main/res/xml/sentinel_accessibility.xml`
- Create: `engine-a11y/src/main/kotlin/com/sentinel/a11y/di/A11yModule.kt`
- Modify: `engine-a11y/src/main/AndroidManifest.xml`

**Tests:** UT-AY-8-01

**Interfaces:**
- Consumes: Task 1–7；data 的 `RuleStore.loadUiIndex`、`GlobalStateRepository`（ruleVersion）、`AppConfigRepository`、`OverrideRepository`、`JumpExceptionRepository`、`RewardWindowRepository`、`EventRepository`、`SignalRepository`、`EngineStatusRepository`、`UserRuleRepository`、`SubscriptionUpdater.rebuildFromCache`。
- Produces: `A11yActions` 常量：`LEARN_ACCEPT`（extra `ruleJson`）、`LEARN_REJECT`、`JUMP_UNDO`（extra `source`、`target`）。

配置：`accessibilityEventTypes = typeWindowStateChanged|typeWindowContentChanged|typeViewClicked|typeViewScrolled|typeViewLongClicked`；`canRetrieveWindowContent=true`；`accessibilityFlags = flagReportViewIds|flagIncludeNotImportantViews`；`notificationTimeout=100`。

接线要点：
- `onServiceConnected`：`VolumeKeeper.restoreIfPending()`、上报 `RUNNING`、加载索引并订阅 ruleVersion 热更新；`onUnbind`/`onDestroy` 上报 `STOPPED`。
- 事件 → `UiInput` → `A11yBrain.onInput`；`WindowChanged` 的 activity 用 `PackageManager.getActivityInfo` 校验是否真的是 Activity；当前包无规则且不在启动窗口、激励等待、摇一摇跟踪中时不获取节点树（`root` 传 null）。
- `Click`：对节点或最近的可点击祖先 `performAction(ACTION_CLICK)`，失败则在节点中心用 `dispatchGesture` 点击；记事件。
- `Revert`：`getLaunchIntentForPackage(source)` + `FLAG_ACTIVITY_NEW_TASK` 启动，抛异常则 `performGlobalAction(GLOBAL_ACTION_BACK)`；记 `JUMP_REVERTED`（ruleId `builtin:jumpback`）；显示撤销提示。撤销：启动目标 App、`JumpExceptionRepository.add`、`SignalRepository.emit(source, USER_UNDO)`（ruleId 为空）。
- `OpenRewardWindow` → `RewardWindowRepository.open(pkg)`；`AskRewarded` → 小窗「静默播完 / 正常观看 / ☐记住」，勾选记住且选静默时 `setRewarded(pkg, SILENT)`。
- `ProposeRule` → 通知「刚才关闭的弹窗，以后自动关？」；接受时 `UserRuleRepository.add(rule, LEARNED)`，然后 `rebuildFromCache()`。
- `Signal` → `SignalRepository.emit`。
- 每秒调用一次 `A11yBrain.onTick()`（仅在激励等待中）。
- 任意回调内的异常都要捕获并记日志，不允许抛出导致服务崩溃。

- [ ] **Step 1:** 编写 UT-AY-8-01。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现以上全部接线。
- [ ] **Step 4:** 重跑，期望 PASS；`./gradlew :engine-a11y:assembleDebug` 成功。
- [ ] **Step 5:** 提交 `feat(engine-a11y): service wiring, overlays and actions`。

### 模块完成 → 关卡 G4

- [ ] 完成 `testing/rule-regression/PLAN.md` Task 1 的快照录制工具与 Task 3（RR-02、RR-03）；执行 `testing/README.md` 中 G4 的全部项目（含 DI-05、DI-21～25），写关卡报告。DI-22 若显示后台无法 `startActivity`，在报告中记录，并确认回退到「返回键」的效果可接受。
