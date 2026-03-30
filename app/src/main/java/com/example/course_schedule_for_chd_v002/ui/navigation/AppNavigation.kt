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
import com.example.course_schedule_for_chd_v002.util.LogExporter
import com.example.course_schedule_for_chd_v002.util.LogExporter.CrashLogSummary
import com.example.course_schedule_for_chd_v002.ui.screens.login.LoginViewModel
import com.example.course_schedule_for_chd_v002.ui.screens.login.WebViewScreen
import com.example.course_schedule_for_chd_v002.ui.screens.schedule.ScheduleScreen
import com.example.course_schedule_for_chd_v002.util.AppLogger
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

    // 始终以课程表视图为起始目的地
    var startDestination by remember { mutableStateOf<String?>(null) }

    // 崩溃报告弹窗状态
    var showCrashDialog by remember { mutableStateOf(false) }
    var crashSummary by remember { mutableStateOf<CrashLogSummary?>(null) }
    var crashExporting by remember { mutableStateOf(false) }
    var crashExportResult by remember { mutableStateOf<LogExporter.ExportResult?>(null) }

    LaunchedEffect(Unit) {
        AppLogger.d(TAG, "=== 初始化起始目的地 ===")
        withContext(Dispatchers.IO) {
            // 崩溃检测
            val wasCrash = CourseApplication.wasLastSessionCrash()
            if (wasCrash) {
                AppLogger.w(TAG, "[NAV] 检测到上次非正常退出，准备显示崩溃报告")
                crashSummary = LogExporter.getCrashLogSummary(context)
                showCrashDialog = true
            }

            // 直接进入课程表
            val semester = repository.getCurrentSemester() ?: "2024-2025-1"
            val route = Screen.Schedule.createRoute(semester)
            AppLogger.i(TAG, "[NAV] 起始目的地 -> Schedule: $route")
            startDestination = route
        }
    }

    // 加载中状态
    if (startDestination == null) {
        AppLogger.d(TAG, "正在加载，显示进度条...")
        Box(modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        return
    }

    AppLogger.i(TAG, "[NAV] NavHost 初始化，startDestination=${startDestination}")

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
        startDestination = startDestination!!
    ) {
        // 登录界面
        composable(Screen.Login.route) {
            AppLogger.d(TAG, "=== 进入 Login 屏幕 ===")
            val viewModel: LoginViewModel = koinViewModel()

            LaunchedEffect(Unit) {
                AppLogger.d(TAG, "[v29] 开始监听导航事件")
                viewModel.navigateBackEvent.collect {
                    AppLogger.i(TAG, "[NAV] [v29] >>> 收到导航事件，执行导航")
                    navController.navigate(Screen.Schedule.createRoute("2024-2025-1")) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                    AppLogger.i(TAG, "[NAV] [v29] >>> 导航到 Schedule 完成")
                }
            }

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
                    defaultValue = "2024-2025-1"
                }
            )
        ) { backStackEntry ->
            val semester = backStackEntry.arguments?.getString(Screen.Schedule.SEMESTER_ARG)
                ?: "2024-2025-1"
            AppLogger.i(TAG, "=== 进入 Schedule 屏幕, semester=$semester ===")
            ScheduleScreen(
                semester = semester,
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
                }
            )
        }
    }
}
