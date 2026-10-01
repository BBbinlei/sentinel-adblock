# testing 测试模块（第 9 个模块）

项目所有测试内容的**唯一来源**。前 8 个模块的实施计划只引用这里的测试编号，不再各自定义测试。

## 1. 四个板块与边界

| 板块 | 计划文档 | 测什么 | 不测什么 | 运行环境 | 代码位置 |
|---|---|---|---|---|---|
| ① 单元测试 | `unit/PLAN.md` | **单元级**：单个类/函数的逻辑；**模块级**：一个模块通过对外接口整体工作（相邻模块用假实现或内存数据库代替） | 真实系统服务（VPN、无障碍、Shizuku、通知）的行为；跨进程 | 电脑 JVM（纯 Kotlin 或 Robolectric） | 各模块自己的 `src/test/`（需要访问模块内部类，Gradle 要求放在模块内） |
| ② 规则回归 | `rule-regression/PLAN.md` | 规则与真实数据的匹配质量：录制的页面快照、真实订阅文件、常用域名、ColorOS 配置档案 | 引擎运行时行为 | 电脑 JVM | Gradle 子模块 `:testing:rule-regression` |
| ③ 设备集成 | `device-integration/PLAN.md` | 真实系统服务与多进程：模拟器自动化 + Find X7 Ultra 真机检查单 | 长期稳定性、拦截率统计 | Android 模拟器 + 真机 | Gradle 子模块 `:testing:device-integration`（自动化部分）；真机记录在 `device-integration/reports/` |
| ④ 实际验收 | `acceptance/PLAN.md` | 规格第 8 节的验收标准：30 个 App 一周实测、拦截率、耗电、误判、易用性 | 新功能开发 | 真机 | `acceptance/`（检查单、脚本、报告） |

## 2. 编号规则

| 前缀 | 含义 | 示例 |
|---|---|---|
| `UT-<模块>-<任务>-<序号>` | 单元级测试 | `UT-CR-2-01` = core-rules 第 2 个任务的第 1 个用例 |
| `MT-<模块>-<序号>` | 模块级测试 | `MT-VP-01` |
| `RR-<序号>` | 规则回归 | `RR-01` |
| `DI-<序号>` | 设备集成 | `DI-11` |
| `AC-<序号>` | 实际验收 | `AC-06` |

模块缩写：`CR` core-rules、`DA` data、`GD` guard、`VP` engine-vpn、`AY` engine-a11y、`NT` engine-notify、`SY` engine-system、`AP` app。

## 3. 与开发流程的关系

- **开发中（TDD）**：模块计划的每个任务写明「按 UT-XX-n-* 编写测试」。开发者先按本模块定义写测试、确认失败，再实现到通过。
- **模块完成后（质量关卡）**：立即运行该模块的全部单元级 + 模块级测试，以及下表中关联的回归、设备测试。**全部通过才能进入依赖它的下一个里程碑**；每个关卡同时跑一遍 `./gradlew test`，防止改坏其他模块。

| 关卡 | 时机（里程碑完成后） | 必须通过 |
|---|---|---|
| G1 | M1 core-rules | `UT-CR-*`、`MT-CR-*`、`RR-01`、`RR-04`、`RR-05`（需先完成 `rule-regression/PLAN.md` Task 1 中的子模块搭建；快照录制工具留到 M4） |
| G2 | M2 data | `UT-DA-*`、`MT-DA-*` |
| G3 | M3 engine-vpn + app 最小壳 | `UT-VP-*`、`MT-VP-*`、`UT-AP-1-*`、`UT-AP-2-*`、`DI-01`～`DI-03`、`DI-11`～`DI-14`、`DI-51` |
| G4 | M4 engine-a11y | `UT-AY-*`、`MT-AY-*`、`RR-02`、`RR-03`、`DI-05`、`DI-21`～`DI-25` |
| G5 | M5 engine-system | `UT-SY-*`、`MT-SY-*`、`RR-06`、`DI-31`、`DI-32` |
| G6 | M6 engine-notify | `UT-NT-*`、`MT-NT-*`、`DI-07`、`DI-41` |
| G7 | M7 guard | `UT-GD-*`、`MT-GD-*` |
| G8 | M8 app 完整界面 | `UT-AP-*`、`MT-AP-*`、`DI-52`、`DI-61` |
| G9 | M9 验收 | `AC-01`～`AC-07` |

关卡结果写入 `testing/reports/<关卡>-<日期>.md`：通过/失败的编号、失败原因、修复提交。

## 4. Review Focus（最容易出问题、需要专门测试的 5 种情况）

| # | 情况 | 期望 | 对应测试 |
|---|---|---|---|
| 1 | 系统「私人 DNS」设为指定服务器 | 首页警告并引导关闭 | `UT-VP-6-01`、`UT-AP-4-03`、`DI-13` |
| 2 | VPN 被其他 VPN 顶掉 / 授权撤销 / 内部崩溃 | 状态「已停止」、网络直连、不崩溃 | `UT-VP-7-01`～`UT-VP-7-05`、`MT-VP-02`、`DI-03`、`DI-14` |
| 3 | 重启后 Shizuku 未激活 | 已生效改动保持、不重复执行、首页提示 | `UT-SY-5-03`、`MT-SY-03`、`UT-AP-4-03`、`DI-32` |
| 4 | 设置完成后新装银行 App | 立即排除出 VPN，各引擎不生效 | `UT-DA-3-01`、`UT-VP-5-02`、`MT-DA-02` |
| 5 | 激励视频静默中途离开 / 服务被杀 | 媒体音量一定恢复 | `UT-AY-5-02`、`UT-AY-5-06`、`MT-AY-02`、`DI-24` |

## 5. 通用约定

- 测试框架：JUnit5 + kotlin.test（纯 Kotlin 模块）；Android 模块单元测试用 JUnit4 + Robolectric + kotlin.test；Flow 用 Turbine；网络用 MockWebServer。
- 时间一律通过可控 `Clock` 注入，测试中不得 `Thread.sleep`。
- 测试数据（快照、订阅样本、域名表）放在 `testing/rule-regression/fixtures/` 或各模块 `src/test/resources/`，不得包含个人隐私信息。
- 失败的测试不能被注释或跳过来「通过」关卡；确属用例错误时，先修改本模块文档中的用例定义，再改代码。
