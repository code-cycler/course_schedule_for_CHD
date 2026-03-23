package com.example.course_schedule_for_chd_v002.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
        android.util.Log.d("UserPreferences", "[v74] 保存冲突缓存: semester=$semester, ${conflicts.size}周有冲突")
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
        android.util.Log.d("UserPreferences", "[v74] 清除冲突缓存: semester=$semester")
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
            android.util.Log.e("UserPreferences", "[v74] 反序列化冲突缓存失败: ${e.message}")
        }

        return result
    }
}
