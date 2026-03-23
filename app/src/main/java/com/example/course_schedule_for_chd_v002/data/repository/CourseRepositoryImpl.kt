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
import com.example.course_schedule_for_chd_v002.util.WebViewLogger
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
private const val REPO_TAG = "CourseRepository"

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
        android.util.Log.d(REPO_TAG, "=== isLoggedIn 检查 ===")

        // 检查 DataStore 中的登录状态
        val savedLoginState = userPreferences.isLoggedIn.first()
        android.util.Log.d(REPO_TAG, "DataStore 登录状态: $savedLoginState")
        if (!savedLoginState) {
            android.util.Log.d(REPO_TAG, "isLoggedIn 返回 false (DataStore)")
            return false
        }

        // 检查 Cookie 是否有效
        val hasCookie = cookieManager.hasSessionCookie()
        android.util.Log.d(REPO_TAG, "Cookie 状态: hasSessionCookie=$hasCookie")
        if (!hasCookie) {
            android.util.Log.d(REPO_TAG, "isLoggedIn 返回 false (无 Cookie)")
            return false
        }

        // 验证服务器端会话
        val serverValid = eamsApi.accessHomePage().getOrDefault(false)
        android.util.Log.d(REPO_TAG, "服务器验证: $serverValid")
        return serverValid
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
     * [v72] 从 WebView 同步 Cookie 到 OkHttp
     * 使用系统 WebView，直接从 Android CookieManager 同步
     */
    override fun syncCookiesFromWebView(url: String, cookies: String): Boolean {
        return try {
            // 使用系统 WebView 的 CookieManager 同步
            cookieManager.syncFromWebView(url)
            WebViewLogger.logSuccess("Cookie", "同步成功，Cookie 数量: ${cookieManager.getAllCookies().size}")
            true
        } catch (e: Exception) {
            WebViewLogger.logError("Cookie", "同步失败: ${e.message}")
            false
        }
    }

    /**
     * [v61] 用 OkHttp 获取课表
     * 在 CAS 登录成功后，用已同步的 Cookie 获取课表数据
     *
     * [v65] 修改：入口页面包含课表数据 table0.activities，直接解析即可
     */
    override suspend fun fetchCourseTableWithOkHttp(semester: String): Result<List<Course>> {
        WebViewLogger.logDebug(REPO_TAG, "=== fetchCourseTableWithOkHttp 开始 ===")

        return try {
            // [v65] 访问课表入口页面，直接获取包含课表数据的 HTML
            // 入口页面包含 table0.activities JavaScript 变量
            WebViewLogger.logNavigation("OkHttp", "访问课表入口页面...")
            val html = eamsApi.accessCourseTableEntry().getOrNull()
            if (html.isNullOrEmpty()) {
                WebViewLogger.logError("OkHttp", "访问课表入口失败")
                return Result.failure(Exception("Cannot access course table entry"))
            }
            WebViewLogger.logSuccess("OkHttp", "课表入口访问成功，HTML 长度: ${html.length}")

            // 检查 HTML 是否包含课表数据
            val hasTaskActivity = html.contains("TaskActivity")
            val hasTable0 = html.contains("table0.activities") || html.contains("table0 = new CourseTable")
            WebViewLogger.logParseDetail("HTML 包含 TaskActivity: $hasTaskActivity, table0: $hasTable0")

            // 直接解析 HTML（入口页面包含 table0.activities）
            WebViewLogger.logParseDetail("开始解析 HTML...")
            val entities = htmlParser.parse(html, semester)
            WebViewLogger.logParseDetail("解析完成，原始课程数: ${entities.size}")

            // 保存到数据库
            if (entities.isNotEmpty()) {
                courseDao.deleteBySemester(semester)
                courseDao.insertAll(entities)
                WebViewLogger.logDatabaseSave(entities.size, true)
            } else {
                WebViewLogger.logError("OkHttp", "解析结果为空，请检查 HTML 结构")
            }

            // 更新当前学期
            userPreferences.saveCurrentSemester(semester)

            // 转换为领域模型
            val courses = entities.map { it.toDomainModel() }
            Result.success(courses)
        } catch (e: Exception) {
            WebViewLogger.logError("OkHttp", "获取课表异常: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * 验证 WebView 登录状态
     * 从 WebView 同步 Cookie 后验证登录状态
     *
     * 注意：GeckoView 和 OkHttp 有独立的 Cookie 存储，无法同步。
     * 对于 GeckoView 场景，我们假设用户已登录（因为他们已在 GeckoView 中看到课表页面）
     * 并直接尝试获取课表数据。
     */
    override suspend fun verifyWebViewLogin(): Boolean {
        android.util.Log.d(REPO_TAG, "=== verifyWebViewLogin 开始 ===")

        // 对于 GeckoView 场景：
        // 由于 GeckoView 和 OkHttp 的 Cookie 存储完全隔离，
        // syncFromWebView 无法获取 GeckoView 的 Cookie。
        // 但是，用户已经通过 GeckoView 登录并看到了课表页面，
        // 所以我们假设用户已登录，直接尝试获取课表。

        // 尝试同步 Cookie（可能失败，但不影响后续操作）
        try {
            android.util.Log.d(REPO_TAG, "尝试从 WebView 同步 Cookie...")
            cookieManager.syncFromWebView(Constants.EamsUrls.HOME_PAGE)
            android.util.Log.d(REPO_TAG, "Cookie 同步完成，当前 Cookie 数量: ${cookieManager.getAllCookies().size}")
        } catch (e: Exception) {
            android.util.Log.w(REPO_TAG, "同步 Cookie 失败: ${e.message}")
        }

        // 验证登录状态
        android.util.Log.d(REPO_TAG, "调用 eamsApi.accessHomePage()...")
        val isLoggedIn = eamsApi.accessHomePage().getOrDefault(false)

        android.util.Log.i(REPO_TAG, "verifyWebViewLogin 结果: isLoggedIn=$isLoggedIn, hasSessionCookie=${cookieManager.hasSessionCookie()}")

        if (isLoggedIn) {
            // 保存登录状态（不需要学生信息）
            userPreferences.saveLoginState(
                isLoggedIn = true,
                username = "",
                studentId = "",
                studentName = ""
            )
            android.util.Log.i(REPO_TAG, "[OK] 登录状态已保存")
        } else {
            // GeckoView 场景：即使用 OkHttp 验证失败，用户也可能已在 GeckoView 中登录
            // 保存登录状态，允许用户继续操作
            android.util.Log.w(REPO_TAG, "OkHttp 验证失败，但用户可能已在 GeckoView 中登录，保存登录状态")
            userPreferences.saveLoginState(
                isLoggedIn = true,
                username = "",
                studentId = "",
                studentName = ""
            )
        }

        return true  // GeckoView 场景下始终返回 true
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
     * 直接解析 HTML 或 JSON 内容为课程列表
     * [v53] 支持两种格式：
     * - HTML 格式：直接解析 HTML
     * - JSON 格式：以 "JSON:" 开头，直接从 table0.activities 提取
     *
     * @param html 渲染后的 HTML 内容或 JSON 数据
     * @param semester 学期标识
     * @return 解析出的课程列表
     */
    override suspend fun parseHtmlToCourses(html: String, semester: String): Result<List<Course>> {
        return try {
            WebViewLogger.logParseDetail("=== parseHtmlToCourses 开始 ===")

            // [v53] 检测输入格式
            val entities = if (html.startsWith("JSON:")) {
                val json = html.removePrefix("JSON:")
                WebViewLogger.logParseDetail("[v53] Using JSON parsing method")
                htmlParser.parseActivitiesJson(json, semester)
            } else {
                WebViewLogger.logParseDetail("Using HTML parsing method")
                htmlParser.parse(html, semester)
            }

            WebViewLogger.logParseDetail("Parsing complete, course count: ${entities.size}")

            // 保存到数据库
            if (entities.isNotEmpty()) {
                courseDao.deleteBySemester(semester)
                courseDao.insertAll(entities)
                WebViewLogger.logDatabaseSave(entities.size, true)
            } else {
                WebViewLogger.logParseDetail("[WARN] Parsing result is empty, not saved to database")
            }

            // 更新当前学期
            userPreferences.saveCurrentSemester(semester)

            // 转换为领域模型
            val courses = entities.map { it.toDomainModel() }
            Result.success(courses)
        } catch (e: Exception) {
            WebViewLogger.logParseDetail("[ERROR] parseHtmlToCourses exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * 获取远程课表
     * @param semester 学期标识
     * @return 课程列表
     */
    override suspend fun fetchRemoteSchedule(semester: String): Result<List<Course>> {
        android.util.Log.d(REPO_TAG, "=== fetchRemoteSchedule 开始, semester=$semester ===")

        return try {
            // GeckoView 场景：跳过登录状态检查，直接尝试获取课表
            // 因为 GeckoView 的 Cookie 和 OkHttp 隔离，Cookie 检查会失败
            // 但用户可能已在 GeckoView 中登录，所以直接尝试获取

            // 获取课表 HTML - 使用 GET 请求直接获取课表页面
            android.util.Log.d(REPO_TAG, "获取课表 HTML (GET)...")
            val html = eamsApi.getCourseTablePage().getOrNull()
            if (html == null) {
                android.util.Log.e(REPO_TAG, "fetchRemoteSchedule 失败: 无法获取课表 HTML")
                return Result.failure(Exception("[X] Cannot get course table"))
            }
            android.util.Log.d(REPO_TAG, "获取到 HTML，长度: ${html.length}")

            // 解析 HTML（支持原始 HTML 中的 TaskActivity 数据和渲染后的 infoTitle 单元格）
            android.util.Log.d(REPO_TAG, "解析 HTML...")
            val entities = htmlParser.parse(html, semester)

            // 转换为领域模型
            val courses = entities.map { it.toDomainModel() }
            android.util.Log.i(REPO_TAG, "解析完成，课程数量: ${courses.size}")

            // 保存到本地数据库
            if (courses.isNotEmpty()) {
                // 先删除该学期的旧数据
                courseDao.deleteBySemester(semester)
                // 插入新数据
                courseDao.insertAll(entities)
                android.util.Log.i(REPO_TAG, "已保存 ${courses.size} 门课程到数据库")

                // 更新当前学期
                userPreferences.saveCurrentSemester(semester)
            }

            android.util.Log.i(REPO_TAG, "[OK] fetchRemoteSchedule 成功")
            Result.success(courses)
        } catch (e: Exception) {
            android.util.Log.e(REPO_TAG, "fetchRemoteSchedule 异常: ${e.message}", e)
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

    /**
     * 从教务系统首页获取当前教学周
     * @return Pair<学期字符串, 当前教学周>，失败返回 null
     */
    override suspend fun fetchCurrentWeek(): Pair<String, Int>? {
        android.util.Log.i("CHD_CurrentWeek", "========== [Repository] fetchCurrentWeek 开始 ==========")
        return try {
            android.util.Log.i("CHD_CurrentWeek", "[Step1] 调用 eamsApi.getHomePageHtml()...")
            val html = eamsApi.getHomePageHtml().getOrNull()
            if (html == null) {
                android.util.Log.e("CHD_CurrentWeek", "[Step1.1] 获取失败: eamsApi.getHomePageHtml() 返回 null")
                android.util.Log.i("CHD_CurrentWeek", "========== [Repository] fetchCurrentWeek 失败 ==========")
                return null
            }
            android.util.Log.i("CHD_CurrentWeek", "[Step1.1] 获取成功，HTML 长度: ${html.length}")

            android.util.Log.i("CHD_CurrentWeek", "[Step2] 调用 htmlParser.parseCurrentWeek()...")
            val result = htmlParser.parseCurrentWeek(html)
            if (result != null) {
                android.util.Log.i("CHD_CurrentWeek", "[Step2.1] 解析成功: 学期=${result.first}, 周次=${result.second}")
            } else {
                android.util.Log.w("CHD_CurrentWeek", "[Step2.1] 解析失败: parseCurrentWeek 返回 null")
            }
            android.util.Log.i("CHD_CurrentWeek", "========== [Repository] fetchCurrentWeek 结束 ==========")
            result
        } catch (e: Exception) {
            android.util.Log.e("CHD_CurrentWeek", "[Exception] fetchCurrentWeek 异常: ${e.message}", e)
            android.util.Log.i("CHD_CurrentWeek", "========== [Repository] fetchCurrentWeek 异常结束 ==========")
            null
        }
    }

    /**
     * [v73] 从首页 HTML 解析当前教学周（直接解析，不发起网络请求）
     * 用于 WebView 场景，从 JS 渲染后的首页 HTML 解析教学周信息
     */
    override fun parseCurrentWeekFromHtml(html: String): Pair<String, Int>? {
        android.util.Log.i("CHD_CurrentWeek", "========== [Repository] parseCurrentWeekFromHtml 开始 ==========")
        android.util.Log.i("CHD_CurrentWeek", "HTML 长度: ${html.length}")

        val result = htmlParser.parseCurrentWeek(html)

        if (result != null) {
            android.util.Log.i("CHD_CurrentWeek", "解析成功: 学期=${result.first}, 周次=${result.second}")
        } else {
            android.util.Log.w("CHD_CurrentWeek", "解析失败")
        }

        android.util.Log.i("CHD_CurrentWeek", "========== [Repository] parseCurrentWeekFromHtml 结束 ==========")
        return result
    }
}
