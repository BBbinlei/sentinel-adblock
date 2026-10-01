# 板块① 单元测试 Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 定义 8 个模块的全部单元级（UT）与模块级（MT）测试：测什么、断言什么、放在哪、怎么跑。

**Architecture:** 测试代码放在各模块 `src/test/`。单元级测试在对应模块任务开发时按 TDD 编写；模块级测试在该模块最后一个任务完成后编写，作为质量关卡的一部分。

**Tech Stack:** JUnit5 + kotlin.test（core-rules）；JUnit4 + Robolectric + kotlin.test（Android 模块）；Turbine；MockWebServer；Koin test。

**Spec:** `docs/superpowers/specs/2026-10-01-adblock-sentinel-design.md`（第 8 节）；总则见 `testing/README.md`。

## 测试边界

- **单元级（UT）**：一个类或一个函数；依赖一律用假实现；不启动 Koin、不访问真实文件系统（RuleStore 等文件类用临时目录）、不访问网络（用 MockWebServer）。
- **模块级（MT）**：一个模块的公开接口整体运行；本模块内部全部用真实实现；相邻模块用内存数据库、假 Shell、假 TunFactory、假 Notifier 代替；**不**使用真实系统服务。
- **不在本板块**：规则质量（→ ②）、真实 VPN/无障碍/Shizuku/通知/多进程（→ ③）、长期稳定性与拦截率（→ ④）。

## 运行命令约定

- 纯 Kotlin 模块：`./gradlew :core-rules:test --tests "<过滤>"`
- Android 模块：`./gradlew :<模块>:testDebugUnitTest --tests "<过滤>"`
- 关卡全量：`./gradlew :<模块>:test`（或 `testDebugUnitTest`），再跑 `./gradlew test`

---

## Task CR: core-rules

测试目录：`core-rules/src/test/kotlin/com/sentinel/rules/`

### UT-CR-1 规则模型与域名解析（`parse/HostsParserTest.kt`、`parse/AdGuardParserTest.kt`；过滤 `com.sentinel.rules.parse.*`）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-CR-1-01 | hosts 解析 `0.0.0.0` 与 `127.0.0.1` 行，跳过注释与 localhost | 输入 `"# c\n0.0.0.0 Ad.Example.com.\n127.0.0.1 localhost\n127.0.0.1 t.x.cn # tail\n"` → 域名 `["ad.example.com","t.x.cn"]`；首条 id = `"dns:ad.example.com"` |
| UT-CR-1-02 | hosts 接受纯域名行 | `"a.cn\n"` → `["a.cn"]` |
| UT-CR-1-03 | AdGuard 只解析 `\|\|域名^` | 输入 `"\|\|ads.x.com^\n\|\|y.com^$third-party\n@@\|\|ok.com^\n##.banner\n! c"` → `["ads.x.com"]`，`skipped == 3` |
| UT-CR-1-04 | 规范化拒绝 IP 与通配符 | `normalize("1.2.3.4")`、`normalize("*.x.com")` 为 null |

### UT-CR-2 域名编译与匹配（`domain/DomainMatcherTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-CR-2-01 | 后缀匹配返回最具体规则 | 规则 `x.com(AD,STRONG)`、`ads.x.com(AD_SDK,STANDARD)`：`a.ads.x.com`→`ads.x.com`；`cdn.x.com`→`x.com`；`x.cn`、`notx.com`→null |
| UT-CR-2-02 | 永不拦截名单优先 | 规则含 `alipay.com`，`lookup("m.alipay.com") == null` |
| UT-CR-2-03 | 截断文件校验失败 | `verify(bytes.copyOf(size-1)) == false` |
| UT-CR-2-04 | 重复域名保留 STANDARD | 同一域名 STRONG 与 STANDARD 各一条 → 命中 level 为 STANDARD |

### UT-CR-3 内置目录（`catalog/CatalogTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-CR-3-01 | 应用名判定银行 | `isSensitive("com.new.bank","某某银行") == true` |
| UT-CR-3-02 | HttpDNS 网段 | 某个 cidr 包含 `203.107.1.33` |
| UT-CR-3-03 | 跳转落地目录 | `isAdLanding("com.xunmeng.pinduoduo")` 为 true；`"com.android.settings"` 为 false |

### UT-CR-4 选择器（`ui/SelectorTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-CR-4-01 | 属性运算符 | `[text~="^跳过\s*\d+"]` 命中文字「跳过 3」的节点；`=`、`^=`、`$=`、`*=` 各一例 |
| UT-CR-4-02 | 子节点与后代 | `FrameLayout > TextView[text="关闭"]` 只命中直接子节点；`FrameLayout TextView[text="关闭"]` 命中任意深度 |
| UT-CR-4-03 | 类名简称 | `TextView` 命中 `android.widget.TextView` |
| UT-CR-4-04 | 语法错误 | `Selector.parse("[text=")` 抛 `SelectorSyntaxException` |

