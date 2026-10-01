# engine-vpn 暂存测试

已编写 **48/48 个编号（46 UT + 2 MT）**，共 15 个测试文件、4 个测试辅助文件。所有编号在真实 `@Test` 函数名中；没有空测试、`@Ignore`、产品替身同名类或 `src/main` 文件。

这些文件目前不属于任何 Gradle source set。仅做编号核对与 Kotlin PSI 语法解析（19 个 Kotlin 文件、0 个语法错误）；**未运行 Gradle、未编译测试、未验证运行通过**。未使用外网、设备或许可安装流程。签名未定部分不能承诺搬迁后立即编译，须先核对下面 A1～A9，尤其 A4。

## 编号对照

文件路径均相对于 `src/test/kotlin/com/sentinel/vpn/`。

| 编号 | 文件 | 测试函数 |
|---|---|---|
| UT-VP-1-01 | `packet/IpPacketTest.kt` | `UT-VP-1-01 IPv4 UDP fields` |
| UT-VP-1-02 | `packet/IpPacketTest.kt` | `UT-VP-1-02 IPv6 UDP fields` |
| UT-VP-1-03 | `packet/IpPacketTest.kt` | `UT-VP-1-03 TCP SYN` |
| UT-VP-1-04 | `packet/IpPacketTest.kt` | `UT-VP-1-04 truncated packets are rejected` |
| UT-VP-1-05 | `packet/PacketBuilderTest.kt` | `UT-VP-1-05 UDP reply swaps endpoints and checksums` |
| UT-VP-1-06 | `packet/PacketBuilderTest.kt` | `UT-VP-1-06 RST ACK accounts for payload and SYN` |
| UT-VP-1-07 | `packet/PacketBuilderTest.kt` | `UT-VP-1-07 ICMP quotes original header and IPv6 code` |
| UT-VP-2-01 | `decide/DnsDeciderTest.kt` | `UT-VP-2-01 unavailable matcher and misses forward` |
| UT-VP-2-02 | `decide/DnsDeciderTest.kt` | `UT-VP-2-02 OFF wins over observing and reward` |
| UT-VP-2-03 | `decide/DnsDeciderTest.kt` | `UT-VP-2-03 strong requires strong App including null default` |
| UT-VP-2-04 | `decide/DnsDeciderTest.kt` | `UT-VP-2-04 disabled rule wins over observing` |
| UT-VP-2-05 | `decide/DnsDeciderTest.kt` | `UT-VP-2-05 reward releases SDK only` |
| UT-VP-2-06 | `decide/DnsDeciderTest.kt` | `UT-VP-2-06 observing records would block` |
| UT-VP-2-07 | `decide/DnsDeciderTest.kt` | `UT-VP-2-07 matching ordinary rule blocks` |
| UT-VP-2-08 | `dns/DnsMessageTest.kt` | `UT-VP-2-08 question parsing offsets compression and lowercase` |
| UT-VP-2-09 | `dns/DnsMessageTest.kt` | `UT-VP-2-09 blocked answers by type and failure ID` |
| UT-VP-2-10 | `decide/RetryStormDetectorTest.kt` | `UT-VP-2-10 threshold and pair isolation` |
| UT-VP-2-11 | `decide/RetryStormDetectorTest.kt` | `UT-VP-2-11 cooldown expires after ten minutes` |
| UT-VP-2-12 | `decide/RetryStormDetectorTest.kt` | `UT-VP-2-12 records outside rolling minute do not count` |
| UT-VP-3-01 | `dns/UpstreamResolverTest.kt` | `UT-VP-3-01 DoH POST returns response and populates cache` |
| UT-VP-3-02 | `dns/UpstreamResolverTest.kt` | `UT-VP-3-02 HTTP 500 falls back to protected UDP` |
| UT-VP-3-03 | `dns/UpstreamResolverTest.kt` | `UT-VP-3-03 failed DoH and silent UDP produce SERVFAIL with original ID` |
| UT-VP-3-04 | `dns/UpstreamResolverTest.kt` | `UT-VP-3-04 cache hit rewrites transaction ID without upstream access` |
| UT-VP-3-05 | `dns/DnsCacheTest.kt` | `UT-VP-3-05 cache TTL is minimum bounded to 30 and 3600 seconds` |
| UT-VP-4-01 | `tun/TunSpecTest.kt` | `UT-VP-4-01 routes include virtual DNS and every HttpDNS CIDR` |
| UT-VP-4-02 | `tun/PacketLoopTest.kt` | `UT-VP-4-02 ad query gets zero address` |
| UT-VP-4-03 | `tun/PacketLoopTest.kt` | `UT-VP-4-03 normal query gets upstream answer` |
| UT-VP-4-04 | `tun/PacketLoopTest.kt` | `UT-VP-4-04 HttpDNS SYN rejected with RST` |
| UT-VP-4-05 | `tun/PacketLoopTest.kt` | `UT-VP-4-05 HttpDNS UDP rejected with ICMP` |
| UT-VP-4-06 | `tun/PacketLoopTest.kt` | `UT-VP-4-06 malformed packet is ignored and next packet works` |
| UT-VP-4-07 | `tun/PacketLoopTest.kt` | `UT-VP-4-07 closed input lets run return normally` |
| UT-VP-5-01 | `service/VpnControllerTest.kt` | `UT-VP-5-01 exclusions rebuild before old TUN closes` |
| UT-VP-5-02 | `service/VpnControllerTest.kt` | `UT-VP-5-02 newly registered sensitive App is excluded` |
| UT-VP-5-03 | `service/VpnControllerTest.kt` | `UT-VP-5-03 rule version hot swaps matcher without rebuilding TUN` |
| UT-VP-5-04 | `service/VpnControllerTest.kt` | `UT-VP-5-04 tenth blocked event emits retry storm` |
| UT-VP-5-05 | `service/EventBatcherTest.kt` | `UT-VP-5-05 two-second batches and per-pair minute throttle` |
| UT-VP-5-06 | `service/VpnControllerTest.kt` | `UT-VP-5-06 DNS event detail preserves actual subdomain` |
| UT-VP-5-07 | `service/VpnControllerTest.kt` | `UT-VP-5-07 empty and failed reward reads keep SDK blocked and loop running` |
| UT-VP-6-01 | `health/PrivateDnsDetectorTest.kt` | `UT-VP-6-01 only active named private DNS warns` |
| UT-VP-6-02 | `health/ServiceWatchdogTest.kt` | `UT-VP-6-02 disabled wanted accessibility reports STOPPED and notifies` |
| UT-VP-6-03 | `health/ServiceWatchdogTest.kt` | `UT-VP-6-03 healthy or never-enabled services do not notify` |
| UT-VP-7-01 | `service/VpnFailSafeTest.kt` | `UT-VP-7-01 read failure closes TUN before STOPPED report` |
| UT-VP-7-02 | `service/VpnFailSafeTest.kt` | `UT-VP-7-02 revoked authorization closes TUN and reports reason` |
| UT-VP-7-03 | `service/VpnFailSafeTest.kt` | `UT-VP-7-03 establish refusal is NOT_SETUP` |
| UT-VP-7-04 | `service/VpnFailSafeTest.kt` | `UT-VP-7-04 unavailable rules forward while degraded` |
| UT-VP-7-05 | `service/VpnFailSafeTest.kt` | `UT-VP-7-05 every exit calls stop service after closing all TUN handles` |
| MT-VP-01 | `VpnModuleTest.kt` | `MT-VP-01 standard observation reward pause resume script` |
| MT-VP-02 | `VpnModuleTest.kt` | `MT-VP-02 seeded thousand faults preserve loop and close on stop` |

