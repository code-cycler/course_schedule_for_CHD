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
     * 从 WebView 同步 Cookie 到 OkHttp
     * 用于 GeckoView 登录场景
     * @param url 当前页面 URL
     * @param cookies Cookie 字符串
     * @return 是否同步成功
     */
    fun syncCookiesFromWebView(url: String, cookies: String): Boolean

    /**
     * [v61] 用 OkHttp 获取课表
     * 在 CAS 登录成功后，用已同步的 Cookie 获取课表数据
     * @param semester 学期标识
     * @return 课程列表
     */
    suspend fun fetchCourseTableWithOkHttp(semester: String): Result<List<Course>>

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

    /**
     * 直接解析 HTML 内容为课程列表
     * 用于 GeckoView 场景，从渲染后的 HTML 解析课程
     * @param html 渲染后的 HTML 内容
     * @param semester 学期标识
     * @return 解析出的课程列表
     */
    suspend fun parseHtmlToCourses(html: String, semester: String): Result<List<Course>>

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

    /**
     * 从教务系统首页获取当前教学周
     * @return Pair<学期字符串, 当前教学周>，失败返回 null
     */
    suspend fun fetchCurrentWeek(): Pair<String, Int>?

    /**
     * [v73] 从首页 HTML 解析当前教学周（直接解析，不发起网络请求）
     * 用于 WebView 场景，从 JS 渲染后的首页 HTML 解析教学周信息
     * @param html 首页 HTML（WebView 获取的渲染后 HTML）
     * @return Pair<学期字符串, 当前教学周>，失败返回 null
     */
    fun parseCurrentWeekFromHtml(html: String): Pair<String, Int>?

    // ================ [v74] 冲突预计算缓存相关 ================

    /**
     * [v74] 预计算并缓存所有周的冲突信息
     * 在导入课表后调用，一次性计算 1-25 周的所有课程冲突
     * @param courses 课程列表
     * @param semester 学期
     */
    suspend fun precomputeAndCacheConflicts(courses: List<Course>, semester: String)

    /**
     * [v74] 获取指定周的冲突课程ID
     * @param semester 学期
     * @param week 周次
     * @return 冲突课程ID集合，无缓存返回 null
     */
    suspend fun getConflictsForWeek(semester: String, week: Int): Set<Long>?

    /**
     * [v74] 获取所有周的冲突缓存
     * @param semester 学期
     * @return Map<周次, Set<冲突课程ID>>，无缓存返回空 Map
     */
    suspend fun getConflictCache(semester: String): Map<Int, Set<Long>>
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
