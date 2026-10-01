#!/usr/bin/env python3
"""哨兵项目的无人值守监督器（Python 3.9 兼容）。

由 launchd 每 10 分钟触发一次 `tick`：短命、幂等，所有状态落盘在 .supervisor/。
- 一方额度耗尽 → 通道转给另一方（接力）；两方都耗尽 → 本次不启动任何东西。
- 额度刷新时间优先从报错文案里解析；解析不到就按退避序列等待，并在恢复前先做一次极小的探测调用。
- 脚本绝不 push、绝不改远端；真机类任务（kind=user）只通知，不执行。

用法：
  supervisor.py tick [--dry-run] [--sync]
  supervisor.py status
  supervisor.py resume <通道>      # 清除「需人工」状态，允许重试
环境变量（主要给测试用）：SUP_ROOT SUP_STATE_DIR SUP_CHANNELS SUP_PATTERNS SUP_PROMPT SUP_NOW
                          SUP_NO_NOTIFY SUP_NO_CAFFEINATE SUP_NO_LAUNCHD
"""
import argparse
import fcntl
import hashlib
import json
import os
import re
import shutil
import signal
import subprocess
import sys
import time
from contextlib import contextmanager
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import classify as cls  # noqa: E402

ROOT = Path(os.environ.get("SUP_ROOT", str(HERE.parents[1]))).resolve()
SUP = Path(os.environ.get("SUP_STATE_DIR", str(ROOT / ".supervisor")))
CFG_PATH = Path(os.environ.get("SUP_CHANNELS", str(HERE / "channels.json")))
PAT_PATH = Path(os.environ.get("SUP_PATTERNS", str(HERE / "quota-patterns.json")))
PROMPT_PATH = Path(os.environ.get("SUP_PROMPT", str(ROOT / "docs" / "codex-channel-prompt.md")))

TERMINAL = {"完成", "已合并"}
STATUS_RE = re.compile(r"^##\s*\[(?P<name>[^\]]+)\]\s*状态[:：]\s*(?P<st>[^\s|]+)", re.M)
OTHER = {"codex": "claude", "claude": "codex"}


def now():
    return int(os.environ["SUP_NOW"]) if "SUP_NOW" in os.environ else int(time.time())


def log(msg):
    print("[%s] %s" % (time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(now())), msg), flush=True)


# ---------------------------------------------------------------- 状态文件

def default_state():
    return {"blocked_until": {}, "quota_count": {}, "needs_probe": {}, "channels": {}, "notified": {}}


@contextmanager
def state_lock():
    SUP.mkdir(parents=True, exist_ok=True)
    f = open(str(SUP / "state.lock"), "a+")
    try:
        fcntl.flock(f, fcntl.LOCK_EX)
        yield
    finally:
        fcntl.flock(f, fcntl.LOCK_UN)
        f.close()


def load_state():
    p = SUP / "state.json"
    if p.exists():
        try:
            st = json.loads(p.read_text(encoding="utf-8"))
            for k, v in default_state().items():
                st.setdefault(k, v)
            return st
        except ValueError:
            pass
    return default_state()


def save_state(st):
    tmp = SUP / "state.json.tmp"
    tmp.write_text(json.dumps(st, ensure_ascii=False, indent=2), encoding="utf-8")
    os.replace(str(tmp), str(SUP / "state.json"))


def mutate(fn):
    with state_lock():
        st = load_state()
        r = fn(st)
        save_state(st)
        return r


def chstate(st, name):
    return st["channels"].setdefault(name, {"pid": None, "fails": 0, "halted": False})


# ---------------------------------------------------------------- 配置与进度

def load_cfg():
    return json.loads(CFG_PATH.read_text(encoding="utf-8"))


def by_name(cfg):
    return {c["name"]: c for c in cfg["channels"]}


def is_root_channel(ch):
    return ch.get("worktree", ".") in (".", "")


def workdir(ch):
    return ROOT if is_root_channel(ch) else (ROOT / ch["worktree"]).resolve()


