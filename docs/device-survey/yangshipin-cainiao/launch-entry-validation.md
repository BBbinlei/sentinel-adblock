# 启动入口实机验证（2026-10-04）

设备：Huawei OCE_AN50。账号与 Wi-Fi 未主动更改，初始无障碍已启用。保护状态未逐次保存；升级后发现菜鸟两条规则被重启保护自动停用，因此不能宣称全部原始对照的运行规则完全一致。界面处于网络观察期，未主动启用哨兵 VPN。未清除数据、禁用组件或修改目标 APK。

菜鸟 8.11.923 / 475，央视频 3.5.3.26910 / 305030，原 APK 指纹与 metadata.json 相同。每个应用按正常、候选顺序做三组冷启动；每次 force-stop 后启动，开始时间间隔至少 70 秒（脚本 monotonic 等待再加 HOME/force-stop 约 2 秒）。记录 wall_time 为采样结束时间，不能拿结束时间差代替启动间隔。采样偏移 .35/.85/1.6/3/5/8/12 秒，实际执行耗时见 JSON；截图是离散采样，不声称逐帧无广告。

| 应用 | 正常入口三次 | 候选入口三次 | 判断 |
|---|---|---|---|
| 菜鸟 | WelcomeActivity → AdsActivity → HomePageActivity；LAN 面膜、千问推广商业广告 | 直接 HomePageActivity；约 1.1–1.4 秒 Activity 已为首页；截图首页约 1–2 秒出现，没有 AdsActivity/欢迎页回跳 | 冷启动入口通过，开放此精确版本；仍有短暂品牌启动屏、首页内广告 |
| 央视频 | SplashActivity → HomeActivity 内全屏广告，随后首页弹窗 | OpenActivity 深链 → HomeActivity；全屏广告消失，但三次均有品牌强国/五粮液首页广告弹窗 | 候选未通过，不开放创建 |

原始时序：launch-validation/cold-launches.json。代表截图：cainiao-commercial.png、cainiao-direct-home.png、yangshipin-commercial.png、yangshipin-popup-failure.png（均在 launch-validation）。快递列表可正常加载但当前显示 0/暂无包裹；用户已明确选择稍后自行检查物流详情，不用空列表代替物流详情验证。

## 央视频代码支持的候选及边界

候选精确组件：com.tencent.videolite.android.component.literoute.OpenActivity；ACTION_VIEW/BROWSABLE；URI：`cctvvideo://cctv.com/HomeActivity?from=third_h5`。

这个 URI 由代码支持的 HomeActivity 路由、query 解析和明确存在的 third_h5 值组成，并非猜测字段。证据见 launch-validation/yangshipin-route-methods.txt 与既有 evidence/yangshipin-entry-methods.txt：

- OpenActivity.c 优先读取 action_key，否则 getDataString；e 用 j.f(Uri) 解析查询 Map，并读取 from，代码含 third_h5 分支。
- 路由回调 t$a.e 对 HomeActivity 返回 null，因此落入 OpenActivity 的 j.d(context, class, map) 分支；j.d 遍历 Map，对非 exp_ 键调用 Intent.putExtra(String,String)。HomeActivity 的 from 得到原字符串。
- HomeActivity.parseIntent 在 third_/push_ 时置 needHideADSplash=true；initSplash 隐藏全屏素材后仍调用 onGoHomeEvent。实机证实这个条件不能关闭首页弹窗。

2026-10-04 用户追加要求处理首页广告；继续研究精确关闭控件并使用已有界面规则契约处理，不把自动关闭弹窗等同为入口天然免广告。快捷启动仍不依赖 Shizuku、VPN 或无障碍。

功能回归结果见签名交付报告。

## 后台切回（独立记录）

分别 HOME 后等待至少 70 秒，保留进程再经正常/候选入口返回。每个入口只做一次，不作为三组冷启动的替代。
- 央视频正常与候选均回到已加载首页，本次没有重新出现全屏或中央广告弹窗；右下角活动入口仍存在。这个样本不证明后台始终免广告。
- 菜鸟候选返回首页后出现“查看电商平台包裹”的账号关联引导（非商业广告），正常入口也单独采样；时序见 launch-validation/*-hot-*.txt。
- 央视频冷启动弹窗关闭控件：HomeActivity 内 modal FrameLayout → RelativeLayout → LinearLayout → ImageView，精确 ID `com.cctv.yangshipin.app.androidp:id/iv_close`；fl_click/iv_image 是广告跳转而非关闭按钮。脱敏层级见 yangshipin-popup.xml。用已有 UiRule/Page/ANYTIME 契约添加 app 内控制，未改规则引擎或数据层。

## 用户追加接受的央视频组合方案

用户实测原央视频图标仍显示全屏开屏广告后，明确接受“快捷入口＋弹窗自动关闭”。因此将验证范围拆清：先前三组候选对照只支持第三方深链跳过全屏开屏，不支持纯入口消除首页弹窗；新增央视频桌面快捷方式只承诺前者，详情页明确首页弹窗需单独开启无障碍自动关闭且可能短暂显示。这个决定不把原始失败改写为纯入口免全部广告的成功。原图标依旧普通启动，未修改目标 APK。