### UT-CR-5 UI 规则解析与索引（`ui/UiRuleIndexTest.kt`、`parse/GkdParserTest.kt`、`parse/JsonRuleParserTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-CR-5-01 | 查找顺序 | Page 规则在前，其次 App，最后 Global |
| UT-CR-5-02 | 非法选择器 | 被跳过且 `invalidCount` 计数 |
| UT-CR-5-03 | GKD 单段 | 单段纯属性选择器被接受（去掉 `@`），多段选择器计入 `skipped`；有 `activityIds` 生成 Page 作用域 |
| UT-CR-5-04 | JSON5 | 注释、未加引号的键、单引号、尾随逗号都能转换并解析 |
| UT-CR-5-05 | JSON 往返 | `parse(encode(rules)) == rules` |

### UT-CR-6 通知匹配（`notify/NotifyMatcherTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-CR-6-01 | 包名 + 渠道命中 | true |
| UT-CR-6-02 | 关键词出现在正文 | true |
| UT-CR-6-03 | 三项全空的规则 | `isValid == false` |
| UT-CR-6-04 | 其他 App | false |

### MT-CR 模块级（`ModuleTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| MT-CR-01 | 全流程 | hosts + AdGuard + GKD 样本文本 → 解析 → `DomainCompiler` → `DomainMatcher`；UI 规则 → `UiRuleIndex`：样本中每个域名都能命中、每条 UI 规则可在对应快照上找到节点 |
| MT-CR-02 | 性能与体积 | 20 万条随机域名编译后文件 < 8 MB；10 万次查询平均 < 5 µs |
| MT-CR-03 | 纯 Kotlin 约束 | 扫描 `core-rules/src/main` 全部源文件，不出现 `import android.` |

- [ ] **关卡步骤：** 运行 `./gradlew :core-rules:test`，期望全部通过；结果写入 G1 报告。

---

## Task DA: data

测试目录：`data/src/test/kotlin/com/sentinel/data/`（Robolectric，内存 Room，可控 `Clock`）

### UT-DA-1 数据库与生效配置（`policy/EffectivePolicyTest.kt`、`db/DatabaseTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-DA-1-01 | 总开关关闭或暂停中 | level = OFF |
| UT-DA-1-02 | 临时放行中 | level = OFF |
| UT-DA-1-03 | 用户设置了级别 | level = 用户设置 |
| UT-DA-1-04 | 敏感应用、用户未设置 | level = OFF |
| UT-DA-1-05 | 其他 / cfg 为 null | level = STANDARD，开关取默认 |
| UT-DA-1-06 | observing 四种组合 | level 为 null 且 `observationEndsAt > 0` → true；level 已设置 → false；`observationEndsAt == 0` → false；cfg null → false |
| UT-DA-1-07 | 实体往返 | 每个实体写入后读回相等 |
| UT-DA-1-08 | 枚举转换器 | 所有枚举值往返一致 |

### UT-DA-2 通用仓库（`repo/RepositoriesTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-DA-2-01 | `pauseFor()` | `pausedUntil == now + 300_000` |
| UT-DA-2-02 | 今日计数 | 只统计本地零点之后的事件 |
| UT-DA-2-03 | `prune()` | 删除 30 天前的事件 |
| UT-DA-2-04 | 钉住的规则 | `disable` 返回 false |
| UT-DA-2-05 | `observeDisabled` | 按 pkg 分组 |
| UT-DA-2-06 | `rulesHitSince` | 去重 |
| UT-DA-2-07 | 奖励窗口 | `until == now + 60_000` |
| UT-DA-2-08 | 用户规则 | 经 `JsonRuleParser` 往返一致 |
| UT-DA-2-09 | `observeRecent(since)` | 只返回 since 之后的停用记录 |

### UT-DA-3 App 配置与登记（`apps/AppRegistryTest.kt`、`repo/AppConfigRepositoryTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-DA-3-01 | 新装银行 App 立即排除 | `onPackageAdded(InstalledApp("com.x.bank","某某银行"))` 后 `observeExcluded()` 含该包 |
| UT-DA-3-02 | 新装普通 App | `observationEndsAt == now + 259_200_000`，生效 observing = true |
| UT-DA-3-03 | 首次同步的 App | `observationEndsAt == 0` |
| UT-DA-3-04 | 临时放行 | 24 小时内被排除，且产生 `TEMP_ALLOW` 信号 |
| UT-DA-3-05 | 卸载 | 清理 `app_config`、`rule_overrides`、`jump_exceptions`、`reward_windows` |
| UT-DA-3-06 | 暂停到期 | 推进 60s 后 `observeExcluded` 重新计算 |
| UT-DA-3-07 | `observationDue` | 只返回 level 为 null 且 `0 < observationEndsAt <= now` 的 App |

