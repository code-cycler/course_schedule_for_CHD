package com.example.course_schedule_for_chd_v002.util

import com.example.course_schedule_for_chd_v002.domain.model.Course
import com.example.course_schedule_for_chd_v002.domain.model.DayOfWeek

/**
 * 时间工具类
 * 提供课程冲突检测等时间相关功能
 * [新功能] 添加当前教学周计算功能
 */
object TimeUtils {

    private const val TAG = "CHD_Conflict"
    private const val TAG_WEEK = "CHD_WeekCalc"

    // ================ [新功能] 教学周计算相关 ================

    /**
     * [新功能] 计算当前教学周
     * @param semesterStartDate 学期开始日期 (格式: "yyyy-MM-dd" 如 "2026-02-24")
     * @return 当前教学周 (1-25)，计算失败或超出范围返回 null
     */
    fun calculateCurrentWeek(semesterStartDate: String): Int? {
        android.util.Log.i(TAG_WEEK, "========== calculateCurrentWeek 开始 ==========")
        android.util.Log.i(TAG_WEEK, "学期开始日期: $semesterStartDate")

        return try {
            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val startDate = java.time.LocalDate.parse(semesterStartDate, formatter)
            val today = java.time.LocalDate.now()

            android.util.Log.i(TAG_WEEK, "开始日期: $startDate, 今天: $today")

            // 计算天数差
            val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(startDate, today)
            android.util.Log.i(TAG_WEEK, "天数差: $daysBetween")

            // 转换为周次（向上取整，第一天为第1周）
            // daysBetween=0 -> 第1周, daysBetween=7 -> 第2周
            val week = (daysBetween / 7 + 1).toInt()
            android.util.Log.i(TAG_WEEK, "计算周次: $week")

            // 边界检查
            val result = if (week in 1..25) {
                android.util.Log.i(TAG_WEEK, "========== calculateCurrentWeek 结果: $week ==========")
                week
            } else {
                android.util.Log.w(TAG_WEEK, "周次 $week 超出范围(1-25), 返回 null")
                android.util.Log.i(TAG_WEEK, "========== calculateCurrentWeek 结果: null ==========")
                null
            }
            result
        } catch (e: Exception) {
            android.util.Log.e(TAG_WEEK, "计算教学周失败: ${e.message}")
            android.util.Log.i(TAG_WEEK, "========== calculateCurrentWeek 异常: null ==========")
            null
        }
    }

    /**
     * [新功能] 根据当前教学周反推学期开始日期
     * @param currentWeek 当前教学周 (1-25)
     * @return 学期开始日期字符串 (格式: "yyyy-MM-dd")
     */
    fun calculateSemesterStartDate(currentWeek: Int): String {
        android.util.Log.i(TAG_WEEK, "========== calculateSemesterStartDate 开始 ==========")
        android.util.Log.i(TAG_WEEK, "当前周次: $currentWeek")

        val today = java.time.LocalDate.now()
        // 学期第1周的第1天 = 今天 - (当前周-1)*7天
        val startDate = today.minusDays((currentWeek - 1).toLong() * 7)
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val result = startDate.format(formatter)

        android.util.Log.i(TAG_WEEK, "反推结果: 今天=$today, 学期开始=$result")
        android.util.Log.i(TAG_WEEK, "========== calculateSemesterStartDate 结束 ==========")
        return result
    }

    /**
     * [新功能] 获取今天是星期几
     * @return DayOfWeek 枚举值 (1=周一, 7=周日)
     */
    fun getTodayDayOfWeek(): DayOfWeek {
        val javaDayOfWeek = java.time.LocalDate.now().dayOfWeek.value
        // Java: 1=周一, 7=周日 -> 直接对应我们的 DayOfWeek
        val result = when (javaDayOfWeek) {
            1 -> DayOfWeek.MONDAY
            2 -> DayOfWeek.TUESDAY
            3 -> DayOfWeek.WEDNESDAY
            4 -> DayOfWeek.THURSDAY
            5 -> DayOfWeek.FRIDAY
            6 -> DayOfWeek.SATURDAY
            7 -> DayOfWeek.SUNDAY
            else -> DayOfWeek.MONDAY
        }
        android.util.Log.d(TAG_WEEK, "今天是: $result (Java dayOfWeek=$javaDayOfWeek)")
        return result
    }

    // ================ 课程冲突检测相关 ================

    /**
     * 找出所有存在冲突的课程
     *
     * @param courses 课程列表
     * @return Map<Long, List<Long>> key 为冲突课程ID，value 为与之冲突的其他课程ID列表
     */
    fun findConflicts(courses: List<Course>): Map<Long, List<Long>> {
        android.util.Log.i(TAG, "========== [TimeUtils] findConflicts 开始 ==========")
        android.util.Log.i(TAG, "输入课程数: ${courses.size}")

        val conflicts = mutableMapOf<Long, MutableList<Long>>()
        var checkCount = 0
        var conflictCount = 0

        for (i in courses.indices) {
            for (j in i + 1 until courses.size) {
                checkCount++
                val c1 = courses[i]
                val c2 = courses[j]

                if (c1.hasTimeConflict(c2)) {
                    conflictCount++
                    android.util.Log.i(TAG, "[冲突发现] '${c1.name}'(id=${c1.id}) <-> '${c2.name}'(id=${c2.id})")
                    android.util.Log.i(TAG, "  课程1: 周${c1.dayOfWeek.value} 第${c1.startNode}-${c1.endNode}节 周${c1.startWeek}-${c1.endWeek}")
                    android.util.Log.i(TAG, "  课程2: 周${c2.dayOfWeek.value} 第${c2.startNode}-${c2.endNode}节 周${c2.startWeek}-${c2.endWeek}")

                    conflicts.getOrPut(c1.id) { mutableListOf() }.add(c2.id)
                    conflicts.getOrPut(c2.id) { mutableListOf() }.add(c1.id)
                }
            }
        }

        android.util.Log.i(TAG, "检测结果: 检查${checkCount}对, 发现${conflictCount}对冲突, 涉及${conflicts.size}门课程")
        android.util.Log.i(TAG, "========== [TimeUtils] findConflicts 结束 ==========")
        return conflicts
    }

    /**
     * 检查课程是否与列表中的任何其他课程冲突
     *
     * @param course 要检查的课程
     * @param others 其他课程列表
     * @return 是否存在冲突
     */
    fun hasConflictWithAny(course: Course, others: List<Course>): Boolean {
        return others.any { it.id != course.id && course.hasTimeConflict(it) }
    }

    /**
     * 获取指定周次内的冲突课程
     *
     * @param courses 课程列表
     * @param week 周次
     * @return 冲突课程ID映射
     */
    fun findConflictsForWeek(courses: List<Course>, week: Int): Map<Long, List<Long>> {
        android.util.Log.i(TAG, "========== [TimeUtils] findConflictsForWeek 开始 ==========")
        android.util.Log.i(TAG, "参数: 课程数=${courses.size}, 检测周次=$week")

        val weekCourses = courses.filter { it.isWeekInRange(week) }
        android.util.Log.i(TAG, "当前周有课的课程数: ${weekCourses.size}")

        // 打印该周有课的课程列表
        weekCourses.forEachIndexed { index, course ->
            android.util.Log.i(TAG, "  [$index] ${course.name}: 周${course.dayOfWeek.value} 第${course.startNode}-${course.endNode}节 周${course.startWeek}-${course.endWeek}")
        }

        val result = findConflicts(weekCourses)
        android.util.Log.i(TAG, "========== [TimeUtils] findConflictsForWeek 结束 ==========")
        return result
    }
}
