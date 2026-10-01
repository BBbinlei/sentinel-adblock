# engine-vpn Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `:vpn` 进程中实现只接管 DNS 与 HttpDNS IP 段的本地 VPN，按每个 App 的生效配置过滤广告域名，且任何故障都不会导致断网。

**Architecture:** Android library，包 `com.sentinel.vpn`。纯逻辑（IP/UDP/TCP/ICMP 报文解析与构造、DNS 报文、`DnsDecider`、重试风暴检测）放在 `packet`、`dns`、`decide` 包；`VpnController` 是不依赖 `VpnService` 的状态机；`SentinelVpnService` 只做薄封装。

**Tech Stack:** VpnService、OkHttp（DoH）、Kotlin 协程、Koin。

**Spec:** `docs/superpowers/specs/2026-10-01-adblock-sentinel-design.md`（5.2、7 节）；总调度见 `MASTER_PLAN.md`「设计补充」第 1 条。

**Tests:** 全部测试定义在 `testing/unit/PLAN.md` 的「Task VP」（UT-VP-*、MT-VP-*）与 `testing/device-integration/PLAN.md`（DI-01～03、DI-11～14）。模块完成后执行关卡 **G3**。

## Global Constraints

见 `MASTER_PLAN.md`。本模块固定值：
- TUN 地址 `10.111.0.1/32`、`fd11:1::1/128`；虚拟 DNS `10.111.0.2`、`fd11:1::2`；MTU 1500；会话名「哨兵」。
- 上游：DoH `https://223.5.5.5/dns-query`（POST `application/dns-message`，超时 3s）；失败回退 UDP `223.5.5.5:53`（超时 2s）；都失败返回 SERVFAIL。
- DNS 缓存 2000 条，TTL 取应答中的最小 TTL，并夹在 [30s, 3600s]。
- 拦截应答：A → `0.0.0.0`，AAAA → `::`，TTL 60；其他类型 → NOERROR 空应答。
- 重试风暴：同一 (pkg, ruleId) 60 秒内 ≥10 次；触发后 10 分钟内不重复上报。
- TUN 重建防抖 2 秒；事件批量写库间隔 2 秒；HTTPDNS 拒绝事件同一 (pkg, IP) 每分钟最多记 1 条。
- 守护检查间隔 5 分钟；通知渠道 id `vpn`；前台通知 id `1001`。

## 每个任务的通用步骤

按该任务列出的测试编号在 `engine-vpn/src/test/` 下编写测试 → 运行确认失败 → 实现 → 重跑确认通过 → 提交。运行命令：`./gradlew :engine-vpn:testDebugUnitTest --tests "<该任务测试类>"`。

---

### Task 1: 报文解析与构造

**Files:**
- Create: `engine-vpn/src/main/kotlin/com/sentinel/vpn/packet/IpPacket.kt`
- Create: `engine-vpn/src/main/kotlin/com/sentinel/vpn/packet/PacketBuilder.kt`
- Create: `engine-vpn/src/main/kotlin/com/sentinel/vpn/packet/Checksum.kt`

**Tests:** UT-VP-1-01～07

**Interfaces:**
- Produces:
  ```kotlin
  enum class Proto { TCP, UDP, ICMP, ICMPV6, OTHER }
  class IpPacket(val raw: ByteArray, val length: Int, val version: Int, val proto: Proto,
                 val src: ByteArray, val dst: ByteArray, val srcPort: Int, val dstPort: Int,
                 val payloadOffset: Int, val payloadLength: Int, val tcpFlags: Int) {
      val isTcpSyn: Boolean
      companion object { fun parse(buf: ByteArray, len: Int): IpPacket? }   // 不支持或畸形返回 null
  }
  object PacketBuilder {
      fun udpReply(req: IpPacket, payload: ByteArray): ByteArray       // 交换地址与端口
      fun tcpRst(req: IpPacket): ByteArray                              // ack = req.seq + payloadLen（SYN 再 +1）
      fun icmpPortUnreachable(req: IpPacket): ByteArray                 // v4: type3 code3；v6: type1 code4
  }
  ```
  IPv6 只处理无扩展头的报文；带扩展头的返回 `proto = OTHER`。