### UT-DA-4 规则存储（`rules/RuleStoreTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-DA-4-01 | 安装后加载 | matcher 含已安装规则 |
| UT-DA-4-02 | 当前文件损坏 | 自动回滚到 prev |
| UT-DA-4-03 | 校验失败 | 不替换、抛 `RuleInstallException`，旧版本可用 |
| UT-DA-4-04 | 无文件 | matcher 为 null，UI 索引只含内置规则 |
| UT-DA-4-05 | 安装 | `ruleVersion` + 1 |

### UT-DA-5 规则构建与订阅（`rules/RuleBuilderTest.kt`、`rules/SubscriptionUpdaterTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-DA-5-01 | 无订阅 | 结果含标准级域名与 HttpDNS |
| UT-DA-5-02 | 去重与跳过计数 | stats 正确 |
| UT-DA-5-03 | 下载失败 | 使用缓存并写 `lastError` |
| UT-DA-5-04 | 304 | 保留缓存 |
| UT-DA-5-05 | 构建异常 | 不安装，`ruleVersion` 不变 |

### UT-DA-6 Koin（`di/DataModuleTest.kt`）
| UT-DA-6-01 | `dataModule` 通过 Koin `verify()` | 无缺失依赖 |
|---|---|---|

### MT-DA 模块级（`DataModuleIntegrationTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| MT-DA-01 | 首次启动 | 建库 → 写入默认订阅 → `rebuildFromCache()` → `loadDomainMatcher()` 非空且命中 `gdt.qq.com` |
| MT-DA-02 | App 生命周期 | 装银行 App → 被排除；装普通 App → 观察中；临时放行 → 信号 + 排除；推进 24h → 不再排除；卸载 → 记录清空 |
| MT-DA-03 | 规则更新端到端 | MockWebServer 两个订阅一成一败 → 安装成功、版本 +1、matcher 含成功订阅的规则与失败订阅的缓存规则 |

- [ ] **关卡步骤：** 运行 `./gradlew :data:testDebugUnitTest`，期望全部通过。

---

## Task GD: guard

测试目录：`guard/src/test/kotlin/com/sentinel/guard/`

### UT-GD-1 决策策略（`policy/GuardPolicyTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-GD-1-01 | RETRY_STORM 带 ruleId | `DisableRules(pkg,[ruleId])` |
| UT-GD-1-02 | USER_UNDO 带 ruleId | `DisableRules(pkg,[ruleId])` |
| UT-GD-1-03 | USER_UNDO 无 ruleId，24h ≥2 次，有命中 | `DisableRules(recentHits)` |
| UT-GD-1-04 | 同上但无命中 | `NoRuleFound` |
| UT-GD-1-05 | USER_UNDO 无 ruleId，<2 次 | null |
| UT-GD-1-06 | TEMP_ALLOW 有命中 | `DisableRules` |
| UT-GD-1-07 | 崩溃类无命中 | `NoRuleFound` |
| UT-GD-1-08 | `hitWindowMs` | 崩溃类 120_000，其余 600_000 |
| UT-GD-1-09 | recentHits 去重 | 结果无重复 |
| UT-GD-1-10 | 观察期评估 | 有撤销/放行 → Extend；无 → End |

### UT-GD-2 运行时（`runtime/GuardRunnerTest.kt`、`runtime/ObservationWorkerTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-GD-2-01 | 重试风暴 | 规则被停用、信号标记已处理 |
| UT-GD-2-02 | 规则已钉住 | 不停用，改发 NoRule 通知 |
| UT-GD-2-03 | 单条信号异常 | 后续信号照常处理 |
| UT-GD-2-04 | 撤销 | 删除停用并钉住 |
| UT-GD-2-05 | 观察期内有临时放行 | 延长 3 天 |
| UT-GD-2-06 | 观察期干净 | 结束（`observationEndsAt = 0`） |

### MT-GD 模块级（`GuardModuleTest.kt`，内存 data + 假通知）
| 编号 | 用例 | 断言 |
|---|---|---|
| MT-GD-01 | 降级闭环 | 事件命中规则 R → 发 TEMP_ALLOW → R 被停用并通知 → 撤销 → R 钉住 → 再发 RETRY_STORM(R) → 不停用 |
| MT-GD-02 | 观察期闭环 | 新 App 观察 → 推进 3 天 → Worker 结束观察 → `EffectivePolicy` 不再 observing |

