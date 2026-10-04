# 开屏免广告入口：可行性补充

日期：2026-10-04；样本与 README.md 的两份 APK SHA-256 一致，本轮重新提取并核对。仅静态分析，没有执行启动实验、安装快捷方式、改变组件或应用设置。

## 判断

存在值得验证的入口候选，但还没有一个经过实机对照证明有效的免开屏方案。用户所说的“自动调用无广告接口”，可以具体化为“由桌面快捷方式调用应用允许外部访问的入口，绕过开屏流程或传入应用已有的免开屏参数”。请求广告服务器的另一条 URL，本身不能控制应用是否展示缓存广告。

百度网盘的项目报告 `docs/device-survey/netdisk-splash-bypass.md` 并未证明成功：它发现 `filterad=1`，但并行启动会丢弃外部参数，冷启动无商业广告对照，热启动参数测试仍进入 SplashAdActivity。不能把它作为已验证方案类推到其他应用。

## 菜鸟：直接进入首页是最明确的候选

Manifest 明确将 `HomePageActivity` 设置为 `exported=true`，有 `guoguo://go/home_page` 的 VIEW 路由；正常桌面入口为另一个导出的 `WelcomeActivity`。

`WelcomeActivity.onCreateImpl()` / `requestMamaAndRtbSplash()` 有明确广告请求调用。直接指定 `HomePageActivity` 有机会绕过这段 Welcome 流程。候选显式入口：

```text
package = com.cainiao.wireless
component = com.cainiao.wireless.homepage.view.activity.HomePageActivity
```

这是合法可调用的组件候选，不等于免广告已验证。HomePageActivity 自身的 onCreate/onResume、其父类、Application 和生命周期回调仍可能执行热启动或首页广告流程。首页 onResume 也调用 AdxTopViewManager，因此不能宣称进入首页后没有广告。此次没有发现可据此认定生效的通用 `skip_ad=1` 参数。

依据：`evidence/cainiao-entry-manifest.txt`，`evidence/cainiao-entry-methods.txt`。特别注意 `isHotLaunch` 是冷热启动标记；onCreateImpl 的 true 分支仍调用广告请求，不能将它当成免广告开关。

## 央视频：内部确有免开屏分支，但外部可达性未证实

HomeActivity 的 `parseIntent()` 读取 String extra `from`；值以 `push_` 或 `third_` 开头时设置 `needHideADSplash=true`。`switch_tab` 等分支也会设置此字段。

`initSplash()` 读取该字段：false 时创建 splashManager 并开始开屏；true 时隐藏开屏容器并调用首页回调。这是实际控制流，说明有免开屏路径，而不是只找到一个类似命名的变量。

不过 Manifest 的 HomeActivity 未声明 exported、也没有 intent-filter，按该声明其默认不可从外部直接启动。导出的路由组件是 `com.tencent.videolite.android.component.literoute.OpenActivity`，接收 `cctvvideo` scheme。现有字节码包含 `cctvvideo://cctv.com/HomeActivity` 路由字符串，但未追踪 OpenActivity 的完整参数转换和转发链路；不能断言在 URI 中添加 `from=third_...` 就会成为 HomeActivity 的 String extra，也未验证是否冷启动可达。

正常 SplashActivity 只创建新的 HomeActivity Intent，并写 `ImUrFather=true`；所读方法未转发任意外部 extra。因此给 SplashActivity 随便附上 `from` 参数，不是已经成立的绕过方案。

依据：`evidence/yangshipin-entry-manifest.txt`，`evidence/yangshipin-entry-methods.txt`，原 `evidence/yangshipin-bytecode.txt` 的 SplashActivity.onCreate。

## 自动化方式和边界

若候选入口通过验证，可以在哨兵中生成由用户点击的桌面快捷方式，让它直接调用候选 Intent。原应用图标仍按原入口启动；普通应用无法在用户点击另一个应用图标的同时，可靠地改写那个启动 Intent。无障碍看到窗口后再次启动其他页面存在时序竞争，不能承诺广告从不出现。

“点应用图标”和“从后台回前台”需要分别验证，解锁手机本身也不是同一个触发事件。推荐优先验证菜鸟 HomePageActivity，央视频先继续追踪路由参数。对照必须先确认正常入口能显示商业开屏广告，再比较候选入口，同时观察是否进入正确首页、是否崩溃或回到 Welcome，以及直播/快递功能是否正常。本轮未执行这些设备实验，不部署候选入口。
