package com.example.course_schedule_for_chd_v002.util

import com.example.course_schedule_for_chd_v002.domain.model.CheckInLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [v126] 签到位置备份序列化器单测（grill W01 Q3：迁移前备份 / F005 位置导出导入共用）
 */
class CheckInLocationBackupTest {

    @Test
    fun `serialize deserialize round trip preserves all fields`() {
        val locations = listOf(
            CheckInLocation(
                id = 1, name = "图书馆东侧", latitude = 34.000001, longitude = 108.000002,
                accuracyRadiusMeters = 10.5f, note = "二楼",
                linkedCourseId = "高等数学", linkedRoomNames = listOf("WM2303机房", "WM1403"),
                createdAt = 1700000000000L
            ),
            CheckInLocation(
                id = 2, name = "含\"引号\"名称", latitude = -34.5, longitude = 108.7,
                accuracyRadiusMeters = null, note = null,
                linkedCourseId = null, linkedRoomNames = emptyList(),
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
        assertEquals("高等数学", first.linkedCourseId)
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
}
