package com.example.course_schedule_for_chd_v002.domain.model

/**
 * 星期枚举
 * 用于表示课程所在的星期几
 */
enum class DayOfWeek(val value: Int) {
    MONDAY(1),
    TUESDAY(2),
    WEDNESDAY(3),
    THURSDAY(4),
    FRIDAY(5),
    SATURDAY(6),
    SUNDAY(7);

    companion object {
        /**
         * 从整数值获取对应的星期枚举
         * @param value 星期值 (1-7)
         * @return 对应的 DayOfWeek，默认返回 MONDAY
         */
        fun fromValue(value: Int): DayOfWeek = entries.find { it.value == value } ?: MONDAY
    }
}
