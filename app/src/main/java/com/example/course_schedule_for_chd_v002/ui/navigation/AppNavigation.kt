package com.example.course_schedule_for_chd_v002.ui.navigation

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.course_schedule_for_chd_v002.CourseApplication
import com.example.course_schedule_for_chd_v002.data.local.preferences.UserPreferences
import com.example.course_schedule_for_chd_v002.domain.repository.ICourseRepository
import com.example.course_schedule_for_chd_v002.ui.components.CrashReportDialog
import com.example.course_schedule_for_chd_v002.ui.screens.checkinassist.CheckInAssistScreen
import com.example.course_schedule_for_chd_v002.util.LogExporter
import com.example.course_schedule_for_chd_v002.util.LogExporter.CrashLogSummary
import com.example.course_schedule_for_chd_v002.ui.screens.login.LoginViewModel
import com.example.course_schedule_for_chd_v002.ui.screens.login.WebViewScreen
import com.example.course_schedule_for_chd_v002.ui.screens.schedule.ScheduleScreen
import com.example.course_schedule_for_chd_v002.util.AppLogger
import com.example.course_schedule_for_chd_v002.util.Constants
import com.example.course_schedule_for_chd_v002.util.CrashHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.io.File

/**
 * 应用主导航配置
 */
private const val TAG = "AppNavigation"

