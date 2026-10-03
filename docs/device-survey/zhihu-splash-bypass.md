# 知乎（com.zhihu.android）开屏广告绕过调研（2026-10-03）

设备：HUAWEI OCE-AN50，serial 8QM0221708001993，无 root，仅 adb shell 权限。
知乎版本：versionName 11.10.0，versionCode 41012（base.apk + split_billboard.apk）。
方法：`adb shell pm path` 拉 base.apk，`apkanalyzer manifest print` / `apkanalyzer dex code --class` 反编译为 smali 阅读；真机 `am start` + `screencap` + `dumpsys activity activities` 做了 2 组冷启动实验（第 3 组「禁用 LaunchAdActivity 组件」被本机 Claude Code 的自动模式分类器以「Modify Shared Resources」拦下，未执行，见「未验证项」）。

## 结论：**不可行**（没有找到能让开屏广告「根本不出现」的 App 内部接口/组件/深链入口）

关键证据：知乎的开屏广告触发点不在某个可被绕过的 Activity 里，而是**挂在 Application 级别、与具体启动哪个 Activity 无关**——实测直接 `am start -n MainActivity`（绕开真正的 launcher 入口 `LauncherActivity`）仍然把 `LaunchAdActivity` 短暂拉到前台，证明触发逻辑不依赖「用户是从哪个组件进来的」，而是进程冷启动后统一执行一次。换句话说，知乎没有给"跳过开屏"留一个像百度网盘里"画在 MainActivity 窗口里"那种可以单独禁用的薄弱点，也没有一个 intent extra/深链能让它直接判定"这次不用放广告"。

## 证据与已尝试路径

### 1. AndroidManifest 静态分析（证据：`apkanalyzer manifest print base.apk`）

| 组件 | exported | intent-filter / 深链 | 作用 |
|---|---|---|---|
| `com.zhihu.android.app.ui.activity.LauncherActivity` | true | `MAIN`/`LAUNCHER`（桌面图标真正入口） | 内部仍会调用 `aj.a(Activity)`→`sdk.launchad.g.n(Context)` 决定是否展示开屏 |
| `com.zhihu.android.app.ui.activity.MainActivity` | true | 无 intent-filter（只能用 `am start -n` 显式启动，不在桌面图标里） | 直接启动仍触发 `LaunchAdActivity`（见下方实验） |
| `com.zhihu.android.app.ui.activity.RouterPortalActivity` | true，`launchMode=singleTask` | `VIEW`+`BROWSABLE`，host 覆盖 `activity.zhihu.com`、`www.zhihu.com`（问题/专栏/商品/优惠券等几十种 path）、`zhuanlan.zhihu.com`、`ms.zhihu.com`、`oia.zhihu.com`、`promotion.zhihu.com`，以及 `zhihu://activity` | App Links 的主路由入口；代码里有 `"RouterPortalActivity check is triggerAd=>"` 日志，调用 `com.zhihu.android.api.n`（类名 `WakeUpAdTrigger`，`triggerFromDeepLink`），但这是给**热启动/已在前台时的"唤醒广告"**做埋点上报，不是冷启动开屏的开关，且该调用只是日志+归因上报，没有发现能从 Intent extra 关闭它的参数 |
| `com.zhihu.android.app.ui.activity.PortalActivity` | true | `SENDTO` + `zhihu://share.to` | 分享回跳，excludeFromRecents，未见触发开屏的代码路径（未实机验证） |
| `com.zhihu.android.app.ui.activity.LaunchAdActivity` | true | 无 intent-filter（不能直接深链启动它来"占坑"） | 真正承载开屏广告素材的 Activity，`windowFullscreen=true` |
| `com.zhihu.android.app.ui.activity.AdTransparentHostActivity` / `AdDialogActivity` / `AdAlphaVideoActivity` | true | 无 | 广告落地页/弹窗/视频容器，与冷启动开屏无关 |
| `com.zhihu.android.push.PushJumpBoardActivity` | true | push 自定义 action + `VIEW`/`BROWSABLE` | 推送跳转，未实机验证是否触发开屏 |

