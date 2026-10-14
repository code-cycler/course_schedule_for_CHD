package com.example.course_schedule_for_chd_v002.service.checkin

/**
 * [v126] courseHint 提取器（checkin-assist-v2 B2）。
 *
 * 规则（L1 契约 2 + grill W01 Q2）：
 * - 通知标题/正文与当前学期课表课程名做双向子串匹配（contains），命中取课程名；
 * - 多课程歧义取最长名（课名越长越具体）；
 * - 提取为空的兜底（课表推断「当前节次正在上的课」）由 Coordinator 复用 computeCurrentCourse 完成，
 *   不在本对象内重复查询。
 *
 * 纯函数，JVM 可测。
 */
object CourseHintExtractor {

    /**
     * @param content 通知标题 + 正文拼接文本
     * @param courseNames 当前学期课程名集合（去重后）
     * @return 命中的课程名；无命中返回 null
     */
    fun extract(content: String, courseNames: Collection<String>): String? {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return null
        return courseNames
            .filter { name ->
                name.isNotBlank() && (content.contains(name) || name.contains(trimmed))
            }
            .maxByOrNull { it.length }
    }
}
