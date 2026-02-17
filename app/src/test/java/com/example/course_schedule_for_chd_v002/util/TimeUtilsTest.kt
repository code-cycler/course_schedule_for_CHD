package com.example.course_schedule_for_chd_v002.util

import com.example.course_schedule_for_chd_v002.domain.model.DayOfWeek
import org.junit.Assert.*
import org.junit.Test

/**
 * TimeUtils 单元测试
 * 测试课程冲突检测功能
 */
class TimeUtilsTest {

    // ================ findConflicts 测试 ================

    @Test
    fun `findConflicts with no courses returns empty map`() {
        val result = TimeUtils.findConflicts(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `findConflicts with single course returns empty map`() {
        val courses = listOf(TestDataFactory.createCourse())
        val result = TimeUtils.findConflicts(courses)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `findConflicts with two conflicting courses returns both`() {
        val course1 = TestDataFactory.createCourse(
            id = 1,
            name = "Math",
            dayOfWeek = DayOfWeek.MONDAY,
            startWeek = 1, endWeek = 16,
            startNode = 1, endNode = 2
        )
        val course2 = TestDataFactory.createCourse(
            id = 2,
            name = "Physics",
            dayOfWeek = DayOfWeek.MONDAY,
            startWeek = 1, endWeek = 16,
            startNode = 2, endNode = 3
        )

        val result = TimeUtils.findConflicts(listOf(course1, course2))

        assertEquals(2, result.size)
        assertTrue(result.containsKey(course1.id))
        assertTrue(result.containsKey(course2.id))
        // 验证冲突关系
        assertTrue(result[course1.id]?.contains(course2.id) == true)
        assertTrue(result[course2.id]?.contains(course1.id) == true)
    }

    @Test
    fun `findConflicts with non-overlapping courses returns empty map`() {
        val course1 = TestDataFactory.createCourse(
            dayOfWeek = DayOfWeek.MONDAY,
            startWeek = 1, endWeek = 8
        )
        val course2 = TestDataFactory.createCourse(
            dayOfWeek = DayOfWeek.MONDAY,
            startWeek = 9, endWeek = 16
        )

        val result = TimeUtils.findConflicts(listOf(course1, course2))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `findConflicts with courses on different days returns empty map`() {
        val course1 = TestDataFactory.createCourse(
            id = 1,
            dayOfWeek = DayOfWeek.MONDAY
        )
        val course2 = TestDataFactory.createCourse(
            id = 2,
            dayOfWeek = DayOfWeek.TUESDAY
        )

        val result = TimeUtils.findConflicts(listOf(course1, course2))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `findConflicts with multiple courses groups conflicts correctly`() {
        val conflict1a = TestDataFactory.createCourse(
            id = 1, name = "A1",
            dayOfWeek = DayOfWeek.MONDAY,
            startNode = 1, endNode = 2
        )
        val conflict1b = TestDataFactory.createCourse(
            id = 2, name = "A2",
            dayOfWeek = DayOfWeek.MONDAY,
            startNode = 2, endNode = 3
        )
        val noConflict = TestDataFactory.createCourse(
            id = 3, name = "B",
            dayOfWeek = DayOfWeek.TUESDAY
        )

        val result = TimeUtils.findConflicts(listOf(conflict1a, conflict1b, noConflict))

        assertEquals(2, result.size)
        assertFalse(result.containsKey(3))
    }

    @Test
    fun `findConflicts with three way conflict returns all three`() {
        val course1 = TestDataFactory.createCourse(
            id = 1, name = "C1",
            dayOfWeek = DayOfWeek.WEDNESDAY,
            startWeek = 1, endWeek = 16,
            startNode = 3, endNode = 4
        )
        val course2 = TestDataFactory.createCourse(
            id = 2, name = "C2",
            dayOfWeek = DayOfWeek.WEDNESDAY,
            startWeek = 1, endWeek = 16,
            startNode = 3, endNode = 4
        )
        val course3 = TestDataFactory.createCourse(
            id = 3, name = "C3",
            dayOfWeek = DayOfWeek.WEDNESDAY,
            startWeek = 1, endWeek = 16,
            startNode = 4, endNode = 5
        )

        val result = TimeUtils.findConflicts(listOf(course1, course2, course3))

        assertEquals(3, result.size)
        // course1 conflicts with course2 and course3
        assertEquals(2, result[1]?.size)
        // course2 conflicts with course1 and course3
        assertEquals(2, result[2]?.size)
        // course3 conflicts with course1 and course2
        assertEquals(2, result[3]?.size)
    }

    // ================ hasConflictWithAny 测试 ================

    @Test
    fun `hasConflictWithAny with no other courses returns false`() {
        val course = TestDataFactory.createCourse()
        assertFalse(TimeUtils.hasConflictWithAny(course, emptyList()))
    }

    @Test
    fun `hasConflictWithAny with conflicting course returns true`() {
        val course = TestDataFactory.createCourse(
            dayOfWeek = DayOfWeek.MONDAY,
            startNode = 1, endNode = 2
        )
        val others = listOf(
            TestDataFactory.createCourse(
                id = 99,
                dayOfWeek = DayOfWeek.MONDAY,
                startNode = 2, endNode = 3
            )
        )

        assertTrue(TimeUtils.hasConflictWithAny(course, others))
    }

    @Test
    fun `hasConflictWithAny with non-conflicting course returns false`() {
        val course = TestDataFactory.createCourse(
            dayOfWeek = DayOfWeek.MONDAY
        )
        val others = listOf(
            TestDataFactory.createCourse(
                id = 99,
                dayOfWeek = DayOfWeek.TUESDAY
            )
        )

        assertFalse(TimeUtils.hasConflictWithAny(course, others))
    }

    @Test
    fun `hasConflictWithAny excludes self from check`() {
        val course = TestDataFactory.createCourse(id = 1)

        assertFalse(TimeUtils.hasConflictWithAny(course, listOf(course)))
    }

    // ================ findConflictsForWeek 测试 ================

    @Test
    fun `findConflictsForWeek filters by week correctly`() {
        val course1 = TestDataFactory.createCourse(
            id = 1,
            dayOfWeek = DayOfWeek.MONDAY,
            startWeek = 1, endWeek = 8,
            startNode = 1, endNode = 2
        )
        val course2 = TestDataFactory.createCourse(
            id = 2,
            dayOfWeek = DayOfWeek.MONDAY,
            startWeek = 9, endWeek = 16,
            startNode = 1, endNode = 2
        )
        val course3 = TestDataFactory.createCourse(
            id = 3,
            dayOfWeek = DayOfWeek.MONDAY,
            startWeek = 1, endWeek = 16,
            startNode = 1, endNode = 2
        )

        // Week 1: course1 and course3 overlap
        val resultWeek1 = TimeUtils.findConflictsForWeek(listOf(course1, course2, course3), 1)
        assertEquals(2, resultWeek1.size)
        assertTrue(resultWeek1.containsKey(1))
        assertTrue(resultWeek1.containsKey(3))

        // Week 10: course2 and course3 overlap
        val resultWeek10 = TimeUtils.findConflictsForWeek(listOf(course1, course2, course3), 10)
        assertEquals(2, resultWeek10.size)
        assertTrue(resultWeek10.containsKey(2))
        assertTrue(resultWeek10.containsKey(3))
    }

    @Test
    fun `findConflictsForWeek with empty list returns empty map`() {
        val result = TimeUtils.findConflictsForWeek(emptyList(), 1)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `findConflictsForWeek with no courses_in_week returns empty map`() {
        val course = TestDataFactory.createCourse(
            startWeek = 5, endWeek = 10
        )

        val result = TimeUtils.findConflictsForWeek(listOf(course), 1)
        assertTrue(result.isEmpty())
    }
}
