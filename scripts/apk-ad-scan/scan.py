#!/usr/bin/env python3
"""离线扫描 APK 的 DEX string_ids，或逐行扫描已有 strings 文本。"""

import argparse
import ipaddress
from pathlib import Path
import re
import struct
import sys
from urllib.parse import urlsplit
import zipfile


CONFIRMED = "确认广告域名"
SUSPECTED = "疑似"
PROTECTED = "第一方/不能拦"

# 数据表：SDK、专用域后缀、精确主机、未核实关键词、离线依据。
# 不把某 SDK 使用过的共享主域推广为整域广告规则。
SDK_FEATURES = (
    ("穿山甲/字节广告", ("pangolin-sdk-toutiao.com", "pangolin-sdk-toutiao-b.com",
                       "pglstatp-toutiao.com"), (), ("pangolin",),
     "core-rules/PLAN.md Task 3、standard_domains.txt 的 AD_SDK 种子"),
    ("广点通/优量汇", ("gdt.qq.com", "gdtimg.com"),
     ("pgdt.ugdtimg.com", "adsmind.ugdtimg.com"), ("gdt", "ugdtimg"),
     "项目 AD_SDK 种子；ugdtimg 两个主机来自本任务人工确认列表"),
    ("百度联盟", ("mobads.baidu.com", "mobads-logs.baidu.com"), (), ("mobads",),
     "项目 AD_SDK 种子及本任务人工确认列表；不扩展到 baidu.com"),
    ("Ubix", (), ("adx-cfg-u1.ubixioe.com", "adx-data-u1.ubixioe.com",
                 "entry-su1.ubixioe.com"), ("ubix",), "本任务人工确认列表（精确主机）"),
    ("Vlion", (), ("bid-adx2.vlion.cn", "bj-td-01-callback.advlion.com",
                  "bj-td-03-callback.advlion.com", "api-v3.mentamob.com",
                  "api-gray-v3.mentamob.com"), ("vlion", "mentamob"),
     "人工确认 bid-adx2；网盘 classes24.dex 的 SmartVlionHttpUtil、"
     "HttpRequestUtil、cn/vlion/ad/inland/core/o 的 const-string"),
    ("Octopus", (), ("sdk.zhangyuyidong.cn", "sdklog.zhangyuyidong.cn"),
     ("octopus", "zhangyuyidong"),
     "网盘 classes29.dex 的 com/octopus/ad/internal/q 的 const-string"),
    ("美数", (), ("sdk.1rtb.net", "sdk-demo.1rtb.net", "sdk-report.1rtb.com",
                 "dsp.1rtb.com"), ("meishu", "1rtb"),
     "网盘 classes28.dex 的 com/meishu/sdk/core/{AdSdk,loader/c,exception/a,"
     "uri/a,utils/r0} 的 const-string"),
    ("快手联盟", ("adukwai.com",), (), ("adkwai", "adukwai", "kwad"),
     "core-rules/PLAN.md Task 3、standard_domains.txt 的 AD_SDK 种子"),
    ("Sigmob", ("sigmob.cn",), (), ("sigmob",),
     "core-rules/PLAN.md Task 3、standard_domains.txt 的 AD_SDK 种子"),
    ("Mintegral", (), (), ("mintegral", "mbridge"),
     "仅 SDK 名称特征；本次离线材料没有核实专用域名，命中只列疑似"),
    ("倍孜", (), (), ("beizi", "adxbid"),
     "仅 SDK 名称特征；本次离线材料没有核实专用域名，命中只列疑似"),
    ("趣盟", (), (), ("qumeng",),
     "仅 SDK 名称特征；本次离线材料没有核实专用域名，命中只列疑似"),
)

FIRST_PARTY = ("baidu.com", "baidu-int.com", "baidustatic.com", "bdstatic.com",
               "baidupcs.com", "bcebos.com", "bcehost.com", "baidubce.com")
URL = re.compile(r"[a-zA-Z][a-zA-Z0-9+.-]*://[^\s\"'<>\\]+")
LABEL = r"[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
BARE = re.compile(r"(?<![\w.@-])(?:" + LABEL + r"\.)+" + LABEL + r"\.?(?![\w.-])")
AD_WORD = re.compile(r"(?:^|[.\-])(?:ad|ads|adx|bid|splash|union|mobads|adserver|adsense)(?:[.\-]|\d|$)")
CODE_PREFIXES = ("com.", "org.", "net.", "android.", "androidx.", "java.",
                 "javax.", "kotlin.", "kotlinx.", "okhttp3.", "io.",
                 "window.", "document.", "mobadssdk.", "mobadssdkbridge.")


def within(host, suffix):
    return host == suffix or host.endswith("." + suffix)


def normalize(host):
    try:
        host = host.rstrip(".").encode("idna").decode("ascii").lower()
        ipaddress.ip_address(host)
        return None  # 清单仅含域名，不生成 IP 规则。
    except (ValueError, UnicodeError):
        pass
    if (len(host) > 253 or "." not in host or
            not all(re.fullmatch(LABEL, label) for label in host.split("."))):
        return None
    tld = host.rsplit(".", 1)[1]
    return host if re.fullmatch(r"[a-z]{2,63}|xn--[a-z0-9-]+", tld) else None


