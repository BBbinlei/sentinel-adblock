# 跨模块契约暂存测试

状态：只存放测试源文件，尚未迁入任何 Gradle 测试源集。产品实现未完成；本次未运行 Gradle，也未验证编译或执行结果。

## 编号对照

已编写 4 / 4 个计划编号；每个编号都有实际断言。编号覆盖表示代码已写，不表示测试已通过。

| 编号 | 文件（相对此目录） | 测试函数 |
|---|---|---|
| MT-CT-01 | `src/test/kotlin/com/sentinel/regression/contract/ContractTest.kt` | `MT_CT_01_reward_window_is_scoped_expiring_and_fail_closed` |
| MT-CT-02 | `src/test/kotlin/com/sentinel/regression/contract/ContractTest.kt` | `MT_CT_02_each_declared_emitter_produces_a_handled_kind_and_off_emits_none` |
| MT-CT-03 | `src/test/kotlin/com/sentinel/regression/contract/ContractTest.kt` | `MT_CT_03_real_readers_hot_swap_only_after_successful_rebuild` |
| MT-CT-04 | `src/test/kotlin/com/sentinel/regression/contract/ContractTest.kt` | `MT_CT_04_normal_stops_preserve_intent_and_degradation_has_message` |

## 文件与测试边界

主文件 `src/test/kotlin/com/sentinel/regression/contract/ContractTest.kt` 包名为 `com.sentinel.regression.contract`。共享文件位于同包 `fakes/`：`ContractFixture.kt` 负责内存 data 和真实模块接线，`ContractPorts.kt` 提供可控时钟、内存音量/KV、虚拟 TUN、无网络上游和无真实 Shizuku 的 shell/binder。

- MT-CT-01：真实 `A11yBrain` → `OpenRewardWindow` → 真实窗口仓库 → **生产** `DecisionSource`/`DnsDecider`。检查 SILENT/ASK、BLOCK、标签隔离、App 隔离、重复开窗覆盖与过期。最后删除仅存在于本测试内存库中的窗口表，并让 VPN 重新订阅，验证读取失败关闭窗口。
- MT-CT-02：真实 Brain 产生崩溃/冷启动 Signal 动作，测试把动作交给真实 SignalRepository；真实撤销 Receiver 产生 USER_UNDO；虚拟 TUN 把合法 DNS 包送入真实 VpnController/PacketLoop，10 次实际拦截触发 RETRY_STORM；真实 Dropbox Worker 与 parser 产生 DROPBOX_CRASH；真实 `tempAllow` 产生 TEMP_ALLOW。每类信号均进入真实 `GuardPolicy` 并精确断言 Decision/null。OFF 后重复全部路径，计数均不得增加。
- MT-CT-03：真实 `SubscriptionUpdater.rebuildFromCache()` → ruleVersion → 三个引擎的**生产快照**；用新版/旧版 DNS、UI、通知行为确认替换。向内存库的缓存 JSON 注入语法错误，检查构建抛异常、版本不变且旧规则继续生效。测试没有手写版本订阅器或替代引擎重载逻辑。
- MT-CT-04：Robolectric 中调用真实服务生命周期与 Controller.stop，检查 STOPPED；使用生产 EnabledServicesChecker 检查用户意愿并运行真实 watchdog；让真实 SystemStatusReporter 遇到未就绪网关，检查 DEGRADED 有文案。

Robolectric 服务只用于本地生命周期/接线，不连接 Android 系统服务、VPN、通知授权或设备。Dropbox 样本在测试函数内生成，是合成的隐私无关格式样本，不宣称来自实机采集。没有独立资源文件。

## 搬迁与测试配置

```sh
cp -R testing/unit/pending/contract/src/test testing/rule-regression/src/
```

现有 rule-regression 已依赖全部功能模块，已声明 JUnit4、kotlin.test.junit、Robolectric、coroutines-test 和 serialization.json，并开启 Android 资源。因为测试直接使用 Room、Koin Android/DSL、WorkManager test builder，搬迁时使用版本目录已有别名补充：

```kotlin
testImplementation(libs.room.runtime)
testImplementation(libs.koin.android)
testImplementation(libs.koin.test)
testImplementation(libs.work.testing)
```

不加版本、不引入 mockk/mockito。当前没有修改构建配置。实现齐备后运行：

```sh
./gradlew :testing:rule-regression:testDebugUnitTest --tests 'com.sentinel.regression.contract.*'
```

## 接口假设（8 条）

这些是 PLAN 空白的接线假设，需实现方接受或同步修改测试；编写了断言并不表示已经存在可编译的产品实现。

