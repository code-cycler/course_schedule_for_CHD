package com.example.course_schedule_for_chd_v002.ui.screens.schedule

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.course_schedule_for_chd_v002.ui.components.CourseReportDialog
import com.example.course_schedule_for_chd_v002.ui.components.edit.CourseEditorSheet
import com.example.course_schedule_for_chd_v002.util.AppLogger
import com.example.course_schedule_for_chd_v002.util.HtmlCache
import com.example.course_schedule_for_chd_v002.util.LogExporter
import com.example.course_schedule_for_chd_v002.util.ReportGenerator
import com.example.course_schedule_for_chd_v002.util.TimeUtils
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlinx.coroutines.launch
import java.io.File
import android.widget.Toast
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import com.example.course_schedule_for_chd_v002.data.remote.parser.ScheduleHtmlParser
import com.example.course_schedule_for_chd_v002.ui.components.ExportableScheduleGrid
import com.example.course_schedule_for_chd_v002.ui.util.OffscreenScheduleCapture
import com.example.course_schedule_for_chd_v002.ui.util.ScheduleImageExporter
import com.example.course_schedule_for_chd_v002.ui.util.rememberScheduleCaptureState

/**
 * 课程表界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    semester: String,
    onNavigateToSemester: (String) -> Unit,
    onLogout: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToCheckInAssist: () -> Unit = {},
    viewModel: ScheduleViewModel = koinViewModel { parametersOf(semester) }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // 日志导出对话框状态
    var showLogExportDialog by remember { mutableStateOf(false) }
    var logExportResult by remember { mutableStateOf<LogExporter.ExportResult?>(null) }
    var isExportingLogs by remember { mutableStateOf(false) }

    // 日历同步设置
    val reminderSettings by viewModel.reminderSettings.collectAsStateWithLifecycle()

    val context = LocalContext.current

    // [导出图片] 离屏捕获 + 导出流程
    val captureState = rememberScheduleCaptureState()
    var captureArmed by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    val screenWidthPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.roundToPx()
    }
    LaunchedEffect(captureArmed) {
        if (captureArmed) {
            withFrameNanos { }  // 等离屏 Composable 首帧 record 完成
            val uri = captureState.captureToFile(context, "schedule_${semester}_w${uiState.currentWeek}.png")
            captureArmed = false
            isExporting = false
            if (uri != null) {
                context.startActivity(
                    Intent.createChooser(ScheduleImageExporter.buildShareIntent(uri), context.getString(R.string.export_share_title))
                )
            } else {
                Toast.makeText(context, context.getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // [切换学期] 学期选择 + [获取新学期] 二级对话框
    var showSemesterDialog by remember { mutableStateOf(false) }
    var showFetchSemesterDialog by remember { mutableStateOf(false) }
    var showCampusDialog by remember { mutableStateOf(false) }

    // SAF 保存状态
    var pendingSaveFile by remember { mutableStateOf<File?>(null) }

    // 报告保存 Launcher (SAF CreateDocument)
    val reportSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let {
            val file = pendingSaveFile
            if (file != null && file.exists()) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { output: java.io.OutputStream ->
                        file.inputStream().use { input: java.io.InputStream ->
                            input.copyTo(output)
                        }
                    }
                    AppLogger.i("ScheduleScreen", "[保存] 报告已保存到: $uri")
                } catch (e: Exception) {
                    AppLogger.e("ScheduleScreen", "[保存] 保存报告失败", e)
                }
            }
            pendingSaveFile = null
        }
    }

    // 日志保存 Launcher (SAF CreateDocument)
    val logSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let {
            val file = pendingSaveFile
            if (file != null && file.exists()) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { output: java.io.OutputStream ->
                        file.inputStream().use { input: java.io.InputStream ->
                            input.copyTo(output)
                        }
                    }
                    AppLogger.i("ScheduleScreen", "[保存] 日志已保存到: $uri")
                } catch (e: Exception) {
                    AppLogger.e("ScheduleScreen", "[保存] 保存日志失败", e)
                }
            }
            pendingSaveFile = null
        }
    }

    // 日历权限请求 Launcher
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val hasRead = permissions[Manifest.permission.READ_CALENDAR] == true
        val hasWrite = permissions[Manifest.permission.WRITE_CALENDAR] == true
        val hasCalendar = hasRead && hasWrite
        AppLogger.d("ScheduleScreen", "[权限] 日历权限结果: $hasCalendar")
        viewModel.onCalendarPermissionResult(hasCalendar)
    }

    fun hasCalendarPermission(): Boolean {
        val hasRead = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        val hasWrite = ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        return hasRead && hasWrite
    }

    fun requestCalendarPermission() {
        AppLogger.d("ScheduleScreen", "[权限] 请求日历权限")
        calendarPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR
            )
        )
    }

    // 每次进入屏幕时重新加载数据
    LaunchedEffect(Unit) {
        AppLogger.d("ScheduleScreen", "LaunchedEffect 触发，调用 reload()")
        viewModel.reload()
    }

    // 监听生命周期
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                AppLogger.d("ScheduleScreen", "ON_RESUME 触发，刷新当前时间和教学周")
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

    SettingsDrawer(
        drawerState = drawerState,
        settings = reminderSettings,
        calendarSyncState = uiState.calendarSyncState,
        onSettingsChange = {
            AppLogger.d("ScheduleScreen", "设置变化: $it")
            viewModel.updateReminderSettings(it)
        },
        onCalendarSyncClick = {
            if (!hasCalendarPermission()) {
                requestCalendarPermission()
            } else {
                viewModel.syncToCalendar()
            }
        },
        onDeleteCalendarClick = {
            viewModel.deleteCalendarEvents()
        },
        onRequestCalendarPermission = {
            requestCalendarPermission()
        },
        onClearData = {
            viewModel.clearAllSchedules()
            scope.launch { drawerState.close() }
            Toast.makeText(context, "课表已清除", Toast.LENGTH_SHORT).show()
        },
        currentSemester = semester,
        currentCampusName = uiState.campus.displayName,
        onSemesterClick = { showSemesterDialog = true },
        onCampusClick = { showCampusDialog = true },
        onExportLogsClick = { showLogExportDialog = true },
        onCheckInAssistClick = {
            scope.launch { drawerState.close() }
            onNavigateToCheckInAssist()
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch { drawerState.open() }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "设置"
                            )
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = uiState.getTitleText(),
                                style = MaterialTheme.typography.titleMedium
                            )
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
                        // [导出图片] 导出当前周课表
                        IconButton(
                            onClick = {
                                if (!isExporting) {
                                    isExporting = true
                                    captureArmed = true
                                }
                            },
                            enabled = !isExporting
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = context.getString(R.string.export_schedule)
                            )
                        }

                        // 回到当前周按钮
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

                        // 同步按钮
                        TextButton(
                            onClick = { onNavigateToLogin() }
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
                // [跨学期] 新学期横幅：本地学期落后于日期推断学期（2/15、8/15 规则）时提醒
                uiState.newSemesterBanner?.let { banner ->
                    NewSemesterBanner(
                        banner = banner,
                        isFetching = uiState.isFetchingNewSemester,
                        error = uiState.newSemesterError,
                        onAcquire = { viewModel.acquireNewSemester(onNavigateToSemester) },
                        onDismiss = { viewModel.dismissNewSemesterBanner() }
                    )
                }

                var isWeekendExpanded by remember { mutableStateOf(false) }

                val displayCourses = uiState.displayCourses
                val hasSaturdayCourses = displayCourses.any { it.dayOfWeek == DayOfWeek.SATURDAY }
                val hasSundayCourses = displayCourses.any { it.dayOfWeek == DayOfWeek.SUNDAY }
                val hasWeekendCourses = hasSaturdayCourses || hasSundayCourses

                LaunchedEffect(uiState.currentWeek, hasWeekendCourses) {
                    isWeekendExpanded = hasWeekendCourses
                }

                // 周选择行
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 周数选择器
                    WeekSelector(
                        currentWeek = uiState.currentWeek,
                        maxWeeks = uiState.maxWeeks,
                        onWeekSelected = viewModel::onWeekSelected,
                        modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally)
                    )

                    // 周末折叠按钮
                    FilterChip(
                        selected = isWeekendExpanded,
                        onClick = { isWeekendExpanded = !isWeekendExpanded },
                        modifier = Modifier.wrapContentWidth(),
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
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            color = if (hasSaturdayCourses)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(2.dp)
                                        )
                                )
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

                // 校区选择对话框
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

                // [切换学期] 学期选择对话框
                if (showSemesterDialog) {
                    AlertDialog(
                        onDismissRequest = { showSemesterDialog = false },
                        title = { Text("选择学期") },
                        text = {
                            Column {
                                uiState.allSemesters.forEach { sem ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onNavigateToSemester(sem)
                                                showSemesterDialog = false
                                            }
                                            .padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = sem == semester,
                                            onClick = {
                                                onNavigateToSemester(sem)
                                                showSemesterDialog = false
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(sem)
                                    }
                                }
                                if (uiState.allSemesters.isNotEmpty()) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                    TextButton(
                                        onClick = {
                                            showSemesterDialog = false
                                            showFetchSemesterDialog = true
                                            viewModel.fetchRemoteSemesterOptions()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.fetch_other_semester))
                                    }
                                }
                                if (uiState.allSemesters.isEmpty()) {
                                    Text(
                                        text = "暂无本地学期，请点「同步」获取当前学期课表",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showSemesterDialog = false }) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    )
                }

                // [获取新学期] 二级对话框
                if (showFetchSemesterDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            if (!uiState.isFetchingSemester) showFetchSemesterDialog = false
                        },
                        title = { Text(stringResource(R.string.fetch_other_semester)) },
                        text = {
                            Box {
                                if (uiState.isFetchingSemester && uiState.remoteSemesterOptions.isEmpty()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(stringResource(R.string.fetching_semester_list))
                                    }
                                } else {
                                    Column {
                                        uiState.remoteSemesterOptions.forEach { opt ->
                                            val localStr = ScheduleHtmlParser.parseSemesterString(opt.label) ?: opt.label
                                            val isInDb = localStr in uiState.allSemesters
                                            val isCurrent = localStr == semester
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .then(
                                                        if (isInDb || uiState.isFetchingSemester) Modifier
                                                        else Modifier.clickable {
                                                            viewModel.fetchSpecifiedSemester(opt.remoteId, localStr) { newSem, count ->
                                                                showFetchSemesterDialog = false
                                                                Toast.makeText(context, "已获取 $newSem，$count 门课程", Toast.LENGTH_SHORT).show()
                                                                onNavigateToSemester(newSem)
                                                            }
                                                        }
                                                    )
                                                    .padding(vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = opt.label,
                                                    color = if (isInDb) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = when {
                                                        isCurrent -> stringResource(R.string.current_label)
                                                        isInDb -> stringResource(R.string.in_database)
                                                        else -> ""
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        }
                                        if (uiState.isFetchingSemester && uiState.remoteSemesterOptions.isNotEmpty()) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(stringResource(R.string.fetching_course_table))
                                            }
                                        }
                                        uiState.fetchSemesterError?.let { err ->
                                            Text(err, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showFetchSemesterDialog = false }, enabled = !uiState.isFetchingSemester) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    )
                }

                // [导出图片] 离屏渲染当前周课表（仅 captureArmed 时组合，等一帧捕获后移除）
                if (captureArmed) {
                    val parts = semester.split("-")
                    val semDisplay = if (parts.size == 3) "${parts[0]}-${parts[1]} 第${parts[2]}学期" else semester
                    OffscreenScheduleCapture(
                        captureState = captureState,
                        widthPx = screenWidthPx
                    ) {
                        ExportableScheduleGrid(
                            courses = displayCourses,
                            conflictingCourseIds = uiState.conflictingCourseIds,
                            waterCourseNames = uiState.waterCourseNames,
                            isWeekendExpanded = hasWeekendCourses,
                            campus = uiState.campus,
                            todayDayOfWeek = uiState.todayDayOfWeek,
                            isCurrentWeek = uiState.currentWeek == uiState.actualCurrentWeek,
                            titleText = "$semDisplay 第${uiState.currentWeek}周·${uiState.campus.displayName}"
                        )
                    }
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
                    val pagerState = rememberPagerState(
                        initialPage = (uiState.currentWeek - 1).coerceIn(0, (uiState.maxWeeks - 1).coerceAtLeast(0)),
                        pageCount = { uiState.maxWeeks }
                    )

                    LaunchedEffect(pagerState.currentPage) {
                        val newWeek = pagerState.currentPage + 1
                        if (newWeek != uiState.currentWeek) {
                            viewModel.onWeekSelected(newWeek)
                        }
                    }

                    LaunchedEffect(uiState.currentWeek) {
                        val targetPage = uiState.currentWeek - 1
                        if (pagerState.currentPage != targetPage && !pagerState.isScrollInProgress) {
                            val distance = kotlin.math.abs(pagerState.currentPage - targetPage)
                            if (distance >= 3) {
                                pagerState.scrollToPage(targetPage)
                            } else {
                                pagerState.animateScrollToPage(targetPage)
                            }
                        }
                    }

                    HorizontalPager(
                        state = pagerState,
                        pageSpacing = 16.dp,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp)
                    ) { page ->
                        val week = page + 1
                        val weekCourses = uiState.coursesByWeek[week] ?: emptyList()

                        if (weekCourses.isEmpty()) {
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
                                waterCourseNames = uiState.waterCourseNames,
                                onCourseClick = { course -> viewModel.onCourseSelected(course) },
                                isWeekendExpanded = isWeekendExpanded,
                                campus = uiState.campus,
                                todayDayOfWeek = uiState.todayDayOfWeek,
                                isCurrentWeek = week == uiState.actualCurrentWeek,
                                weekStartDate = uiState.weekStartDate,
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

    // 课程详情弹窗
    uiState.selectedCourse?.let { course ->
        val weekCourses = uiState.coursesByWeek[uiState.currentWeek] ?: emptyList()

        // 实时计算当前课程与哪些课程冲突
        val conflictingCourses = remember(course.id, weekCourses) {
            val conflictMap = TimeUtils.findConflicts(weekCourses)
            val conflictIds = conflictMap[course.id] ?: emptyList()
            val courseMap = weekCourses.associateBy { it.id }
            conflictIds.mapNotNull { courseMap[it] }
        }

        CourseDetailDialog(
            course = course,
            isWaterCourse = uiState.isWaterCourse(course.name),
            conflictingCourses = conflictingCourses,
            onToggleWaterCourse = viewModel::toggleWaterCourse,
            onEditCourse = {
                viewModel.onCourseSelected(null)
                viewModel.openCourseEditor(course.name)
            },
            onReportError = {
                AppLogger.d("ScheduleViewModel", "[报告] 课程详情'报告错误'按钮被点击: course=${course.name}")
                viewModel.onCourseSelected(null)
                viewModel.onReportFromCourseDetail(course)
            },
            onConflictCourseClick = { conflictCourse ->
                viewModel.onCourseSelected(conflictCourse)
            },
            onDismiss = { viewModel.onCourseSelected(null) }
        )
    }

    // 课程编辑器
    uiState.editCourseGroup?.let { group ->
        CourseEditorSheet(
            editGroup = group,
            suggestedTeachers = uiState.suggestedTeachers,
            suggestedLocations = uiState.suggestedLocations,
            editConflicts = uiState.editConflicts,
            onUpdateInstance = viewModel::updateCourseInstance,
            onDeleteInstance = viewModel::deleteCourseInstance,
            onAddInstance = viewModel::addCourseInstance,
            onDismiss = viewModel::dismissCourseEditor
        )
    }

    // 日志导出对话框
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
                    AppLogger.e("ScheduleScreen", "分享日志失败", e)
                }
            },
            onSave = { file ->
                pendingSaveFile = file
                logSaveLauncher.launch(file.name)
            },
            onDismiss = {
                showLogExportDialog = false
                logExportResult = null
            }
        )
    }

    // 课程识别错误报告对话框
    if (uiState.showCourseReport) {
        AppLogger.d("ScheduleViewModel", "[报告] CourseReportDialog 渲染中, reportState=${uiState.reportState}, targetCourse=${uiState.reportTargetCourse?.name}")
        CourseReportDialog(
            targetCourse = uiState.reportTargetCourse,
            semester = uiState.semester,
            courseCount = uiState.courses.size,
            reportState = uiState.reportState,
            onGenerate = { description, target, incCourses, incHtml, incLogs ->
                AppLogger.d("ScheduleViewModel", "[报告] onGenerate 回调触发: desc='${description.take(30)}', target=${target?.name}, incCourses=$incCourses, incHtml=$incHtml, incLogs=$incLogs")
                viewModel.generateReport(context, description, target, incCourses, incHtml, incLogs)
            },
            onShare = { file ->
                try {
                    val shareIntent = com.example.course_schedule_for_chd_v002.util.ReportGenerator.shareReport(context, file)
                    context.startActivity(Intent.createChooser(shareIntent, "分享课程识别错误报告"))
                } catch (e: Exception) {
                    AppLogger.e("ScheduleScreen", "分享报告失败", e)
                }
            },
            onSave = { file ->
                pendingSaveFile = file
                reportSaveLauncher.launch(file.name)
            },
            onDismiss = viewModel::dismissCourseReport
        )
    }
}

/**
 * 课程详情弹窗
 */
