package com.sentinel.system.fakes

import com.sentinel.system.shell.ShizukuState
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.*

class FakeDeviceTest {
    @Test fun fake_command_formats_and_snapshot_roundtrip() = runTest {
        val d = FakeDevice()
        d.settings["secure" to "recommend"] = "1"
        d.installedPackages["com.example.ads"] = true
        d.enabledPackages["com.example.ads"] = true
        d.appops["com.example.reader" to "READ_CLIPBOARD"] = "allow"
        val before = d.snapshot()
        assertEquals("1\n", d.exec("settings get secure recommend").stdout)
        assertTrue(d.exec("settings put secure recommend 0").ok)
        assertEquals("0\n", d.exec("settings get secure recommend").stdout)
        d.exec("settings put secure recommend 1")
        d.exec("settings put global new_key 2")
        d.exec("settings delete global new_key")
        assertEquals("null\n", d.exec("settings get global new_key").stdout)
        d.exec("pm disable-user --user 0 com.example.ads")
        assertEquals("package:com.example.ads\n", d.exec("pm list packages -d").stdout)
        assertEquals("", d.exec("pm list packages -e").stdout)
        d.exec("pm enable --user 0 com.example.ads")
        d.exec("pm uninstall -k --user 0 com.example.ads")
        assertEquals("", d.exec("pm list packages com.example.ads").stdout)
        assertEquals("package:com.example.ads\n", d.exec("pm list packages -u").stdout)
        d.exec("pm install-existing --user 0 com.example.ads")
        d.exec("pm uninstall -k --user 0 com.example.ads")
        d.exec("cmd package install-existing --user 0 com.example.ads")
        d.exec("appops set com.example.reader READ_CLIPBOARD ignore")
        assertEquals("READ_CLIPBOARD: ignore\n", d.exec("appops get com.example.reader READ_CLIPBOARD").stdout)
        d.exec("appops set com.example.reader READ_CLIPBOARD allow")
        assertEquals(before, d.snapshot())
        d.failures += "settings put secure recommend 0"
        assertFalse(d.exec("settings put secure recommend 0").ok)
        assertFalse(d.exec("not-a-supported-command").ok)
        d.state.value = ShizukuState.NOT_RUNNING
        assertEquals(-2, d.exec("settings put secure recommend 0").exitCode)
        assertEquals(before, d.snapshot())
    }
}
