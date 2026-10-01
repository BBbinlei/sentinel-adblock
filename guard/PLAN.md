# guard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把引擎上报的误伤信号转成「对单个 App 停用具体规则」的决定，执行、通知、可撤销，并负责结束观察期。

**Architecture:** Android library，包 `com.sentinel.guard`。决策在 `policy` 包内为纯函数（不引用 `android.*`）；`runtime` 包在主进程用协程订阅 `SignalRepository.observeUnhandled()`，执行决定并发通知；观察期由 WorkManager 每小时评估一次。

**Tech Stack:** Kotlin 协程、WorkManager、NotificationCompat、Koin。

**Spec:** `docs/superpowers/specs/2026-10-01-adblock-sentinel-design.md`（5.6、6、7 节）；总调度见 `MASTER_PLAN.md`「设计补充」第 3、4 条。

**Tests:** 全部测试定义在 `testing/unit/PLAN.md` 的「Task GD」（UT-GD-*、MT-GD-*）。模块完成后执行关卡 **G7**。

## Global Constraints

见 `MASTER_PLAN.md`。本模块固定值：
- 重试风暴由 engine-vpn 判定后上报（60 秒内 ≥10 次）；
- 规则命中回溯窗口：崩溃类 2 分钟，撤销/放行类 10 分钟；
- USER_UNDO 不带 ruleId 时，24 小时内 ≥2 次才处置；
- 观察期每次延长 3 天；
- 通知渠道 id `guard`。

## 每个任务的通用步骤

按该任务列出的测试编号在 `guard/src/test/` 下编写测试 → 运行确认失败 → 实现 → 重跑确认通过 → 提交。运行命令：`./gradlew :guard:testDebugUnitTest --tests "<该任务测试类>"`。

---

### Task 1: 决策策略

**Files:**
- Create: `guard/src/main/kotlin/com/sentinel/guard/policy/GuardPolicy.kt`

**Tests:** UT-GD-1-01～10

**Interfaces:**
- Consumes: `SignalEntity`、`SignalKind`、`AppConfigEntity`（data）。
- Produces:
  ```kotlin
  sealed interface Decision {
      data class DisableRules(val pkg: String, val ruleIds: List<String>, val reason: String) : Decision
      data class NoRuleFound(val pkg: String, val reason: String) : Decision
      data class ExtendObservation(val pkg: String) : Decision
      data class EndObservation(val pkg: String) : Decision
  }
  object GuardPolicy {
      fun hitWindowMs(kind: SignalKind): Long          // CRASH_DIALOG/COLD_START_LOOP/DROPBOX_CRASH → 120_000；其余 → 600_000
      fun onSignal(signal: SignalEntity, recentHits: List<String>, sameKindCount24h: Int): Decision?   // 对 SignalKind 的 when 必须穷举（无 else），新增信号类型时编译即失败；契约见 docs/CONTRACTS.md C2
      fun evaluateObservation(cfg: AppConfigEntity, undoOrAllowCount: Int): Decision
  }
  ```

`onSignal` 规则：
| 信号 | 条件 | 决定 |
|---|---|---|
| RETRY_STORM | ruleId 非空 | `DisableRules(pkg, [ruleId])` |
| USER_UNDO | ruleId 非空 | `DisableRules(pkg, [ruleId])` |
| USER_UNDO | ruleId 为空且 `sameKindCount24h >= 2` | `recentHits` 非空 → `DisableRules`，否则 `NoRuleFound` |
| USER_UNDO | ruleId 为空且 `< 2` | `null` |
| TEMP_ALLOW / CRASH_DIALOG / COLD_START_LOOP / DROPBOX_CRASH | — | `recentHits` 非空 → `DisableRules`，否则 `NoRuleFound` |

`recentHits` 先去重。`reason` 文案：`"重试风暴"`、`"你撤销了拦截"`、`"你临时放行了该应用"`、`"应用崩溃"`、`"应用反复重启"`。`evaluateObservation`：`undoOrAllowCount > 0` → `ExtendObservation`，否则 `EndObservation`。

- [ ] **Step 1:** 编写 UT-GD-1-01～10。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(guard): decision policy`。

### Task 2: 运行时执行、通知与观察期评估

**Files:**
- Create: `guard/src/main/kotlin/com/sentinel/guard/runtime/GuardRunner.kt`
- Create: `guard/src/main/kotlin/com/sentinel/guard/runtime/GuardNotifier.kt`
- Create: `guard/src/main/kotlin/com/sentinel/guard/runtime/GuardActionReceiver.kt`
- Create: `guard/src/main/kotlin/com/sentinel/guard/runtime/ObservationWorker.kt`
- Create: `guard/src/main/kotlin/com/sentinel/guard/di/GuardModule.kt`
- Create: `guard/src/main/kotlin/com/sentinel/guard/di/GuardEntry.kt`
- Create: `guard/src/main/resources/META-INF/services/com.sentinel.data.module.ModuleEntry`
- Modify: `guard/src/main/AndroidManifest.xml`（注册 `GuardActionReceiver`，`exported=false`）

**Tests:** UT-GD-2-01～06

**Interfaces:**
- Consumes: `GuardPolicy`（Task 1）；`SignalRepository`、`EventRepository.rulesHitSince`、`OverrideRepository`、`AppConfigRepository`（data）。
- Produces:
  ```kotlin
  class GuardRunner(...) { fun start(scope: CoroutineScope); suspend fun undo(pkg: String, ruleIds: List<String>) }  // undo = remove + pin
  interface GuardNotifier { fun notifyDisabled(pkg: String, label: String, ruleIds: List<String>, reason: String)
      fun notifyNoRule(pkg: String, label: String, reason: String); fun notifyObservationExtended(pkg: String, label: String) }
  class AndroidGuardNotifier(context: Context) : GuardNotifier
  class ObservationWorker : CoroutineWorker   // 唯一周期任务名 "observation-check"，间隔 1h
  val guardModule: Module
  class GuardEntry : ModuleEntry      // id = "guard"，processes = {MAIN}，start：创建通知渠道 `guard`、启动 GuardRunner、注册周期任务 "observation-check"
  ```
  通知「撤销」按钮：action `com.sentinel.guard.UNDO`，extra `pkg: String`、`ruleIds: Array<String>`。

行为：
- `GuardRunner` 对每条未处理信号：计算 `recentHits = rulesHitSince(pkg, ts - hitWindowMs)` 与 `sameKindCount24h`，执行决定，发通知，`markHandled`。
- `DisableRules` 对每个 ruleId 调 `OverrideRepository.disable`；如果全部返回 false（都被钉住），按 `NoRuleFound` 处理。
- 单条信号处理异常只记录日志、标记已处理，不能让收集协程退出。
- `ObservationWorker` 对 `observationDue()` 返回的每个 App，统计 `[firstSeenAt, now]` 内 USER_UNDO + TEMP_ALLOW 的次数，按 `evaluateObservation` 的结果执行 `extendObservation(pkg, 259_200_000)` 或 `endObservation(pkg)`。

- [ ] **Step 1:** 编写 UT-GD-2-01～06。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(guard): runtime runner, notifications and observation check`。

### 模块完成 → 关卡 G7

- [ ] 按 `testing/unit/PLAN.md` 编写 MT-GD-01～02，执行 `testing/README.md` 中 G7 的全部项目，写关卡报告。
