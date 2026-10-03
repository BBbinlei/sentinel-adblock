# 百度网盘开屏「绕过」可行性（内部入口 / 组件 / 配置）

日期：2026-10-03　设备：HUAWEI OCE-AN50（HarmonyOS 4.2，无 root，shell 身份）　目标：com.baidu.netdisk 13.34.3（versionCode 4228，targetSdk 31）
方法：adb pull base.apk + apkanalyzer / aapt2 静态分析（smali 级），加上 adb 只读实验。未登录、未改系统设置、未装卸 App、未改仓库源码。

## 1. 结论：**不可行（无可验证的绕过），冷启动部分「理论上有官方后门但大概率到不了」，热启动完全无解**

| 场景 | 结论 |
|---|---|
| 冷启动 | 找到 App 自带的「免开屏」开关 `filterad=1`（Intent String extra）和若干深链条件，但它们读的是**开屏逻辑所在 Activity 的 Intent**；实测冷启动时 Navigate 立刻用一个只带 `is_from_login` 的新 Intent 拉起 MainActivity，extra 被丢弃，所以从外部给 Navigate 加 extra 大概率无效（静态推断，未能实证，原因见下）。 |
| 热启动（后台切回） | 由进程生命周期触发 `SplashLifecycleManager.backgroundResumeAdStart` 直接 `startActivity(SplashAdActivity)`，与入口 Intent 无关。实测 `filterad` 无效（4/4 次仍出现 SplashAdActivity）。 |
| 直接 `am start` 非导出组件 | **本机不可行**：shell(uid 2000) 启动非导出的 MainActivity 被拒（`Permission Denial ... not exported from uid 10398`）。HarmonyOS 上不能假设「shell 能启动非导出 Activity」。 |
| 改 SharedPreferences / 配置 | 不可行（无 root，run-as 对非 debuggable 包不可用；外部存储里只有缓存和 ASR 日志，没有开关）。 |

**重要的测试局限**：今天（10-03 19:52–20:30）对网盘做了 12 次冷启动（monkey / am start 各种入口），**一次开屏广告都没有出现**：无 SplashAdActivity、截图 2/5/8 秒均为品牌启动图→首页、外部缓存目录没有新增广告素材文件（最后一次素材下载是 16:26）。所以没有「有广告」的对照组，任何绕过手段的「广告没出现」都**不能**归功于该手段。冷启动无广告的原因未知（服务器频控/账号状态/无填充均有可能，Sentinel VPN 当时未开，`dumpsys connectivity` 只有 Wi-Fi 且 NOT_VPN；Sentinel 无障碍开着，但 shell 启动不算「桌面启动」，不会触发其跳过规则，events 表无法读取，故这条只能由截图/缓存文件判断）。

## 2. 证据

### 2.1 Manifest（aapt2 dump xmltree，已解析为 /scratchpad/netdisk/acts.txt）
- 启动入口 `com.baidu.netdisk.ui.Navigate`（exported，MAIN/DEFAULT + 三个 wap/samsung action）；桌面图标是 activity-alias `DefaultMainActivity`/`OPLauncherActivity`/`SvipLauncherActivity`，都 `targetActivity=Navigate`。当前启用的是 `DefaultMainActivity`。
- `com.baidu.netdisk.ui.MainActivity`、`com.baidu.netdisk.advertise.ui.SplashAdActivity`：**exported=false**。
- 1386 个 Activity 中 116 个导出。与开屏相关的：
  - `WapLauncherActivity`：VIEW 深链 scheme `baiduyun / yundownload / yunacceptinvite / bdnetdiskwap / bdnetdisk`，以及 `http(s)://snsyun.baidu.com`；内部把 Intent（含 data）转给 Navigate，并加 `extra_is_cold_start`。
  - `EnterPcOpenFileActivity`（VIEW file/content + 各 MIME）：**App 自己的免广告入口**，代码里 `putExtra("filterad","1")` + `IS_PC_OPEN_FILE=true` 后 `setClass(Navigate)` 启动。需要登录、存储权限，语义是「在电脑上打开文件」，不是回首页。
  - 其余 `Enter*Activity`、`NetdiskOpen*Activity`：文件打开/分享类入口，均转 Navigate。
  - `advertise.incentive.IncentivePointActivity` 导出但是激励视频页，与开屏无关。

