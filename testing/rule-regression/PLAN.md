# 板块② 规则回归测试 Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用真实数据（录制的页面快照、真实订阅文件、常用域名表、ColorOS 配置档案）持续验证规则「该拦的拦得住、不该拦的不误拦」。

**Architecture:** Gradle 子模块 `:testing:rule-regression`，类型为 Android library（只有测试源码），依赖 `core-rules`、`data`、`guard`、`engine-vpn`、`engine-a11y`（使用其 `core` 包）、`engine-notify`、`engine-system`（`ProfileLoader`）；其中 `contract/` 目录下的跨模块契约测试（MT-CT-*，见 `testing/unit/PLAN.md` Task CT）需要全部依赖。测试数据放在 `testing/rule-regression/fixtures/`。快照由 engine-a11y 调试版的录制功能采集。

**Tech Stack:** JUnit4 + kotlin.test（Android library 单元测试，JVM 运行）、kotlinx.serialization。

**Spec:** `docs/superpowers/specs/2026-10-01-adblock-sentinel-design.md`（8 节「规则回归」）；总则见 `testing/README.md`。

## 测试边界

- **测**：规则与真实数据的匹配结果，包括域名规则 vs 常用域名、UI 规则/点击判定 vs 真实页面快照、真实订阅文件的解析质量、ColorOS 配置档案的完整性。
- **不测**：引擎运行时行为（→ ①③）、真机上的广告是否真的消失（→ ③④）。
- **共用说明**：本子模块的 `src/test/.../contract/` 目录存放板块①的跨模块契约测试（MT-CT-*），它们的边界、编号和关卡（G7）按 `testing/unit/PLAN.md` Task CT 执行，不属于本板块；本板块的测试放在 `contract/` 以外的目录。
- **何时运行**：G1（RR-01、RR-04、RR-05）、G4（RR-02、RR-03）、G5（RR-06）关卡；此后**每次**修改规则、选择器、点击判定、内置目录、配置档案，以及每次更新 fixtures 时都要运行。命令：`./gradlew :testing:rule-regression:testDebugUnitTest`。

## 目录结构

```
testing/rule-regression/
├── build.gradle.kts
├── fixtures/
│   ├── top-domains-cn.txt              # 常用域名表
│   ├── subscriptions/<名称>-<日期>.txt  # 真实订阅原文样本
│   ├── snapshots/<包名>/<页面>.json     # SnapshotNode 快照
│   ├── snapshots/expected.json          # 每个快照的期望判定
│   └── user-rules.json                  # 学习/手动规则样本
└── src/test/kotlin/com/sentinel/regression/
```

---

### Task 1: 回归子模块与快照录制工具

**Files:**
- Create: `testing/rule-regression/build.gradle.kts`（并在 `settings.gradle.kts` 中 `include(":testing:rule-regression")`）
- Create: `engine-a11y/src/debug/kotlin/com/sentinel/a11y/debug/SnapshotDumper.kt`
- Create: `testing/rule-regression/scripts/pull-snapshot.sh`

**Interfaces:**
- Produces: 调试广播 `com.sentinel.a11y.DEBUG_DUMP`（extra `name: String`）：无障碍服务把当前活动窗口的节点树转成 `SnapshotNode` JSON，写到 `/sdcard/Android/data/com.sentinel.adblock/files/snapshots/<包名>/<name>.json`；`pull-snapshot.sh <name>` 执行 `adb shell am broadcast` 后再 `adb pull` 到 `fixtures/snapshots/`。仅 debug 构建包含该功能。

- [ ] **Step 1（M1 期间，G1 之前）:** 建子模块并确认 `./gradlew :testing:rule-regression:testDebugUnitTest` 能运行（0 个测试）。
- [ ] **Step 2（M4 期间，engine-a11y 服务可用后）:** 实现 `SnapshotDumper` 与脚本；在真机上录一个页面，确认 JSON 能被 `SnapshotNode` 反序列化。
- [ ] **Step 3:** 提交 `test(regression): module and snapshot recorder`。

### Task 2: RR-01 误拦测试（常用域名）

**Files:**
- Create: `testing/rule-regression/fixtures/top-domains-cn.txt`
- Create: `testing/rule-regression/src/test/kotlin/com/sentinel/regression/TopDomainsFalsePositiveTest.kt`

**数据：** Tranco 最新榜单前 1000 个域名 + 手工补充 100 个国内常用服务主域名及其 CDN 主域（淘宝、天猫、微信、QQ、抖音、快手、B 站、美团、京东、拼多多、百度、微博、知乎、小红书、12306、各大银行官网等），文件头注释写明生成日期与来源。

| 编号 | 用例 | 断言 |
|---|---|---|
| RR-01a | STANDARD 级（`StandardDomains` + `HttpDnsCatalog`） | 表中每个域名 `lookup` 结果为 null；失败时打印全部命中项 |
| RR-01b | STRONG 级（加上 `fixtures/subscriptions/` 中全部订阅） | 命中项写入报告 `build/reports/strong-hits.txt`，**只报告不失败**（强力级允许更激进） |

