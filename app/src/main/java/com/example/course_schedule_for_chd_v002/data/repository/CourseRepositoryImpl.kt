package com.example.course_schedule_for_chd_v002.data.repository

import com.example.course_schedule_for_chd_v002.data.local.database.CourseDao
import com.example.course_schedule_for_chd_v002.data.local.database.entity.CourseEntity
import com.example.course_schedule_for_chd_v002.data.local.preferences.UserPreferences
import com.example.course_schedule_for_chd_v002.data.remote.api.CasApi
import com.example.course_schedule_for_chd_v002.data.remote.api.EamsApi
import com.example.course_schedule_for_chd_v002.data.remote.client.CookieManager
import com.example.course_schedule_for_chd_v002.data.remote.parser.ScheduleHtmlParser
import com.example.course_schedule_for_chd_v002.domain.model.Course
import com.example.course_schedule_for_chd_v002.domain.repository.ICourseRepository
import com.example.course_schedule_for_chd_v002.domain.repository.LoginResult
import com.example.course_schedule_for_chd_v002.util.Constants
import com.example.course_schedule_for_chd_v002.util.JsonUtils
import kotlinx.coroutines.flow.first

/**
 * 课程仓库实现
 * 整合网络请求、HTML解析和本地存储
 *
 * 主要职责：
 * - 处理CAS统一认证登录流程
 * - 从教务系统获取课表数据
 * - 解析HTML并存储到本地数据库
 * - 提供本地课程数据访问
 */
