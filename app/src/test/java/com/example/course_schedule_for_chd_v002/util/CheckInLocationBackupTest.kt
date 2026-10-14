package com.example.course_schedule_for_chd_v002.util

import com.example.course_schedule_for_chd_v002.domain.model.CheckInLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [v126] 签到位置备份序列化器单测（grill W01 Q3：迁移前备份 / F005 位置导出导入共用）
 * [v134] 课程集合化（linkedCourseIds 数组）+ 旧格式（linkedCourseId 单值）导入兼容
 */
class CheckInLocationBackupTest {

    @Test
    fun `serialize deserialize round trip preserves all fields`() {
        val locations = listOf(
            CheckInLocation(
                id = 1, name = "图书馆东侧", latitude = 34.000001, longitude = 108.000002,
                accuracyRadiusMeters = 10.5f, note = "二楼",
                linkedCourseIds = listOf("高等数学", "数据库（英）"), linkedRoomNames = listOf("WM2303机房", "WM1403"),
                createdAt = 1700000000000L
            ),
            CheckInLocation(
                id = 2, name = "含\"引号\"名称", latitude = -34.5, longitude = 108.7,
                accuracyRadiusMeters = null, note = null,
                linkedCourseIds = emptyList(), linkedRoomNames = emptyList(),
                createdAt = 1700000000001L
            )
        )
        val json = CheckInLocationBackup.serialize(locations, """{"高等数学|WM2303机房":1}""")
        val (parsed, memoryRaw) = CheckInLocationBackup.deserialize(json)

        assertEquals(2, parsed.size)
        val first = parsed[0]
        assertEquals(1L, first.id)
        assertEquals("图书馆东侧", first.name)
        assertEquals(34.000001, first.latitude, 1e-9)
        assertEquals(108.000002, first.longitude, 1e-9)
        assertEquals(10.5f, first.accuracyRadiusMeters)
        assertEquals("二楼", first.note)
        assertEquals(listOf("高等数学", "数据库（英）"), first.linkedCourseIds)
        assertEquals(listOf("WM2303机房", "WM1403"), first.linkedRoomNames)
        assertEquals(1700000000000L, first.createdAt)
        assertEquals("含\"引号\"名称", parsed[1].name)
        assertNull(parsed[1].note)
        assertEquals("""{"高等数学|WM2303机房":1}""", memoryRaw)
    }

    @Test
    fun `empty locations serialize to empty list`() {
        val json = CheckInLocationBackup.serialize(emptyList(), null)
        val (parsed, memoryRaw) = CheckInLocationBackup.deserialize(json)
        assertEquals(0, parsed.size)
        assertNull(memoryRaw)
    }

    // ===== [v134] 旧格式（v133 及更早导出文件：linkedCourseId 单值）导入兼容 =====

    @Test
    fun `v134 legacy single linkedCourseId field imported as single element list`() {
        val legacy = """
            {"locations":[{"id":7,"name":"旧导出位置","latitude":34.1,"longitude":108.2,
            "accuracyRadiusMeters":null,"note":null,
            "linkedCourseId":"大学英语",
            "linkedRoomNames":["WM101"],
            "createdAt":1700000000002}],"locationMemoryV1":null}
        """.trimIndent()
        val (parsed, _) = CheckInLocationBackup.deserialize(legacy)
        assertEquals(1, parsed.size)
        assertEquals(listOf("大学英语"), parsed[0].linkedCourseIds)
        assertEquals(listOf("WM101"), parsed[0].linkedRoomNames)
    }

    @Test
    fun `v134 legacy null linkedCourseId imports as empty list`() {
        val legacy = """
            {"locations":[{"id":8,"name":"无关联位置","latitude":34.1,"longitude":108.2,
            "accuracyRadiusMeters":null,"note":null,
            "linkedCourseId":null,
            "linkedRoomNames":[],
            "createdAt":1700000000003}],"locationMemoryV1":null}
        """.trimIndent()
        val (parsed, _) = CheckInLocationBackup.deserialize(legacy)
        assertEquals(1, parsed.size)
        assertEquals(emptyList<String>(), parsed[0].linkedCourseIds)
    }

    @Test
    fun `v134 new array field takes precedence over legacy field`() {
        val json = """
            {"locations":[{"id":9,"name":"新格式","latitude":34.1,"longitude":108.2,
            "accuracyRadiusMeters":null,"note":null,
            "linkedCourseIds":["A课","B课"],
            "linkedCourseId":"旧值应被忽略",
            "linkedRoomNames":[],
            "createdAt":1700000000004}],"locationMemoryV1":null}
        """.trimIndent()
        val (parsed, _) = CheckInLocationBackup.deserialize(json)
        assertEquals(listOf("A课", "B课"), parsed[0].linkedCourseIds)
    }
}
