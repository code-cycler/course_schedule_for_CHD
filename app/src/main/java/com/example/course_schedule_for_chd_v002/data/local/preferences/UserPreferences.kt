package com.example.course_schedule_for_chd_v002.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.course_schedule_for_chd_v002.domain.model.ReminderSettings
import com.example.course_schedule_for_chd_v002.util.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// 扩展属性：创建 DataStore 实例
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * 用户偏好设置管理
 * 使用 DataStore 存储用户登录状态和基本信息
 */
class UserPreferences(private val context: Context) {

    companion object {
        private val KEY_IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_STUDENT_ID = stringPreferencesKey("student_id")
        private val KEY_STUDENT_NAME = stringPreferencesKey("student_name")
        private val KEY_CURRENT_SEMESTER = stringPreferencesKey("current_semester")
        private val KEY_CAMPUS = stringPreferencesKey("campus")  // [v61] 校区选择
        private val KEY_CURRENT_WEEK = intPreferencesKey("current_week")  // 当前教学周
        private const val KEY_CONFLICT_CACHE_PREFIX = "conflict_cache_"  // [v74] 冲突缓存前缀
        private val KEY_SEMESTER_START_DATE = stringPreferencesKey("semester_start_date")  // [新功能] 学期开始日期
        private val KEY_LAST_PARSED_WEEK = intPreferencesKey("last_parsed_week")  // [新功能] 上次从首页解析的周次

        // [课程提醒] 提醒设置
        private val KEY_REMINDER_SETTINGS = stringPreferencesKey("reminder_settings")

        // [权限管理] 首次启动标记
        private val KEY_FIRST_LAUNCH = booleanPreferencesKey("first_launch")
    }

