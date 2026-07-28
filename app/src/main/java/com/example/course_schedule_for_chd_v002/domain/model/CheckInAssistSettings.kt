package com.example.course_schedule_for_chd_v002.domain.model

/**
 * [v114] 签到辅助设置
 *
 * @param autoOpenChaoqing 收到签到触发后是否自动打开畅课，默认关闭
 * @param mockDurationMinutes 虚拟定位单次保持时长（分钟），范围 1–10，默认 10
 */
data class CheckInAssistSettings(
    val autoOpenChaoqing: Boolean = false,
    val mockDurationMinutes: Int = DEFAULT_MOCK_DURATION_MINUTES
) {
    companion object {
        const val DEFAULT_MOCK_DURATION_MINUTES = 10
        const val MIN_MOCK_DURATION_MINUTES = 1
        const val MAX_MOCK_DURATION_MINUTES = 10

        val DEFAULT = CheckInAssistSettings()
    }
}
