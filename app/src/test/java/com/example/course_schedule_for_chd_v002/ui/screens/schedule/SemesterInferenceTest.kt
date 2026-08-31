package com.example.course_schedule_for_chd_v002.ui.screens.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [获取新学期]/[跨学期] 学期推断工具单测
 * 覆盖：12 个月 + 2/15、8/15 边界（含当日）+ 编码比较 + 秋春跨季节序列
 * 规则来源：harness/design/cross-semester/L0-cross-semester.md 验收标准 1、2（2026-08-31 人拍板）
 */
class SemesterInferenceTest {

    @Test
    fun `inferCurrentSemester spring window feb15 to aug14`() {
        // 2/15（含）– 8/14：春季第二学期 (year-1)-year-2
        assertEquals("2025-2026-2", inferCurrentSemester(2026, 2, 15))
        assertEquals("2025-2026-2", inferCurrentSemester(2026, 4, 1))
        assertEquals("2025-2026-2", inferCurrentSemester(2026, 7, 31))
        assertEquals("2025-2026-2", inferCurrentSemester(2026, 8, 14))
    }

    @Test
    fun `inferCurrentSemester fall window aug15 to dec31`() {
        // 8/15（含）– 12/31：秋季第一学期 year-(year+1)-1
        assertEquals("2026-2027-1", inferCurrentSemester(2026, 8, 15))
        assertEquals("2026-2027-1", inferCurrentSemester(2026, 9, 1))
        assertEquals("2026-2027-1", inferCurrentSemester(2026, 12, 31))
    }

    @Test
    fun `inferCurrentSemester fall tail jan1 to feb14`() {
        // 1/1 – 2/14：去年秋季收尾 (year-1)-year-1
        assertEquals("2025-2026-1", inferCurrentSemester(2026, 1, 1))
        assertEquals("2025-2026-1", inferCurrentSemester(2026, 1, 20))
        assertEquals("2025-2026-1", inferCurrentSemester(2026, 2, 14))
        assertEquals("2026-2027-1", inferCurrentSemester(2027, 1, 20))
    }

    @Test
    fun `inferCurrentSemester boundary day 15 switches on same day`() {
        // 15 日当天即切新学期（人拍板：含当日）
        assertEquals("2025-2026-1", inferCurrentSemester(2026, 2, 14))
        assertEquals("2025-2026-2", inferCurrentSemester(2026, 2, 15))
        assertEquals("2025-2026-2", inferCurrentSemester(2026, 8, 14))
        assertEquals("2026-2027-1", inferCurrentSemester(2026, 8, 15))
    }

    @Test
    fun `semesterCode encodes monotonically and rejects invalid`() {
        assertEquals(2025 * 2, semesterCode("2025-2026-1"))
        assertEquals(2025 * 2 + 1, semesterCode("2025-2026-2"))
        assertTrue(semesterCode("2025-2026-2")!! < semesterCode("2026-2027-1")!!)
        assertNull(semesterCode("invalid"))
        assertNull(semesterCode("2025-2026"))
        assertNull(semesterCode("2025-2026-3"))
    }

    @Test
    fun `isSemesterOutdated compares by code`() {
        // 本地落后于推断 → 过期
        assertTrue(isSemesterOutdated("2025-2026-2", "2026-2027-1"))
        // 相等 → 未过期
        assertFalse(isSemesterOutdated("2026-2027-1", "2026-2027-1"))
        // 用户提前抓了未来学期而日期未到（local > inferred）→ 未过期
        assertFalse(isSemesterOutdated("2026-2027-1", "2025-2026-2"))
        // 格式异常 → 保守不过期
        assertFalse(isSemesterOutdated("invalid", "2026-2027-1"))
        assertFalse(isSemesterOutdated("2025-2026-2", "invalid"))
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
