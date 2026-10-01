#!/bin/bash
# 假的 claude / codex。行为由 $STUB_DIR/<名字>.mode 控制：
#   ok            在工作目录提交一个文件（算有进展）
#   done          把提示词里通道的状态改成「完成」并提交
#   quota-epoch   输出 "usage limit reached|$STUB_RESET" 并退出 1
#   quota-text    输出带「3 小时 2 分钟」的额度文案并退出 1
#   quota-plain   输出不带时间的额度文案并退出 1
#   fail          输出 boom 并退出 2
# 探测调用（提示词是 "Reply with the single word ok"）：quota-* → 额度错误；fail → 退出 2；否则输出 ok。
name=$(basename "$0")
echo "$name $*" | tr '\n' ' ' >> "$STUB_DIR/calls.log"; echo >> "$STUB_DIR/calls.log"
mode=$(cat "$STUB_DIR/$name.mode" 2>/dev/null || echo ok)
wd="$PWD"; prev=""
for a in "$@"; do [ "$prev" = "--cd" ] && wd="$a"; prev="$a"; done

emit_quota() {
  case "$1" in
    quota-epoch) echo "Claude AI usage limit reached|$STUB_RESET";;
    quota-text) echo "You've hit your usage limit. Try again in 3 hours 2 minutes.";;
    quota-plain) echo "You've hit your usage limit.";;
  esac
  exit 1
}

case "$*" in
  *"Reply with the single word ok"*)
    case "$mode" in quota-*) emit_quota "$mode";; fail) echo boom; exit 2;; esac
    echo ok; exit 0;;
esac

case "$mode" in
  quota-*) emit_quota "$mode";;
  fail) echo boom; exit 2;;
  ok)
    echo "$RANDOM$RANDOM" > "$wd/work-$name.txt"
    (cd "$wd" && git add -A && git -c user.email=t@t -c user.name=t commit -qm "work by $name")
    echo done; exit 0;;
  done)
    ch=$(echo "$*" | sed -n 's/.*通道 \*\*\([^*]*\)\*\*.*/\1/p' | head -1)
    sed -i '' "s/^## \[$ch\] 状态: [^ ]*/## [$ch] 状态: 完成/" "$wd/docs/PROGRESS.md"
    (cd "$wd" && git add -A && git -c user.email=t@t -c user.name=t commit -qm "complete $ch")
    echo done; exit 0;;
esac
