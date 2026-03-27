package com.example.course_schedule_for_chd_v002.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.course_schedule_for_chd_v002.domain.model.ReminderSettings
import com.example.course_schedule_for_chd_v002.ui.screens.schedule.CalendarSyncState
import com.example.course_schedule_for_chd_v002.util.AppLogger
import java.util.Calendar

/**
 * 侧边栏设置抽屉组件
 *
 * 显示提醒设置和日历同步功能
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDrawer(
    drawerState: DrawerState,
    settings: ReminderSettings,
    hasNotificationPermission: Boolean = false,
    hasCalendarPermission: Boolean = false,
    hasExactAlarmPermission: Boolean = false,
    calendarSyncState: CalendarSyncState = CalendarSyncState.Idle,  // [v100] 日历同步状态
    onSettingsChange: (ReminderSettings) -> Unit,
    onCalendarSyncClick: () -> Unit,
    onDeleteCalendarClick: () -> Unit,  // [v98] 删除日历事件回调
    onRequestCalendarPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestExactAlarmPermission: () -> Unit,
    onRequestAllPermissions: () -> Unit = {},  // [权限管理] 一键获取所需权限
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scrollState = rememberScrollState()

    // Debug: 侧边栏状态变化
    AppLogger.d("SettingsDrawer", "SettingsDrawer recomposed, drawerState.isOpen=${drawerState.isOpen}, settings=$settings, hasNotification=$hasNotificationPermission, hasCalendar=$hasCalendarPermission, hasExactAlarm=$hasExactAlarmPermission")

    ModalNavigationDrawer(
        drawerState = drawerState,
        modifier = modifier,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(320.dp)
            ) {
                SettingsDrawerContent(
                    settings = settings,
                    hasNotificationPermission = hasNotificationPermission,
                    hasCalendarPermission = hasCalendarPermission,
                    hasExactAlarmPermission = hasExactAlarmPermission,
                    calendarSyncState = calendarSyncState,  // [v100]
                    onSettingsChange = onSettingsChange,
                    onCalendarSyncClick = onCalendarSyncClick,
                    onDeleteCalendarClick = onDeleteCalendarClick,  // [v98]
                    onRequestCalendarPermission = onRequestCalendarPermission,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onRequestExactAlarmPermission = onRequestExactAlarmPermission,
                    onRequestAllPermissions = onRequestAllPermissions,  // [权限管理]
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                )
            }
        },
        content = content
    )
}

@Composable
private fun SettingsDrawerContent(
    settings: ReminderSettings,
    hasNotificationPermission: Boolean,
    hasCalendarPermission: Boolean,
    hasExactAlarmPermission: Boolean,
    calendarSyncState: CalendarSyncState = CalendarSyncState.Idle,  // [v100] 日历同步状态
    onSettingsChange: (ReminderSettings) -> Unit,
    onCalendarSyncClick: () -> Unit,
    onDeleteCalendarClick: () -> Unit,  // [v98] 删除日历事件回调
    onRequestCalendarPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestExactAlarmPermission: () -> Unit,
    onRequestAllPermissions: () -> Unit = {},  // [权限管理] 一键获取所需权限
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
                text = "提醒设置",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // [权限状态] 权限状态卡片
        PermissionStatusCard(
            hasNotificationPermission = hasNotificationPermission,
            hasCalendarPermission = hasCalendarPermission,
            hasExactAlarmPermission = hasExactAlarmPermission,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onRequestCalendarPermission = onRequestCalendarPermission,
            onRequestExactAlarmPermission = onRequestExactAlarmPermission,
            onRequestAllPermissions = onRequestAllPermissions
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // 次日早八提醒设置
        ReminderSection(
            title = "次日早八提醒",
            description = "前一天晚上提醒第二天的早八课程",
            icon = Icons.Filled.Warning,
            enabled = settings.earlyMorningReminderEnabled,
            onEnabledChange = { enabled ->
                onSettingsChange(settings.copy(earlyMorningReminderEnabled = enabled))
            }
        ) {
            // [v106] 时间选择器 - 始终可编辑
            TimePickerRow(
                hour = settings.earlyMorningReminderHour,
                minute = settings.earlyMorningReminderMinute,
                enabled = settings.earlyMorningReminderEnabled,
                onTimeChange = { hour, minute ->
                    onSettingsChange(
                        settings.copy(
                            earlyMorningReminderHour = hour,
                            earlyMorningReminderMinute = minute
                        )
                    )
                }
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "将在设定时间检查第二天第1-2节是否有课",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // [v106] 上课前提醒设置 - 使用现代化 Slider 组件
        ReminderSection(
            title = "上课前提醒",
            description = "上课前提前通知",
            icon = Icons.Filled.Warning,
            enabled = settings.beforeClassReminderEnabled,
            onEnabledChange = { enabled ->
                onSettingsChange(settings.copy(beforeClassReminderEnabled = enabled))
            }
        ) {
            BeforeClassTimeSelector(
                currentValue = settings.beforeClassReminderMinutes,
                enabled = settings.beforeClassReminderEnabled,
                onValueChange = { minutes ->
                    onSettingsChange(settings.copy(beforeClassReminderMinutes = minutes))
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // 系统日历集成
        ReminderSection(
            title = "系统日历",
            description = "同步课程到手机日历",
            icon = Icons.Filled.Edit,
            enabled = settings.calendarSyncEnabled,
            onEnabledChange = { enabled ->
                if (enabled) {
                    // 请求日历权限
                    onRequestCalendarPermission()
                }
                onSettingsChange(settings.copy(calendarSyncEnabled = enabled))
            }
        ) {
            // [v105] 日历提醒设置（开关控制是否在日历中添加提醒）
            Spacer(modifier = Modifier.height(12.dp))

            // 日历课前提醒开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("日历课前提醒")
                }
                Switch(
                    checked = settings.calendarBeforeClassReminderEnabled,
                    onCheckedChange = { enabled ->
                        AppLogger.d("SettingsDrawer", "[v105] 日历课前提醒开关变化: $enabled")
                        onSettingsChange(settings.copy(calendarBeforeClassReminderEnabled = enabled))
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 日历早八提醒开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("日历早八提醒")
                }
                Switch(
                    checked = settings.calendarEarlyMorningReminderEnabled,
                    onCheckedChange = { enabled ->
                        AppLogger.d("SettingsDrawer", "[v105] 日历早八提醒开关变化: $enabled")
                        onSettingsChange(settings.copy(calendarEarlyMorningReminderEnabled = enabled))
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "开关变更后需点击同步才会生效",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // [v100] 同步按钮（带状态）
            OutlinedButton(
                onClick = onCalendarSyncClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = calendarSyncState !is CalendarSyncState.Syncing  // 同步中禁用按钮
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
                        Icon(Icons.Filled.Edit, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("同步课程到日历")
                    }
                }
            }

            // [v100] 同步状态提示
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

            // [v100] 删除日历按钮（带状态）
            OutlinedButton(
                onClick = onDeleteCalendarClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = calendarSyncState !is CalendarSyncState.Deleting  // 删除中禁用按钮
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
                        Icon(Icons.Filled.Close, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("删除日历事件")
                    }
                }
            }

            // [v100] 删除状态提示
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

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // 通知设置
        Text(
            text = "通知设置",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 提醒声音
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("提醒声音")
            }
            Switch(
                checked = settings.reminderSoundEnabled,
                onCheckedChange = { enabled ->
                    AppLogger.d("SettingsDrawer", "[Debug] 声音开关变化: $enabled")
                    onSettingsChange(settings.copy(reminderSoundEnabled = enabled))
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 振动
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("振动")
            }
            Switch(
                checked = settings.reminderVibrationEnabled,
                onCheckedChange = { enabled ->
                    AppLogger.d("SettingsDrawer", "[Debug] 振动开关变化: $enabled")
                    onSettingsChange(settings.copy(reminderVibrationEnabled = enabled))
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * 水平分隔线
 */
