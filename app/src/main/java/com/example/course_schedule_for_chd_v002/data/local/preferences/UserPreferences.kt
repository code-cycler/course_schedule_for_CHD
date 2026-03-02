package com.example.course_schedule_for_chd_v002.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
}
