package com.example.course_schedule_for_chd_v002.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.course_schedule_for_chd_v002.domain.model.Campus
import com.example.course_schedule_for_chd_v002.domain.model.Course
import com.example.course_schedule_for_chd_v002.domain.model.DayOfWeek

/**
 * [导出图片] 不滚动、完整高度（1–11 节）的课表网格，专用于导出为图片。
 *
 * 与 [ScheduleGrid] 的几何常量一致；区别：
 * - 去 verticalScroll / 滚动提示 / 末尾空节次折叠按钮
 * - displayNodes 恒为 11（导出完整网格）
 * - 顶部加标题行（学期 + 周次 + 校区）
 * - 课程卡直接用 public [CourseCard]
 *
 * @param titleText 标题（如「2025-2026 第2学期 第12周·渭水校区」）
 */
@Composable
fun ExportableScheduleGrid(
    courses: List<Course>,
    conflictingCourseIds: Set<Long> = emptySet(),
    waterCourseNames: Set<String> = emptySet(),
    isWeekendExpanded: Boolean = false,
    campus: Campus = Campus.WEISHUI,
    todayDayOfWeek: DayOfWeek? = null,
    isCurrentWeek: Boolean = false,
    titleText: String,
    modifier: Modifier = Modifier
) {
    val days = DayOfWeek.entries
    val totalNodes = 11
    val cellHeight = 70.dp
    val headerHeight = 32.dp
    val labelWidth = 40.dp
    val separatorHeight = 4.dp
    val separatorAfterMorning = 4
    val separatorAfterAfternoon = 8
    val timeSlots = campus.timeSlots
    val displayNodes = totalNodes  // 完整 1–11，不折叠
    val gridHeight = cellHeight * displayNodes + separatorHeight * 2
    val density = LocalDensity.current

    fun getNodeOffset(node: Int): IntOffset {
        var offsetDp = cellHeight * (node - 1)
        if (node > separatorAfterMorning) offsetDp += separatorHeight
        if (node > separatorAfterAfternoon) offsetDp += separatorHeight
        return IntOffset(0, with(density) { offsetDp.roundToPx() })
    }

    fun getCourseHeight(startNode: Int, endNode: Int): Dp {
        val baseHeight = cellHeight * (endNode - startNode + 1)
        var extraHeight = 0.dp
        if (startNode <= separatorAfterMorning && endNode > separatorAfterMorning) extraHeight += separatorHeight
        if (startNode <= separatorAfterAfternoon && endNode > separatorAfterAfternoon) extraHeight += separatorHeight
        return baseHeight + extraHeight
    }

    Column(modifier = modifier) {
        // 标题
        Text(
            text = titleText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        )

        // 表头行
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier.width(labelWidth).height(headerHeight)
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                contentAlignment = Alignment.Center
            ) { Text("#", style = MaterialTheme.typography.labelSmall) }
            days.take(5).forEach { day ->
                val isToday = isCurrentWeek && todayDayOfWeek == day
                Box(
                    modifier = Modifier.weight(1f).height(headerHeight)
                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                        .then(if (isToday) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        dayAbbreviation(day),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if (isWeekendExpanded) {
                listOf(DayOfWeek.SATURDAY to "Sat", DayOfWeek.SUNDAY to "Sun").forEach { (day, abbr) ->
                    val isToday = isCurrentWeek && todayDayOfWeek == day
                    Box(
                        modifier = Modifier.weight(1f).height(headerHeight)
                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                            .then(if (isToday) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            abbr,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // 表头分隔线
        Box(
            modifier = Modifier.fillMaxWidth().height(2.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))
        )

        // 网格主体（固定高度，不滚动）
        Box(modifier = Modifier.fillMaxWidth().height(gridHeight)) {
            // 底层：网格背景（节次标签 + 单元格 + 分隔线）
            Column(modifier = Modifier.fillMaxSize()) {
                repeat(displayNodes) { nodeIndex ->
                    val node = nodeIndex + 1
                    if (nodeIndex > 0 && nodeIndex == separatorAfterMorning) {
                        SeparatorRow(labelWidth, separatorHeight, isWeekendExpanded)
                    }
                    if (nodeIndex > 0 && nodeIndex == separatorAfterAfternoon) {
                        SeparatorRow(labelWidth, separatorHeight, isWeekendExpanded)
                    }
                    Row(modifier = Modifier.fillMaxWidth().height(cellHeight)) {
                        val timeSlot = timeSlots[nodeIndex]
                        val timeParts = timeSlot.split("-")
                        Column(
                            modifier = Modifier.width(labelWidth).height(cellHeight)
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "${node}节",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                timeParts.getOrNull(0) ?: "",
                                fontSize = 8.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                timeParts.getOrNull(1) ?: "",
                                fontSize = 8.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val dayColumns = if (isWeekendExpanded) days else days.take(5)
                        dayColumns.forEach { day ->
                            val isToday = isCurrentWeek && todayDayOfWeek == day
                            Box(
                                modifier = Modifier.weight(1f).fillMaxHeight()
                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                    .then(
                                        if (isToday) Modifier.background(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        ) else Modifier
                                    )
                            )
                        }
                    }
                }
            }

            // 顶层：课程卡片
            Row(modifier = Modifier.matchParentSize()) {
                Spacer(modifier = Modifier.width(labelWidth))
                val dayColumns = if (isWeekendExpanded) days else days.take(5)
                dayColumns.forEach { day ->
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        courses.filter { it.dayOfWeek == day }.forEach { course ->
                            val topOffset = getNodeOffset(course.startNode)
                            val courseHeightDp = getCourseHeight(course.startNode, course.endNode)
                            Box(
                                modifier = Modifier.fillMaxWidth().height(courseHeightDp)
                                    .offset { topOffset }.padding(1.dp)
                            ) {
                                CourseCard(
                                    course = course,
                                    hasConflict = course.id in conflictingCourseIds,
                                    isWaterCourse = course.name in waterCourseNames,
                                    onClick = null
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeparatorRow(labelWidth: Dp, separatorHeight: Dp, isWeekendExpanded: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().height(separatorHeight)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Box(
            modifier = Modifier.width(labelWidth).height(separatorHeight)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        repeat(if (isWeekendExpanded) 7 else 5) {
            Box(
                modifier = Modifier.weight(1f).height(separatorHeight)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}

private fun dayAbbreviation(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "Mon"
    DayOfWeek.TUESDAY -> "Tue"
    DayOfWeek.WEDNESDAY -> "Wed"
    DayOfWeek.THURSDAY -> "Thu"
    DayOfWeek.FRIDAY -> "Fri"
    DayOfWeek.SATURDAY -> "Sat"
    DayOfWeek.SUNDAY -> "Sun"
}
