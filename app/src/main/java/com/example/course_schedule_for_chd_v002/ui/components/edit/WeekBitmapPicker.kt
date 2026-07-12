package com.example.course_schedule_for_chd_v002.ui.components.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private const val CHIPS_PER_ROW = 5

/**
 * 周次位图选择器
 * 支持非连续周次选择，输出位图格式 "weeksBitmap:010101..."
 */
@Composable
fun WeekBitmapPicker(
    maxWeeks: Int,
    initialBitmap: String?,
    onBitmapChanged: (bitmap: String, startWeek: Int, endWeek: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // 解析初始位图为已选周次集合
    val initialSelectedWeeks = remember(initialBitmap) {
        if (initialBitmap != null && initialBitmap.isNotEmpty()) {
            initialBitmap.mapIndexedNotNull { index, c ->
                if (c == '1') index else null
            }.toSet()
        } else emptySet()
    }

    var selectedWeeks by remember { mutableStateOf(initialSelectedWeeks) }

    Column(modifier = modifier.fillMaxWidth()) {
        // 快捷操作按钮 - 均分宽度防挤压
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    selectedWeeks = (1..maxWeeks).toSet()
                    emitBitmapChange(selectedWeeks, maxWeeks, onBitmapChanged)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("全选", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = {
                    selectedWeeks = (1..maxWeeks).filter { it % 2 == 1 }.toSet()
                    emitBitmapChange(selectedWeeks, maxWeeks, onBitmapChanged)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("单周", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = {
                    selectedWeeks = (1..maxWeeks).filter { it % 2 == 0 }.toSet()
                    emitBitmapChange(selectedWeeks, maxWeeks, onBitmapChanged)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("双周", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = {
                    selectedWeeks = emptySet()
                    emitBitmapChange(selectedWeeks, maxWeeks, onBitmapChanged)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("清空", style = MaterialTheme.typography.labelSmall)
            }
        }

        // 周次芯片网格 - 手动分行，每行 CHIPS_PER_ROW 个
        val totalItems = maxWeeks
        val rows = (totalItems + CHIPS_PER_ROW - 1) / CHIPS_PER_ROW

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (rowIndex in 0 until rows) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (colIndex in 0 until CHIPS_PER_ROW) {
                        val week = rowIndex * CHIPS_PER_ROW + colIndex + 1
                        if (week <= maxWeeks) {
                            FilterChip(
                                selected = week in selectedWeeks,
                                onClick = {
                                    selectedWeeks = if (week in selectedWeeks) {
                                        selectedWeeks - week
                                    } else {
                                        selectedWeeks + week
                                    }
                                    emitBitmapChange(selectedWeeks, maxWeeks, onBitmapChanged)
                                },
                                label = { Text("$week") }
                            )
                        }
                    }
                }
            }
        }

        // 预览文本
        if (selectedWeeks.isNotEmpty()) {
            val sorted = selectedWeeks.sorted()
            val preview = buildWeeksPreviewText(sorted)
            Text(
                text = preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/**
 * 构建位图并回调
 */
private fun emitBitmapChange(
    selectedWeeks: Set<Int>,
    maxWeeks: Int,
    onBitmapChanged: (String, Int, Int) -> Unit
) {
    if (selectedWeeks.isEmpty()) {
        onBitmapChanged("", 0, 0)
        return
    }
    val sorted = selectedWeeks.sorted()
    val bitmap = CharArray(maxWeeks + 1) { '0' }
    for (w in selectedWeeks) {
        if (w in bitmap.indices) bitmap[w] = '1'
    }
    onBitmapChanged(
        String(bitmap),
        sorted.first(),
        sorted.last()
    )
}

/**
 * 生成周次预览文本
 */
private fun buildWeeksPreviewText(sortedWeeks: List<Int>): String {
    if (sortedWeeks.isEmpty()) return ""

    // 检查是否连续
    val isConsecutive = sortedWeeks.zipWithNext().all { (a, b) -> b == a + 1 }

    if (isConsecutive) {
        val allOdd = sortedWeeks.all { it % 2 == 1 }
        val allEven = sortedWeeks.all { it % 2 == 0 }
        return when {
            allOdd && sortedWeeks.size > 1 -> "单周 第${sortedWeeks.first()}-${sortedWeeks.last()}周"
            allEven && sortedWeeks.size > 1 -> "双周 第${sortedWeeks.first()}-${sortedWeeks.last()}周"
            else -> "第${sortedWeeks.first()}-${sortedWeeks.last()}周"
        }
    }

    return "第${sortedWeeks.joinToString(",")}周"
}
