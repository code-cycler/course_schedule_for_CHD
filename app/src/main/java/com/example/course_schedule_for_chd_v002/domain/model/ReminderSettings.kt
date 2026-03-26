package com.example.course_schedule_for_chd_v002.domain.model

/**
 * 提醒设置领域模型
 *
 * 用于存储用户的课程提醒偏好设置
 */
data class ReminderSettings(
    // ============ 次日早八提醒设置 ============
    /** 是否启用次日早八提醒 */
    val earlyMorningReminderEnabled: Boolean = true,

    /** 早八提醒时间 - 小时 (0-23) */
    val earlyMorningReminderHour: Int = 23,  // 默认晚上11点

    /** 早八提醒时间 - 分钟 (0-59) */
    val earlyMorningReminderMinute: Int = 0,

    // ============ 上课前提醒设置 ============
    /** 是否启用上课前提醒 */
    val beforeClassReminderEnabled: Boolean = true,

    /** 上课前多少分钟提醒 */
    val beforeClassReminderMinutes: Int = 15,  // 默认15分钟

    // ============ 系统日历集成设置 ============
    /** 是否启用系统日历同步 */
    val calendarSyncEnabled: Boolean = false,

    /** 选中的日历ID (null表示使用应用专用日历) */
    val calendarId: Long? = null,

    // ============ 通知设置 ============
    /** 是否启用提醒声音 */
    val reminderSoundEnabled: Boolean = true,

    /** 是否启用振动 */
    val reminderVibrationEnabled: Boolean = true
) {
    /**
     * 获取早八提醒时间的完整描述
     * @return 如 "23:00"
     */
    fun getEarlyMorningTimeDisplay(): String {
        return String.format("%02d:%02d", earlyMorningReminderHour, earlyMorningReminderMinute)
    }

    /**
     * 获取上课前提醒时间的描述
     * @return 如 "15分钟"
     */
    fun getBeforeClassTimeDisplay(): String {
        return "${beforeClassReminderMinutes}分钟"
    }

    /**
     * 序列化为 JSON 字符串
     */
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"earlyMorningReminderEnabled\":$earlyMorningReminderEnabled,")
            append("\"earlyMorningReminderHour\":$earlyMorningReminderHour,")
            append("\"earlyMorningReminderMinute\":$earlyMorningReminderMinute,")
            append("\"beforeClassReminderEnabled\":$beforeClassReminderEnabled,")
            append("\"beforeClassReminderMinutes\":$beforeClassReminderMinutes,")
            append("\"calendarSyncEnabled\":$calendarSyncEnabled,")
            append("\"calendarId\":${calendarId ?: "null"},")
            append("\"reminderSoundEnabled\":$reminderSoundEnabled,")
            append("\"reminderVibrationEnabled\":$reminderVibrationEnabled")
            append("}")
        }
    }

    companion object {
        /**
         * 从 JSON 字符串反序列化
         */
        fun fromJson(json: String): ReminderSettings {
            android.util.Log.d("ReminderSettings", "[Debug] fromJson 输入: $json")
            if (json.isEmpty() || json == "{}") {
                android.util.Log.d("ReminderSettings", "[Debug] JSON 为空，返回默认设置")
                return ReminderSettings()
            }

            return try {
                val content = json.trim().removeSurrounding("{", "}")
                var earlyMorningEnabled = true
                var earlyMorningHour = 23
                var earlyMorningMinute = 0
                var beforeClassEnabled = true
                var beforeClassMinutes = 15
                var calendarSyncEnabled = false
                var calendarId: Long? = null
                var soundEnabled = true
                var vibrationEnabled = true

                // 解析各个字段
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
                            // boolean 值
                            val end = content.indexOfAny(charArrayOf(',', '}'), start)
                            if (end == -1) null else content.substring(start, end).trim()
                        }
                        content[start] == 'n' -> {
                            // null 值
                            val end = content.indexOfAny(charArrayOf(',', '}'), start)
                            if (end == -1) null else content.substring(start, end).trim()
                        }
                        content[start].isDigit() || content[start] == '-' -> {
                            // 数字
                            val end = content.indexOfAny(charArrayOf(',', '}'), start)
                            if (end == -1) null else content.substring(start, end).trim()
                        }
                        else -> null
                    }
                }

                // 解析 boolean
                parseValue("earlyMorningReminderEnabled", content)?.let {
                    earlyMorningEnabled = it == "true"
                }
                parseValue("beforeClassReminderEnabled", content)?.let {
                    beforeClassEnabled = it == "true"
                }
                parseValue("calendarSyncEnabled", content)?.let {
                    calendarSyncEnabled = it == "true"
                }
                parseValue("reminderSoundEnabled", content)?.let {
                    soundEnabled = it == "true"
                }
                parseValue("reminderVibrationEnabled", content)?.let {
                    vibrationEnabled = it == "true"
                }

                // 解析 int
                parseValue("earlyMorningReminderHour", content)?.toIntOrNull()?.let {
                    earlyMorningHour = it
                }
                parseValue("earlyMorningReminderMinute", content)?.toIntOrNull()?.let {
                    earlyMorningMinute = it
                }
                parseValue("beforeClassReminderMinutes", content)?.toIntOrNull()?.let {
                    beforeClassMinutes = it
                }

                // 解析 Long?
                parseValue("calendarId", content)?.let {
                    calendarId = if (it == "null") null else it.toLongOrNull()
                }

                val result = ReminderSettings(
                    earlyMorningReminderEnabled = earlyMorningEnabled,
                    earlyMorningReminderHour = earlyMorningHour,
                    earlyMorningReminderMinute = earlyMorningMinute,
                    beforeClassReminderEnabled = beforeClassEnabled,
                    beforeClassReminderMinutes = beforeClassMinutes,
                    calendarSyncEnabled = calendarSyncEnabled,
                    calendarId = calendarId,
                    reminderSoundEnabled = soundEnabled,
                    reminderVibrationEnabled = vibrationEnabled
                )
                android.util.Log.d("ReminderSettings", "[Debug] fromJson 成功: $result")
                result
            } catch (e: Exception) {
                android.util.Log.e("ReminderSettings", "Failed to parse JSON: ${e.message}")
                ReminderSettings()
            }
        }

        /**
         * 默认设置
         */
        val DEFAULT = ReminderSettings()
    }

    /**
     * 辅助函数：构建字符串
     */
    private inline fun buildString(builderAction: StringBuilder.() -> Unit): String {
        return StringBuilder().apply(builderAction).toString()
    }
}
