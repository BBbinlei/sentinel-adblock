# core-rules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现纯 Kotlin 的规则模型、解析、编译与匹配，供所有引擎使用。

**Architecture:** `kotlin("jvm")` 模块，包 `com.sentinel.rules`。域名规则编译为排序二进制，`DomainMatcher` 基于 `ByteBuffer` 二分查找；UI 选择器在抽象节点树 `NodeView` 上匹配，便于用快照测试。

**Tech Stack:** Kotlin、kotlinx.serialization。

**Spec:** `docs/superpowers/specs/2026-10-01-adblock-sentinel-design.md`（5.1 节）；总调度见 `MASTER_PLAN.md`。

**Tests:** 全部测试定义在 `testing/unit/PLAN.md` 的「Task CR」（UT-CR-*、MT-CR-*）与 `testing/rule-regression/PLAN.md`（RR-01、RR-04、RR-05）。模块完成后执行关卡 **G1**（见 `testing/README.md`）。

## Global Constraints

见 `MASTER_PLAN.md`。本模块额外约束：禁止引用 `android.*`；所有域名一律小写、去掉末尾的 `.`。

## 每个任务的通用步骤

1. 按该任务列出的测试编号，在 `core-rules/src/test/` 下编写测试；
2. 运行，确认失败；
3. 实现；
4. 重跑，确认通过；
5. 提交。

---

### Task 1: 规则模型与域名解析器

**Files:**
- Create: `core-rules/src/main/kotlin/com/sentinel/rules/model/Rule.kt`
- Create: `core-rules/src/main/kotlin/com/sentinel/rules/model/EventKind.kt`
- Create: `core-rules/src/main/kotlin/com/sentinel/rules/parse/DomainNormalizer.kt`
- Create: `core-rules/src/main/kotlin/com/sentinel/rules/parse/HostsParser.kt`
- Create: `core-rules/src/main/kotlin/com/sentinel/rules/parse/AdGuardParser.kt`

**Tests:** UT-CR-1-01～04

**Interfaces:**
- Produces:
  ```kotlin
  enum class RuleLevel { STANDARD, STRONG }
  enum class DomainTag { AD, AD_SDK, TRACKER, HTTPDNS }
  sealed interface RuleScope {
      data object Global : RuleScope
      data class App(val pkg: String) : RuleScope
      data class Page(val pkg: String, val activity: String) : RuleScope
  }
  enum class UiAction { CLICK, REWARDED_HANDLE, NOTIFY_AUTORENEW }
  enum class UiPhase { LAUNCH, ANYTIME }
  sealed interface Rule { val id: String; val source: String }
  data class DnsRule(override val id: String, val domain: String, val tag: DomainTag,
                     val level: RuleLevel, override val source: String) : Rule   // id = "dns:$domain"
  data class UiRule(override val id: String, val scope: RuleScope, val selector: String,
                    val action: UiAction, val phase: UiPhase, override val source: String) : Rule
  data class NotifyRule(override val id: String, val pkg: String?, val channelId: String?,
                        val keywords: List<String>, override val source: String) : Rule
  enum class EventKind { SPLASH_SKIPPED, POPUP_CLOSED, REWARDED_SILENCED, JUMP_REVERTED,
      DNS_BLOCKED, HTTPDNS_REJECTED, NOTIFICATION_CANCELLED, SYSTEM_OP, AUTO_RENEW_WARNED, WOULD_BLOCK }
  data class ParseResult<T : Rule>(val rules: List<T>, val skipped: Int)
  object DomainNormalizer { fun normalize(raw: String): String? }   // 非法返回 null
  object HostsParser { fun parse(text: String, source: String, level: RuleLevel, tag: DomainTag): ParseResult<DnsRule> }
  object AdGuardParser { fun parse(text: String, source: String, level: RuleLevel, tag: DomainTag): ParseResult<DnsRule> }
  ```

规则：`HostsParser` 接受 `0.0.0.0 域名`、`127.0.0.1 域名` 与纯域名行，忽略 `#` 注释与 `localhost`。`AdGuardParser` 只接受 `||域名^`；带 `$` 修饰符、`@@` 例外、`##` 元素隐藏的行计入 `skipped`，`!` 注释不计。`DomainNormalizer`：trim、小写、去末尾点；拒绝 IP、含 `*`、无点、含非法字符的输入。