- [ ] **关卡步骤：** 运行 `./gradlew :guard:testDebugUnitTest`，期望全部通过。

---

## Task VP: engine-vpn

测试目录：`engine-vpn/src/test/kotlin/com/sentinel/vpn/`

### UT-VP-1 报文（`packet/IpPacketTest.kt`、`packet/PacketBuilderTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-VP-1-01 | IPv4/UDP 解析 | 各字段正确 |
| UT-VP-1-02 | IPv6/UDP 解析 | 各字段正确 |
| UT-VP-1-03 | IPv4/TCP SYN | `isTcpSyn` 为 true |
| UT-VP-1-04 | 截断报文 | `parse` 返回 null |
| UT-VP-1-05 | `udpReply` | 地址端口互换，IP/UDP 校验和正确 |
| UT-VP-1-06 | `tcpRst` | 标志 RST\|ACK，ack 号正确 |
| UT-VP-1-07 | ICMP 不可达 | v4 载荷含原 IP 头 + 8 字节；v6 type1 code4 |

### UT-VP-2 DNS 与判定（`dns/DnsMessageTest.kt`、`decide/DnsDeciderTest.kt`、`decide/RetryStormDetectorTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-VP-2-01 | matcher 为 null / 未命中 | FORWARD |
| UT-VP-2-02 | App 级别 OFF | FORWARD |
| UT-VP-2-03 | STRONG 规则遇 STANDARD App（含 cfg null） | FORWARD |
| UT-VP-2-04 | 规则被 guard 停用 | FORWARD |
| UT-VP-2-05 | AD_SDK 且奖励窗口开启 | FORWARD |
| UT-VP-2-06 | 观察期 | WOULD_BLOCK |
| UT-VP-2-07 | 其他命中 | BLOCK |
| UT-VP-2-08 | 问题解析 | 支持压缩指针，域名小写 |
| UT-VP-2-09 | 拦截应答 | A→0.0.0.0、AAAA→::、HTTPS(65)→NOERROR 空应答，TTL 60 |
| UT-VP-2-10 | 重试风暴阈值 | 第 10 次 true、第 11 次 false |
| UT-VP-2-11 | 重新触发 | 10 分钟后满足条件可再次 true |
| UT-VP-2-12 | 60 秒窗口 | 窗口外的记录不计 |

### UT-VP-3 上游与缓存（`dns/UpstreamResolverTest.kt`、`dns/DnsCacheTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-VP-3-01 | DoH 成功 | 返回应答并写缓存 |
| UT-VP-3-02 | DoH 返回 500 | 改走 UDP |
| UT-VP-3-03 | 都失败 | SERVFAIL，id 与请求一致 |
| UT-VP-3-04 | 缓存命中 | 应答 id 改写为新请求 id |
| UT-VP-3-05 | TTL | 夹在 [30, 3600] |

### UT-VP-4 TUN 与报文循环（`tun/TunSpecTest.kt`、`tun/PacketLoopTest.kt`，管道流模拟 TUN）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-VP-4-01 | TunSpec | 含两个虚拟 DNS 路由与全部 HttpDNS CIDR，`disallowed` 含自身包名 |
| UT-VP-4-02 | 广告域名查询 | 输出 0.0.0.0 应答 |
| UT-VP-4-03 | 正常域名 | 输出上游应答 |
| UT-VP-4-04 | 发往 `203.107.1.33:443` 的 SYN | 输出 RST |
| UT-VP-4-05 | 发往 HttpDNS IP 的 UDP | 输出 ICMP 不可达 |
| UT-VP-4-06 | 畸形报文 | 被忽略，循环继续 |
| UT-VP-4-07 | input 关闭 | `run()` 正常返回 |

### UT-VP-5 控制器（`service/VpnControllerTest.kt`、`service/EventBatcherTest.kt`，假 TunFactory）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-VP-5-01 | 排除名单变化 | 先 establish 新 TUN，再 close 旧 TUN |
| UT-VP-5-02 | 新装敏感 App | 触发重建，新 TunSpec 的 disallowed 含该包 |
| UT-VP-5-03 | 规则版本变化 | 重新加载 matcher，不重建 TUN |
| UT-VP-5-04 | 拦截事件 | 记 `DNS_BLOCKED`；第 10 次触发 `RETRY_STORM` 信号 |
| UT-VP-5-05 | 批量写库 | 每 2 秒一批；HTTPDNS 同 (pkg, IP) 每分钟 1 条 |
| UT-VP-5-06 | 事件 detail | 为实际查询的域名 |