## 文件清单与边界

- `packet/IpPacketTest.kt`、`packet/PacketBuilderTest.kt`：解析、截断、地址端口交换、校验和、RST/ACK、ICMP。
- `dns/DnsMessageTest.kt`、`dns/UpstreamResolverTest.kt`、`dns/DnsCacheTest.kt`：问题解析、应答、DoH/UDP 回退、缓存 ID 与 TTL。
- `decide/DnsDeciderTest.kt`、`decide/RetryStormDetectorTest.kt`：判定优先级、阈值、冷却与滚动窗口。
- `tun/TunSpecTest.kt`、`tun/PacketLoopTest.kt`：路由与管道 TUN 的实际报文处理。
- `service/VpnControllerTest.kt`、`service/EventBatcherTest.kt`、`service/VpnFailSafeTest.kt`：配置热更新、重建顺序、事件、奖励窗口故障、安全退出。
- `health/PrivateDnsDetectorTest.kt`、`health/ServiceWatchdogTest.kt`：警告与手写 checker/notifier。
- `VpnModuleTest.kt`：真实控制器和真实 PacketLoop 的场景脚本、固定种子 1000 次故障注入。
- `fakes/WireFixtures.kt`：独立构造网络字节、解读字段并检查校验和；真实 core-rules 编译器/匹配器；完整的 EffectiveConfig 样本。
- `fakes/TunFakes.kt`：手写 TunFactory/TunHandle、带报文边界的管道输入、捕获输出、上游与判定源。阻塞 IO 有超时，异常注入有 Channel 确认，不用 sleep。
- `fakes/MemoryData.kt`：真实 data 仓库接 Room 内存数据库；RuleStore 使用用后删除的临时目录。按构造参数类型接线，不依赖未定义的 DAO 方法名，也不继承 final 仓库。
- `fakes/ControllerFixture.kt`：可控 Clock、协程调度器、控制器接线、停止回调记录、判定源装饰器；构造推断集中在这里。

