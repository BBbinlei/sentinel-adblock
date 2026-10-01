# data Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 提供多进程共享的数据库、生效配置计算、App 登记、规则存储与订阅更新。

**Architecture:** Android library，包 `com.sentinel.data`。Room 开启 `enableMultiInstanceInvalidation()`，两个进程各自打开同一个数据库文件，并通过 Flow 感知变化。生效配置计算为纯函数 `EffectivePolicy`。规则文件由 `RuleStore` 原子安装并保留上一版。

**Tech Stack:** Room、WorkManager、OkHttp、kotlinx.serialization、Koin。

**Spec:** `docs/superpowers/specs/2026-10-01-adblock-sentinel-design.md`（5.1、7 节）；总调度见 `MASTER_PLAN.md`。

**Tests:** 全部测试定义在 `testing/unit/PLAN.md` 的「Task DA」（UT-DA-*、MT-DA-*）。模块完成后执行关卡 **G2**。

## Global Constraints

见 `MASTER_PLAN.md`。本模块固定值：观察期 3 天（`259_200_000` ms）；临时放行 24 小时；暂停 5 分钟；奖励窗口 60 秒（取自 `RewardWindowContract.TTL_MS`）；事件保留 30 天；数据库文件名 `sentinel.db`；规则目录 `filesDir/rules/`。

## 每个任务的通用步骤

按该任务列出的测试编号在 `data/src/test/` 下编写测试 → 运行确认失败 → 实现 → 重跑确认通过 → 提交。运行命令：`./gradlew :data:testDebugUnitTest --tests "<该任务测试类>"`。

---

### Task 1: 数据库、实体与生效配置

**Files:**
- Create: `data/src/main/kotlin/com/sentinel/data/db/SentinelDatabase.kt`
- Create: `data/src/main/kotlin/com/sentinel/data/db/Entities.kt`
- Create: `data/src/main/kotlin/com/sentinel/data/db/Converters.kt`
- Create: `data/src/main/kotlin/com/sentinel/data/db/Daos.kt`
- Create: `data/src/main/kotlin/com/sentinel/data/policy/EffectivePolicy.kt`

**Tests:** UT-DA-1-01～08

**Interfaces:**
- Consumes: `RuleLevel`、`DomainTag`、`EventKind`（core-rules）。
- Produces:
  ```kotlin
  enum class ProtectLevel { OFF, STANDARD, STRONG }
  enum class RewardedMode { SILENT, BLOCK, ASK }
  enum class SubscriptionFormat { HOSTS, ADGUARD, GKD, SENTINEL_JSON }
  enum class SignalKind { USER_UNDO, TEMP_ALLOW, RETRY_STORM, CRASH_DIALOG, COLD_START_LOOP, DROPBOX_CRASH }
  enum class OverrideState { DISABLED, PINNED }
  enum class EngineId { VPN, A11Y, NOTIFY, SYSTEM }
  enum class EngineState { RUNNING, STOPPED, DEGRADED, NOT_SETUP }
  enum class RuleOrigin { LEARNED, MANUAL }

  @Entity("app_config") data class AppConfigEntity(@PrimaryKey val pkg: String, val label: String,
      val level: ProtectLevel?,            // null = 用户未设置，走默认
      val splash: Boolean = true, val rewarded: RewardedMode = RewardedMode.SILENT,
      val shake: Boolean = true, val jumpBack: Boolean = true, val notify: Boolean = true,
      val limitOverlay: Boolean = false, val denyClipboard: Boolean = false,
      val sensitive: Boolean, val tempAllowUntil: Long? = null,
      val firstSeenAt: Long, val observationEndsAt: Long)
  @Entity("global_state") data class GlobalStateEntity(@PrimaryKey val id: Int = 0, val enabled: Boolean = true,
      val pausedUntil: Long? = null, val ruleVersion: Long = 0)
  @Entity("subscriptions") data class SubscriptionEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0,
      val name: String, val url: String, val format: SubscriptionFormat, val level: RuleLevel, val tag: DomainTag,
      val enabled: Boolean = true, val lastUpdatedAt: Long? = null, val ruleCount: Int = 0,
      val etag: String? = null, val lastError: String? = null)
  @Entity("user_rules") data class UserRuleEntity(@PrimaryKey val id: String, val json: String, val origin: RuleOrigin, val createdAt: Long)
  @Entity("events", indices = [Index("ts"), Index("pkg")]) data class EventEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0,
      val ts: Long, val pkg: String?, val kind: EventKind, val ruleId: String?, val detail: String? = null)
  @Entity("signals") data class SignalEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val ts: Long,
      val pkg: String, val kind: SignalKind, val ruleId: String?, val detail: String? = null, val handled: Boolean = false)
  @Entity("rule_overrides", primaryKeys = ["pkg", "ruleId"]) data class RuleOverrideEntity(val pkg: String,
      val ruleId: String, val state: OverrideState, val reason: String, val createdAt: Long)
  @Entity("op_log") data class OpLogEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val ts: Long,
      val opId: String, val target: String, val beforeState: String, val command: String,
      val success: Boolean, val output: String, val undone: Boolean = false)
  @Entity("jump_exceptions", primaryKeys = ["sourcePkg", "targetPkg"]) data class JumpExceptionEntity(val sourcePkg: String, val targetPkg: String)
  @Entity("reward_windows") data class RewardWindowEntity(@PrimaryKey val pkg: String, val until: Long)
  @Entity("engine_status") data class EngineStatusEntity(@PrimaryKey val engine: EngineId, val state: EngineState,
      val message: String?, val updatedAt: Long)

  data class EffectiveConfig(val pkg: String, val level: ProtectLevel, val observing: Boolean,
      val splash: Boolean, val rewarded: RewardedMode, val shake: Boolean, val jumpBack: Boolean,
      val notify: Boolean, val limitOverlay: Boolean, val denyClipboard: Boolean)
  object EffectivePolicy { fun resolve(cfg: AppConfigEntity?, pkg: String, global: GlobalStateEntity, now: Long): EffectiveConfig }
  ```