### 2.2 开屏判定代码（apkanalyzer dex code）
`SplashManager.isShowSplash(Activity)` 依次判定，任一命中就关闭开屏并返回 false：
1. 青少年模式；2. **未登录**（`ColdAdCloseType.NOT_LOGIN`）；3. 企业空间（`space_type`）；
4. **Intent 的 String extra `filterad` == "1"**（`PUSH_CLOSE_AD`，记录 `url` 或 `push_type`）；
5. 当日次数上限 `limitCountAdShow`（服务器 `limitAdShowCount`，SVIP 另有 `svipLimitAdShowCount`；0=不限，<0=禁）；
6. 冷启动间隔 `limitColdDurationAdShow`（服务器 `coldDuration`，`PersonalConfig.ad_splash_show_daily_number` 记录上次时间）；
7. 画中画（`isAdPipForbidden`）；
8. `isAdvertFilter`：启动 URI（`Intent.data`，或 extra `com.baidu.netdisk.launch.deeplink`，或 router action 字符串）里 `logargs` 命中服务器下发的 `scheme_launch` 列表；**`sc=honor` 或 `sc=xiaomi_anywhere_door`**；**https/http + host `snsyun.baidu.com` + 非空 path（App Link）**；
9. `ReturnToYun`（新安装/回流场景，服务端实验）。

调用点有两处，都用「所在 Activity 的 `getIntent()`」：
- `Navigate`：`SplashManager.isShowSplash(this)`，通过则 `startShowColdSplashAd`；否则 `dispatch()` 进主页。
- `MainActivity`（并行加载模式，`PersonalConfig key_main_open_ad_parallel`，服务器下发）：`setupSplashAdView → SplashLifeHolderContainer.loadAD → isShowSplash(MainActivity)`。MainActivity 的 Intent 来自 `Navigate.enterMainActivity`：`new Intent(app, MainActivity.class).putExtra("is_from_login", z)`，**不转发 Navigate 的 extra 和 data**。MainActivity 自己只有 `putAdFilterToIntent()`：当 `MemoryConfig["filterad"]=="1"`（进程内存配置，外部写不到）时才把 filterad 写进自己的 Intent。
- 实测冷启动时 `Navigate` 在 1 秒内就让位给 `MainActivity`，与并行模式一致，所以外部给 Navigate 的 `filterad` 到不了 MainActivity 的判定。**但并行开关是服务器下发的，别的账号/时段可能走 Navigate 分支，此时 `filterad` 有效。**

热启动：`SplashLifecycleManager.handleLifeCycle` 在进程回前台时算后台停留时间，超过 `AdvertiseHotStartManager` 的 `minTime`（兜底 `switchTime`，服务器）就 `new Intent(topActivity, SplashAdActivity)` 启动，附 `key_extra_last_hot_time`。入口 Intent 不参与。

### 2.3 实验（均在持 phone.lock 下；冷启动间隔 ≥ 60 s；序列 force-stop → HOME → 2 s → 启动）

| 编号 | 命令（缩写） | 次数 | 观察 | 说明 |
|---|---|---|---|---|
| base1-4、c1-c7 | `monkey -p … -c LAUNCHER 1` / `am start -a MAIN -c LAUNCHER -n …/.ui.DefaultMainActivity` / `…/.ui.Navigate -f 0x10200000`（对照） | 11 | resumed 全程 `MainActivity`；2 秒为品牌启动窗口，5/8 秒为首页，无广告帧；缓存目录无新素材 | **对照组本身就没有开屏广告** |
| a1、a2、e1a | `am start -n …/.ui.Navigate --es filterad 1` | 3 | 同上，进入 MainActivity，无广告 | 与对照无差别，不可判定 |
| b1、b2 | `am start -n …/.ui.MainActivity --es filterad 1` | 2 | **启动失败**，停在桌面。logcat：`Permission Denial: starting Intent {cmp=com.baidu.netdisk/.ui.MainActivity} from null (pid=2124, uid=2000) not exported from uid 10398` | shell 不能启动非导出 Activity（本机） |
| d1 | `am start -a VIEW -d https://snsyun.baidu.com/abc/def -n …/.ui.WapLauncherActivity` | 1 | 直接落到 `ui.cloudp2p.RichMediaActivity`（分享/富媒体页），未经首页 | 深链进入功能页，没有开屏；但无对照，且不是回首页 |
| e1 | `am start -a VIEW -d "bdnetdisk://app/home?sc=honor" -n …/.ui.WapLauncherActivity` | 1 | Navigate 一闪后回到桌面（该 URI 无有效路由） | 无结论 |
| hot1-hot2 | 冷启动 → 8 s → HOME → 35 s → `monkey` 回前台 | 2 | **SplashAdActivity** 为 resumed，约 3 秒（屏上是品牌图「美好由我 全盘掌握」，无广告素材，说明无填充）后进 MainActivity | 热启动开屏页确实出现，且该机热启动间隔 ≤ 35 s |
| plain1/2、filt1/2 | 同上，但回前台用 `am start -n …/.ui.Navigate`（plain）与 `… --es filterad 1`（filt） | 各 2 | 4/4 均出现 `SplashAdActivity`（持续 ≥ 5 次采样） | `filterad` 对热启动**无效** |
| 其他 | `adb shell cat files/config.ini`、列外部目录 | — | 只有 `peer_id`；cache 下 `skin` 目录和哈希命名素材（最近 16:26）；`files/asr_log/` 每次进程启动一个语音 SDK 日志，无开屏信息 | 外部存储里没有可改的开关 |

