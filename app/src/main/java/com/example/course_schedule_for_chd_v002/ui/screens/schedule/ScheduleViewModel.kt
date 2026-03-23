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
     * [v89] 改为按当前周次计算冲突，而非全局冲突
     * [新增] 优先使用保存的当前教学周
     * [v74] 优先从缓存获取冲突，无缓存时实时计算并缓存
     * [新功能] 计算当前实际教学周，并自动跳转
     */
    private fun loadSchedule() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            android.util.Log.i("CHD_CurrentWeek", "========== [ScheduleViewModel] loadSchedule 开始 ==========")

            val courses = repository.getLocalSchedule(semester)
            android.util.Log.i("CHD_CurrentWeek", "[Step1] 本地课程数: ${courses.size}")

            // [v35] 计算最大周数
            val maxWeek = findMaxWeekWithCourse(courses)
            android.util.Log.i("CHD_CurrentWeek", "[Step2] 最大周数: $maxWeek")

            // [新功能] 计算当前实际教学周
            val semesterStartDate = userPreferences.getSemesterStartDateOnce()
            val lastParsedWeek = userPreferences.getLastParsedWeekOnce()
            android.util.Log.i("CHD_CurrentWeek", "[Step3] 学期开始日期: $semesterStartDate, 上次解析周次: $lastParsedWeek")

            // [新功能] 优先使用学期开始日期计算，否则使用上次解析的周次
            val actualCurrentWeek = when {
                semesterStartDate != null -> TimeUtils.calculateCurrentWeek(semesterStartDate)
                lastParsedWeek != null && lastParsedWeek in 1..maxWeek -> lastParsedWeek
                else -> null
            }
            android.util.Log.i("CHD_CurrentWeek", "[Step4] 实际当前教学周: $actualCurrentWeek")

            // [新功能] 获取今天星期几
            val todayDayOfWeek = TimeUtils.getTodayDayOfWeek()
            android.util.Log.i("CHD_CurrentWeek", "[Step5] 今天是: $todayDayOfWeek")

            // [新功能] 加载水课列表
            val waterCourses = userPreferences.getWaterCoursesForSemester(semester)
            android.util.Log.d("ScheduleViewModel", "[新功能] 水课数量: ${waterCourses.size}")

            // 决定初始显示周次：
            // 1. 如果有实际当前周且在有效范围内，使用实际当前周
            // 2. 否则使用保存的当前周
            // 3. 最后回退到第一个有课的周
            val savedCurrentWeek = userPreferences.getCurrentWeekOnce()
            val initialWeek = when {
                actualCurrentWeek != null && actualCurrentWeek in 1..maxWeek -> {
                    android.util.Log.i("CHD_CurrentWeek", "[Step6] 使用实际当前周: $actualCurrentWeek")
                    actualCurrentWeek
                }
                savedCurrentWeek in 1..maxWeek -> {
                    android.util.Log.i("CHD_CurrentWeek", "[Step6] 使用保存的周次: $savedCurrentWeek")
                    savedCurrentWeek
                }
                else -> {
                    val firstWeek = findFirstWeekWithCourse(courses)
                    android.util.Log.i("CHD_CurrentWeek", "[Step6] 回退到第一个有课周次: $firstWeek")
                    firstWeek
                }
            }

            // [v74] 优先从缓存获取冲突，无缓存或缓存不完整时预计算
            // [v74 fix] 检查缓存是否完整（需要覆盖最大周次）
            if (courses.isNotEmpty()) {
                val cache = repository.getConflictCache(semester)
                val cachedMaxWeek = cache.keys.maxOrNull() ?: 0
                val isComplete = cache.isNotEmpty() && cachedMaxWeek >= maxWeek

                if (!isComplete) {
                    android.util.Log.i("CHD_Conflict", "[v74 fix] 缓存不完整: 缓存覆盖周1-$cachedMaxWeek, 需要1-$maxWeek, 重新预计算...")
                    repository.precomputeAndCacheConflicts(courses, semester)
                } else {
                    android.util.Log.i("CHD_Conflict", "[v74 fix] 缓存完整: 覆盖周1-$cachedMaxWeek")
                }
            }

            // 从缓存获取当前周的冲突
            val conflicts = repository.getConflictsForWeek(semester, initialWeek)
            val conflictIds: Set<Long> = if (conflicts != null) {
                android.util.Log.i("CHD_Conflict", "[Step7] 周$initialWeek 从缓存获取冲突: ${conflicts.size} 门")
                conflicts
            } else {
                // 缓存中该周无冲突记录，返回空集
                android.util.Log.i("CHD_Conflict", "[Step7] 周$initialWeek 缓存中无冲突记录")
                emptySet()
            }
            android.util.Log.i("CHD_Conflict", "[Step7.1] 周$initialWeek 最终冲突数: ${conflictIds.size}")

            android.util.Log.i("CHD_CurrentWeek", "[Step8] 最终显示周次: $initialWeek")
            android.util.Log.i("CHD_CurrentWeek", "========== [ScheduleViewModel] loadSchedule 结束 ==========")

            _uiState.update {
                it.copy(
                    courses = courses,
                    conflictingCourseIds = conflictIds,
                    currentWeek = initialWeek,
                    maxWeeks = maxWeek,  // [v35] 动态设置最大周数
                    actualCurrentWeek = actualCurrentWeek,  // [新功能]
                    todayDayOfWeek = todayDayOfWeek,        // [新功能]
                    waterCourseNames = waterCourses,        // [新功能] 水课列表
                    isLoading = false
                )
            }
        }
    }

    /**
     * [v89] 根据周次更新冲突课程ID
     * [v74] 优先从缓存获取，无缓存时实时计算
     * [v74 fix] 缓存已在 loadSchedule 中确保完整，直接从缓存读取
     * @param week 周次
     */
    private fun updateConflictsForWeek(week: Int) {
        viewModelScope.launch {
            // [v74 fix] 直接从缓存获取，不再实时计算
            val conflicts = repository.getConflictsForWeek(semester, week)
            val conflictIds: Set<Long> = conflicts ?: emptySet()

            android.util.Log.d("ScheduleViewModel", "[v74 fix] updateConflictsForWeek: 周$week, 缓存获取 ${conflictIds.size} 个冲突")

            _uiState.update { it.copy(conflictingCourseIds = conflictIds) }
        }
    }

    /**
     * [v34] 找到应该显示的周次
     * 忽略当前日期的影响，自动选择第一个有课的周次
     * 如果没有任何课程，默认显示第一周
     * [v95] 添加边界检查，确保周次至少为1
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

        // [v95] 确保周次至少为1（防止旧数据中 startWeek=0 的情况）
        val firstWeek = maxOf(1, if (minStartWeek == Int.MAX_VALUE) 1 else minStartWeek)
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
     * [v89] 切换周次时更新冲突标记
     *
     * @param week 周次 (1-16)
     */
    fun onWeekSelected(week: Int) {
        val newWeek = week.coerceIn(1, _uiState.value.maxWeeks)
        _uiState.update {
            it.copy(currentWeek = newWeek)
        }
        // [v89] 切换周次时更新冲突
        updateConflictsForWeek(newWeek)
    }

    /**
     * [新功能] 跳转到当前教学周
     */
    fun goToCurrentWeek() {
        val targetWeek = _uiState.value.actualCurrentWeek ?: return
        if (targetWeek in 1.._uiState.value.maxWeeks) {
            android.util.Log.i("ScheduleViewModel", "[新功能] 跳转到当前教学周: $targetWeek")
            onWeekSelected(targetWeek)
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
     * [新功能] 切换水课标注状态
     * @param courseName 课程名称
     */
    fun toggleWaterCourse(courseName: String) {
        viewModelScope.launch {
            val isWaterCourse = courseName in _uiState.value.waterCourseNames

            if (isWaterCourse) {
                userPreferences.removeWaterCourse(courseName, semester)
                _uiState.update {
                    it.copy(waterCourseNames = it.waterCourseNames - courseName)
                }
                android.util.Log.d("ScheduleViewModel", "[新功能] 取消水课标注: $courseName")
            } else {
                userPreferences.addWaterCourse(courseName, semester)
                _uiState.update {
                    it.copy(waterCourseNames = it.waterCourseNames + courseName)
                }
                android.util.Log.d("ScheduleViewModel", "[新功能] 添加水课标注: $courseName")
            }
        }
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
