package com.example.course_schedule_for_chd_v002.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Divider
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.course_schedule_for_chd_v002.domain.model.ReminderSettings
import com.example.course_schedule_for_chd_v002.ui.screens.schedule.CalendarSyncState

/**
 * 侧边栏设置抽屉组件
 *
 * 显示日历同步功能
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDrawer(
    drawerState: DrawerState,
    settings: ReminderSettings,
    calendarSyncState: CalendarSyncState = CalendarSyncState.Idle,
    onSettingsChange: (ReminderSettings) -> Unit,
    onCalendarSyncClick: () -> Unit,
    onDeleteCalendarClick: () -> Unit,
    onRequestCalendarPermission: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scrollState = rememberScrollState()

    ModalNavigationDrawer(
        drawerState = drawerState,
        modifier = modifier,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(320.dp)
            ) {
                SettingsDrawerContent(
                    settings = settings,
                    calendarSyncState = calendarSyncState,
                    onSettingsChange = onSettingsChange,
                    onCalendarSyncClick = onCalendarSyncClick,
                    onDeleteCalendarClick = onDeleteCalendarClick,
                    onRequestCalendarPermission = onRequestCalendarPermission,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                )
            }
        },
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDrawerContent(
    settings: ReminderSettings,
    calendarSyncState: CalendarSyncState,
    onSettingsChange: (ReminderSettings) -> Unit,
    onCalendarSyncClick: () -> Unit,
    onDeleteCalendarClick: () -> Unit,
    onRequestCalendarPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp)
    ) {
        // 标题
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "日历同步",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 系统日历同步开关
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = null,
                    tint = if (settings.calendarSyncEnabled)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "同步到系统日历",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "将课程添加到手机日历",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = settings.calendarSyncEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        onRequestCalendarPermission()
                    }
                    onSettingsChange(settings.copy(calendarSyncEnabled = enabled))
                }
            )
        }

        if (settings.calendarSyncEnabled) {
            Spacer(modifier = Modifier.height(16.dp))

            // ===== 日历课前提醒 =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("日历课前提醒")
                }
                Switch(
                    checked = settings.calendarBeforeClassReminderEnabled,
                    onCheckedChange = { enabled ->
                        onSettingsChange(settings.copy(calendarBeforeClassReminderEnabled = enabled))
                    }
                )
            }

            // 课前提醒 -> 提前时间选择
            if (settings.calendarBeforeClassReminderEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "提前提醒时间",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    listOf(5, 10, 15, 30, 60).forEach { minutes ->
                        FilterChip(
                            selected = settings.beforeClassReminderMinutes == minutes,
                            onClick = {
                                onSettingsChange(settings.copy(beforeClassReminderMinutes = minutes))
                            },
                            label = { Text("${minutes}分钟") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ===== 日历早八提醒 =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("日历早八提醒")
                }
                Switch(
                    checked = settings.calendarEarlyMorningReminderEnabled,
                    onCheckedChange = { enabled ->
                        onSettingsChange(settings.copy(calendarEarlyMorningReminderEnabled = enabled))
                    }
                )
            }

            // 早八提醒 -> 时间选择
            if (settings.calendarEarlyMorningReminderEnabled) {
                var showTimePicker by remember { mutableStateOf(false) }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "提醒时间（前一天晚上）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${settings.earlyMorningReminderHour}:${String.format("%02d", settings.earlyMorningReminderMinute)}"
                    )
                }

                if (showTimePicker) {
                    val timePickerState = rememberTimePickerState(
                        initialHour = settings.earlyMorningReminderHour,
                        initialMinute = settings.earlyMorningReminderMinute,
                        is24Hour = true
                    )
                    AlertDialog(
                        onDismissRequest = { showTimePicker = false },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    onSettingsChange(
                                        settings.copy(
                                            earlyMorningReminderHour = timePickerState.hour,
                                            earlyMorningReminderMinute = timePickerState.minute
                                        )
                                    )
                                    showTimePicker = false
                                }
                            ) {
                                Text("确定")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showTimePicker = false }) {
                                Text("取消")
                            }
                        },
                        title = {
                            Text("选择早八提醒时间")
                        },
                        text = {
                            TimePicker(state = timePickerState)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "开关变更后需点击同步才会生效",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 同步按钮
            OutlinedButton(
                onClick = onCalendarSyncClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = calendarSyncState !is CalendarSyncState.Syncing
            ) {
                when (calendarSyncState) {
                    is CalendarSyncState.Syncing -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("同步中...")
                    }
                    else -> {
                        Icon(Icons.Filled.Settings, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("同步课程到日历")
                    }
                }
            }

            // 同步状态提示
            when (calendarSyncState) {
                is CalendarSyncState.Synced -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "[OK] 已同步 ${calendarSyncState.count} 节课程",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is CalendarSyncState.Error -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "[X] ${calendarSyncState.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {}
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 删除日历按钮
            OutlinedButton(
                onClick = onDeleteCalendarClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = calendarSyncState !is CalendarSyncState.Deleting
            ) {
                when (calendarSyncState) {
                    is CalendarSyncState.Deleting -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("删除中...")
                    }
                    else -> {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("删除日历事件")
                    }
                }
            }

            // 删除状态提示
            when (calendarSyncState) {
                is CalendarSyncState.Deleted -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "[OK] 已删除所有日历事件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                else -> {}
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "同步: 添加课程到系统日历\n删除: 移除所有已同步的课程",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
