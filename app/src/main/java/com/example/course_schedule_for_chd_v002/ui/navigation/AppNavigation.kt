package com.example.course_schedule_for_chd_v002.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.course_schedule_for_chd_v002.domain.repository.ICourseRepository
import com.example.course_schedule_for_chd_v002.ui.screens.login.LoginViewModel
import com.example.course_schedule_for_chd_v002.ui.screens.login.GeckoViewScreen
import com.example.course_schedule_for_chd_v002.ui.screens.schedule.ScheduleScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * 应用主导航配置
 * 使用 Navigation Compose 管理屏幕导航
 *
 * 启动时直接进入课程表界面
 * 点击"同步数据"按钮可跳转到登录页获取课程数据
 *
 * @param navController 导航控制器
 */
private const val TAG = "AppNavigation"

@Composable
fun AppNavigation(
    navController: NavHostController
) {
    val repository: ICourseRepository = koinInject()

    // 始终以课程表视图为起始目的地
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        android.util.Log.d(TAG, "=== 初始化起始目的地 ===")
        withContext(Dispatchers.IO) {
            // 获取当前学期（始终以课程表视图开始）
            val semester = repository.getCurrentSemester() ?: "2024-2025-1"
            val route = Screen.Schedule.createRoute(semester)
            android.util.Log.i(TAG, "[NAV] 起始目的地 -> Schedule: $route")
            startDestination = route
        }
    }

    // 加载中状态
    if (startDestination == null) {
        android.util.Log.d(TAG, "正在加载，显示进度条...")
        Box(modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        return
    }

    android.util.Log.i(TAG, "[NAV] NavHost 初始化，startDestination=${startDestination}")

    NavHost(
        navController = navController,
        startDestination = startDestination!!
    ) {
        // 登录界面 - 只显示 WebView
        composable(Screen.Login.route) {
            android.util.Log.d(TAG, "=== 进入 Login 屏幕 ===")
            val viewModel: LoginViewModel = koinViewModel()

            // [v29] 使用一次性事件进行导航
            // 使用 navigate 而不是 popBackStack，因为 Schedule 可能不在栈中（例如登出后）
            LaunchedEffect(Unit) {
                android.util.Log.d(TAG, "[v29] 开始监听导航事件")
                viewModel.navigateBackEvent.collect {
                    android.util.Log.i(TAG, "[NAV] [v29] >>> 收到导航事件，执行导航")

                    // 尝试 popBackStack，如果失败则 navigate 到 Schedule
                    val currentRoute = navController.currentDestination?.route
                    android.util.Log.d(TAG, "[v29] 当前路由: $currentRoute")

                    // 直接导航到 Schedule，清除 Login 及其上面的所有屏幕
                    navController.navigate(Screen.Schedule.createRoute("2024-2025-1")) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                    android.util.Log.i(TAG, "[NAV] [v29] >>> 导航到 Schedule 完成")
                }
            }

            // 直接显示 WebView 登录
            GeckoViewScreen(
                onFetchCourseTable = viewModel::onFetchCourseTable
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
            android.util.Log.i(TAG, "=== 进入 Schedule 屏幕, semester=$semester ===")
            ScheduleScreen(
                semester = semester,
                onLogout = {
                    android.util.Log.i(TAG, "[NAV] 登出，返回 Login")
                    // 登出后返回登录界面，清除所有返回栈
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    // 导航到登录页（不清除返回栈，允许返回）
                    android.util.Log.i(TAG, "[NAV] Schedule -> Login (同步数据)")
                    navController.navigate(Screen.Login.route)
                }
            )
        }
    }
}