- [ ] **Step 1:** 生成 fixture，写测试。
- [ ] **Step 2:** 运行，期望 RR-01a 通过；若失败，从 `standard_domains.txt` 删除误拦项，并在提交说明中写明。
- [ ] **Step 3:** 提交 `test(regression): RR-01 top domains false positive`。

### Task 3: RR-02 / RR-03 页面快照回归

**Files:**
- Create: `testing/rule-regression/fixtures/snapshots/**`（首批至少：开屏 15 个、弹窗 10 个、激励视频 5 个、陷阱 5 个、自动续费 2 个、正常页面 20 个，来自 ≥10 个常用 App）
- Create: `testing/rule-regression/fixtures/snapshots/expected.json`
- Create: `testing/rule-regression/src/test/kotlin/com/sentinel/regression/SplashRuleRegressionTest.kt`
- Create: `testing/rule-regression/src/test/kotlin/com/sentinel/regression/RuleClickerRegressionTest.kt`

**`expected.json` 格式：** `{"<包名>/<页面>": {"category": "splash|popup|rewarded|trap|autorenew|normal", "expect": "CLICK|REWARDED|WARN|NONE", "mustNotClickText": ["立即下载", ...]}}`

| 编号 | 用例 | 断言 |
|---|---|---|
| RR-02 | 内置通用开屏规则 `builtin:splash-skip` | 命中全部 `splash` 快照；不命中任何 `normal` 快照 |
| RR-03a | `RuleClicker.decide`（启动窗口内、默认配置、内置规则 + fixtures 中的 GKD 订阅） | 每个快照的判定类型与 `expect` 一致 |
| RR-03b | 陷阱快照 | 被点击节点的文字不在 `mustNotClickText` 中，也不匹配陷阱词 |
| RR-03c | `normal` 快照（启动窗口外） | 判定为 NONE |

- [ ] **Step 1:** 用 Task 1 的工具录制快照，人工标注 `expected.json`。
- [ ] **Step 2:** 写测试并运行，期望通过；不通过时先判断是规则问题还是标注问题，修正后在提交说明中写明。
- [ ] **Step 3:** 提交 `test(regression): RR-02/RR-03 snapshot regression`。

### Task 4: RR-04 订阅解析体检

**Files:**
- Create: `testing/rule-regression/fixtures/subscriptions/`（anti-AD、AWAvenue、GKD 官方订阅的原文样本，文件名带下载日期）
- Create: `testing/rule-regression/src/test/kotlin/com/sentinel/regression/SubscriptionHealthTest.kt`

| 编号 | 用例 | 断言 |
|---|---|---|
| RR-04a | 每个样本都能解析 | 不抛异常；规则数 > 0 |
| RR-04b | AdGuard 跳过率 | `skipped / (rules + skipped)` < 20%，否则失败（说明格式变了，需要升级解析器） |
| RR-04c | GKD 接受率 | 被接受的规则数 > 0；接受率写入报告 `build/reports/gkd-coverage.txt` |
| RR-04d | 编译体积 | 全部样本合并编译后 < 8 MB |

- [ ] **Step 1:** 下载样本，写测试并运行，期望通过。
- [ ] **Step 2:** 提交 `test(regression): RR-04 subscription health`。

### Task 5: RR-05 用户规则回归

**Files:**
- Create: `testing/rule-regression/fixtures/user-rules.json`（`LearningRecorder` 生成的典型规则 + 手写规则，各 ≥10 条）
- Create: `testing/rule-regression/src/test/kotlin/com/sentinel/regression/UserRulesRegressionTest.kt`

| 编号 | 用例 | 断言 |
|---|---|---|
| RR-05a | 全部规则可解析 | `JsonRuleParser.parse` 成功；UI 规则的选择器都能 `Selector.parse` |
| RR-05b | 索引构建 | `UiRuleIndex.build` 的 `invalidCount == 0` |

- [ ] **Step 1:** 写 fixture 与测试，运行，期望通过。
- [ ] **Step 2:** 提交 `test(regression): RR-05 user rules`。

### Task 6: RR-06 ColorOS 配置档案体检

**Files:**
- Create: `testing/rule-regression/src/test/kotlin/com/sentinel/regression/ProfileHealthTest.kt`（读取 `engine-system/src/main/assets/profiles/*.json`）

| 编号 | 用例 | 断言 |
|---|---|---|
| RR-06a | 档案可解析 | 每个文件 `ProfileLoader.parse` 成功；`id` 不重复 |
| RR-06b | 命令完整 | `kind != WIZARD` 的项都有 `apply`、`revert`、`probe`、`appliedRegex`，且 `appliedRegex` 能编译 |
| RR-06c | 覆盖套路 | 各档案 `trick` 覆盖 {1,2,3,4,5,6,7,8,10,21} |
| RR-06d | 安全 | `verified == true` 的项不得是 `UNINSTALL` 且 `optional == false`（卸载必须是可选项） |
| RR-06e | WIZARD 项 | 都有 `intent` |

- [ ] **Step 1:** 写测试并运行，期望通过。
- [ ] **Step 2:** 提交 `test(regression): RR-06 profile health`。