- [ ] **Step 1:** 编写 UT-CR-1-01～04。
- [ ] **Step 2:** 运行 `./gradlew :core-rules:test --tests "com.sentinel.rules.parse.*"`，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(core-rules): rule model and domain parsers`。

### Task 2: 域名编译器与匹配器

**Files:**
- Create: `core-rules/src/main/kotlin/com/sentinel/rules/domain/DomainCompiler.kt`
- Create: `core-rules/src/main/kotlin/com/sentinel/rules/domain/DomainMatcher.kt`
- Create: `core-rules/src/main/kotlin/com/sentinel/rules/domain/NeverBlockList.kt`

**Tests:** UT-CR-2-01～04

**Interfaces:**
- Consumes: `DnsRule`、`DomainTag`、`RuleLevel`（Task 1）。
- Produces:
  ```kotlin
  object DomainCompiler { fun compile(rules: Collection<DnsRule>): ByteArray }
  data class DomainHit(val ruleDomain: String, val tag: DomainTag, val level: RuleLevel) { val ruleId get() = "dns:$ruleDomain" }
  class DomainMatcher(buffer: ByteBuffer) {
      fun lookup(domain: String): DomainHit?          // 先查 NeverBlockList，命中则返回 null
      val size: Int
      companion object {
          fun load(file: File): DomainMatcher          // FileChannel.map(READ_ONLY)
          fun verify(bytes: ByteArray): Boolean        // 校验 magic/version/偏移越界
      }
  }
  object NeverBlockList { fun contains(domain: String): Boolean }   // 后缀匹配
  ```

二进制格式（小端）：`"SNTL"` 4 字节 | version u16 = 1 | count u32 | offsets count×u32（相对条目区起点）| 条目区：每条 `tag u8, level u8, len u16, UTF-8 bytes`。条目按域名字节序升序；同一域名重复时保留 STANDARD。查找：从完整域名开始，逐次去掉最左一级，直到剩两级，每级二分查找精确匹配，返回第一个命中（最具体）。

`NeverBlockList` 内置后缀：`alipay.com`、`alipayobjects.com`、`tenpay.com`、`wechatpay.cn`、`unionpay.com`、`95516.com`、`cmbchina.com`、`icbc.com.cn`、`ccb.com`、`abchina.com`、`boc.cn`、`bankcomm.com`、`psbc.com`、`12306.cn`、`gov.cn`。

- [ ] **Step 1:** 编写 UT-CR-2-01～04。
- [ ] **Step 2:** 运行 `./gradlew :core-rules:test --tests "*DomainMatcherTest"`，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(core-rules): binary domain compiler and matcher`。

### Task 3: 内置目录

**Files:**
- Create: `core-rules/src/main/kotlin/com/sentinel/rules/catalog/StandardDomains.kt`（读取资源 `standard_domains.txt`）
- Create: `core-rules/src/main/resources/standard_domains.txt`
- Create: `core-rules/src/main/kotlin/com/sentinel/rules/catalog/HttpDnsCatalog.kt`
- Create: `core-rules/src/main/kotlin/com/sentinel/rules/catalog/SensitiveApps.kt`
- Create: `core-rules/src/main/kotlin/com/sentinel/rules/catalog/JumpTargetCatalog.kt`

**Tests:** UT-CR-3-01～03；误拦由 RR-01 把关（G1）。

**Interfaces:**
- Produces:
  ```kotlin
  object StandardDomains { fun rules(): List<DnsRule> }          // level = STANDARD
  data class Cidr(val address: ByteArray, val prefix: Int) { fun contains(ip: ByteArray): Boolean; companion object { fun parse(s: String): Cidr } }
  object HttpDnsCatalog { val domains: List<String>; val cidrs: List<Cidr>; fun rules(): List<DnsRule> }   // tag = HTTPDNS, level = STANDARD
  object SensitiveApps { fun isSensitive(pkg: String, label: String?): Boolean }
  object JumpTargetCatalog { fun isAdLanding(pkg: String): Boolean }
  ```

