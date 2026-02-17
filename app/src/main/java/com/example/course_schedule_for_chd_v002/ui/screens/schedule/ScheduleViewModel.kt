package com.example.course_schedule_for_chd_v002.ui.screens.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.course_schedule_for_chd_v002.domain.model.Course
import com.example.course_schedule_for_chd_v002.domain.repository.ICourseRepository
import com.example.course_schedule_for_chd_v002.util.NetworkUtils
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
 * @param semester 当前学期
 */
class ScheduleViewModel(
    private val repository: ICourseRepository,
    private val semester: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState(semester = semester))
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    init {
        loadSchedule()
    }

    /**
     * 加载本地课表
     */
    private fun loadSchedule() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val courses = repository.getLocalSchedule(semester)
            val conflicts = TimeUtils.findConflicts(courses)

            _uiState.update {
                it.copy(
                    courses = courses,
                    conflictingCourseIds = conflicts.keys,
                    isLoading = false
                )
            }
        }
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

    /**
     * 刷新课表（从服务器获取最新数据）
     * 使用自动重试机制和友好错误提示
     */
    fun refreshSchedule() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }

            // 使用带重试的网络请求
            val result = NetworkUtils.retryWith(maxRetries = 3) {
                repository.fetchRemoteSchedule(semester)
            }

            result.fold(
                onSuccess = { courses ->
                    val conflicts = TimeUtils.findConflicts(courses)
                    _uiState.update {
                        it.copy(
                            courses = courses,
                            conflictingCourseIds = conflicts.keys,
                            isRefreshing = false
                        )
                    }
                },
                onFailure = { error ->
                    // 使用友好的错误提示
                    val friendlyMessage = NetworkUtils.getFriendlyErrorMessage(
                        error.message ?: ""
                    )
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            errorMessage = friendlyMessage
                        )
                    }
                }
            )
        }
    }

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
