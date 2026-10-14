package com.example.course_schedule_for_chd_v002.service.mocklocation

import com.example.course_schedule_for_chd_v002.domain.model.CheckInLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [v114] 触发时选位置规则单元测试
 * [v126] 教室 tag 集合语义重写：任一 tag 命中 + 活跃卡教室优先 + 无活跃卡回退全并集（ADR-0007 / S6）
 */
class LocationSelectionTest {

    private fun loc(
        id: Long,
        name: String,
        course: String? = null,
        rooms: List<String> = emptyList()
    ) = CheckInLocation(id, name, 34.0, 108.0, linkedCourseId = course, linkedRoomNames = rooms)

    private fun select(
        locations: List<CheckInLocation>,
        courseName: String?,
        activeRooms: Set<String> = emptySet(),
        fallbackRooms: Set<String> = emptySet(),
        rememberedLocationId: Long? = null
    ) = LocationSelection.select(locations, courseName, activeRooms, fallbackRooms, rememberedLocationId)

    @Test
    fun `empty locations returns Empty`() {
        assertEquals(
            LocationSelection.Result.Empty,
            select(emptyList(), null)
        )
    }

    @Test
    fun `single location returns Auto regardless of course`() {
        val only = loc(1, "A")
        val result = select(listOf(only), null)
        assertTrue(result is LocationSelection.Result.Auto)
        assertEquals(1L, (result as LocationSelection.Result.Auto).location.id)
    }

    @Test
    fun `course match with single candidate returns Auto`() {
        val a = loc(1, "A", course = "高等数学")
        val b = loc(2, "B", course = "其他")
        val result = select(listOf(a, b), "高等数学")
        assertTrue(result is LocationSelection.Result.Auto)
        assertEquals(1L, (result as LocationSelection.Result.Auto).location.id)
    }

    @Test
    fun `multiple matches with remembered in set returns remembered Auto`() {
        val a = loc(1, "A", course = "高等数学")
        val b = loc(2, "B", course = "高等数学")
        val result = select(listOf(a, b), "高等数学", rememberedLocationId = 2)
        assertTrue(result is LocationSelection.Result.Auto)
        assertEquals(2L, (result as LocationSelection.Result.Auto).location.id)
    }

    @Test
    fun `multiple matches without remembered returns NeedManual with candidates`() {
        val a = loc(1, "A", course = "高等数学")
        val b = loc(2, "B", course = "高等数学")
        val result = select(listOf(a, b), "高等数学")
        assertTrue(result is LocationSelection.Result.NeedManual)
        assertEquals(2, (result as LocationSelection.Result.NeedManual).candidates.size)
    }

    @Test
    fun `no course match falls back to remembered`() {
        val a = loc(1, "A", course = "X")
        val b = loc(2, "B", course = "Y")
        val result = select(listOf(a, b), "高等数学", rememberedLocationId = 1)
        assertTrue(result is LocationSelection.Result.Auto)
        assertEquals(1L, (result as LocationSelection.Result.Auto).location.id)
    }

    @Test
    fun `no match no remembered returns NeedManual with all`() {
        val a = loc(1, "A", course = "X")
        val b = loc(2, "B", course = "Y")
        val result = select(listOf(a, b), "高等数学")
        assertTrue(result is LocationSelection.Result.NeedManual)
        assertEquals(2, (result as LocationSelection.Result.NeedManual).candidates.size)
    }

    // ===== [v126] 教室 tag 集合语义 =====

    @Test
    fun `v126 any room tag hit in active rooms matches`() {
        // 位置挂多教室 tag，活跃卡教室是其中之一 → 任一命中即选中
        val a = loc(1, "A", rooms = listOf("WM3305", "WM1403"))
        val b = loc(2, "B", rooms = listOf("WM9999"))
        val result = select(listOf(a, b), null, activeRooms = setOf("WM1403"))
        assertTrue(result is LocationSelection.Result.Auto)
        assertEquals(1L, (result as LocationSelection.Result.Auto).location.id)
    }

    @Test
    fun `v126 active rooms empty falls back to course room union`() {
        // 本周无活跃卡（course=null 也无 activeRooms），用 fallbackRooms 全并集匹配
        val a = loc(1, "A", rooms = listOf("WM2303机房"))
        val b = loc(2, "B", rooms = listOf("WM8888"))
        val result = select(
            listOf(a, b), null,
            activeRooms = emptySet(),
            fallbackRooms = setOf("WM2303机房", "WM2301机房")
        )
        assertTrue(result is LocationSelection.Result.Auto)
        assertEquals(1L, (result as LocationSelection.Result.Auto).location.id)
    }

    @Test
    fun `v126 active rooms take precedence over fallback`() {
        // 活跃卡教室（线上周 = 虚拟教室）优先于全并集：分周换教室课不选错线下教室位置
        val offline = loc(1, "线下教室位", rooms = listOf("WM1403"))
        val online = loc(2, "线上位", rooms = listOf("虚拟教室-同步学习平台"))
        val result = select(
            listOf(offline, online), "高等数学",
            activeRooms = setOf("虚拟教室-同步学习平台"),
            fallbackRooms = setOf("虚拟教室-同步学习平台", "WM1403")
        )
        assertTrue(result is LocationSelection.Result.Auto)
        assertEquals(2L, (result as LocationSelection.Result.Auto).location.id)
    }

    @Test
    fun `v126 course card with multiple rooms parses comma location`() {
        // 活跃卡 location 为逗号连接多教室（v120），rooms 集合全参与匹配
        val a = loc(1, "机房位", rooms = listOf("WM2301机房"))
        val b = loc(2, "B", rooms = listOf("WM8888"))
        val result = select(
            listOf(a, b), "高等数学",
            activeRooms = setOf("*WM2303机房", "*WM2301机房")
        )
        assertTrue(result is LocationSelection.Result.Auto)
        assertEquals(1L, (result as LocationSelection.Result.Auto).location.id)
    }
}