数据内容：
- `standard_domains.txt` 每行 `域名<TAB>标签`，初始种子：`pangolin-sdk-toutiao.com AD_SDK`、`pangolin-sdk-toutiao-b.com AD_SDK`、`pglstatp-toutiao.com AD_SDK`、`gdt.qq.com AD_SDK`、`gdtimg.com AD_SDK`、`mobads.baidu.com AD_SDK`、`mobads-logs.baidu.com AD_SDK`、`ads.oppomobile.com AD`、`adukwai.com AD_SDK`、`sigmob.cn AD_SDK`。以后增补的域名必须通过 RR-01a。
- `HttpDnsCatalog`：域名与 IPv4/IPv6 CIDR 取自调研来源 `拦截HTTPDNS.sgmodule` 清单，全部收录不做删减。其中 `dns.weixin.qq.com` 等微信条目实际不影响微信，因为微信默认属于敏感应用，不经过 VPN。
- `SensitiveApps`：包名 `com.eg.android.AlipayGphone`、`com.tencent.mm`、`cmb.pb`、`com.icbc`、`com.chinamworld.main`、`com.android.bankabc`、`com.chinamworld.bocmbci`、`com.bankcomm.Bankcomm`、`com.yitong.mbank.psbc`、`com.unionpay`、`com.jd.jrapp`、`com.hexin.plat.android`；另外应用名匹配 `银行|证券|支付|钱包|保险|基金|信用卡` 也判为敏感。
- `JumpTargetCatalog`：`com.taobao.taobao`、`com.tmall.wireless`、`com.taobao.litetao`、`com.xunmeng.pinduoduo`、`com.jingdong.app.mall`、`com.achievo.vipshop`、`com.taobao.idlefish`、`com.ss.android.ugc.aweme`、`com.ss.android.ugc.aweme.lite`、`com.smile.gifmaker`、`com.kuaishou.nebula`、`com.sankuai.meituan`、`me.ele`、`com.UCMobile`、`com.baidu.searchbox`、`com.heytap.market`、`com.oppo.market`、`com.nearme.instant.platform`、`com.nearme.gamecenter`。

- [ ] **Step 1:** 编写 UT-CR-3-01～03。
- [ ] **Step 2:** 运行 `./gradlew :core-rules:test --tests "*CatalogTest"`，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(core-rules): built-in catalogs`。

### Task 4: 选择器

**Files:**
- Create: `core-rules/src/main/kotlin/com/sentinel/rules/ui/NodeView.kt`
- Create: `core-rules/src/main/kotlin/com/sentinel/rules/ui/Selector.kt`
- Create: `core-rules/src/main/kotlin/com/sentinel/rules/ui/SnapshotNode.kt`（可序列化，测试与录制用）

**Tests:** UT-CR-4-01～04

**Interfaces:**
- Produces:
  ```kotlin
  data class Rect4(val l: Int, val t: Int, val r: Int, val b: Int)
  interface NodeView { val className: String?; val text: String?; val desc: String?; val viewId: String?
                       val clickable: Boolean; val checked: Boolean; val bounds: Rect4; val children: List<NodeView> }
  @Serializable data class SnapshotNode(...) : NodeView      // 字段同上，children: List<SnapshotNode>
  class SelectorSyntaxException(msg: String) : Exception(msg)
  class Selector private constructor(...) {
      fun findFirst(root: NodeView): NodeView?
      fun findAll(root: NodeView): List<NodeView>
      companion object { fun parse(text: String): Selector }
  }
  ```

语法（本项目子集）：`segment (( " > " | " " ) segment)*`，最后一段为目标。`>` 表示直接子节点，空格表示任意后代。`segment = (类名简称 | *)? ("[" attr op value "]")*`。属性：`text`、`desc`、`vid`（id 中 `:id/` 之后的部分）、`id`、`clickable`、`checked`。运算符：`=`、`^=`、`$=`、`*=`、`~=`（正则，对整串做 `find`）。值用双引号，`true/false` 不加引号。实现：手写递归下降解析器；匹配时先找满足目标段的节点，再向上验证祖先链（匹配期间维护父指针表）。

- [ ] **Step 1:** 编写 UT-CR-4-01～04。
- [ ] **Step 2:** 运行 `./gradlew :core-rules:test --tests "*SelectorTest"`，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(core-rules): selector engine on NodeView`。

### Task 5: UI 规则解析、索引与内置规则

**Files:**
- Create: `core-rules/src/main/kotlin/com/sentinel/rules/parse/Json5.kt`
- Create: `core-rules/src/main/kotlin/com/sentinel/rules/parse/GkdParser.kt`
- Create: `core-rules/src/main/kotlin/com/sentinel/rules/parse/JsonRuleParser.kt`
- Create: `core-rules/src/main/kotlin/com/sentinel/rules/ui/UiRuleIndex.kt`
- Create: `core-rules/src/main/kotlin/com/sentinel/rules/ui/BuiltInUiRules.kt`
- Create: `core-rules/src/main/kotlin/com/sentinel/rules/ui/BuiltInPatterns.kt`

