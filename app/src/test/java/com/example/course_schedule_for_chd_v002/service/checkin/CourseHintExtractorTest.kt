package com.example.course_schedule_for_chd_v002.service.checkin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [v126] courseHint 提取单测（子串匹配 / 双向 contains / 多课程歧义取最长名）
 */
class CourseHintExtractorTest {

    private val courses = setOf("高等数学", "高等数学（下）", "大学英语", "人工智能导论")

    @Test
    fun `notification text containing course name matches`() {
        assertEquals(
            "高等数学",
            CourseHintExtractor.extract("高等数学 老师发起了一个签到，请及时参与", courses)
        )
    }

    @Test
    fun `no match returns null`() {
        assertNull(CourseHintExtractor.extract("老师发起了一个签到", courses))
    }

    @Test
    fun `multiple ambiguous matches pick longest name`() {
        // 「高等数学（下）」与「高等数学」同时命中 → 取更具体（更长）的
        assertEquals(
            "高等数学（下）",
            CourseHintExtractor.extract("高等数学（下）开始签到", courses)
        )
    }

    @Test
    fun `reverse containment also matches`() {
        // 通知文本是课程名的子串（如只发课程名本体）
        assertEquals(
            "大学英语",
            CourseHintExtractor.extract("大学英语", courses)
        )
    }

    @Test
    fun `blank content returns null`() {
        assertNull(CourseHintExtractor.extract("", courses))
        assertNull(CourseHintExtractor.extract("   ", courses))
    }
}
