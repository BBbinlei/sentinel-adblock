# 板块③ 设备集成测试 Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 验证真实系统服务（VpnService、AccessibilityService、Shizuku、NotificationListenerService）、多进程协作与故障安全在真实 Android 环境中按设计工作。

**Architecture:** 分两部分。**自动化部分**放在 Gradle 子模块 `:testing:device-integration`（`com.android.test` 类型，目标为 `:app`），在 Android 15 模拟器上用 `connectedDebugAndroidTest` 运行；测试 APK 内带一个夹具 Activity，用来模拟开屏页和发送通知。**真机部分**是 Find X7 Ultra 上的检查单，结果写入 `testing/device-integration/reports/`。

**Tech Stack:** AndroidX Test、UiAutomator、adb；真机部分人工执行，必要时配合 adb 脚本。

**Spec:** `docs/superpowers/specs/2026-10-01-adblock-sentinel-design.md`（8 节「设备集成测试」、10 节待核实项）；总则见 `testing/README.md`。

## 测试边界

- **测**：真实系统服务的行为、跨进程数据同步、权限与系统限制、故障注入后的恢复、ColorOS 上的实际效果。
- **不测**：纯逻辑（→ ①）、规则质量（→ ②）、长期稳定性与拦截率（→ ④）。
- **前置**：模拟器测试前用 adb 预授权：`adb shell appops set com.sentinel.adblock ACTIVATE_VPN allow`；无障碍服务通过 `settings put secure enabled_accessibility_services` 开启；通知使用权通过 `cmd notification allow_listener` 开启。
- **真机测试**：只在你同意并连接手机后执行；涉及系统设置的修改都要有撤销步骤。

---

### Task 1: 子模块与夹具

**Files:**
- Create: `testing/device-integration/build.gradle.kts`（`com.android.test`，`targetProjectPath = ":app"`；`settings.gradle.kts` 中 `include(":testing:device-integration")`）
- Create: `testing/device-integration/src/main/kotlin/com/sentinel/di/FixtureSplashActivity.kt`（显示「跳过 3」按钮并记录被点击次数）
- Create: `testing/device-integration/src/main/kotlin/com/sentinel/di/FixtureNotifier.kt`（以指定渠道发通知）
- Create: `testing/device-integration/scripts/grant-emulator.sh`（上述前置授权命令）

- [ ] **Step 1:** 建子模块与夹具；`./gradlew :testing:device-integration:connectedDebugAndroidTest` 能在模拟器上运行（0 个测试）。
- [ ] **Step 2:** 提交 `test(device): integration module and fixtures`。

### Task 2: 模拟器自动化用例

**Files:**
- Create: `testing/device-integration/src/main/kotlin/com/sentinel/di/VpnIntegrationTest.kt`
- Create: `testing/device-integration/src/main/kotlin/com/sentinel/di/MultiProcessDataTest.kt`
- Create: `testing/device-integration/src/main/kotlin/com/sentinel/di/A11yIntegrationTest.kt`
- Create: `testing/device-integration/src/main/kotlin/com/sentinel/di/NotifyIntegrationTest.kt`

| 编号 | 用例 | 步骤 | 断言 |
|---|---|---|---|
| DI-01 | VPN 启动与 DNS 过滤 | 启动 VPN 服务，等待状态 RUNNING | `InetAddress.getByName("gdt.qq.com")` 为 `0.0.0.0`；`www.baidu.com` 解析为非 0 地址；事件表出现 `DNS_BLOCKED` |
| DI-02 | 多进程数据同步 | 主进程把某 App 设为 OFF | 5 秒内 `:vpn` 进程重建 TUN，且新 TUN 的 disallowed 含该 App（通过调试状态接口读取） |
| DI-03 | 杀掉 `:vpn` 进程 | `Process.killProcess(<:vpn pid>)` | 3 秒内对正常网站的 HTTP 请求成功 |
| DI-05 | 开屏跳过 | UiAutomator 回到桌面 → 启动 `FixtureSplashActivity` | 2 秒内「跳过 3」被点击 1 次；事件表出现 `SPLASH_SKIPPED` |
| DI-07 | 通知过滤 | 为测试包添加一条用户通知规则 → `FixtureNotifier` 发「限时福利」 | 2 秒内通知被清除；事件表出现 `NOTIFICATION_CANCELLED` |