def extract_hosts(value, tlds):
    value = value.replace(r"\/", "/")
    for match in URL.finditer(value):
        try:
            host = normalize(urlsplit(match.group().rstrip("),;]}")).hostname or "")
        except ValueError:
            continue
        if host:
            yield host
    # URL 的路径/参数不是主机名；裸域名排除常见 Java/JS 命名空间。
    # ponytail: TLD + 语法仍不能区分同形标识符；需精确验证时补运行时请求证据。
    for match in BARE.finditer(URL.sub(" ", value)):
        raw = match.group().rstrip(".")
        if raw.lower().startswith(CODE_PREFIXES):
            continue
        host = normalize(raw)
        if host and host.rsplit(".", 1)[1] in tlds:
            yield host


def classify(host):
    # 专用 mobads 两项是唯一允许穿过百度主域保护的已核实广告特征。
    if any(within(host, root) for root in FIRST_PARTY):
        baidu = SDK_FEATURES[2]
        if any(within(host, root) for root in baidu[1]):
            root = next(root for root in baidu[1] if within(host, root))
            return CONFIRMED, baidu[0], "专用域后缀 " + root + "；" + baidu[4]
        return PROTECTED, "百度第一方/共享服务", "受保护主域；可能用于登录、网盘、下载或共享存储，不能整域拦截"
    for sdk, suffixes, exact, keywords, basis in SDK_FEATURES:
        for root in suffixes:
            if within(host, root):
                return CONFIRMED, sdk, "专用域后缀 " + root + "；" + basis
        if host in exact:
            return CONFIRMED, sdk, "精确主机 " + host + "；" + basis
    for sdk, suffixes, exact, keywords, basis in SDK_FEATURES:
        term = next((term for term in keywords if term in host), None)
        if term:
            return SUSPECTED, sdk, "未核实域名关键词 " + term + "；不自动拦截"
    word = AD_WORD.search(host)
    if word:
        return SUSPECTED, "未知 SDK", "广告词 " + word.group().strip(".-") + "；未命中已核实特征"
    return PROTECTED, "未判定", "无已核实广告特征；静态字符串不能证明是广告或可拦截"


def dex_strings(data):
    if (len(data) < 112 or not re.fullmatch(rb"dex\n0(?:3[5-9]|40)\x00", data[:8]) or
            struct.unpack_from("<III", data, 32) != (len(data), 112, 0x12345678)):
        raise ValueError("无效或不支持的 DEX 头（仅支持标准小端 DEX 035–040）")
    count, offset = struct.unpack_from("<II", data, 56)
    if count and (offset < 112 or offset + count * 4 > len(data)):
        raise ValueError("DEX string_ids 越界")
    for index in range(count):
        pos = struct.unpack_from("<I", data, offset + index * 4)[0]
        if not 112 <= pos < len(data):
            raise ValueError("DEX string_data 偏移越界")
        for byte_index in range(5):
            if pos >= len(data):
                raise ValueError("DEX 字符串长度截断")
            byte = data[pos]
            pos += 1
            if byte_index == 4 and byte > 15:
                raise ValueError("DEX ULEB128 长度溢出")
            if byte < 128:
                break
        end = data.find(b"\0", pos)
        if end < 0:
            raise ValueError("DEX 字符串缺少终止符")
        # 主机名的 ASCII 字节与 MUTF-8 相同；其余无效字符保留为分隔符。
        yield index, data[pos:end].decode("utf-8", errors="replace")


def input_strings(path):
    if path.suffix.lower() in (".apk", ".zip") or zipfile.is_zipfile(path):
        with zipfile.ZipFile(path) as archive:
            names = [name for name in archive.namelist()
                     if re.fullmatch(r"classes(?:[2-9]|[1-9][0-9]+)?\.dex", name)]
            if not names:
                raise ValueError("APK 中没有 classes*.dex")
            if len(names) != len(set(names)):
                raise ValueError("APK 中有重复 DEX 条目")
            for name in sorted(names, key=lambda n: int(n[7:-4] or "1")):
                try:
                    for index, value in dex_strings(archive.read(name)):
                        yield name + ":string@" + str(index), value
                except ValueError as error:
                    raise ValueError(name + ": " + str(error)) from error
    else:
        with path.open(encoding="utf-8", errors="replace") as source:
            for line, value in enumerate(source, 1):
                yield path.name + ":" + str(line), value


def scan(path):
    with Path(__file__).with_name("tlds.txt").open(encoding="utf-8") as source:
        tlds = {line.strip() for line in source if line.strip() and not line.startswith("#")}
    tlds.update(("example", "test", "invalid", "localhost"))
    hosts = {}
    for origin, value in input_strings(Path(path)):
        for host in extract_hosts(value, tlds):
            hosts.setdefault(host, origin)
    return hosts


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path, help="APK 路径或已提取的 strings 文本路径")
    parser.add_argument("--format", choices=("text", "domains"), default="text",
                        help="text：域名、分类、SDK、依据、首个位置（TAB 分隔）；domains：仅确认广告域名")
    args = parser.parse_args()
    try:
        hosts = scan(args.input)
    except (OSError, ValueError, zipfile.BadZipFile, RuntimeError, NotImplementedError) as error:
        parser.exit(2, "扫描失败：" + str(error) + "\n")
    for host, origin in sorted(hosts.items()):
        category, sdk, reason = classify(host)
        if args.format == "text":
            print("\t".join((host, category, sdk, reason, origin)))
        elif category == CONFIRMED:
            print(host)


if __name__ == "__main__":
    main()
