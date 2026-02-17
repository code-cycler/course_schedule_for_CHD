package com.example.course_schedule_for_chd_v002.util

import com.example.course_schedule_for_chd_v002.domain.model.Course

/**
 * 时间工具类
 * 提供课程冲突检测等时间相关功能
 */
object TimeUtils {

    /**
     * 找出所有存在冲突的课程
     *
     * @param courses 课程列表
     * @return Map<Long, List<Long>> key 为冲突课程ID，value 为与之冲突的其他课程ID列表
     */
    fun findConflicts(courses: List<Course>): Map<Long, List<Long>> {
        val conflicts = mutableMapOf<Long, MutableList<Long>>()

        for (i in courses.indices) {
            for (j in i + 1 until courses.size) {
                if (courses[i].hasTimeConflict(courses[j])) {
                    conflicts.getOrPut(courses[i].id) { mutableListOf() }.add(courses[j].id)
                    conflicts.getOrPut(courses[j].id) { mutableListOf() }.add(courses[i].id)
                }
            }
        }

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
        val weekCourses = courses.filter { it.isWeekInRange(week) }
        return findConflicts(weekCourses)
    }
}