### UT-VP-6 健康检查（`health/PrivateDnsDetectorTest.kt`、`health/ServiceWatchdogTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-VP-6-01 | 私人 DNS 三种组合 | 仅 `active && serverName != null` 返回警告文案 |
| UT-VP-6-02 | 无障碍被关 | 上报 A11Y `STOPPED` 并调用 notifier |
| UT-VP-6-03 | 都正常 | 不调用 notifier |

### UT-VP-7 故障安全（`service/VpnFailSafeTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-VP-7-01 | PacketLoop 抛异常 | TUN 已关闭，状态 STOPPED，文案「网络拦截意外停止，网络已恢复直连」 |
| UT-VP-7-02 | `onRevoked` | TUN 已关闭，文案「VPN 授权被撤销或被其他 VPN 取代」 |
| UT-VP-7-03 | establish 返回 null | NOT_SETUP |
| UT-VP-7-04 | matcher 为 null | 继续运行、全部 FORWARD、DEGRADED「规则不可用，暂停网络拦截」 |
| UT-VP-7-05 | 以上退出路径 | 服务停止回调被调用 |

### MT-VP 模块级（`VpnModuleTest.kt`：真实 VpnController + PacketLoop + 假 TunFactory + 内存 data + 假上游）
| 编号 | 用例 | 断言 |
|---|---|---|
| MT-VP-01 | 场景脚本 | STANDARD App 查广告域名 → 0.0.0.0 且有事件；观察期 App → 正常应答 + `WOULD_BLOCK`；开奖励窗口 → AD_SDK 域名放行；暂停 → 全部放行；恢复 → 恢复拦截 |
| MT-VP-02 | 故障注入 | 随机让上游、判定源、写库抛异常 1000 次：单包错误不终止循环；控制器停止后 TUN 必定已关闭 |

- [ ] **关卡步骤：** 运行 `./gradlew :engine-vpn:testDebugUnitTest`，期望全部通过。

---

## Task AY: engine-a11y

测试目录：`engine-a11y/src/test/kotlin/com/sentinel/a11y/`；小型快照放 `engine-a11y/src/test/resources/snapshots/`（开屏 3、弹窗 3、陷阱 2、自动续费 1、正常页面 3）

### UT-AY-1 前台跟踪（`core/ForegroundTrackerTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-AY-1-01 | 桌面 → A | `fromLauncher = true` |
| UT-AY-1-02 | systemui → A | `fromLauncher = false` |
| UT-AY-1-03 | A → 输入法 → A | 不产生 Transition |
| UT-AY-1-04 | A 内点击 | `lastInteractionAt` 更新 |
| UT-AY-1-05 | `launchesWithin` | 计数正确 |

### UT-AY-2 规则点击（`core/RuleClickerTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-AY-2-01 | 开屏快照，启动窗口内 | 返回 Click（SPLASH_SKIPPED） |
| UT-AY-2-02 | 启动窗口外 | LAUNCH 规则不命中 |
| UT-AY-2-03 | `splash = false` | 不命中 |
| UT-AY-2-04 | 陷阱快照 | 不会点到「立即下载」 |
| UT-AY-2-05 | guard 停用的规则 | 被跳过 |
| UT-AY-2-06 | 正常页面 | 全部 null |
| UT-AY-2-07 | 节流 | 同一规则第 4 次被拒 |
| UT-AY-2-08 | 自动续费快照 | 返回 WarnAutoRenew |

### UT-AY-3 学习模式（`core/LearningRecorderTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-AY-3-01 | 有 viewId | 生成 `[vid="..."]` 选择器 |
| UT-AY-3-02 | 无 viewId | 生成「类名简称 + 文字」选择器 |
| UT-AY-3-03 | 超过 3 秒 | 不生成 |
| UT-AY-3-04 | 文字不像关闭 | 不生成 |
| UT-AY-3-05 | 本窗口已自动点击 | 不生成 |
| UT-AY-3-06 | 生成的选择器 | 能被 `Selector.parse` 解析 |

### UT-AY-4 误伤信号（`core/HealthSignalsTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-AY-4-01 | 中文崩溃弹窗 | 识别出包名，返回 CRASH_DIALOG |
| UT-AY-4-02 | 未知应用名 | null |
| UT-AY-4-03 | 冷启动循环 | 第 3 次触发；10 分钟内不重复 |

