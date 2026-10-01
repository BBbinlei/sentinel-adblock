# engine-notify Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用 NotificationListenerService 按规则清除营销通知，并从用户手动划掉通知的行为中学习新规则。

**Architecture:** Android library，包 `com.sentinel.notify`。判定（`NotifyFilter`）、学习（`DismissLearner`）与编排（`NotifyEngine`）都是纯 Kotlin；`SentinelNotificationListener` 只做适配与执行。

**Tech Stack:** NotificationListenerService、Kotlin 协程、Koin。

**Spec:** `docs/superpowers/specs/2026-10-01-adblock-sentinel-design.md`（5.4 节）；总调度见 `MASTER_PLAN.md`。

**Tests:** 全部测试定义在 `testing/unit/PLAN.md` 的「Task NT」（UT-NT-*、MT-NT-*）与 `testing/device-integration/PLAN.md`（DI-07、DI-41）。模块完成后执行关卡 **G6**。

## Global Constraints

见 `MASTER_PLAN.md`。本模块固定值：
- 学习阈值：7 天内 ≥3 次；拒绝后 30 天内不再询问；
- 通知渠道 id `notify-learn`；
- 内置关键词 `限时|福利|领取|优惠|红包|热门|免费|推荐`；
- 内置包 `com.heytap.market`、`com.heytap.themestore`、`com.nearme.gamecenter`、`com.heytap.browser`、`com.heytap.quicksearchbox`、`com.coloros.assistantscreen`（包名以实机核实报告为准，Task 1 实施时据报告修正）。

## 每个任务的通用步骤

按该任务列出的测试编号在 `engine-notify/src/test/` 下编写测试 → 运行确认失败 → 实现 → 重跑确认通过 → 提交。运行命令：`./gradlew :engine-notify:testDebugUnitTest --tests "<该任务测试类>"`。

---

### Task 1: 判定与内置规则

**Files:**
- Create: `engine-notify/src/main/kotlin/com/sentinel/notify/core/NotifyFilter.kt`
- Create: `engine-notify/src/main/kotlin/com/sentinel/notify/core/BuiltInNotifyRules.kt`

**Tests:** UT-NT-1-01～06

**Interfaces:**
- Consumes: `NotifyRule`、`NotifyMatcher`（core-rules）；`EffectiveConfig`、`ProtectLevel`（data）。
- Produces:
  ```kotlin
  data class PostedNotification(val pkg: String, val channelId: String?, val title: String?, val text: String?, val ongoing: Boolean, val key: String)
  object BuiltInNotifyRules { val all: List<NotifyRule> }      // 每个内置包一条，id: "builtin:notify:<pkg>"，keywords = 内置关键词
  object NotifyFilter { fun decide(n: PostedNotification, selfPkg: String, cfg: EffectiveConfig,
                                   rules: List<NotifyRule>, disabled: Set<String>): NotifyRule? }   // 返回命中的规则，null 表示不处理
  ```

判定：`n.pkg == selfPkg`、`n.ongoing`、`cfg.level == OFF`、`!cfg.notify` 任一成立 → null；否则返回第一条有效（`isValid`）、未被停用、且 `NotifyMatcher.matches` 的规则。

- [ ] **Step 1:** 编写 UT-NT-1-01～06。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(engine-notify): filter and built-in rules`。

### Task 2: 划除学习

**Files:**
- Create: `engine-notify/src/main/kotlin/com/sentinel/notify/core/DismissLearner.kt`

**Tests:** UT-NT-2-01～05

**Interfaces:**
- Produces:
  ```kotlin
  interface LearnerStore { fun load(): String?; fun save(json: String) }       // 持久化计数与拒绝记录
  class DismissLearner(store: LearnerStore, clock: () -> Long) {
      fun onUserDismissed(pkg: String, channelId: String?): NotifyRule?       // 达到阈值且不在拒绝期 → 候选规则（keywords 为空）
      fun onRejected(pkg: String, channelId: String?) }
  ```
  候选规则 id：`learn:notify:<pkg>:<channelId>`；`channelId` 为 null 时不生成（只按包过滤，误伤风险太大）。

- [ ] **Step 1:** 编写 UT-NT-2-01～05。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(engine-notify): dismiss learner`。

### Task 3: 编排器 NotifyEngine

**Files:**
- Create: `engine-notify/src/main/kotlin/com/sentinel/notify/core/NotifyEngine.kt`

**Tests:** MT-NT-01～02

**Interfaces:**
- Consumes: Task 1–2。
- Produces:
  ```kotlin
  sealed interface NotifyAction {
      data class Cancel(val key: String, val pkg: String, val ruleId: String, val title: String?) : NotifyAction
      data class AskLearn(val rule: NotifyRule) : NotifyAction
  }
  interface NotifyState { fun cfg(pkg: String): EffectiveConfig; fun disabled(pkg: String): Set<String>; fun rules(): List<NotifyRule> }   // rules = 内置 + 用户
  class NotifyEngine(selfPkg: String, state: NotifyState, learner: DismissLearner) {
      fun onPosted(n: PostedNotification): NotifyAction?
      fun onUserDismissed(pkg: String, channelId: String?): NotifyAction?
      fun onLearnRejected(pkg: String, channelId: String?) }
  ```

- [ ] **Step 1:** 编写 MT-NT-01～02。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(engine-notify): notify engine`。

### Task 4: 服务接线

**Files:**
- Create: `engine-notify/src/main/kotlin/com/sentinel/notify/service/SentinelNotificationListener.kt`
- Create: `engine-notify/src/main/kotlin/com/sentinel/notify/service/NotifyLearnReceiver.kt`
- Create: `engine-notify/src/main/kotlin/com/sentinel/notify/di/NotifyModule.kt`
- Modify: `engine-notify/src/main/AndroidManifest.xml`（`BIND_NOTIFICATION_LISTENER_SERVICE`）

**Tests:** UT-NT-4-01

**Interfaces:**
- Consumes: Task 3；data 的 `RuleStore.loadNotifyRules`、`AppConfigRepository`、`OverrideRepository`、`EventRepository`、`EngineStatusRepository`、`UserRuleRepository`、`SubscriptionUpdater.rebuildFromCache`、`GlobalStateRepository`（ruleVersion）。
- Produces: `fun StatusBarNotification.toPosted(): PostedNotification`；动作 `com.sentinel.notify.LEARN_ACCEPT` / `LEARN_REJECT`（extra `ruleJson`）。

接线：
- `onListenerConnected` 上报 `RUNNING`，`onListenerDisconnected` 上报 `STOPPED`；
- 规则 = `BuiltInNotifyRules.all` + `loadNotifyRules()`，随 ruleVersion 热更新；
- `Cancel` → `cancelNotification(key)` + 事件 `NOTIFICATION_CANCELLED`（detail = 标题）；
- `onNotificationRemoved(sbn, map, reason)` 中 `reason == REASON_CANCEL` 时调用 `onUserDismissed`，得到 `AskLearn` 就发询问通知；接受 → `UserRuleRepository.add` + `rebuildFromCache()`；拒绝 → `onLearnRejected`；
- 所有回调都捕获异常。

- [ ] **Step 1:** 编写 UT-NT-4-01。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现接线。
- [ ] **Step 4:** 重跑，期望 PASS；`./gradlew :engine-notify:assembleDebug` 成功。
- [ ] **Step 5:** 提交 `feat(engine-notify): listener service wiring`。

### 模块完成 → 关卡 G6

- [ ] 执行 `testing/README.md` 中 G6 的全部项目（含 DI-07、DI-41），写关卡报告。
