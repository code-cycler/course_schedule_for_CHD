package com.example.course_schedule_for_chd_v002.domain.repository

import com.example.course_schedule_for_chd_v002.domain.model.Course

/**
 * 课程仓库接口
 * 定义数据访问的抽象接口，由 Data 层实现
 */
interface ICourseRepository {
    // 登录相关
    suspend fun login(username: String, password: String): Result<LoginResult>
    suspend fun isLoggedIn(): Boolean
    suspend fun logout()

    // WebView 登录相关
    /**
     * 验证 WebView 登录状态
     * 在 WebView 登录成功后调用，同步 Cookie 并验证
     * @return 是否登录成功
     */
    suspend fun verifyWebViewLogin(): Boolean

    /**
     * 获取学生姓名
     * @return 学生姓名，失败返回 null
     */
    suspend fun getStudentName(): String?

    /**
     * 获取学生 ID
     * @return 学生 ID 字符串，失败返回 null
     */
    suspend fun getStudentId(): String?

    // 课程表相关
    /**
     * 获取当前学期
     * @return 当前学期字符串，未设置返回 null
     */
    suspend fun getCurrentSemester(): String?

    suspend fun fetchRemoteSchedule(semester: String): Result<List<Course>>
    suspend fun getLocalSchedule(semester: String): List<Course>
    suspend fun saveSchedule(courses: List<Course>)
    suspend fun deleteSchedule(semester: String)
    suspend fun getAllSemesters(): List<String>

    // 导入导出相关
    /**
     * 导出课程为 JSON 字符串
     * @param semester 学期标识
     * @return JSON 字符串
     */
    suspend fun exportScheduleToJson(semester: String): String

    /**
     * 从 JSON 字符串导入课程
     * @param jsonString JSON 字符串
     * @param semester 目标学期
     * @return 导入的课程数量
     */
    suspend fun importScheduleFromJson(jsonString: String, semester: String): Int
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
