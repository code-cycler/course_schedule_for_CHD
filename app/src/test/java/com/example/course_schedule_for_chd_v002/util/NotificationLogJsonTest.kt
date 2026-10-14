package com.example.course_schedule_for_chd_v002.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [v126] 畅课通知记录 JSON 序列化 + 环形淘汰单测
 */
class NotificationLogJsonTest {

    @Test
    fun `serialize decode round trip preserves fields`() {
        val log = listOf(
            TronClassNotificationRecord("JPush_4_7", "签到提醒", "高等数学 老师发起签到", 1700000000000L),
            TronClassNotificationRecord(null, null, null, 1700000000001L)
        )
        assertEquals(log, NotificationLogJson.decode(NotificationLogJson.serialize(log)))
    }

    @Test
    fun `empty and null decode to empty list`() {
        assertEquals(emptyList<TronClassNotificationRecord>(), NotificationLogJson.decode(null))
        assertEquals(emptyList<TronClassNotificationRecord>(), NotificationLogJson.decode("[]"))
    }

    @Test
    fun `ring buffer evicts oldest beyond cap`() {
        val initial = (1..50).map { TronClassNotificationRecord("ch", "t$it", "x$it", it.toLong()) }
        val appended = NotificationLogJson.append(
            initial, TronClassNotificationRecord("ch", "t51", "x51", 51L)
        )
        assertEquals(50, appended.size)
        // 最旧（t1）被淘汰，最新在尾部
        assertEquals("t51", appended.last().title)
        assertEquals("t2", appended.first().title)
    }

    @Test
    fun `append below cap keeps all`() {
        val appended = NotificationLogJson.append(
            emptyList(), TronClassNotificationRecord("ch", "t", "x", 1L)
        )
        assertEquals(1, appended.size)
    }

    @Test
    fun `text with quotes and newlines survives round trip`() {
        val log = listOf(TronClassNotificationRecord("ch", "含\"引号\"", "多行\n文本", 1L))
        assertEquals(log, NotificationLogJson.decode(NotificationLogJson.serialize(log)))
    }
}
