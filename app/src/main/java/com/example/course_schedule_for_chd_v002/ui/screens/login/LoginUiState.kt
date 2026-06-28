package com.example.course_schedule_for_chd_v002.ui.screens.login

/**
 * 登录界面 UI 状态
 *
 * 仅服务 WebView 登录流程（表单登录相关字段已随 LoginScreen 移除）。
 */
data class LoginUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val errorMessage: String? = null,
    val currentSemester: String = "2024-2025-1",
    val showWebView: Boolean = true
)
