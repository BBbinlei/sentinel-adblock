# APK 广告域名扫描

Python 3.9+，仅标准库，完全离线。直接读取 APK 内全部根目录 `classes*.dex` 的
`string_ids`；也接受已有 `strings` 文本。不解压到磁盘、不执行 APK、不请求域名。

```sh
python3 scripts/apk-ad-scan/scan.py /path/to/base.apk
python3 scripts/apk-ad-scan/scan.py /path/to/allstrings.txt --format domains
python3 scripts/apk-ad-scan/tests/run_tests.py
```

默认每行 `域名<TAB>分类<TAB>SDK<TAB>命中依据<TAB>首个字符串位置`，排序去重。
`--format domains` 只输出确认广告域名，无标题、注释或通配符；空确认集输出为空。
失败返回 2，扫描完成前不输出部分清单。输入只读，输出到 stdout。

## 分类依据

`scan.py` 的 `SDK_FEATURES` 是数据表，后缀匹配必须在 DNS 标签边界，精确主机
不扩大到父域或子域。依据限定为项目已有 AD_SDK 种子、任务人工确认列表，以及
2026-10-02 网盘 APK 用本机 `build-tools/36.1.0/dexdump -d` 核对的 `const-string`：

| SDK | 已核实特征 | 依据 |
|---|---|---|
| 穿山甲/字节广告 | `pangolin-sdk-toutiao.com`、`pangolin-sdk-toutiao-b.com`、`pglstatp-toutiao.com` 后缀 | `core-rules/PLAN.md` Task 3、`standard_domains.txt` |
| 广点通/优量汇 | `gdt.qq.com`、`gdtimg.com` 后缀；`pgdt.ugdtimg.com`、`adsmind.ugdtimg.com` 精确主机 | 项目种子及任务人工确认 |
| 百度联盟 | `mobads.baidu.com`、`mobads-logs.baidu.com` 后缀 | 项目种子及任务人工确认；是百度主域保护的两个专用例外 |
| Ubix | `adx-cfg-u1.ubixioe.com`、`adx-data-u1.ubixioe.com`、`entry-su1.ubixioe.com` 精确主机 | 任务人工确认 |
| Vlion | `bid-adx2.vlion.cn`；两个 `bj-td-*-callback.advlion.com` 主机；`api-v3.mentamob.com`、`api-gray-v3.mentamob.com` 精确主机 | 人工确认；`classes24.dex` 的 `cn/vlion/ad/inland/ad/utils/smart/SmartVlionHttpUtil`、`base/network/ok/HttpRequestUtil`、`core/o` |
| Octopus | `sdk.zhangyuyidong.cn`、`sdklog.zhangyuyidong.cn` 精确主机 | `classes29.dex` 的 `com/octopus/ad/internal/q` |
| 美数 | `sdk.1rtb.net`、`sdk-demo.1rtb.net`、`sdk-report.1rtb.com`、`dsp.1rtb.com` 精确主机 | `classes28.dex` 的 `com/meishu/sdk/core/AdSdk`、`loader/c`、`exception/a`、`uri/a`、`utils/r0` |
| 快手联盟 | `adukwai.com` 后缀 | 项目 AD_SDK 种子 |
| Sigmob | `sigmob.cn` 后缀 | 项目 AD_SDK 种子 |
| Mintegral | `mintegral`、`mbridge` 关键词，仅疑似 | 离线材料未核实专用域名 |
| 倍孜 | `beizi`、`adxbid` 关键词，仅疑似 | 离线材料未核实专用域名 |
| 趣盟 | `qumeng` 关键词，仅疑似 | 离线材料未核实专用域名 |

未核实 SDK 名称或 `ad/ads/adx/bid/splash/union/mobads` 等广告词命中只列疑似，
不进入纯域名清单。`baidu.com` 主站、网盘、HTTPDNS、`baidupcs.com`、`bcebos.com`
等第一方/共享服务优先保护，即使主机名里含广告词也不加入确认清单。
其他主机没有广告证据时归入「第一方/不能拦」并标明「未判定」，不声称都是第一方。

## 扫描边界

「确认」表示命中已核实广告 SDK 特征，不证明当前版本真的请求该域名，也不等于
真机验证可以安全拦截。域名包只适用于来源 App；不推导父域、不扫描 IP、不写运行时规则。
专用 SDK 的配置、上报、测试域也会列出，阻断可能影响该 SDK 的激励广告。

URL 提取主机，忽略端口、凭据、路径、查询参数，支持转义斜线、大小写及 IDNA；
裸域名以 DNS 语法及 `tlds.txt` 的公共 TLD 提取，排除常见 Java/JS 命名空间。
`tlds.txt` 是本机 OpenJDK 27 `public_suffix_list.dat` ZIP 条目名的离线快照，
仅保存公共顶级域名称（含 IDNA）；来源 SHA-256 写在文件头，未复制完整 PSL 规则。
它不需要 Java 或网络；另识别 `example/test/invalid/localhost` 保留测试域。
未作 DNS 查询，同形标识符仍可能成为「未判定」候选；新 TLD、私有裸域名及
某些与命名空间同形的裸域名会被过滤；`.zip/.so` 等真实 TLD 不作为文件扩展名丢弃。
URL 主机不受 TLD 快照限制。
`strings` 的长度字节/拼接噪声无法可靠逆推，优先使用 APK 输入。

只支持标准小端 DEX 035–040。未扫描 native 库、资源、加密或动态拼接字符串、
动态下载的 SDK 和 split APK；它们需要分别提供 APK/字符串或另行分析。
不覆盖 HTTPDNS/IP 直连，也不解决 `pan.baidu.com` 上的自营广告。