- [ ] **Step 1:** 编写 UT-VP-1-01～07。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(engine-vpn): ip packet parse and build`。

### Task 2: DNS 报文与判定

**Files:**
- Create: `engine-vpn/src/main/kotlin/com/sentinel/vpn/dns/DnsMessage.kt`
- Create: `engine-vpn/src/main/kotlin/com/sentinel/vpn/decide/DnsDecider.kt`
- Create: `engine-vpn/src/main/kotlin/com/sentinel/vpn/decide/RetryStormDetector.kt`

**Tests:** UT-VP-2-01～12

**Interfaces:**
- Consumes: `DomainMatcher`、`DomainHit`、`DomainTag`、`RuleLevel`（core-rules）；`EffectiveConfig`、`ProtectLevel`（data）。
- Produces:
  ```kotlin
  data class DnsQuestion(val id: Int, val name: String, val qtype: Int)
  object DnsMessage { fun parseQuestion(payload: ByteArray, off: Int, len: Int): DnsQuestion?
      fun blockedResponse(query: ByteArray): ByteArray; fun servFail(query: ByteArray): ByteArray
      fun minTtl(response: ByteArray): Int?; fun withId(msg: ByteArray, id: Int): ByteArray }
  data class DnsContext(val cfg: EffectiveConfig?, val disabledRules: Set<String>, val rewardWindowOpen: Boolean)
  enum class DnsVerdict { FORWARD, BLOCK, WOULD_BLOCK }
  data class DnsDecision(val verdict: DnsVerdict, val hit: DomainHit?)
  object DnsDecider { fun decide(domain: String, matcher: DomainMatcher?, ctx: DnsContext): DnsDecision }
  class RetryStormDetector(clock: () -> Long) { fun record(pkg: String, ruleId: String): Boolean }
  ```

`DnsDecider.decide` 判定顺序：
1. matcher 为 null 或未命中 → FORWARD
2. `cfg?.level == OFF` → FORWARD
3. 命中 STRONG 规则而 App 为 STANDARD（`cfg == null` 视为 STANDARD）→ FORWARD
4. `hit.ruleId in disabledRules` → FORWARD
5. `hit.tag in RewardWindowContract.RELEASED_TAGS && rewardWindowOpen` → FORWARD（常量见 data 的 `contract` 包）
6. `cfg?.observing == true` → WOULD_BLOCK
7. 否则 BLOCK

- [ ] **Step 1:** 编写 UT-VP-2-01～12。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(engine-vpn): dns message and decider`。

### Task 3: 上游解析与缓存

**Files:**
- Create: `engine-vpn/src/main/kotlin/com/sentinel/vpn/dns/UpstreamResolver.kt`
- Create: `engine-vpn/src/main/kotlin/com/sentinel/vpn/dns/DnsCache.kt`

**Tests:** UT-VP-3-01～05

**Interfaces:**
- Produces:
  ```kotlin
  fun interface SocketProtector { fun protect(socket: java.net.Socket): Boolean }   // 由 VpnService.protect 提供
  interface UpstreamResolver { suspend fun resolve(query: ByteArray): ByteArray }    // 永不抛异常，失败返回 servFail
  class DohUdpResolver(dohUrl: String, udpServer: InetSocketAddress, protector: SocketProtector,
                       datagramProtector: (java.net.DatagramSocket) -> Boolean, cache: DnsCache) : UpstreamResolver
  class DnsCache(capacity: Int = 2000, clock: () -> Long) { fun get(name: String, qtype: Int): ByteArray?; fun put(name: String, qtype: Int, resp: ByteArray) }
  ```
  OkHttp 的 `socketFactory` 包一层，创建的每个 socket 都调用 `protector.protect`。

