package com.example.course_schedule_for_chd_v002.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.course_schedule_for_chd_v002.data.local.preferences.UserPreferences
import com.example.course_schedule_for_chd_v002.domain.repository.ICourseRepository
import com.example.course_schedule_for_chd_v002.util.AppLogger
import com.example.course_schedule_for_chd_v002.util.Constants
import com.example.course_schedule_for_chd_v002.util.TimeUtils
import com.example.course_schedule_for_chd_v002.util.WebViewLogger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 登录界面 ViewModel (v67)
 * 管理登录流程和UI状态
 *
 * v67: WebView 完成整个流程
 *      - CAS 登录
 *      - 自动导航到课表页面
 *      - 提取 HTML 并解析
 *
 * @param repository 课程仓库接口
 * @param userPreferences 用户偏好设置
 */
private const val TAG = "LoginViewModel"

class LoginViewModel(
    private val repository: ICourseRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // [v28] 使用一次性事件进行导航，避免状态标志的问题
    private val _navigateBackEvent = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val navigateBackEvent: SharedFlow<Unit> = _navigateBackEvent.asSharedFlow()

    init {
        AppLogger.d(TAG, "=== LoginViewModel 初始化 ===")
        // [v28] 不再重置 isLoggedIn，因为这会导致状态混乱
        // 使用 navigateBackEvent 替代状态标志进行导航
    }

    /**
     * 重置登录状态
     * [v27] 在导航成功后调用，防止下次进入时状态残留
     */
    fun resetLoginState() {
        _uiState.update { it.copy(isLoggedIn = false, isLoading = false, errorMessage = null) }
    }

    /**
     * 更新用户名
     */
    fun onUsernameChange(value: String) {
        _uiState.update {
            it.copy(
                username = value,
                usernameError = null,
                errorMessage = null
            )
        }
    }

    /**
     * 更新密码
     */
    fun onPasswordChange(value: String) {
        _uiState.update {
            it.copy(
                password = value,
                passwordError = null,
                errorMessage = null
            )
        }
    }

    /**
     * 执行登录
     */
    fun login() {
        // 验证输入
        val currentState = _uiState.value
        var hasError = false

        if (currentState.username.isBlank()) {
            _uiState.update { it.copy(usernameError = "Please enter student ID") }
            hasError = true
        }

        if (currentState.password.isBlank()) {
            _uiState.update { it.copy(passwordError = "Please enter password") }
            hasError = true
        }

        if (hasError) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val result = repository.login(
                username = currentState.username,
                password = currentState.password
            )

            result.fold(
                onSuccess = { loginResult ->
                    if (loginResult.success) {
                        // 登录成功，获取课表
                        val defaultSemester = "2024-2025-1"
                        repository.fetchRemoteSchedule(defaultSemester)

                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isLoggedIn = true,
                                studentName = loginResult.studentName,
                                studentId = loginResult.studentId,
                                currentSemester = defaultSemester
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = loginResult.errorMessage ?: "Login failed"
                            )
                        }
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Login failed"
                        )
                    }
                }
            )
        }
    }

    // ================ WebView 登录相关 ================

    /**
     * [v73] CAS 登录成功后的处理
     * WebView 完成登录、首页（教学周）、课表获取
     *
     * @param courseTableHtml 课表页面的 HTML 内容
     * @param homePageHtml 首页 HTML 内容（包含教学周信息），可能为 null
     */
    fun onCasLoginSuccess(courseTableHtml: String, homePageHtml: String?) {
        AppLogger.i("CHD_CurrentWeek", "========== [LoginViewModel] onCasLoginSuccess 开始 ==========")
        WebViewLogger.logSuccess("CAS", "登录成功，开始解析...")
        WebViewLogger.logDebug(TAG, "课表 HTML 长度: ${courseTableHtml.length}, 首页 HTML: ${homePageHtml?.length ?: "null"}")

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            // [Cookie 同步] WebView 登录成功后，把 Cookie 灌进 OkHttp（CookieManager），
            // 供后续 OkHttp 请求用（获取新学期 dataQuery.action / 抓指定学期课表等）。
            // 2026-07-13 修复：原 onCasLoginSuccess 不同步，OkHttp Cookie 永远空，
            // 导致 getSemesterOptions 返回"登录已过期"。
            val cookieSynced = repository.syncCookiesFromWebView(Constants.EamsUrls.HOME_PAGE, "")
            AppLogger.i("CHD_CurrentWeek", "[Cookie] 同步 WebView→OkHttp: success=$cookieSynced")

            // 步骤1：先从首页 HTML 解析真实学期+教学周（决定课程入哪个学期 key）
            // 2026-07-12 修正：原硬编码 defaultSemester="2024-2025-1" 导致课程存错学期，
            // 与 saveCurrentSemester 保存的真实学期错位 → 启动卡在老学期。
            val currentWeekInfo = homePageHtml?.let { repository.parseCurrentWeekFromHtml(it) }
            val realSemester = currentWeekInfo?.first
            val targetSemester = realSemester ?: "2024-2025-1"  // 回退：首页无教学周信息时
            AppLogger.i("CHD_CurrentWeek", "[Step1] 真实学期=$realSemester, 入库用 targetSemester=$targetSemester")

            if (homePageHtml != null && currentWeekInfo == null) {
                val weekInfoIndex = homePageHtml.indexOf("本周为")
                if (weekInfoIndex >= 0) {
                    val start = maxOf(0, weekInfoIndex - 50)
                    val end = minOf(homePageHtml.length, weekInfoIndex + 200)
                    AppLogger.w("CHD_CurrentWeek", "[Step1] 首页教学周解析失败，'本周为'附近: ${homePageHtml.substring(start, end)}")
                } else {
                    AppLogger.w("CHD_CurrentWeek", "[Step1] 首页 HTML 不含'本周为'关键字，回退 defaultSemester")
                }
            }

            // 步骤2：用真实学期解析+入库课表（替代原硬编码 defaultSemester）
            AppLogger.i("CHD_CurrentWeek", "[Step2] 开始解析课表 HTML，存入学期=$targetSemester")
            val result = repository.parseHtmlToCourses(courseTableHtml, targetSemester)

            result.fold(
                onSuccess = { courses ->
                    WebViewLogger.logSuccess("课表", "解析成功，共 ${courses.size} 门课程")
                    AppLogger.i("CHD_CurrentWeek", "[Step2] 课表解析成功，课程数: ${courses.size}, 学期: $targetSemester")

                    // 步骤3：保存教学周/学期/开始日期（来自 Step1 的 currentWeekInfo）
                    if (currentWeekInfo != null) {
                        val (semester, week) = currentWeekInfo
                        WebViewLogger.logSuccess("教学周", "当前: $semester 第${week}周")

                        val semesterStartDate = TimeUtils.calculateSemesterStartDate(week)
                        AppLogger.i("CHD_Semester", "[新功能] 反推学期开始日期: $semesterStartDate (当前周=$week)")

                        userPreferences.saveCurrentWeek(week)
                        userPreferences.saveCurrentSemester(semester)
                        userPreferences.saveSemesterStartDate(semesterStartDate)
                        userPreferences.saveLastParsedWeek(week)
                        AppLogger.i("CHD_CurrentWeek", "[Step3] 已保存: week=$week, semester=$semester, startDate=$semesterStartDate")
                    } else {
                        // 首页无教学周信息，至少保证 currentSemester 与入库 key 一致
                        userPreferences.saveCurrentSemester(targetSemester)
                        AppLogger.w("CHD_CurrentWeek", "[Step3] 首页无教学周信息，仅保存 currentSemester=$targetSemester")
                    }

                    // 步骤4：发射导航事件 + UI 状态
                    val navResult = _navigateBackEvent.tryEmit(Unit)
                    AppLogger.i("CHD_CurrentWeek", "[Step4] 导航事件已发射: $navResult")

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoggedIn = true,
                            showWebView = false,
                            currentSemester = targetSemester
                        )
                    }
                    AppLogger.i("CHD_CurrentWeek", "========== [LoginViewModel] onCasLoginSuccess 成功结束 ==========")
                },
                onFailure = { error ->
                    AppLogger.e("CHD_CurrentWeek", "[ERROR] 课表解析失败: ${error.message}")
                    WebViewLogger.logError("课表", "解析失败: ${error.message}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to parse course table: ${error.message}"
                        )
                    }
                }
            )
        }
    }

    /**
     * 切换到 WebView 登录界面
     */
    fun switchToWebView() {
        _uiState.update { it.copy(showWebView = true) }
    }

    /**
     * 从 WebView 返回表单登录
     */
    fun switchToForm() {
        _uiState.update { it.copy(showWebView = false) }
    }

    /**
     * WebView 登录成功后的处理
     * 同步 Cookie 并验证登录状态
     */
    fun onWebViewLoginSuccess() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            // 验证 WebView 登录状态（内部会同步 Cookie）
            val isLoggedIn = repository.verifyWebViewLogin()

            if (isLoggedIn) {
                // 获取学生信息
                val studentName = repository.getStudentName()
                val studentId = repository.getStudentId()

                // 获取课表
                val defaultSemester = "2024-2025-1"
                repository.fetchRemoteSchedule(defaultSemester)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        showWebView = false,
                        studentName = studentName,
                        studentId = studentId,
                        currentSemester = defaultSemester
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        showWebView = false,
                        errorMessage = "Login verification failed, please try again"
                    )
                }
            }
        }
    }

    /**
     * 用户点击"获取课表"按钮后的处理
     * v52: 使用 WebViewLogger 统一日志输出
     *
     * @param url 当前页面 URL
     * @param htmlContent WebView 获取的页面 HTML 内容
     */
    fun onFetchCourseTable(url: String, htmlContent: String) {
        WebViewLogger.logParseDetail("=== onFetchCourseTable 开始 ===")
        WebViewLogger.logParseDetail("URL: $url, HTML长度: ${htmlContent.length}")

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            // 检查 URL 是否在课表页面
            val isOnCourseTablePage = url.contains("courseTableForStd")
            WebViewLogger.logParseDetail("URL 检查: isOnCourseTablePage=$isOnCourseTablePage")

            if (!isOnCourseTablePage) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Please navigate to course table page first"
                    )
                }
                return@launch
            }

            // 直接解析 WebView 获取的 HTML
            if (htmlContent.isNotEmpty()) {
                WebViewLogger.logParseDetail("[步骤1] 解析 HTML 内容...")

                val defaultSemester = "2024-2025-1"
                val result = repository.parseHtmlToCourses(htmlContent, defaultSemester)

                result.fold(
                    onSuccess = { courses ->
                        WebViewLogger.logParseDetail("[OK] 解析成功，共 ${courses.size} 门课程")

                        // [v28] 发射一次性导航事件
                        val navResult = _navigateBackEvent.tryEmit(Unit)
                        WebViewLogger.logNavigationEventEmit(navResult)

                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                showWebView = false,
                                currentSemester = defaultSemester
                            )
                        }
                    },
                    onFailure = { error ->
                        WebViewLogger.logParseDetail("[FAIL] 解析失败: ${error.message}")
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Parse failed: ${error.message}"
                            )
                        }
                    }
                )
            } else {
                WebViewLogger.logParseDetail("[FAIL] HTML 内容为空")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to get page content"
                    )
                }
            }
        }
    }

    /**
     * 获取课表并导航到课程表视图
     */
    private suspend fun fetchCourseTableAndNavigate() {
        AppLogger.d(TAG, "fetchCourseTableAndNavigate: 开始")

        // 获取学生信息
        val studentName = repository.getStudentName()
        val studentId = repository.getStudentId()
        AppLogger.d(TAG, "学生信息: name=$studentName, id=$studentId")

        // 获取课表
        val defaultSemester = "2024-2025-1"
        AppLogger.d(TAG, "调用 fetchRemoteSchedule...")
        val fetchResult = repository.fetchRemoteSchedule(defaultSemester)

        fetchResult.fold(
            onSuccess = { courses ->
                AppLogger.i(TAG, "[OK] fetchRemoteSchedule 成功，共 ${courses.size} 门课程")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        showWebView = false,
                        studentName = studentName,
                        studentId = studentId,
                        currentSemester = defaultSemester
                    )
                }
                AppLogger.i(TAG, "[STATE] 状态已更新，isLoggedIn=true，等待 LaunchedEffect 触发导航")
            },
            onFailure = { error ->
                AppLogger.e(TAG, "[FAIL] fetchRemoteSchedule 失败: ${error.message}")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to fetch course table: ${error.message}"
                    )
                }
            }
        )
    }
}