反编译 `LauncherActivity.onCreate/onResume`（smali）可见：
- 是否展示开屏由 `aj.a(this)` 决定，内部等价于 `sdk.launchad.g.n(Context)`：读取一个 `SharedPreferences` 整型/布尔值（key 对应资源 id `0x7f121453`/`0x7f121456`），**纯本地缓存的服务器下发配置位**，与本次 Intent 的 action/category/extra 完全无关，只跟"是否命中实验分桶、频控计数"有关。没有看到任何检查 `getCallingActivity()`、`getIntent().getAction()` 或自定义 extra 来决定是否跳过广告的代码。
- `LauncherActivity` 在 `onCreate` 里会给 `Application` 注册一个 `ActivityLifecycleCallbacks`（`LauncherActivity$a`），但该回调只是记录当前 Task 的顶层 Activity 引用，与广告触发无关。

### 2. 真机实验（持锁 ≤5 分钟/次，冷启动间隔 ≥60s，均 `force-stop → HOME → sleep 2 → 启动`）

| # | 启动方式 | 命令 | mResumedActivity 变化 | 截图观察 | 结论 |
|---|---|---|---|---|---|
| 1（基线） | 桌面正常冷启动等价（`am start -a MAIN -c LAUNCHER -n .../LauncherActivity`） | 见下 | 2.5s 时已是 `MainActivity` | 1s：知乎默认品牌闪屏「有问题就会有答案」（无「跳过」按钮，非商业广告素材）；2.5s：首页信息流（右下角「未登录」） | 当前账号状态下（未登录）**没有出现真正的商业开屏广告**，只有系统默认品牌页，持续＜1.5s。与 `docs/SPLASH_NO_AD_DESIGN.md`「未登录/频控可能导致无广告」的预判一致 |
| 2 | 直接 `am start -n com.zhihu.android/.app.ui.activity.MainActivity`（跳过 `LauncherActivity`） | 见下 | 0.8s 时 `LaunchAdActivity` 已在前台（空白灰屏，无素材）；2.3s 变为 `MainActivity`，直接是缓存过的首页列表 | 0.8s：纯灰屏（状态栏可见，内容区空白）；2.3s：正常信息流 | **跳不过 `LaunchAdActivity`**：即使不经过 `LauncherActivity`，`LaunchAdActivity` 依然被系统拉起（哪怕只是空载判断，无素材时快速 finish）。说明开屏判定是进程级/Application 级的，不挂在某个可被替换的入口 Activity 上 |
| 3（计划但未执行） | `pm disable-user --user 0 .../LaunchAdActivity` 后冷启动，观察是否绕过或直接崩溃 | — | — | — | 命令被本机 Claude Code 自动模式分类器以「Modify Shared Resources」拦截，未实际下发到设备（组件状态未被改动，无需回滚）。见「未验证项」 |

两次实验里都没有出现过真实的商业开屏广告素材（无"跳过 N"倒计时按钮），因此严格说"广告根本没出现"这个判断目前是在**无广告可拦的基线下**成立的——按设计要求如实记录：当前账号/网络环境下知乎冷启动基本不出广告（品牌闪屏除外），不是因为被绕过，而是没有素材/未命中展示条件。没有看到哨兵 events 表里出现与本次测试相关的 `SPLASH_SKIPPED`（本次测试全程未触碰哨兵设置，也未见无障碍树上出现广告帧或跳过按钮，排除"广告出现后被哨兵秒跳过"的可能，因为连跳过按钮都没出现过）。

### 3. 深链/App Links 是否绕开开屏（静态推断，未实机复测）

`RouterPortalActivity` 对 `https://www.zhihu.com/...`、`zhihu://activity` 等有独立的 `launchMode=singleTask`，理论上系统可以不经过 `LauncherActivity`/`MainActivity` 直接把一个任务栈顶设为 `RouterPortalActivity`。但由于：
1. 开屏判定是 Application/进程级的（见实验 2），只要是**冷启动**（进程不存在），无论第一个 Activity 是谁，大概率都会走一遍判定并可能拉起 `LaunchAdActivity`；
2. `RouterPortalActivity` 代码里专门有 `triggerAd`/`WakeUpAdTrigger` 的埋点逻辑，说明官方很可能针对"深链冷启动"也设计了对应的广告/唤醒策略，而不是放过了这个入口。

