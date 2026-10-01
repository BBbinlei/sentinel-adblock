# data 数据层

整个软件的「公共记事本」。两个进程（主进程、`:vpn`）共用同一个 Room 数据库，引擎之间不直接对话，全部通过这里交换信息。

## 详细功能

### 1. 数据库（Room，多进程共享）
| 表 | 存什么 |
|---|---|
| `app_config` | 每个 App 的设置：拦截级别、激励视频模式、各开关、临时放行截止时间、观察期截止时间、是否敏感应用 |
| `global_state` | 总开关、暂停截止时间、规则版本号 |
| `subscriptions` | 规则订阅：地址、格式、级别、上次更新时间、条数、错误信息 |
| `user_rules` | 学习模式生成或手动添加的规则 |
| `events` | 拦截事件（谁、哪种套路、哪条规则），首页统计用，保留 30 天 |
| `signals` | 引擎上报的误伤信号，交给 guard 处理 |
| `rule_overrides` | guard 对某 App 停用的规则，或用户「钉住」不许停用的规则 |
| `op_log` | 系统净化的每一次操作（改之前的状态、命令、结果），用于撤销 |
| `jump_exceptions` | 跳转回退的例外（A → B 允许） |
| `reward_windows` | 激励视频奖励窗口（某 App 在 60 秒内放行广告 SDK 域名） |
| `engine_status` | 四个引擎的运行状态，首页显示用 |

### 2. 生效配置计算
把「用户设置 + 总开关/暂停 + 临时放行 + 敏感应用 + 观察期」合成为每个 App 的**最终生效配置**，所有引擎都只读这个结果，保证判断口径一致。

### 3. App 登记
- 首次运行时登记所有已装 App（不进入观察期）。
- 之后新装的 App 自动登记，进入 3 天观察期；银行/支付类自动标为敏感、默认不拦。
- 卸载时清理该 App 的所有记录。

### 4. 规则存储
- 编译好的规则文件（域名二进制、UI 规则、通知规则）保存在私有目录。
- 写入时先校验，再原子替换，同时保留上一版；读取时发现损坏自动回滚。
- 每次安装新规则时规则版本号 +1，引擎监听版本号热加载。

### 5. 规则订阅更新
- 每天自动更新（有网时），支持 ETag 避免重复下载。
- 单个订阅失败不影响其他订阅，继续使用上次缓存。
- 合并：内置标准级域名 + HttpDNS 清单 + 各订阅 + 用户规则 + 内置 UI 规则 → 编译 → 安装。
- 首次安装预置默认订阅（anti-AD、AWAvenue、GKD 官方订阅）。

## 依赖
`core-rules`。

## 实施约定

- 固定时长集中在 `contract/DataContract`；奖励窗口和信号发出方分别由 `RewardWindowContract`、`SignalContract` 定义。
- `RuleStore` 读取损坏文件时直接加载上一版；不会由 VPN 读取进程改写文件。安装失败会恢复当前和上一版，并保持版本号。
- `BuiltRules.stats`：`builtin:domains`、`builtin:httpdns`、`builtin:ui`、`user` 为来源条数；`subscription:<id>` 与 `subscription:<id>:skipped` 为各订阅解析/跳过数；`domains`、`ui`、`notify` 为去重后的条数，`duplicates`、`skipped` 为重复/跳过总数。
- 默认订阅地址于 2026-10-02 核对：[anti-AD README](https://github.com/privacy-protection-tools/anti-AD#快速使用使用官网地址速度更稳定)、[AWAvenue README](https://github.com/TG-Twilight/AWAvenue-Ads-Rule#订阅规则)、[GKD README](https://github.com/gkd-kit/subscription#readme)。GKD README 标明暂时停止维护；按计划保留其官方地址。
