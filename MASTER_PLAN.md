# 「哨兵」广告拦截软件 总调度计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按里程碑顺序调度 8 个模块的实施，最终在 OPPO Find X7 Ultra 上交付一个不 root、覆盖 27 种广告套路、且不影响原软件正常使用的广告拦截 App。

**Architecture:** Gradle 多模块 Android 项目，两个进程（主进程 + `:vpn`）。纯 Kotlin 核心（core-rules、guard 决策）→ 数据层（Room 多进程共享）→ 四个引擎（VPN / 无障碍 / 通知 / Shizuku 系统）→ Compose 界面。引擎之间不互相调用，只通过数据层交换信息；跨模块的行为约定见 `docs/CONTRACTS.md`。各模块通过 `ModuleEntry`（ServiceLoader）自己接线，app 不在代码里列举模块。

**Tech Stack:** Kotlin、协程/Flow、Jetpack Compose + Material 3、VpnService、AccessibilityService、NotificationListenerService、Shizuku、Room、WorkManager、Koin、kotlinx.serialization。

**Spec:** `docs/superpowers/specs/2026-10-01-adblock-sentinel-design.md`

---

## Global Constraints

- 包名根：`com.sentinel`；applicationId：`com.sentinel.adblock`；应用名：「哨兵」。
- minSdk 30（Android 11，无线调试激活 Shizuku 的下限）；targetSdk/compileSdk = 脚手架时最新稳定版；目标设备 Android 15/16（ColorOS 15/16）。
- 不依赖 root；不做 HTTPS 中间人解密；不做云端服务器、不做账号、不对外分发规则。
- 模块接线：每个模块自己导出 `ModuleEntry`（定义在 data），自己负责创建通知渠道、启动协程、注册周期任务；`:app` 用 `ServiceLoader` 发现入口，**代码中不引用任何引擎/guard 的类**（构建上仍依赖，以便打包）。新增引擎不改 app。
- 跨模块行为契约（奖励窗口、误伤信号、规则热更新、引擎状态）定义在 `docs/CONTRACTS.md`，数值与枚举落成 data 的 `contract` 包常量；引擎不得写字面量。
- 进程：只有主进程与 `:vpn` 两个；进程间只通过 Room（`enableMultiInstanceInvalidation()`）+ 规则二进制文件交换数据，不写 AIDL。
- 依赖方向只能自上而下：`app → engine-* / guard → data → core-rules`；引擎之间、引擎与 guard 之间互不依赖。
- `core-rules` 与 guard 的决策包 `com.sentinel.guard.policy` 必须是纯 Kotlin（不引用 `android.*`）。
- 依赖注入用 Koin；序列化用 kotlinx.serialization；不引入 Hilt、Gson。
- 版本号统一写在 `gradle/libs.versions.toml`，模块内不写死版本。
- 新 App 观察期 = 3 天；开屏/跳转回退的启动窗口 = 5 秒；无障碍防抖 ≥100ms；临时放行 = 24 小时；暂停快捷开关 = 5 分钟。
- **任何故障只能让拦截变弱，不能让手机断网或 App 不可用**（所有引擎的异常路径都必须遵守）。
- engine-system 只执行配置档案中 `verified = true` 的操作。
- 文案语言：简体中文。

## Review Focus

最容易出问题的 6 种情况及其对应测试，统一定义在 `testing/README.md` 第 4 节。

## 测试总则

- 所有测试内容（单元级、模块级、规则回归、设备集成、实际验收）只在 `testing/` 模块中定义；各模块计划只引用测试编号。
- 开发中按 TDD 编写该任务引用的测试；**每个模块完成后立即执行对应的质量关卡（G1～G8）**，全部通过才能进入依赖它的下一个里程碑。关卡内容见 `testing/README.md` 第 3 节。

---

## 设计补充（写计划时确定、规格中未细化的点）

以下几点在规格基础上做了细化，已同步到各模块计划，审阅时请重点确认：

