package com.example.course_schedule_for_chd_v002.service.checkin.polling

import com.example.course_schedule_for_chd_v002.domain.model.Course
import com.example.course_schedule_for_chd_v002.domain.model.CourseType
import com.example.course_schedule_for_chd_v002.domain.model.DayOfWeek
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * [v129] B5 课时有窗判定单测（默认窗 = 课前 10 分钟至课后 5 分钟，渭水节次表）。
 */
class ClassWindowSchedulerTest {

    private fun course(
        day: DayOfWeek = DayOfWeek.MONDAY,
        startWeek: Int = 1, endWeek: Int = 16,
        startNode: Int = 1, endNode: Int = 2
    ) = Course(
        id = 1, name = "课", teacher = "", location = "WM3201",
        dayOfWeek = day, startWeek = startWeek, endWeek = endWeek,
        startNode = startNode, endNode = endNode,
        courseType = CourseType.REQUIRED, credit = 0.0, remark = "", semester = "s"
    )

    // 第 1–2 节 08:30–10:05 → 默认窗 08:20–10:10

    @Test
    fun `inside window before class start`() {
        assertTrue(
            ClassWindowScheduler.inAnyClassWindow(
                listOf(course()), 3, DayOfWeek.MONDAY, LocalTime.of(8, 20)
            )
        )
    }

    @Test
    fun `just before lead window is outside`() {
        assertFalse(
            ClassWindowScheduler.inAnyClassWindow(
                listOf(course()), 3, DayOfWeek.MONDAY, LocalTime.of(8, 19)
            )
        )
    }

    @Test
    fun `inside window after class end`() {
        assertTrue(
            ClassWindowScheduler.inAnyClassWindow(
                listOf(course()), 3, DayOfWeek.MONDAY, LocalTime.of(10, 10)
            )
        )
    }

    @Test
    fun `just after lag window is outside`() {
        assertFalse(
            ClassWindowScheduler.inAnyClassWindow(
                listOf(course()), 3, DayOfWeek.MONDAY, LocalTime.of(10, 11)
            )
        )
    }

    @Test
    fun `mid class is inside`() {
        assertTrue(
            ClassWindowScheduler.inAnyClassWindow(
                listOf(course()), 3, DayOfWeek.MONDAY, LocalTime.of(9, 0)
            )
        )
    }

    @Test
    fun `other weekday no window`() {
        assertFalse(
            ClassWindowScheduler.inAnyClassWindow(
                listOf(course()), 3, DayOfWeek.TUESDAY, LocalTime.of(9, 0)
            )
        )
    }

    @Test
    fun `week outside range no window`() {
        assertFalse(
            ClassWindowScheduler.inAnyClassWindow(
                listOf(course()), 17, DayOfWeek.MONDAY, LocalTime.of(9, 0)
            )
        )
    }

    @Test
    fun `saturday course hits on saturday`() {
        val satCourse = course(day = DayOfWeek.SATURDAY)
        assertTrue(
            ClassWindowScheduler.inAnyClassWindow(
                listOf(satCourse), 3, DayOfWeek.SATURDAY, LocalTime.of(8, 30)
            )
        )
        assertFalse(
            ClassWindowScheduler.inAnyClassWindow(
                listOf(satCourse), 3, DayOfWeek.MONDAY, LocalTime.of(8, 30)
            )
        )
    }

    @Test
    fun `second course window also counts`() {
        // 第 9–11 节晚课 19:00–21:25 → 窗 18:50–21:30
        val evening = course(startNode = 9, endNode = 11)
        assertTrue(
            ClassWindowScheduler.inAnyClassWindow(
                listOf(course(), evening), 3, DayOfWeek.MONDAY, LocalTime.of(19, 0)
            )
        )
    }

    @Test
    fun `unknown node maps to no window for that course`() {
        // 越界节次（如第 12 节）无时间映射：该课跳过，不误开窗
        val weird = course(startNode = 12, endNode = 13)
        assertFalse(
            ClassWindowScheduler.inAnyClassWindow(
                listOf(weird), 3, DayOfWeek.MONDAY, LocalTime.of(9, 0)
            )
        )
    }

    @Test
    fun `custom lead lag respected`() {
        // 课前 30 分钟窗：08:00 即在窗内
        assertTrue(
            ClassWindowScheduler.inAnyClassWindow(
                listOf(course()), 3, DayOfWeek.MONDAY, LocalTime.of(8, 0),
                leadMinutes = 30, lagMinutes = 5
            )
        )
    }

    @Test
    fun `no courses never in window`() {
        assertFalse(
            ClassWindowScheduler.inAnyClassWindow(
                emptyList(), 3, DayOfWeek.MONDAY, LocalTime.of(9, 0)
            )
        )
    }
}
