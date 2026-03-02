package com.example.course_schedule_for_chd_v002.ui.screens.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.course_schedule_for_chd_v002.data.local.preferences.UserPreferences
import com.example.course_schedule_for_chd_v002.domain.model.Campus
import com.example.course_schedule_for_chd_v002.domain.model.Course
import com.example.course_schedule_for_chd_v002.domain.repository.ICourseRepository
import com.example.course_schedule_for_chd_v002.util.Constants
import com.example.course_schedule_for_chd_v002.util.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 课程表界面 ViewModel
 * 管理课程表显示、周次选择、刷新等逻辑
 *
 * @param repository 课程仓库接口
 * @param userPreferences 用户偏好设置 [v61]
 * @param semester 当前学期
 */
class ScheduleViewModel(
    private val repository: ICourseRepository,
    private val userPreferences: UserPreferences,  // [v61] 新增
    private val semester: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState(semester = semester))
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    // [v37] 添加初始化保护，防止启动崩溃
    init {
        try {
            android.util.Log.d("ScheduleViewModel", "[v37] 初始化，学期: $semester")
            loadCampus()  // [v61] 先加载校区设置
            loadSchedule()
        } catch (e: Exception) {
            android.util.Log.e("ScheduleViewModel", "[v37] 初始化失败: ${e.message}", e)
            _uiState.update { it.copy(isLoading = false, errorMessage = "初始化失败: ${e.message}") }
        }
    }

    /**
     * 重新加载课程数据
     * 用于从登录页返回后刷新数据
     */
    fun reload() {
        android.util.Log.d("ScheduleViewModel", "[v24] reload() 被调用")
        loadSchedule()
    }

    /**
     * [v61] 加载保存的校区设置
     */
    private fun loadCampus() {
        viewModelScope.launch {
            val campusName = userPreferences.getCampusOnce()
            val campus = Campus.fromName(campusName)
            android.util.Log.d("ScheduleViewModel", "[v61] 加载校区: $campusName")
            _uiState.update { it.copy(campus = campus) }
        }
    }

    /**
     * [v61] 切换校区
     * @param campus 新的校区
     */
    fun onCampusChanged(campus: Campus) {
        viewModelScope.launch {
            android.util.Log.d("ScheduleViewModel", "[v61] 切换校区: ${campus.name}")
            userPreferences.saveCampus(campus.name)
            _uiState.update { it.copy(campus = campus) }
        }
    }

    /**
     * 加载本地课表
     * [v35] 加载后自动选择第一个有课的周次，动态设置最大周数
     */
    private fun loadSchedule() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val courses = repository.getLocalSchedule(semester)
            val conflicts = TimeUtils.findConflicts(courses)

            // [v35] 计算第一个和最后一个有课的周次
            val firstWeek = findFirstWeekWithCourse(courses)
            val maxWeek = findMaxWeekWithCourse(courses)

            android.util.Log.d("ScheduleViewModel", "[v35] loadSchedule 完成，课程数: ${courses.size}, 显示周: $firstWeek, 最大周: $maxWeek")

            _uiState.update {
                it.copy(
                    courses = courses,
                    conflictingCourseIds = conflicts.keys,
                    currentWeek = firstWeek,
                    maxWeeks = maxWeek,  // [v35] 动态设置最大周数
                    isLoading = false
                )
            }
        }
    }

    /**
     * [v34] 找到应该显示的周次
     * 忽略当前日期的影响，自动选择第一个有课的周次
     * 如果没有任何课程，默认显示第一周
     */
    private fun findFirstWeekWithCourse(courses: List<Course>): Int {
        if (courses.isEmpty()) {
            android.util.Log.d("ScheduleViewModel", "[v35] 无课程，默认第一周")
            return 1
        }

        // [v34] 找到所有课程中最早的开始周次
        var minStartWeek = Int.MAX_VALUE
        for (course in courses) {
            if (course.startWeek < minStartWeek) {
                minStartWeek = course.startWeek
            }
        }

        val firstWeek = if (minStartWeek == Int.MAX_VALUE) 1 else minStartWeek
        android.util.Log.d("ScheduleViewModel", "[v35] 第一个有课的周次: $firstWeek")
        return firstWeek
    }

    /**
     * [v35] 找到最后一个有课的周次
     * 用于动态设置周选择器的最大值
     */
    private fun findMaxWeekWithCourse(courses: List<Course>): Int {
        if (courses.isEmpty()) {
            android.util.Log.d("ScheduleViewModel", "[v35] 无课程，默认最大25周")
            return Constants.Schedule.MAX_WEEKS
        }

        var maxEndWeek = 0
        for (course in courses) {
            if (course.endWeek > maxEndWeek) {
                maxEndWeek = course.endWeek
            }
        }

        val result = if (maxEndWeek == 0) Constants.Schedule.MAX_WEEKS else maxEndWeek
        android.util.Log.d("ScheduleViewModel", "[v35] 最大周次: $result")
        return result
    }

    /**
     * 选择周次
     *
     * @param week 周次 (1-16)
     */
    fun onWeekSelected(week: Int) {
        _uiState.update {
            it.copy(
                currentWeek = week.coerceIn(1, it.maxWeeks)
            )
        }
    }

    // [v37] 删除 refreshSchedule() 方法，不再需要刷新功能

    /**
     * 选择课程（用于显示详情）
     *
     * @param course 选中的课程，null表示取消选择
     */
    fun onCourseSelected(course: Course?) {
        _uiState.update { it.copy(selectedCourse = course) }
    }

    /**
     * 关闭错误提示
     */
    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * 登出
     */
    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _uiState.update { it.copy(isLoggedOut = true) }
        }
    }

    // ================ 导入导出相关 ================

    private val _exportResult = MutableStateFlow<ExportResult?>(null)
    val exportResult: StateFlow<ExportResult?> = _exportResult.asStateFlow()

    private val _importResult = MutableStateFlow<ImportResult?>(null)
    val importResult: StateFlow<ImportResult?> = _importResult.asStateFlow()

    /**
     * 导出课表为 JSON
     */
    fun exportSchedule() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val json = repository.exportScheduleToJson(semester)
                _exportResult.value = ExportResult.Success(json)
            } catch (e: Exception) {
                _exportResult.value = ExportResult.Error(e.message ?: "Export failed")
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * 从 JSON 导入课表
     * @param jsonString JSON 字符串
     */
    fun importSchedule(jsonString: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val count = repository.importScheduleFromJson(jsonString, semester)
                _importResult.value = ImportResult.Success(count)

                // 重新加载课表
                loadSchedule()
            } catch (e: Exception) {
                _importResult.value = ImportResult.Error(e.message ?: "Import failed")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * 清除导出结果
     */
    fun clearExportResult() {
        _exportResult.value = null
    }

    /**
     * 清除导入结果
     */
    fun clearImportResult() {
        _importResult.value = null
    }
}

/**
 * 导出结果密封类
 */
sealed class ExportResult {
    data class Success(val json: String) : ExportResult()
    data class Error(val message: String) : ExportResult()
}

/**
 * 导入结果密封类
 */
sealed class ImportResult {
    data class Success(val count: Int) : ImportResult()
    data class Error(val message: String) : ImportResult()
}
