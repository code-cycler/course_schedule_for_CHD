package com.example.course_schedule_for_chd_v002.ui.screens.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.course_schedule_for_chd_v002.data.local.preferences.UserPreferences
import com.example.course_schedule_for_chd_v002.domain.model.Campus
import com.example.course_schedule_for_chd_v002.domain.model.Course
import com.example.course_schedule_for_chd_v002.domain.model.ReminderSettings
import com.example.course_schedule_for_chd_v002.domain.repository.ICourseRepository
import com.example.course_schedule_for_chd_v002.service.calendar.CalendarSyncService
import com.example.course_schedule_for_chd_v002.util.Constants
import com.example.course_schedule_for_chd_v002.util.TimeUtils
import com.example.course_schedule_for_chd_v002.util.AppLogger
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
 * @param userPreferences 用户偏好设置
 * @param semester 当前学期
 * @param calendarSyncService 日历同步服务
 */
class ScheduleViewModel(
    private val repository: ICourseRepository,
    private val userPreferences: UserPreferences,
    private val semester: String,
    private val calendarSyncService: CalendarSyncService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState(semester = semester))
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    // 日历同步设置状态
    private val _reminderSettings = MutableStateFlow<ReminderSettings>(ReminderSettings())

    /**
     * 获取日历同步设置
     */
    val reminderSettings: StateFlow<ReminderSettings> = _reminderSettings.asStateFlow()

    // [v37] 添加初始化保护，防止启动崩溃
    init {
        try {
            AppLogger.d("ScheduleViewModel", "[v37] 初始化,学期: $semester")
            loadCampus()
            loadSchedule()
            loadReminderSettings()
        } catch (e: Exception) {
            AppLogger.e("ScheduleViewModel", "[v37] 初始化失败: ${e.message}", e)
            _uiState.update { it.copy(isLoading = false, errorMessage = "初始化失败: ${e.message}") }
        }
    }

    /**
     * 重新加载课程数据
     */
    fun reload() {
        AppLogger.d("ScheduleViewModel", "[v24] reload() 被调用")
        loadSchedule()
    }

    /**
     * 加载保存的校区设置
     */
    private fun loadCampus() {
        viewModelScope.launch {
            val campusName = userPreferences.getCampusOnce()
            val campus = Campus.fromName(campusName)
            AppLogger.d("ScheduleViewModel", "加载校区: $campusName")
            _uiState.update { it.copy(campus = campus) }
        }
    }

    /**
     * 切换校区
     */
    fun onCampusChanged(campus: Campus) {
        viewModelScope.launch {
            AppLogger.d("ScheduleViewModel", "切换校区: ${campus.name}")
            val oldSettings = _reminderSettings.value
            userPreferences.saveCampus(campus.name)
            _uiState.update { it.copy(campus = campus) }

            // 如果日历同步已启用，自动重新同步
            if (oldSettings.calendarSyncEnabled) {
                AppLogger.i("ScheduleViewModel", "校区变更，自动重新同步日历")
                syncToCalendar()
            }
        }
    }

    /**
     * 加载本地课表
     */
    private fun loadSchedule() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            AppLogger.i("CHD_CurrentWeek", "========== [ScheduleViewModel] loadSchedule 开始 ==========")

            val courses = repository.getLocalSchedule(semester)
            AppLogger.i("CHD_CurrentWeek", "[Step1] 本地课程数: ${courses.size}")

            val maxWeek = findMaxWeekWithCourse(courses)
            AppLogger.i("CHD_CurrentWeek", "[Step2] 最大周数: $maxWeek")

            val semesterStartDate = userPreferences.getSemesterStartDateOnce()
            val lastParsedWeek = userPreferences.getLastParsedWeekOnce()
            AppLogger.i("CHD_CurrentWeek", "[Step3] 学期开始日期: $semesterStartDate, 上次解析周次: $lastParsedWeek")

            val actualCurrentWeek = when {
                semesterStartDate != null -> TimeUtils.calculateCurrentWeek(semesterStartDate)
                lastParsedWeek != null && lastParsedWeek in 1..maxWeek -> lastParsedWeek
                else -> null
            }
            AppLogger.i("CHD_CurrentWeek", "[Step4] 实际当前教学周: $actualCurrentWeek")

            val todayDayOfWeek = TimeUtils.getTodayDayOfWeek()
            AppLogger.i("CHD_CurrentWeek", "[Step5] 今天是: $todayDayOfWeek")

            val waterCourses = userPreferences.getWaterCoursesForSemester(semester)
            AppLogger.d("ScheduleViewModel", "水课数量: ${waterCourses.size}")

            val savedCurrentWeek = userPreferences.getCurrentWeekOnce()
            val initialWeek = when {
                actualCurrentWeek != null && actualCurrentWeek in 1..maxWeek -> {
                    AppLogger.i("CHD_CurrentWeek", "[Step6] 使用实际当前周: $actualCurrentWeek")
                    actualCurrentWeek
                }
                savedCurrentWeek in 1..maxWeek -> {
                    AppLogger.i("CHD_CurrentWeek", "[Step6] 使用保存的周次: $savedCurrentWeek")
                    savedCurrentWeek
                }
                else -> {
                    val firstWeek = findFirstWeekWithCourse(courses)
                    AppLogger.i("CHD_CurrentWeek", "[Step6] 回退到第一个有课周次: $firstWeek")
                    firstWeek
                }
            }

            // 冲突缓存
            if (courses.isNotEmpty()) {
                val cache = repository.getConflictCache(semester)
                val cachedMaxWeek = cache.keys.maxOrNull() ?: 0
                val isComplete = cache.isNotEmpty() && cachedMaxWeek >= maxWeek

                if (!isComplete) {
                    AppLogger.i("CHD_Conflict", "缓存不完整: 缓存覆盖周1-$cachedMaxWeek, 需要1-$maxWeek, 重新预计算...")
                    repository.precomputeAndCacheConflicts(courses, semester)
                } else {
                    AppLogger.i("CHD_Conflict", "缓存完整: 覆盖周1-$cachedMaxWeek")
                }
            }

            val conflicts = repository.getConflictsForWeek(semester, initialWeek)
            val conflictIds: Set<Long> = conflicts ?: emptySet()
            AppLogger.i("CHD_Conflict", "[Step7] 周$initialWeek 最终冲突数: ${conflictIds.size}")

            AppLogger.i("CHD_CurrentWeek", "[Step8] 最终显示周次: $initialWeek")

            val weekStartDate = if (semesterStartDate != null) {
                TimeUtils.calculateWeekStartDate(semesterStartDate, initialWeek)
            } else {
                null
            }

            AppLogger.i("CHD_CurrentWeek", "========== [ScheduleViewModel] loadSchedule 结束 ==========")

            val initialDisplayCourses = courses.filter { it.isWeekInRange(initialWeek) }
            val initialCoursesByWeek = (1..maxWeek).associateWith { week ->
                courses.filter { course -> course.isWeekInRange(week) }
            }

            _uiState.update {
                it.copy(
                    courses = courses,
                    conflictingCourseIds = conflictIds,
                    currentWeek = initialWeek,
                    maxWeeks = maxWeek,
                    actualCurrentWeek = actualCurrentWeek,
                    todayDayOfWeek = todayDayOfWeek,
                    weekStartDate = weekStartDate,
                    waterCourseNames = waterCourses,
                    displayCourses = initialDisplayCourses,
                    coursesByWeek = initialCoursesByWeek,
                    isLoading = false
                )
            }
        }
    }

    /**
     * 根据周次更新冲突课程ID
     */
    private fun updateConflictsForWeek(week: Int) {
        viewModelScope.launch {
            val conflicts = repository.getConflictsForWeek(semester, week)
            val conflictIds: Set<Long> = conflicts ?: emptySet()
            AppLogger.d("ScheduleViewModel", "updateConflictsForWeek: 周$week, ${conflictIds.size} 个冲突")
            _uiState.update { it.copy(conflictingCourseIds = conflictIds) }
        }
    }

    private fun findFirstWeekWithCourse(courses: List<Course>): Int {
        if (courses.isEmpty()) {
            return 1
        }
        var minStartWeek = Int.MAX_VALUE
        for (course in courses) {
            if (course.startWeek < minStartWeek) {
                minStartWeek = course.startWeek
            }
        }
        return maxOf(1, if (minStartWeek == Int.MAX_VALUE) 1 else minStartWeek)
    }

    private fun findMaxWeekWithCourse(courses: List<Course>): Int {
        if (courses.isEmpty()) {
            return Constants.Schedule.MAX_WEEKS
        }
        var maxEndWeek = 0
        for (course in courses) {
            if (course.endWeek > maxEndWeek) {
                maxEndWeek = course.endWeek
            }
        }
        return if (maxEndWeek == 0) Constants.Schedule.MAX_WEEKS else maxEndWeek
    }

    fun onWeekSelected(week: Int) {
        val newWeek = week.coerceIn(1, _uiState.value.maxWeeks)

        viewModelScope.launch {
            val semesterStartDate = userPreferences.getSemesterStartDateOnce()
            val weekStartDate = if (semesterStartDate != null) {
                TimeUtils.calculateWeekStartDate(semesterStartDate, newWeek)
            } else {
                null
            }

            val allCourses = _uiState.value.courses
            val cachedDisplayCourses = _uiState.value.coursesByWeek[newWeek]
                ?: allCourses.filter { it.isWeekInRange(newWeek) }

            _uiState.update {
                it.copy(
                    currentWeek = newWeek,
                    weekStartDate = weekStartDate,
                    displayCourses = cachedDisplayCourses
                )
            }
        }

        updateConflictsForWeek(newWeek)
    }

    fun goToCurrentWeek() {
        val targetWeek = _uiState.value.actualCurrentWeek ?: return
        if (targetWeek in 1.._uiState.value.maxWeeks) {
            AppLogger.i("ScheduleViewModel", "跳转到当前教学周: $targetWeek")
            onWeekSelected(targetWeek)
        }
    }

    /**
     * 刷新当前时间和教学周信息
     */
    fun refreshCurrentTimeInfo() {
        viewModelScope.launch {
            val semesterStartDate = userPreferences.getSemesterStartDateOnce()
            val maxWeek = _uiState.value.maxWeeks
            val currentDisplayWeek = _uiState.value.currentWeek

            val actualCurrentWeek = when {
                semesterStartDate != null -> TimeUtils.calculateCurrentWeek(semesterStartDate)
                else -> null
            }

            val todayDayOfWeek = TimeUtils.getTodayDayOfWeek()

            val weekStartDate = if (semesterStartDate != null) {
                TimeUtils.calculateWeekStartDate(semesterStartDate, currentDisplayWeek)
            } else {
                null
            }

            _uiState.update { currentState ->
                val shouldUpdateDisplayWeek = actualCurrentWeek != null &&
                    actualCurrentWeek in 1..maxWeek &&
                    currentState.currentWeek != actualCurrentWeek &&
                    currentState.currentWeek == currentState.actualCurrentWeek

                currentState.copy(
                    actualCurrentWeek = actualCurrentWeek,
                    todayDayOfWeek = todayDayOfWeek,
                    weekStartDate = weekStartDate,
                    currentWeek = if (shouldUpdateDisplayWeek) actualCurrentWeek else currentState.currentWeek
                )
            }
        }
    }

    fun onCourseSelected(course: Course?) {
        _uiState.update { it.copy(selectedCourse = course) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun toggleWaterCourse(courseName: String) {
        viewModelScope.launch {
            val isWaterCourse = courseName in _uiState.value.waterCourseNames

            if (isWaterCourse) {
                userPreferences.removeWaterCourse(courseName, semester)
                _uiState.update {
                    it.copy(waterCourseNames = it.waterCourseNames - courseName)
                }
            } else {
                userPreferences.addWaterCourse(courseName, semester)
                _uiState.update {
                    it.copy(waterCourseNames = it.waterCourseNames + courseName)
                }
            }
        }
    }

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

    fun importSchedule(jsonString: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val count = repository.importScheduleFromJson(jsonString, semester)
                _importResult.value = ImportResult.Success(count)
                loadSchedule()
            } catch (e: Exception) {
                _importResult.value = ImportResult.Error(e.message ?: "Import failed")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun clearExportResult() {
        _exportResult.value = null
    }

    fun clearImportResult() {
        _importResult.value = null
    }

    // ================ 日历同步设置相关 ================

    /**
     * 加载日历同步设置
     */
    private fun loadReminderSettings() {
        viewModelScope.launch {
            val settings = userPreferences.getReminderSettingsOnce()
            _reminderSettings.value = settings
            AppLogger.d("ScheduleViewModel", "加载日历同步设置: $settings")
        }
    }

    /**
     * 更新日历同步设置
     */
    fun updateReminderSettings(settings: ReminderSettings) {
        val oldSettings = _reminderSettings.value

        viewModelScope.launch {
            _reminderSettings.value = settings
            userPreferences.saveReminderSettings(settings)
            AppLogger.d("ScheduleViewModel", "保存日历同步设置: $settings")

            // 检查是否需要自动重新同步日历
            if (settings.calendarSyncEnabled && shouldResyncCalendar(oldSettings, settings)) {
                AppLogger.i("ScheduleViewModel", "日历提醒设置变更，自动重新同步")
                syncToCalendar()
            }
        }
    }

    /**
     * 判断是否需要重新同步日历
     */
    private fun shouldResyncCalendar(old: ReminderSettings, new: ReminderSettings): Boolean {
        return old.calendarBeforeClassReminderEnabled != new.calendarBeforeClassReminderEnabled ||
               old.calendarEarlyMorningReminderEnabled != new.calendarEarlyMorningReminderEnabled ||
               old.beforeClassReminderMinutes != new.beforeClassReminderMinutes ||
               old.earlyMorningReminderHour != new.earlyMorningReminderHour ||
               old.earlyMorningReminderMinute != new.earlyMorningReminderMinute
    }

    /**
     * 同步课程到日历
     */
    fun syncToCalendar() {
        viewModelScope.launch {
            AppLogger.i("CHD_CalendarDebug", "========== syncToCalendar 开始 ==========")

            _uiState.update { it.copy(calendarSyncState = CalendarSyncState.Syncing) }

            val courses = _uiState.value.courses
            val semesterStartDate = userPreferences.getSemesterStartDateOnce()
            val campus = _uiState.value.campus
            val reminderSettings = _reminderSettings.value

            AppLogger.i("CHD_CalendarDebug", "课程数: ${courses.size}, 校区: ${campus.displayName}")

            if (courses.isEmpty()) {
                _uiState.update {
                    it.copy(
                        calendarSyncState = CalendarSyncState.Error("没有课程可同步"),
                        errorMessage = "没有课程可同步"
                    )
                }
                return@launch
            }

            if (semesterStartDate == null) {
                _uiState.update {
                    it.copy(
                        calendarSyncState = CalendarSyncState.Error("缺少学期开始日期，请先同步课表"),
                        errorMessage = "缺少学期开始日期，请先同步课表"
                    )
                }
                return@launch
            }

            try {
                val result = calendarSyncService.syncCoursesToCalendar(
                    courses,
                    semesterStartDate,
                    campus,
                    reminderSettings
                )
                val statusMsg = buildString {
                    append("同步完成: 成功 ${result.successCount} 节")
                    if (result.failCount > 0) {
                        append(", 失败 ${result.failCount} 节")
                    }
                    if (result.reminderCount > 0) {
                        append(", 提醒 ${result.reminderCount} 个")
                    }
                    if (result.earlyMorningCount > 0) {
                        append(", 早八提醒 ${result.earlyMorningCount} 个")
                    }
                    append(" (${campus.displayName})")
                }

                _uiState.update {
                    it.copy(
                        calendarSyncState = CalendarSyncState.Synced(result.successCount),
                        errorMessage = statusMsg
                    )
                }
                AppLogger.i("CHD_CalendarDebug", "同步完成: $result")
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        calendarSyncState = CalendarSyncState.Error(e.message ?: "同步失败"),
                        errorMessage = "同步失败: ${e.message}"
                    )
                }
                AppLogger.e("CHD_CalendarDebug", "同步失败", e)
            }
        }
    }

    /**
     * 删除日历中的所有课程事件
     */
    fun deleteCalendarEvents() {
        viewModelScope.launch {
            AppLogger.d("ScheduleViewModel", "开始删除日历事件...")

            _uiState.update { it.copy(calendarSyncState = CalendarSyncState.Deleting) }

            try {
                val deleted = calendarSyncService.deleteCalendar()
                if (deleted) {
                    _uiState.update {
                        it.copy(
                            calendarSyncState = CalendarSyncState.Deleted,
                            errorMessage = "已删除日历中的所有课程事件"
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            calendarSyncState = CalendarSyncState.Error("删除日历失败，请检查权限"),
                            errorMessage = "删除日历失败，请检查权限"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        calendarSyncState = CalendarSyncState.Error(e.message ?: "删除失败"),
                        errorMessage = "删除失败: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 日历权限结果处理
     */
    fun onCalendarPermissionResult(isGranted: Boolean) {
        AppLogger.d("ScheduleViewModel", "日历权限结果: $isGranted")
        if (isGranted) {
            syncToCalendar()
        }
    }

    // ================ 课程编辑相关 ================

    fun openCourseEditor(courseName: String) {
        viewModelScope.launch {
            try {
                val instances = repository.getCoursesByName(semester, courseName)
                if (instances.isEmpty()) return@launch

                val group = CourseEditGroup(
                    courseName = courseName,
                    semester = semester,
                    instances = instances,
                    courseType = instances.first().courseType,
                    credit = instances.first().credit
                )

                val teachers = repository.getDistinctTeachers(semester)
                val locations = repository.getDistinctLocations(semester)

                _uiState.update {
                    it.copy(
                        editCourseGroup = group,
                        suggestedTeachers = teachers,
                        suggestedLocations = locations,
                        editConflicts = emptyList()
                    )
                }
            } catch (e: Exception) {
                AppLogger.e("ScheduleViewModel", "[编辑] 打开编辑器失败", e)
            }
        }
    }

    fun updateCourseInstance(course: Course) {
        viewModelScope.launch {
            try {
                repository.updateCourse(course)
                refreshAfterEdit()
            } catch (e: Exception) {
                AppLogger.e("ScheduleViewModel", "[编辑] 更新失败", e)
            }
        }
    }

    fun addCourseInstance(course: Course) {
        viewModelScope.launch {
            try {
                repository.insertCourse(course)
                refreshAfterEdit()
            } catch (e: Exception) {
                AppLogger.e("ScheduleViewModel", "[编辑] 添加失败", e)
            }
        }
    }

    fun deleteCourseInstance(courseId: Long) {
        viewModelScope.launch {
            try {
                repository.deleteCourseById(courseId)
                refreshAfterEdit()
            } catch (e: Exception) {
                AppLogger.e("ScheduleViewModel", "[编辑] 删除失败", e)
            }
        }
    }

    fun dismissCourseEditor() {
        _uiState.update {
            it.copy(
                editCourseGroup = null,
                suggestedTeachers = emptyList(),
                suggestedLocations = emptyList(),
                editConflicts = emptyList()
            )
        }
    }

    /**
     * 编辑后刷新数据
     */
    private suspend fun refreshAfterEdit() {
        val courses = repository.getLocalSchedule(semester)
        val maxWeek = findMaxWeekWithCourse(courses)
        val currentWeek = _uiState.value.currentWeek.coerceIn(1, maxWeek)

        if (courses.isNotEmpty()) {
            repository.precomputeAndCacheConflicts(courses, semester)
        }

        val conflicts = repository.getConflictsForWeek(semester, currentWeek) ?: emptySet()

        val displayCourses = courses.filter { it.isWeekInRange(currentWeek) }
        val coursesByWeek = (1..maxWeek).associateWith { week ->
            courses.filter { course -> course.isWeekInRange(week) }
        }

        val teachers = repository.getDistinctTeachers(semester)
        val locations = repository.getDistinctLocations(semester)

        val currentGroupName = _uiState.value.editCourseGroup?.courseName
        val updatedGroup = if (currentGroupName != null) {
            val instances = repository.getCoursesByName(semester, currentGroupName)
            if (instances.isEmpty()) {
                null
            } else {
                CourseEditGroup(
                    courseName = currentGroupName,
                    semester = semester,
                    instances = instances,
                    courseType = instances.first().courseType,
                    credit = instances.first().credit
                )
            }
        } else null

        _uiState.update {
            it.copy(
                courses = courses,
                conflictingCourseIds = conflicts,
                maxWeeks = maxWeek,
                displayCourses = displayCourses,
                coursesByWeek = coursesByWeek,
                suggestedTeachers = teachers,
                suggestedLocations = locations,
                editCourseGroup = updatedGroup,
                editConflicts = emptyList()
            )
        }

        // 重新同步日历（如果已启用）
        val settings = _reminderSettings.value
        if (settings.calendarSyncEnabled) {
            AppLogger.d("ScheduleViewModel", "[编辑] 日历同步已启用，自动重新同步")
            syncToCalendar()
        }

        AppLogger.d("ScheduleViewModel", "[编辑] 刷新完成, 课程数: ${courses.size}")
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
