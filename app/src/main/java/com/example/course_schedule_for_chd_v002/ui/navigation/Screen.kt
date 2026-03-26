package com.example.course_schedule_for_chd_v002.ui.navigation

/**
 * 导航目的地定义
 * 定义应用的各个屏幕路由
 */
sealed class Screen(val route: String) {
    /**
     * 登录界面 - 起始屏幕
     */
    object Login : Screen("login")

    /**
     * [权限管理] 权限请求引导页 - 首次启动时显示
     */
    object PermissionRequest : Screen("permission_request")

    /**
     * 课程表界面 - 登录成功后跳转
     * @param semester 学期参数，格式如 "2024-2025-1"
     */
    object Schedule : Screen("schedule/{semester}") {
        const val SEMESTER_ARG = "semester"

        /**
         * 创建带参数的路由
         */
        fun createRoute(semester: String): String = "schedule/$semester"
    }
}