@Composable
private fun HorizontalDivider(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    ) {}
}

/**
 * 提醒设置区块
 * [v106] 时间编辑器始终显示，不依赖开关状态
 */
@Composable
private fun ReminderSection(
    title: String,
    description: String,
    icon: ImageVector,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (enabled)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (enabled)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange
            )
        }

        // [v106] 时间编辑器始终显示，使用视觉提示区分启用/禁用状态
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = if (enabled)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                content()
            }
        }
    }
}

/**
 * [v106] 现代化时间选择器行
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerRow(
    hour: Int,
    minute: Int,
    enabled: Boolean = true,
    onTimeChange: (Int, Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    // 现代化时间显示卡片
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { showDialog = true },
        shape = RoundedCornerShape(12.dp),
        color = if (enabled)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        tonalElevation = if (enabled) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    tint = if (enabled)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "提醒时间",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 现代化时间显示
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (enabled)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outline
            ) {
                Text(
                    text = String.format("%02d:%02d", hour, minute),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.surface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }

    if (showDialog) {
        ModernTimePickerDialog(
            initialHour = hour,
            initialMinute = minute,
            onDismiss = { showDialog = false },
            onConfirm = { h, m ->
                onTimeChange(h, m)
                showDialog = false
            }
        )
    }
}

/**
 * [v106] 现代化 Material 3 时间选择对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    // 使用当前时间作为默认值，然后设置初始值
    val calendar = Calendar.getInstance()
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "选择提醒时间",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Material 3 TimePicker
                TimePicker(
                    state = timePickerState,
                    modifier = Modifier.padding(vertical = 16.dp),
                    colors = TimePickerDefaults.colors(
                        clockDialColor = MaterialTheme.colorScheme.surfaceVariant,
                        selectorColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "将在设定时间检查第二天第1-2节是否有课",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(timePickerState.hour, timePickerState.minute) }
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * [v106] 现代化上课前时间选择组件 - 使用 Slider
 */
