package com.example.course_schedule_for_chd_v002.ui.navigation

/**
 * 导航目的地定义
 * 定义应用的各个屏幕路由
 */
sealed class Screen(val route: String) {
    /**
     * 登录界面
     */
    object Login : Screen("login")

    /**
     * 课程表界面
     * @param semester 学期参数，格式如 "2024-2025-1"
     */
    object Schedule : Screen("schedule/{semester}") {
        const val SEMESTER_ARG = "semester"

        /**
         * 创建带参数的路由
         */
        fun createRoute(semester: String): String = "schedule/$semester"
    }

    /**
     * [Bug2 修复 2026-07-13] 启动无参跳板路由。
     *
     * NavHost startDestination 带路径参数（schedule/{semester}）首次组合时 {semester} 绑不上
     * （getString 返回 null → fallback 到硬编码错学期 2024-2025-1），导致启动进错学期、本地课程查空
     * （看似"数据丢失"，实际数据在 Room 里好好的）。改用无参 schedule_root 作 startDestination，
     * 读 DataStore 真实学期后 navigate 到 schedule/{真实}，规避该坑。
     *
     * 正常 navigate（同步成功、切换学期）时 schedule/{semester} 参数绑定正常，不受影响。
     */
    object ScheduleRoot : Screen("schedule_root")

    /**
     * [v114] 签到辅助界面
     */
    object CheckInAssist : Screen("checkin_assist")
}