没有外部测试资源：所有报文、DNS 应答、规则均为代码生成的合成样本。没有真实 VPN、Koin 启动、跨进程、手机调用；不覆盖 DI/AC 或跨模块 MT-CT。DoH/UDP 测试只在执行时连接 `127.0.0.1` 上的 MockWebServer/手写 UDP 应答器，不查询公共服务器。

## 搬迁与测试配置

在项目根目录执行：

```sh
cp -R testing/unit/pending/engine-vpn/src/test engine-vpn/src/
```

`engine-vpn/build.gradle.kts` 已声明 JUnit4、kotlin.test.junit、Robolectric、coroutines-test、MockWebServer 等，且已开启 `unitTests.isIncludeAndroidResources`。本测试直接引用 Room API，需要补充**已在版本目录声明的**测试依赖（不新增库版本）：

```kotlin
testImplementation(libs.room.runtime)
```

data 自身必须先按计划实现 Room 数据库并生成 `SentinelDatabase_Impl`；无需在 engine-vpn 加 Room 编译插件或 KSP。Robolectric 测试使用 `sdk = [30]` 与 `manifest = Config.NONE`，避免启动真实服务或 Application。若离线运行，SDK 30 对应的 Robolectric Android jar 必须已缓存；测试代码不会下载或接受许可。

先核对 A4 接线；以后由开发方运行：

```sh
./gradlew :engine-vpn:testDebugUnitTest
```

本次没有修改 build.gradle.kts，也没有执行上述命令。源码仍只在暂存目录。

## 接口假设（9 条）

下列推断不构成既定契约；实现方要么按此实现，要么同步修改测试。已写死的 `IpPacket`、`PacketBuilder`、`DnsMessage`、`DnsDecider`、`DnsCache`、`DohUdpResolver`、`PacketLoop` 等参数顺序均照 engine-vpn/PLAN.md，core-rules API 以当前真实源码为准。

### A1：data 类型所在包

依据 data/PLAN.md Task 1 的 `db/Entities.kt` 与 `policy/EffectivePolicy.kt` 文件划分：枚举（EngineState、EngineId、ProtectLevel、RewardedMode 等）和实体推断在 `com.sentinel.data.db`；`EffectiveConfig` 推断在 `com.sentinel.data.policy`。其字段与参数顺序完全照 PLAN。Clock 位于 `com.sentinel.data`，仓库位于 `.repo`，规则存储位于 `.rules`，与计划文件路径一致。EffectiveConfig 的确切包名未写死，主要在 `WireFixtures.kt` 中调整。

### A2：Room 数据库可直接由测试建立

