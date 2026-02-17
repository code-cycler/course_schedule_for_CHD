package com.example.course_schedule_for_chd_v002.ui.screens.schedule

import com.example.course_schedule_for_chd_v002.domain.model.Course
import com.example.course_schedule_for_chd_v002.domain.model.DayOfWeek

/**
 * 课程表界面UI状态
 * 使用单向数据流管理状态
 */
data class ScheduleUiState(
    // 学期信息
    val semester: String = "",

    // 课表数据
    val courses: List<Course> = emptyList(),

    // UI 状态
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,

    // 周次选择
    val currentWeek: Int = 1,
    val maxWeeks: Int = 16,

    // 登出状态
    val isLoggedOut: Boolean = false,

    // 选中的课程（用于显示详情）
    val selectedCourse: Course? = null,

    // 冲突课程ID集合（用于UI标记）
    val conflictingCourseIds: Set<Long> = emptySet()
) {
    /**
     * 检查课程是否存在时间冲突
     * @param course 要检查的课程
     * @return 是否存在冲突
     */
    fun hasConflict(course: Course): Boolean {
        return course.id in conflictingCourseIds
    }
    /**
     * 获取当前周次显示的课程
     * 筛选出当前选中周次内的课程
     */
    fun getDisplayCourses(): List<Course> {
        return courses.filter { it.isWeekInRange(currentWeek) }
    }

    /**
     * 获取指定星期和节次的课程
     *
     * @param day 星期几
     * @param node 节次 (1-12)
     * @return 匹配的课程，如果没有则返回null
     */
    fun getCourseAt(day: DayOfWeek, node: Int): Course? {
        return getDisplayCourses().find { course ->
            course.dayOfWeek == day && node in course.nodeRange
        }
    }

    /**
     * 获取周次范围文本
     */
    fun getWeekRangeText(): String {
        return "Week $currentWeek / $maxWeeks"
    }
}
