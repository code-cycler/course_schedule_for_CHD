package com.example.course_schedule_for_chd_v002.util

import com.example.course_schedule_for_chd_v002.domain.model.Course
import com.example.course_schedule_for_chd_v002.domain.model.CourseType
import com.example.course_schedule_for_chd_v002.domain.model.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [v124] ScheduleStatsCalculator 纯函数单测（L2-build 接口规格：正常/空/含水课 三用例）
 */
class ScheduleStatsCalculatorTest {

    private fun course(
        name: String, credit: Double = 2.0,
        startNode: Int = 1, endNode: Int = 2
    ) = Course(
        id = 0, name = name, teacher = "T", location = "L", dayOfWeek = DayOfWeek.MONDAY,
        startWeek = 1, endWeek = 16, startNode = startNode, endNode = endNode,
        courseType = CourseType.REQUIRED, credit = credit, remark = "", semester = "2025-2026-2"
    )

    @Test
    fun `正常课表统计学分门数学时`() {
        val courses = listOf(
            course("高数", credit = 5.0, startNode = 1, endNode = 4),   // 4 节
            course("英语", credit = 2.0, startNode = 5, endNode = 6),   // 2 节
            // 同名多教学班：学分只计一次、门数只计一门，学时按两条真实占用累加
            course("体育", credit = 1.0, startNode = 7, endNode = 8),
            course("体育", credit = 1.0, startNode = 9, endNode = 10)
        )
        val stats = ScheduleStatsCalculator.calculate(courses, emptySet())

        assertEquals(8.0, stats.totalCredits, 1e-9)   // 5+2+1（体育去重）
        assertEquals(3, stats.courseCount)            // 高数/英语/体育
        assertEquals(10, stats.totalNodes)            // 4+2+2+2
        assertEquals(0, stats.waterCourseCount)
    }

    @Test
    fun `空课表返回全零`() {
        val stats = ScheduleStatsCalculator.calculate(emptyList(), setOf("水课"))
        assertEquals(0.0, stats.totalCredits, 1e-9)
        assertEquals(0, stats.courseCount)
        assertEquals(0, stats.totalNodes)
        assertEquals(0, stats.waterCourseCount)
    }

    @Test
    fun `水课按课程名交集计数且不影响其它指标`() {
        val courses = listOf(
            course("军事理论"), course("心理健康"), course("高数")
        )
        val stats = ScheduleStatsCalculator.calculate(courses, setOf("军事理论", "心理健康", "不存在的课"))
        assertEquals(2, stats.waterCourseCount)
        assertEquals(3, stats.courseCount)
    }
}
