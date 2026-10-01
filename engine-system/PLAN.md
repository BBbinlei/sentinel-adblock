# engine-system Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 通过 Shizuku 执行经实机验证的 ColorOS 去广告操作，带操作日志、撤销、巡检与崩溃日志采集。

**Architecture:** Android library，包 `com.sentinel.system`。所有命令都经过 `Shell` 接口（生产实现为 Shizuku UserService）。操作定义来自 assets 中的 ColorOS 配置档案；`OpExecutor` 负责「探测 → 执行 → 再探测 → 记日志」，`DriftInspector` 负责巡检。

**Tech Stack:** Shizuku API（`dev.rikka.shizuku:api`、`provider`）、WorkManager、kotlinx.serialization、Koin。

**Spec:** `docs/superpowers/specs/2026-10-01-adblock-sentinel-design.md`（5.5、6、7 节）；总调度见 `MASTER_PLAN.md` Task 1（实机核实）与「设计补充」第 5 条。

**Tests:** 全部测试定义在 `testing/unit/PLAN.md` 的「Task SY」（UT-SY-*、MT-SY-*）、`testing/rule-regression/PLAN.md`（RR-06）、`testing/device-integration/PLAN.md`（DI-31、DI-32）。模块完成后执行关卡 **G5**。

## Global Constraints

见 `MASTER_PLAN.md`。本模块额外约束与固定值：
- **例外**：Shizuku UserService 必须用 AIDL 定义接口（`IShellService`），这是 Shizuku 的要求，不违反「进程间不写 AIDL」的约束（该约束针对本项目自身的两个进程）。
- 只执行 `verified == true` 的操作；`kind == UNINSTALL` 的操作在界面上需要二次确认。
- 巡检每天一次（唯一周期任务名 `system-drift`）；崩溃采集每 30 分钟一次（`system-dropbox`）；单条命令超时 15s。
- 操作日志 `opId` 规则：档案项用档案中的 `id`；按 App 的权限项用 `appop:<OP>:<pkg>`。

## 每个任务的通用步骤

按该任务列出的测试编号在 `engine-system/src/test/` 下编写测试 → 运行确认失败 → 实现 → 重跑确认通过 → 提交。运行命令：`./gradlew :engine-system:testDebugUnitTest --tests "<该任务测试类>"`。

---

### Task 1: 配置档案

**Files:**
- Create: `engine-system/src/main/kotlin/com/sentinel/system/profile/ColorOsProfile.kt`
- Create: `engine-system/src/main/kotlin/com/sentinel/system/profile/ProfileLoader.kt`
- Create: `engine-system/src/main/assets/profiles/coloros-<版本>.json`（版本号取自实机核实报告）

**Tests:** UT-SY-1-01～04；档案完整性由 RR-06 把关（G5）。

**Interfaces:**
- Produces:
  ```kotlin
  enum class OpKind { SETTING, APPOP, DISABLE, UNINSTALL, WIZARD }
  @Serializable data class SettingsIntent(val action: String? = null, val pkg: String? = null, val cls: String? = null)
  @Serializable data class ProfileOp(val id: String, val trick: Int, val title: String, val kind: OpKind,
      val verified: Boolean, val apply: String? = null, val revert: String? = null,
      val probe: String? = null, val appliedRegex: String? = null, val intent: SettingsIntent? = null,
      val optional: Boolean = false)                                  // optional = 默认不勾选（如卸载 com.opos.ads）
  @Serializable data class ColorOsProfile(val romPrefix: String, val backgroundPopupOp: String? = null, val ops: List<ProfileOp>)
  object ProfileLoader { fun parse(json: String): ColorOsProfile; fun select(romVersion: String, profiles: List<ColorOsProfile>): ColorOsProfile? }
  ```
  `apply`/`revert` 可以包含占位符 `{before}`（执行前探测到的值）。`select` 选取 `romVersion` 以 `romPrefix` 开头、且前缀最长的档案。ROM 版本来源：`getprop ro.build.version.oplusrom`；若核实报告显示该属性不存在，改用报告中确认可用的属性，并同步修改本句。

档案内容依据实机核实报告填写，未核实的项 `verified=false`。必须覆盖：
| id | trick | kind 首选 |
|---|---|---|
| `pictorial-recommend` | 1 | SETTING / WIZARD |
| `pictorial-disable` | 1 | DISABLE（optional） |
| `assistant-cards` | 2 | WIZARD |
| `search-hot` | 3 | SETTING / WIZARD |
| `commercial-service` | 4 | SETTING / WIZARD |
| `opos-ads-uninstall` | 4 | UNINSTALL（optional） |
| `app-recommend-*`（天气/日历/浏览器/视频/音乐/主题/游戏中心各一项） | 5 | SETTING / WIZARD |
| `market-recommend` | 6 | WIZARD |
| `safecenter-recommend` | 7 | SETTING / WIZARD |
| `quickapp-disable` | 8 | DISABLE |
| `limit-ad-tracking` | 10 | SETTING / WIZARD |
| `autostart-manage` | 21 | WIZARD |

