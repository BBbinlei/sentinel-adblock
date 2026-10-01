#!/bin/bash
# 监督脚本自测（不消耗真实额度）。
set -e
cd "$(dirname "$0")"
python3 -c "import sys; sys.path.insert(0,'..'); import classify as c; p=c.load_patterns('../quota-patterns.json'); \
assert c.classify(1,'usage limit reached|1800003600',5,1800000000,p)['reset_at']==1800003690; \
assert not c.classify(1,'compile error',5,1800000000,p)['quota']; print('classify ok')"
python3 test_supervisor.py
