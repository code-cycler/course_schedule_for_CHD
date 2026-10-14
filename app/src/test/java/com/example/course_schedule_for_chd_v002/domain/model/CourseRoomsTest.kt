package com.example.course_schedule_for_chd_v002.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [v126] Course.rooms 教室解析单测（v120 逗号连接多教室 / 星号前缀 / 空白）
 */
class CourseRoomsTest {

    private fun course(location: String) = Course(
        id = 1, name = "课", teacher = "", location = location,
        dayOfWeek = DayOfWeek.MONDAY, startWeek = 1, endWeek = 16,
        startNode = 1, endNode = 2, courseType = CourseType.REQUIRED,
        credit = 0.0, remark = "", semester = "s"
    )

    @Test
    fun `single room`() {
        assertEquals(setOf("WM3201"), course("WM3201").rooms)
    }

    @Test
    fun `v120 comma joined multiple rooms split and trimmed`() {
        assertEquals(
            setOf("*WM2303机房", "*WM2301机房"),
            course("*WM2303机房,*WM2301机房").rooms
        )
    }

    @Test
    fun `blank location yields empty set`() {
        assertEquals(emptySet<String>(), course("").rooms)
        assertEquals(emptySet<String>(), course(" , ").rooms)
    }
}