@Composable
private fun BeforeClassTimeSelector(
    currentValue: Int,
    enabled: Boolean = true,
    onValueChange: (Int) -> Unit
) {
    // 预设选项
    val presetOptions = listOf(5, 10, 15, 20, 30, 45, 60)
    val selectedIndex = presetOptions.indexOf(currentValue).coerceAtLeast(0)

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 快捷选择按钮
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = if (enabled)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                            tint = if (enabled)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "提前时间",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (enabled)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 当前值显示
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (enabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outline
                    ) {
                        Text(
                            text = "${currentValue}分钟",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (enabled)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.surface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 现代化 Slider
                Slider(
                    value = selectedIndex.toFloat(),
                    onValueChange = { index ->
                        if (enabled) {
                            onValueChange(presetOptions[index.toInt()])
                        }
                    },
                    valueRange = 0f..(presetOptions.size - 1).toFloat(),
                    steps = presetOptions.size - 2,
                    enabled = enabled,
                    colors = SliderDefaults.colors(
                        thumbColor = if (enabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outline,
                        activeTrackColor = if (enabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outline,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // 选项标签 - 显示所有 7 个选项
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    presetOptions.forEach { minutes ->
                        Text(
                            text = "${minutes}分",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (currentValue == minutes && enabled)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "将在上课前提醒即将开始的课程",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * [权限状态] 权限状态卡片
 * 显示各项权限的授权状态和请求按钮
 */
@Composable
private fun PermissionStatusCard(
    hasNotificationPermission: Boolean,
    hasCalendarPermission: Boolean,
    hasExactAlarmPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onRequestCalendarPermission: () -> Unit,
    onRequestExactAlarmPermission: () -> Unit,
    onRequestAllPermissions: () -> Unit  // [权限管理] 一键获取所需权限
) {
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "权限状态",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 通知权限
            PermissionRow(
                icon = Icons.Filled.Warning,
                label = "通知权限",
                isGranted = hasNotificationPermission,
                onClick = if (!hasNotificationPermission) onRequestNotificationPermission else null
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 日历权限
            PermissionRow(
                icon = Icons.Filled.Edit,
                label = "日历权限",
                isGranted = hasCalendarPermission,
                onClick = if (!hasCalendarPermission) onRequestCalendarPermission else null
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 精确闹钟权限
            PermissionRow(
                icon = Icons.Filled.Warning,
                label = "精确闹钟",
                isGranted = hasExactAlarmPermission,
                onClick = if (!hasExactAlarmPermission) onRequestExactAlarmPermission else null
            )

            // 如果有缺失权限，显示提示和一键获取按钮
            val missingCount = listOf(
                hasNotificationPermission,
                hasCalendarPermission,
                hasExactAlarmPermission
            ).count { !it }

            if (missingCount > 0) {
                Spacer(modifier = Modifier.height(12.dp))

                // [权限管理] 一键获取所需权限按钮（当缺失权限 > 1 时显示）
                if (missingCount > 1) {
                    Button(
                        onClick = onRequestAllPermissions,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "一键获取所需权限 ($missingCount)",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text(
                    text = if (missingCount == 1) "点击未授权项可请求对应权限" else "点击按钮可依次请求所有缺失权限",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * [权限状态] 权限状态行
 */
@Composable
private fun PermissionRow(
    icon: ImageVector,
    label: String,
    isGranted: Boolean,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier
                        .clickable { onClick() }
                        .padding(vertical = 4.dp)
                } else {
                    Modifier.padding(vertical = 4.dp)
                }
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isGranted)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isGranted)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.error
            )
        }

        // 状态图标
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = if (isGranted)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Filled.Check else Icons.Filled.Close,
                    contentDescription = if (isGranted) "已授权" else "未授权",
                    modifier = Modifier.size(14.dp),
                    tint = if (isGranted)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isGranted) "已授权" else "未授权",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isGranted)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
