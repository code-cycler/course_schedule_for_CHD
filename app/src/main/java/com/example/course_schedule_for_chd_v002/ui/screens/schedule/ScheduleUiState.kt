package com.example.course_schedule_for_chd_v002.ui.screens.schedule

import com.example.course_schedule_for_chd_v002.domain.model.Campus
import com.example.course_schedule_for_chd_v002.domain.model.Course
import com.example.course_schedule_for_chd_v002.domain.model.CourseType
import com.example.course_schedule_for_chd_v002.domain.model.DayOfWeek
import com.example.course_schedule_for_chd_v002.domain.model.SemesterOption
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

    // [切换学期] 本地已存的所有学期（供学期选择器列出）
    val allSemesters: List<String> = emptyList(),

    // ================ [获取新学期] 远程学期抓取 ================
    /** 教务系统返回、智能筛选后的候选学期（前2+当前+往后1） */
    val remoteSemesterOptions: List<SemesterOption> = emptyList(),
    /** 正在获取候选列表 / 正在抓取指定学期 */
    val isFetchingSemester: Boolean = false,
    /** 抓取错误信息（Cookie 过期 / 网络失败 / 该学期无课） */
    val fetchSemesterError: String? = null,

    // ================ [跨学期] 学期过期检测 ================
    /** 新学期横幅（本地 currentSemester < 日期推断学期 且 当日未关闭）；null = 不显示 */
    val newSemesterBanner: NewSemesterBanner? = null,
    /** 横幅点击后正在免登录获取/抓取新学期 */
    val isFetchingNewSemester: Boolean = false,
    /** 新学期获取失败提示（登录过期 / 教务未发布） */
    val newSemesterError: String? = null,

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
 * [跨学期] 新学期横幅数据
 * @param inferredSemester 日期推断的应处学期（本地串，如 "2026-2027-1"）
 * @param localExists 本地 Room 是否已有该学期课表（有则点击直接切换，无则走免登录抓取）
 */
data class NewSemesterBanner(
    val inferredSemester: String,
    val localExists: Boolean
)

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