def read_status(progress_file, name):
    try:
        text = Path(progress_file).read_text(encoding="utf-8")
    except OSError:
        return None
    for m in STATUS_RE.finditer(text):
        if m.group("name") == name:
            return m.group("st")
    return None


def status_of(ch):
    """通道当前状态。main 上标「已合并」优先；直接在 main 上做的通道，完成即视为已合并。"""
    name = ch["name"]
    root_st = read_status(ROOT / "docs" / "PROGRESS.md", name)
    if root_st == "已合并":
        return "已合并"
    if is_root_channel(ch):
        st = root_st or "待办"
        return "已合并" if (st == "完成" and ch.get("kind") != "merge") else st
    wd = workdir(ch)
    if wd.exists():
        return read_status(wd / "docs" / "PROGRESS.md", name) or root_st or "待办"
    return root_st or "待办"


def git(args, cwd, check=False):
    r = subprocess.run(["git"] + args, cwd=str(cwd), stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                       universal_newlines=True)
    if check and r.returncode != 0:
        raise RuntimeError("git %s: %s" % (" ".join(args), r.stderr.strip()))
    return r


def git_head(cwd):
    r = git(["rev-parse", "HEAD"], cwd)
    return r.stdout.strip() if r.returncode == 0 else None


def tag_exists(tag):
    return git(["rev-parse", "-q", "--verify", "refs/tags/" + tag], ROOT).returncode == 0


def ensure_worktree(ch, dry):
    """通道目录不存在时，从 base_ref 建 worktree + 分支 chan/<name>。成功/已存在返回 True。"""
    if is_root_channel(ch):
        return True
    wd = workdir(ch)
    if wd.exists():
        return True
    base = ch.get("base_ref", "HEAD")
    if base != "HEAD" and base != "main" and not tag_exists(base):
        return False
    if dry:
        log("DRY: 将创建 worktree %s（分支 chan/%s，基于 %s）" % (wd, ch["name"], base))
        return False
    branch = "chan/" + ch["name"]
    if git(["rev-parse", "-q", "--verify", "refs/heads/" + branch], ROOT).returncode == 0:
        r = git(["worktree", "add", str(wd), branch], ROOT)
    else:
        r = git(["worktree", "add", "-b", branch, str(wd), base], ROOT)
    if r.returncode != 0:
        log("创建 worktree 失败 %s: %s" % (ch["name"], r.stderr.strip()))
        return False
    log("已创建 worktree %s（分支 %s，基于 %s）" % (wd, branch, base))
    return True


# ---------------------------------------------------------------- 通知

def notify(key, title, msg, st=None):
    """同一个 key 只通知一次（st 给出时在其中记录）。"""
    if st is not None:
        if st["notified"].get(key):
            return
        st["notified"][key] = now()
    line = "%s | %s | %s" % (time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(now())), title, msg)
    SUP.mkdir(parents=True, exist_ok=True)
    with open(str(SUP / "notifications.log"), "a", encoding="utf-8") as f:
        f.write(line + "\n")
    log("通知: " + title + " — " + msg)
    if os.environ.get("SUP_NO_NOTIFY"):
        return
    try:
        script = 'display notification "%s" with title "%s"' % (msg.replace('"', "'"), title.replace('"', "'"))
        subprocess.run(["osascript", "-e", script], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=10)
    except Exception:
        pass


# ---------------------------------------------------------------- 额度与可用性

def blocked(st, agent):
    return now() < st["blocked_until"].get(agent, 0)


def block_agent(st, agent, reset_at, pats):
    """记录某个 agent 额度耗尽。reset_at 为 None 时用退避序列并要求恢复前先探测。"""
    n = st["quota_count"].get(agent, 0)
    seq = pats.get("backoff_sec", [1800, 3600, 7200, 10800])
    if reset_at:
        until = reset_at
        st["needs_probe"][agent] = False
    else:
        until = now() + seq[min(n, len(seq) - 1)]
        st["needs_probe"][agent] = True
    st["quota_count"][agent] = n + 1
    st["blocked_until"][agent] = until
    notify("quota-%s-%d" % (agent, until), "%s 额度耗尽" % agent,
           "预计 %s 恢复%s" % (time.strftime("%m-%d %H:%M", time.localtime(until)),
                              "（按退避估算）" if not reset_at else ""), st)
    return until


