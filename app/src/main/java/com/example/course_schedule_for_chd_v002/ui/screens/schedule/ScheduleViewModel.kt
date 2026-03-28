package com.example.course_schedule_for_chd_v002.ui.screens.schedule

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.course_schedule_for_chd_v002.data.local.preferences.UserPreferences
import com.example.course_schedule_for_chd_v002.domain.model.Campus
import com.example.course_schedule_for_chd_v002.domain.model.Course
import com.example.course_schedule_for_chd_v002.domain.model.ReminderSettings
import com.example.course_schedule_for_chd_v002.domain.repository.ICourseRepository
import com.example.course_schedule_for_chd_v002.service.calendar.CalendarSyncService
import com.example.course_schedule_for_chd_v002.service.reminder.ReminderManager
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
 * @param userPreferences 用户偏好设置 [v61]
 * @param semester 当前学期
 * @param reminderManager 提醒管理器 [课程提醒]
 * @param calendarSyncService 日历同步服务 [课程提醒]
 */
class ScheduleViewModel(
    private val repository: ICourseRepository,
    private val userPreferences: UserPreferences,
    // [v61] 新增
    private val semester: String,
    private val reminderManager: ReminderManager,  // [课程提醒] 新增
    private val calendarSyncService: CalendarSyncService  // [课程提醒] 新增
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState(semester = semester))
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    // [课程提醒] 提醒设置状态
    private val _reminderSettings = MutableStateFlow<ReminderSettings>(ReminderSettings())

    /**
     * [课程提醒] 获取提醒设置
     */
    val reminderSettings: StateFlow<ReminderSettings> = _reminderSettings.asStateFlow()

    // [v37] 添加初始化保护，防止启动崩溃
    init {
        try {
            AppLogger.d("ScheduleViewModel", "[v37] 初始化,学期: $semester")
            loadCampus()  // [v61] 先加载校区设置
            loadSchedule()
            loadReminderSettings()  // [课程提醒] 加载提醒设置
        } catch (e: Exception) {
            AppLogger.e("ScheduleViewModel", "[v37] 初始化失败: ${e.message}", e)
            _uiState.update { it.copy(isLoading = false, errorMessage = "初始化失败: ${e.message}") }
        }
    }

    /**
     * 重新加载课程数据
     * 用于从登录页返回后刷新数据
     */
    fun reload() {
        AppLogger.d("ScheduleViewModel", "[v24] reload() 被调用")
        loadSchedule()
    }

    /**
     * [v61] 加载保存的校区设置
     */
    private fun loadCampus() {
        viewModelScope.launch {
            val campusName = userPreferences.getCampusOnce()
            val campus = Campus.fromName(campusName)
            AppLogger.d("ScheduleViewModel", "[v61] 加载校区: $campusName")
            _uiState.update { it.copy(campus = campus) }
        }
    }

    /**
     * [v61] 切换校区
     * @param campus 新的校区
     */
    fun onCampusChanged(campus: Campus) {
        viewModelScope.launch {
            AppLogger.d("ScheduleViewModel", "[v61] 切换校区: ${campus.name}")
            val oldSettings = _reminderSettings.value
            userPreferences.saveCampus(campus.name)
            _uiState.update { it.copy(campus = campus) }

            // [v108] 切换校区后重新调度提醒（使用新校区时间）
            rescheduleRemindersWithNewCampus(campus)

            // [v108] 如果日历同步已启用，自动重新同步
            if (oldSettings.calendarSyncEnabled) {
                AppLogger.i("ScheduleViewModel", "[v108] 校区变更，自动重新同步日历")
                syncToCalendar()
            }
        }
    }

    /**
     * [v108] 使用新校区时间重新调度提醒
     */
    private suspend fun rescheduleRemindersWithNewCampus(campus: Campus) {
        val settings = _reminderSettings.value
        val semesterStartDate = userPreferences.getSemesterStartDateOnce()
        val currentWeek = _uiState.value.actualCurrentWeek
        val courses = _uiState.value.courses

        if (semesterStartDate != null && currentWeek != null) {
            // 重新调度上课前提醒（传递新校区）
            reminderManager.scheduleBeforeClassReminders(
                courses, settings, semesterStartDate, currentWeek, campus
            )
            AppLogger.d("ScheduleViewModel", "[v108] 已用新校区时间重新调度提醒: ${campus.displayName}")
        }

        // 重新调度早八提醒（早八提醒不依赖校区，但重新调度确保一致）
        reminderManager.scheduleEarlyMorningReminder(settings)
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

            AppLogger.i("CHD_CurrentWeek", "========== [ScheduleViewModel] loadSchedule 开始 ==========")

            val courses = repository.getLocalSchedule(semester)
            AppLogger.i("CHD_CurrentWeek", "[Step1] 本地课程数: ${courses.size}")

            // [v35] 计算最大周数
            val maxWeek = findMaxWeekWithCourse(courses)
            AppLogger.i("CHD_CurrentWeek", "[Step2] 最大周数: $maxWeek")

            // [新功能] 计算当前实际教学周
            val semesterStartDate = userPreferences.getSemesterStartDateOnce()
            val lastParsedWeek = userPreferences.getLastParsedWeekOnce()
            AppLogger.i("CHD_CurrentWeek", "[Step3] 学期开始日期: $semesterStartDate, 上次解析周次: $lastParsedWeek")

            // [新功能] 优先使用学期开始日期计算，否则使用上次解析的周次
            val actualCurrentWeek = when {
                semesterStartDate != null -> TimeUtils.calculateCurrentWeek(semesterStartDate)
                lastParsedWeek != null && lastParsedWeek in 1..maxWeek -> lastParsedWeek
                else -> null
            }
            AppLogger.i("CHD_CurrentWeek", "[Step4] 实际当前教学周: $actualCurrentWeek")

            // [新功能] 获取今天星期几
            val todayDayOfWeek = TimeUtils.getTodayDayOfWeek()
            AppLogger.i("CHD_CurrentWeek", "[Step5] 今天是: $todayDayOfWeek")

            // [新功能] 加载水课列表
            val waterCourses = userPreferences.getWaterCoursesForSemester(semester)
            AppLogger.d("ScheduleViewModel", "[新功能] 水课数量: ${waterCourses.size}")

            // 决定初始显示周次：
            // 1. 如果有实际当前周且在有效范围内，使用实际当前周
            // 2. 否则使用保存的当前周
            // 3. 最后回退到第一个有课的周
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

            // [v74] 优先从缓存获取冲突，无缓存或缓存不完整时预计算
            // [v74 fix] 检查缓存是否完整（需要覆盖最大周次）
            if (courses.isNotEmpty()) {
                val cache = repository.getConflictCache(semester)
                val cachedMaxWeek = cache.keys.maxOrNull() ?: 0
                val isComplete = cache.isNotEmpty() && cachedMaxWeek >= maxWeek

                if (!isComplete) {
                    AppLogger.i("CHD_Conflict", "[v74 fix] 缓存不完整: 缓存覆盖周1-$cachedMaxWeek, 需要1-$maxWeek, 重新预计算...")
                    repository.precomputeAndCacheConflicts(courses, semester)
                } else {
                    AppLogger.i("CHD_Conflict", "[v74 fix] 缓存完整: 覆盖周1-$cachedMaxWeek")
                }
            }

            // 从缓存获取当前周的冲突
            val conflicts = repository.getConflictsForWeek(semester, initialWeek)
            val conflictIds: Set<Long> = if (conflicts != null) {
                AppLogger.i("CHD_Conflict", "[Step7] 周$initialWeek 从缓存获取冲突: ${conflicts.size} 门")
                conflicts
            } else {
                // 缓存中该周无冲突记录，返回空集
                AppLogger.i("CHD_Conflict", "[Step7] 周$initialWeek 缓存中无冲突记录")
                emptySet()
            }
            AppLogger.i("CHD_Conflict", "[Step7.1] 周$initialWeek 最终冲突数: ${conflictIds.size}")

            AppLogger.i("CHD_CurrentWeek", "[Step8] 最终显示周次: $initialWeek")

            // [新功能] 计算当前显示周的周一日期
            val weekStartDate = if (semesterStartDate != null) {
                TimeUtils.calculateWeekStartDate(semesterStartDate, initialWeek)
            } else {
                null
            }
            AppLogger.i("CHD_CurrentWeek", "[Step9] 周$initialWeek 周一日期: $weekStartDate")

            AppLogger.i("CHD_CurrentWeek", "========== [ScheduleViewModel] loadSchedule 结束 ==========")

            // [优化] 预计算课程缓存：displayCourses 和 coursesByWeek
            val initialDisplayCourses = courses.filter { it.isWeekInRange(initialWeek) }
            val initialCoursesByWeek = (1..maxWeek).associateWith { week ->
                courses.filter { course -> course.isWeekInRange(week) }
            }

            _uiState.update {
                it.copy(
                    courses = courses,
                    conflictingCourseIds = conflictIds,
                    currentWeek = initialWeek,
                    maxWeeks = maxWeek,  // [v35] 动态设置最大周数
                    actualCurrentWeek = actualCurrentWeek,  // [新功能]
                    todayDayOfWeek = todayDayOfWeek,        // [新功能]
                    weekStartDate = weekStartDate,          // [新功能] 当前周的周一日期
                    waterCourseNames = waterCourses,        // [新功能] 水课列表
                    displayCourses = initialDisplayCourses,           // [优化] 缓存
                    coursesByWeek = initialCoursesByWeek,             // [优化] 缓存
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

            AppLogger.d("ScheduleViewModel", "[v74 fix] updateConflictsForWeek: 周$week, 缓存获取 ${conflictIds.size} 个冲突")

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
            AppLogger.d("ScheduleViewModel", "[v35] 无课程，默认第一周")
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
        AppLogger.d("ScheduleViewModel", "[v35] 第一个有课的周次: $firstWeek")
        return firstWeek
    }

    /**
     * [v35] 找到最后一个有课的周次
     * 用于动态设置周选择器的最大值
     */
    private fun findMaxWeekWithCourse(courses: List<Course>): Int {
        if (courses.isEmpty()) {
            AppLogger.d("ScheduleViewModel", "[v35] 无课程，默认最大25周")
            return Constants.Schedule.MAX_WEEKS
        }

        var maxEndWeek = 0
        for (course in courses) {
            if (course.endWeek > maxEndWeek) {
                maxEndWeek = course.endWeek
            }
        }

        val result = if (maxEndWeek == 0) Constants.Schedule.MAX_WEEKS else maxEndWeek
        AppLogger.d("ScheduleViewModel", "[v35] 最大周次: $result")
        return result
    }

    /**
     * 选择周次
     * [v89] 切换周次时更新冲突标记
     * [新功能] 切换周次时更新周开始日期
     *
     * @param week 周次 (1-16)
     */
    fun onWeekSelected(week: Int) {
        val newWeek = week.coerceIn(1, _uiState.value.maxWeeks)

        // [新功能] 计算新周的周一日期
        viewModelScope.launch {
            val semesterStartDate = userPreferences.getSemesterStartDateOnce()
            val weekStartDate = if (semesterStartDate != null) {
                TimeUtils.calculateWeekStartDate(semesterStartDate, newWeek)
            } else {
                null
            }
            AppLogger.d("ScheduleViewModel", "[新功能] 切换到周$newWeek, 周一日期: $weekStartDate")

            // [优化] 从缓存获取 displayCourses，避免重新过滤
            val allCourses = _uiState.value.courses
            val cachedDisplayCourses = _uiState.value.coursesByWeek[newWeek]
                ?: allCourses.filter { it.isWeekInRange(newWeek) }

            _uiState.update {
                it.copy(
                    currentWeek = newWeek,
                    weekStartDate = weekStartDate,
                    displayCourses = cachedDisplayCourses  // [优化] 使用缓存
                )
            }
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
            AppLogger.i("ScheduleViewModel", "[新功能] 跳转到当前教学周: $targetWeek")
            onWeekSelected(targetWeek)
        }
    }

    /**
     * [新功能] 刷新当前时间和教学周信息
     * 每次 APP 回到前台时调用，确保当前教学周和今日列与现实时间同步
     */
    fun refreshCurrentTimeInfo() {
        viewModelScope.launch {
            AppLogger.i("CHD_CurrentWeek", "========== [ScheduleViewModel] refreshCurrentTimeInfo 开始 ==========")

            val semesterStartDate = userPreferences.getSemesterStartDateOnce()
            val maxWeek = _uiState.value.maxWeeks
            val currentDisplayWeek = _uiState.value.currentWeek

            // 计算当前实际教学周
            val actualCurrentWeek = when {
                semesterStartDate != null -> TimeUtils.calculateCurrentWeek(semesterStartDate)
                else -> null
            }
            AppLogger.i("CHD_CurrentWeek", "学期开始日期: $semesterStartDate, 计算的实际当前周: $actualCurrentWeek")

            // 获取今天星期几
            val todayDayOfWeek = TimeUtils.getTodayDayOfWeek()
            AppLogger.i("CHD_CurrentWeek", "今天是: $todayDayOfWeek")

            // [新功能] 计算当前显示周的周一日期
            val weekStartDate = if (semesterStartDate != null) {
                TimeUtils.calculateWeekStartDate(semesterStartDate, currentDisplayWeek)
            } else {
                null
            }
            AppLogger.i("CHD_CurrentWeek", "当前显示周$currentDisplayWeek 周一日期: $weekStartDate")

            // 更新 UI 状态
            _uiState.update { currentState ->
                // 如果当前正在查看的是实际当前周（或之前没有选择特定周），则保持在当前周
                // 否则保持用户选择的周次不变
                val shouldUpdateDisplayWeek = actualCurrentWeek != null &&
                    actualCurrentWeek in 1..maxWeek &&
                    currentState.currentWeek != actualCurrentWeek &&
                    currentState.currentWeek == currentState.actualCurrentWeek // 只有之前也在看当前周时才自动跳转

                currentState.copy(
                    actualCurrentWeek = actualCurrentWeek,
                    todayDayOfWeek = todayDayOfWeek,
                    weekStartDate = weekStartDate,  // [新功能] 更新周开始日期
                    currentWeek = if (shouldUpdateDisplayWeek) actualCurrentWeek else currentState.currentWeek
                )
            }

            AppLogger.i("CHD_CurrentWeek", "========== [ScheduleViewModel] refreshCurrentTimeInfo 结束 ==========")
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
                AppLogger.d("ScheduleViewModel", "[新功能] 取消水课标注: $courseName")
            } else {
                userPreferences.addWaterCourse(courseName, semester)
                _uiState.update {
                    it.copy(waterCourseNames = it.waterCourseNames + courseName)
                }
                AppLogger.d("ScheduleViewModel", "[新功能] 添加水课标注: $courseName")
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

    // ================ [课程提醒] 提醒设置相关 = =================

    /**
     * [课程提醒] 加载提醒设置
     */
    private fun loadReminderSettings() {
        viewModelScope.launch {
            val settings = userPreferences.getReminderSettingsOnce()
            _reminderSettings.value = settings
            AppLogger.d("ScheduleViewModel", "[课程提醒] 加载提醒设置: $settings")
        }
    }

    /**
     * [课程提醒] 更新提醒设置
     * [v102] 添加日历自动同步功能
     */
    fun updateReminderSettings(settings: ReminderSettings) {
        // [v102] 保存旧设置用于比较
        val oldSettings = _reminderSettings.value

        viewModelScope.launch {
            _reminderSettings.value = settings
            // [v103] 同时更新 _uiState 中的 reminderSettings，确保 syncToCalendar() 读取到最新值
            _uiState.update { it.copy(reminderSettings = settings) }
            userPreferences.saveReminderSettings(settings)
            AppLogger.d("ScheduleViewModel", "[课程提醒] 保存提醒设置: $settings")

            // 重新调度早八提醒
            reminderManager.scheduleEarlyMorningReminder(settings)

            // [健壮性优化] 重新调度上课前提醒
            val semesterStartDate = userPreferences.getSemesterStartDateOnce()
            val currentWeek = _uiState.value.actualCurrentWeek
            val courses = _uiState.value.courses
            if (semesterStartDate != null && currentWeek != null) {
                val campus = _uiState.value.campus  // [v108] 传递当前校区
                reminderManager.scheduleBeforeClassReminders(courses, settings, semesterStartDate, currentWeek, campus)
                AppLogger.d("ScheduleViewModel", "[课程提醒] 已重新调度上课前提醒, 校区: ${campus.displayName}")
            }

            // [v102] 检查是否需要自动重新同步日历
            if (settings.calendarSyncEnabled && shouldResyncCalendar(oldSettings, settings)) {
                AppLogger.i("ScheduleViewModel", "[v102] 日历提醒设置变更，自动重新同步")
                syncToCalendar()
            }
        }
    }

    /**
     * [v102] 判断是否需要重新同步日历
     * 比较影响日历提醒的关键字段
     */
    private fun shouldResyncCalendar(old: ReminderSettings, new: ReminderSettings): Boolean {
        return old.calendarBeforeClassReminderEnabled != new.calendarBeforeClassReminderEnabled ||
               old.calendarEarlyMorningReminderEnabled != new.calendarEarlyMorningReminderEnabled ||
               old.beforeClassReminderMinutes != new.beforeClassReminderMinutes ||
               old.earlyMorningReminderHour != new.earlyMorningReminderHour ||
               old.earlyMorningReminderMinute != new.earlyMorningReminderMinute
    }

    /**
     * [课程提醒] 同步课程到日历
     * [v98] 根据当前选择的校区使用对应的上课时间
     * [v99 Debug] 添加关键日期计算 debug 日志
     * [v100] 添加同步状态更新
     * [v101] 传递 ReminderSettings，支持课前提醒和早八提醒
     */
    fun syncToCalendar() {
        viewModelScope.launch {
            AppLogger.i("CHD_CalendarDebug", "========== [v101] syncToCalendar 开始 ==========")

            // [v100] 设置同步中状态
            _uiState.update { it.copy(calendarSyncState = CalendarSyncState.Syncing) }

            // 获取课程数据
            val courses = _uiState.value.courses
            val semesterStartDate = userPreferences.getSemesterStartDateOnce()
            // [v98] 获取当前选择的校区
            val campus = _uiState.value.campus
            // [v101] 获取提醒设置
            val reminderSettings = _uiState.value.reminderSettings

            // [v99 Debug] 入口参数日志
            val today = java.time.LocalDate.now()
            val todayDayOfWeek = today.dayOfWeek.value
            AppLogger.i("CHD_CalendarDebug", "[v101 入口] 今天: $today (周$todayDayOfWeek)")
            AppLogger.i("CHD_CalendarDebug", "[v101 入口] 学期开始日期: $semesterStartDate")
            AppLogger.i("CHD_CalendarDebug", "[v101 入口] 课程数: ${courses.size}")
            AppLogger.i("CHD_CalendarDebug", "[v101 入口] 校区: ${campus.displayName}")
            AppLogger.i("CHD_CalendarDebug", "[v101 入口] 课前提醒: ${reminderSettings.calendarBeforeClassReminderEnabled}")
            AppLogger.i("CHD_CalendarDebug", "[v101 入口] 早八提醒: ${reminderSettings.calendarEarlyMorningReminderEnabled}")

            // 验证学期开始日期
            if (semesterStartDate != null) {
                val expectedMonday = semesterStartDate.let {
                    try {
                        java.time.LocalDate.parse(it, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    } catch (e: Exception) {
                        null
                    }
                }
                if (expectedMonday != null) {
                    val isMonday = expectedMonday.dayOfWeek.value == 1
                    AppLogger.i("CHD_CalendarDebug", "[v101 验证] 学期开始日期是周一: $isMonday (${expectedMonday.dayOfWeek})")
                }
            }

            if (courses.isEmpty()) {
                // [v100] 设置错误状态
                _uiState.update {
                    it.copy(
                        calendarSyncState = CalendarSyncState.Error("没有课程可同步"),
                        errorMessage = "没有课程可同步"
                    )
                }
                AppLogger.w("CHD_CalendarDebug", "[v101] 没有课程可同步")
                AppLogger.i("CHD_CalendarDebug", "========== [v101] syncToCalendar 结束（无课程）==========")
                return@launch
            }

            if (semesterStartDate == null) {
                // [v100] 设置错误状态
                _uiState.update {
                    it.copy(
                        calendarSyncState = CalendarSyncState.Error("缺少学期开始日期，请先同步课表"),
                        errorMessage = "缺少学期开始日期，请先同步课表"
                    )
                }
                AppLogger.w("CHD_CalendarDebug", "[v101] 缺少学期开始日期")
                AppLogger.i("CHD_CalendarDebug", "========== [v101] syncToCalendar 结束（无学期日期）==========")
                return@launch
            }

            // 调用日历同步服务 [v98] 传入校区参数 [v101] 传入提醒设置
            try {
                val result = calendarSyncService.syncCoursesToCalendar(
                    courses,
                    semesterStartDate,
                    campus,
                    reminderSettings  // [v101]
                )
                // [v101] 使用 SyncResult 更新状态
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
                AppLogger.i("CHD_CalendarDebug", "[v101] 同步完成: $result")
                AppLogger.i("CHD_CalendarDebug", "========== [v101] syncToCalendar 结束 ==========")
            } catch (e: Exception) {
                // [v100] 设置错误状态
                _uiState.update {
                    it.copy(
                        calendarSyncState = CalendarSyncState.Error(e.message ?: "同步失败"),
                        errorMessage = "同步失败: ${e.message}"
                    )
                }
                AppLogger.e("CHD_CalendarDebug", "[v101] 同步失败", e)
                AppLogger.i("CHD_CalendarDebug", "========== [v101] syncToCalendar 异常结束 ==========")
            }
        }
    }

    /**
     * [v98] 删除日历中的所有课程事件
     * [v100] 添加删除状态更新
     */
    fun deleteCalendarEvents() {
        viewModelScope.launch {
            AppLogger.d("ScheduleViewModel", "[v98] 开始删除日历事件...")

            // [v100] 设置删除中状态
            _uiState.update { it.copy(calendarSyncState = CalendarSyncState.Deleting) }

            try {
                val deleted = calendarSyncService.deleteCalendar()
                if (deleted) {
                    // [v100] 设置删除完成状态
                    _uiState.update {
                        it.copy(
                            calendarSyncState = CalendarSyncState.Deleted,
                            errorMessage = "已删除日历中的所有课程事件"
                        )
                    }
                    AppLogger.d("ScheduleViewModel", "[v98] 删除日历成功")
                } else {
                    // [v100] 设置错误状态
                    _uiState.update {
                        it.copy(
                            calendarSyncState = CalendarSyncState.Error("删除日历失败，请检查权限"),
                            errorMessage = "删除日历失败，请检查权限"
                        )
                    }
                    AppLogger.w("ScheduleViewModel", "[v98] 删除日历失败")
                }
            } catch (e: Exception) {
                // [v100] 设置错误状态
                _uiState.update {
                    it.copy(
                        calendarSyncState = CalendarSyncState.Error(e.message ?: "删除失败"),
                        errorMessage = "删除失败: ${e.message}"
                    )
                }
                AppLogger.e("ScheduleViewModel", "[v98] 删除日历异常", e)
            }
        }
    }

    /**
     * [课程提醒] 权限结果处理 - 通知权限
     */
    fun onNotificationPermissionResult(isGranted: Boolean) {
        AppLogger.d("ScheduleViewModel", "[课程提醒] 通知权限结果: $isGranted")
        if (isGranted) {
            // 权限授予后重新调度提醒
            viewModelScope.launch {
                val settings = _reminderSettings.value
                reminderManager.scheduleEarlyMorningReminder(settings)
            }
        }
    }

    /**
     * [课程提醒] 权限结果处理 - 日历权限
     */
    fun onCalendarPermissionResult(isGranted: Boolean) {
        AppLogger.d("ScheduleViewModel", "[课程提醒] 日历权限结果: $isGranted")
        if (isGranted) {
            // 权限授予后自动同步
            syncToCalendar()
        }
    }

    /**
     * [课程提醒] 检查是否可以调度精确闹钟
     */
    fun canScheduleExactAlarms(): Boolean {
        return reminderManager.canScheduleExactAlarms()
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
