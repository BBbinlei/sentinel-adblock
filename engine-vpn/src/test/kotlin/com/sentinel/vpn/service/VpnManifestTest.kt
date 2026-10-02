package com.sentinel.vpn.service

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Element
import kotlin.test.*

class VpnManifestTest {
    @Test fun `R05-01 SentinelVpnService explicitly disables always on VPN`() {
        val file = listOf(File("src/main/AndroidManifest.xml"), File("engine-vpn/src/main/AndroidManifest.xml"))
            .first { it.isFile }
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(file)
        val android = "http://schemas.android.com/apk/res/android"
        val services = document.getElementsByTagName("service")
        val service = (0 until services.length).map { services.item(it) as Element }
            .single { it.getAttributeNS(android, "name") == SentinelVpnService::class.java.name }
        val entries = service.childNodes
        val meta = (0 until entries.length).mapNotNull { entries.item(it) as? Element }
            .single { it.tagName == "meta-data" &&
                it.getAttributeNS(android, "name") == "android.net.VpnService.SUPPORTS_ALWAYS_ON" }
        assertEquals("false", meta.getAttributeNS(android, "value"))
        assertEquals("android.permission.BIND_VPN_SERVICE", service.getAttributeNS(android, "permission"))
    }
}
