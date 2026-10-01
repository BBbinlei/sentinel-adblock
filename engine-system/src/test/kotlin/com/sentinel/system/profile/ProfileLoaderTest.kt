package com.sentinel.system.profile

import org.junit.Test
import kotlin.test.*

class ProfileLoaderTest {
    private fun sample() = requireNotNull(javaClass.getResource("/profile_sample.json")).readText()

    // UT-SY-1-01
    @Test fun UT_SY_1_01_parse_all_profile_fields() {
        val p = ProfileLoader.parse(sample())
        assertEquals("V15.0", p.romPrefix)
        assertEquals("BACKGROUND_POPUP_TEST", p.backgroundPopupOp)
        assertEquals(2, p.ops.size)
        assertEquals(ProfileOp(
            "pictorial-recommend", 1, "关闭画报推荐", OpKind.SETTING, true,
            "settings put secure test_recommend 0", "settings put secure test_recommend {before}",
            "settings get secure test_recommend", "^0$",
        ), p.ops[0])
        assertEquals(ProfileOp(
            id = "assistant-cards", trick = 2, title = "关闭负一屏卡片", kind = OpKind.WIZARD,
            verified = false, optional = true,
            intent = SettingsIntent("android.settings.SETTINGS", "com.android.settings", "com.android.settings.Settings"),
        ), p.ops[1])
    }
    // UT-SY-1-02
    @Test fun UT_SY_1_02_select_longest_prefix_independent_of_order() {
        val broad = ColorOsProfile("V15", ops = emptyList())
        val narrow = ColorOsProfile("V15.0.1", ops = emptyList())
        val other = ColorOsProfile("V16", ops = emptyList())
        assertEquals(narrow, ProfileLoader.select("V15.0.1.500", listOf(broad, narrow, other)))
        assertEquals(narrow, ProfileLoader.select("V15.0.1.500", listOf(narrow, other, broad)))
        assertEquals(broad, ProfileLoader.select("V15.2", listOf(narrow, broad)))
    }
    // UT-SY-1-03
    @Test fun UT_SY_1_03_unmatched_rom_returns_null() {
        assertNull(ProfileLoader.select("V16.0", listOf(ColorOsProfile("V15", ops = emptyList()))))
        assertNull(ProfileLoader.select("V15.0", emptyList()))
    }
    // UT-SY-1-04
    @Test fun UT_SY_1_04_preserve_before_placeholders() {
        val json = sample().replace("settings put secure test_recommend 0", "settings put secure test_recommend {before}")
        val op = ProfileLoader.parse(json).ops.first()
        assertEquals("settings put secure test_recommend {before}", op.apply)
        assertEquals("settings put secure test_recommend {before}", op.revert)
    }
}
