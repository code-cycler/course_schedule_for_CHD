package com.example.course_schedule_for_chd_v002.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import com.example.course_schedule_for_chd_v002.domain.model.Course
import com.example.course_schedule_for_chd_v002.domain.model.DayOfWeek

/**
 * 课程表网格组件 (v25)
 * 使用 Box + 绝对定位修复跨节课程超出表格的问题
 * [v25] 增加单元格高度，添加滚动支持
 *
 * @param courses 课程列表
 * @param conflictingCourseIds 冲突课程ID集合
 * @param onCourseClick 课程点击回调
 * @param modifier 修饰符
 */
@Composable
fun ScheduleGrid(
    courses: List<Course>,
    conflictingCourseIds: Set<Long> = emptySet(),
    onCourseClick: ((Course) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val days = DayOfWeek.entries
    val totalNodes = 12
    val cellHeight = 60.dp  // [v25] 增加单元格高度 (48dp -> 60dp)
    val headerHeight = 32.dp
    val labelWidth = 40.dp

    // 计算网格总高度
    val gridHeight = cellHeight * totalNodes

    Column(modifier = modifier) {
        // 表头行 (星期)
        Row(modifier = Modifier.fillMaxWidth()) {
            // 左上角空白单元格
            Box(
                modifier = Modifier
                    .width(labelWidth)
                    .height(headerHeight)
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "#",
                    style = MaterialTheme.typography.labelSmall
                )
            }

            // 星期标题
            days.forEach { day ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(headerHeight)
                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = getDayAbbreviation(day),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        // [v25] 课程表内容区域 - 添加滚动支持
        val scrollState = rememberScrollState()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(gridHeight)
            ) {
            // 底层：绘制网格背景
            Column(modifier = Modifier.fillMaxSize()) {
                repeat(totalNodes) { nodeIndex ->
                    val node = nodeIndex + 1
                    Row(modifier = Modifier.fillMaxWidth().height(cellHeight)) {
                        // 节次标签
                        Box(
                            modifier = Modifier
                                .width(labelWidth)
                                .height(cellHeight)
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = node.toString(),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        // 每天的单元格
                        days.forEach { _ ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                            )
                        }
                    }
                }
            }

            // 顶层：课程卡片区域
            Row(modifier = Modifier.matchParentSize()) {
                // 左侧留空（节次标签区域）
                Spacer(modifier = Modifier.width(labelWidth))

                // 课程区域 - 7列布局
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    days.forEachIndexed { dayIndex, day ->
                        // 每一列的课程
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            // 该列的所有课程
                            val dayCourses = courses.filter { it.dayOfWeek == day }

                            dayCourses.forEach { course ->
                                val topOffset = (course.startNode - 1) * cellHeight
                                val courseHeight = (course.endNode - course.startNode + 1) * cellHeight

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(courseHeight)
                                        .offset { IntOffset(0, topOffset.roundToPx()) }
                                        .padding(1.dp)
                                ) {
                                    CourseCardInternal(
                                        course = course,
                                        hasConflict = course.id in conflictingCourseIds,
                                        onClick = onCourseClick
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }  // 关闭内层 Box (gridHeight)
        }      // 关闭外层 Box (滚动容器)
    }          // 关闭 Column
}

/**
 * 课程卡片内部组件
 */
@Composable
private fun CourseCardInternal(
    course: Course,
    hasConflict: Boolean,
    onClick: ((Course) -> Unit)?
) {
    val backgroundColor = getCourseColor(course.courseType)
    val borderColor = if (hasConflict) Color.Red else Color.Transparent
    val borderWidth = if (hasConflict) 2.dp else 0.dp

    Surface(
        modifier = Modifier
            .fillMaxSize()
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
        color = if (hasConflict) backgroundColor.copy(alpha = 0.7f) else backgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 课程名称
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasConflict) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Time Conflict",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                }
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White
                )
            }

            // 教室 - [v35] 移除行数限制，显示完整教室名
            if (course.location.isNotBlank()) {
                Text(
                    text = course.location,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

/**
 * 获取星期的缩写
 */
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
