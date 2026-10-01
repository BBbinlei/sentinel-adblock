# 进度表

> 规则见 `docs/HANDOFF.md`。每个通道标题行的格式固定，监督脚本只解析「状态」：
> `## [通道] 状态: <待办|进行中|等待|完成|已合并> | 负责方: <codex|claude|用户> | 关卡: <Gx>`

（`core-rules`、`data` 直接在 `main` 上做，状态到 `完成` 即视为已合并；其余通道在各自 worktree 分支上做，由合并通道并入 `main` 后标 `已合并`。）

## [core-rules] 状态: 进行中 | 负责方: codex | 关卡: G1

| Task | 状态 | 最后提交 |
|---|---|---|
| Task 1: 规则模型与域名解析器 | 阻塞：基线测试无法启动 | 见本次 wip 提交 |
| Task 2: 域名编译器与匹配器 | 完成 | c0c0b11 |
| Task 3: 内置目录 | 完成 | 8a9ff77 |
| Task 4: 选择器 | 完成 | d022343 |
| Task 5: UI 规则解析、索引与内置规则 | 完成 | 247887e |
| Task 6: 通知匹配 | 完成 | 本提交（Task 6） |

- 下一步：在允许 Gradle 写入缓存和创建本地 socket 的执行环境中先运行 `./gradlew :core-rules:test`；然后从 Task 1 按 TDD 实施 Task 1～6，每 Task 一次提交；完成 MT-CR、RR-01/04/05，执行 G1、全量 test 与 check-merge。本会话未实施源码或测试；收尾发现外部新增提交 `70291f1`（Task 1）及未提交的 domain 测试文件，尚未验证，不得视为关卡完成。
- 已知问题：2026-10-02 当前沙箱禁止写入默认 Gradle 缓存锁；复制已有缓存到临时可写目录后，Gradle 仍因 `FileLockContentionHandler` 创建本地 socket 被拒绝（`java.net.SocketException: Operation not permitted`）而在构建启动前失败。模块基线、回归、全量 test 和 check-merge 的测试项均未执行成功，G1 未通过，详见 `testing/reports/G1-2026-10-02.md`。本会话未更改依赖、未跳过失败测试、未执行真机步骤。存在另一执行者并发写入本通道的迹象，接手时先核实实际 HEAD 与测试结果，避免重复实施。

## [data] 状态: 待办 | 负责方: codex | 关卡: G2

| Task | 状态 | 最后提交 |
|---|---|---|
| Task 1: 数据库、实体与生效配置 | 待办 | — |
| Task 2: 通用仓库 | 待办 | — |
| Task 3: App 配置仓库与 App 登记 | 待办 | — |
| Task 4: 规则存储 | 待办 | — |
| Task 5: 规则构建与订阅更新 | 待办 | — |
| Task 6: Koin 模块与模块接线入口 | 待办 | — |

- 下一步：编写 MT-CR-01～03、RR-01/RR-04/RR-05，执行 G1 三项构建并提交报告；UT-CR-6-01～04 已通过。
- 已知问题：无

## [engine-vpn] 状态: 待办 | 负责方: codex | 关卡: G3

| Task | 状态 | 最后提交 |
|---|---|---|
| Task 1: 报文解析与构造 | 待办 | — |
| Task 2: DNS 报文与判定 | 待办 | — |
| Task 3: 上游解析与缓存 | 待办 | — |
| Task 4: TUN 配置与报文循环 | 待办 | — |
| Task 5: VpnController 与服务接线 | 待办 | — |
| Task 6: 私人 DNS 检测与服务守护 | 待办 | — |
| Task 7: 故障安全 | 待办 | — |

- 下一步：从 Task 1 开始。
- 已知问题：无

## [engine-system] 状态: 待办 | 负责方: codex | 关卡: G5

| Task | 状态 | 最后提交 |
|---|---|---|
| Task 1: 配置档案 | 待办 | — |
| Task 2: Shizuku 网关 | 待办 | — |
| Task 3: 操作执行与撤销 | 待办 | — |
| Task 4: 按 App 权限同步 | 待办 | — |
| Task 5: 巡检与状态上报 | 待办 | — |
| Task 6: 崩溃日志采集 | 待办 | — |
| Task 7: 接线 | 待办 | — |

- 下一步：从 Task 1 开始。
- 已知问题：无

## [engine-notify] 状态: 待办 | 负责方: codex | 关卡: G6

| Task | 状态 | 最后提交 |
|---|---|---|
| Task 1: 判定与内置规则 | 待办 | — |
| Task 2: 划除学习 | 待办 | — |
| Task 3: 编排器 NotifyEngine | 待办 | — |
| Task 4: 服务接线 | 待办 | — |

- 下一步：从 Task 1 开始。
- 已知问题：无

## [engine-a11y] 状态: 待办 | 负责方: codex | 关卡: G4

| Task | 状态 | 最后提交 |
|---|---|---|
| Task 1: 前台跟踪与启动识别 | 待办 | — |
| Task 2: 规则点击（开屏 / 弹窗 / 自动续费） | 待办 | — |
| Task 3: 学习模式候选规则 | 待办 | — |
| Task 4: 误伤探测信号 | 待办 | — |
| Task 5: 激励视频处理与音量保护 | 待办 | — |
| Task 6: 跳转回退 | 待办 | — |
| Task 7: 编排器 A11yBrain | 待办 | — |
| Task 8: 服务接线、悬浮提示与通知动作 | 待办 | — |

- 下一步：从 Task 1 开始。
- 已知问题：无

## [guard] 状态: 待办 | 负责方: codex | 关卡: G7

| Task | 状态 | 最后提交 |
|---|---|---|
| Task 1: 决策策略 | 待办 | — |
| Task 2: 运行时执行、通知与观察期评估 | 待办 | — |

- 下一步：从 Task 1 开始。
- 已知问题：无

## [app] 状态: 待办 | 负责方: claude | 关卡: G3(Task 1–2) / G8

| Task | 状态 | 最后提交 |
|---|---|---|
| Task 1: 应用骨架与全局接线 | 待办 | — |
| Task 2: 最小首页（M3 可用版本） | 待办 | — |
| Task 3: 引导向导 | 待办 | — |
| Task 4: 完整首页、引擎日志、系统净化与撤销记录 | 待办 | — |
| Task 5: 应用页与应用详情 | 待办 | — |
| Task 6: 规则页 | 待办 | — |
| Task 7: 快捷开关 | 待办 | — |

- 下一步：Task 1–2（M3）；Task 5、6、7 只依赖 data，可提前做；Task 3 等 engine-system 合并，Task 4 等 guard 合并（此时把状态改为 `等待`）。
- 已知问题：无

## [merge] 状态: 待办 | 负责方: claude | 关卡: —

- 职责：当某个通道状态为 `完成` 时，在 `main` 上按顺序（notify → system → a11y → vpn → guard → app）合并；每次先在该通道 worktree 里运行 `scripts/check-merge.sh <模块>`，通过后合并，再在 `main` 跑 `./gradlew assembleDebug test`，并把该通道状态改为 `已合并`。
- 下一步：等待第一个通道完成。
- 已知问题：无

## [survey] 状态: 待办 | 负责方: 用户 | 关卡: —

- 职责：M0 Task 1 实机核实。用户连接手机后运行 `docs/device-survey/scripts/survey.sh`；报告写入 `docs/device-survey/find-x7-ultra-survey.md`。
- 下一步：等用户。
- 已知问题：无