    /**
     * 获取登录状态
     */
    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_IS_LOGGED_IN] ?: false
    }

    /**
     * 获取用户名
     */
    val username: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_USERNAME] ?: ""
    }

    /**
     * 获取学生 ID
     */
    val studentId: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_STUDENT_ID] ?: ""
    }

    /**
     * 获取学生姓名
     */
    val studentName: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_STUDENT_NAME] ?: ""
    }

    /**
     * 获取当前学期
     */
    val currentSemester: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_CURRENT_SEMESTER] ?: ""
    }

    /**
     * [v61] 获取校区选择
     */
    val campus: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_CAMPUS] ?: "WEISHUI"
    }

    /**
     * 获取当前教学周
     */
    val currentWeek: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_CURRENT_WEEK] ?: 1
    }

    /**
     * 保存登录状态
     */
    suspend fun saveLoginState(
        isLoggedIn: Boolean,
        username: String = "",
        studentId: String = "",
        studentName: String = ""
    ) {
        context.dataStore.edit { preferences ->
            preferences[KEY_IS_LOGGED_IN] = isLoggedIn
            preferences[KEY_USERNAME] = username
            preferences[KEY_STUDENT_ID] = studentId
            preferences[KEY_STUDENT_NAME] = studentName
        }
    }

    /**
     * 保存当前学期
     */
    suspend fun saveCurrentSemester(semester: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CURRENT_SEMESTER] = semester
        }
    }

    /**
     * 获取登录状态（一次性读取）
     */
    suspend fun isLoggedInOnce(): Boolean {
        return isLoggedIn.first()
    }

    /**
     * 获取用户名（一次性读取）
     */
    suspend fun getUsernameOnce(): String {
        return username.first()
    }

    /**
     * 获取学生信息（一次性读取）
     */
    suspend fun getStudentInfoOnce(): Pair<String, String> {
        return Pair(studentId.first(), studentName.first())
    }

    /**
     * 清除所有偏好设置（登出时调用）
     */
    suspend fun clear() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    /**
     * 更新登录状态
     */
    suspend fun updateLoginState(isLoggedIn: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_IS_LOGGED_IN] = isLoggedIn
        }
    }

    /**
     * 更新学生姓名
     */
    suspend fun updateStudentName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_STUDENT_NAME] = name
        }
    }

    /**
     * [v61] 保存校区选择
     */
    suspend fun saveCampus(campus: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CAMPUS] = campus
        }
    }

    /**
     * [v61] 获取校区选择（一次性读取）
     */
    suspend fun getCampusOnce(): String {
        return campus.first()
    }

    /**
     * 保存当前教学周
     */
    suspend fun saveCurrentWeek(week: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CURRENT_WEEK] = week
        }
    }

    /**
     * 获取当前教学周（一次性读取）
     */
    suspend fun getCurrentWeekOnce(): Int {
        return currentWeek.first()
    }

    // ================ [新功能] 学期开始日期相关 ================

    /**
     * [新功能] 保存学期开始日期
     * @param date 格式: "yyyy-MM-dd" 如 "2026-02-24"
     */
    suspend fun saveSemesterStartDate(date: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SEMESTER_START_DATE] = date
        }
        AppLogger.d("UserPreferences", "[新功能] 保存学期开始日期: $date")
    }

    /**
     * [新功能] 获取学期开始日期
     */
    val semesterStartDate: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_SEMESTER_START_DATE]
    }

    /**
     * [新功能] 获取学期开始日期（一次性读取）
     */
    suspend fun getSemesterStartDateOnce(): String? {
        return semesterStartDate.first()
    }

    /**
     * [新功能] 保存上次从首页解析的周次
     */
    suspend fun saveLastParsedWeek(week: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LAST_PARSED_WEEK] = week
        }
        AppLogger.d("UserPreferences", "[新功能] 保存解析周次: $week")
    }

    /**
     * [新功能] 获取上次从首页解析的周次
     */
    val lastParsedWeek: Flow<Int?> = context.dataStore.data.map { preferences ->
        preferences[KEY_LAST_PARSED_WEEK]
    }

    /**
     * [新功能] 获取上次从首页解析的周次（一次性读取）
     */
    suspend fun getLastParsedWeekOnce(): Int? {
        return lastParsedWeek.first()
    }

    // ================ [v74] 冲突缓存相关 ================

    /**
     * [v74] 保存冲突缓存
     * @param semester 学期
     * @param conflicts Map<周次, Set<冲突课程ID>>
     */
    suspend fun saveConflictCache(semester: String, conflicts: Map<Int, Set<Long>>) {
        val key = stringPreferencesKey(KEY_CONFLICT_CACHE_PREFIX + semester)
        val json = serializeConflictCache(conflicts)
        context.dataStore.edit { preferences ->
            preferences[key] = json
        }
        AppLogger.d("UserPreferences", "[v74] 保存冲突缓存: semester=$semester, ${conflicts.size}周有冲突")
    }

    /**
     * [v74] 获取冲突缓存
     * @param semester 学期
     * @return Map<周次, Set<冲突课程ID>>，无缓存返回空 Map
     */
    suspend fun getConflictCache(semester: String): Map<Int, Set<Long>> {
        val key = stringPreferencesKey(KEY_CONFLICT_CACHE_PREFIX + semester)
        val json = context.dataStore.data.map { preferences -> preferences[key] ?: "" }.first()
        return if (json.isNotEmpty()) {
            deserializeConflictCache(json)
        } else {
            emptyMap()
        }
    }

    /**
     * [v74] 清除冲突缓存
     * @param semester 学期
     */
    suspend fun clearConflictCache(semester: String) {
        val key = stringPreferencesKey(KEY_CONFLICT_CACHE_PREFIX + semester)
        context.dataStore.edit { preferences ->
            preferences.remove(key)
        }
        AppLogger.d("UserPreferences", "[v74] 清除冲突缓存: semester=$semester")
    }

    /**
     * [v74] 序列化冲突缓存为 JSON
     * 格式: {"1":[123,456],"5":[789]}
     */
    private fun serializeConflictCache(conflicts: Map<Int, Set<Long>>): String {
        if (conflicts.isEmpty()) return "{}"
        val entries = conflicts.entries.map { (week, ids) ->
            "\"$week\":[${ids.joinToString(",")}]"
        }
        return "{${entries.joinToString(",")}}"
    }

    /**
     * [v74] 反序列化冲突缓存
     */
    private fun deserializeConflictCache(json: String): Map<Int, Set<Long>> {
        if (json.isEmpty() || json == "{}") return emptyMap()

        val result = mutableMapOf<Int, Set<Long>>()
        try {
            // 移除外层 {}
            val content = json.trim().removeSurrounding("{", "}")
            if (content.isEmpty()) return result

            // 分割各个周次条目
            var i = 0
            while (i < content.length) {
                // 查找周次
                val weekStart = content.indexOf('"', i)
                if (weekStart == -1) break
                val weekEnd = content.indexOf('"', weekStart + 1)
                if (weekEnd == -1) break
                val week = content.substring(weekStart + 1, weekEnd).toIntOrNull()
                if (week == null) {
                    i = weekEnd + 1
                    continue
                }

                // 查找冒号后的数组
                val colonPos = content.indexOf(':', weekEnd)
                if (colonPos == -1) break
                val arrayStart = content.indexOf('[', colonPos)
                if (arrayStart == -1) break
                val arrayEnd = content.indexOf(']', arrayStart)
                if (arrayEnd == -1) break

                // 解析课程ID
                val arrayContent = content.substring(arrayStart + 1, arrayEnd)
                if (arrayContent.isNotEmpty()) {
                    val ids = arrayContent.split(",")
                        .mapNotNull { it.trim().toLongOrNull() }
                        .toSet()
                    if (ids.isNotEmpty()) {
                        result[week] = ids
                    }
                }

                i = arrayEnd + 1
                // 跳过逗号
                while (i < content.length && content[i] == ',') i++
            }
        } catch (e: Exception) {
            AppLogger.e("UserPreferences", "[v74] 反序列化冲突缓存失败: ${e.message}")
        }

        return result
    }

    // ================ [新功能] 水课标注相关 ================

    private val KEY_WATER_COURSES = stringPreferencesKey("water_courses")  // JSON 格式

    /**
     * [新功能] 获取水课名称列表（按学期）
     */
    val waterCourseNames: Flow<Map<String, Set<String>>> = context.dataStore.data.map { preferences ->
        val json = preferences[KEY_WATER_COURSES] ?: "{}"
        parseWaterCoursesJson(json)
    }

    /**
     * [新功能] 获取指定学期的水课列表
     */
    suspend fun getWaterCoursesForSemester(semester: String): Set<String> {
        return waterCourseNames.first()[semester] ?: emptySet()
    }

    /**
     * [新功能] 添加水课标注
     */
    suspend fun addWaterCourse(courseName: String, semester: String) {
        context.dataStore.edit { preferences ->
            val currentMap = parseWaterCoursesJson(preferences[KEY_WATER_COURSES] ?: "{}").toMutableMap()
            val currentSet = currentMap[semester]?.toMutableSet() ?: mutableSetOf()
            currentSet.add(courseName)
            currentMap[semester] = currentSet
            preferences[KEY_WATER_COURSES] = serializeWaterCoursesJson(currentMap)
        }
        AppLogger.d("UserPreferences", "[新功能] 添加水课标注: $courseName @ $semester")
    }

    /**
     * [新功能] 移除水课标注
     */
    suspend fun removeWaterCourse(courseName: String, semester: String) {
        context.dataStore.edit { preferences ->
            val currentMap = parseWaterCoursesJson(preferences[KEY_WATER_COURSES] ?: "{}").toMutableMap()
            val currentSet = currentMap[semester]?.toMutableSet() ?: mutableSetOf()
            currentSet.remove(courseName)
            if (currentSet.isEmpty()) {
                currentMap.remove(semester)
            } else {
                currentMap[semester] = currentSet
            }
            preferences[KEY_WATER_COURSES] = serializeWaterCoursesJson(currentMap)
        }
        AppLogger.d("UserPreferences", "[新功能] 移除水课标注: $courseName @ $semester")
    }

    /**
     * [新功能] 序列化水课列表为 JSON
     * 格式: {"2024-2025-1":["课程A","课程B"],"2024-2025-2":["课程C"]}
     */
    private fun serializeWaterCoursesJson(map: Map<String, Set<String>>): String {
        if (map.isEmpty()) return "{}"
        val entries = map.entries.map { (semester, names) ->
            val namesJson = names.joinToString(",") { "\"${it.escapeJson()}\"" }
            "\"${semester.escapeJson()}\":[$namesJson]"
        }
        return "{${entries.joinToString(",")}}"
    }

    /**
     * [新功能] 反序列化水课列表
     */
    private fun parseWaterCoursesJson(json: String): Map<String, Set<String>> {
        if (json.isEmpty() || json == "{}") return emptyMap()

        val result = mutableMapOf<String, Set<String>>()
        try {
            val content = json.trim().removeSurrounding("{", "}")
            if (content.isEmpty()) return result

            var i = 0
            while (i < content.length) {
                // 查找学期名
                val semesterStart = content.indexOf('"', i)
                if (semesterStart == -1) break
                val semesterEnd = content.indexOf('"', semesterStart + 1)
                if (semesterEnd == -1) break
                val semester = content.substring(semesterStart + 1, semesterEnd).unescapeJson()

                // 查找冒号后的数组
                val colonPos = content.indexOf(':', semesterEnd)
                if (colonPos == -1) break
                val arrayStart = content.indexOf('[', colonPos)
                if (arrayStart == -1) break
                val arrayEnd = content.indexOf(']', arrayStart)
                if (arrayEnd == -1) break

                // 解析课程名
                val arrayContent = content.substring(arrayStart + 1, arrayEnd)
                if (arrayContent.isNotEmpty()) {
                    val names = parseJsonStringArray(arrayContent)
                    if (names.isNotEmpty()) {
                        result[semester] = names.toSet()
                    }
                }

                i = arrayEnd + 1
                while (i < content.length && content[i] == ',') i++
            }
        } catch (e: Exception) {
            AppLogger.e("UserPreferences", "[新功能] 反序列化水课列表失败: ${e.message}")
        }

        return result
    }

    /**
     * [新功能] 解析 JSON 字符串数组
     */
    private fun parseJsonStringArray(content: String): List<String> {
        val result = mutableListOf<String>()
        var i = 0
        while (i < content.length) {
            val start = content.indexOf('"', i)
            if (start == -1) break
            val end = content.indexOf('"', start + 1)
            if (end == -1) break
            result.add(content.substring(start + 1, end).unescapeJson())
            i = end + 1
            while (i < content.length && (content[i] == ',' || content[i].isWhitespace())) i++
        }
        return result
    }

    /**
     * [新功能] JSON 字符串转义
     */
    private fun String.escapeJson(): String {
        return this.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }

    /**
     * [新功能] JSON 字符串反转义
     */
    private fun String.unescapeJson(): String {
        return this.replace("\\r", "\r")
            .replace("\\n", "\n")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    // ================ [课程提醒] 提醒设置相关 ================

    /**
     * [课程提醒] 获取提醒设置
     */
    val reminderSettings: Flow<ReminderSettings> = context.dataStore.data.map { preferences ->
        val json = preferences[KEY_REMINDER_SETTINGS] ?: ""
        if (json.isNotEmpty()) {
            ReminderSettings.fromJson(json)
        } else {
            ReminderSettings.DEFAULT
        }
    }

    /**
     * [课程提醒] 获取提醒设置（一次性读取）
     */
    suspend fun getReminderSettingsOnce(): ReminderSettings {
        val result = reminderSettings.first()
        AppLogger.d("UserPreferences", "[Debug] getReminderSettingsOnce: $result")
        return result
    }

    /**
     * [课程提醒] 保存提醒设置
     */
    suspend fun saveReminderSettings(settings: ReminderSettings) {
        AppLogger.d("UserPreferences", "[Debug] saveReminderSettings 开始: $settings")
        context.dataStore.edit { preferences ->
            preferences[KEY_REMINDER_SETTINGS] = settings.toJson()
        }
        AppLogger.d("UserPreferences", "[Debug] saveReminderSettings 完成")
    }

    /**
     * [日历同步] 更新日历同步设置（部分更新）
     */
    suspend fun updateCalendarSyncSettings(
        calendarSyncEnabled: Boolean? = null,
        calendarId: Long? = null,
        calendarBeforeClassReminderEnabled: Boolean? = null,
        calendarEarlyMorningReminderEnabled: Boolean? = null
    ) {
        val current = getReminderSettingsOnce()
        val updated = current.copy(
            calendarSyncEnabled = calendarSyncEnabled ?: current.calendarSyncEnabled,
            calendarId = calendarId ?: current.calendarId,
            calendarBeforeClassReminderEnabled = calendarBeforeClassReminderEnabled ?: current.calendarBeforeClassReminderEnabled,
            calendarEarlyMorningReminderEnabled = calendarEarlyMorningReminderEnabled ?: current.calendarEarlyMorningReminderEnabled
        )
        saveReminderSettings(updated)
    }

    // ================ [权限管理] 首次启动标记 ================

    /**
     * [权限管理] 检查是否首次启动
     * 用于决定是否显示权限请求引导页
     */
    val isFirstLaunch: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_FIRST_LAUNCH] ?: true
    }

    /**
     * [权限管理] 获取首次启动状态（一次性读取）
     */
    suspend fun isFirstLaunchOnce(): Boolean {
        return isFirstLaunch.first()
    }

    /**
     * [权限管理] 标记为非首次启动
     * 在用户完成权限引导或跳过后调用
     */
    suspend fun markAsNotFirstLaunch() {
        context.dataStore.edit { preferences ->
            preferences[KEY_FIRST_LAUNCH] = false
        }
        AppLogger.d("UserPreferences", "[权限管理] 标记为非首次启动")
    }
}
