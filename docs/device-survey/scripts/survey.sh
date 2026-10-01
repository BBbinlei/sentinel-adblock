#!/bin/bash
# M0 Task 1：实机核实（只读）。手机开启 USB 调试并连接 Mac 后运行。
# 全程只执行只读命令：getprop / pm list / settings list / dumpsys package / appops get。不修改手机上的任何东西。
#
#   survey.sh                    采集系统版本、系统 App、候选广告包信息、appops
#   survey.sh snapshot <标签>    只导出一次 settings（用于「拨动开关前后各导一次」）
#   survey.sh diff <标签A> <标签B>  对比两次 settings 快照，列出变化的键
#
# 输出目录：docs/device-survey/out/<时间戳>/
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT_ROOT="$HERE/../out"
command -v adb >/dev/null || { echo "找不到 adb，请先安装 Android platform-tools"; exit 1; }

need_device() {
  local n
  n=$(adb devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')
  [ "$n" = "1" ] || { echo "需要恰好连接 1 台已授权的设备（当前 $n 台）。请检查 USB 调试授权。"; adb devices; exit 1; }
}

snapshot_settings() {  # $1=输出目录 $2=标签
  mkdir -p "$1"
  for ns in system secure global; do
    adb shell settings list "$ns" | sort > "$1/settings-$ns-$2.txt"
  done
}

CANDIDATES="com.opos.ads com.android.adservices.api com.heytap.pictorial com.nearme.instant.platform com.coloros.assistantscreen com.heytap.quicksearchbox com.heytap.market com.heytap.browser com.heytap.themestore com.nearme.gamecenter"

case "${1:-all}" in
  snapshot)
    [ -n "${2:-}" ] || { echo "用法: $0 snapshot <标签>"; exit 2; }
    need_device
    DIR="$OUT_ROOT/settings"
    snapshot_settings "$DIR" "$2"
    echo "已保存到 $DIR（标签 $2）"
    ;;
  diff)
    [ -n "${2:-}" ] && [ -n "${3:-}" ] || { echo "用法: $0 diff <标签A> <标签B>"; exit 2; }
    DIR="$OUT_ROOT/settings"
    for ns in system secure global; do
      echo "=== $ns ==="
      diff "$DIR/settings-$ns-$2.txt" "$DIR/settings-$ns-$3.txt" || true
    done
    ;;
  all)
    need_device
    DIR="$OUT_ROOT/$(date +%Y%m%d-%H%M%S)"
    mkdir -p "$DIR"
    {
      echo "ro.build.display.id        = $(adb shell getprop ro.build.display.id | tr -d '\r')"
      echo "ro.build.version.release   = $(adb shell getprop ro.build.version.release | tr -d '\r')"
      echo "ro.build.version.oplusrom  = $(adb shell getprop ro.build.version.oplusrom | tr -d '\r')"
      echo "ro.product.model           = $(adb shell getprop ro.product.model | tr -d '\r')"
    } | tee "$DIR/system-version.txt"

    adb shell pm list packages -s -f | tr -d '\r' | sort > "$DIR/system-packages.txt"
    echo "系统 App 共 $(wc -l < "$DIR/system-packages.txt" | tr -d ' ') 个 → $DIR/system-packages.txt"

    snapshot_settings "$DIR" baseline

    : > "$DIR/candidates.txt"
    for p in $CANDIDATES; do
      if adb shell pm list packages | tr -d '\r' | grep -q "^package:$p$"; then
        echo "存在  $p" | tee -a "$DIR/candidates.txt"
        adb shell dumpsys package "$p" > "$DIR/dumpsys-$p.txt" 2>&1
        adb shell appops get "$p" > "$DIR/appops-$p.txt" 2>&1
      else
        echo "不存在 $p" | tee -a "$DIR/candidates.txt"
      fi
    done

    # 与广告/推荐/营销相关、但不在候选名单里的系统包，供人工判断
    grep -iE 'ads|advert|pictorial|recommend|assistantscreen|instant|push|promot|market' "$DIR/system-packages.txt" \
      | sed 's/.*=//' | sort -u > "$DIR/other-ad-related-packages.txt"
    echo "其他疑似广告相关的系统包 → $DIR/other-ad-related-packages.txt"
    echo
    echo "完成。下一步：对照 MASTER_PLAN.md Task 1 的 Step 4，逐个拨动广告开关，用"
    echo "  $0 snapshot <标签前>   /   $0 snapshot <标签后>   /   $0 diff <标签前> <标签后>"
    echo "找出开关对应的 settings 键，再写入 docs/device-survey/find-x7-ultra-survey.md。"
    ;;
  *)
    echo "用法: $0 [all | snapshot <标签> | diff <标签A> <标签B>]"; exit 2 ;;
esac
