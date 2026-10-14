package com.example.course_schedule_for_chd_v002.util

/**
 * [v126] 畅课通知原文记录（checkin-assist-v2 B3，grill W01 Q3 关联）。
 * 收录**全部**畅课通知（含未命中关键字的普通/营销推送——关键字校准的价值恰在看到
 * 「未命中」的真实文案）；DataStore JSON 环形缓冲，默认保留最近 [DEFAULT_CAP] 条。
 */
data class TronClassNotificationRecord(
    val channelId: String?,
    val title: String?,
    val text: String?,
    val timestamp: Long
)

/** 纯函数集合：环形追加 + JSON 序列化（JVM 可测） */
object NotificationLogJson {

    const val DEFAULT_CAP = 50

    /** 环形追加：新记录在尾部，超出容量淘汰最旧 */
    fun append(log: List<TronClassNotificationRecord>, record: TronClassNotificationRecord, cap: Int = DEFAULT_CAP): List<TronClassNotificationRecord> =
        (log + record).takeLast(cap)

    fun serialize(log: List<TronClassNotificationRecord>): String {
        if (log.isEmpty()) return "[]"
        return "[" + log.joinToString(",") { r ->
            buildString {
                append("{\"channelId\":")
                append(r.channelId?.let { "\"${escape(it)}\"" } ?: "null")
                append(",\"title\":")
                append(r.title?.let { "\"${escape(it)}\"" } ?: "null")
                append(",\"text\":")
                append(r.text?.let { "\"${escape(it)}\"" } ?: "null")
                append(",\"timestamp\":${r.timestamp}")
                append("}")
            }
        } + "]"
    }

    fun decode(json: String?): List<TronClassNotificationRecord> {
        if (json.isNullOrBlank()) return emptyList()
        val content = json.trim().removeSurrounding("[", "]")
        if (content.isEmpty()) return emptyList()
        val result = mutableListOf<TronClassNotificationRecord>()
        var i = 0
        while (i < content.length) {
            val start = content.indexOf('{', i)
            if (start == -1) break
            var depth = 0
            var end = -1
            for (j in start until content.length) {
                when (content[j]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            end = j
                            break
                        }
                    }
                }
            }
            if (end == -1) break
            val obj = content.substring(start, end + 1)
            result.add(decodeRecord(obj))
            i = end + 1
        }
        return result
    }

    private fun decodeRecord(obj: String): TronClassNotificationRecord {
        fun str(field: String): String? {
            val key = "\"$field\":"
            val idx = obj.indexOf(key)
            if (idx == -1) return null
            val v = obj.substring(idx + key.length).trim()
            if (v == "null" || !v.startsWith("\"")) return null
            val sb = StringBuilder()
            var j = 1
            while (j < v.length) {
                val c = v[j]
                if (c == '\\' && j + 1 < v.length) {
                    when (v[j + 1]) {
                        'n' -> sb.append('\n')
                        '\\' -> sb.append('\\')
                        '"' -> sb.append('"')
                        else -> sb.append(v[j + 1])
                    }
                    j += 2
                    continue
                }
                if (c == '"') break
                sb.append(c); j++
            }
            return sb.toString()
        }
        fun lng(field: String): Long {
            val key = "\"$field\":"
            val idx = obj.indexOf(key)
            if (idx == -1) return 0L
            val v = obj.substring(idx + key.length).trim()
            val end = v.indexOfFirst { it == ',' || it == '}' }
            return v.substring(0, if (end == -1) v.length else end).toLongOrNull() ?: 0L
        }
        return TronClassNotificationRecord(
            channelId = str("channelId"),
            title = str("title"),
            text = str("text"),
            timestamp = lng("timestamp")
        )
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
