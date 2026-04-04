package com.example.course_schedule_for_chd_v002.ui.screens.schedule

import com.example.course_schedule_for_chd_v002.domain.model.Campus
import com.example.course_schedule_for_chd_v002.domain.model.Course
import com.example.course_schedule_for_chd_v002.domain.model.CourseType
import com.example.course_schedule_for_chd_v002.domain.model.DayOfWeek
import com.example.course_schedule_for_chd_v002.util.AppLogger
import java.io.File

/**
 * 日历同步状态
 */
sealed class CalendarSyncState {
    object Idle : CalendarSyncState()
    object Syncing : CalendarSyncState()
    data class Synced(val count: Int) : CalendarSyncState()
    object Deleting : CalendarSyncState()
    object Deleted : CalendarSyncState()
    data class Error(val message: String) : CalendarSyncState()
}

/**
 * 课程表界面UI状态
 */
data class ScheduleUiState(
    val semester: String = "",
    val courses: List<Course> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val currentWeek: Int = 1,
    val maxWeeks: Int = 16,
    val isLoggedOut: Boolean = false,
    val selectedCourse: Course? = null,
    val conflictingCourseIds: Set<Long> = emptySet(),
    val campus: Campus = Campus.WEISHUI,

    // 当前教学周相关
    val actualCurrentWeek: Int? = null,
    val todayDayOfWeek: DayOfWeek? = null,
    val weekStartDate: java.time.LocalDate? = null,
    val waterCourseNames: Set<String> = emptySet(),

    // 日历同步状态
    val calendarSyncState: CalendarSyncState = CalendarSyncState.Idle,

    // 预计算缓存
    val displayCourses: List<Course> = emptyList(),
    val coursesByWeek: Map<Int, List<Course>> = emptyMap(),

    // 课程编辑
    val editCourseGroup: CourseEditGroup? = null,
    val suggestedTeachers: List<String> = emptyList(),
    val suggestedLocations: List<String> = emptyList(),
    val editConflicts: List<CourseConflictInfo> = emptyList(),

    // 课程识别错误报告
    val showCourseReport: Boolean = false,
    val reportTargetCourse: Course? = null,
    val reportState: ReportState = ReportState.Idle
) {
    fun isWaterCourse(courseName: String): Boolean {
        return courseName in waterCourseNames
    }

    fun isViewingCurrentWeek(): Boolean {
        return actualCurrentWeek != null && currentWeek == actualCurrentWeek
    }

    fun getTitleText(): String {
        return if (actualCurrentWeek != null) {
            "第${actualCurrentWeek}周"
        } else {
            "第${currentWeek}周"
        }
    }

    fun getSubtitleText(): String? {
        return if (actualCurrentWeek != null && currentWeek != actualCurrentWeek) {
            "查看第${currentWeek}周"
        } else {
            null
        }
    }

    fun hasConflict(course: Course): Boolean {
        return course.id in conflictingCourseIds
    }

    fun getWeekRangeText(): String {
        return "Week $currentWeek / $maxWeeks"
    }
}

/**
 * 同名课程编辑组
 */
data class CourseEditGroup(
    val courseName: String,
    val semester: String,
    val instances: List<Course>,
    val courseType: CourseType,
    val credit: Double
)

/**
 * 课程冲突信息
 */
data class CourseConflictInfo(
    val course1: Course,
    val course2: Course,
    val overlappingWeeks: List<Int>
)

/**
 * 报告生成状态
 */
sealed class ReportState {
    object Idle : ReportState()
    object Generating : ReportState()
    data class Success(val file: File) : ReportState()
    data class Error(val message: String) : ReportState()
}
