# 跨模块契约测试

2026-10-02：按 docs/TEST_MODULE_REQUESTS.md B 部分修复，已迁入 `testing/rule-regression/src/test/kotlin/com/sentinel/regression/contract/`。本目录保留完全一致的暂存副本；运行结果与遗留项见 `testing/reports/G7-2026-10-02.md`。

| 编号 | 测试函数 | 断言 |
|---|---|---|
| MT-CT-01 | `MT_CT_01_reward_window_is_scoped_expiring_and_fail_closed` | SILENT/ASK 开窗，BLOCK 不开窗；App/标签隔离、重复开窗、TTL 过期与读表失败关闭窗口 |
| MT-CT-02 | `MT_CT_02_each_declared_emitter_produces_a_handled_kind_and_off_emits_none` | 六类信号由各真实入口产生，GuardPolicy 精确决策；OFF 的信号经 SignalRepository.emit 后不新增行，无历史 OFF App 保持零行 |
| MT-CT-03 | `MT_CT_03_real_readers_hot_swap_only_after_successful_rebuild` | 成功重建版本 +1，三个生产读取方使用新版行为且 TUN 不重建；缓存损坏使重建失败，版本与已有行为保持不变 |
| MT-CT-04 | `MT_CT_04_normal_stops_preserve_intent_and_degradation_has_message` | VPN/A11Y/NOTIFY 真实生命周期上报 RUNNING→STOPPED；公开 checker 接口接入真实 watchdog 后提醒；SYSTEM DEGRADED 带文案 |

共享夹具位于 `src/test/kotlin/com/sentinel/regression/contract/fakes/`，使用真实生产实现、Room 内存数据库、可控 Clock，以及虚拟 TUN、音量和 Shizuku 边界端口。没有连接设备或访问真实上游网络。

接线方式：

- 已吸收 `G7-contract-wiring-attempt.diff` 中三个 contract 文件的四项适配；补丁内已有 RR-* 文件不重复搬迁。
- `TunFactory`/`TunHandle` 从 `com.sentinel.vpn.service` 导入；按真实服务的依赖手工构造 VpnController，经公开 `decisionSourceDecorator` 捕获生产 DecisionSource。Koin factory 每次取得当前控制器的快照，停止后新建控制器。
- A11yState 直接绑定 `A11yRuntime.state`。驱动 Brain 前调用公开 `prefetch()`；配置改变时推进 Clock 越过公开缓存 TTL 后再预取，不反射私有缓存。
- 通过真实 `A11yEntry.start` / `NotifyEntry.start` 启动 ruleVersion 订阅。夹具没有自造热更新订阅器，也不手动 reload 来完成热更新断言。
- NotifyState 的构造与规则字段私有，因此使用公开 `NotifyRuntime.engineFor(pkg)`，通过规则命中 ID 和不命中行为验证替换及失败后保留旧规则。
- 撤销 Receiver 经 registerReceiver/sendBroadcast 驱动，等待 Robolectric PendingResult 的完成 Future，不使用 Thread.sleep。生命周期与异步重载有 5 秒上限的条件等待，超时即失败；业务时间只推进 Clock。Dropbox Worker 使用平台时间，调用前将 Robolectric 的平台时钟设为同一 Clock 值。
- MT-CT-04 按本轮授权在测试中实现公开 EnabledServicesChecker。它使用真实停止状态提供意愿输入，真实 ServiceWatchdog 执行提醒与写库；不宣称覆盖私有 SettingsChecker 的 SharedPreferences 历史持久化。SYSTEM 没有正常 stop 入口，本用例验证其未激活时的 DEGRADED。

依赖配置沿用 rule-regression 已有版本目录库，没有新增依赖或版本。执行：

```sh
./gradlew :testing:rule-regression:testDebugUnitTest --tests 'com.sentinel.regression.contract.*'
./gradlew test
```

生产代码、本轮范围之外的测试与契约文档均未修改。原 CT-A2/A3/A6 接线假设已由上述公开入口替代；CT-A7 按 CONTRACTS C2 的最终落库结果执行。
