package com.example.course_schedule_for_chd_v002.ui.screens.schedule

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableFloatStateOf
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
                    // [v41] 同步/导入按钮（带文字）
                    TextButton(onClick = onNavigateToLogin) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sync")
                    }

                    // 登出按钮
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
                // [v37] 删除 Sync Data 按钮，只显示提示信息
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
                            text = "Please login to fetch course data",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                    // [v37] 使用更安全的状态初始化，防止启动崩溃
                    var previousWeek by remember { mutableIntStateOf(1) }
                    var hasInitialized by remember { mutableStateOf(false) }

                    // [v37] 在 uiState 更新后初始化 previousWeek
                    LaunchedEffect(uiState.currentWeek) {
                        if (!hasInitialized && uiState.currentWeek > 0) {
                            previousWeek = uiState.currentWeek
                            hasInitialized = true
                        }
                    }

                    // [v37] 添加滑动防抖状态
                    var totalDrag by remember { mutableFloatStateOf(0f) }

                    // [v36] 使用 AnimatedContent 添加切换动画
                    AnimatedContent(
                        targetState = uiState.currentWeek to displayCourses,
                        transitionSpec = {
                            // [v37] 添加安全检查，避免未初始化时崩溃
                            val isGoingForward = if (hasInitialized) {
                                targetState.first > previousWeek
                            } else {
                                true
                            }

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
                            if (hasInitialized) {
                                previousWeek = currentWeek
                            }
                        }

                        // [v37] 添加水平滑动手势检测，使用防抖机制确保一次只切换一周
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp)
                                .pointerInput(uiState.maxWeeks, uiState.currentWeek) {
                                    detectHorizontalDragGestures(
                                        onDragStart = {
                                            // [v37] 手势开始时重置累计滑动距离
                                            totalDrag = 0f
                                        },
                                        onDragEnd = {
                                            // [v37] 手势结束时检查总滑动距离，确保一次只切换一周
                                            val threshold = 100f  // 总滑动阈值（像素）
                                            when {
                                                totalDrag < -threshold -> {
                                                    // 左滑 -> 下一周
                                                    if (uiState.currentWeek < uiState.maxWeeks) {
                                                        viewModel.onWeekSelected(uiState.currentWeek + 1)
                                                    }
                                                }
                                                totalDrag > threshold -> {
                                                    // 右滑 -> 上一周
                                                    if (uiState.currentWeek > 1) {
                                                        viewModel.onWeekSelected(uiState.currentWeek - 1)
                                                    }
                                                }
                                            }
                                            totalDrag = 0f
                                        }
                                    ) { change, dragAmount ->
                                        change.consume()
                                        // [v37] 累计滑动距离，不在此处切换周次
                                        totalDrag += dragAmount
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