1. **奖励窗口**：广告 SDK 的投放域名（标签 `AD_SDK`）默认被拦截，这会导致激励视频加载不出来。解决：无障碍检测到用户点击「看视频领奖励」类按钮时，写入一个 60 秒的「奖励窗口」，窗口内 VPN 对该 App 放行 `AD_SDK` 域名，激励视频得以加载，再由「静默播完」处理。激励模式为「直接拦截」的 App 不开窗口。
2. **「摇一摇跳转」开关的含义**：开启后，App 使用全程（不限于启动 5 秒）只要屏幕上出现「摇一摇/扭一扭」类文字、且发生无交互跳转，就执行跳转回退。
3. **观察期评估**：3 天期满时，若观察期内该 App 有「撤销 / 临时放行」信号，则延长 3 天并提醒；否则自动结束观察、开始拦截。
4. **引擎上报信号**：引擎把误伤信号写入数据层的 `signals` 表，guard 订阅处理；因此引擎不依赖 guard 模块（规格 3.1 表中 engine-system 对 guard 的依赖取消）。
5. **应用详情高级项**新增两项：「限制悬浮窗/后台弹窗」「禁止读取剪贴板」（对应套路 #20、#23，由 engine-system 执行）。
6. **待实机核实**新增两项：DNS 请求能否通过 `getConnectionOwnerUid` 归属到具体 App；Accessibility Service 能否在后台直接拉起源 App。
7. **激励视频「每次询问」的选项**：识别到激励视频页时，视频已经加载出来，「拦截」已不可能。所以小窗选项改为「静默播完 / 正常观看 / ☐记住」（规格 4.5 原为「拦截 / 本次放行 / 记住」）。想要拦截，请把该 App 设为「直接拦截」模式。
8. **观察期只作用于网络层**：界面引擎点「跳过」、关弹窗的风险很低，观察期内照常工作。
9. **NotifyRule 内置规则**放在 engine-notify（`BuiltInNotifyRules`），只针对 ColorOS 自带的 6 个 App，按营销关键词过滤。
10. **编排器**：为了能做模块级测试，engine-a11y 新增 `A11yBrain`、engine-notify 新增 `NotifyEngine`，把各子组件串起来、返回要执行的动作列表；系统服务只负责适配和执行。
11. **模块接线分散到各模块**：原计划把通知渠道、周期任务、`GuardRunner`/`AppOpsSync` 启动都集中写在 `SentinelApp`，导致 app 要引用 M7 才有的 `guardModule`，并行开发时也要多方改同一文件。改为每个模块导出 `ModuleEntry`，app 用 `ServiceLoader` 发现并按进程启动。
12. **跨模块契约文档化并常量化**：引擎之间靠表交换信息、行为靠约定。约定写在 `docs/CONTRACTS.md`，数值与枚举落成 data 的 `contract` 包常量，并有契约测试 `MT-CT-*`（G7）。

---

## 模块与文档索引

| 模块 | 功能说明 | 实施计划 |
|---|---|---|
| core-rules | `core-rules/README.md` | `core-rules/PLAN.md` |
| data | `data/README.md` | `data/PLAN.md` |
| guard | `guard/README.md` | `guard/PLAN.md` |
| engine-vpn | `engine-vpn/README.md` | `engine-vpn/PLAN.md` |
| engine-a11y | `engine-a11y/README.md` | `engine-a11y/PLAN.md` |
| engine-notify | `engine-notify/README.md` | `engine-notify/PLAN.md` |
| engine-system | `engine-system/README.md` | `engine-system/PLAN.md` |
| app | `app/README.md` | `app/PLAN.md` |
| **跨模块契约** | `docs/CONTRACTS.md` | — |
| **testing** | `testing/README.md` | `testing/unit/PLAN.md`、`testing/rule-regression/PLAN.md`、`testing/device-integration/PLAN.md`、`testing/acceptance/PLAN.md` |

---

## 里程碑、质量关卡与调度

```
M0 骨架 + 实机核实 ──► M1 core-rules ─G1─► M2 data ─G2─┬─► M3 engine-vpn + app 最小壳 ─G3─ ★首个可用版本
                                                         ├─► M4 engine-a11y ─G4─
                                                         ├─► M6 engine-notify ─G6─
                         核实报告 ───────────────────────┴─► M5 engine-system ─G5─
                                      M3～M6 全部过关 ──► M7 guard ─G7─► M8 app 完整界面 ─G8─► M9 验收（G9）
```

| 里程碑 | 内容 | 前置 | 完成后立即执行的关卡 | 交付物 |
|---|---|---|---|---|
| M0 | 本文 Task 0、Task 1 | 无 | 冒烟构建通过 | 可编译的空项目（含 testing 子模块）；实机核实报告 |
| M1 | `core-rules/PLAN.md` | Task 0 | **G1** | 规则解析/编译/匹配/选择器 |
| M2 | `data/PLAN.md` | G1 | **G2** | 数据库、仓库、规则订阅更新 |
| M3 | `engine-vpn/PLAN.md` + `app/PLAN.md` Task 1–2 | G2 | **G3** | **首个可用版本**：手机上开启后网络层拦截广告 |
| M4 | `engine-a11y/PLAN.md` | G2 | **G4** | 开屏跳过、弹窗关闭、激励视频、跳转回退、学习模式 |
| M5 | `engine-system/PLAN.md` | G2 + Task 1 核实报告 | **G5** | ColorOS 系统净化、操作日志、巡检 |
| M6 | `engine-notify/PLAN.md` | G2 | **G6** | 营销通知过滤 |
| M7 | `guard/PLAN.md` | G3～G6 | **G7** | 健康守护、自动降级、观察期评估 |
| M8 | `app/PLAN.md` Task 3–7 | G7 | **G8** | 完整界面与引导向导 |
| M9 | `testing/acceptance/PLAN.md` | G8 | **G9** | 验收报告 |

