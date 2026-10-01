# 板块④ 实际验收 Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Find X7 Ultra 上按规格第 8 节的验收标准，确认软件可以交付自用和家用。

**Architecture:** 以真机实测为主：自动化测试全量回归 + 专项实测 + 一周日常使用。所有检查单、统计脚本、报告放在 `testing/acceptance/`。

**Tech Stack:** adb、屏幕录制、ColorOS 电池统计、事件表导出脚本。

**Spec:** `docs/superpowers/specs/2026-10-01-adblock-sentinel-design.md`（8 节「验收标准」）；总则见 `testing/README.md`。

## 测试边界

- **测**：整体是否达到验收标准，包括核心功能不受影响、拦截率、耗电、误判、故障恢复、易用性。
- **不测**：单个功能的正确性（前三个板块负责）。
- **前置**：G1～G8 全部通过。验收中发现问题 → 修复 → 重跑受影响模块的关卡 → 再继续验收。

## 验收标准

| 编号 | 项目 | 标准 |
|---|---|---|
| AC-01 | 自动化全量回归 | `./gradlew test`、`./gradlew :testing:rule-regression:testDebugUnitTest`、`./gradlew :testing:device-integration:connectedDebugAndroidTest` 全部通过 |
| AC-02 | 误拦 | 用最终版规则重跑 RR-01a，0 命中 |
| AC-03 | 跳转误判 | 真机按 `UT-AY-6-03`～`UT-AY-6-22` 的 20 个场景各操作 1 次，0 次被回退 |
| AC-04 | 激励视频 | 10 个 App 在「静默播完」模式下奖励全部到账，音量全部恢复 |
| AC-05 | 故障恢复 | 最终版本重跑 DI-61a～d 全部通过 |
| AC-06 | 一周实测 | 30 个 App 核心功能全部正常；开屏拦截率 ≥95%；额外耗电 <3% |
| AC-07 | 易用性 | 一位家人在无人指导下完成 3 件事：开启防护、给某个 App 临时放行、找到今日拦截数 |

---

### Task 1: 检查单与统计工具

**Files:**
- Create: `testing/acceptance/top30-apps.md`（30 个常用 App 及各自 2～3 项核心功能检查点，例如：微信-发消息/支付；淘宝-搜索/下单页；银行-登录查余额）
- Create: `testing/acceptance/scripts/export-events.sh`（`adb exec-out run-as com.sentinel.adblock` 导出数据库中的事件表）
- Create: `testing/acceptance/scripts/splash-rate.md`（开屏拦截率的测量方法，见下）
- Create: `testing/acceptance/report-template.md`

**开屏拦截率测量方法**：30 个 App 中挑出所有带开屏广告的，每个冷启动 10 次（每次先从最近任务划掉）并录屏；开屏广告可见超过 1 秒记为「漏网」。拦截率 = 1 − 漏网次数 / 总启动次数。

**耗电测量方法**：连续 24 小时正常使用后，读取 ColorOS「电池 → 耗电详情」中「哨兵」的占比，取一周中 3 天的平均值。

- [ ] **Step 1:** 写全部文件。
- [ ] **Step 2:** 提交 `test(acceptance): checklists and tools`。

### Task 2: 专项验收（AC-01～AC-05）

- [ ] **Step 1:** 跑 AC-01 三条命令，记录结果。
- [ ] **Step 2:** AC-02：重跑 RR-01a。
- [ ] **Step 3:** AC-03：按 20 个场景逐一操作并录屏，记录每个场景的结果。
- [ ] **Step 4:** AC-04：10 个 App 各领 1 次奖励，记录到账情况与音量。
- [ ] **Step 5:** AC-05：重跑 DI-61a～d。
- [ ] **Step 6:** 结果写入 `testing/acceptance/reports/acceptance-<日期>.md`；有失败项 → 修复 → 重跑相关关卡 → 重做该项。

### Task 3: 一周实测与易用性（AC-06、AC-07）

- [ ] **Step 1:** 第 1 天：按 `top30-apps.md` 逐项检查核心功能，测开屏拦截率。
- [ ] **Step 2:** 第 2～7 天：日常使用；每天晚上导出事件表，检查 guard 降级记录与临时放行记录，任何「核心功能受影响」都记录在报告中并修复。
- [ ] **Step 3:** 第 3、5、7 天：记录耗电占比。
- [ ] **Step 4:** 第 7 天：再做一次 30 个 App 核心功能检查；完成 AC-07 易用性走查。
- [ ] **Step 5:** 填写验收报告，给出结论（通过 / 不通过 + 遗留问题清单）并提交。
