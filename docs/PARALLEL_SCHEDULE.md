# 并行调度方案（M3～M6）

> 配合 `MASTER_PLAN.md` 使用。本文只回答一件事：G2 通过后，engine-vpn / engine-a11y / engine-system / engine-notify 四个模块怎么同时做，才不会互相踩。

## 1. 总体时间线

```
阶段 A（串行，不可并行）
  M0 Task 0 骨架 ──► M1 core-rules ─G1─► M2 data ─G2
  M0 Task 1 实机核实（只读，与 M1/M2 同时做，人工执行，不占 AI 通道）

阶段 B（并行，G2 通过 + 冻结点 F1 之后）
  通道 1  engine-vpn + app Task 1–2  ─G3
  通道 2  engine-a11y                ─G4
  通道 3  engine-system              ─G5   ← 需要实机核实报告
  通道 4  engine-notify              ─G6

阶段 C（串行）
  合流点 F2 ─► M7 guard ─G7 ─► M8 app Task 3–7 ─G8 ─► M9 验收
```

## 2. 冻结点（并行前必须完成）

### F1：G2 通过后立即冻结，打 tag `g2-frozen`

| 冻结项 | 原因 |
|---|---|
| `data` 的 Room schema（表、字段、DAO 签名） | 四个引擎都读写 `events`、`signals`、`engine_status`、`reward_window`、`op_log`；任何人改 schema，其他三个同时编译失败 |
| `core-rules` 的公开 API | 引擎都消费 `EffectiveConfig`、`DomainTag`、`UiRule`、`NotifyRule` 等 |
| `docs/CONTRACTS.md` 与 data 的 `contract` 包常量、`ModuleEntry` 接口 | 四个通道对奖励窗口、误伤信号、规则热更新、引擎状态的共同依据 |
| `gradle/libs.versions.toml` 里所有依赖 | 并行期间禁止升级版本 |
| `settings.gradle.kts` 的模块列表 | Task 0 已经 include 全部 10 个模块，并行期间不再改 |

冻结期间如果某通道确实需要改 data / core-rules：**停下来，走第 5 节的变更流程**，不要自行修改。

### 接线问题已在计划里解决，不再需要预置空壳

原先担心的三件事（空 Koin 模块、`SentinelApp` 一次性接线、Worker 注册位置）已经通过 `MASTER_PLAN.md` 设计补充第 11、12 点从根上消除：

- 每个模块导出自己的 `ModuleEntry`，自己创建通知渠道、启动协程、注册周期任务；app 用 `ServiceLoader` 发现，**并行期间没有任何通道需要改 `SentinelApp.kt`**。
- guard 在 M7 之前没有入口，app 照常启动，不再需要 `guardModule` 空壳。
- 跨模块行为约定见 `docs/CONTRACTS.md`，数值落成 data 的 `contract` 包常量，F1 冻结时一并冻结。

F1 唯一需要确认的接线前提：data Task 6 的 `ModuleEntry` / `ProcessKind` 已在 G2 前完成并冻结。

## 3. 四个通道的分工

| 通道 | 范围 | 独占目录 | 额外前置 | 出口 |
|---|---|---|---|---|
| 1 | `engine-vpn/PLAN.md` Task 1–7 + `app/PLAN.md` Task 1–2 | `engine-vpn/`、`app/` | 无 | G3 |
| 2 | `engine-a11y/PLAN.md` Task 1–8 | `engine-a11y/` | 无 | G4 |
| 3 | `engine-system/PLAN.md` Task 1–7 | `engine-system/` | 实机核实报告（M0 Task 1），否则 Task 1 的档案没有 `verified = true` 的数据 | G5 |
| 4 | `engine-notify/PLAN.md` Task 1–4 | `engine-notify/` | 核实报告里的候选包名（可用占位值先做，报告出来再改） | G6 |

**写入边界（硬规则）：**

- 每个通道**只能写自己的独占目录**。通道 1 额外独占 `app/`，其他三个通道不得改 `app/`。
- `data`、`core-rules`、`guard`、`testing/*` 并行期间对所有通道只读。
- 引擎之间不得互相依赖（Global Constraints 已规定），所以各通道不会因为编译依赖而互相阻塞。
- 行为约定一律引用 `contract` 包常量，不写字面量（如 `60_000`、`AD_SDK`）；发现契约有歧义，走第 5 节变更流程，不要各自理解。
- 每个通道在自己模块内新增 `ModuleEntry` 与 `META-INF/services` 登记文件，**不改 `:app`**（通道 1 的 app 最小壳除外）。
- 引擎各自的测试放在自己的模块里；`testing/` 下的规则回归和设备集成测试，只在该通道的关卡阶段才追加，追加时只改本通道对应的子目录/文件。

## 4. 执行方式（推荐：子代理 + git worktree）

适用前提：已 `git init`（Task 0 Step 1 会做），且 F1 打了 tag。

1. 从 `g2-frozen` 为每个通道各开一个 worktree 和分支：
   `chan/vpn`、`chan/a11y`、`chan/system`、`chan/notify`。