1. **CT-A1：类型包。** data Task 1 的实体/枚举/`EffectiveConfig` 位于 `com.sentinel.data.db`；依据 `Entities.kt` 的 Files 路径推断。其他 import 沿各 PLAN 的 Files 路径定位：core 类型、Brain/Action/State 在 a11y.core；Controller 在 vpn.service；DecisionSource 在 vpn.tun；NotifyState 在 notify.core。
2. **CT-A2：生产快照绑定。** vpn Task 5、a11y Task 8、notify Task 4 描述真实快照订阅，但没有暴露快照实例的获取方法。假设各 Koin module 可解析计划已声明的 `DecisionSource`、`A11yState`、`NotifyState`，且它们与运行中的 Controller/服务使用的是同一生产快照。这里不假设新的方法或类；如果实际快照不通过 Koin 暴露，必须调整测试获取方式，不能在测试中自行订阅并重载规则来冒充契约通过。
3. **CT-A3：VPN 参数和边界端口。** vpn Task 5 的 Controller 构造仅写 `(scope, tunFactory, ...)`。假设通过 `koin.get<VpnController> { parametersOf(scope) }` 创建，其他省略参数由 module 从容器取得；`TunFactory`、`PackageResolver`、`UpstreamResolver` 均可覆盖。它使用注入的 Clock 驱动 RetryStormDetector，阻塞 InputStream 读取在 IO 上运行，不阻塞测试调度器。未假设任何 `onLoopEvent` 等未声明 API。
4. **CT-A4：生命周期接线。** a11y Task 8 与 notify Task 4 的生产服务在标准 `onServiceConnected`/`onListenerConnected` 回调内启动快照订阅并上报状态，构造/依赖可由已启动的 module 解析。测试用 Robolectric create + 回调驱动；只对受保护的 `onServiceConnected` 使用反射，未反射私有状态或自造测试方法。
5. **CT-A5：Dropbox Worker 入口。** system Task 6 仅指定 Worker 文件/行为。假设 `DropboxCrashWorker(Context, WorkerParameters)` 是标准入口，使用容器内 Shell/或 ShizukuGateway、SignalRepository、AppConfigRepository、Clock；其上次检查时间初始可由一次空输出运行建立。若使用构造注入，需在 test builder 添加 WorkerFactory。Worker 的正式实现仍需在 system 自身测试中用真实去隐私样例核实解析格式。
6. **CT-A6：系统状态与 watchdog 的 DI。** system Task 7 明确提供网关等，但未写明 SystemStatusReporter 的绑定；vpn Task 6 也未写明 EnabledServicesChecker 的构造和提供位置。假设二者可从所属 module 取得，reporter 使用覆盖后的网关，checker 按状态表中的运行/已停止记录计算 userWants，并从系统授权状态判断 enabled。Robolectric 未授权状态用于 watchdog 缺失服务路径。
7. **CT-A7：撤销常量位置及 OFF 过滤层。** a11y Task 8 给出 `A11yActions.JUMP_UNDO` 与 Receiver 文件，推断常量在 `com.sentinel.a11y.service`。Receiver 接收 `source`、`target` 并真实写 USER_UNDO。C2 要求 OFF 不发信号，但 Brain/服务哪层过滤没有写死：测试最合理地要求 Brain 不返回 OFF 应用的健康 Signal，Receiver/Worker/Controller/data 各入口也不得写入 OFF 信号；若正式接线把健康过滤放在服务执行动作处，应把这个动作驱动段改为调用真实执行器，保持 OFF 计数断言，不能在测试适配器里加过滤。
8. **CT-A8：缓存与失败入口。** data Task 4/5 规定缓存构建异常不安装、不 bump。测试推断缓存的用户规则 `json` 是 `UserRuleRepository.observeAll()` 的解析输入，直接损坏内存 Room 的该列会使真实 rebuild 抛异常；`RuleStore` 使用可覆盖的目录实例。若正式缓存布局不同，应改故障注入位置，不改版本/旧规则断言。

## 待确认

- **CT 快照获取尚无正式契约。** CT-A2/A3/A6 不能从 PLAN 推导出唯一 DI 写法。当前代码只使用已声明类型与方法，但要求这些实例可由生产 Koin 提供；若实现方选择别的接线方式，需要同步测试辅助文件。不能声称任意照 PLAN 的实现都能零改动运行。
- **CT-02 与 data Task 3 存在行为空白。** `tempAllow` 写为“设置 24h 并 emit TEMP_ALLOW”，C2 则禁止 OFF 应用发信号。当前按“操作前的 effective level”判断：从 STANDARD 发起会有信号（动作后本就变 OFF），已经 OFF 的应用不会产生新信号。不得按动作后的 OFF 一律抑制，否则合法临时放行全无信号。
- **CT-02 健康信号的 OFF 检查层未确定。** 当前依据 CT-A7 实现。正式 Brain 若只产出候选、由服务统一过滤，需把测试驱动接到真实执行器，而不能修改为测试自行过滤。
- **CT-04 测试清单的方法归属写错。** `ServiceWatchdog` 只有 `checkOnce()`；`userWantsA11y()`/`userWantsNotify()` 在 `EnabledServicesChecker` 上。测试用正式 checker 的两个方法加真实 watchdog 检查，没有新增 Watchdog 方法。
- **CT-04 的 system 停用入口未定义。** system 是周期任务/状态 reporter，PLAN 没有 stop API。当前正常停用断言覆盖 VPN/A11Y/NOTIFY，SYSTEM 覆盖未激活时的 DEGRADED + 非空 message；如需 system 的 STOPPED 行为，需先给出停用入口及语义。
- **C1/C6 文案有张力。** C6 说读不到数据更宽松，C1、Task CT、Review Focus 明确窗口读取失败视为关闭、AD_SDK 继续拦截。测试按更具体的 C1 执行，未自行更改故障原则。
- **格式和 Worker 注入未写死。** Dropbox 的 dumpsys 分隔格式、持久化 lastChecked 方式、标准构造或 WorkerFactory 均需开发时对齐 CT-A5；合成样本只验证契约流，不替代 system 的实机格式样例测试。

## 本次检查

仅静态核对 4 个编号、真实断言、已声明 API 引用、辅助文件边界和禁止项；没有 Gradle 编译/运行，也没有连接手机、联网、接受协议或提交。所有快照热更新都要求生产接线完成；本次没有编写替代热更新器或产品同名类。