`EffectivePolicy.resolve` 规则（按顺序，先命中先返回 level）：
1. `!global.enabled` 或 `global.pausedUntil > now` → `OFF`
2. `cfg.tempAllowUntil > now` → `OFF`
3. `cfg.level != null` → `cfg.level`
4. `cfg.sensitive` → `OFF`
5. 否则 `STANDARD`；`cfg == null` 时也为 `STANDARD`，各开关取默认值。

`observing = cfg != null && cfg.level == null && cfg.observationEndsAt > 0`。观察期**只由 guard 结束**：guard 评估通过后把 `observationEndsAt` 置 0。这样不会出现「已经到期、但 guard 还没评估就开始拦截」的空档；`observationEndsAt` 的时间值只用来判断何时该评估。

数据库：版本 1；禁止 `fallbackToDestructiveMigration`；`build()` 时开启 `enableMultiInstanceInvalidation()`；首次创建回调写入 `GlobalStateEntity()`；枚举按名字存储。

- [ ] **Step 1:** 编写 UT-DA-1-01～08。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现（DAO 只写本任务和后续任务要用到的查询）。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(data): database schema and effective policy`。

### Task 2: 通用仓库

**Files:**
- Create: `data/src/main/kotlin/com/sentinel/data/Clock.kt`
- Create: `data/src/main/kotlin/com/sentinel/data/repo/GlobalStateRepository.kt`
- Create: `data/src/main/kotlin/com/sentinel/data/repo/EventRepository.kt`
- Create: `data/src/main/kotlin/com/sentinel/data/repo/SignalRepository.kt`
- Create: `data/src/main/kotlin/com/sentinel/data/repo/OverrideRepository.kt`
- Create: `data/src/main/kotlin/com/sentinel/data/repo/OpLogRepository.kt`
- Create: `data/src/main/kotlin/com/sentinel/data/repo/JumpExceptionRepository.kt`
- Create: `data/src/main/kotlin/com/sentinel/data/repo/RewardWindowRepository.kt`
- Create: `data/src/main/kotlin/com/sentinel/data/repo/EngineStatusRepository.kt`
- Create: `data/src/main/kotlin/com/sentinel/data/repo/UserRuleRepository.kt`
- Create: `data/src/main/kotlin/com/sentinel/data/contract/RewardWindowContract.kt`
- Create: `data/src/main/kotlin/com/sentinel/data/contract/SignalContract.kt`

**Tests:** UT-DA-2-01～12

**Interfaces:**
- Produces:
  ```kotlin
  fun interface Clock { fun now(): Long }
  class GlobalStateRepository { fun observe(): Flow<GlobalStateEntity>; suspend fun get(): GlobalStateEntity
      suspend fun setEnabled(on: Boolean); suspend fun pauseFor(ms: Long = 300_000); suspend fun resume(); suspend fun bumpRuleVersion(): Long }
  class EventRepository { suspend fun log(pkg: String?, kind: EventKind, ruleId: String?, detail: String? = null)
      fun observeTodayCount(): Flow<Int>; fun observeCountByKind(since: Long): Flow<Map<EventKind, Int>>
      fun observeCountByPkg(since: Long): Flow<Map<String, Int>>; fun observeRecent(kinds: Set<EventKind>, limit: Int): Flow<List<EventEntity>>
      suspend fun rulesHitSince(pkg: String, since: Long): List<String>; suspend fun prune() }   // 删除 30 天前的事件
  class SignalRepository { suspend fun emit(pkg: String, kind: SignalKind, ruleId: String? = null, detail: String? = null)
      fun observeUnhandled(): Flow<List<SignalEntity>>; suspend fun markHandled(ids: List<Long>)
      suspend fun countSince(pkg: String, kinds: Set<SignalKind>, since: Long): Int }
  class OverrideRepository { fun observeDisabled(): Flow<Map<String, Set<String>>>   // pkg → ruleIds
      fun observeRecent(since: Long): Flow<List<RuleOverrideEntity>>                // 首页 guard 提醒用
      suspend fun disable(pkg: String, ruleId: String, reason: String): Boolean        // 已 PINNED 返回 false
      suspend fun pin(pkg: String, ruleId: String); suspend fun remove(pkg: String, ruleId: String) }
  class OpLogRepository { suspend fun append(e: OpLogEntity): Long; fun observeAll(): Flow<List<OpLogEntity>>
      suspend fun latestApplied(opId: String): OpLogEntity?; suspend fun markUndone(id: Long) }
  class JumpExceptionRepository { suspend fun isExcepted(src: String, tgt: String): Boolean; suspend fun add(src: String, tgt: String) }
  class RewardWindowRepository { suspend fun open(pkg: String, ms: Long = RewardWindowContract.TTL_MS); fun observeOpen(): Flow<Map<String, Long>> }
  class EngineStatusRepository { suspend fun report(engine: EngineId, state: EngineState, message: String? = null)
      fun observeAll(): Flow<Map<EngineId, EngineStatusEntity>> }
  class UserRuleRepository { suspend fun add(rule: Rule, origin: RuleOrigin); fun observeAll(): Flow<List<Rule>>; suspend fun delete(id: String) }
  ```
  所有仓库的构造参数为对应 DAO（或 `SentinelDatabase`）+ `Clock`。「今日」以本地时区零点为界。
- Produces（跨模块契约常量，行为说明见 `docs/CONTRACTS.md` C1、C2；引擎一律引用常量，不写字面量）：
  ```kotlin
  object RewardWindowContract { const val TTL_MS = 60_000L; val RELEASED_TAGS: Set<DomainTag> = setOf(DomainTag.AD_SDK) }
  object SignalContract { val EMITTERS: Map<SignalKind, EngineId?> }   // null = 由 data/app 层发出；每个 SignalKind 必须有一项
  ```
  `RewardWindowRepository.open` 的默认时长取 `RewardWindowContract.TTL_MS`，不再另写 `60_000`。

- [ ] **Step 1:** 编写 UT-DA-2-01～09。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(data): repositories`。

