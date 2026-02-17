package com.example.course_schedule_for_chd_v002.ui.screens.schedule

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.course_schedule_for_chd_v002.domain.model.Course
import com.example.course_schedule_for_chd_v002.ui.components.ScheduleGrid
import com.example.course_schedule_for_chd_v002.ui.components.WeekSelector
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 课程表界面
 *
 * @param semester 学期
 * @param onLogout 登出回调
 * @param onNavigateToLogin 导航到登录页回调（用于同步数据）
 * @param viewModel 课程表ViewModel
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    semester: String,
    onLogout: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: ScheduleViewModel = koinViewModel { parametersOf(semester) }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // [v24] 每次进入屏幕时重新加载数据
    LaunchedEffect(Unit) {
        android.util.Log.d("ScheduleScreen", "[v24] LaunchedEffect 触发，调用 reload()")
        viewModel.reload()
    }

    // 登出后导航
    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) {
            onLogout()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Course Schedule") },
                actions = {
                    // Sync button (navigate to login to fetch new data)
                    IconButton(onClick = onNavigateToLogin) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Sync Data"
                        )
                    }
                    // Refresh button
                    IconButton(
                        onClick = { viewModel.refreshSchedule() },
                        enabled = !uiState.isRefreshing
                    ) {
                        if (uiState.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh"
                            )
                        }
                    }
                    // Logout button
                    IconButton(onClick = { viewModel.logout() }) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Logout"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Week selector
            WeekSelector(
                currentWeek = uiState.currentWeek,
                maxWeeks = uiState.maxWeeks,
                onWeekSelected = viewModel::onWeekSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .wrapContentWidth(Alignment.CenterHorizontally)
            )

            // Loading state
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            // Empty state
            else if (uiState.courses.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No courses found",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Click 'Sync' to fetch course data",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = onNavigateToLogin) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sync Data")
                        }
                    }
                }
            }
            // Schedule grid
            else {
                // [v35] 获取当前周的课程
                val displayCourses = uiState.getDisplayCourses()

                // [v35] 如果当前周没有课程，显示提示
                if (displayCourses.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "No courses in week ${uiState.currentWeek}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Try switching to another week",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // [v36] 记录上一次的周次，用于判断滑动方向
                    var previousWeek by remember { mutableIntStateOf(uiState.currentWeek) }

                    // [v36] 使用 AnimatedContent 添加切换动画
                    AnimatedContent(
                        targetState = uiState.currentWeek to displayCourses,
                        transitionSpec = {
                            // 根据周次变化方向确定滑动方向
                            val isGoingForward = targetState.first > previousWeek

                            (slideInHorizontally { width ->
                                if (isGoingForward) width else -width
                            } togetherWith slideOutHorizontally { width ->
                                if (isGoingForward) -width else width
                            }).using(SizeTransform(clip = false))
                        },
                        label = "WeekAnimation"
                    ) { (currentWeek, courses) ->
                        // [v36] 更新 previousWeek 用于下次动画方向判断
                        SideEffect {
                            previousWeek = currentWeek
                        }

                        // [v35] 添加水平滑动手势检测，左右滑动切换周次
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp)
                                .pointerInput(uiState.maxWeeks) {
                                    detectHorizontalDragGestures { change, dragAmount ->
                                        change.consume()
                                        // 左滑 (dragAmount < 0) = 下一周
                                        // 右滑 (dragAmount > 0) = 上一周
                                        val threshold = 50  // 滑动阈值（像素）
                                        when {
                                            dragAmount < -threshold -> {
                                                // 左滑 -> 下一周
                                                if (uiState.currentWeek < uiState.maxWeeks) {
                                                    viewModel.onWeekSelected(uiState.currentWeek + 1)
                                                }
                                            }
                                            dragAmount > threshold -> {
                                                // 右滑 -> 上一周
                                                if (uiState.currentWeek > 1) {
                                                    viewModel.onWeekSelected(uiState.currentWeek - 1)
                                                }
                                            }
                                        }
                                    }
                                }
                        ) {
                            ScheduleGrid(
                                courses = courses,
                                conflictingCourseIds = uiState.conflictingCourseIds,
                                onCourseClick = { course -> viewModel.onCourseSelected(course) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            // Error message
            uiState.errorMessage?.let { error ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.dismissError() }) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    Text(error)
                }
            }
        }
    }

    // Course detail dialog
    uiState.selectedCourse?.let { course ->
        CourseDetailDialog(
            course = course,
            onDismiss = { viewModel.onCourseSelected(null) }
        )
    }
}

/**
 * 课程详情弹窗
 *
 * @param course 课程数据
 * @param onDismiss 关闭回调
 */
@Composable
private fun CourseDetailDialog(
    course: Course,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(course.name) },
        text = {
            Column {
                if (course.teacher.isNotBlank()) {
                    Text("Teacher: ${course.teacher}")
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (course.location.isNotBlank()) {
                    Text("Location: ${course.location}")
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text("Weeks: ${course.startWeek} - ${course.endWeek}")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Time: Node ${course.startNode} - ${course.endNode}")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Credit: ${course.credit}")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Type: ${course.courseType.displayName}")
                if (course.remark.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Remark: ${course.remark}")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}
