package com.example.course_schedule_for_chd_v002.ui.screens.schedule

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border  // [v74] 末尾空节次按钮边框
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape  // [v74] 末尾空节次按钮边框
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
// [新功能] 回到当前周按钮图标 - 不使用 Today 图标
import androidx.compose.material3.*
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.course_schedule_for_chd_v002.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.course_schedule_for_chd_v002.domain.model.Campus
import com.example.course_schedule_for_chd_v002.domain.model.Course
import com.example.course_schedule_for_chd_v002.domain.model.DayOfWeek
import com.example.course_schedule_for_chd_v002.ui.components.ScheduleGrid
import com.example.course_schedule_for_chd_v002.ui.components.WeekSelector
import com.example.course_schedule_for_chd_v002.ui.components.SettingsDrawer
import com.example.course_schedule_for_chd_v002.ui.components.LogExportDialog
import com.example.course_schedule_for_chd_v002.util.LogExporter
// [v96] 移除不再需要的协程导入（防抖已取消）
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlinx.coroutines.launch

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

    // [课程提醒] 侧边栏状态
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // [日志导出] 日志导出对话框状态
    var showLogExportDialog by remember { mutableStateOf(false) }
    var logExportResult by remember { mutableStateOf<LogExporter.ExportResult?>(null) }
    var isExportingLogs by remember { mutableStateOf(false) }

    // [课程提醒] 提醒设置
    val reminderSettings by viewModel.reminderSettings.collectAsStateWithLifecycle()

    // [权限管理] 获取 Context
    val context = LocalContext.current

    // [权限管理] 通知权限请求 Launcher
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        android.util.Log.d("ScheduleScreen", "[权限] 通知权限结果: $isGranted")
        viewModel.onNotificationPermissionResult(isGranted)
    }

    // [权限管理] 日历权限请求 Launcher
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val hasRead = permissions[Manifest.permission.READ_CALENDAR] == true
        val hasWrite = permissions[Manifest.permission.WRITE_CALENDAR] == true
        val hasCalendar = hasRead && hasWrite
        android.util.Log.d("ScheduleScreen", "[权限] 日历权限结果: read=$hasRead, write=$hasWrite, hasCalendar=$hasCalendar")
        viewModel.onCalendarPermissionResult(hasCalendar)
    }

    // [权限管理] 检查通知权限
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Android 13 以下不需要运行时权限
        }
    }

    // [权限管理] 检查日历权限
    fun hasCalendarPermission(): Boolean {
        val hasRead = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        val hasWrite = ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        return hasRead && hasWrite
    }

    // [权限管理] 请求通知权限
    fun requestNotificationPermission() {
        android.util.Log.d("ScheduleScreen", "[权限] 请求通知权限")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // [权限管理] 请求日历权限
    fun requestCalendarPermission() {
        android.util.Log.d("ScheduleScreen", "[权限] 请求日历权限")
        calendarPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR
            )
        )
    }

    // [权限管理] 打开精确闹钟设置页面
    fun openExactAlarmSettings() {
        android.util.Log.d("ScheduleScreen", "[权限] 打开精确闹钟设置页面")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                android.util.Log.e("ScheduleScreen", "[权限] 打开精确闹钟设置失败", e)
            }
        }
    }

    // [v24] 每次进入屏幕时重新加载数据
    LaunchedEffect(Unit) {
        android.util.Log.d("ScheduleScreen", "[v24] LaunchedEffect 触发，调用 reload()")
        viewModel.reload()
    }

    // [新功能] 监听生命周期，每次 onResume 时刷新当前时间和教学周
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                android.util.Log.d("ScheduleScreen", "[新功能] ON_RESUME 触发，刷新当前时间和教学周")
                viewModel.refreshCurrentTimeInfo()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 登出后导航
    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) {
            onLogout()
        }
    }

    android.util.Log.d("ScheduleScreen", "[Debug] SettingsDrawer 组件初始化, settings=$reminderSettings")

    SettingsDrawer(
        drawerState = drawerState,
        settings = reminderSettings,
        hasNotificationPermission = hasNotificationPermission(),
        hasCalendarPermission = hasCalendarPermission(),
        hasExactAlarmPermission = viewModel.canScheduleExactAlarms(),
        calendarSyncState = uiState.calendarSyncState,  // [v100] 日历同步状态
        onSettingsChange = {
            android.util.Log.d("ScheduleScreen", "[Debug] 设置变化: $it")
            viewModel.updateReminderSettings(it)
        },
        onCalendarSyncClick = {
            android.util.Log.d("ScheduleScreen", "[Debug] 日历同步按钮被点击")
            // 先检查日历权限
            if (!hasCalendarPermission()) {
                android.util.Log.d("ScheduleScreen", "[Debug] 没有日历权限，先请求权限")
                requestCalendarPermission()
            } else {
                android.util.Log.d("ScheduleScreen", "[Debug] 已有日历权限，直接同步")
                viewModel.syncToCalendar()
            }
        },
        onDeleteCalendarClick = {  // [v98] 删除日历事件
            android.util.Log.d("ScheduleScreen", "[Debug] 删除日历按钮被点击")
            viewModel.deleteCalendarEvents()
        },
        onRequestCalendarPermission = {
            android.util.Log.d("ScheduleScreen", "[Debug] 请求日历权限")
            requestCalendarPermission()
        },
        onRequestNotificationPermission = {
            android.util.Log.d("ScheduleScreen", "[Debug] 请求通知权限")
            requestNotificationPermission()
        },
        onRequestExactAlarmPermission = {
            android.util.Log.d("ScheduleScreen", "[Debug] 请求精确闹钟权限")
            openExactAlarmSettings()
        },
        onRequestAllPermissions = {
            android.util.Log.d("ScheduleScreen", "[Debug] 一键请求所有权限")
            // [权限管理] 依次请求缺失的权限
            when {
                !hasNotificationPermission() -> {
                    android.util.Log.d("ScheduleScreen", "[Debug] 请求通知权限")
                    requestNotificationPermission()
                }
                !hasCalendarPermission() -> {
                    android.util.Log.d("ScheduleScreen", "[Debug] 请求日历权限")
                    requestCalendarPermission()
                }
                !viewModel.canScheduleExactAlarms() -> {
                    android.util.Log.d("ScheduleScreen", "[Debug] 请求精确闹钟权限")
                    openExactAlarmSettings()
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = {
                            android.util.Log.d("ScheduleScreen", "[Debug] 设置按钮被点击, 尝试打开侧边栏")
                            scope.launch { drawerState.open() }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "设置"
                            )
                        }
                    },
                    title = {
                    // [新功能] 显示当前教学周（永远显示现实世界的周次）
                    Column {
                        Text(
                            text = uiState.getTitleText(),
                            style = MaterialTheme.typography.titleMedium
                        )
                        // [新功能] 如果不在当前周，显示副标题
                        uiState.getSubtitleText()?.let { subtitle ->
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    // [日志导出] 日志导出按钮（在同步按钮左侧）
                    // [日志导出] 日志导出按钮（使用 Info 图标区分于设置）
                    IconButton(
                        onClick = { showLogExportDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "导出日志"
                        )
                    }

                    // [新功能] "回到当前周"按钮（移到 actions 区域）
                    if (!uiState.isViewingCurrentWeek() && uiState.actualCurrentWeek != null) {
                        TextButton(
                            onClick = { viewModel.goToCurrentWeek() }
                        ) {
                            Text(
                                text = "回到第${uiState.actualCurrentWeek}周",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    // [v96] 同步按钮（带文字），取消防抖
                    TextButton(
                        onClick = {
                            onNavigateToLogin()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.sync))
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
                // [v75] 固定宽度以更直观显示有课的周末 [v83] 增加宽度并改周六指示器为方形
                FilterChip(
                    selected = isWeekendExpanded,
                    onClick = { isWeekendExpanded = !isWeekendExpanded },
                    modifier = Modifier.width(96.dp),  // [v83] 增加宽度 80->96
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.weekend),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                            // [v45] 周六指示器 [v83] 改为方形
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = if (hasSaturdayCourses)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(2.dp)  // [v83] 方形
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
                                Icons.Filled.KeyboardArrowDown
                            else
                                Icons.Filled.KeyboardArrowRight,
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
                title = { Text(stringResource(R.string.select_campus)) },
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
                        Text(stringResource(R.string.cancel))
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
                            text = stringResource(R.string.no_courses_found),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.please_login),
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
                // [v73] 添加 pageSpacing 分隔表格
                HorizontalPager(
                    state = pagerState,
                    pageSpacing = 16.dp,
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
                                    text = stringResource(R.string.no_courses_in_week, week),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = stringResource(R.string.try_another_week),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        ScheduleGrid(
                            courses = weekCourses,
                            conflictingCourseIds = uiState.conflictingCourseIds,
                            waterCourseNames = uiState.waterCourseNames,  // [新功能] 传递水课列表
                            onCourseClick = { course -> viewModel.onCourseSelected(course) },
                            isWeekendExpanded = isWeekendExpanded,
                            campus = uiState.campus,  // [v61] 传递校区参数
                            todayDayOfWeek = uiState.todayDayOfWeek,  // [新功能] 传递今日星期几
                            isCurrentWeek = week == uiState.actualCurrentWeek,  // [新功能 fix] 只有当前周才高亮
                            weekStartDate = uiState.weekStartDate,  // [新功能] 传递周开始日期
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
                            Text(stringResource(R.string.dismiss))
                        }
                    }
                ) {
                    Text(error)
                }
            }
        }
    }
    }  // SettingsDrawer end

    // Course detail dialog
    uiState.selectedCourse?.let { course ->
        CourseDetailDialog(
            course = course,
            isWaterCourse = uiState.isWaterCourse(course.name),  // [新功能]
            onToggleWaterCourse = viewModel::toggleWaterCourse,  // [新功能]
            onDismiss = { viewModel.onCourseSelected(null) }
        )
    }

    // [日志导出] 日志导出对话框
    if (showLogExportDialog) {
        LogExportDialog(
            isExporting = isExportingLogs,
            result = logExportResult,
            onExport = {
                isExportingLogs = true
                logExportResult = null
                scope.launch {
                    logExportResult = LogExporter.exportLogs(context)
                    isExportingLogs = false
                }
            },
            onShare = { file ->
                try {
                    val shareIntent = LogExporter.shareLogFile(context, file)
                    context.startActivity(Intent.createChooser(shareIntent, "分享日志文件"))
                } catch (e: Exception) {
                    android.util.Log.e("ScheduleScreen", "分享日志失败", e)
                }
            },
            onDismiss = {
                showLogExportDialog = false
                logExportResult = null
            }
        )
    }
}

/**
 * 课程详情弹窗
 *
 * @param course 课程数据
 * @param isWaterCourse 是否为水课 [新功能]
 * @param onToggleWaterCourse 切换水课标注回调 [新功能]
 * @param onDismiss 关闭回调
 */
@Composable
private fun CourseDetailDialog(
    course: Course,
    isWaterCourse: Boolean,  // [新功能]
    onToggleWaterCourse: (String) -> Unit,  // [新功能]
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(course.name) },
        text = {
            Column {
                if (course.teacher.isNotBlank()) {
                    Text(stringResource(R.string.teacher, course.teacher))
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (course.location.isNotBlank()) {
                    Text(stringResource(R.string.location, course.location))
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(course.getWeeksDisplayText())  // [v94] 使用位图精确显示周次
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.time, course.startNode, course.endNode))
            }
        },
        confirmButton = {
            Row {
                // [新功能] 水课标注按钮
                TextButton(
                    onClick = {
                        onToggleWaterCourse(course.name)
                        onDismiss()
                    }
                ) {
                    Text(
                        text = stringResource(
                            if (isWaterCourse) R.string.unmark_water_course
                            else R.string.mark_as_water_course
                        ),
                        color = if (isWaterCourse)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.ok))
                }
            }
        }
    )
}
