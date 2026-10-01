#!/usr/bin/env python3
"""判断一次 CLI 运行是否因为额度耗尽失败，并尽量解析额度刷新时间。

classify(returncode, text, duration_sec, now, patterns) -> {"quota": bool, "reset_at": int|None}
纯函数，不碰文件系统；时间一律由调用方传入（now 为 epoch 秒）。
"""
import json
import re
import time
from datetime import datetime, timedelta, timezone


def load_patterns(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def _any(regexes, text):
    return any(re.search(r, text, re.I) for r in regexes)


def parse_reset(text, now, horizon, slack):
    """按优先级尝试几种常见格式，返回未来的 epoch 秒，解析不到返回 None。"""
    cands = []

    # Claude 旧格式: "usage limit reached|1760000000"
    for m in re.finditer(r"\|\s*(\d{10})\b", text):
        cands.append(int(m.group(1)))

    # ISO 时间
    for m in re.finditer(r"(\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}(?:\.\d+)?)(Z|[+-]\d{2}:?\d{2})?", text):
        raw = m.group(1).replace(" ", "T")
        tz = m.group(2)
        try:
            dt = datetime.fromisoformat(raw.split(".")[0])
            if tz and tz != "Z":
                sign = 1 if tz[0] == "+" else -1
                digits = tz[1:].replace(":", "")
                dt = dt.replace(tzinfo=timezone(sign * timedelta(hours=int(digits[:2]), minutes=int(digits[2:]))))
            elif tz == "Z":
                dt = dt.replace(tzinfo=timezone.utc)
            else:
                dt = dt.astimezone()  # 无时区视为本地
                dt = datetime.fromisoformat(raw.split(".")[0]).replace(tzinfo=dt.tzinfo)
            cands.append(int(dt.timestamp()))
        except ValueError:
            pass

    # Retry-After 秒数
    for m in re.finditer(r"retry-?after\D{0,3}(\d+)", text, re.I):
        cands.append(now + int(m.group(1)))

    # "in 3 hours 2 minutes" / "in 45 minutes" / "in 2h 5m"
    for m in re.finditer(
        r"\bin\s+(?:(\d+)\s*(?:h|hours?|hrs?))?\s*(?:and\s+)?(?:(\d+)\s*(?:m|mins?|minutes?))?(?![a-z])",
        text, re.I,
    ):
        h, mi = m.group(1), m.group(2)
        if h or mi:
            cands.append(now + int(h or 0) * 3600 + int(mi or 0) * 60)

    # "resets at 3pm" / "resets 15:30" / "resets at 3:30 am"
    for m in re.finditer(r"resets?\s*(?:at\s*)?(\d{1,2})(?::(\d{2}))?\s*(am|pm)?", text, re.I):
        hh, mm, ap = int(m.group(1)), int(m.group(2) or 0), (m.group(3) or "").lower()
        if ap == "pm" and hh < 12:
            hh += 12
        if ap == "am" and hh == 12:
            hh = 0
        if hh > 23 or mm > 59:
            continue
        base = datetime.fromtimestamp(now)
        t = base.replace(hour=hh, minute=mm, second=0, microsecond=0)
        if t.timestamp() <= now:
            t += timedelta(days=1)
        cands.append(int(t.timestamp()))

    future = [c for c in cands if now < c <= now + horizon]
    return (min(future) + slack) if future else None


def classify(returncode, text, duration_sec, now, patterns):
    tail = text[-patterns.get("tail_bytes", 6000):]
    strong = _any(patterns["strong_regexes"], tail)
    weak = _any(patterns["weak_regexes"], tail)

    quota = False
    if returncode != 0 and (strong or weak):
        quota = True
    elif (
        returncode == 0
        and strong
        and duration_sec <= patterns.get("strong_max_runtime_sec", 120)
        and len(text) <= patterns.get("strong_max_log_bytes", 6000)
    ):
        # 有些 CLI 在额度耗尽时仍以 0 退出，但输出很短且包含明确的额度文案
        quota = True

    reset_at = None
    if quota:
        reset_at = parse_reset(tail, now, patterns.get("max_reset_horizon_sec", 691200),
                               patterns.get("reset_slack_sec", 90))
    return {"quota": quota, "reset_at": reset_at}


if __name__ == "__main__":
    import sys
    pats = load_patterns(sys.argv[1])
    data = sys.stdin.read()
    print(json.dumps(classify(int(sys.argv[2]), data, 0, int(time.time()), pats), ensure_ascii=False))
