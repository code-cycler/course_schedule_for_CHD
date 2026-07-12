package com.example.course_schedule_for_chd_v002.ui.screens.schedule

import android.content.Intent
import android.widget.Toast
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.background
import androidx.compose.foundation.border  // [v74] 末尾空节次按钮边框
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape  // [v74] 末尾空节次按钮边框
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
// [新功能] 回到当前周按钮图标 - 不使用 Today 图标
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.course_schedule_for_chd_v002.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.course_schedule_for_chd_v002.domain.model.Campus
import com.example.course_schedule_for_chd_v002.domain.model.Course
import com.example.course_schedule_for_chd_v002.data.remote.parser.ScheduleHtmlParser
import com.example.course_schedule_for_chd_v002.domain.model.DayOfWeek
import com.example.course_schedule_for_chd_v002.ui.components.ExportableScheduleGrid
import com.example.course_schedule_for_chd_v002.ui.components.ScheduleGrid
import com.example.course_schedule_for_chd_v002.ui.components.WeekSelector
import com.example.course_schedule_for_chd_v002.ui.util.OffscreenScheduleCapture
import com.example.course_schedule_for_chd_v002.ui.util.ScheduleImageExporter
import com.example.course_schedule_for_chd_v002.ui.util.rememberScheduleCaptureState
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
    onNavigateToSemester: (String) -> Unit,
    viewModel: ScheduleViewModel = koinViewModel { parametersOf(semester) }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current  // [获取新学期] Toast + [导出图片] 分享用

    // [导出图片] 离屏捕获状态 + 导出流程
    val captureState = rememberScheduleCaptureState()
    var captureArmed by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    val screenWidthPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.roundToPx()
    }

    // [导出图片] 点导出 → 组合 OffscreenScheduleCapture → 等一帧 record → 捕获 → 分享
    LaunchedEffect(captureArmed) {
        if (captureArmed) {
            withFrameNanos { }  // 等离屏 Composable 首帧 record 完成
            val uri = captureState.captureToFile(
                context,
                "schedule_${semester}_w${uiState.currentWeek}.png"
            )
            captureArmed = false
            isExporting = false
            if (uri != null) {
                val shareIntent = ScheduleImageExporter.buildShareIntent(uri)
                context.startActivity(
                    Intent.createChooser(shareIntent, context.getString(R.string.export_share_title))
                )
            } else {
                Toast.makeText(context, context.getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

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
                    // [导出图片] 导出当前周课表为图片
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
                            imageVector = Icons.Default.Share,
                            contentDescription = context.getString(R.string.export_schedule)
                        )
                    }
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

            // [切换学期] 学期选择对话框状态
            var showSemesterDialog by remember { mutableStateOf(false) }

            // [获取新学期] 二级「获取其它学期」对话框状态
            var showFetchSemesterDialog by remember { mutableStateOf(false) }

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
                // 左侧：学期 + 校区切换
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // [切换学期] 学期切换
                    FilterChip(
                        selected = false,
                        onClick = { showSemesterDialog = true },
                        label = {
                            Text(
                                text = semester,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                    )
                    // [v61] 校区切换按钮
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
                }

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
                            // [获取新学期] 仅在本地已有学期（即登录过）时显示入口
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
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
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

            // [获取新学期] 二级「获取其它学期」对话框
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
                                                            Toast.makeText(
                                                                context,
                                                                "已获取 $newSem，$count 门课程",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                            onNavigateToSemester(newSem)
                                                        }
                                                    }
                                                )
                                                .padding(vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = opt.label,
                                                color = if (isInDb) MaterialTheme.colorScheme.outline
                                                        else MaterialTheme.colorScheme.onSurface
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
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(stringResource(R.string.fetching_course_table))
                                        }
                                    }
                                    if (uiState.remoteSemesterOptions.isEmpty()
                                        && uiState.fetchSemesterError == null
                                        && !uiState.isFetchingSemester) {
                                        Text(
                                            "暂无可获取的学期",
                                            color = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                    uiState.fetchSemesterError?.let { err ->
                                        Text(
                                            err,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(top = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = { showFetchSemesterDialog = false },
                            enabled = !uiState.isFetchingSemester
                        ) {
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

    // Course detail dialog
    uiState.selectedCourse?.let { course ->
        CourseDetailDialog(
            course = course,
            isWaterCourse = uiState.isWaterCourse(course.name),  // [新功能]
            onToggleWaterCourse = viewModel::toggleWaterCourse,  // [新功能]
            onDismiss = { viewModel.onCourseSelected(null) }
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
