package com.example.course_schedule_for_chd_v002.data.repository

import com.example.course_schedule_for_chd_v002.data.local.database.CourseDao
import com.example.course_schedule_for_chd_v002.data.local.database.entity.CourseEntity
import com.example.course_schedule_for_chd_v002.data.local.preferences.UserPreferences
import com.example.course_schedule_for_chd_v002.data.remote.api.CasApi
import com.example.course_schedule_for_chd_v002.data.remote.dto.CasLoginPage
import com.example.course_schedule_for_chd_v002.data.remote.api.EamsApi
import com.example.course_schedule_for_chd_v002.data.remote.client.CookieManager
import com.example.course_schedule_for_chd_v002.data.remote.parser.ScheduleHtmlParser
import com.example.course_schedule_for_chd_v002.domain.model.Course
import com.example.course_schedule_for_chd_v002.domain.repository.ICourseRepository
import com.example.course_schedule_for_chd_v002.domain.repository.LoginResult
import com.example.course_schedule_for_chd_v002.util.Constants
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

    // 缓存登录页面信息
    private var cachedLoginPage: CasLoginPage? = null

    /**
     * 用户登录
     * @param username 用户名（学号）
     * @param password 密码
     * @param captcha 验证码
     * @return 登录结果
     */
    override suspend fun login(
        username: String,
        password: String,
        captcha: String
    ): Result<LoginResult> {
        return try {
            // 1. 获取登录页面信息
            val loginPage = cachedLoginPage
                ?: casApi.getLoginPage(Constants.CasUrls.LOGIN_SERVICE).getOrNull()
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
                captcha = captcha,
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

            // 清除缓存的登录页面
            cachedLoginPage = null

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
     * 获取验证码图片
     * @return 验证码图片的字节数据
     */
    override suspend fun getCaptchaImage(): Result<ByteArray> {
        return try {
            // 先获取登录页面，缓存信息
            val loginPage = casApi.getLoginPage(Constants.CasUrls.LOGIN_SERVICE).getOrNull()
                ?: return Result.failure(Exception("[X] Cannot get login page"))

            cachedLoginPage = loginPage

            // 获取验证码图片
            casApi.getCaptchaImage(loginPage.captchaUrl)
        } catch (e: Exception) {
            Result.failure(e)
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
}
