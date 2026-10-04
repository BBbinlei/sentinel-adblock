# 启动入口集成验证

基线 app 37/37。Task 2 app 57/57；全项目 `./gradlew test`：app 57、core-rules 29、data 46、a11y 76、notify 14、system 43、vpn 58、guard 23、rule-regression 14，共 360，失败/错误/跳过 0。

新增覆盖精确版本与组件状态、未知 ID 拒绝、失效/启动异常回退、未验证入口拒绝、桌面不支持与固定快捷方式数据、请求/确认/未确认反馈、详情页三种状态/创建按钮，以及央视频真实 XML 弹窗关闭节点匹配。仅使用既有依赖。

实机冷/热对照详见 docs/device-survey/yangshipin-cainiao/launch-entry-validation.md：菜鸟冷启动通过，央视频候选有商业首页弹窗，不开放快捷方式。后续用户接受央视频“快捷入口＋弹窗自动关闭”，追加开放全屏开屏绕过入口，未宣称纯入口消除首页弹窗。

check-merge 的冻结差异仅为之前已登记的 HTTPDNS CR，本次无新增冻结项修改；按该 CR 使用 ALLOW_FROZEN=1。不 push、不公开发布。

## Task 3 签名与手机回归

最终 0.1.2 / code 3；增加央视频组合方案说明用例后 app 58/58，全项目 361/361（原 360 加一），失败/错误/跳过 0。`./gradlew :app:testDebugUnitTest :app:assembleRelease` 和 `./gradlew test` 均成功。冻结模块、依赖版本未改。

手机原安装使用 Android Debug 证书，而既有发布密钥是另一证书。安装前核对原 APK 指纹：debug SHA-256 `ad643041690b088915d35f0e69809b4fecbf0c3339e23722d8189ba75cc1aad7`。为保留设置，将同一个 release 构建分别签成手机兼容包和发布包；两者 apksigner verify 通过。只用兼容包 `adb install --no-incremental -r`，Success；未卸载、清数据。发布证书 SHA-256 `78f2f71c5fbb7ccde21d4e6da77b91b8fcb6b1a6067ccb5a03a1dfacae83b4e5`，它不能覆盖当前 debug 签名安装。

交付目录 `/Users/binlei/广告拦截软件/deliverables/launch-shortcuts/`：

| 文件 | SHA-256 | 用途 |
|---|---|---|
| sentinel-0.1.2-phone-upgrade.apk | 430624c6e30bf01d5f82cac3b0d7924e24af79c025f06cc50d1980f62748d946 | 当前手机同签名覆盖升级，已安装 |
| sentinel-0.1.2-release.apk | e586b47f9446ccc539df912bc2355ddff1182887311708b774ab066c784b21cb | 既有发布签名交付，未安装到此手机 |

安装后保留此前保护级别、观察期、拦截历史与自动降级记录。首次升级时无障碍虽然显示开启但未绑定，通过系统页面关闭再开启恢复；第二次升级系统绑定保持，但首页仍显示 STOPPED；结束前再次通过系统页面恢复哨兵服务；一次重启哨兵进程后恢复无障碍授权，没有清除数据。系统列表位置变化期间误点会员中心向导任务助手授权页，已取消、未开启该服务。最终系统确认开启并绑定，快捷入口进入首页，13:05:48 有新的 POPUP_CLOSED；首页“界面处理已停止”仍与实际服务状态不一致，重绑未解决显示问题。保留证据，不将这项现有状态上报异常描述为已修复。

华为桌面已通过系统“添加”确认两个 `sentinel-launch-cainiao` / `sentinel-launch-yangshipin` 固定快捷方式；哨兵反馈“已确认添加到桌面”。真实桌面点击菜鸟快捷入口冷启动进入包裹首页，显示 0 个包裹；扫码页可进入，相机权限用途提示后已显示扫码页面，未拿真实条码测试识别。物流详情按用户要求稍后补验。

真实桌面点击央视频快捷入口冷启动，采样约 1.45–9.59 秒均为 HomeActivity，离散截图无全屏商业开屏或中央弹窗；仍有右下角活动入口，不承诺全部广告消失。首页广告自动关闭启用期间，哨兵日志有 12:25:46、12:27:40 的 POPUP_CLOSED，页面可操作；日志不显示 ruleId，不能独占归因新规则。电视 CCTV-1 新闻30分直播、新闻联播 20261003 点播均看到播放画面。选择性截图与文本在 `docs/device-survey/yangshipin-cainiao/delivery-validation/`。

### 原图标自动改路的限制

用户追加要求点击原央视频图标直接改走快捷路径。未修改目标 APK、禁用组件或更换桌面。ADB 复现先正常 SplashActivity 启动、约 0.3 秒后通过 OpenActivity 转入 third_h5 深链（NEW_TASK/CLEAR_TASK）；约 3 秒后仍看到梦之蓝商业全屏开屏，证据 redirect-probe.png。HomeActivity.onNewIntent 只重新解析参数，未直接调用 initSplash 或关闭已建立的开屏流程。这个转接样本失败，不标为可靠原图标拦截；此前原入口的三组商业广告对照仍成立。

正在等待用户选择是否用同图标、同位置、同名称的快捷方式代替桌面原入口。这个方案只更改桌面布局，不将其描述为修改原应用的 launcher 组件。

### 菜鸟补充对照

升级后按原方案另做三组正常/直接首页冷启动，开始时间 12:48:23、12:49:35、12:50:48、12:52:01、12:53:14、12:54:27，间隔至少 72 秒。每次记录无障碍均为 Bound。正常与候选均未采样到商业开屏或 AdsActivity，因此本轮只记“待验证/无商业广告基线”，不能作为新增免广告成功证据，也不能推测广告服务端频次。原早期三组真实商业广告截图保留，但其规则状态一致性没有逐次证据，已明确限制；不借这轮无广告对照增强旧结论。时序与服务状态见 delivery-validation/cainiao-recheck.json。