截图保存在 /private/tmp/claude-501/-Users-binlei-------/592dffe2-d2d7-414c-a38a-c50b730cff96/scratchpad/netdisk/（base*_N.png、c*_N.png、hot*_2.png、mc.png、mh.png）。

「没出现」与「被哨兵跳过」的区分：本报告所有启动都由 shell 发起，不是桌面启动，哨兵开屏规则的 `launch.fromLauncher` 条件不成立，不会点跳过；热启动页上没有「跳过」按钮可点；且缓存目录无新素材。因此上表中的「无广告」是**广告根本没加载**，不是被哨兵跳过。哨兵状态：无障碍服务运行、VPN 未连接、Shizuku 未触碰，全程未改哨兵设置。

## 3. 已尝试路径汇总

| 路径 | 结果 |
|---|---|
| shell 直接启动 MainActivity / SplashAdActivity（非导出） | 失败：本机拒绝（Permission Denial） |
| 导出入口 Navigate + `filterad=1` | 能启动；冷启动判定读不到（静态）；实验无对照，**无法证实**；热启动无效（实证） |
| App Link `https://snsyun.baidu.com/<path>`（`isAppLinkUri`） | 静态上会关闭开屏；实测进入 RichMediaActivity，不回首页；对照缺失 |
| `sc=honor` / `sc=xiaomi_anywhere_door` 深链参数 | 静态上会关闭开屏；`bdnetdisk://app/home` 没有有效路由，未能验证 |
| `EnterPcOpenFileActivity`（官方 `filterad=1` 入口） | 未执行：会走「在电脑上打开」上传流程，需要登录与文件 URI，副作用大 |
| 热启动开屏（SplashAdActivity） | 无外部开关；进入后约 3 秒自行退出（本次为无广告的品牌页） |
| 改 prefs / 配置 | 不可行 |

## 4. 若要在 Sentinel 里落地：目前没有值得做的「绕过」方案；可选的低风险探索

1. **不建议**把「绕过」写进 Sentinel 的产品承诺：冷启动入口 extra 被 MainActivity 丢弃（并行模式），热启动与入口无关，两者的开关都由服务器控制。
2. 唯一值得再验证的是**冷启动 + `filterad=1`**，前提是等到有广告的时段拿到对照：
   - 做法：Sentinel 通过 `ShortcutManager.requestPinShortcut` 生成一个桌面快捷方式，Intent 为 `ComponentName(com.baidu.netdisk, com.baidu.netdisk.ui.Navigate)` + `putExtra("filterad","1")`（Navigate 是导出的，普通 App 即可调用，不需要 Shizuku；Shizuku 版等价命令 `am start -n com.baidu.netdisk/.ui.Navigate --es filterad 1`）。用户点这个图标而不是网盘原图标。
   - 时机：只能在用户主动点击时；Sentinel 无法在点击原图标之后、广告出现之前介入（无障碍事件晚于 Navigate 创建，强停重启会更明显地闪屏，且会触发哨兵自己的「应用反复重启」守卫）。
   - 副作用/风险：不影响登录态；`filterad=1` 在 App 里的语义是「推送/PC 打开」，会记一次 `PUSH_CLOSE_AD` 统计；如果服务器走并行模式则完全无效；网盘更新后该 extra 名称可能变。
3. 热启动：没有可调用的入口。可做的只是维持现有「看到再跳过」；该机热启动页约 3 秒，且在无填充时是品牌图而不是广告。

## 5. 未验证项
- **没有对照组**：今天冷启动没有任何开屏广告，所以 `filterad` / App Link / `sc=` 对冷启动开屏是否有效均未实证。需在服务器再下发广告的时段（或次日、账号状态变化后）重做：每种入口 ≥ 3 次，间隔 ≥ 60 s，并用外部缓存目录是否新增素材文件作为「广告被加载」的判据。
- 并行开关 `key_main_open_ad_parallel` 与热启动 `minTime` 的当前取值（在应用私有 prefs，读不到）。
- 真实桌面图标点击路径：桌面上网盘图标在「精选应用」服务卡里，我第一次点击打开了卡片管理页（已按 HOME 退出，无副作用），之后只用 `am start` / `monkey` 模拟。
- `bdnetdisk://` 深链能回首页的有效路由（需看 router 表）；`EnterPcOpenFileActivity` 实际行为。
- 是否有 SVIP / 登录态以外的账号状态导致今天没有冷启动广告。
- HarmonyOS 4.2 上 shell 不能启动非导出 Activity，原 OPPO 目标机（ColorOS）可能不同，未测。