- [ ] **Step 1:** 编写 UT-SY-1-01～04。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现模型与加载器；按核实报告写入档案 JSON（全部 `verified=false`，等 DI-31 验证后逐项改为 true）。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(engine-system): coloros profile model and loader`。

### Task 2: Shizuku 网关

**Files:**
- Create: `engine-system/src/main/aidl/com/sentinel/system/shell/IShellService.aidl`
- Create: `engine-system/src/main/kotlin/com/sentinel/system/shell/Shell.kt`
- Create: `engine-system/src/main/kotlin/com/sentinel/system/shell/ShellUserService.kt`
- Create: `engine-system/src/main/kotlin/com/sentinel/system/shell/ShizukuGateway.kt`

**Tests:** UT-SY-2-01～04

**Interfaces:**
- Produces:
  ```kotlin
  data class ExecResult(val exitCode: Int, val stdout: String, val stderr: String) { val ok get() = exitCode == 0 }
  interface Shell { suspend fun exec(cmd: String): ExecResult }             // 超时返回 exitCode = -1
  enum class ShizukuState { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, READY }
  interface ShizukuApi { fun pingBinder(): Boolean; fun checkSelfPermission(): Boolean; fun isInstalled(): Boolean }   // 包装 Shizuku 静态方法
  class ShizukuGateway(api: ShizukuApi, binder: suspend () -> IShellService) : Shell {
      fun state(): ShizukuState; val stateFlow: StateFlow<ShizukuState>; fun requestPermission(requestCode: Int = 7001) }
  ```
  `IShellService` 定义为 `String exec(String cmd)`，返回 JSON `{"exitCode":..,"stdout":..,"stderr":..}`，由 `ShizukuGateway` 解析成 `ExecResult`。`ShellUserService` 用 `ProcessBuilder("sh", "-c", cmd)` 执行。非 READY 时 `exec` 返回 `exitCode=-2`、`stderr="shizuku not ready"`。Manifest 中声明 Shizuku 的 `ShizukuProvider`。

- [ ] **Step 1:** 编写 UT-SY-2-01～04。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(engine-system): shizuku gateway`。

### Task 3: 操作执行与撤销

**Files:**
- Create: `engine-system/src/main/kotlin/com/sentinel/system/ops/OpExecutor.kt`

**Tests:** UT-SY-3-01～07

**Interfaces:**
- Consumes: `Shell`（Task 2）、`ProfileOp`（Task 1）；data 的 `OpLogRepository`、`EventRepository`。
- Produces:
  ```kotlin
  sealed interface OpOutcome { data object Applied : OpOutcome; data object AlreadyApplied : OpOutcome
      data object NotVerified : OpOutcome; data object WizardOnly : OpOutcome; data class Failed(val reason: String) : OpOutcome }
  enum class OpStatus { APPLIED, NOT_APPLIED, UNKNOWN }
  class OpExecutor(shell: Shell, log: OpLogRepository, events: EventRepository) {
      suspend fun status(op: ProfileOp): OpStatus
      suspend fun apply(op: ProfileOp): OpOutcome
      suspend fun applyAll(ops: List<ProfileOp>): Map<String, OpOutcome>   // 逐个执行，单项失败不影响其他
      suspend fun undo(logId: Long): Boolean
      suspend fun undoAll(): Int }
  ```

`apply` 流程：
1. `!verified` → NotVerified；`kind == WIZARD` → WizardOnly；
2. 执行 `probe` 得到 `before`；已经匹配 `appliedRegex` → AlreadyApplied；
3. 执行 `apply`（替换 `{before}`）；
4. 再执行 `probe`：匹配则写 `OpLogEntity(success=true)` 和事件 `SYSTEM_OP`，返回 Applied；否则写失败日志，返回 Failed。

`undo`：取日志里的 `beforeState` 替换 `revert` 中的 `{before}` 后执行，成功则 `markUndone`。

