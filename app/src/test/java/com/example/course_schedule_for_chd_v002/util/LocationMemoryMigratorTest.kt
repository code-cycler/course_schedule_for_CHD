package com.example.course_schedule_for_chd_v002.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [v126] 位置记忆 v1 → v2 迁移规则单测（ADR-0007：分组取最大 / 纯教室键丢弃）
 */
class LocationMemoryMigratorTest {

    @Test
    fun `course room key grouped by course name`() {
        val migrated = LocationMemoryMigrator.migrate(
            mapOf("高等数学|WM3201" to 3L, "高等数学|WM3305" to 5L)
        )
        assertEquals(mapOf("高等数学" to 5L), migrated)
    }

    @Test
    fun `same course multiple memories keeps max location id`() {
        // 旧格式无时间戳，locationId 自增 → 取最大近似「最新标定的位置」
        val migrated = LocationMemoryMigrator.migrate(
            mapOf("英语|" to 10L, "英语|外语楼" to 7L, "英语|WM2101" to 12L)
        )
        assertEquals(mapOf("英语" to 12L), migrated)
    }

    @Test
    fun `pure room keys without course name are dropped`() {
        val migrated = LocationMemoryMigrator.migrate(
            mapOf("|WM3201" to 1L, "高等数学|WM3201" to 2L)
        )
        assertEquals(mapOf("高等数学" to 2L), migrated)
    }

    @Test
    fun `empty input returns empty map`() {
        assertEquals(emptyMap<String, Long>(), LocationMemoryMigrator.migrate(emptyMap()))
    }

    @Test
    fun `different courses stay separate`() {
        val migrated = LocationMemoryMigrator.migrate(
            mapOf("高等数学|WM1" to 1L, "英语|WM2" to 2L)
        )
        assertEquals(mapOf("高等数学" to 1L, "英语" to 2L), migrated)
    }
}
