# 央视频、菜鸟广告机制研究

采集日期：2026-10-04。结论来自用户手机上实际安装的 APK，以及本机 `dexdump -d` 的调用指令；不是商店版本、网络文章或仅凭 SDK 名称推测。此次完成静态逆向，不修改 APK、手机设置或哨兵规则。

## 结论

央视频的开屏至少包含两条实现：CMG 广告 SDK 的加载/展示链路，以及 CMS 启动图片或视频链路。CMG SDK 有独立的广告池、远程配置、缓存和上报地址，具备预加载缓存能力；CMS 链路也有启动配置、素材下载和超时处理。不能将普通启动图片一律当作商业广告。

菜鸟包含服务端 RTB 广告请求、多个第三方 SDK 的直连竞价、素材预加载与缓存、展示及点击反馈。穿山甲、优量汇、美数、Ubix 都有应用自身适配器直接调用加载方法，不只是孤立的 SDK 类名。它还包含优酷开屏适配器和阿里广告资源。具体一次启动选择哪个广告来源，取决于运行配置和加载结果，本次没有测量。

对于哨兵，两者都不能只凭“启动时请求了哪些 IP”决定拦截：缓存意味着素材可能在此前已下载；菜鸟的 MTOP 广告接口也不能作为 DNS 域名加入规则。独立广告 SDK 主机可作为后续验证候选；共享业务域名需要保留。

## 样本

| 应用 | 实际包名 | versionName / versionCode | APK 大小 | 根目录 DEX |
|---|---|---|---:|---:|
| 央视频 | `com.cctv.yangshipin.app.androidp` | `3.5.3.26910` / `305030` | 150882723 bytes | 11 |
| 菜鸟 | `com.cainiao.wireless` | `8.11.923` / `475` | 97199939 bytes | 13 |

连接手机报告型号为 `OCE_AN50`，不是计划里的 OPPO Find X7 Ultra。`pm path` 对两个包均只返回 `base.apk`。包版本、大小、SHA-256 见 [metadata.json](evidence/metadata.json)。

央视频 SHA-256：`ded396b535e74533072c4a3254d28d59a15da53ee7d0636b7ebc785261ad7442`。

菜鸟 SHA-256：`d09deab83b7e7a6853aba56033ba5d1f696bade566a9bae768b2f7ee9812d581`。

## 央视频：加载、缓存、展示分开

`ZSplashAD` 创建 `com.cmg.ads.splash.SplashAD`、设置监听器、调用 `fetchAdOnly()`，另一个方法再调用 `showAd(FrameLayout)`。`ZSplashProAD` 同样调用 `SplashProAD.fetchAdOnly()`。监听器分别处理 `onADLoaded`、`onADTick`、`onADClicked`、`onSkipClicked`、`onNoAD` 和超时，说明加载、倒计时、展示、点击和跳过是不同阶段。

`CMGADManager.loadCache()`、`ZDPreloadCacheManager.getPreloadPath()` 的真实调用证明 SDK 有缓存入口。`n.a.a.c` 的 URL 选择方法按 `CMGADManager.isDebug()` 区分调试和正式地址：

| 正式主机 / 路径 | 静态代码中的职责 | 调试地址 |
|---|---|---|
| `gapi.cmgadx.com/sdk/pool` | 广告池接口 | `adsdk001.cmgadx.com/sdk/pool` |
| `rapi.cmgadx.com/sdk/reports` | SDK 上报 | `adsdk002.cmgadx.com/sdk/reports` |
| `cdn.cmgadx.com/sdk/config/sdkConfig.json` | SDK 配置 | `test-pub-cdn.cmgadx.com` 对应路径 |
| `cdn.cmgadx.com/sdk/pool/` | 池资源 URL 前缀 | 同上 |
| `cdn.cmgadx.com/sdk/urlCache/` | URL 缓存资源前缀 | 同上 |

这些地址的广告组件归属已由字节码确认；本次没有观察请求，不能声称当前进程请求了全部主机，不能由此得出可安全阻断的 IP。

另一条链路：`LoadHomeSplashHelper` 创建 `GetAppStartUpPictureConfigRequest`；`SplashVideoDownManager` 管理启动视频相关文件；`CMSSplashView` 使用图片视图和 `SimplePlayerApi`，记录 `material_type`、`skip_btn`、`ad_load_timeout` 并处理超时。启动图配置与 CMG 商业投放必须分别验证。

完整 DEX 字符串还包含播放广告的 VIP 判断、前后台恢复、前贴片条件、频道条件、Banner 和弹窗相关文字。它们证明存在对应代码线索，尚未完整追踪播放广告调用链，不能断言广告是否与节目媒体流拼接，也不能断言全部页面当前都会显示广告。

证据：[字节码](evidence/yangshipin-bytecode.txt)、[关键调用行号索引](evidence/yangshipin-mechanism-index.tsv)、[字符串](evidence/yangshipin-strings.tsv)。索引第一列是对应字节码文件的一基行号，字节码保留 DEX 名、类、方法及指令偏移。

## 菜鸟：服务端广告和 SDK 竞价共同接入

服务端取广告入口为 `MtopCainiaoGuoguoRtbAdsGetRequest`，构造器把 `API_NAME` 设为 `mtop.cainiao.guoguo.nbnetflow.ads.show.cn`；`RtbAdsQueryApi` / `CnRtbAdController` 管理查询、回调和超时。该字符串是 MTOP API 名，不是被观察到的 DNS 请求。

第三方路径包括：

