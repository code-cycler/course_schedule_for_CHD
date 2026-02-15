package com.example.course_schedule_for_chd_v002.domain.model

/**
 * 学期数据模型
 * 用于表示学年学期信息
 */
data class Semester(
    val year: Int,
    val term: Int
) {
    /**
     * 转换为字符串格式 "2024-2025-1"
     */
    override fun toString(): String = "$year-${year + 1}-$term"

    companion object {
        /**
         * 从字符串解析学期
         * @param semesterStr 学期字符串，格式如 "2024-2025-1"
         * @return 解析后的 Semester 对象
         */
        fun fromString(semesterStr: String): Semester {
            val parts = semesterStr.split("-")
            return if (parts.size == 3) {
                Semester(parts[0].toInt(), parts[2].toInt())
            } else {
                Semester(2024, 1)
            }
        }
    }
}
