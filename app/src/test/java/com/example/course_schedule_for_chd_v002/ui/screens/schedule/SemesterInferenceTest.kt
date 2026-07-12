package com.example.course_schedule_for_chd_v002.ui.screens.schedule

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [获取新学期] 学期推断工具 inferCurrentSemester / candidateSemesters 的单测
 * 覆盖 12 个月 + 1月/8月边界 + 秋春跨季节序列
 */
class SemesterInferenceTest {

    @Test
    fun `inferCurrentSemester spring months 2_to_7`() {
        // 2–7月：春季第二学期 (year-1)-year-2
        assertEquals("2025-2026-2", inferCurrentSemester(2026, 2))
        assertEquals("2025-2026-2", inferCurrentSemester(2026, 4))
        assertEquals("2025-2026-2", inferCurrentSemester(2026, 7))
    }

    @Test
    fun `inferCurrentSemester fall months from august`() {
        // 8–12月：秋季第一学期（8月起切新学年）
        assertEquals("2026-2027-1", inferCurrentSemester(2026, 8))
        assertEquals("2026-2027-1", inferCurrentSemester(2026, 9))
        assertEquals("2026-2027-1", inferCurrentSemester(2026, 12))
    }

    @Test
    fun `inferCurrentSemester january is fall tail`() {
        // 1月：去年秋季收尾
        assertEquals("2025-2026-1", inferCurrentSemester(2026, 1))
        assertEquals("2026-2027-1", inferCurrentSemester(2027, 1))
    }

    @Test
    fun `inferCurrentSemester today example 2026_07`() {
        // 今天 2026/7/11
        assertEquals("2025-2026-2", inferCurrentSemester(2026, 7))
    }

    @Test
    fun `candidateSemesters returns four around current spring`() {
        val result = candidateSemesters("2025-2026-2")
        assertEquals(
            listOf("2024-2025-2", "2025-2026-1", "2025-2026-2", "2026-2027-1"),
            result
        )
    }

    @Test
    fun `candidateSemesters crosses fall to spring boundary`() {
        // 当前秋季 2026-2027-1，往后1个是春季 2026-2027-2
        val result = candidateSemesters("2026-2027-1")
        assertEquals(
            listOf("2025-2026-1", "2025-2026-2", "2026-2027-1", "2026-2027-2"),
            result
        )
    }

    @Test
    fun `candidateSemester invalid input returns single`() {
        assertEquals(listOf("invalid"), candidateSemesters("invalid"))
        assertEquals(listOf("2025-2026"), candidateSemesters("2025-2026"))
    }
}
