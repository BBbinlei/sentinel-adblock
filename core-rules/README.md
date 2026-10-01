# core-rules 规则核心

纯 Kotlin 模块（不依赖 Android），是所有引擎的「大脑」：规则长什么样、怎么读、怎么编译、怎么匹配，全部在这里定义。

## 详细功能

### 1. 统一规则模型
三类规则共享一个 `Rule` 接口：
- **DnsRule**：一个域名 + 标签（`AD` 广告 / `AD_SDK` 广告 SDK 投放 / `TRACKER` 追踪 / `HTTPDNS`）+ 级别（`STANDARD` / `STRONG`）。
- **UiRule**：作用范围（全局 / 某 App / 某页面）+ 选择器 + 动作（点击 / 激励视频处理 / 自动续费提醒）+ 生效阶段（仅启动窗口 / 任何时候）。
- **NotifyRule**：App + 通知渠道 + 关键词。

### 2. 规则解析
- hosts 格式（`0.0.0.0 ad.example.com`）
- AdGuard 域名格式（`||ad.example.com^`，忽略不支持的高级语法并计数）
- GKD 订阅格式（取其中的 App、Activity、选择器字段）
- 本项目自定义 JSON（学习模式生成的规则、内置规则）

### 3. 规则编译
- 域名规则 → 排好序的二进制文件 `domains.bin`，供 VPN 进程用内存映射加载，内存占用几 MB。
- UI 规则 → 按「包名 + 页面」建立的索引，无障碍引擎查一次就知道当前页面有没有规则。

### 4. 匹配
- **域名匹配**：按后缀逐级查（`a.b.c.com` → `b.c.com` → `c.com`），最多约 5 次二分查找。
- **永不拦截名单**：支付、银行、系统更新、推送通道等关键域名，任何级别都不会命中，优先级最高。
- **选择器匹配**：GKD 风格选择器的子集（属性匹配 + 父子关系），在抽象的节点树 `NodeView` 上运行，便于用录制的页面快照做测试。
- **通知匹配**：按 App、渠道、关键词判断。

### 5. 内置数据
- **标准级精选域名表**：只含纯广告域名（穿山甲、优量汇、百青藤、OPPO 广告联盟等投放域名）。
- **HttpDNS 清单**：域名 + IP 段（供 VPN 快速拒绝）。
- **敏感应用清单**：银行、支付、证券类包名（默认不拦截）。
- **跳转目标目录**：电商、下载类等广告常见落地 App 的包名（跳转回退用）。
- **通用开屏跳过规则**：识别「跳过 3s」这类按钮的全局选择器。

## 对外接口（其他模块依赖）
`Rule` / `DnsRule` / `UiRule` / `NotifyRule`、各 Parser、`DomainCompiler`、`DomainMatcher`、`NeverBlockList`、`Selector`、`NodeView`、`UiRuleIndex`、`NotifyMatcher`、`SensitiveApps`、`JumpTargetCatalog`、`HttpDnsCatalog`、`EventKind`。

## 依赖
无（只依赖 Kotlin 标准库与 kotlinx.serialization）。