def run_capture(cmd, cwd, timeout):
    """运行命令，返回 (returncode, 输出文本, 秒数)。"""
    t0 = time.time()
    try:
        r = subprocess.run(cmd, cwd=str(cwd), stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                           stderr=subprocess.STDOUT, universal_newlines=True, timeout=timeout)
        return r.returncode, r.stdout or "", time.time() - t0
    except subprocess.TimeoutExpired as e:
        out = e.stdout.decode("utf-8", "replace") if isinstance(e.stdout, bytes) else (e.stdout or "")
        return 124, out, time.time() - t0
    except FileNotFoundError as e:
        return 127, str(e), 0.0


def probe_agent(cfg, pats, agent):
    """额度退避期满后，先用极小调用确认是否真的恢复。返回 True=可用。"""
    cmd = cfg["agents"][agent]["probe"]
    rc, text, dur = run_capture(cmd, ROOT, 180)
    res = cls.classify(rc, text, dur, now(), pats)
    if res["quota"]:
        mutate(lambda st: block_agent(st, agent, res["reset_at"], pats))
        log("探测：%s 仍然额度耗尽" % agent)
        return False
    if rc != 0:
        log("探测：%s 探测命令失败（退出码 %s），本次不使用它" % (agent, rc))
        return False

    def ok(st):
        st["needs_probe"][agent] = False
        st["quota_count"][agent] = 0
    mutate(ok)
    log("探测：%s 已恢复" % agent)
    return True


# ---------------------------------------------------------------- 运行一次通道任务

def render_prompt(ch, agent, takeover_from):
    tpl = PROMPT_PATH.read_text(encoding="utf-8")
    mod = ch.get("module", "")
    if mod.startswith("("):
        plans = "本通道没有独立模块计划，见下面的任务说明与 `docs/PARALLEL_SCHEDULE.md`、`scripts/check-merge.sh`。"
    else:
        plans = "`%s/README.md`、`%s/PLAN.md`（你要实施的模块）。" % (mod, mod)
    takeover = ""
    if takeover_from:
        takeover = ("**【接力】** 本通道原由 %s 负责，因其额度耗尽，现由你（%s）接手。严格遵守 `docs/HANDOFF.md` 第 4 节："
                    "先跑测试了解真实进度，只补未完成的 Task，不重写已通过测试的代码；额度恢复后不会自动交还。"
                    % (takeover_from, agent))
    values = {"channel": ch["name"], "module": mod, "agent": agent, "workdir": str(workdir(ch)),
              "takeover": takeover, "plans": plans, "instructions": ch.get("instructions", ""),
              "write_scope": ch.get("write_scope", "本通道目录")}
    for k, v in values.items():
        tpl = tpl.replace("{{%s}}" % k, v)
    return tpl


def build_cmd(cfg, agent, ch, prompt):
    cmd = [a.replace("{workdir}", str(workdir(ch))).replace("{prompt}", prompt) for a in cfg["agents"][agent]["cmd"]]
    if shutil.which("caffeinate") and not os.environ.get("SUP_NO_CAFFEINATE"):
        cmd = ["caffeinate", "-i"] + cmd
    return cmd