**关卡规则**：关卡未通过 → 在该模块内修复 → 重跑该关卡全部项目 + `./gradlew test`；通过后才能开始依赖它的里程碑。M3～M6 可以并行，各自过关即可。

**单人顺序建议**：M0 → M1 → M2 → M3 → M4 → M5 → M6 → M7 → M8 → M9。

---

### Task 0: 项目骨架

**Files:**
- Create: `settings.gradle.kts`、`build.gradle.kts`、`gradle.properties`、`gradle/libs.versions.toml`、`.gitignore`
- Create: 8 个功能模块各自的 `build.gradle.kts` 与 `src/main/AndroidManifest.xml`（Android 模块）
- Create: `testing/rule-regression/build.gradle.kts`（Android library，只有测试源码）、`testing/device-integration/build.gradle.kts`（`com.android.test`，目标 `:app`）
- Create: `app/src/main/kotlin/com/sentinel/app/SentinelApp.kt`（`Application` 子类，空的 Koin 启动）

**Interfaces:**
- Produces: 模块 `:app`、`:data`、`:core-rules`、`:guard`、`:engine-vpn`、`:engine-a11y`、`:engine-notify`、`:engine-system`、`:testing:rule-regression`、`:testing:device-integration`。`:core-rules` 为 `kotlin("jvm")` 模块；`:app` 为 application；`:testing:device-integration` 为 `com.android.test`；其余为 Android library。

- [ ] **Step 1:** `git init`，写 `.gitignore`（Android Studio 标准模板 + `local.properties`）。
- [ ] **Step 2:** 写 `settings.gradle.kts`，include 上述 10 个模块；`libs.versions.toml` 写入 AGP、Kotlin、Compose BOM、Room、WorkManager、Koin、kotlinx.serialization、OkHttp、Shizuku（`dev.rikka.shizuku:api`、`dev.rikka.shizuku:provider`）、JUnit5、kotlin.test、Robolectric、Turbine、MockWebServer、AndroidX Test、UiAutomator 的最新稳定版本。
- [ ] **Step 3:** 按 Global Constraints 设置 minSdk 30、applicationId；按依赖方向配置各模块的 `dependencies`。 `:app` 对所有引擎与 guard 保持 `implementation` 依赖（只为打包，代码不引用）；`:testing:rule-regression` 的 `testImplementation` 依赖全部功能模块。
- [ ] **Step 4:** 运行 `./gradlew assembleDebug :core-rules:test :testing:rule-regression:testDebugUnitTest`，期望 BUILD SUCCESSFUL（此时 0 个测试）。
- [ ] **Step 5:** 提交：`git add -A && git commit -m "chore: scaffold sentinel multi-module project"`。

### Task 1: 实机核实（只读）

**Files:**
- Create: `docs/device-survey/find-x7-ultra-survey.md`（核实报告）
- Create: `docs/device-survey/scripts/survey.sh`（只读采集脚本）

**Interfaces:**
- Produces: 核实报告，供 engine-system Task 1 填写 `profiles/coloros-<版本>.json`、engine-notify 修正内置包名。

前置：你在手机上开启 USB 调试并连接 Mac；全程只运行只读命令，不做任何修改。（这一步是开发前的环境调研，不属于测试；开发中的真机测试见 `testing/device-integration/PLAN.md`。）

- [ ] **Step 1:** 写 `survey.sh`，只包含只读命令：`getprop ro.build.display.id`、`getprop ro.build.version.release`、`getprop ro.build.version.oplusrom`、`pm list packages -s -f`、`settings list system|secure|global`、针对候选广告包的 `dumpsys package <包名>`。
- [ ] **Step 2:** 运行脚本，记录系统版本；对照 BL 熔断版本（16.0.3.500 / 16.0.8.xxx）给出结论。
- [ ] **Step 3:** 确认候选包名是否存在：`com.opos.ads`、`com.android.adservices.api`、`com.heytap.pictorial`、`com.nearme.instant.platform`、`com.coloros.assistantscreen`、`com.heytap.quicksearchbox`、`com.heytap.market`、`com.heytap.browser`、`com.heytap.themestore`、`com.nearme.gamecenter`，以及系统实际使用的其他广告相关包。
- [ ] **Step 4:** settings 键 diff：你在手机上逐个拨动规格第 9 节涉及的广告开关，每拨一个，前后各导出一次 `settings list`，diff 出键名与取值，写入报告表格（开关名 / 设置页路径 / namespace / key / 开值 / 关值）。
- [ ] **Step 5:** 记录以下能力是否存在：单应用「设备动作与方向」权限；「后台弹出界面」对应的 appop 名称（用 `appops get <包名>` 观察）。
- [ ] **Step 6:** 提交：`git add docs/device-survey && git commit -m "docs: add Find X7 Ultra device survey"`。