依据 data/PLAN.md Task 1 的 Room 数据库设计：`com.sentinel.data.db.SentinelDatabase` 推断为可供 `Room.inMemoryDatabaseBuilder` 使用的公开 RoomDatabase 子类。表名/列名采用 Entity 注解及属性名，枚举按名称存储、Boolean 为 SQLite 整数；不用未给出签名的 DAO 写入方法。数据库初始化回调不会要求启动网络订阅。手工写入初始化 global_state 用 INSERT OR IGNORE，不覆盖已有配置。Room 查询协程使用测试上下文，避免依赖真实等待。

### A3：仓库构造与 DAO 获取

依据 data/PLAN.md Task 2「对应 DAO（或 SentinelDatabase）+ Clock」与 Task 3 消费 GlobalStateRepository/SignalRepository：测试按参数类型反射选择唯一公开构造器，注入数据库或数据库上唯一无参 DAO 获取器、Clock，以及需要的其他 `.repo` 仓库。未假设 DAO 类名/方法名/参数顺序，不使用 Unsafe 或模拟框架。若实现采用其他构造依赖、多个候选构造器或多个同类型 DAO 获取器，需要同步调整 `MemoryData.repository`。

### A4：VpnController 完整构造参数与测试入口

engine-vpn/PLAN.md Task 5 仅写 `VpnController(scope, tunFactory, ...)`，省略依赖；Task 7 与 UT-VP-7-05 又要求停止服务回调。因此 `ControllerFixture.kt` 采用以下**推断签名**（参数名也需核对，因为使用命名参数）：

```kotlin
class VpnController(
    scope: CoroutineScope,
    tunFactory: TunFactory,
    selfPkg: String,
    apps: AppConfigRepository,
    global: GlobalStateRepository,
    overrides: OverrideRepository,
    rewards: RewardWindowRepository,
    ruleStore: RuleStore,
    events: EventRepository,
    signals: SignalRepository,
    status: EngineStatusRepository,
    resolver: UpstreamResolver,
    pkgs: PackageResolver,
    clock: Clock,
    stopService: () -> Unit,
    decisionSourceDecorator: (DecisionSource) -> DecisionSource = { it }
)
```

除装饰器外的参数来自 Task 5 消费依赖、TunSpec 自身包名、可控时间和 Task 7 退出要求。**`decisionSourceDecorator` 是 PLAN 未定义的测试入口假设**：装饰内部真实 DecisionSource，既读取真实快照，也只在 MT-VP-02 的指定轮次注入异常。UT-VP-5-07 借此直接断言真实 context 的 rewardWindowOpen；不在测试中复制控制器快照逻辑。如果实现提供其他等价入口，改本测试接线，不能改为只测一个独立假 DecisionSource。

### A5：onRevoked 与停止回调

依据 Task 7：推断 `onRevoked()` 无参数，测试可在协程内调用（实现为普通函数或 suspend 都可）。内部异常、撤销、establish 返回 null、显式 stop 都调用注入的停止回调；关闭任何已创建 TUN 必须先于回调。matcher 不可用属于继续运行的 DEGRADED 路径，不调用停止回调。尚未建立 TUN 时无对象可关闭，只验证 NOT_SETUP 与停止回调。

### A6：EventBatcher 的定时调度

依据 Task 5 固定签名 `EventBatcher(events, intervalMs = 2_000)`：没有 scope/dispatcher/start/close 参数或方法可引用。测试推断其第一次 offer 后开始定时写库，定时任务使用可替换的 Dispatchers.Main。测试在第一次 offer 后 runCurrent，再精确推进 1999+1ms，断言写入前为空、写入后有完整一批。如果实现的作用域来自 Koin 或固定 Dispatchers.IO，必须补足计划中的测试入口并同步改测试；不能靠 sleep 或放宽断言通过。

### A7：EventBatcher 的节流时间源

依据 Task 5 每分钟最多一条与 testing/README.md 可控时间约定：既定构造没有 Clock 参数，测试同时推进仓库 Clock/协程时间和 Robolectric ShadowSystemClock，推断节流使用可控的单调 Android SystemClock（或同一仓库时钟），不用未受控的 System.currentTimeMillis。重复 DNS 事件不节流；HTTPDNS 按 (pkg, IP) 分组，到达一分钟后可再记录。若要求严格统一通过 Clock 注入，应在计划中补全 EventBatcher 时间接口再同步调整测试，见待确认。

