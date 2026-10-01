# 页面快照

当前只有空标注模板 `expected.json`；录制器由 M4 的 engine-a11y 提供，本目录不实现录制器，也不虚构快照。

录制时安装含 `SnapshotDumper` 的 debug 版 `com.sentinel.adblock`，开启并连接无障碍服务，在目标 App 打开待录页面。从仓库根目录执行：

```bash
testing/rule-regression/scripts/pull-snapshot.sh splash-001
```

脚本发送 `com.sentinel.a11y.DEBUG_DUMP` 广播，目标包为 `com.sentinel.adblock`，String extra `name=splash-001`。录制器将当前窗口写入 `/sdcard/Android/data/com.sentinel.adblock/files/snapshots/<包名>/splash-001.json`，脚本等待最多 10 秒并拉到本目录 `<包名>/splash-001.json`。支持 adb 标准环境变量 `ANDROID_SERIAL`。名称只用字母、数字、下划线、连字符，必须在设备快照目录内唯一且此前未使用；同名旧快照、多个结果、本地已存在文件或空文件均报错。出现同名错误时换一个名称，不覆盖旧证据。

目录按真实窗口所属包名分组，例如 `com.example.fixture/splash-001.json`。JSON 是 M4 的 `SnapshotNode` 序列化输出，具体字段以该实现为准；入库前确认可反序列化并去除账号、消息、手机号等个人信息。首批目标：开屏 15、弹窗 10、激励视频 5、陷阱 5、自动续费 2、正常页面 20，覆盖至少 10 个常用 App。

每拉取一份快照，人工检查页面并在 `expected.json` 增加对应项。键是 `<包名>/<页面>`，不带 `.json`：

```json
{
  "com.example.fixture/splash-001": {
    "category": "splash",
    "expect": "CLICK",
    "mustNotClickText": ["立即下载", "立即购买"]
  },
  "com.example.fixture/home-001": {
    "category": "normal",
    "expect": "NONE",
    "mustNotClickText": []
  }
}
```

`category` 仅取 `splash`、`popup`、`rewarded`、`trap`、`autorenew`、`normal`；`expect` 仅取 `CLICK`、`REWARDED`、`WARN`、`NONE`；`mustNotClickText` 为禁止点击的节点文字数组（无禁点文字时填 `[]`）。以上仅为格式示例，不是已录制证据。按 PLAN Task 3，RR-02 核对开屏与正常页，RR-03 核对判定、禁点文字及启动窗口外的正常页。具备产品实现后运行 `./gradlew :testing:rule-regression:testDebugUnitTest`。
