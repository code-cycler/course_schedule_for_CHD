package com.example.course_schedule_for_chd_v002.util

import com.example.course_schedule_for_chd_v002.domain.model.Course
import com.example.course_schedule_for_chd_v002.domain.model.CourseType
import com.example.course_schedule_for_chd_v002.domain.model.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [v125] ScheduleDiffCalculator 单测（L2-build 接口规格五用例）：
 * 全同 / 全新增 / 全删除 / 部分变更 / 多教学班合并形态。
 */
class ScheduleDiffCalculatorTest {

    private fun course(
        id: Long, name: String, teacher: String = "张三", location: String = "WM-101",
        day: DayOfWeek = DayOfWeek.MONDAY, startWeek: Int = 1, endWeek: Int = 16,
        startNode: Int = 1, endNode: Int = 2, remark: String = "weeksBitmap:011111111111111110"
    ) = Course(
        id = id, name = name, teacher = teacher, location = location, dayOfWeek = day,
        startWeek = startWeek, endWeek = endWeek, startNode = startNode, endNode = endNode,
        courseType = CourseType.REQUIRED, credit = 2.0, remark = remark, semester = "2025-2026-2"
    )

    @Test
    fun `全同 - id 不同但业务字段相同视为无差异（Room 自增 id vs 远端 id=0）`() {
        val local = listOf(course(id = 1, name = "高数"), course(id = 2, name = "英语"))
        val remote = listOf(course(id = 0, name = "高数"), course(id = 0, name = "英语"))

        val diff = ScheduleDiffCalculator.computeDiff(local, remote)

        assertTrue("业务字段全同应无差异", diff.isEmpty)
    }

    @Test
    fun `全新增 - 本地空远端有课`() {
        val remote = listOf(course(0, "高数"), course(0, "英语"))
        val diff = ScheduleDiffCalculator.computeDiff(emptyList(), remote)
        assertEquals(2, diff.added.size)
        assertTrue(diff.removed.isEmpty())
        assertTrue(diff.changed.isEmpty())
    }

    @Test
    fun `全删除 - 远端空本地有课`() {
        val local = listOf(course(1, "高数"), course(2, "英语"))
        val diff = ScheduleDiffCalculator.computeDiff(local, emptyList())
        assertEquals(2, diff.removed.size)
        assertTrue(diff.added.isEmpty())
        assertTrue(diff.changed.isEmpty())
    }

    @Test
    fun `部分变更 - 同名其余字段异配对为 changed，含周位图变化`() {
        val local = listOf(
            course(1, "高数", startNode = 1, endNode = 2),
            course(2, "英语")
        )
        val remote = listOf(
            course(0, "高数", startNode = 3, endNode = 4),          // 节次变了
            course(0, "英语"),                                       // 不变
            course(0, "人工智能导论")                                 // 新课
        )

        val diff = ScheduleDiffCalculator.computeDiff(local, remote)

        assertEquals(1, diff.added.size)
        assertEquals("人工智能导论", diff.added.first().name)
        assertEquals(1, diff.changed.size)
        assertEquals("高数", diff.changed.first().first.name)
        assertEquals(3, diff.changed.first().second.startNode)
        assertTrue("英语不变不应出现在任何差异里", diff.removed.isEmpty())
    }

    @Test
    fun `多教学班合并形态 - 同名多条部分变更时逐条配对`() {
        // v90/v120 合并逻辑下的真实形态：同名课多条记录（不同教室/周次）
        val local = listOf(
            course(1, "人工智能", location = "WM3305", startWeek = 13, endWeek = 13),
            course(2, "人工智能", location = "WM2303机房", startWeek = 1, endWeek = 10)
        )
        val remote = listOf(
            course(0, "人工智能", location = "WM3305", startWeek = 13, endWeek = 13),   // 不变
            course(0, "人工智能", location = "WM2303机房", startWeek = 1, endWeek = 12) // 周次延长
        )

        val diff = ScheduleDiffCalculator.computeDiff(local, remote)

        assertEquals("只有一条真实变更", 1, diff.changed.size)
        assertEquals("WM2303机房", diff.changed.first().first.location)
        assertEquals(12, diff.changed.first().second.endWeek)
        assertTrue(diff.added.isEmpty())
        assertTrue(diff.removed.isEmpty())
    }
}