### Task 3: App 配置仓库与 App 登记

**Files:**
- Create: `data/src/main/kotlin/com/sentinel/data/repo/AppConfigRepository.kt`
- Create: `data/src/main/kotlin/com/sentinel/data/apps/AppRegistry.kt`
- Create: `data/src/main/kotlin/com/sentinel/data/apps/PackageWatcher.kt`

**Tests:** UT-DA-3-01～07（`PackageWatcher` 无单测，由 DI-02 与 G3 真机测试覆盖）

**Interfaces:**
- Consumes: `EffectivePolicy`（Task 1）、`SignalRepository`、`GlobalStateRepository`（Task 2）、`SensitiveApps`（core-rules）。
- Produces:
  ```kotlin
  data class InstalledApp(val pkg: String, val label: String)
  class AppRegistry { suspend fun syncInstalled(apps: List<InstalledApp>, initial: Boolean)
      suspend fun onPackageAdded(app: InstalledApp); suspend fun onPackageRemoved(pkg: String) }
  class PackageWatcher(context: Context, registry: AppRegistry, scope: CoroutineScope) { fun start(); fun stop() }  // 动态注册 PACKAGE_ADDED/REMOVED
  class AppConfigRepository {
      fun observeAll(): Flow<List<AppConfigEntity>>; fun observe(pkg: String): Flow<AppConfigEntity?>
      suspend fun effective(pkg: String): EffectiveConfig
      fun observeEffective(pkg: String): Flow<EffectiveConfig>
      fun observeExcluded(): Flow<Set<String>>        // 生效级别为 OFF 的包；配置变化时与每 60 秒各重算一次
      suspend fun setLevel(pkg: String, level: ProtectLevel?); suspend fun setRewarded(pkg: String, mode: RewardedMode)
      suspend fun setToggles(pkg: String, transform: (AppConfigEntity) -> AppConfigEntity)
      suspend fun tempAllow(pkg: String)              // tempAllowUntil = now + 24h，并 emit TEMP_ALLOW 信号
      suspend fun extendObservation(pkg: String, ms: Long)   // observationEndsAt = now + ms
      suspend fun endObservation(pkg: String)                 // observationEndsAt = 0
      suspend fun observationDue(): List<AppConfigEntity> }   // level == null 且 0 < observationEndsAt <= now
  ```

