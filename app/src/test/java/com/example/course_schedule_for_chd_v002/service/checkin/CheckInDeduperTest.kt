package com.example.course_schedule_for_chd_v002.service.checkin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [v126] 签到会话级去重单测（ADR-0006：归一化课程名 + 2 分钟窗 + ID 优先 + 无键退化纯时间窗）
 */
class CheckInDeduperTest {

    private val t0 = 1_000_000L

    @Before
    fun reset() = CheckInDeduper.reset()

    private fun notification(courseHint: String? = null, checkInId: String? = null, at: Long = t0) =
        CheckInTriggerEvent(
            source = CheckInTriggerSource.Notification,
            timestamp = at,
            courseHint = courseHint,
            checkInId = checkInId
        )

    @Test
    fun `same course within window is suppressed`() {
        assertFalse(CheckInDeduper.shouldSuppress(notification("高等数学"), now = t0))
        assertTrue(CheckInDeduper.shouldSuppress(notification("高等数学"), now = t0 + 30_000))
    }

    @Test
    fun `different courses are not suppressed`() {
        assertFalse(CheckInDeduper.shouldSuppress(notification("高等数学"), now = t0))
        assertFalse(CheckInDeduper.shouldSuppress(notification("大学英语"), now = t0 + 30_000))
    }

    @Test
    fun `course name normalization ignores case and whitespace`() {
        assertFalse(CheckInDeduper.shouldSuppress(notification("高等数学"), now = t0))
        assertTrue(CheckInDeduper.shouldSuppress(notification("  高等数学 "), now = t0 + 10_000))
    }

    @Test
    fun `outside window not suppressed`() {
        val window = 2 * 60 * 1000L
        assertFalse(CheckInDeduper.shouldSuppress(notification("高等数学"), now = t0))
        assertFalse(CheckInDeduper.shouldSuppress(notification("高等数学"), now = t0 + window + 1))
    }

    @Test
    fun `no courseHint degrades to pure time window`() {
        // 无 ID 无 courseHint：共享占位键，同窗内相互抑制（接受误并权衡）
        assertFalse(CheckInDeduper.shouldSuppress(notification(), now = t0))
        assertTrue(CheckInDeduper.shouldSuppress(notification(), now = t0 + 30_000))
    }

    @Test
    fun `checkInId takes precedence over courseHint`() {
        // 同一签到 ID 但课程名不同（轮询与通知两源）→ 抑制；不同 ID 同课程名 → 不抑制
        assertFalse(CheckInDeduper.shouldSuppress(notification("高等数学", checkInId = "sig-1"), now = t0))
        assertTrue(CheckInDeduper.shouldSuppress(notification("大学英语", checkInId = "sig-1"), now = t0 + 5_000))
        assertFalse(CheckInDeduper.shouldSuppress(notification("高等数学", checkInId = "sig-2"), now = t0 + 6_000))
    }

    @Test
    fun `polling source shares session with notification source`() {
        // 多源并存语义：通知源先触发，轮询源同课程同窗命中 → 抑制（同一签到只响应一次）
        assertFalse(CheckInDeduper.shouldSuppress(notification("高等数学"), now = t0))
        val polling = CheckInTriggerEvent(
            source = CheckInTriggerSource.Polling,
            timestamp = t0 + 10_000,
            courseHint = "高等数学"
        )
        assertTrue(CheckInDeduper.shouldSuppress(polling, now = t0 + 10_000))
    }
}
