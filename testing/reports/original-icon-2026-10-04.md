# Task 4 原央视频图标自动转接

实现只在 app，未改目标 APK、禁用组件、清除数据或移动原图标。普通应用权限下启动后转接曾失败；本轮用 Shizuku shell UserService 的 Android IActivityController，在启动前取消原 SplashActivity，异步启动固定 OpenActivity 深链。需要 Shizuku 运行并授权；重启手机后需重新启动 Shizuku。

## 固定范围与恢复行为

- API 31，央视频 305030 / 3.5.3.26910，MAIN + LAUNCHER + 精确 SplashActivity 才匹配。
- 每次匹配检查版本及 OpenActivity 导出/启用/权限状态；失配、未知应用、内部启动、其他深链直接放行。
- 固定 URI 为 `cctvvideo://cctv.com/HomeActivity?from=third_h5`，不接受外部组件、URI 或 shell 命令。
- 关闭时解除控制器并销毁服务；Binder 服务死亡后系统恢复普通启动。快捷启动失败时允许一次普通入口恢复，避免循环；重复点击合并到正在进行的转接。
- 全屏开屏绕过与首页广告弹窗关闭独立。首页弹窗可能显示数秒，需已有无障碍与防护开启，不称为完全没有广告。

## 真机证据

设备 OCE_AN50 / Android 12 / API 31。真实原图标位于 `[34,1689][287,1987]`，实际点击 (160,1810)，保留原名称/图标/位置。三组关闭/启用对照，冷启动间隔至少 70 秒；同一账号、网络、标准保护、已恢复并保持启用的首页关闭规则。关闭/启用只切换新增转接开关。采样原图标点击后 1、3、7 秒截图及系统启动记录。没有用 UiAutomation 转储干扰无障碍。

| 样本 | 手机本地开始时间 | 结果 |
|---|---|---|
| off-1 | 16:45:19 | 国窖 1573 全屏商业广告 |
| on-1 | 16:46:53 | 取消 SplashActivity，转 OpenActivity；无全屏开屏，7 秒首页可操作 |
| off-2 | 16:48:51 | 梦之蓝全屏商业广告 |
| on-2 | 16:52:43 | 取消 SplashActivity，转 OpenActivity；无全屏开屏，7 秒首页可操作 |
| off-3 | 16:54:40 | 国窖 1573 全屏商业广告 |
| on-3 | 17:00:03 | 取消 SplashActivity，转 OpenActivity；无全屏开屏，7 秒首页可操作 |

开启样本系统日志：原 MAIN/LAUNCHER SplashActivity `result = 102`（START_ABORTED），约 0.12 秒后 UID 2000 启动固定 OpenActivity，再进入 HomeActivity；不是等待开屏自然结束。原图标和快捷图标并列的桌面截图单独保留。

本轮初次连接发生 NullPointerException，查明 Shizuku UserServiceArgs 必须提供 processNameSuffix，补配置后恢复；增加对 API 参数真实序列化的回归测试。初次安装曾被页面切换打断，后续收到 adb install Success 才继续，保留设置与历史。

## 自动化验证与签名

先跑原 app 基线 58 项通过。本轮最终 app 70 项，全项目 373 项，失败/错误/跳过均为 0；test、assembleRelease、check-merge app 通过。覆盖原入口匹配、版本和系统不支持、未知应用/深链/内部启动放行、服务默认关闭、非法 Binder 接口、未验证系统启用拒绝、Shizuku 参数序列化、am 零退出但错误输出不能当启动成功、界面未授权与运行成功分开。

手机使用原开发证书 SHA-256 `ad643041690b088915d35f0e69809b4fecbf0c3339e23722d8189ba75cc1aad7` 同签名覆盖；另用原发布证书生成 release 包，不能用后者覆盖当前开发签名安装。测试阶段未发布；随后用户明确授权更新 GitHub、Release 与下载页，按 Task 5 发布。

## 功能复核与证据边界

开启样本 POPUP_CLOSED 时间分别为 16:46:57、16:52:46、17:00:06，和首页 3/7 秒截图一致；没有人为点击关闭按钮，日志没有 ruleId，因此不独占归因某一条关闭规则。首页广告不是被免广告接口删除，仍会短暂出现。后台再点原图标样本 warm-1 未出现全屏广告，单独记录，不扩张为所有后台切回场景。CCTV1 藏锋第1集直播及新闻联播20261003点播出现实际播放画面。

最终保留 Shizuku 和原图标转接开启、原桌面布局不变；未重启手机验证自动启动 Shizuku（界面已说明重启后需要用户启动）。只有 API31 和当前央视频版本开放新功能。菜鸟快递详情仍按用户选择由用户补验；未改变菜鸟原图标路径。

证据目录：`docs/device-survey/yangshipin-cainiao/original-icon-validation/`。原始完整系统活动转储已裁剪到当前央视频记录；临时 APK/探针和其手机副本清理。
