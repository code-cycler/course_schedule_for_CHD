package com.example.course_schedule_for_chd_v002.ui.screens.login

/**
 * 登录界面 UI 状态
 * 使用单向数据流管理状态
 *
 * v117: 表单登录死代码清理——用户名/密码/验证字段/学生信息/showWebView 全删（登录只走 WebView），
 *      仅保留同步流程所需状态；currentSemester 默认置空（不再硬编码 "2024-2025-1"）。
 */
data class LoginUiState(
    // 登录状态
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val errorMessage: String? = null,

    // 登录成功后的当前学期（按需展示用）
    val currentSemester: String = ""
)