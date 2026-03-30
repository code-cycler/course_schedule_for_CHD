package com.example.course_schedule_for_chd_v002.domain.model

import com.example.course_schedule_for_chd_v002.util.AppLogger

/**
 * 日历同步设置领域模型
 *
 * 用于存储用户的日历同步偏好设置
 */
data class ReminderSettings(
    // ============ 系统日历集成设置 ============
    /** 是否启用系统日历同步 */
    val calendarSyncEnabled: Boolean = false,

    /** 选中的日历ID (null表示使用应用专用日历) */
    val calendarId: Long? = null,

    // ============ 日历提醒设置 ============
    /** 是否在日历中添加课前提醒 */
    val calendarBeforeClassReminderEnabled: Boolean = true,

    /** 课前提醒提前分钟数 */
    val beforeClassReminderMinutes: Int = 15,

    /** 是否在日历中添加早八提醒 */
    val calendarEarlyMorningReminderEnabled: Boolean = true,

    /** 早八提醒时间 - 小时 */
    val earlyMorningReminderHour: Int = 23,

    /** 早八提醒时间 - 分钟 */
    val earlyMorningReminderMinute: Int = 0
) {
    /**
     * 序列化为 JSON 字符串
     */
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"calendarSyncEnabled\":$calendarSyncEnabled,")
            append("\"calendarId\":${calendarId ?: "null"},")
            append("\"calendarBeforeClassReminderEnabled\":$calendarBeforeClassReminderEnabled,")
            append("\"beforeClassReminderMinutes\":$beforeClassReminderMinutes,")
            append("\"calendarEarlyMorningReminderEnabled\":$calendarEarlyMorningReminderEnabled,")
            append("\"earlyMorningReminderHour\":$earlyMorningReminderHour,")
            append("\"earlyMorningReminderMinute\":$earlyMorningReminderMinute")
            append("}")
        }
    }

    companion object {
        /**
         * 从 JSON 字符串反序列化
         */
        fun fromJson(json: String): ReminderSettings {
            AppLogger.d("ReminderSettings", "fromJson 输入: $json")
            if (json.isEmpty() || json == "{}") {
                return ReminderSettings()
            }

            return try {
                val content = json.trim().removeSurrounding("{", "}")
                var calendarSyncEnabled = false
                var calendarId: Long? = null
                var calendarBeforeClassReminderEnabled = true
                var beforeClassReminderMinutes = 15
                var calendarEarlyMorningReminderEnabled = true
                var earlyMorningReminderHour = 23
                var earlyMorningReminderMinute = 0

                fun parseValue(key: String, content: String): String? {
                    val keyPattern = "\"$key\""
                    val keyIndex = content.indexOf(keyPattern)
                    if (keyIndex == -1) return null
                    val colonIndex = content.indexOf(':', keyIndex + keyPattern.length)
                    if (colonIndex == -1) return null

                    var start = colonIndex + 1
                    while (start < content.length && content[start].isWhitespace()) start++
                    if (start >= content.length) return null

                    return when {
                        content[start] == 't' || content[start] == 'f' -> {
                            val end = content.indexOfAny(charArrayOf(',', '}'), start)
                            if (end == -1) null else content.substring(start, end).trim()
                        }
                        content[start] == 'n' -> {
                            val end = content.indexOfAny(charArrayOf(',', '}'), start)
                            if (end == -1) null else content.substring(start, end).trim()
                        }
                        content[start].isDigit() || content[start] == '-' -> {
                            val end = content.indexOfAny(charArrayOf(',', '}'), start)
                            if (end == -1) null else content.substring(start, end).trim()
                        }
                        else -> null
                    }
                }

                // 解析 boolean
                parseValue("calendarSyncEnabled", content)?.let {
                    calendarSyncEnabled = it == "true"
                }
                parseValue("calendarBeforeClassReminderEnabled", content)?.let {
                    calendarBeforeClassReminderEnabled = it == "true"
                }
                parseValue("calendarEarlyMorningReminderEnabled", content)?.let {
                    calendarEarlyMorningReminderEnabled = it == "true"
                }

                // 解析 int
                parseValue("beforeClassReminderMinutes", content)?.toIntOrNull()?.let {
                    beforeClassReminderMinutes = it
                }
                parseValue("earlyMorningReminderHour", content)?.toIntOrNull()?.let {
                    earlyMorningReminderHour = it
                }
                parseValue("earlyMorningReminderMinute", content)?.toIntOrNull()?.let {
                    earlyMorningReminderMinute = it
                }

                // 解析 Long?
                parseValue("calendarId", content)?.let {
                    calendarId = if (it == "null") null else it.toLongOrNull()
                }

                ReminderSettings(
                    calendarSyncEnabled = calendarSyncEnabled,
                    calendarId = calendarId,
                    calendarBeforeClassReminderEnabled = calendarBeforeClassReminderEnabled,
                    beforeClassReminderMinutes = beforeClassReminderMinutes,
                    calendarEarlyMorningReminderEnabled = calendarEarlyMorningReminderEnabled,
                    earlyMorningReminderHour = earlyMorningReminderHour,
                    earlyMorningReminderMinute = earlyMorningReminderMinute
                )
            } catch (e: Exception) {
                AppLogger.e("ReminderSettings", "Failed to parse JSON: ${e.message}")
                ReminderSettings()
            }
        }

        val DEFAULT = ReminderSettings()
    }

    private inline fun buildString(builderAction: StringBuilder.() -> Unit): String {
        return StringBuilder().apply(builderAction).toString()
    }
}