def run_job(name, agent, takeover_from):
    """实际调用 CLI 并在结束时回写状态。可在后台子进程里运行，也可由 --sync 直接调用。"""
    cfg = load_cfg()
    pats = cls.load_patterns(str(PAT_PATH))
    ch = by_name(cfg)[name]
    wd = workdir(ch)
    SUP.mkdir(parents=True, exist_ok=True)
    (SUP / "logs").mkdir(exist_ok=True)
    logfile = SUP / "logs" / ("%s-%s-%d.log" % (name, agent, now()))
    head0, status0 = git_head(wd), status_of(ch)

    def mark_running(st):
        c = chstate(st, name)
        c.update({"pid": os.getpid(), "agent": agent, "owner": agent, "started": now(), "log": str(logfile)})
    mutate(mark_running)

    cmd = build_cmd(cfg, agent, ch, render_prompt(ch, agent, takeover_from))
    log("启动 %s ← %s（目录 %s，日志 %s）" % (name, agent, wd, logfile.name))
    max_rt = cfg.get("max_runtime_sec", 14400)
    t0 = time.time()
    rc = 0
    try:
        with open(str(logfile), "w", encoding="utf-8") as lf:
            p = subprocess.Popen(cmd, cwd=str(wd), stdin=subprocess.DEVNULL, stdout=lf, stderr=subprocess.STDOUT,
                                 start_new_session=True)
            try:
                rc = p.wait(timeout=max_rt)
            except subprocess.TimeoutExpired:
                os.killpg(os.getpgid(p.pid), signal.SIGTERM)
                try:
                    p.wait(timeout=30)
                except subprocess.TimeoutExpired:
                    os.killpg(os.getpgid(p.pid), signal.SIGKILL)
                rc = 124
    except FileNotFoundError as e:
        rc = 127
        logfile.write_text(str(e), encoding="utf-8")
    duration = time.time() - t0

    try:
        text = logfile.read_text(encoding="utf-8", errors="replace")
    except OSError:
        text = ""
    res = cls.classify(rc, text, duration, now(), pats)
    head1, status1 = git_head(wd), status_of(ch)
    progressed = (head1 != head0) or (status1 != status0)
    max_fail = cfg.get("max_consecutive_failures", 3)

    def finish(st):
        c = chstate(st, name)
        c["pid"] = None
        if res["quota"]:
            block_agent(st, agent, res["reset_at"], pats)
            return
        st["quota_count"][agent] = 0
        if progressed:
            c["fails"] = 0
        else:
            c["fails"] = c.get("fails", 0) + 1
            if c["fails"] >= max_fail:
                c["halted"] = True
                notify("halt-%s-%d" % (name, now()), "通道 %s 需人工" % name,
                       "连续 %d 次运行没有任何进展（最后退出码 %s），已停止自动重试。日志 %s。修好后执行 supervisor.py resume %s"
                       % (c["fails"], rc, logfile.name, name), st)
        if status1 in TERMINAL and status0 not in TERMINAL:
            notify("done-%s-%s" % (name, status1), "通道 %s %s" % (name, status1), "关卡已通过，等待合并" if status1 == "完成" else "", st)
    mutate(finish)
    log("结束 %s ← %s：退出码 %s，%s%s" % (name, agent, rc, "额度耗尽" if res["quota"] else ("有进展" if progressed else "无进展"),
                                      "，预计恢复 %s" % time.strftime("%H:%M", time.localtime(res["reset_at"])) if res["reset_at"] else ""))
    return rc


# ---------------------------------------------------------------- tick

def pid_alive(pid):
    if not pid:
        return False
    try:
        os.kill(pid, 0)
        return True
    except ProcessLookupError:
        return False
    except PermissionError:
        return True


def deps_ok(ch, chans):
    need = {"已合并"} if ch.get("depends_merged") else TERMINAL
    return all(status_of(chans[d]) in need for d in ch.get("depends_on", []))


def runnable(ch, chans, st):
    """返回 (是否可跑, 原因)。"""
    kind = ch.get("kind", "work")
    if kind == "user":
        return False, "等用户"
    c = chstate(st, ch["name"])
    if c.get("halted"):
        return False, "需人工"
    if pid_alive(c.get("pid")):
        return False, "运行中"
    s = status_of(ch)
    if kind == "merge":
        pending = [x["name"] for x in chans.values()
                   if x.get("kind", "work") == "work" and not is_root_channel(x) and status_of(x) == "完成"]
        return (True, "待合并: " + ",".join(pending)) if pending else (False, "无待合并通道")
    if s in TERMINAL:
        return False, s
    if not deps_ok(ch, chans):
        return False, "依赖未满足"
    if s == "等待":
        w = ch.get("wait_for", [])
        if not all(status_of(chans[x]) == "已合并" for x in w):
            return False, "等待 " + ",".join(x for x in w if status_of(chans[x]) != "已合并")
    return True, s


