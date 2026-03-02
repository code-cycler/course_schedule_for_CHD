package com.example.course_schedule_for_chd_v002.ui.screens.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.course_schedule_for_chd_v002.domain.model.Campus
import com.example.course_schedule_for_chd_v002.domain.model.Course
import com.example.course_schedule_for_chd_v002.domain.model.DayOfWeek
import com.example.course_schedule_for_chd_v002.ui.components.ScheduleGrid
import com.example.course_schedule_for_chd_v002.ui.components.WeekSelector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

    // [v42] 导航防抖状态
    var isNavigating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Course Schedule") },
                actions = {
                    // [v42] 同步按钮（带文字），添加防抖
                    TextButton(
                        onClick = {
                            if (!isNavigating) {
                                isNavigating = true
                                onNavigateToLogin()
                                // 延迟重置防抖状态
                                scope.launch(Dispatchers.Main) {
                                    delay(500)
                                    isNavigating = false
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sync")
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
            // [v44] 周末折叠状态 - 在 ScheduleScreen 管理
            var isWeekendExpanded by remember { mutableStateOf(false) }

            // [v61] 校区切换状态
            var showCampusDialog by remember { mutableStateOf(false) }

            // [v46] 获取当前周的课程，并分别检测周六和周日是否有课
            val displayCourses = uiState.getDisplayCourses()
            val hasSaturdayCourses = displayCourses.any { it.dayOfWeek == DayOfWeek.SATURDAY }
            val hasSundayCourses = displayCourses.any { it.dayOfWeek == DayOfWeek.SUNDAY }
            val hasWeekendCourses = hasSaturdayCourses || hasSundayCourses

            // [v47] 当周次变化时，根据当前周是否有周末课程自动切换折叠状态
            LaunchedEffect(uiState.currentWeek, hasWeekendCourses) {
                isWeekendExpanded = hasWeekendCourses
            }

            // [v44] Week selector row - 添加周末折叠按钮和校区切换
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // [v61] 左侧：校区切换按钮
                FilterChip(
                    selected = false,
                    onClick = { showCampusDialog = true },
                    label = {
                        Text(
                            text = uiState.campus.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                )

                // 中间：周数选择器
                WeekSelector(
                    currentWeek = uiState.currentWeek,
                    maxWeeks = uiState.maxWeeks,
                    onWeekSelected = viewModel::onWeekSelected,
                    modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally)
                )

                // [v45] 右侧：周末折叠按钮 - 带周六/周日指示器
                FilterChip(
                    selected = isWeekendExpanded,
                    onClick = { isWeekendExpanded = !isWeekendExpanded },
                    modifier = Modifier.width(110.dp),
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Weekend",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                            // [v45] 周六指示器
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = if (hasSaturdayCourses)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.outlineVariant,
                                        shape = CircleShape
                                    )
                            )
                            // [v45] 周日指示器
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = if (hasSundayCourses)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.outlineVariant,
                                        shape = CircleShape
                                    )
                            )
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isWeekendExpanded)
                                Icons.Default.KeyboardArrowDown
                            else
                                Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                )
            }

            // [v61] 校区选择对话框
            if (showCampusDialog) {
                AlertDialog(
                onDismissRequest = { showCampusDialog = false },
                title = { Text("Select Campus") },
                text = {
                    Column {
                        Campus.entries.forEach { campus ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.onCampusChanged(campus)
                                        showCampusDialog = false
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = campus == uiState.campus,
                                    onClick = {
                                        viewModel.onCampusChanged(campus)
                                        showCampusDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(campus.displayName)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showCampusDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
            }

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
                // [v57] 使用 HorizontalPager 实现跟手滑动
                val pagerState = rememberPagerState(
                    initialPage = (uiState.currentWeek - 1).coerceIn(0, (uiState.maxWeeks - 1).coerceAtLeast(0)),
                    pageCount = { uiState.maxWeeks }
                )

                // [v57] 同步 pagerState -> ViewModel (滑动切换时)
                LaunchedEffect(pagerState.currentPage) {
                    val newWeek = pagerState.currentPage + 1
                    if (newWeek != uiState.currentWeek) {
                        viewModel.onWeekSelected(newWeek)
                    }
                }

                // [v57] 同步 ViewModel -> pagerState (周选择器点击时)
                // [v58] 优化跳转逻辑：大距离跳转使用瞬间切换，小距离使用动画
                LaunchedEffect(uiState.currentWeek) {
                    val targetPage = uiState.currentWeek - 1
                    if (pagerState.currentPage != targetPage && !pagerState.isScrollInProgress) {
                        val distance = kotlin.math.abs(pagerState.currentPage - targetPage)
                        if (distance >= 3) {
                            // [v58] 大距离跳转：瞬间切换，避免停在两页之间
                            pagerState.scrollToPage(targetPage)
                        } else {
                            // [v58] 小距离跳转：平滑动画
                            pagerState.animateScrollToPage(targetPage)
                        }
                    }
                }

                // [v57] HorizontalPager 替代 AnimatedContent
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp)
                ) { page ->
                    val week = page + 1  // 周次 = 页码 + 1
                    val weekCourses = uiState.courses.filter { it.isWeekInRange(week) }

                    if (weekCourses.isEmpty()) {
                        // [v57] 该周没有课程，显示提示
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "No courses in week $week",
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
                        ScheduleGrid(
                            courses = weekCourses,
                            conflictingCourseIds = uiState.conflictingCourseIds,
                            onCourseClick = { course -> viewModel.onCourseSelected(course) },
                            isWeekendExpanded = isWeekendExpanded,
                            campus = uiState.campus,  // [v61] 传递校区参数
                            modifier = Modifier.fillMaxSize()
                        )
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
