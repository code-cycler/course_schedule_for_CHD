package com.example.course_schedule_for_chd_v002.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.course_schedule_for_chd_v002.domain.model.Course
import com.example.course_schedule_for_chd_v002.domain.model.CourseType

/**
 * 课程卡片组件
 * 显示单个课程的信息
 *
 * @param course 课程数据
 * @param modifier 修饰符
 * @param backgroundColor 背景颜色
 * @param hasConflict 是否存在时间冲突
 * @param onClick 点击回调
 */
@Composable
fun CourseCard(
    course: Course,
    modifier: Modifier = Modifier,
    backgroundColor: Color = getCourseColor(course.courseType),
    hasConflict: Boolean = false,
    onClick: ((Course) -> Unit)? = null
) {
    // 冲突时显示红色边框
    val borderColor = if (hasConflict) Color.Red else Color.Transparent
    val borderWidth = if (hasConflict) 2.dp else 0.dp

    Surface(
        modifier = modifier.then(
            if (onClick != null) {
                Modifier.clickable { onClick(course) }
            } else {
                Modifier
            }
        ).border(borderWidth, borderColor, RoundedCornerShape(4.dp)),
        shape = RoundedCornerShape(4.dp),
        color = if (hasConflict) backgroundColor.copy(alpha = 0.7f) else backgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Course name with conflict indicator
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

            // Location
            if (course.location.isNotBlank()) {
                Text(
                    text = course.location,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

/**
 * 根据课程类型获取颜色
 *
 * @param courseType 课程类型
 * @return 对应的颜色
 */
@Composable
fun getCourseColor(courseType: CourseType): Color {
    return when (courseType) {
        CourseType.REQUIRED -> MaterialTheme.colorScheme.primary
        CourseType.ELECTIVE -> MaterialTheme.colorScheme.secondary
        CourseType.PUBLIC_ELECTIVE -> MaterialTheme.colorScheme.tertiary
        CourseType.PHYSICAL_EDUCATION -> Color(0xFF4CAF50) // Green
        CourseType.PRACTICE -> MaterialTheme.colorScheme.error
        CourseType.OTHER -> MaterialTheme.colorScheme.outline
    }
}