| 菜鸟自身适配器 | 下游调用证据 |
|---|---|
| `CsjSplashAdsManager` | 穿山甲开屏加载和展示回调；区分 `server_bidding_real_time` / `server_bidding_prefetch` |
| `YLHSplashManager` | 调用优量汇 `SplashAD.getECPM()`、`sendWinNotification()`、`sendLossNotification()` |
| `MSSplashManager` | 调用美数 `SplashAdLoader.loadAd()`、`ISplashAd.sendWinNotification()` / `sendLossNotification()` |
| `UBIXSplashManager` | 调用 `UBiXSplashManager.loadSplashAd()`、`loadAd()`、`loadBiddingAd()` |
| `YKSplashManager` | 优酷开屏适配器和加载/关闭回调，具体投放状态未测 |

`SDKBiddingRequestManager` / `SDKBiddingArena`、`key_sdk_bidding_floor_price` 和竞价结果实体，与上述报价和胜负通知共同支持“有 SDK 竞价机制”的结论；不能仅凭这些证明每次都选择最高价，也没有测得本次的底价、超时和优先级数值。

预加载入口有 `splash_rtb_request_at_homepage_idle` / `splash_rtb_response_at_homepage_idle`，`AdResourceProcessor` 有资源下载、缓存文件处理，穿山甲适配器区分预取和实时请求。由此推断，首页空闲时取得的广告可能服务于之后的开屏；这是代码机制推断，尚未用连续运行和流量时序验证。

APK 的 `assets/template/homepage/` 内还包含图片、视频、直播 ADX 模板，以及补贴、任务、奖励弹窗模板，说明广告体系不止开屏。模板存在不等于本次账号被下发或展示了这些内容。`assets/gdt_plugin/gdtadv2.jar` 是额外的插件资源，本次只确认存在，未展开分析其内部代码。

## 菜鸟：“禁止摇一摇”不等于关闭所有开屏广告

`CNHybridForbidSplashShakeApi.execute()` 接收 `refresh`，解析 JSON 的 `isShakeClose`。值为 `true` 时调用 `ads.utils.c.cb(true)`，接着调用 `deleteAllPreloadSplashByForbidShake()`，然后刷新相关配置；另一分支写 `false`。这是直接的调用证据，不只是界面文案。

`SplashForbidShakeAdsBean`、`mtop.cainiao.adx.feedback.forbid.cn`、灰度配置日志及 `onShake` / `pauseShake` / `resumeShake` 字符串也存在。结论是该版本有控制开屏摇一摇的代码入口，并考虑了旧预加载数据。界面可见路径、默认状态、远端生效范围和传感器阈值尚未测量；不保证可关闭全部 SDK 的交互，更不保证关闭其他广告。

证据：[字节码](evidence/cainiao-bytecode.txt)、[关键调用行号索引](evidence/cainiao-mechanism-index.tsv)、[字符串](evidence/cainiao-strings.tsv)、[广告相关资源](evidence/cainiao-assets.txt)。

## 对后续拦截的判断

| 候选措施 | 当前依据与限制 |
|---|---|
| 央视频 CMG 精确主机候选 | `gapi.cmgadx.com`、`cdn.cmgadx.com`、`rapi.cmgadx.com` 已确认归属广告组件；阻断效果、缓存残留及正常播放仍待测，不扩大为整个央视频域名或 IP 段 |
| 菜鸟第三方广告主机候选 | 字符串命中穿山甲、Ubix、美数特征；实际运行请求仍待测。优量汇插件的域名覆盖也未完整确认 |
| 菜鸟自营/聚合广告 | MTOP API 与普通业务可能共用网关；当前不做 HTTPS 解密，DNS 层不能按 API 名选择性阻断 |
| 开屏界面跳过、关闭 | 两者都有跳过/关闭相关代码；实际节点 ID、可点击性、时序待实机确认后才可写无障碍规则 |
| 菜鸟内置关闭摇一摇 | 静态确认入口与清理预加载动作；实际设置页及效果待确认 |

原有扫描器的结果原样保存在 `*-domains.tsv`，但不能直接用作规则：扫描器尚不认识 CMG，将 `cmgadx.com` 主机标为“第一方/不能拦（未判定）”；它还把部分 `mtop.*` 方法名误识别成疑似域名。本报告以上述调用证据纠正解释，未修改扫描器或自动导入结果。

## 范围和复现

完成：ADB 连接与包版本确认、读取两个已安装 base APK、扫描所有根目录 DEX 的 string_ids、针对关键类反汇编、检查广告相关资源及 Manifest 组件、保存定位证据。未进行重新签名、修改应用、root、HTTPS 中间人解密或广告规则部署。未执行 DI / AC 验收，也未清缓存或强停/启动应用；没有实时请求、IP 或广告出现频率的测量结果。

项目调研工作不涉及生产实现；复用了扫描器并先跑 `python3 scripts/apk-ad-scan/tests/run_tests.py`，全部自测通过且无跳过。反汇编使用现有 build-tools/36.1.0/dexdump，没有新增依赖版本。字节码的 DEX modified UTF-8 以替换方式处理解码，类名及本报告引用的英文指令和 URL 不受影响。

可重新从 `adb shell pm path <package>` 的当前路径 pull 到本地；不要照抄旧安装路径。APK SHA-256 应先与上表核对。对已取得的 APK：

```sh
python3 scripts/apk-ad-scan/scan.py /path/to/base.apk
python3 docs/device-survey/yangshipin-cainiao/extract-bytecode.py yangshipin /path/to/yangshipin.apk /path/to/dexdump /path/to/output.txt
python3 docs/device-survey/yangshipin-cainiao/extract-bytecode.py cainiao /path/to/cainiao.apk /path/to/dexdump /path/to/output.txt
```

原 APK 及完整 Manifest 是本次临时输入，收尾删除；保留 SHA-256、文本证据、研究报告与可复用提取脚本。URL 中的 key/secret 已脱敏。后续实际拦截效果验证须保留直播、点播、快递列表、物流详情和扫码等正常功能作为对照。
