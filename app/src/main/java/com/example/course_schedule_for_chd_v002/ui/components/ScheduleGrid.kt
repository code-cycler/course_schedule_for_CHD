package com.example.course_schedule_for_chd_v002.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale  // [视觉优化] 水课缩放效果
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight  // [v73] 添加 FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp  // [v59] 添加 sp 单位
import com.example.course_schedule_for_chd_v002.domain.model.Campus
import com.example.course_schedule_for_chd_v002.domain.model.Course
import com.example.course_schedule_for_chd_v002.domain.model.DayOfWeek
import kotlin.math.abs  // [v60] 用于课程颜色哈希

/**
 * 课程表网格组件 (v61)
 * - [v38] 每天节次数从12改为11
 * - [v38] 课程卡片布局确保教室信息始终显示
 * - [v38] 末尾空节次折叠功能
 * - [v39] 时间段分隔线（上午1-4节、下午5-8节、晚上9-11节）
 * - [v40] 教室信息自动换行显示
 * - [v42] 重构周末折叠功能，移除 AnimatedVisibility，修复对齐问题
 * - [v44] 周末折叠状态由外部控制，通过参数传入
 * - [v61] 支持校区切换，不同校区有不同的时间安排
 * - [新功能] 支持高亮今日列（仅当视图周=当前教学周时）
 * - [新功能] 表头显示日期（根据周次计算）
 *
 * @param isWeekendExpanded 周末是否展开（由外部控制）
 * @param campus 校区选择（影响时间显示）[v61]
 * @param todayDayOfWeek 今天是星期几，用于高亮今日列 [新功能]
 * @param isCurrentWeek 当前视图是否为当前教学周 [新功能 fix]
 * @param weekStartDate 当前显示周的周一日期，用于表头显示日期 [新功能]
 */
