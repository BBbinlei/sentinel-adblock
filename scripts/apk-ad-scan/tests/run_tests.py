#!/usr/bin/env python3
"""仅标准库、合成 strings 和内存构造 DEX；不读取真实 APK。"""

import importlib.util
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import zipfile

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
SCRIPT = HERE.parent / "scan.py"
spec = importlib.util.spec_from_file_location("apk_ad_scan", SCRIPT)
scanner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(scanner)


def synthetic_dex(values):
    data = bytearray(112 + len(values) * 4)
    data[:8] = b"dex\n035\0"
    struct.pack_into("<II", data, 56, len(values), 112)
    for index, value in enumerate(values):
        struct.pack_into("<I", data, 112 + index * 4, len(data))
        length = len(value)
        while length >= 128:
            data.append((length & 127) | 128)
            length >>= 7
        data.append(length)
        data.extend(value.encode("utf-8") + b"\0")
    struct.pack_into("<III", data, 32, len(data), 112, 0x12345678)
    return bytes(data)


def cli(path, *options):
    return subprocess.run([sys.executable, str(SCRIPT), str(path), *options],
                          capture_output=True, text=True, timeout=15)


def main():
    sample = HERE / "sample-strings.txt"
    hosts = scanner.scan(sample)
    expected = set("c2.gdt.qq.com mi.gdt.qq.com mobads.baidu.com mobads-logs.baidu.com "
                   "pgdt.ugdtimg.com adsmind.ugdtimg.com "
                   "api-access.pangolin-sdk-toutiao.com cdn.pglstatp-toutiao.com "
                   "adx-cfg-u1.ubixioe.com bid-adx2.vlion.cn api-v3.mentamob.com "
                   "sdk.zhangyuyidong.cn sdk.1rtb.net api.adukwai.com api.sigmob.cn".split())
    actual = {host for host in hosts if scanner.classify(host)[0] == scanner.CONFIRMED}
    assert actual == expected, (actual - expected, expected - actual)
    protected = set("pan.baidu.com baidu.com www.baidu.com httpsdns.baidu.com "
                    "mobads-pre-config.cdn.bcebos.com miniapp-ad.cdn.bcebos.com "
                    "download.baidupcs.com cdn.bdstatic.com safe.example.net "
                    "normal.example.zip normal.example.so xn--fsqu00a.xn--0zwm56d".split())
    assert protected <= hosts.keys(), protected - hosts.keys()
    assert all(scanner.classify(host)[0] == scanner.PROTECTED for host in protected)
    suspected = set("ads.new-vendor.net bid.new-vendor.net splash.new-vendor.net "
                    "union.new-vendor.net sdk.mintegral.example.net "
                    "sdk.beizi.example.net sdk.qumeng.example.net "
                    "c2.gdt.qq.com.evil.net mobads.baidu.com.evil.net notgdt.qq.com "
                    "sdk.zhangyuyidong.cn.evil.net child.sdk.zhangyuyidong.cn".split())
    assert suspected <= hosts.keys(), suspected - hosts.keys()
    assert all(scanner.classify(host)[0] == scanner.SUSPECTED for host in suspected)
    assert set(hosts) == expected | protected | suspected, set(hosts) - (expected | protected | suspected)
    assert hosts['c2.gdt.qq.com'] == 'sample-strings.txt:1'
    assert len(scanner.SDK_FEATURES) == 12
    for sdk, suffixes, exact, keywords, basis in scanner.SDK_FEATURES:
        assert sdk and basis and (suffixes or exact or keywords)
        for root in suffixes:
            assert scanner.classify(root)[0] == scanner.CONFIRMED, root
            assert scanner.classify('sub.' + root)[0] == scanner.CONFIRMED, root
            assert scanner.classify(root + '.evil.net')[0] != scanner.CONFIRMED, root
        for host in exact:
            assert scanner.classify(host)[0] == scanner.CONFIRMED, host
            assert scanner.classify('sub.' + host)[0] != scanner.CONFIRMED, host
    text = cli(sample)
    assert text.returncode == 0, text.stderr
    rows = [line.split('\t') for line in text.stdout.splitlines()]
    assert all(len(row) == 5 and row[3] and row[4] for row in rows)
    assert [row[0] for row in rows] == sorted(hosts)
    domains = cli(sample, '--format', 'domains')
    assert domains.returncode == 0, domains.stderr
    assert domains.stdout == ''.join(host + '\n' for host in sorted(expected))
    assert not domains.stderr
    print('PASS: strings 分类、第一方保护、后缀/精确边界、URL/IDNA、文本与纯域名 CLI')

    # 所有临时样本都在授权目录内，退出时自动清理。
    with tempfile.TemporaryDirectory(prefix='synthetic-', dir=HERE) as directory:
        directory = Path(directory)
        apk = directory / 'sample.apk'
        with zipfile.ZipFile(apk, 'w') as archive:
            archive.writestr('classes.dex', synthetic_dex(['pgdt.ugdtimg.com'.ljust(51)]))
            archive.writestr('classes2.dex', synthetic_dex([' ' * 130 + 'sdk.zhangyuyidong.cn']))
            archive.writestr('assets/not-a-dex.txt', 'ads.hidden.example.net')
        parsed = scanner.scan(apk)
        assert parsed == {'pgdt.ugdtimg.com': 'classes.dex:string@0',
                          'sdk.zhangyuyidong.cn': 'classes2.dex:string@0'}, parsed
        result = cli(apk, '--format', 'domains')
        assert result.returncode == 0, result.stderr
        assert result.stdout == 'pgdt.ugdtimg.com\nsdk.zhangyuyidong.cn\n'
        print('PASS: 多 DEX、ULEB128、长度字节不混入主机名、仅扫描 DEX')

        broken = bytearray(synthetic_dex(['c2.gdt.qq.com']))
        struct.pack_into('<I', broken, 112, len(broken) + 1)
        # 合法 DEX 在前、损坏 DEX 在后，失败不得输出先前的确认结果。
        with zipfile.ZipFile(apk, 'w') as archive:
            archive.writestr('classes.dex', synthetic_dex(['c2.gdt.qq.com']))
            archive.writestr('classes2.dex', broken)
        result = cli(apk, '--format', 'domains')
        assert result.returncode == 2 and not result.stdout and '越界' in result.stderr
        for content in [b'not a zip', None]:
            if content is None:
                with zipfile.ZipFile(apk, 'w') as archive:
                    archive.writestr('assets/a.txt', 'c2.gdt.qq.com')
            else:
                apk.write_bytes(content)
            result = cli(apk)
            assert result.returncode == 2 and not result.stdout and result.stderr
        result = cli(directory / 'missing.apk')
        assert result.returncode == 2 and not result.stdout
        result = cli(sample, '--format', 'unknown')
        assert result.returncode == 2 and not result.stdout
        empty = directory / 'empty.txt'
        empty.write_text('pan.baidu.com\nconfig.json\n')
        result = cli(empty, '--format', 'domains')
        assert result.returncode == 0 and not result.stdout
        print('PASS: 损坏 APK/DEX、无 DEX、缺失输入/无效参数、失败无部分清单、空确认集')
    print('全部自测通过（无真实 APK、无网络、无第三方依赖）')


if __name__ == '__main__':
    main()
