package com.example.course_schedule_for_chd_v002.util

/**
 * [v126] 位置记忆 v1 → v2 迁移的纯逻辑（ADR-0007，抽纯函数供 JVM 单测；
 * DataStore 读写封装在 UserPreferences.ensureLocationMemoryMigratedOnce）。
 *
 * 规则：旧键 "course|room" / "course|" / "|room" 按课程名分组；
 * 同课程多条取 locationId 最大（自增 id 近似「最新标定的位置」）；
 * 纯教室键（无课程名部分）丢弃——教室 tag 化后退出记忆键。
 */
object LocationMemoryMigrator {

    fun migrate(oldMap: Map<String, Long>): Map<String, Long> =
        oldMap.entries
            .groupBy({ it.key.substringBefore('|').trim() }, { it.value })
            .filterKeys { it.isNotEmpty() }
            .mapValues { (_, ids) -> ids.max() }
}
