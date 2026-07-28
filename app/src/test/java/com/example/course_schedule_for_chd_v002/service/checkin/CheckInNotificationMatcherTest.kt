package com.example.course_schedule_for_chd_v002.service.checkin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [v114] 畅课签到通知识别单元测试
 */
class CheckInNotificationMatcherTest {

    private val pkg = CheckInNotificationMatcher.TRONCLASS_PACKAGE

    @Test
    fun `签到 keyword matches`() {
        assertTrue(CheckInNotificationMatcher.isCheckInNotification(pkg, "老师发起了签到，请尽快完成"))
    }

    @Test
    fun `考勤 keyword matches`() {
        assertTrue(CheckInNotificationMatcher.isCheckInNotification(pkg, "考勤提醒：高等数学"))
    }

    @Test
    fun `点名 keyword matches`() {
        assertTrue(CheckInNotificationMatcher.isCheckInNotification(pkg, "老师正在点名"))
    }

    @Test
    fun `no keyword does not match`() {
        assertFalse(CheckInNotificationMatcher.isCheckInNotification(pkg, "作业已发布，请查看"))
    }

    @Test
    fun `wrong package does not match even with keyword`() {
        assertFalse(CheckInNotificationMatcher.isCheckInNotification("com.other.app", "老师发起了签到"))
    }

    @Test
    fun `null package does not match`() {
        assertFalse(CheckInNotificationMatcher.isCheckInNotification(null, "签到"))
    }

    @Test
    fun `empty content does not match`() {
        assertFalse(CheckInNotificationMatcher.isCheckInNotification(pkg, ""))
    }

    @Test
    fun `keyword in title or text both match`() {
        // 模拟 "标题 正文" 拼接
        assertTrue(CheckInNotificationMatcher.isCheckInNotification(pkg, "课程通知 老师发起了签到"))
        assertTrue(CheckInNotificationMatcher.isCheckInNotification(pkg, "签到  "))
    }

    @Test
    fun `custom keywords are used`() {
        assertTrue(
            CheckInNotificationMatcher.isCheckInNotification(pkg, "老师发起了随堂测验", keywords = listOf("测验"))
        )
        assertFalse(
            CheckInNotificationMatcher.isCheckInNotification(pkg, "老师发起了签到", keywords = listOf("测验"))
        )
    }

    @Test
    fun `blank keywords are ignored`() {
        assertFalse(CheckInNotificationMatcher.isCheckInNotification(pkg, "签到", keywords = listOf("", "  ")))
    }

    @Test
    fun `shell package allowed only when allowShell`() {
        val shell = CheckInNotificationMatcher.SHELL_PACKAGE
        assertTrue(CheckInNotificationMatcher.isCheckInNotification(shell, "签到", allowShell = true))
        assertFalse(CheckInNotificationMatcher.isCheckInNotification(shell, "签到", allowShell = false))
    }

    @Test
    fun `isTargetPackage tronclass always, shell only when allowed`() {
        assertTrue(CheckInNotificationMatcher.isTargetPackage(pkg, allowShell = false))
        assertTrue(CheckInNotificationMatcher.isTargetPackage(CheckInNotificationMatcher.SHELL_PACKAGE, allowShell = true))
        assertFalse(CheckInNotificationMatcher.isTargetPackage(CheckInNotificationMatcher.SHELL_PACKAGE, allowShell = false))
        assertFalse(CheckInNotificationMatcher.isTargetPackage("com.other", allowShell = true))
    }

    @Test
    fun `matchesKeywords any match`() {
        assertTrue(CheckInNotificationMatcher.matchesKeywords("老师发起签到", listOf("考勤", "签到")))
        assertFalse(CheckInNotificationMatcher.matchesKeywords("作业发布", listOf("签到", "考勤")))
    }
}
