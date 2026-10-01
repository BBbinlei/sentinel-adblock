# <关卡> 真机执行记录（<YYYY-MM-DD>）

> 复制本文件为 `reports/<关卡>-<YYYY-MM-DD>.md` 后填写。用例定义见 `../PLAN.md` Task 3，操作步骤见 `../checklists/real-device.md`。

## 基本信息

| 项 | 值 |
|---|---|
| 关卡 | G3 / G4 / G5 / G6 / G8 |
| 日期 | YYYY-MM-DD |
| 执行人 | |
| 设备 | Find X7 Ultra（型号：`getprop ro.product.model`） |
| ColorOS 版本 | `getprop ro.build.display.id` / `ro.build.version.oplusrom` |
| Android 版本 | `getprop ro.build.version.release` |
| App 版本 | versionName / versionCode |
| 构建类型 | debug / release |
| 代码提交 | `git rev-parse --short HEAD` |
| Shizuku 版本 / 状态（G5、G8） | |
| 网络环境 | Wi-Fi / 移动数据 |

## 逐项结果

| 编号 | 用例 | 结果（通过 / 未通过 / 未执行） | 证据（截图、录屏、日志位置） | 系统设置已撤销 | 备注 |
|---|---|---|---|---|---|
| DI-xx | | | | 是 / 否 / 不涉及 | |

## 未通过项

| 编号 | 现象 | 原因分析 | 修复提交 | 复测日期与结果 |
|---|---|---|---|---|
| | | | | |

## 待核实项与向导/文档更新

- （如 DI-21 记录的 ColorOS 实际路径、DI-11 的归属正确率、DI-22 的回退方式等需同步到其他模块的信息）

## 结论

- 本关卡真机用例：☐ 全部通过　☐ 有未通过项（见上表，修复后复测）
- 执行后设备状态：所有临时修改均已撤销 ☐
