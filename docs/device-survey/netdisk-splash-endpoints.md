# 百度网盘开屏远端测量

结论：本次测量在 Mac 端 ADB 连接阶段被执行环境权限阻断，未取得手机数据。没有可推荐的 AD-ONLY IP/CIDR；不能据此新增拦截规则。未执行冷启动、拦截、补丁、配置变更、安装、提交或推送。

## 环境

- 尝试时间：2026-10-02 16:46:42–43（Asia/Shanghai，ADB 日志时间）。
- 目标（用户提供，未实机核验）：HUAWEI OCE-AN50，HarmonyOS 4.2 / Android 12；USB serial `8QM0221708001993`；网盘 `com.baidu.netdisk`；哨兵 `com.sentinel.adblock`；无 root，shell 权限；Shizuku 已运行。
- 本机 ADB：`/opt/homebrew/bin/adb`，输出版本 `1.0.41 / 37.0.1-15733141`。
- 网盘 UID：未知；手机是否解锁/亮屏：未知。
- Sentinel VPN：未知；Sentinel 无障碍服务：未知；Shizuku 未触碰。

## 方法与阻断证据

已先读取 `CLAUDE.md` 和 `docs/HANDOFF.md`（含第 5 节），并读取总计划、契约与进度。以本次明确授权的只读测量、仅写报告、不提交限制为准。

尝试 `adb -s 8QM0221708001993 get-state`，随后尝试通过同一 serial 读取 `dumpsys power`、`dumpsys window policy`、网盘 `dumpsys package`、`settings get secure enabled_accessibility_services` 和 `dumpsys connectivity`。两次 ADB 调用均在本机 daemon 启动阶段失败，退出码 1；手机端 shell 未运行。

关键原始输出：

```text
* daemon not running; starting now at tcp:5037
Unable to create an interface plug-in (e00002be)
could not install *smartsocket* listener: Operation not permitted
* failed to start daemon
error: cannot connect to daemon
```

当前环境不允许提权，故停止设备操作，没有改用其他权限途径。此错误不能证明手机离线、锁屏或未授权 USB。

Socket 来源：无成功来源。`/proc/net/{tcp,tcp6,udp,udp6}`、`ss`、`netstat`、`dumpsys netstats`、`/proc/<pid>/net/tcp` 尚未测试；不能归因于 Android 的读取限制。本机已有 `dig`、`host`、`whois`、`python3`，未下载安装工具；没有远端 IP 可供归属查询。

## 三轮时间线

| 轮次 | 冷启动/launch | SplashAdActivity resumed | MainActivity resumed | socket 采样 |
|---|---|---|---|---|
| 1 | 未执行：ADB 阻断 | 未观测 | 未观测 | 未执行 |
| 2 | 未执行：ADB 阻断 | 未观测 | 未观测 | 未执行 |
| 3 | 未执行：ADB 阻断 | 未观测 | 未观测 | 未执行 |

实际运行次数 0，不能提供首见偏移、约 200 ms 采样或至少 60 s 间隔的有效测量。未运行 `uiautomator dump`，未截图。

## 远端 IP

| IP | port/proto | owner | class | first-seen offset（各轮） | runs seen |
|---|---|---|---|---|---|
| 无观测数据 | — | — | 不可分类 | — | 无有效轮次 |

未观测不等于无连接。没有 TCP/UDP、UDP/443（QUIC 候选）、IPv6 或 IPv4-mapped IPv6 数据；也没有可核验的地址所有者或 AD-ONLY / BAIDU-SHARED / CDN-UNKNOWN / OTHER 分类。

## 候选 AD-ONLY CIDR

无。用户提供的广告 SDK 列表和开屏 Activity 名仅用于后续定位，不证明某个远端 IP 专用于广告。即使将来确认广告厂商归属，也须核验地址是否承载共享服务；不得从归属或开屏时间相关性直接推出安全拦截结论，不做 /16 扩张。

## BAIDU-SHARED：不得据本报告拦截

暂无经过本次实测确认的地址清单。本报告不授权拦截任何百度地址；需保留网盘主站、登录、更新及共享基础设施，不能将 HTTPDNS 或百度归属地址自动分类为广告专用。

## 尚不清楚

- 手机屏幕状态、网盘 UID、VPN 与无障碍实际状态；已有拦截是否影响广告与采样。
- shell 可用的 socket 来源与 UID 归属能力，三次开屏的真实远端及首见时间。
- 连接是否存在前置 DNS 查询：没有同步 DNS 证据，无法确认硬编码 IP / HTTPDNS；系统 DNS 日志缺少记录本身不足以证明两者。
- 地址是否专用于广告、其精确 CIDR 和对网盘核心功能的影响。没有证据支持“reset 后不影响网盘”。

继续测量需要能访问 USB、连接/启动 ADB daemon 的执行环境，并先确认手机亮屏解锁。随后严格使用用户指定的 force-stop → HOME → 等待 2 s → monkey 冷启动序列，完成三轮间隔至少 60 s 的观测；全程保持 Sentinel/Shizuku 状态，不改变设备配置。
