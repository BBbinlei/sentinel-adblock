#!/bin/bash
# launchd 入口：单次调度。所有逻辑在 supervisor.py。
cd "$(dirname "$0")/../.." || exit 1
exec python3 scripts/supervisor/supervisor.py tick "$@"
