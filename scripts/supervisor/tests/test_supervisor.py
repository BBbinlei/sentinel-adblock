#!/usr/bin/env python3
"""监督脚本的自测：用假 claude/codex 在临时 git 仓库里跑，不消耗任何真实额度。"""
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
SUPERVISOR = HERE.parent / "supervisor.py"
STUB = HERE / "stubs" / "agent-stub.sh"
T0 = 1_800_000_000


class Env:
    """一个临时仓库 + 假 CLI。"""

    def __init__(self, channels, progress):
        self.tmp = Path(tempfile.mkdtemp(prefix="sup-test-"))
        self.root = self.tmp / "repo"
        self.stubs = self.tmp / "bin"
        self.stubdir = self.tmp / "stubstate"
        for d in (self.root / "docs", self.stubs, self.stubdir):
            d.mkdir(parents=True)
        for n in ("codex", "claude"):
            shutil.copy(str(STUB), str(self.stubs / n))
            os.chmod(str(self.stubs / n), 0o755)
        shutil.copy(str(HERE.parent.parent.parent / "docs" / "codex-channel-prompt.md"), str(self.root / "docs"))
        (self.root / "docs" / "PROGRESS.md").write_text(progress, encoding="utf-8")
        cfg = {
            "caps": {"codex": 3, "claude": 1}, "max_runtime_sec": 60, "max_consecutive_failures": 3,
            "agents": {
                "codex": {"cmd": ["codex", "--cd", "{workdir}", "{prompt}"], "probe": ["codex", "Reply with the single word ok"]},
                "claude": {"cmd": ["claude", "-p", "{prompt}"], "probe": ["claude", "Reply with the single word ok"]},
            },
            "channels": channels,
        }
        self.cfg_path = self.tmp / "channels.json"
        self.cfg_path.write_text(json.dumps(cfg, ensure_ascii=False), encoding="utf-8")
        self.git("init", "-q")
        self.git("add", "-A")
        self.git("commit", "-qm", "init")
        self.now = T0

    def git(self, *a):
        return subprocess.run(["git", "-c", "user.email=t@t", "-c", "user.name=t"] + list(a), cwd=str(self.root),
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE, universal_newlines=True)

    def mode(self, agent, value):
        (self.stubdir / (agent + ".mode")).write_text(value, encoding="utf-8")

    def run(self, *args):
        env = dict(os.environ)
        env.update({"SUP_ROOT": str(self.root), "SUP_STATE_DIR": str(self.root / ".supervisor"),
                    "SUP_CHANNELS": str(self.cfg_path), "SUP_NOW": str(self.now),
                    "SUP_NO_NOTIFY": "1", "SUP_NO_CAFFEINATE": "1", "SUP_NO_LAUNCHD": "1",
                    "STUB_DIR": str(self.stubdir), "STUB_RESET": str(T0 + 3600),
                    "PATH": str(self.stubs) + os.pathsep + env["PATH"]})
        r = subprocess.run([sys.executable, str(SUPERVISOR)] + list(args), env=env, cwd=str(self.root),
                           stdout=subprocess.PIPE, stderr=subprocess.STDOUT, universal_newlines=True, timeout=120)
        return r.stdout

    def tick(self, *extra):
        return self.run("tick", "--sync", *extra)

    def state(self):
        return json.loads((self.root / ".supervisor" / "state.json").read_text(encoding="utf-8"))

    def calls(self):
        p = self.stubdir / "calls.log"
        return p.read_text(encoding="utf-8").splitlines() if p.exists() else []

    def notifications(self):
        p = self.root / ".supervisor" / "notifications.log"
        return p.read_text(encoding="utf-8") if p.exists() else ""

    def cleanup(self):
        shutil.rmtree(str(self.tmp), ignore_errors=True)


def progress(*pairs):
    lines = ["# 进度表", ""]
    for name, st, owner in pairs:
        lines += ["## [%s] 状态: %s | 负责方: %s | 关卡: G0" % (name, st, owner), "", "- 下一步：x", ""]
    return "\n".join(lines)


def ch(name, owner="codex", **kw):
    d = {"name": name, "module": name, "owner": owner, "worktree": ".", "depends_on": [], "write_scope": "x", "instructions": "do it"}
    d.update(kw)
    return d


