package com.example.course_schedule_for_chd_v002.ui.screens.login

/**
 * 登录界面UI状态
 * 使用单向数据流管理状态
 */
data class LoginUiState(
    // 输入字段
    val username: String = "",
    val password: String = "",

    // 登录状态
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val errorMessage: String? = null,

    // 登录成功后的信息
    val studentName: String? = null,
    val studentId: String? = null,
    val currentSemester: String = "2024-2025-1",

    // 输入验证错误
    val usernameError: String? = null,
    val passwordError: String? = null,

    // WebView 登录状态 - 默认为 true，直接显示 WebView 登录
    val showWebView: Boolean = true
) {
    /**
     * 表单验证是否通过
     */
    val isFormValid: Boolean
        get() = username.isNotBlank() && password.isNotBlank()
}
