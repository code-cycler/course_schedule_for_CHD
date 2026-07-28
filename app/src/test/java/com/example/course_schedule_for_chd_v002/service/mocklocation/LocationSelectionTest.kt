package com.example.course_schedule_for_chd_v002.service.mocklocation

import com.example.course_schedule_for_chd_v002.domain.model.CheckInLocation
import com.example.course_schedule_for_chd_v002.domain.model.Course
import com.example.course_schedule_for_chd_v002.domain.model.CourseType
import com.example.course_schedule_for_chd_v002.domain.model.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [v114] 触发时选位置规则单元测试
 */
class LocationSelectionTest {

    private fun loc(id: Long, name: String, course: String? = null, room: String? = null) =
        CheckInLocation(id, name, 34.0, 108.0, linkedCourseId = course, linkedRoomName = room)

    private val course = Course(
        id = 1, name = "高等数学", teacher = "", location = "WM3201",
        dayOfWeek = DayOfWeek.MONDAY, startWeek = 1, endWeek = 16,
        startNode = 1, endNode = 2, courseType = CourseType.REQUIRED,
        credit = 4.0, remark = "", semester = "2025-2026-2"
    )

    @Test
    fun `empty locations returns Empty`() {
        assertEquals(
            LocationSelection.Result.Empty,
            LocationSelection.select(emptyList(), null, null)
        )
    }

    @Test
    fun `single location returns Auto regardless of course`() {
        val only = loc(1, "A")
        val result = LocationSelection.select(listOf(only), null, null)
        assertTrue(result is LocationSelection.Result.Auto)
        assertEquals(1L, (result as LocationSelection.Result.Auto).location.id)
    }

    @Test
    fun `course match with single candidate returns Auto`() {
        val a = loc(1, "A", course = "高等数学")
        val b = loc(2, "B", course = "其他")
        val result = LocationSelection.select(listOf(a, b), course, null)
        assertTrue(result is LocationSelection.Result.Auto)
        assertEquals(1L, (result as LocationSelection.Result.Auto).location.id)
    }

    @Test
    fun `multiple matches with remembered in set returns remembered Auto`() {
        val a = loc(1, "A", course = "高等数学")
        val b = loc(2, "B", course = "高等数学")
        val result = LocationSelection.select(listOf(a, b), course, rememberedLocationId = 2)
        assertTrue(result is LocationSelection.Result.Auto)
        assertEquals(2L, (result as LocationSelection.Result.Auto).location.id)
    }

    @Test
    fun `multiple matches without remembered returns NeedManual with candidates`() {
        val a = loc(1, "A", course = "高等数学")
        val b = loc(2, "B", course = "高等数学")
        val result = LocationSelection.select(listOf(a, b), course, null)
        assertTrue(result is LocationSelection.Result.NeedManual)
        assertEquals(2, (result as LocationSelection.Result.NeedManual).candidates.size)
    }

    @Test
    fun `no course match falls back to remembered`() {
        val a = loc(1, "A", course = "X")
        val b = loc(2, "B", course = "Y")
        val result = LocationSelection.select(listOf(a, b), course, rememberedLocationId = 1)
        assertTrue(result is LocationSelection.Result.Auto)
        assertEquals(1L, (result as LocationSelection.Result.Auto).location.id)
    }

    @Test
    fun `no match no remembered returns NeedManual with all`() {
        val a = loc(1, "A", course = "X")
        val b = loc(2, "B", course = "Y")
        val result = LocationSelection.select(listOf(a, b), course, null)
        assertTrue(result is LocationSelection.Result.NeedManual)
        assertEquals(2, (result as LocationSelection.Result.NeedManual).candidates.size)
    }
}
