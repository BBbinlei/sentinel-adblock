#!/usr/bin/env bash
set -euo pipefail

if [[ $# != 1 || ! $1 =~ ^[A-Za-z0-9][A-Za-z0-9_-]*$ ]]; then
    echo '用法：pull-snapshot.sh <name>（字母、数字、下划线、连字符；设备上须为新名称）' >&2
    exit 2
fi
command -v adb >/dev/null
name=$1
remote_root=/sdcard/Android/data/com.sentinel.adblock/files/snapshots
fixture_root=$(cd "$(dirname "$0")/../fixtures/snapshots" && pwd)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

find_snapshot() {
    adb shell "if [ -d '$remote_root' ]; then find '$remote_root' -type f -name '$name.json'; fi" | tr -d '\r'
}

if [[ -n $(find_snapshot) ]]; then
    echo '设备上已有同名快照，请换一个名称以免拉取旧数据。' >&2
    exit 1
fi
adb shell am broadcast -a com.sentinel.a11y.DEBUG_DUMP -p com.sentinel.adblock --es name "$name"
remote=
for ((attempt = 0; attempt < 20; attempt++)); do
    remote=$(find_snapshot)
    [[ -n $remote ]] && break
    sleep 0.5
done
relative=${remote#"$remote_root/"}
pkg=${relative%/*}
if [[ ! $pkg =~ ^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$ || $relative != "$pkg/$name.json" ]]; then
    echo '未找到唯一的 <包名>/<name>.json；请确认 M4 调试录制器已启用、无障碍服务已连接。' >&2
    exit 1
fi
target=$fixture_root/$pkg/$name.json
if [[ -e $target ]]; then
    echo "本地快照已存在，拒绝覆盖：$target" >&2
    exit 1
fi
adb pull "$remote" "$tmp/$name.json"
[[ -s $tmp/$name.json ]] || { echo '快照为空。' >&2; exit 1; }
mkdir -p "$fixture_root/$pkg"
mv "$tmp/$name.json" "$target"
echo "已保存：$target"