- [ ] **Step 1:** 写用例。
- [ ] **Step 2:** 运行 `scripts/grant-emulator.sh` 与 `./gradlew :testing:device-integration:connectedDebugAndroidTest`，期望全部通过。
- [ ] **Step 3:** 提交 `test(device): emulator integration cases`。

### Task 3: 真机检查单（Find X7 Ultra）

**Files:**
- Create: `testing/device-integration/checklists/real-device.md`（下表的可勾选版本）
- Create: `testing/device-integration/reports/<关卡>-<日期>.md`（每次执行的记录）

**网络拦截（G3）**
| 编号 | 用例 | 判定标准 |
|---|---|---|
| DI-11 | DNS 请求归属 App | 打开 5 个常用 App，事件表中 `DNS_BLOCKED` 的 pkg 正确率 ≥90%；不达标时记录，并确认「pkg 为 null 按 STANDARD 判定」生效 |
| DI-12 | 真机杀 `:vpn` 进程 | `adb shell kill <pid>` 后网络立即可用 |
| DI-13 | 私人 DNS 指定服务器 | 设置为指定服务器后首页出现警告文案；改回「自动」后警告消失 |
| DI-14 | 被其他 VPN 取代 | 开启另一个 VPN App 后首页显示「VPN 授权被撤销或被其他 VPN 取代」，网络正常 |

**界面拦截（G4）**
| 编号 | 用例 | 判定标准 |
|---|---|---|
| DI-21 | 侧载开启无障碍 | 按向导的「允许受限设置」流程能开启；记录 ColorOS 实际路径并更新向导图示 |
| DI-22 | 后台拉起源 App | 跳转回退时 `startActivity` 成功；若被拦，确认回退到「返回键」的效果并记录 |
| DI-23 | 区分用户操作与自动跳转 | 5 个有开屏广告的 App 各试 5 次：主动点击广告不被回退；无交互跳转被回退；误判 0 次 |
| DI-24 | 激励视频静默播完 | 3 个 App：音量恢复、奖励到账；播放中途切走，音量也恢复 |
| DI-25 | 摇一摇 | 2 个有摇一摇开屏的 App：晃动手机后被回退或广告未加载 |

**系统净化（G5）**
| 编号 | 用例 | 判定标准 |
|---|---|---|
| DI-31 | 逐项验证档案 | 每个非 WIZARD 项：apply → 对应广告消失 → undo → 恢复 → 再 apply；相关系统功能无异常。通过的项交给 engine-system 改为 `verified=true` |
| DI-32 | 重启 | 已应用项仍生效；首页文案「Shizuku 未激活（不影响已生效项）」；不重复执行 |

**通知过滤（G6）**
| 编号 | 用例 | 判定标准 |
|---|---|---|
| DI-41 | adb 模拟营销通知 | 为 shell 包加一条临时规则，`adb shell cmd notification post -t "限时福利" test tag msg` 后通知被清除并出现在日志；测试后删除临时规则 |

**界面与全链路（G3 / G8）**
| 编号 | 用例 | 判定标准 |
|---|---|---|
| DI-51 | 首个可用版本（G3） | 安装 → 开启防护 → 打开 3 个有广告的 App → 今日拦截数增长 |
| DI-52 | 全流程走查（G8） | 全新安装 → 向导 5 步 → 首页 → 设某 App 为强力 → 临时放行 → 系统净化一键处理 → 撤销一项；每步符合 `app/README.md` 描述 |

**故障演练（G8）**
| 编号 | 用例 | 判定标准 |
|---|---|---|
| DI-61a | 杀 `:vpn` 进程 | 网络正常，首页显示已停止 |
| DI-61b | 断开 Shizuku | 已生效项保持，首页提示 |
| DI-61c | 注入损坏规则 | 用 `run-as` 把 `files/rules/domains.bin` 改为损坏内容 → 引擎自动回滚到上一版，拦截继续 |
| DI-61d | 强制停止主进程 | VPN 继续工作；重新打开 App 状态正确 |

- [ ] **Step 1:** 写检查单文件。
- [ ] **Step 2:** 各关卡执行对应用例，记录到 `reports/`；未通过项在报告中写明原因与修复提交。