2. 每个通道起一个子代理，只给它：对应模块的 `README.md` + `PLAN.md`、`MASTER_PLAN.md` 的 Global Constraints、本文第 3 节的写入边界。要求使用 `subagent-driven-development` 按 Task 逐个 TDD。
3. 每个通道自己跑完该模块的质量关卡（G3～G6）和 `./gradlew test`，通过后才提交合并请求。
4. 合并顺序见第 6 节。

不想用子代理的话，等价的做法是你自己开 4 个终端、各进一个 worktree，每个终端各开一个 Claude Code 会话。

## 5. 变更流程（并行期间必须改冻结项时）

1. 发现需求的通道**暂停**，在 `docs/CHANGE_REQUESTS.md` 追加一条：谁、要改什么、为什么、影响哪些通道。
2. 由主线（你或主会话）判断：能不能在本通道内绕开？绕不开才改。
3. 修改**只在 `main` 上做**，且只允许**向后兼容的新增**（新增表/列/方法，不改不删已有的）。
4. 改完后其他三个通道各自 `git merge main`（或让 Claude 用 sync 工具合并）再继续。
5. 破坏性修改（改已有签名/字段）：四个通道全部暂停，改完统一合并再恢复。

## 6. 合流点 F2

合并顺序按冲突风险从低到高，**每合一个就在 `main` 上跑一次 `./gradlew assembleDebug test`**：

1. `chan/notify`（改动面最小）
2. `chan/system`
3. `chan/a11y`
4. `chan/vpn`（含 `app/`，最后合）

四个都合并并通过全量测试后，打 tag `g3-g6-passed`，再开始 M7 guard。

**合并预期冲突点**（提前知道，别慌）：`libs.versions.toml`（若冻结纪律执行好则无冲突）、各 `AndroidManifest.xml`（每个引擎只改自己的，不冲突）。`SentinelApp.kt` 只有通道 1 的 app 最小壳会改，其他通道不碰。合并后在 `main` 上补跑 `UT-AP-1-01`～`UT-AP-1-03`，确认入口发现正常。

## 7. 风险与对策

| 风险 | 表现 | 对策 |
|---|---|---|
| schema 被偷偷改 | 合并时三个通道同时编译失败 | F1 冻结 + 第 5 节流程；合并前跑 `git diff g2-frozen -- data core-rules`，必须为空或仅有已登记的新增 |
| 引擎自行添加对 guard 的依赖 | 违反依赖方向，M7 时循环依赖 | 误伤信号只写 `signals` 表（设计补充第 4 点）；合并前检查各引擎 `build.gradle.kts` |
| 契约被各通道各自理解 | 奖励窗口时长、信号语义不一致，单测都过、合起来行为不对 | 以 `docs/CONTRACTS.md` 为准、引用常量；合并后跑 `MT-CT-01`～`MT-CT-04`（属于 G7，但建议每合并一个通道就提前跑能跑的部分） |
| 通道 3 等核实报告 | engine-system 无真实 `verified = true` 数据 | 报告在 M0 与 M1/M2 并行做，F1 前必须到位；若没到，通道 3 只做 Task 2、3（Shizuku 网关与执行器，不依赖报告），Task 1 的档案内容最后补 |
| 各通道对同一 data 接口理解不一致 | 同一个仓库方法语义不同 | data 的 `PLAN.md` Produces 签名就是契约；有歧义去看 `data/README.md`，仍然不清楚走第 5 节 |
| 实机测试资源只有一台手机 | G3～G6 的设备集成测试互相抢手机 | 单元与模块级测试各自并行；`testing/device-integration` 的真机用例**串行**排队，按 G3→G4→G5→G6 顺序跑 |
| 并行收益不大 | 你一个人审四份产出，审阅成为瓶颈 | 见第 8 节 |

## 8. 是否值得并行：建议

- **值得并行的情况：** 用子代理/多会话由 AI 同时实现，你只在合流点审阅。四个通道代码量大、互不依赖，墙钟时间能压到约 1/3～1/2。
- **不值得的情况：** 你亲自逐个模块审阅每个 Task。这种情况下审阅是瓶颈，并行只会增加合并成本，按 `MASTER_PLAN.md` 第 99 行的单人顺序做即可。
- **接线分散后：** 并行期间不再有人需要改同一个 `SentinelApp.kt`，合并冲突风险比最初版本明显下降。
- **折中方案（推荐）：** 只把 `engine-notify`（最小）和 `engine-system`（依赖 Shizuku，调试最耗时）与 `engine-vpn`（最重、也是首个可用版本的关键路径）并行，`engine-a11y` 在 `engine-notify` 完成后接着做。这样同时在跑的通道最多 3 个，合并风险和审阅压力都更可控。

## 9. 行动清单

- [ ] G2 前确认：data 的 `ModuleEntry`、`contract` 包常量已完成，`docs/CONTRACTS.md` 已审阅
- [ ] 实机核实报告完成（M0 Task 1），在 F1 之前交付
- [ ] G2 通过 → 打 tag `g2-frozen`
- [ ] 开 4 个 worktree，分发第 3 节的范围和写入边界
- [ ] 各通道通过各自关卡（G3～G6）
- [ ] 按第 6 节顺序合并，每步全量测试
- [ ] 打 tag `g3-g6-passed`，进入 M7