### UT-AY-5 激励视频与音量（`core/RewardedHandlerTest.kt`、`core/VolumeKeeperTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-AY-5-01 | 静默流程 | 静音 → 倒计时消失后 ClickClose → 恢复音量 |
| UT-AY-5-02 | 中途离开 App | 恢复音量 |
| UT-AY-5-03 | 超过 90 秒 | 恢复音量 |
| UT-AY-5-04 | 询问模式 | 返回 AskUser，做出选择前不静音 |
| UT-AY-5-05 | 拦截模式 | 不开奖励窗口 |
| UT-AY-5-06 | 模拟进程死亡后 `restoreIfPending` | 恢复存档音量 |
| UT-AY-5-07 | 连续两次 restore | 不会设成过期的值 |

### UT-AY-6 跳转回退（`core/JumpBackGuardTest.kt`）
应回退：
| 编号 | 用例 |
|---|---|
| UT-AY-6-01 | 从桌面启动 2 秒内无交互跳拼多多（launch） |
| UT-AY-6-02 | 使用中出现「摇一摇」后无交互跳淘宝（shake） |

不应回退（每条断言返回 null）：
| 编号 | 场景 | 编号 | 场景 |
|---|---|---|---|
| UT-AY-6-03 | 启动后 2 秒内点击再跳淘宝 | UT-AY-6-13 | 语音助手打开 App |
| UT-AY-6-04 | 从通知栏打开淘宝 | UT-AY-6-14 | 分屏启动另一 App（来源 systemui） |
| UT-AY-6-05 | 微信中点拼多多链接（有交互） | UT-AY-6-15 | 桌面快捷方式打开淘宝 |
| UT-AY-6-06 | 分享面板选择 App | UT-AY-6-16 | 浏览器中点链接跳淘宝（有交互） |
| UT-AY-6-07 | 扫码后跳转（有交互） | UT-AY-6-17 | 启动时弹出权限对话框 |
| UT-AY-6-08 | A → 桌面 → 淘宝 | UT-AY-6-18 | 启动后跳自家其他页面（同包） |
| UT-AY-6-09 | A → 最近任务 → 淘宝 | UT-AY-6-19 | 弹出输入法 |
| UT-AY-6-10 | 登录时跳支付宝授权 | UT-AY-6-20 | 来电界面 |
| UT-AY-6-11 | 桌面小组件打开淘宝 | UT-AY-6-21 | 闹钟响铃界面 |
| UT-AY-6-12 | 负一屏卡片打开 App | UT-AY-6-22 | 启动 5 秒内滑动后跳转 |
| UT-AY-6-23 | 已加例外的 (A,B) | UT-AY-6-24 | guard 停用 `builtin:jumpback` 后 |

### UT-AY-8 适配器（`service/NodeViewAdapterTest.kt`，Robolectric）
| UT-AY-8-01 | `AccessibilityNodeInfo` 树映射 | 类名、文字、描述、vid 截取、bounds、children 正确 |
|---|---|---|

### MT-AY 模块级（`A11yBrainTest.kt`：真实 `A11yBrain` + 假仓库 + 快照）
| 编号 | 用例 | 断言 |
|---|---|---|
| MT-AY-01 | 冷启动开屏 | 桌面 → App → 开屏快照 → 动作 [Click] + 事件 SPLASH_SKIPPED |
| MT-AY-02 | 激励视频全流程 | 点击「看视频领奖励」→ OpenRewardWindow；激励页 → 静音；倒计时消失 → ClickClose；离开 → 音量恢复 |
| MT-AY-03 | 跳转回退 + 撤销 | 产生回退动作；执行撤销 → 例外写入 + USER_UNDO 信号；同样的跳转再次发生 → 不回退 |
| MT-AY-04 | 学习流程 | 弹窗出现 1 秒后用户点「关闭」→ 产生候选规则 |
| MT-AY-05 | 异常隔离 | 某个子组件抛异常 → `A11yBrain` 返回空动作列表，不抛出 |

- [ ] **关卡步骤：** 运行 `./gradlew :engine-a11y:testDebugUnitTest`，期望全部通过。

---

## Task NT: engine-notify

测试目录：`engine-notify/src/test/kotlin/com/sentinel/notify/`

### UT-NT-1 判定（`core/NotifyFilterTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-NT-1-01 | 商店「限时福利」 | 命中内置规则 |
| UT-NT-1-02 | 商店「更新完成」 | 不命中 |
| UT-NT-1-03 | 常驻通知 | 不处理 |
| UT-NT-1-04 | App 为 OFF 或 `notify = false` | 不处理 |
| UT-NT-1-05 | 规则被停用 | 跳过 |
| UT-NT-1-06 | 自身通知 | 不处理 |

