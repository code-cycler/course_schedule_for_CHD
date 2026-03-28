package com.example.course_schedule_for_chd_v002.ui.components.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.course_schedule_for_chd_v002.domain.model.DayOfWeek

private val DAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/**
 * 星期选择器
 * 7 个 FilterChip 水平排列
 */
@Composable
fun DayOfWeekPicker(
    selected: DayOfWeek,
    onSelected: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        DayOfWeek.entries.forEachIndexed { index, day ->
            FilterChip(
                selected = day == selected,
                onClick = { onSelected(day) },
                label = { Text(DAY_NAMES[index]) },
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 2.dp)
            )
        }
    }
}
