package com.example.course_schedule_for_chd_v002.util

/**
 * [v126] 教室 tag 集合 ↔ JSON 数组字符串的转换（纯 Kotlin 手写，与 JsonUtils/parseLocationMemory
 * 同范式——org.json 在 JVM 单测不可用，转换逻辑必须可测）。
 *
 * 用于：
 * - Room TypeConverter（checkin_locations.linkedRoomNames 列）
 * - MIGRATION_2_3 旧单值搬数据
 */
object RoomsJson {

    /** ["WM2303","WM1403"]；空集合 → [] */
    fun encode(rooms: List<String>): String {
        if (rooms.isEmpty()) return "[]"
        return "[" + rooms.joinToString(",") { "\"${escape(it)}\"" } + "]"
    }

    /** 解析 JSON 数组字符串；格式异常时尽力解析（与 parseLocationMemory 的容错风格一致），至少返回空集合 */
    fun decode(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        val content = json.trim().removeSurrounding("[", "]")
        if (content.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        var i = 0
        while (i < content.length) {
            val start = content.indexOf('"', i)
            if (start == -1) break
            // 找闭合引号（跳过被转义的 \"）
            var end = start + 1
            val sb = StringBuilder()
            while (end < content.length) {
                val c = content[end]
                if (c == '\\' && end + 1 < content.length) {
                    sb.append(content[end + 1])
                    end += 2
                    continue
                }
                if (c == '"') break
                sb.append(c)
                end++
            }
            if (end >= content.length) break
            val room = sb.toString().trim()
            if (room.isNotEmpty()) result.add(room)
            i = end + 1
        }
        return result
    }

    /** [v126] 迁移转换：旧单值 → 单元素 JSON 数组；null/空白 → 空数组 */
    fun fromSingleRoom(single: String?): String {
        val room = single?.trim().orEmpty()
        return if (room.isEmpty()) "[]" else encode(listOf(room))
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")
}