- [ ] **Step 1:** 编写 UT-VP-3-01～05。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(engine-vpn): doh upstream with udp fallback and cache`。

### Task 4: TUN 配置与报文循环

**Files:**
- Create: `engine-vpn/src/main/kotlin/com/sentinel/vpn/tun/TunSpec.kt`
- Create: `engine-vpn/src/main/kotlin/com/sentinel/vpn/tun/PacketLoop.kt`
- Create: `engine-vpn/src/main/kotlin/com/sentinel/vpn/tun/PackageResolver.kt`

**Tests:** UT-VP-4-01～07

**Interfaces:**
- Consumes: Task 1–3；`HttpDnsCatalog`（core-rules）。
- Produces:
  ```kotlin
  data class TunSpec(val addresses: List<Pair<String, Int>>, val dnsServers: List<String>,
                     val routes: List<Pair<String, Int>>, val disallowed: Set<String>, val mtu: Int) {
      companion object { fun build(selfPkg: String, excluded: Set<String>): TunSpec } }   // routes = 虚拟 DNS + HttpDnsCatalog.cidrs
  fun interface PackageResolver { fun pkgFor(pkt: IpPacket): String? }                    // 实现用 getConnectionOwnerUid
  interface DecisionSource { fun matcher(): DomainMatcher?; fun context(pkg: String?): DnsContext }
  sealed interface LoopEvent { data class Dns(val pkg: String?, val domain: String, val decision: DnsDecision) : LoopEvent
                               data class HttpDnsRejected(val pkg: String?, val ip: String) : LoopEvent }
  class PacketLoop(input: InputStream, output: OutputStream, resolver: UpstreamResolver,
                   decisions: DecisionSource, pkgs: PackageResolver, onEvent: (LoopEvent) -> Unit) {
      suspend fun run()          // 读循环；input 关闭时正常返回；单个报文的错误被忽略；其他异常向上抛
  }
  ```

处理规则：
- 目的地址为虚拟 DNS 且是 UDP/53 → 解析问题 → `DnsDecider`：BLOCK 回 `blockedResponse`；FORWARD/WOULD_BLOCK 交给上游，应答经 `udpReply` 写回。DNS 查询并发上限 8。
- 目的地址为虚拟 DNS 的 TCP → `tcpRst`。
- 目的地址落在 HttpDNS CIDR：TCP → `tcpRst`，UDP → `icmpPortUnreachable`，其他丢弃。
- 写 `output` 需加锁。

- [ ] **Step 1:** 编写 UT-VP-4-01～07。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(engine-vpn): tun spec and packet loop`。

### Task 5: VpnController 与服务接线

**Files:**
- Create: `engine-vpn/src/main/kotlin/com/sentinel/vpn/service/VpnController.kt`
- Create: `engine-vpn/src/main/kotlin/com/sentinel/vpn/service/SentinelVpnService.kt`
- Create: `engine-vpn/src/main/kotlin/com/sentinel/vpn/service/EventBatcher.kt`
- Create: `engine-vpn/src/main/kotlin/com/sentinel/vpn/service/VpnNotification.kt`
- Create: `engine-vpn/src/main/kotlin/com/sentinel/vpn/service/BootReceiver.kt`
- Create: `engine-vpn/src/main/kotlin/com/sentinel/vpn/di/VpnModule.kt`
- Create: `engine-vpn/src/main/kotlin/com/sentinel/vpn/di/VpnEntry.kt`
- Create: `engine-vpn/src/main/resources/META-INF/services/com.sentinel.data.module.ModuleEntry`
- Modify: `engine-vpn/src/main/AndroidManifest.xml`（服务 `android:process=":vpn"`、`foregroundServiceType="specialUse"`、`BIND_VPN_SERVICE` 权限；`BootReceiver`）

**Tests:** UT-VP-5-01～07

**Interfaces:**
- Consumes: Task 1–4；data 的 `AppConfigRepository.observeExcluded/observeAll`、`GlobalStateRepository`、`OverrideRepository.observeDisabled`、`RewardWindowRepository.observeOpen`、`RuleStore`、`EventRepository`、`SignalRepository`、`EngineStatusRepository`、`PackageWatcher`、`EffectivePolicy`。
- Produces:
  ```kotlin
  interface TunFactory { fun establish(spec: TunSpec): TunHandle? }          // 由服务用 VpnService.Builder 实现
  interface TunHandle : Closeable { val input: InputStream; val output: OutputStream }
  class VpnController(scope: CoroutineScope, tunFactory: TunFactory, ...) {
      suspend fun start(); suspend fun stop(reason: String)
      val state: StateFlow<EngineState>
  }
  class EventBatcher(events: EventRepository, intervalMs: Long = 2_000)  { fun offer(e: LoopEvent) }
  object VpnActions { const val PAUSE = "com.sentinel.vpn.PAUSE"; const val START = "com.sentinel.vpn.START" }
  val vpnModule: Module
  class VpnEntry : ModuleEntry      // id = "vpn"，processes = {VPN}，start：创建通知渠道 `vpn`
  ```
  对 app 暴露的只有 `VpnStarter` 需要的 Service 类名常量（`VpnActions`）；`:vpn` 进程由 `ModuleEntry` 机制启动，主进程不加载 `vpnModule`。

