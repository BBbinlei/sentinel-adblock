# 华为 OCE-AN50 试装记录（2026-10-02，调试包）

设备：HUAWEI OCE-AN50，HarmonyOS 4.2 / EMUI 14.2（Android 12，SDK 31）。目标机仍是 OPPO Find X7 Ultra，本次只做临时试装。
范围：网络拦截、界面处理、通知过滤三个引擎和界面；不验证系统净化（操作项未在华为上核实，不会执行）。

## 结论
三个引擎都能起来，没有崩溃，开着拦截时上网正常。百度网盘开屏广告在「从桌面启动」时被自动跳过（`SPLASH_SKIPPED / builtin:splash-skip`）。

## 验证到的
| 项 | 结果 |
|---|---|
| 崩溃 | 无 FATAL |
| VPN | `:vpn` 进程在跑，系统 VPN 已连接；ping 223.5.5.5 / 百度 0% 丢包 |
| 无障碍 / 通知使用权 | 已启用，首页三个引擎均「运行中」 |
| 开屏跳过 | 从桌面启动百度网盘 2 次，均在「跳过 4/5」时被跳过，events 表新增 2 条 `SPLASH_SKIPPED` |
| 网络层 | 记录到 `DNS_BLOCKED`（如 `pglstatp-toutiao.com`、`dns.jd.com`）和 `HTTPDNS_REJECTED` |

## 华为上的问题
1. **向导里的 VPN 授权弹窗一闪就关（高，哨兵自己的 bug，已修未装机复测）**：向导的 `SetupChecker.settingsIntent` 给所有跳转统一加了 `FLAG_ACTIVITY_NEW_TASK`，并用普通 `startActivity` 打开，系统的 `ConfirmDialog` 拿不到调用方，创建约 80ms 就自己 finish。不是华为的限制。
   对比：首页开关用带结果的方式启动，同一台手机上弹窗正常出现，点「确定」后 `ACTIVATE_VPN` 变 `allow`、VPN 连接成功、ping 正常。
   此前试装时看到的 `ACTIVATE_VPN: ignore` 只是弹窗被关时留下的拒绝记录；曾用 adb 临时放开，之后已改回默认并由用户在弹窗里点确定授权。
   修复：VPN 这一步改用 `rememberLauncherForActivityResult` 启动，且 VPN 授权 Intent 不再加 `NEW_TASK`（`OnboardingScreen.kt`、`SetupChecker.kt`）。`:app:testDebugUnitTest` 通过；向导路径尚需在新装机或清数据后真机复测。
   补充：向导修好后用户在手机上确认弹窗能留住、点「确定」后进入下一步（授权成功，`ACTIVATE_VPN=allow`）。但向导第 2 步标题是「开启网络拦截」，授权通过后并没有真的启动 VPN，向导走完首页仍是「未开启」，是哨兵自己的问题（与华为无关）。已改：授权返回成功后打开总开关并启动 VPN，逻辑与首页开关一致（`OnboardingScreen.kt`）；待真机复测。
2. **电池优化一步判断不准（中）**：向导用 `PowerManager.isIgnoringBatteryOptimizations`，只认 Android 原生白名单；华为「电池优化 / 应用启动管理」是另一套，写不进原生白名单，所以华为里设置了，应用仍显示未设置，首页一直提示「还有 2 步没完成」。建议这一步改为「我已设置」手动确认。
3. **Shizuku 未激活**：预期内，系统净化「能力下降」。

## 测试方法的坑（后续真机测试注意）
- 开屏跳过规则只在「从桌面启动后 5 秒内」生效（`A11yBrain.LAUNCH_WINDOW_MS`、`launch.fromLauncher`）。用 `monkey`/`am start` 直接冷启动、而上一个前台是 App 自己时，不算桌面启动，规则不触发，会误判成「没拦截」。
  正确做法：先 `adb shell input keyevent KEYCODE_HOME`，等 2 秒，再启动目标 App。
- 不要在测试时跑 `uiautomator dump`：它会临时压制其他无障碍服务，干扰哨兵。
- 设计上的限制：从通知、深链、其他 App 跳转进入的启动，不算「桌面启动」，开屏规则不会触发。是否接受要另行决定。

## 未验证
- 后台存活（华为是否会清理 `:vpn` / 无障碍进程）。
- 拦截准确性（有没有误杀）、弹窗类规则（如百度网盘「开启推送提醒」弹窗未被处理，应属规则覆盖问题）。
- 通知过滤的实际效果。

## 开屏广告「肉眼不可见」（2026-10-02 下午，百度网盘）
### 现象与根因（修复在分支 chan/a11y-curtain，工作目录 ~/wt-curtain）
- 广告在启动后约 4～6 秒才出现（品牌页是系统启动窗口，App 自己的第一个窗口一出来就是开屏广告），而开屏规则原来只认启动后 5 秒内。
- 广告页一直在播动画，无障碍内容变化事件不停，原来的 100ms 去抖永远等不到安静，整段广告期间几乎不扫描 → 改为限频。
- 点桌面图标后 systemui 窗口事件会冲掉「来源是桌面」→ 点桌面图标时打带时间戳的标记，15 秒内第一次前台切换算桌面启动。
- 新增启动遮罩（`LaunchCurtain`）：点桌面图标的瞬间盖纯色启动页，广告被跳过后撤；只对「曾被跳过过开屏」的 App 生效；硬超时 10 秒。
- 实测一次：遮罩在点图标时盖上，到 5.6 秒的所有截图无广告帧，约 6 秒广告被跳过后遮罩撤掉。**重复验证未完成**（后两次测试因桌面翻页错位作废）。
### 源码侧发现（反编译 base.apk，Android SDK 自带 apkanalyzer/aapt2）
- 开屏页 `com.baidu.netdisk.advertise.ui.SplashAdActivity` → `SplashAdsLoadStrategy.loadAd`；多家联盟竞价（csj/gdt/bdmob/ubix/vlion/oct/ms/xz/biz/mars…），开关、频次、冷启动间隔由服务器下发（`/rest/2.0/membership/advertise/period`），SVIP 有免广告逻辑。
- 素材每次冷启动都会重新下载（外部缓存目录里的哈希文件与启动时间对应）。
- VPN 记录到的网盘广告域名只有广点通和穿山甲（共 11 个，已被拦截），但开屏广告仍然出现；冷启动时网盘几乎不走系统 DNS（两次采样都只有 0～1 条查询），疑似自带网络栈（files/TurboNet）绕开系统 DNS。
- 结论：对网盘做「按域名拦开屏广告」基本走不通，主路径仍是遮罩 + 跳过。
### 副作用与隐患
- 我反复冷启动网盘，触发守卫「应用反复重启 / 重试风暴」自动停用了网盘 4 条广告域名规则（已手动恢复）。真实用户一分钟内连开同一 App 三次也会触发，需单独评估这条策略。
- 每次重装调试包都会杀掉 VPN，且偶尔让无障碍服务被标成已崩溃，需要手动重新开启。
- 手机上当前装的调试包仍带一条临时 DNS 域名日志（tag SentinelDnsDebug，仅 adb 可见），源码里已删除，下次重装即消失。
