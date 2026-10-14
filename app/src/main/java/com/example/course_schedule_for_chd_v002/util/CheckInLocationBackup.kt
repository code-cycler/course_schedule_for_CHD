package com.example.course_schedule_for_chd_v002.util

import com.example.course_schedule_for_chd_v002.domain.model.CheckInLocation

/**
 * [v126] 签到位置数据备份序列化器（grill W01 Q3：迁移前自动备份；F005 位置导出/导入共用）。
 *
 * 格式（手写 JSON，项目范式）：
 * {"locations":[{"id":1,"name":"...","latitude":34.0,"longitude":108.0,"accuracyRadiusMeters":10.5,
 *   "note":"...","linkedCourseId":"...","linkedRoomNames":["WM2303"],"createdAt":1700000000000}],
 *  "locationMemoryV1":"<旧格式原文，可能为空>"}
 */
object CheckInLocationBackup {

    fun serialize(locations: List<CheckInLocation>, locationMemoryRaw: String? = null): String {
        val locationJsons = locations.joinToString(",") { loc ->
            buildString {
                append("{\"id\":${loc.id}")
                append(",\"name\":\"${escape(loc.name)}\"")
                append(",\"latitude\":${loc.latitude}")
                append(",\"longitude\":${loc.longitude}")
                append(",\"accuracyRadiusMeters\":")
                append(loc.accuracyRadiusMeters?.toString() ?: "null")
                append(",\"note\":")
                append(loc.note?.let { "\"${escape(it)}\"" } ?: "null")
                append(",\"linkedCourseId\":")
                append(loc.linkedCourseId?.let { "\"${escape(it)}\"" } ?: "null")
                append(",\"linkedRoomNames\":${RoomsJson.encode(loc.linkedRoomNames)}")
                append(",\"createdAt\":${loc.createdAt}")
                append("}")
            }
        }
        val memoryRaw = locationMemoryRaw?.let { "\"${escape(it)}\"" } ?: "null"
        return "{\"locations\":[$locationJsons],\"locationMemoryV1\":$memoryRaw}"
    }

    /** 解析备份（导入用）；解析失败抛异常由调用方决定回退策略 */
    fun deserialize(json: String): Pair<List<CheckInLocation>, String?> {
        val locationsJson = extractTopLevelValue(json, "\"locations\":")
            ?: throw IllegalArgumentException("备份缺少 locations 字段")
        val locations = parseLocations(locationsJson)
        val memoryRaw = extractTopLevelValue(json, "\"locationMemoryV1\":")
            ?.let { raw ->
                if (raw.trim() == "null") null else raw.trim().removeSurrounding("\"").unescape()
            }
        return locations to memoryRaw
    }

    private fun parseLocations(content: String): List<CheckInLocation> {
        val body = content.trim().removeSurrounding("[", "]")
        if (body.isEmpty()) return emptyList()
        // 按对象边界切分（顶层花括号配平）
        val objects = mutableListOf<String>()
        var depth = 0
        var start = -1
        var inString = false
        for (i in body.indices) {
            val c = body[i]
            if (c == '"' && (i == 0 || body[i - 1] != '\\')) inString = !inString
            if (inString) continue
            if (c == '{') {
                if (depth == 0) start = i
                depth++
            } else if (c == '}') {
                depth--
                if (depth == 0 && start >= 0) {
                    objects.add(body.substring(start, i + 1))
                    start = -1
                }
            }
        }
        return objects.map { parseLocation(it) }
    }

    private fun parseLocation(obj: String): CheckInLocation {
        fun str(field: String): String? =
            extractField(obj, field)?.let { raw ->
                if (raw.trim() == "null") null else raw.trim().removeSurrounding("\"").unescape()
            }
        fun dbl(field: String): Double =
            extractField(obj, field)?.trim()?.toDoubleOrNull()
                ?: throw IllegalArgumentException("字段 $field 缺失或非法")
        fun flt(field: String): Float? = extractField(obj, field)?.trim()?.let {
            if (it == "null") null else it.toFloatOrNull()
        }
        fun lng(field: String): Long = extractField(obj, field)?.trim()?.toLongOrNull() ?: 0L
        return CheckInLocation(
            id = lng("id"),
            name = str("name") ?: "未命名位置",
            latitude = dbl("latitude"),
            longitude = dbl("longitude"),
            accuracyRadiusMeters = flt("accuracyRadiusMeters"),
            note = str("note"),
            linkedCourseId = str("linkedCourseId"),
            linkedRoomNames = extractField(obj, "linkedRoomNames")
                ?.let { RoomsJson.decode(it) } ?: emptyList(),
            createdAt = lng("createdAt")
        )
    }

    /** 取「"field":」之后到顶层下一个字段/结尾的原文片段 */
    private fun extractField(obj: String, field: String): String? {
        val key = "\"$field\":"
        val idx = obj.indexOf(key)
        if (idx == -1) return null
        return extractValueFrom(obj, idx + key.length)
    }

    private fun extractTopLevelValue(json: String, key: String): String? {
        val idx = json.indexOf(key)
        if (idx == -1) return null
        return extractValueFrom(json, idx + key.length)
    }

    /** 从 start 开始提取一个值（数组/字符串/数字/null），到顶层逗号或结尾 */
    private fun extractValueFrom(s: String, start: Int): String? {
        var i = start
        while (i < s.length && s[i] == ' ') i++
        if (i >= s.length) return null
        val first = s[i]
        return when {
            first == '[' -> extractBracketed(s, i, '[', ']')
            first == '{' -> extractBracketed(s, i, '{', '}')
            first == '"' -> {
                var j = i + 1
                val sb = StringBuilder()
                while (j < s.length) {
                    val c = s[j]
                    if (c == '\\' && j + 1 < s.length) {
                        sb.append(c).append(s[j + 1])
                        j += 2
                        continue
                    }
                    if (c == '"') break
                    sb.append(c)
                    j++
                }
                sb.insert(0, '"').append('"').toString()
            }
            else -> {
                var j = i
                while (j < s.length && s[j] != ',' && s[j] != '}' && s[j] != ']') j++
                s.substring(i, j)
            }
        }
    }

    private fun extractBracketed(s: String, start: Int, open: Char, close: Char): String {
        var depth = 0
        var inString = false
        for (i in start until s.length) {
            val c = s[i]
            if (c == '"' && (i == 0 || s[i - 1] != '\\')) inString = !inString
            if (inString) continue
            if (c == open) depth++
            else if (c == close) {
                depth--
                if (depth == 0) return s.substring(start, i + 1)
            }
        }
        return s.substring(start)
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    private fun String.unescape(): String =
        replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n")
}
