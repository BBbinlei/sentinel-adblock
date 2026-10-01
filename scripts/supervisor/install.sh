#!/bin/bash
# 安装 launchd 定时任务（每 TICK 秒触发一次 tick）。需要用户确认后再运行。
#   install.sh            安装并加载
#   install.sh --print    只打印将要写入的 plist，不安装
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
LABEL="com.sentinel.supervisor"
PLIST="$HOME/Library/LaunchAgents/$LABEL.plist"
TICK="${TICK_INTERVAL:-$(python3 -c "import json;print(json.load(open('$HERE/channels.json')).get('tick_interval_sec',600))")}"
# launchd 的 PATH 很短，必须把 claude/codex/gradle 所在目录带上
PATH_VALUE="$HOME/.npm-global/bin:/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin"

PLIST_BODY=$(cat <<PL
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key><string>$LABEL</string>
  <key>ProgramArguments</key>
  <array><string>/bin/bash</string><string>$HERE/tick.sh</string></array>
  <key>WorkingDirectory</key><string>$ROOT</string>
  <key>StartInterval</key><integer>$TICK</integer>
  <key>RunAtLoad</key><true/>
  <key>StandardOutPath</key><string>$ROOT/.supervisor/logs/launchd.log</string>
  <key>StandardErrorPath</key><string>$ROOT/.supervisor/logs/launchd.log</string>
  <key>EnvironmentVariables</key>
  <dict><key>PATH</key><string>$PATH_VALUE</string><key>HOME</key><string>$HOME</string></dict>
</dict>
</plist>
PL
)
if [ "${1:-}" = "--print" ]; then echo "$PLIST_BODY"; exit 0; fi

mkdir -p "$ROOT/.supervisor/logs" "$HOME/Library/LaunchAgents"
echo "$PLIST_BODY" > "$PLIST"
launchctl bootout "gui/$(id -u)/$LABEL" 2>/dev/null || true
launchctl bootstrap "gui/$(id -u)" "$PLIST"
echo "已安装：${PLIST}（每 ${TICK}s 一次）。查看状态：python3 ${HERE}/supervisor.py status ；日志：${ROOT}/.supervisor/logs/"
