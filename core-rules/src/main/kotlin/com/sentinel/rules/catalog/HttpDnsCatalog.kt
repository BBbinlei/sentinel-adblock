package com.sentinel.rules.catalog

import com.sentinel.rules.model.*
import java.net.InetAddress

data class Cidr(val address: ByteArray, val prefix: Int) {
    init { require(address.size in listOf(4, 16) && prefix in 0..address.size * 8) }
    fun contains(ip: ByteArray): Boolean {
        if (ip.size != address.size) return false
        val full = prefix / 8
        for (i in 0 until full) if (ip[i] != address[i]) return false
        val remainder = prefix % 8
        val mask = (0xff shl (8 - remainder)) and 0xff
        return remainder == 0 || (ip[full].toInt() and mask) == (address[full].toInt() and mask)
    }
    companion object {
        fun parse(s: String): Cidr {
            val parts = s.split('/')
            require(parts.size == 2) { "Invalid CIDR" }
            val raw = parts[0]
            val address = if (':' in raw) {
                require(raw.all { it in "0123456789abcdefABCDEF:" }) { "Expected numeric IPv6" }
                try { InetAddress.getByName(raw).address } catch (e: java.net.UnknownHostException) {
                    throw IllegalArgumentException("Invalid IPv6", e)
                }
            } else {
                val octets = raw.split('.')
                require(octets.size == 4 && octets.all { it.isNotEmpty() && it.all(Char::isDigit) && it.toIntOrNull() in 0..255 })
                octets.map { it.toInt().toByte() }.toByteArray()
            }
            val prefix = requireNotNull(parts[1].toIntOrNull()) { "Invalid prefix" }
            require(prefix in 0..address.size * 8)
            for (i in address.indices) {
                val bits = (prefix - i * 8).coerceIn(0, 8)
                address[i] = (address[i].toInt() and (0xff shl (8 - bits))).toByte()
            }
            return Cidr(address, prefix)
        }
    }
}

object HttpDnsCatalog {
    val domains: List<String> = listOf(
        "yyapp-httpdns.gslb.yy.com",
        "union-httpdns.gslb.yy.com",
        "httpdns-v6.gslb.yy.com",
        "dns.weixin.qq.com",
        "dns.weixin.qq.com.cn",
        "aedns.weixin.qq.com",
        "paydns.wechatpay.cn",
        "httpdns.kg.qq.com",
        "httpdns.alicdn.com",
        "httpdns-api.aliyuncs.com",
        "httpdns-sc.aliyuncs.com",
        "httpdns.danuoyi.tbcache.com",
        "httpdns.baidu.com",
        "httpsdns.baidu.com",
        "httpdns.n.shifen.com",
        "httpdns.bcelive.com",
        "httpdns.baidubce.com",
        "dns.iqiyi.com",
        "doh.iqiyi.com",
        "dns.qiyipic.iqiyi.com",
        "httpdns.music.163.com",
        "httpdns.n.netease.com",
        "httpdns-sdk.n.netease.com",
        "lofter.httpdns.c.163.com",
        "music.httpdns.c.163.com",
        "httpdns.browser.miui.com",
        "resolver.msg.xiaomi.net",
        "httpdns.huaweicloud.com",
        "httpdns.c.cdnhwc2.com",
        "httpdns.platform.dbankcloud.cn",
        "httpdns.platform.dbankcloud.com",
        "httpdns1.cc.cdnhwc5.com",
        "httpdns-browser.platform.dbankcloud.cn",
        "httpdns.volcengineapi.com",
        "dig.bdurl.net",
        "dig.zjurl.cn",
        "dns2.q2cdn.com",
        "httpdns.bilivideo.com",
        "httpdns.cctv.com",
        "doh.ptqy.gitv.tv",
        "httpdns.ocloud.heytapmobi.com",
        "httpdns.push.heytapmobi.com",
        "dns.jd.com",
        "dotserver.douyucdn.cn",
        "hdns.ksyun.com",
        "httpdns.push.oppomobile.com",
        "httpdns.ocloud.oppomobile.com",
        "kuaishou.httpdns.pro",
        "httpdns.kwd.inkuai.com",
        "apidns.kwd.inkuai.com",
        "apidns-js.kwd.inkuai.com",
        "httpdns.meituan.com",
        "httpdnsmultiapivip.meituan.com",
        "httpdns.yunxindns.com",
        "httpdns.zybang.com",
        "httpdns.calorietech.com",
        "dns.weibo.cn",
        "hd.xiaojukeji.com"
    )
    // 原清单 118.89.204.198/23 已规范化为网络边界 118.89.204.0/23。
    val cidrs: List<Cidr> = listOf(
        "8.134.241.67/32",
        "39.156.140.30/32",
        "39.156.140.47/32",
        "39.156.140.245/32",
        "42.81.232.18/32",
        "42.187.182.106/32",
        "42.187.182.123/32",
        "42.187.184.154/32",
        "43.130.30.237/32",
        "43.130.30.240/32",
        "43.137.153.151/32",
        "43.137.159.31/32",
        "43.152.112.101/32",
        "43.153.248.120/32",
        "60.28.172.100/32",
        "61.151.231.157/32",
        "101.32.104.104/32",
        "101.124.19.122/32",
        "106.39.206.21/32",
        "106.39.206.25/32",
        "106.39.206.70/32",
        "111.31.201.194/32",
        "111.31.241.76/32",
        "111.31.241.140/32",
        "111.206.147.156/32",
        "111.206.147.210/32",
        "111.206.148.27/32",
        "116.128.177.249/32",
        "116.130.224.150/32",
        "116.130.224.205/32",
        "117.185.247.73/32",
        "119.29.29.98/32",
        "119.29.29.99/32",
        "123.151.48.171/32",
        "123.151.48.193/32",
        "123.151.48.208/32",
        "123.151.54.50/32",
        "180.153.202.85/32",
        "183.192.196.31/32",
        "186.76.76.200/32",
        "203.107.1.0/24",
        "203.205.129.102/32",
        "203.205.234.132/32",
        "220.196.159.73/32",
        "39.97.130.51/32",
        "39.97.128.148/32",
        "59.111.239.61/32",
        "59.111.239.62/32",
        "115.236.121.51/32",
        "115.236.121.195/32",
        "118.89.204.0/23",
        "103.224.222.208/32",
        "240e:928:1400:10::25/128",
        "2402:4e00:8030:1::17/128",
        "2402:4e00:1900:1700:0:9554:1ad9:c3a/128",
        "2408:8711:10:10::20/128",
        "2409:8702:4860:10::4d/128",
        "2402:db40:5100:1011::5/128",
        "2402:4e00:1200:ed00:0:9089:6dac:96b6/128"
    ).map(Cidr::parse)
    fun rules(): List<DnsRule> = domains.map { DnsRule("dns:$it", it, DomainTag.HTTPDNS, RuleLevel.STANDARD, "builtin") }
}
