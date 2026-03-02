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
import kotlin.math.abs  // [v60] 用于课程颜色哈希

/**
 * 课程卡片组件
 * 显示单个课程的信息
 * [v60] 颜色基于课程名称生成，教室显示3行并使用中间省略
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
    backgroundColor: Color = getCourseColorByName(course.name),  // [v60] 改为基于课程名
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

            // [v60] Location - 显示3行，使用中间省略
            if (course.location.isNotBlank()) {
                Text(
                    text = course.location.ellipsisMiddle(30),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = true,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

/**
 * [v60] 根据课程名称生成颜色
 * 使用 HSL 色彩空间，确保颜色饱和度和亮度适中
 *
 * @param courseName 课程名称
 * @return 对应的颜色
 */
@Composable
internal fun getCourseColorByName(courseName: String): Color {
    // 使用课程名哈希生成色相 (0-360)
    val hue = (abs(courseName.hashCode()) % 360).toFloat()
    // 饱和度 65%，亮度 55%，确保颜色鲜艳但可读
    return Color.hsl(hue, 0.65f, 0.55f)
}

/**
 * [v60] 根据课程类型获取颜色（保留兼容性）
 * @deprecated 使用 getCourseColorByName 替代
 */
@Composable
internal fun getCourseColor(courseType: CourseType): Color {
    return when (courseType) {
        CourseType.REQUIRED -> MaterialTheme.colorScheme.primary
        CourseType.ELECTIVE -> MaterialTheme.colorScheme.secondary
        CourseType.PUBLIC_ELECTIVE -> MaterialTheme.colorScheme.tertiary
        CourseType.PHYSICAL_EDUCATION -> Color(0xFF4CAF50) // Green
        CourseType.PRACTICE -> MaterialTheme.colorScheme.error
        CourseType.OTHER -> MaterialTheme.colorScheme.outline
    }
}

/**
 * [v62] 中间省略文本，优先保留数字和英文字符
 * 保留首尾文字，中间用省略号替代
 * 优先展示数字、英文字母和 # 符号
 * @param maxLength 最大长度（包含省略号）
 * @return 省略后的文本
 *
 * 示例：
 * "#信息学院计算机专业实验室106 (中法)" -> "#...106"
 */
internal fun String.ellipsisMiddle(maxLength: Int = 20): String {
    if (this.length <= maxLength) return this

    // [v62] 判断是否为关键字符（数字、英文字母、#）
    fun isKeyChar(c: Char): Boolean {
        return c.isDigit() || c in 'a'..'z' || c in 'A'..'Z' || c == '#'
    }

    // [v62] 找出所有关键字符的位置
    val keyIndices = indices.filter { i -> isKeyChar(this[i]) }.toSet()

    // 如果没有关键字符，使用简单的首尾保留逻辑
    if (keyIndices.isEmpty()) {
        val availableChars = maxLength - 3
        val headLength = (availableChars + 1) / 2
        val tailLength = availableChars / 2
        return "${take(headLength)}...${takeLast(tailLength)}"
    }

    // [v62] 计算要保留的字符
    val result = StringBuilder()
    val ellipsis = "..."

    // 从头开始添加字符，遇到关键字符时必须保留
    var headEnd = 0
    var i = 0
    while (i < length && result.length + ellipsis.length < maxLength) {
        val isKey = i in keyIndices
        // 关键字符总是添加，非关键字符只在空间充足时添加
        if (isKey || result.length < (maxLength - ellipsis.length) / 2) {
            result.append(this[i])
        }
        i++
        // 如果当前不是关键字符，下一个是关键字符，则停止头部
        if (!isKey && i < length && i in keyIndices && result.length + ellipsis.length >= maxLength / 2) {
            break
        }
    }
    headEnd = i

    // 如果剩余部分已经足够短，直接返回
    if (length - headEnd <= maxLength - result.length) {
        return this
    }

    // 从尾部添加字符，优先保留关键字符
    val tail = StringBuilder()
    var j = length - 1
    while (j >= headEnd && result.length + ellipsis.length + tail.length < maxLength) {
        val isKey = j in keyIndices
        if (isKey || tail.length < (maxLength - ellipsis.length - result.length) / 2) {
            tail.insert(0, this[j])
        }
        j--
    }

    return "$result$ellipsis$tail"
}