@Composable
fun AppNavigation(
    navController: NavHostController
) {
    val repository: ICourseRepository = koinInject()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 崩溃报告弹窗状态
    var showCrashDialog by remember { mutableStateOf(false) }
    var crashSummary by remember { mutableStateOf<CrashLogSummary?>(null) }
    var crashExporting by remember { mutableStateOf(false) }
    var crashExportResult by remember { mutableStateOf<LogExporter.ExportResult?>(null) }

    // [Bug2 修复 2026-07-13] startDestination 固定无参 schedule_root（不再异步算带参路径）。
    // 原 startDestination=schedule/{semester} 首次组合时路径参数 {semester} 绑不上，fallback 到硬编码
    // 2024-2025-1，启动进错学期、本地课程查空（看似"数据丢失"）。读真实学期移到 schedule_root composable。
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            // 崩溃检测
            if (CourseApplication.wasLastSessionCrash()) {
                AppLogger.w(TAG, "[NAV] 检测到上次非正常退出，准备显示崩溃报告")
                crashSummary = LogExporter.getCrashLogSummary(context)
                showCrashDialog = true
            }
        }
    }

    AppLogger.i(TAG, "[NAV] NavHost 初始化，startDestination=${Screen.ScheduleRoot.route}")

    // 崩溃报告弹窗
    if (showCrashDialog) {
        CrashReportDialog(
            crashSummary = crashSummary,
            isExporting = crashExporting,
            result = crashExportResult,
            onExport = {
                scope.launch(Dispatchers.IO) {
                    crashExporting = true
                    crashExportResult = LogExporter.exportCrashLogs(context)
                    crashExporting = false
                }
            },
            onShare = { file ->
                val shareIntent = LogExporter.shareLogFile(context, file)
                context.startActivity(Intent.createChooser(shareIntent, "分享崩溃日志"))
            },
            onDismiss = {
                showCrashDialog = false
                CrashHandler.deleteCrashStackTrace(context)
                AppLogger.i(TAG, "[NAV] 崩溃报告已关闭")
            }
        )
    }

    NavHost(
        navController = navController,
        startDestination = Screen.ScheduleRoot.route
    ) {
        // [Bug2 修复] 启动跳板：读 DataStore 真实学期后 navigate，规避 startDestination 带路径参数绑不上
        composable(Screen.ScheduleRoot.route) {
            LaunchedEffect(Unit) {
                val semester = withContext(Dispatchers.IO) {
                    // [v113] 冷启动把 WebView cookie 灌进 OkHttp（cookieStore 纯内存重启即丢，
                    // 需在 OkHttp 请求前从持久化的 WebView cookie 同步）
                    runCatching { repository.syncCookiesFromWebView(Constants.EamsUrls.HOME_PAGE, "") }
                    repository.getCurrentSemester() ?: "2024-2025-1"
                }
                AppLogger.i(TAG, "[NAV] ScheduleRoot 读到真实学期=$semester，跳转 schedule/$semester")
                navController.navigate(Screen.Schedule.createRoute(semester)) {
                    popUpTo(Screen.ScheduleRoot.route) { inclusive = true }
                }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }

        // [v114] 签到辅助界面
        composable(Screen.CheckInAssist.route) {
            CheckInAssistScreen(onBack = { navController.popBackStack() })
        }

        // 登录界面
        composable(Screen.Login.route) {
            AppLogger.d(TAG, "=== 进入 Login 屏幕 ===")
            val viewModel: LoginViewModel = koinViewModel()

            LaunchedEffect(Unit) {
                AppLogger.d(TAG, "[v29] 开始监听导航事件")
                viewModel.navigateBackEvent.collect {
                    // 2026-07-13 修正：原硬编码 "2024-2025-1"，同步成功后导航到错学期，
                    // 用户看不到刚同步的课。同步时 saveCurrentSemester 已写真实学期，读偏好拿。
                    val target = repository.getCurrentSemester() ?: "2024-2025-1"
                    AppLogger.i(TAG, "[NAV] [v29] >>> 收到导航事件，目标学期=$target")
                    navController.navigate(Screen.Schedule.createRoute(target)) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                    AppLogger.i(TAG, "[NAV] [v29] >>> 导航到 Schedule 完成")
                }
            }

            // 直接显示 WebView 登录（v47 起改用系统 WebView）
            // [v61] 使用新的回调：CAS 登录成功后同步 Cookie，然后用 OkHttp 获取课表
            WebViewScreen(
                onLoginSuccess = viewModel::onCasLoginSuccess
            )
        }

        // 课程表界面
        composable(
            route = Screen.Schedule.route,
            arguments = listOf(
                navArgument(Screen.Schedule.SEMESTER_ARG) {
                    type = NavType.StringType
                    // 不设 defaultValue：startDestination 路径与所有 navigate 都显式带 semester，
                    // 避免 defaultValue 覆盖路径参数导致启动进错学期（2026-07-13 日志定位）。
                }
            )
        ) { backStackEntry ->
            val semester = backStackEntry.arguments?.getString(Screen.Schedule.SEMESTER_ARG)
                ?: "2024-2025-1"
            AppLogger.i(TAG, "=== 进入 Schedule 屏幕, semester=$semester ===")
            ScheduleScreen(
                semester = semester,
                onNavigateToSemester = { newSemester ->
                    navController.navigate(Screen.Schedule.createRoute(newSemester)) {
                        // [Bug 修复 2026-07-14] 去掉 launchSingleTop + popUpTo 弹出旧 schedule entry：
                        // launchSingleTop 会复用栈顶 backStackEntry，ViewModel scoped to entry 也复用，
                        // semester 构造参数不更新 → 切换学期界面不刷新（课程仍是旧学期的，要退出重进才生效）。
                        // popUpTo inclusive=true 弹出旧 entry，navigate 创建新 entry + 新 ViewModel 重新 loadSchedule。
                        popUpTo(Screen.Schedule.route) { inclusive = true }
                    }
                },
                onLogout = {
                    AppLogger.i(TAG, "[NAV] 登出，返回 Login")
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    val currentRoute = navController.currentDestination?.route
                    if (currentRoute != Screen.Login.route) {
                        AppLogger.i(TAG, "[NAV] Schedule -> Login (同步数据)")
                        navController.navigate(Screen.Login.route) {
                            launchSingleTop = true
                        }
                    } else {
                        AppLogger.w(TAG, "[NAV] 已在 Login 页面，跳过导航")
                    }
                },
                onNavigateToCheckInAssist = {
                    navController.navigate(Screen.CheckInAssist.route)
                }
            )
        }
    }
}
