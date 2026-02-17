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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
 * 启动时检查登录状态：
 * - 已登录：直接进入课程表界面
 * - 未登录：显示 WebView 登录界面
 *
 * @param navController 导航控制器
 */
@Composable
fun AppNavigation(
    navController: NavHostController
) {
    val repository: ICourseRepository = koinInject()

    // 检查登录状态，决定起始目的地
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val isLoggedIn = repository.isLoggedIn()
            startDestination = if (isLoggedIn) {
                // 获取当前学期
                val semester = repository.getCurrentSemester() ?: "2024-2025-1"
                Screen.Schedule.createRoute(semester)
            } else {
                Screen.Login.route
            }
        }
    }

    // 加载中状态
    if (startDestination == null) {
        Box(modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        return
    }

    NavHost(
        navController = navController,
        startDestination = startDestination!!
    ) {
        // 登录界面 - 只显示 WebView
        composable(Screen.Login.route) {
            val viewModel: LoginViewModel = koinViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            // 登录成功后导航
            LaunchedEffect(uiState.isLoggedIn) {
                if (uiState.isLoggedIn) {
                    navController.navigate(Screen.Schedule.createRoute(uiState.currentSemester)) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
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
            ScheduleScreen(
                semester = semester,
                onLogout = {
                    // 登出后返回登录界面，清除所有返回栈
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