### UT-NT-2 划除学习（`core/DismissLearnerTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-NT-2-01 | 第 3 次划除 | 返回候选规则 |
| UT-NT-2-02 | 7 天前的划除 | 不计入 |
| UT-NT-2-03 | 拒绝后 | 30 天内不再返回；30 天后可再返回 |
| UT-NT-2-04 | 重建学习器（同一存储） | 计数保留 |
| UT-NT-2-05 | channelId 为 null | 不生成 |

### UT-NT-4 映射（`service/ListenerMappingTest.kt`，Robolectric）
| UT-NT-4-01 | `toPosted()` | 正确取出 `EXTRA_TITLE`、`EXTRA_TEXT`、`FLAG_ONGOING_EVENT`、channelId |
|---|---|---|

### MT-NT 模块级（`NotifyEngineTest.kt`：真实 `NotifyEngine` + 假仓库）
| 编号 | 用例 | 断言 |
|---|---|---|
| MT-NT-01 | 营销通知 | 返回 Cancel 动作，事件 detail 为标题 |
| MT-NT-02 | 学习闭环 | 同渠道划除 3 次 → 询问 → 接受 → 用户规则写入并触发重建 → 同渠道新通知被清除 |

- [ ] **关卡步骤：** 运行 `./gradlew :engine-notify:testDebugUnitTest`，期望全部通过。

---

## Task SY: engine-system

测试目录：`engine-system/src/test/kotlin/com/sentinel/system/`；`FakeDevice`（测试工具，`src/test/.../FakeDevice.kt`）是一个有状态的假 Shell：内存保存 settings、已启用/已卸载包、appops 模式，并按真实命令格式回应 `settings get/put`、`pm disable-user/enable/uninstall/install-existing/list packages`、`appops get/set`。

### UT-SY-1 配置档案（`profile/ProfileLoaderTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-SY-1-01 | 解析示例档案 | 字段正确 |
| UT-SY-1-02 | 最长前缀 | 被选中 |
| UT-SY-1-03 | 无匹配 | null |
| UT-SY-1-04 | 占位符 | `{before}` 原样保留 |

### UT-SY-2 Shizuku 网关（`shell/ShizukuGatewayTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-SY-2-01 | 四种状态 | 判定正确 |
| UT-SY-2-02 | 非 READY 时 exec | `exitCode = -2`，`stderr = "shizuku not ready"`，不调用 binder |
| UT-SY-2-03 | JSON 结果 | 解析正确 |
| UT-SY-2-04 | 超时 | `exitCode = -1` |

### UT-SY-3 执行与撤销（`ops/OpExecutorTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-SY-3-01 | 未验证项 | 不执行任何命令，返回 NotVerified |
| UT-SY-3-02 | 已应用 | 不重复执行，返回 AlreadyApplied |
| UT-SY-3-03 | 成功 | 写日志，beforeState 正确 |
| UT-SY-3-04 | 执行后探测不匹配 | Failed 并写失败日志 |
| UT-SY-3-05 | 撤销 | 用 before 值替换 `{before}` 恢复 |
| UT-SY-3-06 | `applyAll` | 一项失败其余照常 |
| UT-SY-3-07 | `undoAll` | 只撤销成功且未撤销的项 |

### UT-SY-4 按 App 权限（`ops/AppOpsSyncTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-SY-4-01 | 开启限制悬浮窗 | 执行 `SYSTEM_ALERT_WINDOW deny` 并记日志 |
| UT-SY-4-02 | 关闭 | 恢复原模式 |
| UT-SY-4-03 | Shizuku 未就绪 | `pendingCount` 增加；就绪后 `syncOnce` 清零 |
| UT-SY-4-04 | `backgroundPopupOp` 为空 | 只处理悬浮窗 |

### UT-SY-5 巡检（`drift/DriftInspectorTest.kt`、`drift/ShizukuOfflineTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-SY-5-01 | 探测不匹配 | 计入 drifted |
| UT-SY-5-02 | 已撤销项 | 不检查 |
| UT-SY-5-03 | Shizuku NOT_RUNNING | `inspect()` 返回 null、不执行命令、状态 DEGRADED「Shizuku 未激活（不影响已生效项）」 |
| UT-SY-5-04 | `reapply` | 只执行指定项 |

### UT-SY-6 崩溃解析（`crash/DropboxParserTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-SY-6-01 | 样例输出 | 每条解析出时间与 `Process:` 包名 |
| UT-SY-6-02 | 无崩溃 | 空列表 |

