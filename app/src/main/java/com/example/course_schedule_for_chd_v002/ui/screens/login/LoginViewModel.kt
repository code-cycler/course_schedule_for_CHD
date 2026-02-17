package com.example.course_schedule_for_chd_v002.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.course_schedule_for_chd_v002.domain.repository.ICourseRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 登录界面 ViewModel
 * 管理登录流程和UI状态
 *
 * @param repository 课程仓库接口
 */
private const val TAG = "LoginViewModel"

class LoginViewModel(
    private val repository: ICourseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // [v28] 使用一次性事件进行导航，避免状态标志的问题
    private val _navigateBackEvent = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val navigateBackEvent: SharedFlow<Unit> = _navigateBackEvent.asSharedFlow()

    init {
        android.util.Log.d(TAG, "=== LoginViewModel 初始化 ===")
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
     * v23: 从 GeckoView 直接获取 HTML 内容并解析
     *
     * @param url 当前页面 URL
     * @param htmlContent GeckoView 获取的页面 HTML 内容
     */
    fun onFetchCourseTable(url: String, htmlContent: String) {
        android.util.Log.i(TAG, "=== onFetchCourseTable 开始 (v23) ===")
        android.util.Log.d(TAG, "参数: url=$url, htmlContent长度=${htmlContent.length}")

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            // 检查 URL 是否在课表页面
            val isOnCourseTablePage = url.contains("courseTableForStd")
            android.util.Log.d(TAG, "URL 检查: isOnCourseTablePage=$isOnCourseTablePage")

            if (!isOnCourseTablePage) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Please navigate to course table page first"
                    )
                }
                return@launch
            }

            // 直接解析 GeckoView 获取的 HTML
            if (htmlContent.isNotEmpty()) {
                android.util.Log.d(TAG, "[步骤1] 解析 HTML 内容...")

                val defaultSemester = "2024-2025-1"
                val result = repository.parseHtmlToCourses(htmlContent, defaultSemester)

                result.fold(
                    onSuccess = { courses ->
                        android.util.Log.i(TAG, "[OK] 解析成功，共 ${courses.size} 门课程")

                        // [v28] 发射一次性导航事件
                        android.util.Log.i(TAG, "[v28] 发射导航事件")
                        _navigateBackEvent.tryEmit(Unit)

                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                showWebView = false,
                                currentSemester = defaultSemester
                            )
                        }
                    },
                    onFailure = { error ->
                        android.util.Log.e(TAG, "[FAIL] 解析失败: ${error.message}")
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Parse failed: ${error.message}"
                            )
                        }
                    }
                )
            } else {
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
        android.util.Log.d(TAG, "fetchCourseTableAndNavigate: 开始")

        // 获取学生信息
        val studentName = repository.getStudentName()
        val studentId = repository.getStudentId()
        android.util.Log.d(TAG, "学生信息: name=$studentName, id=$studentId")

        // 获取课表
        val defaultSemester = "2024-2025-1"
        android.util.Log.d(TAG, "调用 fetchRemoteSchedule...")
        val fetchResult = repository.fetchRemoteSchedule(defaultSemester)

        fetchResult.fold(
            onSuccess = { courses ->
                android.util.Log.i(TAG, "[OK] fetchRemoteSchedule 成功，共 ${courses.size} 门课程")
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
                android.util.Log.i(TAG, "[STATE] 状态已更新，isLoggedIn=true，等待 LaunchedEffect 触发导航")
            },
            onFailure = { error ->
                android.util.Log.e(TAG, "[FAIL] fetchRemoteSchedule 失败: ${error.message}")
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
