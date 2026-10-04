#!/usr/bin/env python3
"""Read-only offline evidence extraction: python3 extract-bytecode.py APP APK DEXDUMP OUTPUT."""
import pathlib, re, subprocess, sys, tempfile, zipfile

app, apk, dexdump, output = sys.argv[1:]
selection = {
    'yangshipin': (['classes.dex', 'classes9.dex', 'classes10.dex'],
        r'L(?:com/cctv/yangshipin/app/androidp/ad/zadimpl/ZSplash|com/cmg/ads/|n/a/a/|com/tencent/videolite/android/(?:preload/splash/LoadHomeSplashHelper|ui/SplashVideoDownManager|ui/SplashActivity|ui/fragment/splash/cms/CMSSplashView))'),
    'cainiao': (['classes5.dex', 'classes9.dex', 'classes10.dex', 'classes12.dex'],
        r'Lcom/cainiao/wireless/(?:homepage/rpc/rtb/|homepage/splash/|ads/component/AdSplashConfigComponent|ads/utils/c;|ads/bean/SplashForbidShakeAdsBean|components/hybrid/windvane/CNHybridForbidSplashShakeApi|ads/util/(?:MSSplashManager|UBIXSplashManager|YLHSplashManager|YKSplashManager))')
}
dexes, pattern = selection[app]
def redact(value):
    value = re.sub(r'(?i)((?:vsecret|appkey|api[_-]?key|access[_-]?token|secret|token)=)[^&\s"\\]+', r'\1[REDACTED]', value)
    return re.sub(r'(?i)(/appkey/)[^/\s"\\]+', r'\1[REDACTED]', value)
with zipfile.ZipFile(apk) as archive, tempfile.TemporaryDirectory(prefix='sentinel-dex-') as temp, pathlib.Path(output).open('w') as dest:
    for dex in dexes:
        path = pathlib.Path(temp) / dex
        path.write_bytes(archive.read(dex))
        process = subprocess.Popen([dexdump, '-d', str(path)], stdout=subprocess.PIPE, text=True, errors='replace')
        block, keep = [], False
        for line in process.stdout:
            if line.startswith('Class #'):
                if keep:
                    dest.write('DEX: ' + dex + '\n' + redact(''.join(block)))
                block, keep = [], False
            block.append(line)
            if 'Class descriptor' in line and re.search(pattern, line):
                keep = True
        if keep:
            dest.write('DEX: ' + dex + '\n' + redact(''.join(block)))
        if process.wait() != 0:
            raise SystemExit('dexdump failed for ' + dex)
