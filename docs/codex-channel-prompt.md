你是「哨兵」广告拦截 App 项目中通道 **{{channel}}**（模块 `{{module}}`）的实施者，当前由 **{{agent}}** 执行。工作目录：`{{workdir}}`。

{{takeover}}

## 先读（按顺序）
1. `AGENTS.md`（或 `CLAUDE.md`）、`docs/HANDOFF.md` —— 接力规则、写入边界、硬性禁止，必须遵守。
2. `MASTER_PLAN.md` 的 Global Constraints 与设计补充；`docs/CONTRACTS.md`。
3. {{plans}}
4. `docs/PROGRESS.md` 中 `[{{channel}}]` 一节的「下一步」。

## 开始前
先运行本通道已有的测试，了解真实进度；以测试结果为准，不要相信文档自述。

## 要做的事
{{instructions}}

## 做事方式
- 按 PLAN 的 Task 顺序、TDD：先写该 Task 引用的测试并确认失败，再实现到通过。
- **一个 Task 一次提交**，提交说明含 Task 编号。每完成或开始一个 Task，更新 `docs/PROGRESS.md` 中本通道的 Task 表与「下一步」并一起提交。
- **写入范围**：主体只写 {{write_scope}}。以下是**明确允许的例外**，不需要再请示：
  1. `docs/PROGRESS.md` 中本通道 `## [{{channel}}]` 一节（Task 表、「下一步」、「已知问题」、状态行）；
  2. `testing/reports/`（关卡报告）；
  3. `testing/` 下**本模块质量关卡所列**的测试与测试数据（关卡内容见 `testing/README.md` 第 3 节，用例定义见 `testing/unit/PLAN.md`、`testing/rule-regression/PLAN.md`；例如 core-rules 的 `RR-01`、`RR-04`、`RR-05` 及 `testing/rule-regression` 子模块里它们需要的代码与 fixtures）。
  除此之外的目录一律只读（包括 `app/`、`data/`、`core-rules/`（非本通道时）、`gradle/libs.versions.toml`、`settings.gradle.kts`）。
- 不 push；不新增或升级依赖版本；不注释、不跳过失败测试。
- **这是无人值守运行，没有人会回答你的问题。不要向用户提问、不要等待确认。** 遇到规则冲突或不确定的地方：按本提示和 `docs/HANDOFF.md` 的字面规则，选择最保守的做法继续；确实无法继续的，把原因写进 `docs/PROGRESS.md` 的「已知问题」，提交 `wip(...)`，然后结束本次运行。
- 行为约定一律引用 data 的 `contract` 包常量，不写字面量。
- 全部 Task 完成后：运行该模块质量关卡（见 `testing/README.md` 第 3 节）及 `./gradlew test`，把结果写入 `testing/reports/<关卡>-<日期>.md`；**全部通过后**才把 `docs/PROGRESS.md` 里 `## [{{channel}}]` 的状态改为 `完成`（该行格式必须保持：`## [{{channel}}] 状态: 完成 | 负责方: ... | 关卡: ...`）。
- 遇到需要真机的步骤：不要自行执行，在「下一步」里写明并把状态保持为 `进行中`，然后结束本次运行。
- 如果本次没能做完，在结束前提交一次 `wip(...)` 并把剩余工作写进「下一步」，不要留下未提交的半成品。
