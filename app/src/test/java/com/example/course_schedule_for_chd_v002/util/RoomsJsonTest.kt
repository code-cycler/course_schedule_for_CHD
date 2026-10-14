package com.example.course_schedule_for_chd_v002.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [v126] 教室 tag 集合 JSON 转换单测（迁移三场景 + 转义 + 回环）
 */
class RoomsJsonTest {

    @Test
    fun `encode empty list`() {
        assertEquals("[]", RoomsJson.encode(emptyList()))
    }

    @Test
    fun `encode single and multiple rooms`() {
        assertEquals("[\"WM2303\"]", RoomsJson.encode(listOf("WM2303")))
        assertEquals("[\"WM2303\",\"WM1403\"]", RoomsJson.encode(listOf("WM2303", "WM1403")))
    }

    @Test
    fun `decode round trip`() {
        val rooms = listOf("WM2303机房", "*WM1403", "虚拟教室-同步学习平台")
        assertEquals(rooms, RoomsJson.decode(RoomsJson.encode(rooms)))
    }

    @Test
    fun `decode escapes quotes and backslashes`() {
        val rooms = listOf("含\"引号\"教室", "含\\反斜杠")
        assertEquals(rooms, RoomsJson.decode(RoomsJson.encode(rooms)))
    }

    @Test
    fun `decode null blank and empty returns empty list`() {
        assertEquals(emptyList<String>(), RoomsJson.decode(null))
        assertEquals(emptyList<String>(), RoomsJson.decode(""))
        assertEquals(emptyList<String>(), RoomsJson.decode("[]"))
    }

    @Test
    fun `migration single room to single element array`() {
        assertEquals("[\"WM3201\"]", RoomsJson.fromSingleRoom("WM3201"))
    }

    @Test
    fun `migration null or blank room to empty array`() {
        assertEquals("[]", RoomsJson.fromSingleRoom(null))
        assertEquals("[]", RoomsJson.fromSingleRoom(""))
        assertEquals("[]", RoomsJson.fromSingleRoom("   "))
    }

    @Test
    fun `migration trims surrounding whitespace`() {
        assertEquals("[\"WM3201\"]", RoomsJson.fromSingleRoom(" WM3201 "))
    }
}