class SupervisorTests(unittest.TestCase):
    def setUp(self):
        self.envs = []

    def tearDown(self):
        for e in self.envs:
            e.cleanup()

    def mk(self, channels, prog):
        e = Env(channels, prog)
        self.envs.append(e)
        return e

    def simple(self):
        return self.mk([ch("a")], progress(("a", "待办", "codex")))

    # 1 ------------------------------------------------------------------
    def test_normal_run_makes_progress(self):
        e = self.simple()
        e.tick()
        self.assertEqual(len([c for c in e.calls() if c.startswith("codex")]), 1)
        st = e.state()
        self.assertEqual(st["channels"]["a"]["fails"], 0)
        self.assertEqual(st["quota_count"].get("codex", 0), 0)

    # 2 ------------------------------------------------------------------
    def test_quota_with_reset_time_blocks_and_fails_over(self):
        e = self.simple()
        e.mode("codex", "quota-epoch")
        e.tick()
        st = e.state()
        self.assertEqual(st["blocked_until"]["codex"], T0 + 3600 + 90)  # 解析出的刷新时间 + 缓冲
        self.assertFalse(st["needs_probe"]["codex"])
        e.mode("claude", "ok")
        e.tick()  # 同一时刻：codex 仍耗尽 → 接力给 claude
        claude_calls = [c for c in e.calls() if c.startswith("claude")]
        self.assertEqual(len(claude_calls), 1)
        self.assertIn("接力", claude_calls[0])
        self.assertEqual(e.state()["channels"]["a"]["owner"], "claude")
        self.assertIn("接力：a → claude", e.notifications())

    # 3 ------------------------------------------------------------------
    def test_both_exhausted_launches_nothing_then_resumes_without_taking_back(self):
        e = self.simple()
        e.mode("codex", "quota-epoch")
        e.mode("claude", "quota-epoch")
        e.tick()      # codex 耗尽
        e.tick()      # 接力给 claude，claude 也耗尽
        n = len(e.calls())
        out = e.tick()  # 两方都耗尽
        self.assertEqual(len(e.calls()), n, out)
        self.assertIn("没有可运行的通道", e.notifications())
        # 额度刷新后继续；负责方保持 claude，不自动抢回
        e.mode("codex", "ok")
        e.mode("claude", "ok")
        e.now = T0 + 3600 + 91
        e.tick()
        last = e.calls()[-1]
        self.assertTrue(last.startswith("claude"), last)

    # 4 ------------------------------------------------------------------
    def test_unparseable_quota_uses_backoff_then_probe(self):
        e = self.simple()
        e.mode("codex", "quota-plain")
        e.tick()
        st = e.state()
        self.assertEqual(st["blocked_until"]["codex"], T0 + 1800)
        self.assertTrue(st["needs_probe"]["codex"])
        # 让 claude 在整个测试期间不可用，隔离出 codex 自己的恢复路径（否则通道会被接力给 claude）
        st["blocked_until"]["claude"] = T0 + 10 ** 7
        (e.root / ".supervisor" / "state.json").write_text(json.dumps(st), encoding="utf-8")
        probe = "Reply with the single word ok"

        def codex():
            return [c for c in e.calls() if c.startswith("codex")]

        # 退避期内：不启动
        e.now = T0 + 100
        n = len(codex())
        e.tick()
        self.assertEqual(len(codex()), n)
        # 退避期满、仍然耗尽：只做一次探测，不跑真任务，退避加长
        e.now = T0 + 1801
        e.tick()
        new = codex()[n:]
        self.assertEqual(len(new), 1)
        self.assertIn(probe, new[0])
        self.assertEqual(e.state()["blocked_until"]["codex"], T0 + 1801 + 3600)
        # 恢复后：先探测成功，再跑真任务
        e.mode("codex", "ok")
        e.now = T0 + 1801 + 3601
        n = len(codex())
        e.tick()
        new = codex()[n:]
        self.assertEqual(len(new), 2, new)
        self.assertIn(probe, new[0])
        self.assertNotIn(probe, new[1])
        self.assertFalse(e.state()["needs_probe"]["codex"])
        self.assertEqual(e.state()["quota_count"]["codex"], 0)

    # 5 ------------------------------------------------------------------
    def test_three_failures_without_progress_halt_channel(self):
        e = self.simple()
        e.mode("codex", "fail")
        for _ in range(3):
            e.tick()
        st = e.state()
        self.assertTrue(st["channels"]["a"]["halted"])
        self.assertIn("需人工", e.notifications())
        n = len(e.calls())
        e.tick()
        self.assertEqual(len(e.calls()), n)
        e.run("resume", "a")
        e.mode("codex", "ok")
        e.tick()
        self.assertGreater(len(e.calls()), n)

    # 6 ------------------------------------------------------------------
    def test_dependencies_and_completion(self):
        e = self.mk([ch("a"), ch("b", depends_on=["a"])], progress(("a", "待办", "codex"), ("b", "待办", "codex")))
        e.mode("codex", "done")
        e.tick()
        prompts = [c for c in e.calls() if c.startswith("codex")]
        self.assertTrue(all("通道 **a**" in p for p in prompts[:1]))
        # a 完成后同一轮之后 b 才能启动；下一次 tick 启动 b，且项目完成
        e.tick()
        self.assertTrue(any("通道 **b**" in c for c in e.calls()))
        e.tick()
        self.assertTrue((e.root / ".supervisor" / "DONE").exists())

    # 7 ------------------------------------------------------------------
    def test_dry_run_has_no_side_effects(self):
        e = self.simple()
        out = e.tick("--dry-run")
        self.assertIn("DRY: 启动 a ← codex", out)
        self.assertEqual(e.calls(), [])

    # 8 ------------------------------------------------------------------
    def test_lock_prevents_overlap(self):
        import fcntl
        e = self.simple()
        (e.root / ".supervisor").mkdir()
        f = open(str(e.root / ".supervisor" / "tick.lock"), "a+")
        fcntl.flock(f, fcntl.LOCK_EX)
        try:
            out = e.tick()
        finally:
            f.close()
        self.assertIn("已有另一个 tick", out)
        self.assertEqual(e.calls(), [])

    # 9 ------------------------------------------------------------------
    def test_user_channel_is_never_run(self):
        e = self.mk([ch("s", owner="用户", kind="user")], progress(("s", "待办", "用户")))
        e.tick()
        self.assertEqual(e.calls(), [])
        self.assertIn("等用户", e.notifications())

    # 10 -----------------------------------------------------------------
    def test_waiting_channel_resumes_only_after_wait_for_merged(self):
        e = self.mk([ch("x"), ch("w", wait_for=["x"])], progress(("x", "待办", "codex"), ("w", "等待", "codex")))
        e.mode("codex", "ok")
        out = e.tick()
        self.assertFalse(any("通道 **w**" in c for c in e.calls()), out)


if __name__ == "__main__":
    unittest.main(verbosity=2)