def project_done(cfg):
    work = [c for c in cfg["channels"] if c.get("kind", "work") == "work"]
    return bool(work) and all(status_of(c) == "已合并" for c in work)


def cmd_tick(args):
    SUP.mkdir(parents=True, exist_ok=True)
    lockf = open(str(SUP / "tick.lock"), "a+")
    try:
        fcntl.flock(lockf, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        log("已有另一个 tick 在运行，退出")
        return 0

    cfg = load_cfg()
    pats = cls.load_patterns(str(PAT_PATH))
    chans = by_name(cfg)
    dry = args.dry_run

    # 清理已经死掉的 pid（比如重启后），不算失败
    def reap(st):
        for c in st["channels"].values():
            if c.get("pid") and not pid_alive(c["pid"]):
                c["pid"] = None
    if not dry:
        mutate(reap)

    # data 完成 → 打 g2-frozen（本地标签，不 push）
    if "data" in chans and status_of(chans["data"]) in TERMINAL and not tag_exists("g2-frozen"):
        if dry:
            log("DRY: 将打标签 g2-frozen")
        else:
            r = git(["tag", "g2-frozen"], ROOT)
            log("已打标签 g2-frozen" if r.returncode == 0 else "打标签失败: " + r.stderr.strip())

    if project_done(cfg):
        if not dry:
            (SUP / "DONE").write_text(str(now()), encoding="utf-8")
            mutate(lambda st: notify("project-done", "哨兵项目：全部通道已合并", "监督脚本将卸载自己", st))
            if not os.environ.get("SUP_NO_LAUNCHD"):
                subprocess.run(["bash", str(HERE / "uninstall.sh"), "--quiet"], check=False)
        log("所有通道已合并，结束")
        return 0

    st = load_state()
    running = {"codex": 0, "claude": 0}
    for c in st["channels"].values():
        if pid_alive(c.get("pid")) and c.get("agent") in running:
            running[c["agent"]] += 1
    caps = cfg.get("caps", {"codex": 3, "claude": 1})
    probed = {}
    started = 0
    waiting_reasons = []

    for ch in cfg["channels"]:
        ok, reason = runnable(ch, chans, st)
        if not ok:
            if reason not in ("运行中", "无待合并通道") and not reason.startswith("依赖"):
                waiting_reasons.append("%s:%s" % (ch["name"], reason))
            if ch.get("kind") == "user" and status_of(ch) not in TERMINAL and not dry:
                mutate(lambda s, n=ch["name"]: notify("user-" + n, "等用户：" + n, "需要你亲自操作，见 docs/PROGRESS.md", s))
            continue

        c = chstate(st, ch["name"])
        owner = c.get("owner") or ch["owner"]
        takeover_from = None
        agent = owner
        if blocked(st, owner):
            alt = OTHER.get(owner)
            if alt and not blocked(st, alt):
                agent, takeover_from = alt, owner
            else:
                waiting_reasons.append("%s:两方额度均耗尽" % ch["name"])
                continue

        # 退避期满后，使用前先探测一次
        if st["needs_probe"].get(agent):
            if dry:
                log("DRY: %s 需要先探测" % agent)
            else:
                if agent not in probed:
                    probed[agent] = probe_agent(cfg, pats, agent)
                    st = load_state()
                if not probed[agent]:
                    waiting_reasons.append("%s:%s 探测未通过" % (ch["name"], agent))
                    continue

        if running[agent] >= caps.get(agent, 1):
            continue
        if not ensure_worktree(ch, dry):
            waiting_reasons.append("%s:worktree 未就绪" % ch["name"])
            continue

        if dry:
            log("DRY: 启动 %s ← %s%s（%s）" % (ch["name"], agent, "（接力自 %s）" % takeover_from if takeover_from else "", reason))
            started += 1
            continue

        if takeover_from:
            mutate(lambda s, n=ch["name"], a=agent: chstate(s, n).update({"owner": a}))
            mutate(lambda s, n=ch["name"], a=agent, o=takeover_from:
                   notify("takeover-%s-%s-%d" % (n, a, now()), "接力：%s → %s" % (n, a), "%s 额度耗尽，由 %s 接手" % (o, a), s))
        running[agent] += 1
        started += 1
        if args.sync:
            run_job(ch["name"], agent, takeover_from)
            st = load_state()
        else:
            cmd = [sys.executable, str(Path(__file__).resolve()), "_run", ch["name"], agent, takeover_from or "-"]
            p = subprocess.Popen(cmd, cwd=str(ROOT), stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
                                 stderr=subprocess.DEVNULL, start_new_session=True, env=os.environ.copy())
            mutate(lambda s, n=ch["name"], a=agent, pid=p.pid: chstate(s, n).update({"pid": pid, "agent": a, "started": now()}))
            st = load_state()

    if started == 0 and not any(pid_alive(c.get("pid")) for c in st["channels"].values()) and waiting_reasons and not dry:
        sig = "|".join(sorted(waiting_reasons))
        mutate(lambda s: notify("idle-" + hashlib.md5(sig.encode("utf-8")).hexdigest()[:10], "监督脚本：没有可运行的通道", "；".join(waiting_reasons), s))
    log("tick 结束：启动 %d 个任务%s" % (started, "；阻塞/等待：" + "；".join(waiting_reasons) if waiting_reasons else ""))
    return 0


def cmd_status(_args):
    cfg = load_cfg()
    st = load_state()
    print("agent 可用性：")
    for a in ("codex", "claude"):
        until = st["blocked_until"].get(a, 0)
        print("  %-7s %s%s" % (a, "额度耗尽，预计 " + time.strftime("%m-%d %H:%M", time.localtime(until)) if blocked(st, a) else "可用",
                              "（恢复前需探测）" if st["needs_probe"].get(a) else ""))
    print("通道：")
    for ch in cfg["channels"]:
        c = st["channels"].get(ch["name"], {})
        flags = []
        if pid_alive(c.get("pid")):
            flags.append("运行中(%s)" % c.get("agent"))
        if c.get("halted"):
            flags.append("需人工")
        if c.get("fails"):
            flags.append("连续无进展 %d" % c["fails"])
        print("  %-14s 状态=%-4s 负责=%-7s %s" % (ch["name"], status_of(ch), c.get("owner") or ch["owner"], " ".join(flags)))
    return 0


def cmd_resume(args):
    def f(st):
        c = chstate(st, args.channel)
        c.update({"halted": False, "fails": 0})
    mutate(f)
    log("已清除 %s 的「需人工」标记" % args.channel)
    return 0


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    t = sub.add_parser("tick")
    t.add_argument("--dry-run", action="store_true")
    t.add_argument("--sync", action="store_true", help="同步执行（测试用）：每个任务跑完再继续")
    sub.add_parser("status")
    r = sub.add_parser("resume")
    r.add_argument("channel")
    j = sub.add_parser("_run")
    j.add_argument("channel")
    j.add_argument("agent")
    j.add_argument("takeover")
    args = ap.parse_args()
    if args.cmd == "tick":
        return cmd_tick(args)
    if args.cmd == "status":
        return cmd_status(args)
    if args.cmd == "resume":
        return cmd_resume(args)
    if args.cmd == "_run":
        return 0 if run_job(args.channel, args.agent, None if args.takeover == "-" else args.takeover) is not None else 1
    return 2


if __name__ == "__main__":
    sys.exit(main())
