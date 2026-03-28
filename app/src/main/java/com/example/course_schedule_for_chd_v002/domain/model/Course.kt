package com.example.course_schedule_for_chd_v002.domain.model

import com.example.course_schedule_for_chd_v002.util.AppLogger

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

    // [优化] 缓存位图解析结果，避免每次 isWeekInRange 都重新正则解析
    private val cachedWeeksBitmap: String? by lazy {
        val bitmapMatch = """weeksBitmap:([01]+)""".toRegex().find(remark)
        bitmapMatch?.groupValues?.get(1)
    }

    /**
     * [v93] 从 remark 中提取周数位图
     * 格式: "weeksBitmap:010101..."
     * @return 位图字符串，如果不存在则返回 null
     */
    private fun extractWeeksBitmap(): String? = cachedWeeksBitmap

    /**
     * [v93] 检查指定周是否在课程周次范围内
     * 优先使用位图精确判断，如果位图不存在则使用范围判断
     * [v96] 修正位图索引：学校系统位图从第0周开始，bitmap[week]对应第week周
     * @param week 周次（1-indexed）
     * @return 是否在范围内
     */
    fun isWeekInRange(week: Int): Boolean {
        // [v93] 尝试使用位图精确判断
        val bitmap = cachedWeeksBitmap
        if (bitmap != null && bitmap.isNotEmpty()) {
            // [v96] 位图索引 = 实际周次（学校系统位图从第0周开始）
            // bitmap[0] = 第0周(预备周), bitmap[1] = 第1周, ...
            if (week in bitmap.indices) {
                return bitmap[week] == '1'
            }
            return false
        }

        // 回退到范围判断
        return week in weekRange
    }

    /**
     * [v93] 获取活跃周列表（从位图提取）
     * [v96] 修正位图索引：学校系统位图从第0周开始，index即为周次
     * @return 活跃周列表，如果位图不存在则返回空列表
     */
    fun getActiveWeeks(): List<Int> {
        val bitmap = extractWeeksBitmap() ?: return emptyList()
        val activeWeeks = mutableListOf<Int>()
        for ((index, char) in bitmap.withIndex()) {
            if (char == '1') {
                activeWeeks.add(index)  // [v96] index即为周次（位图从第0周开始）
            }
        }
        return activeWeeks
    }

    /**
     * [v94] 获取周次显示文本
     * 根据位图生成友好的周次显示，正确处理非连续周次
     * @return 周次显示文本，如 "第1-16周"、"单周 第1-15周"、"第1、4、7周"
     */
    fun getWeeksDisplayText(): String {
        val activeWeeks = getActiveWeeks()

        // 没有位图信息，使用范围显示
        if (activeWeeks.isEmpty()) {
            return "第${startWeek}-${endWeek}周"
        }

        // 检查是否为连续周
        if (isConsecutiveWeeks(activeWeeks)) {
            // 检查单双周模式
            val allOdd = activeWeeks.all { it % 2 == 1 }
            val allEven = activeWeeks.all { it % 2 == 0 }

            return when {
                allOdd && activeWeeks.size > 1 -> "单周 第${activeWeeks.first()}-${activeWeeks.last()}周"
                allEven && activeWeeks.size > 1 -> "双周 第${activeWeeks.first()}-${activeWeeks.last()}周"
                else -> "第${activeWeeks.first()}-${activeWeeks.last()}周"
            }
        }

        // 非连续周，显示具体周次列表
        return "第${activeWeeks.joinToString("、")}周"
    }

    /**
     * [v94] 检查周次列表是否连续
     */
    private fun isConsecutiveWeeks(weeks: List<Int>): Boolean {
        if (weeks.size <= 1) return true
        for (i in 1 until weeks.size) {
            if (weeks[i] != weeks[i - 1] + 1) return false
        }
        return true
    }

    /**
     * [v93] 检查与另一门课程是否存在时间冲突
     * 使用位图精确判断周次重叠，避免非连续周的错误冲突判断
     * @param other 另一门课程
     * @return 是否存在冲突
     */
    fun hasTimeConflict(other: Course): Boolean {
        // 不同星期不冲突
        if (dayOfWeek != other.dayOfWeek) return false

        // [优化] 使用缓存位图精确判断周次交集
        val thisBitmap = cachedWeeksBitmap
        val otherBitmap = other.cachedWeeksBitmap

        val hasWeekOverlap = if (thisBitmap != null && otherBitmap != null) {
            val minLen = minOf(thisBitmap.length, otherBitmap.length)
            var hasOverlap = false
            for (i in 0 until minLen) {
                if (thisBitmap[i] == '1' && otherBitmap[i] == '1') {
                    hasOverlap = true
                    break
                }
            }
            hasOverlap
        } else {
            weekRange.intersect(other.weekRange).isNotEmpty()
        }

        if (!hasWeekOverlap) return false

        // 节次范围有交集则冲突
        return nodeRange.intersect(other.nodeRange).isNotEmpty()
    }
}