### A8：HTTPDNS 事件的 detail

Task 5 只明确 DNS 事件 detail = 实际域名，未给 HTTPDNS 字段映射。测试推断 `HTTPDNS_REJECTED` 的 detail 为 `LoopEvent.HttpDnsRejected.ip`，用它验证同包不同 IP 不会错误去重。DNS detail 与 ruleId 则明确区分：子域名查询记录实际子域名，ruleId 仍为命中的父域规则。

### A9：压缩问题与 IO 适配

依据 Task 2 压缩指针与 Task 4 InputStream TUN：压缩问题样本的指针从首问题指向报文尾部保存的名字，推断解析器接受包内可解析的指针、不限定目标在指针之前；同时断言自指针与截断不接受。管道假 TUN 保持「一次缓冲区 read 一个报文」的设备边界；控制器/循环必须把阻塞 read 放在 IO 执行环境，使可控协程调度器能继续运行。实际 InputStream EOF (-1) 正常结束，人工 IOException 用于触发 controller 故障退出。

## 待确认

1. **构造签名缺失**：A4 的完整构造、停止回调和 DecisionSource 装饰入口未在产品计划写死。若拒绝增加装饰入口，需提供可直接观察内部 context、可给真实 PacketLoop 注入判定源故障的等价接口，随后只修改测试夹具。
2. **单测假依赖边界与 final 仓库冲突**：unit/PLAN.md 总则要求 UT 依赖一律假实现；data/PLAN.md 却只提供默认 final 的具体仓库，DAO 接口未定义。不能继承这些类，也不能写同名产品替身。本测试选择真实 data 仓库 + Room 内存数据库用于控制器、EventBatcher、watchdog（MT 同样使用内存 data）；因此这些 UT 暂时有 data 集成边界。需确认接受该边界，或提供可替换的仓库/DAO 接口再改为手写 fake。PacketLoop、上游、checker、notifier、TunFactory 均为手写 fake。
3. **EventBatcher 缺少 Clock、scope 和释放接口**：A6/A7 保持既定构造，只能通过 Main 调度和 ShadowSystemClock 控制。与「时间一律通过 Clock 注入」存在接口缺口；没有引用未定义的 start/close 来隐藏问题。若不采用 A6/A7，实现方应补全计划与测试；测试作用域的释放也应一并明确。
4. **故障包处理与上游契约**：UpstreamResolver 声明永不抛异常，但 MT-VP-02 明确要求让上游抛异常。本测试仅在故障注入时故意违反该承诺，验证 PacketLoop 的第二层保护；允许故障包无应答或 SERVFAIL，随后健康包必须正确返回，控制器保持 RUNNING。写库故障用 SQLite RAISE(ABORT) 触发，下一条拦截事件必须恢复记录。随机种子固定，三种故障各 333/334 轮，共 1000 轮。
5. **UT-VP-7-05「以上退出路径」范围**：UT-VP-7-04 matcher=null 明确继续运行，不是退出。本测试将 7-05 理解为内部异常、撤销、establish=null 和显式 stop；另在 7-04 验证降级继续运行且停止回调不调用。
6. **压缩指针目标方向**：PLAN 仅说「支持压缩指针」；首问题没有可供向后引用的早先域名，所以 A9 使用尾部名字样本。若要求 DNS 压缩严格只允许向后指针，应补充产品解析接口/测试样本约定，而不是把该编号改为空测试。

## 本次静态核对

编号集合与 `testing/unit/PLAN.md` Task VP 完全一致：48/48，未重复。测试文件名与计划一致。独立的 wire oracle 检查 IP/UDP/TCP/ICMP 校验和与 DNS 的 ID、类型、地址、TTL；没有调用被测 PacketBuilder 来生成期望输出。Kotlin PSI 只验证语法，不验证未来产品接口或行为。执行语法检查产生的临时文件已清理。