**Tests:** UT-CR-5-01～05；内置开屏规则对真实快照的效果由 RR-02 把关（G4）。

**Interfaces:**
- Consumes: `UiRule`、`Selector`（Task 1、4）。
- Produces:
  ```kotlin
  object Json5 { fun toJson(text: String): String }
  object GkdParser { fun parse(json: String, source: String): ParseResult<UiRule> }
  object JsonRuleParser { fun parse(json: String): List<Rule>; fun encode(rules: List<Rule>): String }
  data class CompiledUiRule(val rule: UiRule, val selector: Selector)
  class UiRuleIndex private constructor(...) {
      fun lookup(pkg: String, activity: String?): List<CompiledUiRule>   // 顺序：Page → App → Global
      val invalidCount: Int
      companion object { fun build(rules: List<UiRule>): UiRuleIndex }   // 选择器语法错误的规则跳过并计数
  }
  object BuiltInUiRules { val all: List<UiRule> }
  object BuiltInPatterns { val rewardEntry: Regex; val shakeHint: Regex; val closeLike: Regex; val autoRenew: Regex }
  ```

固定值：
- `BuiltInUiRules`：
  - `builtin:splash-skip`，Global，`[text~="^\s*跳过(广告)?\s*\d{0,2}\s*[sS秒]?\s*$"]`，CLICK，LAUNCH；
  - `builtin:rewarded`，Global，`[text~="奖励将于\d+秒后发放|\d+\s*秒后可领取奖励|已获得奖励|恭喜获得奖励"]`，REWARDED_HANDLE，ANYTIME；
  - `builtin:autorenew`，Global，`[checked=true]`，NOTIFY_AUTORENEW，ANYTIME（引擎再结合 `autoRenew` 文字判断）。
- `BuiltInPatterns`：`rewardEntry = 看.{0,4}视频|领.{0,4}奖励|双倍|翻倍`；`shakeHint = 摇一摇|扭一扭|摇动手机|转动手机`；`closeLike = ^(关闭|×|✕|跳过|不感兴趣|以后再说|暂不)$`；`autoRenew = 自动续费|连续包月`。
- `Json5.toJson`：处理注释、未加引号的键、单引号字符串、尾随逗号。
- `GkdParser`：输入是 JSON 或 JSON5，先经 `Json5.toJson` 再解析。读取 `apps[].id`、`apps[].groups[].rules[]` 的 `activityIds` 与 `matches`；只接受**单段、仅属性**的选择器（去掉开头的 `@`），其余计入 `skipped`。有 `activityIds` 的生成 `Page` 作用域（多个 activity 生成多条），否则 `App`。动作一律 CLICK，阶段一律 ANYTIME；rule id = `gkd:<appId>:<groupKey>:<序号>`。

- [ ] **Step 1:** 编写 UT-CR-5-01～05。
- [ ] **Step 2:** 运行 `./gradlew :core-rules:test --tests "com.sentinel.rules.ui.*" --tests "*GkdParserTest" --tests "*JsonRuleParserTest"`，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(core-rules): ui rule parsing, index and built-ins`。

### Task 6: 通知匹配

**Files:**
- Create: `core-rules/src/main/kotlin/com/sentinel/rules/notify/NotifyMatcher.kt`

**Tests:** UT-CR-6-01～04

**Interfaces:**
- Produces: `object NotifyMatcher { fun isValid(rule: NotifyRule): Boolean; fun matches(rule: NotifyRule, pkg: String, channelId: String?, title: String?, text: String?): Boolean }`

语义：`pkg`/`channelId` 为 null 表示任意；`keywords` 为空表示任意，否则标题或正文包含任一关键词即命中；三者全为空的规则无效。

- [ ] **Step 1:** 编写 UT-CR-6-01～04。
- [ ] **Step 2:** 运行 `./gradlew :core-rules:test --tests "*NotifyMatcherTest"`，期望 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 重跑，期望 PASS。
- [ ] **Step 5:** 提交 `feat(core-rules): notification matcher`。

### 模块完成 → 关卡 G1

- [ ] 按 `testing/unit/PLAN.md` 编写 MT-CR-01～03；按 `testing/rule-regression/PLAN.md` 完成 RR-01、RR-04、RR-05；执行 `testing/README.md` 中 G1 的全部项目，写关卡报告。
