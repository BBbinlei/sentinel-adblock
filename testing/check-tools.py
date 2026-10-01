"""离线检查：python3 testing/check-tools.py。adb 完全由本地替身替代。"""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent


def run(script, *args, env):
    return subprocess.run(["bash", str(script), *args], env=env,
                          capture_output=True, text=True)


with tempfile.TemporaryDirectory() as directory:
    tmp = Path(directory)
    bin_dir = tmp / "bin"
    bin_dir.mkdir()
    adb = bin_dir / "adb"
    adb.write_text('''#!/usr/bin/env python3
import os, pathlib, sys
args = sys.argv[1:]
state = pathlib.Path(os.environ["FAKE_STATE"])
if args[:3] == ["shell", "am", "broadcast"]:
    assert args[3:] == ["-a", "com.sentinel.a11y.DEBUG_DUMP", "-p",
                       "com.sentinel.adblock", "--es", "name", "sample-001"]
    state.write_text("recorded")
elif args[0] == "shell" and "find" in args[1]:
    if state.exists():
        print("/sdcard/Android/data/com.sentinel.adblock/files/snapshots/com.example.fixture/sample-001.json")
elif args[0] == "pull":
    assert args[1].endswith("/com.example.fixture/sample-001.json")
    pathlib.Path(args[2]).write_text('{"text":"跳过 3"}')
else:
    raise SystemExit("unexpected adb command: " + repr(args))
''')
    adb.chmod(0o755)
    env = dict(os.environ, PATH=str(bin_dir) + os.pathsep + os.environ["PATH"],
               FAKE_STATE=str(tmp / "recorded"))
    regression = tmp / "rule-regression"
    (regression / "scripts").mkdir(parents=True)
    (regression / "fixtures/snapshots").mkdir(parents=True)
    snapshot = regression / "scripts/pull-snapshot.sh"
    shutil.copyfile(ROOT / "rule-regression/scripts/pull-snapshot.sh", snapshot)
    assert run(snapshot, "../escape", env=env).returncode == 2
    result = run(snapshot, "sample-001", env=env)
    assert result.returncode == 0, result.stderr
    saved = regression / "fixtures/snapshots/com.example.fixture/sample-001.json"
    assert saved.read_text() == '{"text":"跳过 3"}'
    assert run(snapshot, "sample-001", env=env).returncode != 0
    print("snapshot: pull, path validation, stale-file refusal passed")