@Composable
fun ScheduleGrid(
    courses: List<Course>,
    conflictingCourseIds: Set<Long> = emptySet(),
    waterCourseNames: Set<String> = emptySet(),  // [新功能] 水课名称集合
    onCourseClick: ((Course) -> Unit)? = null,
    isWeekendExpanded: Boolean = false,  // [v44] 外部控制折叠状态
    campus: Campus = Campus.WEISHUI,     // [v61] 校区选择
    todayDayOfWeek: DayOfWeek? = null,   // [新功能] 今日星期几
    isCurrentWeek: Boolean = false,      // [新功能 fix] 是否为当前教学周
    weekStartDate: java.time.LocalDate? = null,  // [新功能] 当前周的周一日期
    modifier: Modifier = Modifier
) {
    val days = DayOfWeek.entries
    val totalNodes = 11
    val cellHeight = 70.dp  // [v73] 增加格子高度 60->70dp
    val headerHeight = 40.dp  // [新功能] 增加表头高度 32->40dp 以容纳日期显示
    val labelWidth = 40.dp  // [v75] 进一步减小宽度
    val separatorHeight = 4.dp

    // [v61] 使用校区对应的时间表
    val timeSlots = campus.timeSlots

    // [v45] 计算周末是否有课（用于表头样式）
    val hasWeekendCourses = courses.any {
        it.dayOfWeek == DayOfWeek.SATURDAY || it.dayOfWeek == DayOfWeek.SUNDAY
    }

    // [v38] 计算最后有课的节次（用于末尾空节次折叠）
    val lastNodeWithCourse = courses.maxOfOrNull { it.endNode } ?: totalNodes
    var isTailExpanded by remember { mutableStateOf(false) }
    val hasTailEmptyNodes = lastNodeWithCourse < totalNodes

    // [v38] 显示的节次数
    val displayNodes = if (isTailExpanded || !hasTailEmptyNodes) {
        totalNodes
    } else {
        lastNodeWithCourse
    }

    // [v39] 计算网格高度，包含分隔线
    val separatorAfterMorning = 4
    val separatorAfterAfternoon = 8
    val separatorCount = when {
        displayNodes > separatorAfterAfternoon -> 2
        displayNodes > separatorAfterMorning -> 1
        else -> 0
    }
    val gridHeight = cellHeight * displayNodes + separatorHeight * separatorCount

    Column(modifier = modifier) {
        // [v42] 表头行 - 不使用 AnimatedVisibility，直接渲染
        Row(modifier = Modifier.fillMaxWidth()) {
            // 左上角空白
            Box(
                modifier = Modifier
                    .width(labelWidth)
                    .height(headerHeight)
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "#", style = MaterialTheme.typography.labelSmall)
            }

            // 周一到周五
            days.take(5).forEach { day ->
                val isToday = isCurrentWeek && todayDayOfWeek == day  // [新功能 fix] 只有当前周才高亮
                // [新功能] 计算当天日期（周一 = weekStartDate + 0天，周二 = +1天，...）
                val dayDate = weekStartDate?.plusDays((day.value - 1).toLong())

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(headerHeight)
                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                        .then(
                            if (isToday) {
                                // [视觉优化] 更明显的高亮效果：使用 primary 颜色 + 加粗边框
                                Modifier
                                    .background(MaterialTheme.colorScheme.primary)
                                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(0.dp))
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // [新功能] 使用 Column 显示星期几和日期
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = getDayAbbreviation(day),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,  // [视觉优化] 加粗
                            color = if (isToday)
                                MaterialTheme.colorScheme.onPrimary  // [视觉优化] 使用 onPrimary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                        // [新功能] 显示日期
                        if (dayDate != null) {
                            Text(
                                text = "${dayDate.monthValue}/${dayDate.dayOfMonth}",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                color = if (isToday)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // [v45] 周末区域 - 折叠时完全隐藏
            if (isWeekendExpanded) {
                // 展开时：显示周六、周日两列
                // [新功能] 周六日期 = weekStartDate + 5天
                val saturdayDate = weekStartDate?.plusDays(5)
                val isSaturdayToday = isCurrentWeek && todayDayOfWeek == DayOfWeek.SATURDAY

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(headerHeight)
                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                        .then(
                            if (isSaturdayToday) {
                                Modifier
                                    .background(MaterialTheme.colorScheme.primary)
                                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(0.dp))
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Sat",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSaturdayToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSaturdayToday)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                        if (saturdayDate != null) {
                            Text(
                                text = "${saturdayDate.monthValue}/${saturdayDate.dayOfMonth}",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                fontWeight = if (isSaturdayToday) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSaturdayToday)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // [新功能] 周日日期 = weekStartDate + 6天
                val sundayDate = weekStartDate?.plusDays(6)
                val isSundayToday = isCurrentWeek && todayDayOfWeek == DayOfWeek.SUNDAY

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(headerHeight)
                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                        .then(
                            if (isSundayToday) {
                                Modifier
                                    .background(MaterialTheme.colorScheme.primary)
                                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(0.dp))
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Sun",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSundayToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSundayToday)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                        if (sundayDate != null) {
                            Text(
                                text = "${sundayDate.monthValue}/${sundayDate.dayOfMonth}",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                fontWeight = if (isSundayToday) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSundayToday)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            // [v45] 折叠时：完全不显示周末区域
        }

        // [v81] 星期行下方分隔线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))
        )

        // 课程表内容区域
        val scrollState = rememberScrollState()

        // [v77] 滚动提示状态
        // [v82] 上箭头触发：第一个课程卡片被上界遮盖80%
        val density = LocalDensity.current
        val cellHeightPx = with(density) { cellHeight.roundToPx() }

        // [v82] 计算第一个有课的节次
        val firstNodeWithCourse = courses.minOfOrNull { it.startNode } ?: 1

        // [v82] 滚动提示触发阈值：卡片被遮盖80%
        val scrollThreshold = cellHeightPx * 0.8f

        val canScrollUp by remember {
            derivedStateOf {
                // [v82] 当第一个课程卡片的顶部被遮盖超过80%时显示向上提示
                val firstNodeOffset = (firstNodeWithCourse - 1) * cellHeightPx
                scrollState.value > (firstNodeOffset + scrollThreshold).toInt()
            }
        }

        val canScrollDown by remember {
            derivedStateOf {
                // 当最后有课节次的底部接近可视区域底部时显示向下提示
                scrollState.value < scrollState.maxValue - scrollThreshold.toInt()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // 滚动内容
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(gridHeight)
            ) {
                // 底层：网格背景
                // [v42] 使用与表头完全相同的列布局逻辑
                Column(modifier = Modifier.fillMaxSize()) {
                    repeat(displayNodes) { nodeIndex ->
                        val node = nodeIndex + 1

                        // [v39] 在第4节和第8节后添加时间段分隔线
                        if (nodeIndex > 0 && nodeIndex == separatorAfterMorning) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(separatorHeight)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(labelWidth)
                                        .height(separatorHeight)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                                // [v42] 周一到周五
                                repeat(5) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(separatorHeight)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    )
                                }
                                // [v45] 周末区域 - 与表头同步
                                if (isWeekendExpanded) {
                                    repeat(2) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(separatorHeight)
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        )
                                    }
                                }
                            }
                        }
                        if (nodeIndex > 0 && nodeIndex == separatorAfterAfternoon) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(separatorHeight)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(labelWidth)
                                        .height(separatorHeight)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                                // [v42] 周一到周五
                                repeat(5) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(separatorHeight)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    )
                                }
                                // [v45] 周末区域 - 与表头同步
                                if (isWeekendExpanded) {
                                    repeat(2) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(separatorHeight)
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        )
                                    }
                                }
                            }
                        }

                        // [v45] 网格行 - 与表头完全同步的布局
                        Row(modifier = Modifier.fillMaxWidth().height(cellHeight)) {
                            // [v74] 节次标签 - 三行显示：节次 + 开始时间 + 结束时间
                            val timeSlot = timeSlots[nodeIndex]
                            val timeParts = timeSlot.split("-")
                            val startTime = timeParts.getOrNull(0) ?: ""
                            val endTime = timeParts.getOrNull(1) ?: ""

                            Column(
                                modifier = Modifier
                                    .width(labelWidth)
                                    .height(cellHeight)
                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "${node}节",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,  // [v75] 减小字体
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = startTime,
                                    fontSize = 8.sp,  // [v75] 减小字体
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = endTime,
                                    fontSize = 8.sp,  // [v75] 减小字体
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // 周一到周五单元格
                            days.take(5).forEach { day ->
                                val isToday = isCurrentWeek && todayDayOfWeek == day  // [新功能 fix] 只有当前周才高亮
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                        .then(
                                            if (isToday) {
                                                // [视觉优化] 更明显的背景高亮
                                                Modifier.background(
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                )
                                            } else Modifier
                                        )
                                )
                            }

                            // [v45] 周末单元格 - 与表头同步
                            if (isWeekendExpanded) {
                                // 展开时显示两列
                                listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY).forEach { day ->
                                    val isToday = isCurrentWeek && todayDayOfWeek == day  // [新功能 fix] 只有当前周才高亮
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                            .then(
                                            if (isToday) {
                                                    // [视觉优化] 更明显的背景高亮
                                                    Modifier.background(
                                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                    )
                                                } else Modifier
                                            )
                                    )
                                }
                            }
                            // [v45] 折叠时：完全不显示周末区域
                        }
                    }
                }

                // 顶层：课程卡片
                // [v42] 使用与网格背景完全同步的布局
                Row(modifier = Modifier.matchParentSize()) {
                    // 左侧留空
                    Spacer(modifier = Modifier.width(labelWidth))

                    // [v39] 获取 density 用于 Dp 到 Px 的转换
                    val density = LocalDensity.current

                    // [v39] 计算节次对应的Y偏移量（考虑分隔线）
                    fun getNodeOffset(node: Int): IntOffset {
                        var offsetDp = cellHeight * (node - 1)
                        if (node > separatorAfterMorning) offsetDp += separatorHeight
                        if (node > separatorAfterAfternoon) offsetDp += separatorHeight
                        return IntOffset(0, with(density) { offsetDp.roundToPx() })
                    }

                    // [v39] 计算课程高度（考虑跨分隔线的情况）
                    fun getCourseHeight(startNode: Int, endNode: Int): Dp {
                        val baseHeight = cellHeight * (endNode - startNode + 1)
                        var extraHeight = 0.dp
                        if (startNode <= separatorAfterMorning && endNode > separatorAfterMorning) {
                            extraHeight += separatorHeight
                        }
                        if (startNode <= separatorAfterAfternoon && endNode > separatorAfterAfternoon) {
                            extraHeight += separatorHeight
                        }
                        return baseHeight + extraHeight
                    }

                    // 周一到周五课程
                    days.take(5).forEach { day ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            val dayCourses = courses.filter { it.dayOfWeek == day }
                            dayCourses.forEach { course ->
                                val topOffset = getNodeOffset(course.startNode)
                                val courseHeightDp = getCourseHeight(course.startNode, course.endNode)

                                if (course.startNode <= displayNodes) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(courseHeightDp)
                                            .offset { topOffset }
                                            .padding(1.dp)
                                    ) {
                                        CourseCardInternal(
                                            course = course,
                                            hasConflict = course.id in conflictingCourseIds,
                                            isWaterCourse = course.name in waterCourseNames,  // [新功能]
                                            onClick = onCourseClick
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // [v45] 周末课程区域 - 与表头和网格同步
                    if (isWeekendExpanded) {
                        // 展开时：周六、周日各占一列
                        listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY).forEach { day ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                val dayCourses = courses.filter { it.dayOfWeek == day }
                                dayCourses.forEach { course ->
                                    val topOffset = getNodeOffset(course.startNode)
                                    val courseHeightDp = getCourseHeight(course.startNode, course.endNode)

                                    if (course.startNode <= displayNodes) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(courseHeightDp)
                                                .offset { topOffset }
                                                .padding(1.dp)
                                        ) {
                                            CourseCardInternal(
                                                course = course,
                                                hasConflict = course.id in conflictingCourseIds,
                                                isWaterCourse = course.name in waterCourseNames,  // [新功能] 水课标记
                                                onClick = onCourseClick
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // [v45] 折叠时：完全不显示周末区域
                }
            }
            }

            // [v77] 向上滚动提示 [v78] 降低不透明度
            if (canScrollUp) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = "上方有课程",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // [v77] 向下滚动提示 [v78] 降低不透明度
            if (canScrollDown) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "下方有课程",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // [v38] 末尾空节次按钮 [v74] 添加边框 [v75] 汉化并降低高度 [v77] 进一步减小高度
        if (hasTailEmptyNodes) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),  // [v75] 减小垂直内边距
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(
                    onClick = { isTailExpanded = !isTailExpanded },
                    modifier = Modifier
                        .defaultMinSize(minHeight = 16.dp)  // [v77] 进一步减小高度 32->16
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(8.dp)
                        )
                ) {
                    Text(
                        text = if (isTailExpanded) "收起空节次"
                        else "展开全部 (余${totalNodes - lastNodeWithCourse}节)",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

/**
 * 课程卡片内部组件
 * [v60] 颜色基于课程名称生成，教室显示3行并使用中间省略
 * [v74] 教室信息置于底部并添加低不透明度背景，课程名称改为三行
 * [新功能] 支持水课标注，缩放为80% + 降低不透明度
 */
@Composable
private fun CourseCardInternal(
    course: Course,
    hasConflict: Boolean,
    isWaterCourse: Boolean = false,  // [新功能]
    onClick: ((Course) -> Unit)?
) {
    // [v60] 基于课程名称生成颜色
    val backgroundColor = getCourseColorByName(course.name)
    val borderColor = if (hasConflict) Color.Red else Color.Transparent
    val borderWidth = if (hasConflict) 2.dp else 0.dp

    // [视觉优化] 水课：缩放80% + 降低不透明度
    val scale = if (isWaterCourse) 0.8f else 1.0f
    val cardAlpha = when {
        isWaterCourse -> 0.5f   // 水课半透明
        hasConflict -> 0.7f     // 冲突稍透明
        else -> 1.0f            // 正常
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .scale(scale)  // [视觉优化] 应用缩放
            .clip(RoundedCornerShape(4.dp))
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick(course) }
                } else {
                    Modifier
                }
            )
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp)),
        shape = RoundedCornerShape(4.dp),
        color = backgroundColor.copy(alpha = cardAlpha)  // [视觉优化] 应用不透明度
    ) {
        // [视觉优化] 水课文字也降低透明度
        val textAlpha = if (isWaterCourse) 0.7f else 1.0f

        Box(modifier = Modifier.fillMaxSize()) {
            // 课程名称 - 上方区域
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasConflict) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Conflict",
                            tint = Color.White.copy(alpha = textAlpha),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                    }
                    Text(
                        text = course.name,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 3,  // [v74] 改为三行
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White.copy(alpha = textAlpha)  // [视觉优化]
                    )
                }
            }

            // [v74] 教室信息 - 置于底部，带低不透明度背景
            if (course.location.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            color = Color.Black.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = course.location.ellipsisMiddle(10),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = true,
                        color = Color.White.copy(alpha = if (isWaterCourse) 0.5f else 0.9f)  // [视觉优化]
                    )
                }
9            }
        }
    }
}

@Composable
private fun getDayAbbreviation(day: DayOfWeek): String {
    return when (day) {
        DayOfWeek.MONDAY -> "Mon"
        DayOfWeek.TUESDAY -> "Tue"
        DayOfWeek.WEDNESDAY -> "Wed"
        DayOfWeek.THURSDAY -> "Thu"
        DayOfWeek.FRIDAY -> "Fri"
        DayOfWeek.SATURDAY -> "Sat"
        DayOfWeek.SUNDAY -> "Sun"
    }
}