class CourseRepositoryImpl(
    private val casApi: CasApi,
    private val eamsApi: EamsApi,
    private val cookieManager: CookieManager,
    private val htmlParser: ScheduleHtmlParser,
    private val userPreferences: UserPreferences,
    private val courseDao: CourseDao
) : ICourseRepository {

    /**
     * 用户登录
     * @param username 用户名（学号）
     * @param password 密码
     * @return 登录结果
     */
    override suspend fun login(
        username: String,
        password: String
    ): Result<LoginResult> {
        return try {
            // 1. 获取登录页面信息
            val loginPage = casApi.getLoginPage(Constants.CasUrls.LOGIN_SERVICE).getOrNull()
                ?: return Result.success(
                    LoginResult(
                        success = false,
                        errorMessage = "[X] Cannot get login page"
                    )
                )

            // 2. 提交登录
            val loginResult = casApi.login(
                username = username,
                password = password,
                loginPage = loginPage,
                serviceUrl = Constants.CasUrls.LOGIN_SERVICE
            )

            if (loginResult.isFailure) {
                return Result.success(
                    LoginResult(
                        success = false,
                        errorMessage = loginResult.exceptionOrNull()?.message ?: "[X] Login failed"
                    )
                )
            }

            // 3. 验证登录状态
            val isLoggedIn = eamsApi.accessHomePage().getOrDefault(false)

            if (!isLoggedIn) {
                return Result.success(
                    LoginResult(
                        success = false,
                        errorMessage = "[X] Login verification failed, please retry"
                    )
                )
            }

            // 4. 获取学生信息
            val studentName = eamsApi.getStudentName().getOrDefault("")
            val studentId = eamsApi.getStudentId().getOrDefault(0L)

            // 5. 保存登录状态
            userPreferences.saveLoginState(
                isLoggedIn = true,
                username = username,
                studentId = studentId.toString(),
                studentName = studentName
            )

            Result.success(
                LoginResult(
                    success = true,
                    studentName = studentName,
                    studentId = studentId.toString()
                )
            )
        } catch (e: Exception) {
            Result.success(
                LoginResult(
                    success = false,
                    errorMessage = e.message ?: "[X] Login error"
                )
            )
        }
    }

    /**
     * 检查登录状态
     * @return 是否已登录
     */
    override suspend fun isLoggedIn(): Boolean {
        // 检查 DataStore 中的登录状态
        val savedLoginState = userPreferences.isLoggedIn.first()
        if (!savedLoginState) return false

        // 检查 Cookie 是否有效
        if (!cookieManager.hasSessionCookie()) return false

        // 验证服务器端会话
        return eamsApi.accessHomePage().getOrDefault(false)
    }

    /**
     * 登出
     */
    override suspend fun logout() {
        // 清除 Cookie
        cookieManager.clearAll()

        // 清除保存的登录状态
        userPreferences.clear()
    }

    // ================ WebView 登录相关 ================

    /**
     * 验证 WebView 登录状态
     * 从 WebView 同步 Cookie 后验证登录状态
     */
    override suspend fun verifyWebViewLogin(): Boolean {
        // 从 WebView 同步 Cookie 到 OkHttp
        cookieManager.syncFromWebView(Constants.EamsUrls.HOME_PAGE)

        // 验证登录状态
        val isLoggedIn = eamsApi.accessHomePage().getOrDefault(false)

        if (isLoggedIn) {
            // 获取学生信息并保存登录状态
            val studentName = eamsApi.getStudentName().getOrNull() ?: ""
            val studentId = eamsApi.getStudentId().getOrNull() ?: 0L

            userPreferences.saveLoginState(
                isLoggedIn = true,
                username = studentId.toString(),
                studentId = studentId.toString(),
                studentName = studentName
            )
        }

        return isLoggedIn
    }

    /**
     * 获取学生姓名
     */
    override suspend fun getStudentName(): String? {
        return eamsApi.getStudentName().getOrNull()
    }

    /**
     * 获取学生 ID
     */
    override suspend fun getStudentId(): String? {
        return eamsApi.getStudentId().getOrNull()?.toString()
    }

    /**
     * 获取当前学期
     * @return 当前学期字符串，未设置返回 null
     */
    override suspend fun getCurrentSemester(): String? {
        val semester = userPreferences.currentSemester.first()
        return semester.ifBlank { null }
    }

    /**
     * 获取远程课表
     * @param semester 学期标识
     * @return 课程列表
     */
    override suspend fun fetchRemoteSchedule(semester: String): Result<List<Course>> {
        return try {
            // 检查登录状态
            if (!isLoggedIn()) {
                return Result.failure(Exception("[X] Please login first"))
            }

            // 获取课表 HTML
            val html = eamsApi.getCourseTableHtml(semester).getOrNull()
                ?: return Result.failure(Exception("[X] Cannot get course table"))

            // 解析 HTML
            val entities = htmlParser.parse(html, semester)

            // 转换为领域模型
            val courses = entities.map { it.toDomainModel() }

            // 保存到本地数据库
            if (courses.isNotEmpty()) {
                // 先删除该学期的旧数据
                courseDao.deleteBySemester(semester)
                // 插入新数据
                courseDao.insertAll(entities)
            }

            // 更新当前学期
            userPreferences.saveCurrentSemester(semester)

            Result.success(courses)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取本地课表
     * @param semester 学期标识
     * @return 课程列表
     */
    override suspend fun getLocalSchedule(semester: String): List<Course> {
        return courseDao.getBySemester(semester).map { it.toDomainModel() }
    }

    /**
     * 保存课表
     * @param courses 课程列表
     */
    override suspend fun saveSchedule(courses: List<Course>) {
        val entities = courses.map { CourseEntity.fromDomainModel(it) }
        courseDao.insertAll(entities)
    }

    /**
     * 删除课表
     * @param semester 学期标识
     */
    override suspend fun deleteSchedule(semester: String) {
        courseDao.deleteBySemester(semester)
    }

    /**
     * 获取所有学期
     * @return 学期列表
     */
    override suspend fun getAllSemesters(): List<String> {
        return courseDao.getAllSemesters()
    }

    /**
     * 导出课程为 JSON 字符串
     * @param semester 学期标识
     * @return JSON 字符串
     */
    override suspend fun exportScheduleToJson(semester: String): String {
        val courses = getLocalSchedule(semester)
        return JsonUtils.exportCoursesToJson(courses)
    }

    /**
     * 从 JSON 字符串导入课程
     * @param jsonString JSON 字符串
     * @param semester 目标学期
     * @return 导入的课程数量
     */
    override suspend fun importScheduleFromJson(jsonString: String, semester: String): Int {
        val courses = JsonUtils.importCoursesFromJson(jsonString)
            .map { it.copy(semester = semester) }  // 确保学期一致

        if (courses.isNotEmpty()) {
            // 先删除旧数据
            courseDao.deleteBySemester(semester)
            // 插入新数据
            val entities = courses.map { CourseEntity.fromDomainModel(it) }
            courseDao.insertAll(entities)
        }

        return courses.size
    }
}
