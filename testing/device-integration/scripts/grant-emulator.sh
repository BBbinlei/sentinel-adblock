#!/usr/bin/env bash
# 设备集成测试前置授权（testing/device-integration/PLAN.md「前置」）。
# 只用于模拟器；真机请按 checklists/real-device.md 手动操作并保留撤销步骤。
#
# 用法：
#   ./grant-emulator.sh                    # 只连了一台设备时
#   ANDROID_SERIAL=emulator-5554 ./grant-emulator.sh
#
# 可覆盖的环境变量：
#   APP_PKG        被测 App 包名（默认 com.sentinel.adblock）
#   A11Y_SERVICE   无障碍服务组件名 —— M4 engine-a11y 完成后按实际类名填写
#   NOTIFY_LISTENER 通知监听服务组件名 —— M6 engine-notify 完成后按实际类名填写
#
# 撤销：
#   adb shell appops set "$APP_PKG" ACTIVATE_VPN default
#   adb shell settings delete secure enabled_accessibility_services
#   adb shell settings put secure accessibility_enabled 0
#   adb shell cmd notification disallow_listener "$NOTIFY_LISTENER"
set -euo pipefail

APP_PKG="${APP_PKG:-com.sentinel.adblock}"
# TODO(M4): 按 engine-a11y 实际的 AccessibilityService 类名填写。
A11Y_SERVICE="${A11Y_SERVICE:-com.sentinel.adblock/com.sentinel.a11y.service.SentinelAccessibilityService}"
# TODO(M6): 按 engine-notify 实际的 NotificationListenerService 类名填写。
NOTIFY_LISTENER="${NOTIFY_LISTENER:-com.sentinel.adblock/com.sentinel.notify.service.SentinelNotificationListener}"

if ! command -v adb >/dev/null 2>&1; then
  echo "错误：未找到 adb，请安装 Android SDK platform-tools 并加入 PATH。" >&2
  exit 1
fi

# adb 会自动读取 ANDROID_SERIAL；这里只做提示。
if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  echo "目标设备：$ANDROID_SERIAL"
fi

adb wait-for-device

echo "[1/3] 预授权 VPN：$APP_PKG"
adb shell appops set "$APP_PKG" ACTIVATE_VPN allow

echo "[2/3] 开启无障碍服务：$A11Y_SERVICE"
adb shell settings put secure enabled_accessibility_services "$A11Y_SERVICE"
adb shell settings put secure accessibility_enabled 1

echo "[3/3] 授予通知使用权：$NOTIFY_LISTENER"
adb shell cmd notification allow_listener "$NOTIFY_LISTENER"

echo "完成。"
