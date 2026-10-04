# 启动入口集成验证

基线 app 37/37。实现后 app 57/57；全项目 `./gradlew test`：app 57、core-rules 29、data 46、a11y 76、notify 14、system 43、vpn 58、guard 23、rule-regression 14，共 360，失败/错误/跳过 0。

新增覆盖精确版本与组件状态、未知 ID 拒绝、失效/启动异常回退、未验证入口拒绝、桌面不支持与固定快捷方式数据、请求/确认/未确认反馈、详情页三种状态/创建按钮，以及央视频真实 XML 弹窗关闭节点匹配。仅使用既有依赖。

实机冷/热对照详见 docs/device-survey/yangshipin-cainiao/launch-entry-validation.md：菜鸟冷启动通过，央视频候选有商业首页弹窗，不开放快捷方式。弹窗自动关闭与签名安装回归待 Task 3 补充。

check-merge 的冻结差异仅为之前已登记的 HTTPDNS CR，本次无新增冻结项修改；按该 CR 使用 ALLOW_FROZEN=1。不 push、不公开发布。