- [ ] **Step 1:** 编写 UT-SY-3-01～07。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(engine-system): op executor with undo`。

### Task 4: 按 App 权限同步

**Files:**
- Create: `engine-system/src/main/kotlin/com/sentinel/system/ops/AppOpsSync.kt`

**Tests:** UT-SY-4-01～04

**Interfaces:**
- Consumes: `Shell`、`OpExecutor`（Task 2–3）、`ColorOsProfile.backgroundPopupOp`；data 的 `AppConfigRepository.observeAll`、`OpLogRepository`。
- Produces: `class AppOpsSync(...) { fun start(scope: CoroutineScope); suspend fun syncOnce(): Int; val pendingCount: StateFlow<Int> }`

映射：
- `limitOverlay=true` → `appops set <pkg> SYSTEM_ALERT_WINDOW deny`；`backgroundPopupOp` 非空时再加 `appops set <pkg> <op> ignore`；
- `denyClipboard=true` → `appops set <pkg> READ_CLIPBOARD ignore`；
- 关闭开关时，用日志中的 `beforeState` 恢复原模式（`beforeState` 来自执行前 `appops get <pkg> <OP>` 的输出）；
- Shizuku 非 READY 时不执行，`pendingCount` 记录待同步数量。

- [ ] **Step 1:** 编写 UT-SY-4-01～04。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(engine-system): per-app appops sync`。

### Task 5: 巡检与状态上报

**Files:**
- Create: `engine-system/src/main/kotlin/com/sentinel/system/drift/DriftInspector.kt`
- Create: `engine-system/src/main/kotlin/com/sentinel/system/drift/DriftWorker.kt`
- Create: `engine-system/src/main/kotlin/com/sentinel/system/drift/SystemStatusReporter.kt`

**Tests:** UT-SY-5-01～04

**Interfaces:**
- Consumes: Task 1–4；data 的 `OpLogRepository`、`EngineStatusRepository`。
- Produces:
  ```kotlin
  data class DriftReport(val drifted: List<String>, val checked: Int)
  class DriftInspector(...) { suspend fun inspect(): DriftReport?; suspend fun reapply(opIds: List<String>): Map<String, OpOutcome> }   // Shizuku 非 READY 时返回 null
  class SystemStatusReporter(...) { fun start(scope: CoroutineScope) }
  ```

状态规则：
- Shizuku 非 READY → `DEGRADED`，文案「Shizuku 未激活（不影响已生效项）」；
- READY 且有漂移项或待处理项 → `DEGRADED`，文案「N 项待处理」；
- 否则 `RUNNING`。

发现漂移时发通知「N 项设置被系统恢复」，带「一键重新执行」按钮（action `com.sentinel.system.REAPPLY`）。

- [ ] **Step 1:** 编写 UT-SY-5-01～04。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(engine-system): drift inspection and status`。

### Task 6: 崩溃日志采集

**Files:**
- Create: `engine-system/src/main/kotlin/com/sentinel/system/crash/DropboxParser.kt`
- Create: `engine-system/src/main/kotlin/com/sentinel/system/crash/DropboxCrashWorker.kt`

**Tests:** UT-SY-6-01～02（测试样例用实机采集的 `dumpsys dropbox --print data_app_crash` 输出，去除隐私信息后放在 `engine-system/src/test/resources/dropbox_sample.txt`）

**Interfaces:**
- Produces: `data class CrashEntry(val ts: Long, val pkg: String)`；`object DropboxParser { fun parse(output: String): List<CrashEntry> }`。Worker 记录上次检查时间，只对 `ts > lastChecked`、且该 App 生效级别不是 OFF 的条目执行 `SignalRepository.emit(pkg, DROPBOX_CRASH)`；Shizuku 非 READY 时直接返回成功。

- [ ] **Step 1:** 编写 UT-SY-6-01～02。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(engine-system): dropbox crash collector`。

### Task 7: 接线

**Files:**
- Create: `engine-system/src/main/kotlin/com/sentinel/system/di/SystemModule.kt`
- Create: `engine-system/src/main/kotlin/com/sentinel/system/SystemActionReceiver.kt`

**Tests:** 由 UT-AP-1-01（全部 Koin 模块校验）覆盖。

**Interfaces:**
- Produces: `val systemModule: Module`，提供 `ShizukuGateway`、`OpExecutor`、`AppOpsSync`、`DriftInspector`，以及当前档案 `ColorOsProfile?`。`SentinelApp` 中启动 `AppOpsSync`、`SystemStatusReporter`，并注册两个周期 Worker（见 `app/PLAN.md` Task 1）。

- [ ] **Step 1:** 接线；`./gradlew :engine-system:assembleDebug` 成功。
- [ ] **Step 2:** 提交 `feat(engine-system): wiring`。

### 模块完成 → 关卡 G5

- [ ] 编写 MT-SY-01～03 与 `FakeDevice`（定义见 `testing/unit/PLAN.md`）；完成 RR-06；在真机执行 DI-31、DI-32。根据 DI-31 的结果修改档案：通过的项改为 `verified=true`，失败的项改为 WIZARD 或删除，并提交 `feat(engine-system): verified coloros profile`。最后执行 G5 的全部项目，写关卡报告。
