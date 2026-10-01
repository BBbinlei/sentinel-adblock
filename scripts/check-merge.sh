#!/bin/bash
# 合并前检查：在通道的 worktree（或 main）里运行，参数是模块名。
#   bash scripts/check-merge.sh engine-vpn            # 全部检查（含跑测试）
#   bash scripts/check-merge.sh engine-vpn --no-test  # 只做静态检查
# 只用 git / grep / gradle，Claude 和 Codex 都能直接运行。任何一项失败退出码为 1。
# 例外：ALLOW_FROZEN=1 允许冻结项有 diff（只用于已在 docs/CHANGE_REQUESTS.md 登记的向后兼容新增）。
set -u
MODULE="${1:-}"
RUN_TESTS=1
[ "${2:-}" = "--no-test" ] && RUN_TESTS=0
[ -z "$MODULE" ] && { echo "用法: $0 <模块名> [--no-test]"; exit 2; }
cd "$(git rev-parse --show-toplevel)" || exit 2
[ -d "$MODULE" ] || { echo "找不到模块目录 $MODULE"; exit 2; }

FAIL=0
fail() { echo "  ✗ $1"; FAIL=1; }
pass() { echo "  ✓ $1"; }

# 允许依赖的内部模块（依赖方向：app → engine-*/guard → data → core-rules）
case "$MODULE" in
  core-rules)   ALLOWED="" ;;
  data)         ALLOWED="core-rules" ;;
  guard|engine-*) ALLOWED="data core-rules" ;;
  app)          ALLOWED="data core-rules guard engine-vpn engine-a11y engine-notify engine-system" ;;
  *)            echo "未知模块 $MODULE"; exit 2 ;;
esac

echo "== 1. 冻结项（g2-frozen 之后 data/core-rules/版本目录/模块列表不得改动）"
if [ "$MODULE" = "core-rules" ] || [ "$MODULE" = "data" ]; then
  pass "本模块就是冻结前的工作对象，跳过"
elif ! git rev-parse -q --verify refs/tags/g2-frozen >/dev/null; then
  fail "没有 g2-frozen 标签，无法比较（data 完成后监督脚本会打标签）"
else
  CHANGED=$(git diff --name-only g2-frozen -- data core-rules gradle/libs.versions.toml settings.gradle.kts)
  if [ -z "$CHANGED" ]; then pass "无改动"
  elif [ "${ALLOW_FROZEN:-0}" = "1" ]; then pass "有改动但已声明 ALLOW_FROZEN=1："; echo "$CHANGED" | sed 's/^/      /'
  else fail "冻结项被修改："; echo "$CHANGED" | sed 's/^/      /'; fi
fi

echo "== 2. 依赖方向（$MODULE 只能依赖：${ALLOWED:-无}）"
BAD=""
for dep in $(grep -oE 'project\(":[a-z0-9:-]+"\)' "$MODULE/build.gradle.kts" 2>/dev/null | sed -E 's/project\("://; s/"\)//; s/^://'); do
  case "$dep" in testing:*|testing) continue ;; esac
  ok=0; for a in $ALLOWED; do [ "$dep" = "$a" ] && ok=1; done
  [ $ok -eq 0 ] && BAD="$BAD $dep"
done
[ -z "$BAD" ] && pass "依赖合法" || fail "出现不允许的依赖：$BAD"

echo "== 3. 纯 Kotlin 约束（core-rules 与 guard.policy 不得引用 android.*）"
PURE_DIRS=""
[ "$MODULE" = "core-rules" ] && PURE_DIRS="core-rules/src/main"
[ "$MODULE" = "guard" ] && PURE_DIRS="guard/src/main/kotlin/com/sentinel/guard/policy"
if [ -n "$PURE_DIRS" ] && [ -d "$PURE_DIRS" ]; then
  HITS=$(grep -rnE '^import (android|androidx)\.' "$PURE_DIRS" 2>/dev/null)
  [ -z "$HITS" ] && pass "无 android 引用" || { fail "纯 Kotlin 目录里引用了 android："; echo "$HITS" | sed 's/^/      /'; }
else pass "本模块无此约束"
fi

echo "== 4. 契约常量（引擎不得写奖励窗口/信号的字面量）"
case "$MODULE" in
  engine-*|guard)
    SRC="$MODULE/src/main"
    LIT=$(grep -rnEi '(reward.*(60_000|60000))|((60_000|60000).*reward)' "$SRC" 2>/dev/null)
    ADS=$(grep -rn 'AD_SDK' "$SRC" 2>/dev/null | grep -v 'RELEASED_TAGS' | grep -v '^.*://')
    if [ "$MODULE" = "engine-vpn" ]; then
      [ -z "$ADS" ] && pass "AD_SDK 全部经由 RELEASED_TAGS" || { fail "engine-vpn 直接写了 AD_SDK（应引用 RewardWindowContract.RELEASED_TAGS）："; echo "$ADS" | sed 's/^/      /'; }
    fi
    [ -z "$LIT" ] && pass "没有奖励窗口时长字面量" || { fail "奖励窗口时长写成了字面量（应引用 RewardWindowContract.TTL_MS）："; echo "$LIT" | sed 's/^/      /'; } ;;
  *) pass "本模块无此约束" ;;
esac

echo "== 5. 模块入口登记（ModuleEntry，见 docs/CONTRACTS.md C5）"
case "$MODULE" in
  engine-*|guard|data)
    REG="$MODULE/src/main/resources/META-INF/services/com.sentinel.data.module.ModuleEntry"
    if [ ! -f "$REG" ]; then fail "缺少 $REG"
    else
      CLS=$(grep -v '^\s*#' "$REG" | grep -v '^\s*$' | head -1 | sed 's/.*\.//')
      if grep -rqE "class[[:space:]]+$CLS\b" "$MODULE/src/main" 2>/dev/null; then pass "已登记 $CLS"; else fail "登记的类 $CLS 在源码里找不到"; fi
    fi ;;
  *) pass "本模块不导出入口" ;;
esac
if [ "$MODULE" != "app" ] && [ -d app/src/main ]; then
  case "$MODULE" in engine-*|guard)
    REF=$(grep -rnE "com\.sentinel\.(vpn|a11y|notify|system|guard)\." app/src/main --include=*.kt 2>/dev/null | head -3)
    [ -z "$REF" ] && pass "app 没有引用本模块的类" || { fail "app 代码引用了引擎/guard 的类（应经 ModuleEntry）："; echo "$REF" | sed 's/^/      /'; } ;;
  esac
fi

echo "== 6. 测试"
if [ $RUN_TESTS -eq 0 ]; then
  echo "  - 已跳过（--no-test）"
else
  if [ "$MODULE" = "core-rules" ]; then TASK=":core-rules:test"; else TASK=":$MODULE:testDebugUnitTest"; fi
  if ./gradlew "$TASK" -q; then pass "$TASK 通过"; else fail "$TASK 失败"; fi
fi

echo
if [ $FAIL -eq 0 ]; then echo "check-merge: $MODULE 全部通过"; exit 0; else echo "check-merge: $MODULE 有未通过项"; exit 1; fi
