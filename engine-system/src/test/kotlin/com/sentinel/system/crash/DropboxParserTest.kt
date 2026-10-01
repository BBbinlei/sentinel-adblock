package com.sentinel.system.crash

import java.time.Instant
import java.util.TimeZone
import org.junit.Test
import kotlin.test.*

class DropboxParserTest {
    // UT-SY-6-01
    @Test fun UT_SY_6_01_parse_each_timestamp_and_process_package() {
        // 合成 dumpsys 文本，非实机采集；替换要求与时区假设见 README。
        val output = requireNotNull(javaClass.getResource("/dropbox_sample.txt")).readText()
        val oldZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val entries = DropboxParser.parse(output)
            assertEquals(2, entries.size)
            assertEquals(setOf(
                CrashEntry(Instant.parse("2026-10-01T08:30:00Z").toEpochMilli(), "com.example.reader"),
                CrashEntry(Instant.parse("2026-10-01T08:31:45Z").toEpochMilli(), "com.example.player"),
            ), entries.toSet())
        } finally {
            TimeZone.setDefault(oldZone)
        }
    }
    // UT-SY-6-02
    @Test fun UT_SY_6_02_no_crashes_returns_empty_list() {
        assertEquals(emptyList(), DropboxParser.parse(""))
        assertEquals(emptyList(), DropboxParser.parse("Drop box contents: 0 entries\nNo entries found.\n"))
    }
}
