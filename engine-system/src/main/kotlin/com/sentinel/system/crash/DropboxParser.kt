package com.sentinel.system.crash

import java.text.SimpleDateFormat
import java.util.Locale

data class CrashEntry(val ts: Long, val pkg: String)

object DropboxParser {
    private val header = Regex("^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})\\s+\\S+\\s+\\(")
    private val process = Regex("^Process:\\s*([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+)(?::\\S*)?\\s*$")

    /** ts 为 epoch 毫秒；dumpsys 的日期无时区，按 JVM 默认时区解析。 */
    fun parse(output: String): List<CrashEntry> {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { isLenient = false }
        val result = mutableListOf<CrashEntry>()
        var ts: Long? = null
        for (line in output.lineSequence()) {
            val h = header.find(line)
            if (h != null) {
                ts = runCatching { format.parse(h.groupValues[1])?.time }.getOrNull()
                continue
            }
            val current = ts ?: continue
            val p = process.find(line.trim()) ?: continue
            result += CrashEntry(current, p.groupValues[1])
            ts = null
        }
        return result
    }
}
