package com.example.course_schedule_for_chd_v002.domain.model

/**
 * 课程领域模型
 * 表示一门课程的所有信息
 */
data class Course(
    val id: Long = 0,
    val name: String,
    val teacher: String,
    val location: String,
    val dayOfWeek: DayOfWeek,
    val startWeek: Int,
    val endWeek: Int,
    val startNode: Int,
    val endNode: Int,
    val courseType: CourseType,
    val credit: Double,
    val remark: String = "",
    val semester: String
) {
    /**
     * 获取周次范围
     */
    val weekRange: IntRange get() = startWeek..endWeek

    /**
     * 获取节次范围
     */
    val nodeRange: IntRange get() = startNode..endNode

    /**
     * 检查指定周是否在课程周次范围内
     * @param week 周次
     * @return 是否在范围内
     */
    fun isWeekInRange(week: Int): Boolean = week in weekRange

    /**
     * 检查与另一门课程是否存在时间冲突
     * @param other 另一门课程
     * @return 是否存在冲突
     */
    fun hasTimeConflict(other: Course): Boolean {
        // 不同星期不冲突
        if (dayOfWeek != other.dayOfWeek) return false

        // 周次范围无交集则不冲突
        if (weekRange.intersect(other.weekRange).isEmpty()) return false

        // 节次范围有交集则冲突
        return nodeRange.intersect(other.nodeRange).isNotEmpty()
    }
}
