package com.example.course_schedule_for_chd_v002.domain.repository

import com.example.course_schedule_for_chd_v002.domain.model.Course

/**
 * 课程仓库接口
 * 定义数据访问的抽象接口，由 Data 层实现
 */
interface ICourseRepository {
    // 登录相关
    suspend fun login(username: String, password: String, captcha: String): Result<LoginResult>
    suspend fun getCaptchaImage(): Result<ByteArray>
    suspend fun isLoggedIn(): Boolean
    suspend fun logout()

    // 课程表相关
    suspend fun fetchRemoteSchedule(semester: String): Result<List<Course>>
    suspend fun getLocalSchedule(semester: String): List<Course>
    suspend fun saveSchedule(courses: List<Course>)
    suspend fun deleteSchedule(semester: String)
    suspend fun getAllSemesters(): List<String>
}

/**
 * 登录结果
 */
data class LoginResult(
    val success: Boolean,
    val studentName: String? = null,
    val studentId: String? = null,
    val errorMessage: String? = null
)
