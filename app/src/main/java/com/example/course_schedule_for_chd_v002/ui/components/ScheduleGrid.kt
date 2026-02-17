package com.example.course_schedule_for_chd_v002.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.course_schedule_for_chd_v002.domain.model.Course
import com.example.course_schedule_for_chd_v002.domain.model.DayOfWeek

/**
 * 课程表网格组件
 * 显示7列（周一到周日）x 12行（第1-12节）的课程表
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
    val nodes = 1..12
    val cellHeight = 48.dp

    Column(modifier = modifier) {
        // Header row (days of week)
        Row(modifier = Modifier.fillMaxWidth()) {
            // Empty corner cell
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(32.dp)
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "#",
                    style = MaterialTheme.typography.labelSmall
                )
            }

            // Day headers
            days.forEach { day ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
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

        // Time slots grid
        nodes.forEach { node ->
            Row(modifier = Modifier.fillMaxWidth()) {
                // Node number
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(cellHeight)
                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = node.toString(),
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                // Day cells
                days.forEach { day ->
                    val course = courses.find {
                        it.dayOfWeek == day && node in it.nodeRange
                    }

                    if (course != null && course.startNode == node) {
                        // Course card spanning multiple nodes
                        val spanCount = course.endNode - course.startNode + 1
                        CourseCard(
                            course = course,
                            modifier = Modifier
                                .weight(1f)
                                .height(cellHeight * spanCount),
                            hasConflict = course.id in conflictingCourseIds,
                            onClick = onCourseClick
                        )
                    } else if (course == null) {
                        // Empty cell
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(cellHeight)
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                    // If course != null && course.startNode != node, skip (covered by spanning card)
                }
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
