package com.example.course_schedule_for_chd_v002.service.checkin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [v126] NLS 健康判定纯函数单测（24h 阈值 / 从未心跳视为疑似）
 */
class NlsHealthMonitorTest {

    private val t0 = 1_000_000L
    private val day = 24 * 60 * 60 * 1000L

    @Test
    fun `never heartbeated is suspect`() {
        assertTrue(NlsHealthMonitor.shouldSuspect(null, t0))
    }

    @Test
    fun `recent heartbeat is not suspect`() {
        assertFalse(NlsHealthMonitor.shouldSuspect(t0, t0 + 60_000))
    }

    @Test
    fun `heartbeat older than threshold is suspect`() {
        assertTrue(NlsHealthMonitor.shouldSuspect(t0, t0 + day + 1))
        assertFalse(NlsHealthMonitor.shouldSuspect(t0, t0 + day))
    }
}
