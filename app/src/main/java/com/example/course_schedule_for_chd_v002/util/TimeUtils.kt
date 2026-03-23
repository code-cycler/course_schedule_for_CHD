package com.example.course_schedule_for_chd_v002.util

import com.example.course_schedule_for_chd_v002.domain.model.Course

/**
 * 时间工具类
 * 提供课程冲突检测等时间相关功能
 */
object TimeUtils {

    private const val TAG = "CHD_Conflict"

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