@Composable
private fun CourseDetailDialog(
    course: Course,
    isWaterCourse: Boolean,
    conflictingCourses: List<Course>,
    onToggleWaterCourse: (String) -> Unit,
    onEditCourse: () -> Unit,
    onReportError: () -> Unit,
    onConflictCourseClick: (Course) -> Unit,
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
                Text(course.getWeeksDisplayText())
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.time, course.startNode, course.endNode))

                // 冲突详情区域
                if (conflictingCourses.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.conflict_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.conflict_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    conflictingCourses.forEach { conflictCourse ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            onClick = { onConflictCourseClick(conflictCourse) }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = conflictCourse.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val details = buildList {
                                        if (conflictCourse.teacher.isNotBlank()) add(conflictCourse.teacher)
                                        if (conflictCourse.location.isNotBlank()) add(conflictCourse.location)
                                    }
                                    Text(
                                        text = details.joinToString(" | "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = getDayDisplayName(conflictCourse.dayOfWeek) +
                                                " 第${conflictCourse.startNode}-${conflictCourse.endNode}节",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(
                    onClick = { onReportError() }
                ) {
                    Text(
                        text = "报告错误",
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                TextButton(
                    onClick = { onEditCourse() }
                ) {
                    Text(
                        text = "编辑课程",
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
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

private fun getDayDisplayName(day: DayOfWeek): String {
    return when (day) {
        DayOfWeek.MONDAY -> "周一"
        DayOfWeek.TUESDAY -> "周二"
        DayOfWeek.WEDNESDAY -> "周三"
        DayOfWeek.THURSDAY -> "周四"
        DayOfWeek.FRIDAY -> "周五"
        DayOfWeek.SATURDAY -> "周六"
        DayOfWeek.SUNDAY -> "周日"
    }
}

/**
 * [跨学期] 新学期横幅：本地学期落后于日期推断学期时显示。
 * 点击「获取/切换」→ 免登录抓取或直接切换；点「关闭」当天内不再打扰。
 */
@Composable
private fun NewSemesterBanner(
    banner: NewSemesterBanner,
    isFetching: Boolean,
    error: String?,
    onAcquire: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "新学期 ${banner.inferredSemester} 已开始",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = error
                        ?: if (banner.localExists) "点此切换到新学期课表" else "点此获取新学期课表",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (error != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            if (isFetching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                TextButton(onClick = onAcquire) {
                    Text(if (banner.localExists) "切换" else "获取")
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "关闭提醒",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