行为：`syncInstalled(initial = true)` 时 `observationEndsAt = 0`（已有 App 不观察）；`onPackageAdded` 时 `observationEndsAt = now + 3 天`、`sensitive = SensitiveApps.isSensitive(pkg, label)`；`onPackageRemoved` 删除该包在 `app_config`、`rule_overrides`、`jump_exceptions`、`reward_windows` 中的记录；`syncInstalled(initial = false)` 只补登记缺失的包、删除已不存在的包，不改动已有记录。`PackageWatcher` 用 `context.registerReceiver(..., RECEIVER_EXPORTED)`（系统广播），从 `PackageManager` 取应用名。

- [ ] **Step 1:** 编写 UT-DA-3-01～07。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(data): app registry and config repository`。

### Task 4: 规则存储

**Files:**
- Create: `data/src/main/kotlin/com/sentinel/data/rules/RuleStore.kt`

**Tests:** UT-DA-4-01～05

**Interfaces:**
- Consumes: `DomainMatcher`、`UiRuleIndex`、`JsonRuleParser`、`NotifyRule`、`BuiltInUiRules`（core-rules）；`GlobalStateRepository`（Task 2）。
- Produces:
  ```kotlin
  data class BuiltRules(val domainBytes: ByteArray, val uiJson: String, val notifyJson: String,
                        val stats: Map<String, Int>)   // 各来源条数与跳过数
  class RuleInstallException(msg: String) : Exception(msg)
  class RuleStore(dir: File, global: GlobalStateRepository) {
      suspend fun install(built: BuiltRules): Long            // 返回新的 ruleVersion
      fun loadDomainMatcher(): DomainMatcher?                  // 损坏则自动回滚到 prev；都不可用返回 null
      fun loadUiIndex(): UiRuleIndex                           // 损坏则回滚；都不可用返回只含 BuiltInUiRules 的索引
      fun loadNotifyRules(): List<NotifyRule> }
  ```

文件：`domains.bin`/`domains.prev.bin`、`ui.json`/`ui.prev.json`、`notify.json`/`notify.prev.json`。安装流程：写 `*.tmp` → 校验（`DomainMatcher.verify` / JSON 解析）→ 当前文件改名为 `.prev` → `.tmp` 改名为当前 → `bumpRuleVersion()`。校验失败则不替换，并抛 `RuleInstallException`。

- [ ] **Step 1:** 编写 UT-DA-4-01～05。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(data): atomic rule store with rollback`。

### Task 5: 规则构建与订阅更新

**Files:**
- Create: `data/src/main/kotlin/com/sentinel/data/rules/RuleBuilder.kt`
- Create: `data/src/main/kotlin/com/sentinel/data/rules/SubscriptionUpdater.kt`
- Create: `data/src/main/kotlin/com/sentinel/data/rules/RuleUpdateWorker.kt`
- Create: `data/src/main/kotlin/com/sentinel/data/rules/DefaultSubscriptions.kt`

**Tests:** UT-DA-5-01～05

