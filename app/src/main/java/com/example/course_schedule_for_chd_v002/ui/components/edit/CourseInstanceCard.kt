package com.example.course_schedule_for_chd_v002.ui.components.edit

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.course_schedule_for_chd_v002.domain.model.Course

private val DAY_NAMES = listOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")

/**
 * 单个课程时段卡片
 * 显示: 星期、节次、周次、教师、教室
 */
@Composable
fun CourseInstanceCard(
    instance: Course,
    hasConflict: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (hasConflict) {
        MaterialTheme.colorScheme.error
    } else {
        CardDefaults.cardColors().containerColor
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (hasConflict) {
                    Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.error,
                        CardDefaults.shape
                    )
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (hasConflict) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            } else {
                CardDefaults.cardColors().containerColor
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧: 时间信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = DAY_NAMES[instance.dayOfWeek.value],
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "第${instance.startNode}-${instance.endNode}节",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = instance.getWeeksDisplayText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 右侧: 教师和教室
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.weight(1f)
            ) {
                if (instance.teacher.isNotBlank()) {
                    Text(
                        text = instance.teacher,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (instance.location.isNotBlank()) {
                    Text(
                        text = instance.location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 操作按钮
            Row(
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "编辑",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
