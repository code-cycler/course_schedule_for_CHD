package com.example.course_schedule_for_chd_v002.ui.screens.schedule

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * @param viewModel 课程表ViewModel
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    semester: String,
    onLogout: () -> Unit,
    viewModel: ScheduleViewModel = koinViewModel { parametersOf(semester) }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
                        Button(onClick = { viewModel.refreshSchedule() }) {
                            Text("Refresh")
                        }
                    }
                }
            }
            // Schedule grid
            else {
                ScheduleGrid(
                    courses = uiState.getDisplayCourses(),
                    conflictingCourseIds = uiState.conflictingCourseIds,
                    onCourseClick = { course -> viewModel.onCourseSelected(course) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
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
