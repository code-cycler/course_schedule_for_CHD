package com.example.course_schedule_for_chd_v002.util

import com.example.course_schedule_for_chd_v002.domain.model.Course

/**
 * [v124] 本学期课表统计（L2-build C 批第 ④ 步，L1 契约第 7 条）
 * 纯函数零 IO，可单测；数据源 = 当前学期课程 + 水课名单（DataStore 水课列表按学期）。
 */
data class ScheduleStats(
    val totalCredits: Double,
    val courseCount: Int,
    val totalNodes: Int,
    val waterCourseCount: Int
)

object ScheduleStatsCalculator {

    /**
     * @param courses 当前学期全部课程（含同名多教学班的多条记录，与 Room 行一致）
     * @param waterCourseNames 水课课程名集合（水课按课程名标注，跨学期稳定）
     */
    fun calculate(courses: List<Course>, waterCourseNames: Set<String>): ScheduleStats {
        if (courses.isEmpty()) return ScheduleStats(0.0, 0, 0, 0)

        // 多教学班同名课程学分只计一次（与教务系统一门课一个学分一致）
        val totalCredits = courses.distinctBy { it.name }.sumOf { it.credit }
        // 门数按课程名去重
        val courseCount = courses.distinctBy { it.name }.size
        // 总学时 = 各条记录节次数之和（同名不同教学班是真实占用的不同时段，累加）
        val totalNodes = courses.sumOf { it.endNode - it.startNode + 1 }
        // 水课门数 = 水课名单与课程名集合的交集大小
        val waterCourseCount = courses.map { it.name }.distinct().count { it in waterCourseNames }

        return ScheduleStats(totalCredits, courseCount, totalNodes, waterCourseCount)
    }
}
