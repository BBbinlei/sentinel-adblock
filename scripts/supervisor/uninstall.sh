#!/bin/bash
# 卸载 launchd 定时任务。--quiet 时不输出。
LABEL="com.sentinel.supervisor"
PLIST="$HOME/Library/LaunchAgents/$LABEL.plist"
launchctl bootout "gui/$(id -u)/$LABEL" 2>/dev/null || true
rm -f "$PLIST"
[ "${1:-}" = "--quiet" ] || echo "已卸载 $LABEL"