**Interfaces:**
- Consumes: 各 Parser、`StandardDomains`、`HttpDnsCatalog`、`BuiltInUiRules`、`DomainCompiler`（core-rules）；`RuleStore`（Task 4）；`UserRuleRepository`（Task 2）。
- Produces:
  ```kotlin
  object RuleBuilder { fun build(subs: List<Pair<SubscriptionEntity, String>>, userRules: List<Rule>): BuiltRules }
  data class UpdateReport(val updated: Int, val failed: Int, val ruleVersion: Long?)
  class SubscriptionUpdater { suspend fun updateAll(force: Boolean = false): UpdateReport; suspend fun rebuildFromCache(): Long }
  class RuleUpdateWorker : CoroutineWorker   // 唯一周期任务名 "rule-update"，间隔 24h，需联网
  object DefaultSubscriptions { val all: List<SubscriptionEntity> }
  ```

行为：
- 每个订阅原文缓存到 `filesDir/subs/<id>.txt`；下载带 `If-None-Match`，304 时用缓存；失败时记录 `lastError`，保留缓存继续参与构建。
- 构建内容：域名 = `StandardDomains` + `HttpDnsCatalog.rules()` + 各订阅 + 用户 DNS 规则；UI 规则 = `BuiltInUiRules.all` + GKD 订阅 + 用户 UI 规则；通知规则 = 用户通知规则。
- 任何一步异常都不调用 `install`，保持旧规则。
- `DefaultSubscriptions`：anti-AD（ADGUARD，STRONG，AD）、AWAvenue（ADGUARD，STRONG，AD）、GKD 官方订阅（GKD，STRONG）。三个 URL 在实施时从各项目 README 核对后写入，并在提交说明中注明核对日期。
- 数据库首次创建时写入默认订阅，然后执行一次 `rebuildFromCache()`（没有缓存时只含内置规则），保证没网也有标准级拦截。

- [ ] **Step 1:** 编写 UT-DA-5-01～05。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(data): subscription update pipeline`。

### Task 6: Koin 模块与模块接线入口

**Files:**
- Create: `data/src/main/kotlin/com/sentinel/data/di/DataModule.kt`
- Create: `data/src/main/kotlin/com/sentinel/data/module/ModuleEntry.kt`
- Create: `data/src/main/kotlin/com/sentinel/data/module/DataEntry.kt`
- Create: `data/src/main/resources/META-INF/services/com.sentinel.data.module.ModuleEntry`（内容：`com.sentinel.data.module.DataEntry`）

**Tests:** UT-DA-6-01～02

**Interfaces:**
- Produces: `val dataModule: Module`，提供 `SentinelDatabase`（单例）、`Clock`（`System::currentTimeMillis`）、Task 2–5 的全部仓库，以及 `RuleStore`、`SubscriptionUpdater`、`AppRegistry`。
- Produces（模块接线约定，见 `docs/CONTRACTS.md` C5）：
  ```kotlin
  enum class ProcessKind { MAIN, VPN }
  interface ModuleEntry {
      val id: String                       // "data" / "vpn" / "a11y" / "notify" / "system" / "guard"，全局唯一
      val processes: Set<ProcessKind>      // 在哪些进程里启动
      val koinModule: Module               // 本模块的依赖注入定义（dataModule 本身由 app 直接加载，DataEntry 返回空模块）
      fun start(context: Context, scope: CoroutineScope, koin: Koin)   // 创建本模块用到的通知渠道、启动协程、注册周期任务；不得抛异常
  }
  class DataEntry : ModuleEntry        // id = "data"，processes = {MAIN}，start 注册周期任务 "rule-update"
  ```

约定：
- 每个引擎/guard 在自己模块的 `src/main/resources/META-INF/services/com.sentinel.data.module.ModuleEntry` 登记自己的入口类；app 用 `ServiceLoader` 发现，**不在代码里列举模块**。
- `start` 内部的任何异常必须自行捕获并上报对应引擎 `DEGRADED`，不得影响其他模块启动。
- `:app` 的 R8 规则保留 `ModuleEntry` 的所有实现类（`-keep class * implements com.sentinel.data.module.ModuleEntry`）。

- [ ] **Step 1:** 编写 UT-DA-6-01～02。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(data): koin module and module entry`。

### 模块完成 → 关卡 G2

- [ ] 按 `testing/unit/PLAN.md` 编写 MT-DA-01～03，执行 `testing/README.md` 中 G2 的全部项目，写关卡报告。
