package com.example.course_schedule_for_chd_v002.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 周次选择器组件
 * 用于切换当前显示的周次
 *
 * @param currentWeek 当前周次 (1-16)
 * @param maxWeeks 最大周次数
 * @param onWeekSelected 周次选择回调
 * @param modifier 修饰符
 */
@Composable
fun WeekSelector(
    currentWeek: Int,
    maxWeeks: Int,
    onWeekSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Previous week button
        IconButton(
            onClick = { onWeekSelected(currentWeek - 1) },
            enabled = currentWeek > 1
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowLeft,
                contentDescription = "Previous week"
            )
        }

        // Week text
        Text(
            text = "Week $currentWeek / $maxWeeks",
            style = MaterialTheme.typography.titleMedium
        )

        // Next week button
        IconButton(
            onClick = { onWeekSelected(currentWeek + 1) },
            enabled = currentWeek < maxWeeks
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = "Next week"
            )
        }
    }
}
