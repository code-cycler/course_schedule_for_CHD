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
    // [v37] 删除 isRefreshing，不再需要刷新功能
    val errorMessage: String? = null,

    // 周次选择
    val currentWeek: Int = 1,
    val maxWeeks: Int = 16,  // [v35] 改为更合理的默认值（通常学期16周），ViewModel会动态更新

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
        val result = courses.filter { it.isWeekInRange(currentWeek) }
        android.util.Log.d("ScheduleUiState", "[v35] getDisplayCourses: 总课程=${courses.size}, 当前周=$currentWeek, 过滤后=${result.size}")
        if (result.isEmpty() && courses.isNotEmpty()) {
            // 调试：打印每门课程的周次范围
            courses.forEachIndexed { index, course ->
                android.util.Log.d("ScheduleUiState", "[v35] 课程[$index]: ${course.name}, 周${course.startWeek}-${course.endWeek}")
            }
        }
        return result
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