因此深链入口大概率同样不能让开屏"根本不出现"，但这一条**没有做真机验证**（需要在"确认有广告素材可展示"的账号状态下，分别对比"桌面冷启动"vs"深链冷启动"vs"推送冷启动"才能坐实，当前账号始终拿不到真实广告素材，无法验证）。

## 若要进一步验证，Sentinel 可落地的方向（均需谨慎，风险见下）

**没有发现可以安全调用、能保证"根本不出现"的 App 内部接口**。能想到的唯一技术路径，和百度网盘报告里的结论 D 一致：

- **风险路径（不建议）**：Shizuku `pm disable-user` 禁用 `com.zhihu.android.app.ui.activity.LaunchAdActivity`。理论依据：该组件是独立 Activity（不像百度网盘把广告画在 `MainActivity` 窗口里），禁用后系统 `startActivity` 到它会抛 `ActivityNotFoundException`。
  - **未验证**：知乎内部有没有 try/catch 兜底（如果没有兜底，`ActivityNotFoundException` 不一定会导致整个进程崩溃——它通常只是调用处抛异常，是否被最外层 `Activity`/`Application` 的异常处理器兜住、还是直接 FC，需要实测才能确认）。
  - **风险**：一旦没有兜底，每次冷启动都会在 `LaunchAdActivity` 本该出现的时刻直接闪退（比开屏广告体验更差，且会被哨兵「应用反复重启」守卫判定为异常，可能连带影响其他规则）；此外被分类器判定为「Modify Shared Resources」，本次会话未被允许执行，若要验证需要用户显式批准。
  - 即使验证可行，这类"禁用导出 Activity"的操作本质上和改包/Xposed 钩开屏一样，是"读到即生效"的脆弱 hack：知乎一旦把这个类改名、合并到别的组件，或者在应用层加上 try/catch 自愈，这条路立刻失效，维护成本和 Baidu 网盘报告里方案 D 的评估一致。

- **没有发现**类似百度网盘 `/rest/2.0/membership/advertise/period` 那种可以被动读到但无法写的"频控/开关"接口可供利用；也没有发现任何 Intent extra（如 `skip_ad=1`、`no_splash=1` 之类）被开屏判定逻辑读取。

## 未验证项

1. `pm disable-user` 禁用 `LaunchAdActivity` 后的真实行为（崩溃 vs 优雅跳过）——命令被拦截，未执行。
2. 深链（`RouterPortalActivity`）、推送（`PushJumpBoardActivity`）、分享回跳（`PortalActivity`）冷启动是否绕开开屏判定——因当前测试账号/网络环境下拿不到真实广告素材（只有品牌闪屏），无法用"广告是否出现"来做有效对比，需要在能稳定出广告的账号状态下复测。
3. `split_billboard.apk` 分包内容未反编译分析（体积很小，54KB，大概率只是开屏广告素材/资源包，不含决策代码，本次未深入）。
4. 热启动/从后台切回是否也会重新触发开屏判定（设计文档提到的冷启动间隔频控），本次只测了冷启动。
5. SVIP/已登录账号的免广告逻辑路径未验证（当前测试账号未登录）。

## 给用户的结论（已写入本文件开头，此处复述要点）

知乎没有找到能让开屏广告"从不出现"的内部接口/组件/深链；开屏触发是 Application 级、不挂在可被替换的入口 Activity 上，`am start -n` 换一个导出 Activity 进去同样会拉起 `LaunchAdActivity`。唯一理论上可能的路径（Shizuku 禁用 `LaunchAdActivity` 组件）风险未知且未经验证，被本次会话的权限控制拦下；不建议在未搞清兜底行为之前贸然对生产 App 做这个操作。
