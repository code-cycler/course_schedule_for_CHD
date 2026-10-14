package com.example.course_schedule_for_chd_v002.service.checkin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [v126] 自测通知白名单单测：本包名 + checkin_test 私有标记双条件，缺一不放行
 */
class SelfTestNotificationTest {

    private val self = "com.example.course_schedule_for_chd_v002"

    @Test
    fun `both conditions pass`() {
        assertTrue(CheckInNotificationMatcher.isSelfTestNotification(self, self, hasTestExtra = true))
    }

    @Test
    fun `missing test extra rejected`() {
        assertFalse(CheckInNotificationMatcher.isSelfTestNotification(self, self, hasTestExtra = false))
    }

    @Test
    fun `foreign package with test extra rejected`() {
        // 其它 App 伪造不了本包通知，但防御性验证：包名不匹配即拒绝
        assertFalse(
            CheckInNotificationMatcher.isSelfTestNotification(
                "com.wisdomgarden.trpc", self, hasTestExtra = true
            )
        )
    }

    @Test
    fun `null package rejected`() {
        assertFalse(CheckInNotificationMatcher.isSelfTestNotification(null, self, hasTestExtra = true))
    }
}
