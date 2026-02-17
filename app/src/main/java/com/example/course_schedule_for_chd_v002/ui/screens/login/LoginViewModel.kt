package com.example.course_schedule_for_chd_v002.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.course_schedule_for_chd_v002.domain.repository.ICourseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 登录界面 ViewModel
 * 管理登录流程和UI状态
 *
 * @param repository 课程仓库接口
 */
class LoginViewModel(
    private val repository: ICourseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

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
     * 新流程：用户已在课表页面，同步 Cookie 并爬取课表数据
     */
    fun onFetchCourseTable() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            // 验证 WebView 登录状态（内部会同步 Cookie）
            val isLoggedIn = repository.verifyWebViewLogin()

            if (isLoggedIn) {
                // 获取学生信息
                val studentName = repository.getStudentName()
                val studentId = repository.getStudentId()

                // 获取课表 - 用户已在课表页面，直接爬取
                val defaultSemester = "2024-2025-1"
                val fetchResult = repository.fetchRemoteSchedule(defaultSemester)

                fetchResult.fold(
                    onSuccess = {
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
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Failed to fetch course table: ${error.message}"
                            )
                        }
                    }
                )
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Login verification failed, please login first"
                    )
                }
            }
        }
    }
}
