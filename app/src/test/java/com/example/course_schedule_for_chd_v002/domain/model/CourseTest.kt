package com.example.course_schedule_for_chd_v002.domain.model

import org.junit.Assert.*
import org.junit.Test

class CourseTest {

    @Test
    fun `weekRange returns correct IntRange`() {
        val course = Course(
            name = "测试课程",
            teacher = "老师",
            location = "A101",
            dayOfWeek = DayOfWeek.MONDAY,
            startWeek = 1,
            endWeek = 8,
            startNode = 1,
            endNode = 2,
            courseType = CourseType.REQUIRED,
            credit = 2.0,
            semester = "2024-2025-1"
        )

        assertEquals(1..8, course.weekRange)
    }

    @Test
    fun `nodeRange returns correct IntRange`() {
        val course = Course(
            name = "测试课程",
            teacher = "老师",
            location = "A101",
            dayOfWeek = DayOfWeek.MONDAY,
            startWeek = 1,
            endWeek = 16,
            startNode = 3,
            endNode = 5,
            courseType = CourseType.REQUIRED,
            credit = 2.0,
            semester = "2024-2025-1"
        )

        assertEquals(3..5, course.nodeRange)
    }

    @Test
    fun `isWeekInRange returns true for week in range`() {
        val course = Course(
            name = "测试课程",
            teacher = "老师",
            location = "A101",
            dayOfWeek = DayOfWeek.MONDAY,
            startWeek = 1,
            endWeek = 8,
            startNode = 1,
            endNode = 2,
            courseType = CourseType.REQUIRED,
            credit = 2.0,
            semester = "2024-2025-1"
        )

        assertTrue(course.isWeekInRange(4))
    }

    @Test
    fun `isWeekInRange returns false for week out of range`() {
        val course = Course(
            name = "测试课程",
            teacher = "老师",
            location = "A101",
            dayOfWeek = DayOfWeek.MONDAY,
            startWeek = 1,
            endWeek = 8,
            startNode = 1,
            endNode = 2,
            courseType = CourseType.REQUIRED,
            credit = 2.0,
            semester = "2024-2025-1"
        )

        assertFalse(course.isWeekInRange(12))
    }

    @Test
    fun `hasTimeConflict returns true for overlapping courses`() {
        val course1 = Course(
            name = "课程1",
            teacher = "老师1",
            location = "A101",
            dayOfWeek = DayOfWeek.MONDAY,
            startWeek = 1,
            endWeek = 16,
            startNode = 1,
            endNode = 2,
            courseType = CourseType.REQUIRED,
            credit = 2.0,
            semester = "2024-2025-1"
        )

        val course2 = Course(
            name = "课程2",
            teacher = "老师2",
            location = "B202",
            dayOfWeek = DayOfWeek.MONDAY,
            startWeek = 1,
            endWeek = 8,
            startNode = 2,
            endNode = 4,
            courseType = CourseType.ELECTIVE,
            credit = 1.5,
            semester = "2024-2025-1"
        )

        assertTrue(course1.hasTimeConflict(course2))
    }

    @Test
    fun `hasTimeConflict returns false for different days`() {
        val course1 = Course(
            name = "课程1",
            teacher = "老师1",
            location = "A101",
            dayOfWeek = DayOfWeek.MONDAY,
            startWeek = 1,
            endWeek = 16,
            startNode = 1,
            endNode = 2,
            courseType = CourseType.REQUIRED,
            credit = 2.0,
            semester = "2024-2025-1"
        )

        val course2 = course1.copy(dayOfWeek = DayOfWeek.TUESDAY)

        assertFalse(course1.hasTimeConflict(course2))
    }

    @Test
    fun `hasTimeConflict returns false for non_overlapping weeks`() {
        val course1 = Course(
            name = "课程1",
            teacher = "老师1",
            location = "A101",
            dayOfWeek = DayOfWeek.MONDAY,
            startWeek = 1,
            endWeek = 8,
            startNode = 1,
            endNode = 2,
            courseType = CourseType.REQUIRED,
            credit = 2.0,
            semester = "2024-2025-1"
        )

        val course2 = Course(
            name = "课程2",
            teacher = "老师2",
            location = "B202",
            dayOfWeek = DayOfWeek.MONDAY,
            startWeek = 9,
            endWeek = 16,
            startNode = 1,
            endNode = 2,
            courseType = CourseType.ELECTIVE,
            credit = 1.5,
            semester = "2024-2025-1"
        )

        assertFalse(course1.hasTimeConflict(course2))
    }
}
