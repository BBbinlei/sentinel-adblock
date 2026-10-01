# 跨模块行为契约

> 引擎之间代码上互不依赖，只通过 data 层的表交换信息。这些表的**行为约定**编译器管不了，所以写在这里，并且**数值与枚举落成 data 里的常量**（`com.sentinel.data.contract`），双方都引用常量、不写字面量。契约改动属于 `docs/PARALLEL_SCHEDULE.md` 第 5 节的「变更」，需要登记。

## C1 奖励窗口（a11y 写 → vpn 读）

| 项 | 约定 | 常量 |
|---|---|---|
| 谁开 | 只有 engine-a11y：用户点击匹配 `rewardEntry` 的按钮，且该 App 的激励模式不是「直接拦截」 | — |
| 时长 | 60 秒，从开窗时刻算；对同一 App 再次开窗，以最新一次为准（`until = now + TTL`） | `RewardWindowContract.TTL_MS` |
| 放行范围 | 窗口内、只针对开窗的那个 App、只放行 `AD_SDK` 标签的域名；其他标签（`AD` 等）继续拦截 | `RewardWindowContract.RELEASED_TAGS` |
| 谁读 | 只有 engine-vpn（`DnsDecider` 第 5 步）；其他模块不读 | — |
| 过期 | 读取方以 `until > now` 判断；不需要谁去「关窗」；过期后不再放行 | — |
| 故障 | 表读不到或为空 = 窗口关闭（更严，视频加载失败但不会漏拦） | — |

## C2 误伤信号（各引擎写 → guard 读）

| SignalKind | 谁发 | ruleId | 含义 |
|---|---|---|---|
| `USER_UNDO` | engine-a11y（用户撤销了跳转回退） | 为空 | 用户认为刚才的处理错了 |
| `TEMP_ALLOW` | data `AppConfigRepository.tempAllow`（由 app 的「临时放行」触发） | 为空 | 用户认为该 App 被拦坏了 |
| `RETRY_STORM` | engine-vpn（同一域名短时间被拦并反复重试） | 命中的规则 | 被拦的东西是 App 必需的 |
| `CRASH_DIALOG` | engine-a11y（识别到「已停止运行」弹窗） | 为空 | App 崩了 |
| `COLD_START_LOOP` | engine-a11y（短时间反复冷启动） | 为空 | App 起不来 |
| `DROPBOX_CRASH` | engine-system（读取系统崩溃日志） | 为空 | App 崩了（旁证） |

硬规则：
- **引擎只发信号、不处置**；停用规则、通知、观察期延长只有 guard 做。引擎不得依赖 guard。
- **每个 `SignalKind` 必须在 guard 的决策表里有明确处理**（包括「忽略」）。新增 `SignalKind` 必须同时改 guard 决策表和本表。
- 信号只写 `pkg` 有效的 App；该 App 生效级别为 `OFF`（含排除名单）时不发。
- 发信号失败不得影响引擎主流程（吞掉异常、记日志）。

常量：`SignalContract.EMITTERS: Map<SignalKind, EngineId?>`（`null` 表示由 data/app 层发出）。

## C3 规则热更新（data 写 → 各引擎读）

- 规则重建完成后，`GlobalStateRepository.bumpRuleVersion()` 加一，**这是唯一的更新通知**。
- 各引擎订阅 `ruleVersion`，变化后重新 `RuleStore.load*()` 并热替换内存规则，不重启服务。
- 重建过程中任何异常都不 bump，保持旧规则（旧规则继续生效）。

## C4 引擎状态（各引擎写 → app/guard 读）

- 状态机：`NOT_SETUP`（用户尚未授权/开启）、`RUNNING`、`DEGRADED`（在跑但能力下降，必须带 `message`）、`STOPPED`（曾经运行，现在不在跑）。
- 服务 `onCreate/onConnected` 上报 `RUNNING`，`onDestroy/onUnbind` 上报 `STOPPED`。
- `ServiceWatchdog` 靠「曾经为 `RUNNING`」判断用户是否想要该引擎，所以引擎**不得**在正常停用时把状态写回 `NOT_SETUP`。

## C5 模块接线（每个模块自己写 → app 用 ServiceLoader 发现）

- 每个引擎/guard/data 导出一个 `ModuleEntry`（见 `data/PLAN.md` Task 6），登记在自己模块的 `META-INF/services/com.sentinel.data.module.ModuleEntry`。
- 模块自己负责：创建自己用到的通知渠道、启动自己的协程、注册自己的周期任务。**app 不知道里面有什么**。
- 新增一个引擎 = 新增一个模块 + 一个 `ModuleEntry`，**不改 app**。

## C6 通用故障原则

任何引擎的异常路径只能让拦截变弱，不能让手机断网或 App 不可用（见 `MASTER_PLAN.md` Global Constraints）。契约相关的表现为：读不到数据 → 按更宽松的一侧处理（窗口关闭、规则不生效）；写失败 → 吞掉异常。

## 契约测试

- 常量与不变式：`UT-DA-2-10`～`UT-DA-2-12`（data 内）、`UT-VP-5-07`（C1 故障路径：窗口读取失败视为关闭）、`UT-DA-6-02`、`UT-AP-1-02`～`UT-AP-1-04`（入口发现与进程过滤）。
- 跨引擎行为：`MT-CT-01`～`MT-CT-04`（`:testing:rule-regression`，见 `testing/unit/PLAN.md` Task CT），作为 G7 的一部分通过。
