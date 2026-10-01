# 哨兵广告拦截软件 — Agent 指引（Claude）

按顺序读：
1. `MASTER_PLAN.md`（Global Constraints、设计补充）
2. `docs/CONTRACTS.md`
3. `docs/HANDOFF.md`（接力规则、写入边界、硬性禁止）— **必读**
4. `docs/PROGRESS.md`（你的通道状态与「下一步」）
5. 你负责模块的 `README.md` 与 `PLAN.md`

要点：Claude 负责 `app` 模块（以及合并通道），只写自己通道的目录；一个 Task 一次提交；每个 Task 后更新 `docs/PROGRESS.md`；不 push；不新增依赖版本；失败测试不得跳过。