### MT-SY 模块级（`SystemModuleTest.kt`：真实 OpExecutor + AppOpsSync + DriftInspector + FakeDevice + 内存 data）
| 编号 | 用例 | 断言 |
|---|---|---|
| MT-SY-01 | 净化与全部还原 | 一键处理全部已验证项 → `undoAll()` → FakeDevice 状态与初始**完全一致** |
| MT-SY-02 | 系统升级漂移 | 应用后把 FakeDevice 部分设置重置 → `inspect()` 找出这些项 → `reapply` → 恢复 |
| MT-SY-03 | Shizuku 中途断开 | 断开后的操作不执行、进入待处理；已执行项状态保持；重连后待处理项被执行 |

- [ ] **关卡步骤：** 运行 `./gradlew :engine-system:testDebugUnitTest`，期望全部通过。

---

## Task AP: app

测试目录：`app/src/test/kotlin/com/sentinel/app/`（ViewModel 用假仓库；界面用 Robolectric Compose）

### UT-AP-1 骨架（`di/AllModulesTest.kt`）
| UT-AP-1-01 | 全部 Koin 模块 `verify()` | 无缺失依赖 |
|---|---|---|

### UT-AP-2 首页（`home/HomeViewModelTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-AP-2-01 | 状态合成 | 启用 + VPN RUNNING → PROTECTING；暂停中 → PAUSED；未启用 → OFF |
| UT-AP-2-02 | 点击盾牌 | 调用正确的仓库方法 |
| UT-AP-2-03 | 引擎 DEGRADED | message 进入 warnings |

### UT-AP-3 向导（`onboarding/OnboardingViewModelTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-AP-3-01 | `onResume` | 自动勾选已完成步骤，跳到第一个未完成步骤 |
| UT-AP-3-02 | `skip` | 前进但不标记完成 |
| UT-AP-3-03 | 全部完成 | `finished = true` |

### UT-AP-4 首页完整版与系统净化（`home/HomeGuardAlertTest.kt`、`system/SystemCleanupViewModelTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-AP-4-01 | guard 提醒 | 24 小时内的停用记录按 App 合并 |
| UT-AP-4-02 | 点撤销 | 调用 `GuardRunner.undo` |
| UT-AP-4-03 | 警告条 | 含私人 DNS 文案与「Shizuku 未激活（不影响已生效项）」 |
| UT-AP-4-04 | 未验证项 | `canAuto = false` |
| UT-AP-4-05 | `applyAll` | 跳过 optional 与 UNINSTALL 项 |

### UT-AP-5 应用页（`apps/AppsViewModelTest.kt`、`apps/AppDetailViewModelTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-AP-5-01 | 排序与搜索 | 按 7 天拦截数降序；按应用名或包名搜索 |
| UT-AP-5-02 | 敏感应用 | `defaultAllowed = true` |
| UT-AP-5-03 | 切换级别 | 写入仓库 |
| UT-AP-5-04 | 观察期剩余天数 | 向上取整 |
| UT-AP-5-05 | 将要拦截的域名 | 来自近 3 天 `WOULD_BLOCK` 事件的 detail，去重 |

### UT-AP-6 规则页（`rules/RulesViewModelTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-AP-6-01 | 添加订阅 | 只接受 https |
| UT-AP-6-02 | 立即更新 | 期间 `updating = true`；结束显示「更新 x 个，失败 y 个」 |
| UT-AP-6-03 | 删除用户规则 | 触发 `rebuildFromCache` |

### UT-AP-7 快捷开关（`tile/PauseTileServiceTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| UT-AP-7-01 | 点击 | 未暂停 → `pauseFor(300_000)`；已暂停 → `resume()` |
| UT-AP-7-02 | 磁贴状态 | 随暂停状态变化 |

### MT-AP 模块级（Robolectric Compose：`AppFlowsTest.kt`、`LayoutRobustnessTest.kt`）
| 编号 | 用例 | 断言 |
|---|---|---|
| MT-AP-01 | 临时放行两步 | 从应用列表点进详情（1）→ 点「一键临时放行」（2）→ 调用 `tempAllow`，无确认弹窗 |
| MT-AP-02 | 布局健壮性 | 首页、应用详情、系统净化在 360dp 宽、字体 1.3 倍下无截断与省略号；深色模式可渲染 |
| MT-AP-03 | 启动导航 | 向导未完成 → 进向导；已完成 → 进首页 |
| MT-AP-04 | 二次确认 | 卸载项点击后出现确认框；其他项直接执行 |
| MT-AP-05 | 触控尺寸 | 所有可点击元素 ≥ 48dp |

- [ ] **关卡步骤：** 运行 `./gradlew :app:testDebugUnitTest`，期望全部通过。
