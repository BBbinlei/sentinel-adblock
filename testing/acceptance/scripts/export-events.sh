#!/usr/bin/env bash
# 导出「哨兵」事件表（events）为 CSV，并输出按 kind / pkg 汇总的计数。
#
# 用法：
#   export-events.sh [--since <毫秒时间戳>] [--db <本地 sentinel.db>] [--out-dir <目录>] [--keep-db]
#
#   --since <ms>   只导出 ts >= <ms> 的事件（Unix 毫秒时间戳，例如 $(( $(date +%s) * 1000 - 86400000 )) 为 24 小时前）
#   --db <file>    不连设备，直接使用本地数据库文件（同目录下存在 <file>-wal / <file>-shm 时一并生效），用于离线测试或复查
#   --out-dir <d>  输出目录，默认 testing/acceptance/reports/
#   --keep-db      同时把拉取到的数据库副本保存到输出目录（默认不保存，数据库含已安装应用列表等隐私信息）
#
# 设备：多台设备时设置 ANDROID_SERIAL=<序列号>（adb 自动识别）。
# 前提：设备上安装的是可调试（debug）包；release 包不可调试，run-as 会失败。
set -euo pipefail

PKG=com.sentinel.adblock
DB_NAME=sentinel.db

usage() { sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'; }
die() { echo "错误：$*" >&2; exit 1; }

since=0
local_db=
keep_db=0
script_dir=$(cd "$(dirname "$0")" && pwd)
out_dir=$script_dir/../reports

while [[ $# -gt 0 ]]; do
    case $1 in
        --since) [[ $# -ge 2 ]] || die '--since 需要参数'; since=$2; shift 2 ;;
        --db) [[ $# -ge 2 ]] || die '--db 需要参数'; local_db=$2; shift 2 ;;
        --out-dir) [[ $# -ge 2 ]] || die '--out-dir 需要参数'; out_dir=$2; shift 2 ;;
        --keep-db) keep_db=1; shift ;;
        -h|--help) usage; exit 0 ;;
        *) usage >&2; exit 2 ;;
    esac
done
[[ $since =~ ^[0-9]+$ ]] || die "--since 必须是非负整数毫秒时间戳：$since"

command -v sqlite3 >/dev/null || die '未找到 sqlite3（macOS 自带；Linux 请安装 sqlite3 包）。'

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
work_db=$tmp/$DB_NAME

# ---------- 从设备拉取 ----------
pull_from_device() {
    command -v adb >/dev/null || die '未找到 adb，请先安装 Android platform-tools。'
    local state
    state=$(adb get-state 2>&1 | tr -d '\r') || true
    [[ $state == device ]] || die "adb 未连接到设备（$state）。多台设备时请设置 ANDROID_SERIAL。"
    echo "设备：${ANDROID_SERIAL:-$(adb get-serialno | tr -d '\r')}" >&2

    local listing
    if ! listing=$(adb shell run-as "$PKG" ls databases 2>&1 | tr -d '\r'); then
        run_as_hint "$listing"
    fi
    case $listing in
        *"not debuggable"*|*"unknown"*|*"is not an application"*|*"Permission denied"*|*"run-as:"*)
            run_as_hint "$listing" ;;
    esac
    grep -qx "$DB_NAME" <<<"$listing" || die "设备上 databases/ 中没有 $DB_NAME（App 是否已初始化？）。目录内容：$listing"

    local f
    for f in "$DB_NAME" "$DB_NAME-wal" "$DB_NAME-shm"; do
        if grep -qx "$f" <<<"$listing"; then
            adb exec-out run-as "$PKG" cat "databases/$f" >"$tmp/$f"
            echo "已拉取 databases/$f（$(wc -c <"$tmp/$f" | tr -d ' ') 字节）" >&2
        fi
    done
}

run_as_hint() {
    cat >&2 <<EOF
错误：run-as $PKG 失败。
  设备返回：$1
  可能原因：
    1. 安装的是 release 包（android:debuggable=false），run-as 不可用 → 请安装 debug 包
       （./gradlew :app:installDebug）后重试；验收期间如必须用 release 包，请改用 App 内导出功能（若有）。
    2. 包名不是 $PKG，或 App 未安装。
    3. 多台设备时未设置 ANDROID_SERIAL。
EOF
    exit 1
}

if [[ -n $local_db ]]; then
    [[ -f $local_db ]] || die "找不到本地数据库：$local_db"
    cp "$local_db" "$work_db"
    for suffix in -wal -shm; do
        [[ -f $local_db$suffix ]] && cp "$local_db$suffix" "$work_db$suffix"
    done
    echo "使用本地数据库：$local_db" >&2
else
    pull_from_device
fi

head -c 16 "$work_db" | grep -q '^SQLite format 3' || die '数据库文件不是 SQLite 格式（从设备拉取时可能是 run-as 的错误输出）。'

# ---------- 导出与汇总 ----------
sql() { sqlite3 -batch "$work_db" "$@"; }

check=$(sql 'PRAGMA quick_check;' 2>&1 || true)
[[ $check == ok ]] || echo "警告：数据库 quick_check 结果：$check（复制时 App 可能正在写入，可稍后重导）" >&2
[[ $(sql "SELECT count(*) FROM sqlite_master WHERE type='table' AND name='events';") == 1 ]] \
    || die '数据库中没有 events 表。'

mkdir -p "$out_dir"
out_dir=$(cd "$out_dir" && pwd)
stamp=$(date +%Y%m%d-%H%M%S)
csv=$out_dir/events-$stamp.csv
summary=$out_dir/events-$stamp-summary.txt
where="WHERE ts >= $since"

export_csv() {
    sql -header -csv "SELECT id, ts, strftime('%Y-%m-%d %H:%M:%f', ts / 1000.0, 'unixepoch', 'localtime') AS time,
                              pkg, kind, ruleId, detail
                       FROM events $where ORDER BY ts, id;" >"$csv"
}

table_exists() { [[ $(sql "SELECT count(*) FROM sqlite_master WHERE type='table' AND name='$1';") == 1 ]]; }

write_summary() {
    {
        echo "# 哨兵事件汇总  生成于 $(date '+%Y-%m-%d %H:%M:%S')"
        if [[ $since -gt 0 ]]; then
            echo "# 范围：ts >= $since（$(sql "SELECT datetime($since / 1000, 'unixepoch', 'localtime');")）"
        else
            echo "# 范围：全部（数据库保留最近 30 天）"
        fi
        echo "# 事件总数：$(sql "SELECT count(*) FROM events $where;")"
        echo
        echo "## 按 kind"
        sql -header -column "SELECT kind, count(*) AS n FROM events $where GROUP BY kind ORDER BY n DESC, kind;"
        echo
        echo "## 按 pkg（NULL = 无归属应用，如全局 DNS 拦截）"
        sql -header -column "SELECT ifnull(pkg, '(NULL)') AS pkg, count(*) AS n FROM events $where GROUP BY pkg ORDER BY n DESC, pkg;"
        echo
        echo "## 按 pkg × kind"
        sql -header -column "SELECT ifnull(pkg, '(NULL)') AS pkg, kind, count(*) AS n FROM events $where GROUP BY pkg, kind ORDER BY pkg, n DESC;"
        echo
        echo "## 按日 × kind（本地时间）"
        sql -header -column "SELECT date(ts / 1000, 'unixepoch', 'localtime') AS day, kind, count(*) AS n
                             FROM events $where GROUP BY day, kind ORDER BY day, kind;"
        # 以下两节供一周实测每日检查「guard 降级记录与临时放行记录」，表不存在时跳过。
        if table_exists rule_overrides; then
            echo
            echo "## guard 降级 / 规则覆盖（rule_overrides，全部）"
            sql -header -column "SELECT pkg, ruleId, state, reason,
                                        datetime(createdAt / 1000, 'unixepoch', 'localtime') AS created
                                 FROM rule_overrides ORDER BY createdAt DESC;"
        fi
        if table_exists app_config; then
            echo
            echo "## 临时放行（app_config.tempAllowUntil 非空）"
            sql -header -column "SELECT pkg, label,
                                        datetime(tempAllowUntil / 1000, 'unixepoch', 'localtime') AS allow_until
                                 FROM app_config WHERE tempAllowUntil IS NOT NULL ORDER BY tempAllowUntil DESC;"
        fi
    } >"$summary"
}

export_csv
write_summary
if [[ $keep_db == 1 ]]; then
    cp "$work_db" "$out_dir/sentinel-$stamp.db"
    echo "数据库副本：$out_dir/sentinel-$stamp.db（含隐私信息，勿提交）" >&2
fi

cat "$summary"
echo >&2
echo "CSV：$csv" >&2
echo "汇总：$summary" >&2
