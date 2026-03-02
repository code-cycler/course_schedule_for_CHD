package com.example.course_schedule_for_chd_v002.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 周次选择器组件 (v39)
 * 用于切换当前显示的周次
 * - [v39] 添加跳转到某周的功能
 *
 * @param currentWeek 当前周次 (1-maxWeeks)
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
    // [v39] 跳转对话框状态
    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpInput by remember { mutableStateOf("") }

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

        // [v39] Week text - 可点击以跳转
        // [v58] 添加点击提示
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable { showJumpDialog = true }
        ) {
            Text(
                text = "Week $currentWeek / $maxWeeks",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Tap to jump",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }

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

    // [v39] 跳转对话框
    if (showJumpDialog) {
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("Jump to Week") },
            text = {
                Column {
                    Text(
                        text = "Enter week number (1-$maxWeeks)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = jumpInput,
                        onValueChange = { jumpInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Week") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val week = jumpInput.toIntOrNull()
                        if (week != null && week in 1..maxWeeks) {
                            onWeekSelected(week)
                            showJumpDialog = false
                            jumpInput = ""
                        }
                    }
                ) {
                    Text("Jump")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showJumpDialog = false
                    jumpInput = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}
