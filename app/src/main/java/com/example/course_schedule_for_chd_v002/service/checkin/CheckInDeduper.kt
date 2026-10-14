package com.example.course_schedule_for_chd_v002.service.checkin

import com.example.course_schedule_for_chd_v002.util.AppLogger

/**
 * [v126] 签到会话级去重（checkin-assist-v2 B2 / ADR-0006）。
 *
 * 会话键 = checkInId（非空优先）否则归一化课程名；键在时间窗内重复出现 → 抑制。
 * 无键（无 ID 且无 courseHint）退化为纯时间窗（全部无键事件共享一个键，
 * 同窗内多门课签到可能被误并——接受此权衡，靠轮询带回签到 ID 补偿）。
 *
 * 会话记录仅内存（进程重启清零，可接受：重启后同一签到几乎不可能再触发）。
 * 纯函数风格（注入时钟与窗口），便于 JVM 单测。
 */
object CheckInDeduper {

    private const val DEFAULT_WINDOW_MS = 2 * 60 * 1000L

    /** 无键事件的共享占位键 */
    private const val NO_KEY = "\u0000time_window"

    private val recentSessions = mutableMapOf<String, Long>()
    private val lock = Any()

    /**
     * @return true = 该事件属于近期已响应过的签到会话，应抑制
     */
    fun shouldSuppress(
        event: CheckInTriggerEvent,
        now: Long = System.currentTimeMillis(),
        windowMs: Long = DEFAULT_WINDOW_MS
    ): Boolean {
        val key = sessionKey(event)
        synchronized(lock) {
            // 淘汰过期会话，防 map 无界增长
            recentSessions.entries.removeAll { now - it.value > windowMs }
            val last = recentSessions[key]
            return if (last != null && now - last <= windowMs) {
                recentSessions[key] = now
                AppLogger.d(TAG, "[v126] 去重命中: key=$key, 距上次 ${now - last}ms")
                true
            } else {
                recentSessions[key] = now
                false
            }
        }
    }

    /** 会话键：checkInId 优先，否则归一化课程名（trim + 小写） */
    private fun sessionKey(event: CheckInTriggerEvent): String {
        event.checkInId?.takeIf { it.isNotBlank() }?.let { return "id:$it" }
        event.courseHint?.takeIf { it.isNotBlank() }?.let {
            return "course:${it.trim().lowercase()}"
        }
        return NO_KEY
    }

    /** 测试用：清空会话记录 */
    fun reset() {
        synchronized(lock) { recentSessions.clear() }
    }

    private const val TAG = "CheckInDeduper"
}
