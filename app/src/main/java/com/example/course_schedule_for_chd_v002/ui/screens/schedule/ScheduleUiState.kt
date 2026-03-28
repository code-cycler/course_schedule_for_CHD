package com.example.course_schedule_for_chd_v002.ui.screens.schedule

import com.example.course_schedule_for_chd_v002.domain.model.Campus
import com.example.course_schedule_for_chd_v002.domain.model.Course
import com.example.course_schedule_for_chd_v002.domain.model.DayOfWeek
import com.example.course_schedule_for_chd_v002.domain.model.ReminderSettings
import com.example.course_schedule_for_chd_v002.util.AppLogger

/**
 * [日历同步] 同步状态
 */
sealed class CalendarSyncState {
    object Idle : CalendarSyncState()           // 空闲
    object Syncing : CalendarSyncState()        // 同步中
    data class Synced(val count: Int) : CalendarSyncState()  // 同步完成
    object Deleting : CalendarSyncState()       // 删除中
    object Deleted : CalendarSyncState()        // 删除完成
    data class Error(val message: String) : CalendarSyncState()  // 错误
}

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
    val conflictingCourseIds: Set<Long> = emptySet(),

    // [v61] 校区选择（影响上课时间显示）
    val campus: Campus = Campus.WEISHUI,

    // ================ [新功能] 当前教学周相关 ================

    // [新功能] 当前实际教学周（根据日期计算或从首页解析）
    val actualCurrentWeek: Int? = null,

    // [新功能] 今天是星期几
    val todayDayOfWeek: DayOfWeek? = null,

    // [新功能] 当前显示周的周一日期（用于表头显示日期）
    val weekStartDate: java.time.LocalDate? = null,

    // [新功能] 水课名称集合（用于 UI 标记）
    val waterCourseNames: Set<String> = emptySet(),

    // ================ [课程提醒] 提醒设置相关 ================

    // [课程提醒] 提醒设置
    val reminderSettings: ReminderSettings = ReminderSettings.DEFAULT,

    // ================ [日历同步] 状态相关 ================

    // [日历同步] 同步状态
    val calendarSyncState: CalendarSyncState = CalendarSyncState.Idle,

    // [优化] 预计算缓存
    val displayCourses: List<Course> = emptyList(),       // 当前周的课程缓存
    val coursesByWeek: Map<Int, List<Course>> = emptyMap() // 按周索引的课程缓存（供 HorizontalPager 使用）
) {
    /**
     * [新功能] 判断课程是否为水课
     */
    fun isWaterCourse(courseName: String): Boolean {
        return courseName in waterCourseNames
    }
    /**
     * [新功能] 判断当前视图是否在实际当前周
     */
    fun isViewingCurrentWeek(): Boolean {
        return actualCurrentWeek != null && currentWeek == actualCurrentWeek
    }

    /**
     * [新功能] 获取标题显示文本
     * 永远显示现实世界的当前教学周
     * 格式: "第5周" 或 "第5周(未设置)"
     */
    fun getTitleText(): String {
        return if (actualCurrentWeek != null) {
            "第${actualCurrentWeek}周"
        } else {
            "第${currentWeek}周"
        }
    }

    /**
     * [新功能] 获取副标题（显示当前视图周次，仅在非当前周时显示）
     * 格式: "查看第3周"
     */
    fun getSubtitleText(): String? {
        return if (actualCurrentWeek != null && currentWeek != actualCurrentWeek) {
            "查看第${currentWeek}周"
        } else {
            null
        }
    }
    /**
     * 检查课程是否存在时间冲突
     * @param course 要检查的课程
     * @return 是否存在冲突
     */
    fun hasConflict(course: Course): Boolean {
        return course.id in conflictingCourseIds
    }

    /**
     * 获取周次范围文本
     */
    fun getWeekRangeText(): String {
        return "Week $currentWeek / $maxWeeks"
    }
}