行为：
- `start()` 订阅排除名单，变化后防抖 2 秒；**先 establish 新 TUN、启动新 PacketLoop，再关闭旧 TUN**。
- 订阅 `ruleVersion`，变化时用 `RuleStore.loadDomainMatcher()` 热替换，不重建 TUN。
- `DecisionSource.context(pkg)` 从内存快照（生效配置、停用规则、奖励窗口）读取。
- `LoopEvent.Dns`：BLOCK → 记 `DNS_BLOCKED` 并喂给 `RetryStormDetector`，触发时 `SignalRepository.emit(pkg, RETRY_STORM, ruleId)`；WOULD_BLOCK → 记 `WOULD_BLOCK`。所有 DNS 事件的 `detail` = 实际查询的域名（应用详情页展示「将要拦截的域名」用）。
- 启动成功上报 `RUNNING`；服务内同时调用 `PackageWatcher.start()`。
- 前台通知的「暂停 5 分钟」按钮发送 `VpnActions.PAUSE` → `GlobalStateRepository.pauseFor()`。
- `BootReceiver` 收到 `BOOT_COMPLETED`，且全局开启、`VpnService.prepare()==null` 时启动服务。

- [ ] **Step 1:** 编写 UT-VP-5-01～07。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(engine-vpn): controller and service wiring`。

### Task 6: 私人 DNS 检测与服务守护

**Files:**
- Create: `engine-vpn/src/main/kotlin/com/sentinel/vpn/health/PrivateDnsDetector.kt`
- Create: `engine-vpn/src/main/kotlin/com/sentinel/vpn/health/ServiceWatchdog.kt`

**Tests:** UT-VP-6-01～03

**Interfaces:**
- Produces:
  ```kotlin
  object PrivateDnsDetector { fun evaluate(active: Boolean, serverName: String?): String? }   // 返回警告文案或 null
  class ServiceWatchdog(checker: EnabledServicesChecker, status: EngineStatusRepository, notifier: (EngineId) -> Unit) {
      suspend fun checkOnce() }                                          // 服务每 5 分钟调用一次
  interface EnabledServicesChecker { fun accessibilityEnabled(): Boolean; fun notificationListenerEnabled(): Boolean
      fun userWantsA11y(): Boolean; fun userWantsNotify(): Boolean }      // userWants* = engine_status 中曾经为 RUNNING
  ```

`evaluate`：`active && serverName != null` → `"系统「私人 DNS」设为指定服务器，会让网络拦截失效，请改为「自动」或「关闭」"`，否则 null。服务在启动时、以及网络变化时（`registerDefaultNetworkCallback`）检查；有警告就上报 VPN 状态 `DEGRADED` 并附该文案。

- [ ] **Step 1:** 编写 UT-VP-6-01～03。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(engine-vpn): private dns detection and watchdog`。

### Task 7: 故障安全

**Files:**
- Modify: `engine-vpn/src/main/kotlin/com/sentinel/vpn/service/VpnController.kt`
- Modify: `engine-vpn/src/main/kotlin/com/sentinel/vpn/service/SentinelVpnService.kt`

**Tests:** UT-VP-7-01～05

**Interfaces:**
- Produces: `VpnController.onRevoked()`；不变式：**任何退出路径都先关闭当前 TunHandle**。

行为：
| 情况 | 处理 |
|---|---|
| `PacketLoop.run()` 抛异常 | 关闭 TUN → 上报 `STOPPED`「网络拦截意外停止，网络已恢复直连」→ 停止服务 |
| `onRevoke()` | 关闭 TUN → 上报 `STOPPED`「VPN 授权被撤销或被其他 VPN 取代」 |
| `establish()` 返回 null | 上报 `NOT_SETUP` |
| 规则加载失败（matcher 为 null） | 继续运行、全部 FORWARD、上报 `DEGRADED`「规则不可用，暂停网络拦截」 |

- [ ] **Step 1:** 编写 UT-VP-7-01～05。
- [ ] **Step 2:** 运行，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(engine-vpn): fail-safe shutdown paths`。

### 模块完成 → 关卡 G3

- [ ] 按 `testing/unit/PLAN.md` 编写 MT-VP-01～02；与 `app/PLAN.md` Task 1–2 一起，执行 `testing/README.md` 中 G3 的全部项目（含模拟器 DI-01～03 与真机 DI-11～14、DI-51），写关卡报告。若 DI-11 显示 DNS 无法归属到 App，在报告中记录；此时按「pkg 为 null 时按 STANDARD 默认配置判定」运行（`DnsDecider` 已支持，不需要改代码）。
