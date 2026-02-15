package com.example.course_schedule_for_chd_v002.domain.model

/**
 * 课程类型枚举
 * 用于区分不同类型的课程
 */
enum class CourseType(val displayName: String) {
    REQUIRED("必修"),
    ELECTIVE("选修"),
    PUBLIC_ELECTIVE("公选"),
    PHYSICAL_EDUCATION("体育"),
    PRACTICE("实践"),
    OTHER("其他");

    companion object {
        /**
         * 从中文名称获取对应的课程类型
         * @param type 课程类型名称
         * @return 对应的 CourseType，未匹配则返回 OTHER
         */
        fun fromString(type: String): CourseType {
            return entries.find { it.displayName == type } ?: OTHER
        }
    }
}
