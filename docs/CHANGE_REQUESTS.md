# 变更登记

## CR-2026-10-02-HTTPDNS-BAIDU

| 项目 | 内容 |
|---|---|
| 编号 | CR-2026-10-02-HTTPDNS-BAIDU |
| 日期 | 2026-10-02 |
| 发起通道 | httpdns-baidu（Codex，`chan/httpdns-baidu`） |
| 改了什么 | 在 `HttpDnsCatalog.cidrs` 按原列表顺序新增 `180.76.76.76/32`、`180.76.76.112/32`；固定数量断言 59→61，补命中、相邻地址未命中、无重复及网络边界测试。 |
| 为什么 | `httpsdns.baidu.com` 已被目录覆盖，但硬编码服务 IP 可绕过域名解析；补精确主机路由，使百度 HTTPDNS 直连请求进入现有拒绝分支。 |
| 是否向后兼容 | 是：目录追加数据，已有条目、签名、契约、序列化与依赖版本不变。网络行为扩展到新增两个 IP，具体协议影响见下文。用户已允许此次冻结目录变更在指定 worktree 实施。 |
| 影响通道 | core-rules 目录与测试；engine-vpn 的 TunSpec 自动增加两条 /32 路由，PacketLoop 沿用现有拒绝逻辑。engine-vpn、data、app 及依赖均未修改。其他通道检查冻结差异时按现有脚本约定使用 `ALLOW_FROZEN=1`。 |
| 证据 | 设计依据及 APK 原始字符串、PacketLoop/TunSpec 源码，详见下文。 |
| 批准人 | 用户（2026-10-02 在对话中书面同意） |

证据来源：

- `/Users/binlei/广告拦截软件/docs/SPLASH_NO_AD_DESIGN.md` 第 2 节与 A2：百度网盘 dex 中存在 `https://httpsdns.baidu.com/v6/0010/` 与硬编码 `180.76.76.76`，允许补服务 IP，需登记冻结目录的兼容新增。
- APK 只读提取文件：`/private/tmp/claude-501/-Users-binlei-------/953adea0-24f5-4d8e-b26f-f3118c929464/scratchpad/apk/allstrings.txt`。`rg -n '180\.76\.76\.|httpsdns'` 显示第 3325 行为 `180.76.76.76`，第 97265、109637 行为百度 httpsdns v6 URL。
- 同文件第 1651581 行：百度 TurboNet 配置的 `bdns` 对象启用 `baidu_dns_enabled`，且 `customize_http_dns_server_url_prefix` 明确为 `https://180.76.76.112/v2/0010`；第 1351065～1351067 行还有该 IP 的 `/v6/0010` 与 `/v6/0025` 服务 URL。因此 `.112` 也作为精确 /32 收录。
- `180.76.76.200` 仅在第 361808 行以孤立字符串出现，没有明确的 HTTPDNS 服务关联，保守不加入；不把既有 `186.76.76.200/32` 改成此地址。

协议影响核对：`engine-vpn/src/main/kotlin/com/sentinel/vpn/tun/TunSpec.kt` 将虚拟 DNS 主机路由与全部目录 CIDR 加入 TUN；`PacketLoop.kt` 的 UDP/53 DNS 分支还要求目的地址是 `10.111.0.2` 或 `fd11:1::2`。因此新增地址的 TCP（包括 HTTPS）返回 RST，UDP（包括直连这些地址的 53 端口）返回 ICMP 不可达。背景中“普通 UDP/53 不受影响”无法由当前源码确认；保持不变的是虚拟 DNS 的 UDP/53 处理。SDK 是否回落到系统 DNS、开屏广告出现率是否下降，仍需设备验证。
