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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.example.course_schedule_for_chd_v002.domain.model.ReminderSettings

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
    android.util.Log.d("SettingsDrawer", "SettingsDrawer recomposed, drawerState.isOpen=${drawerState.isOpen}, settings=$settings, hasNotification=$hasNotificationPermission, hasCalendar=$hasCalendarPermission, hasExactAlarm=$hasExactAlarmPermission")

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
                imageVector = Icons.Default.Settings,
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
            icon = Icons.Default.Warning,
            enabled = settings.earlyMorningReminderEnabled,
            onEnabledChange = { enabled ->
                onSettingsChange(settings.copy(earlyMorningReminderEnabled = enabled))
            }
        ) {
            // 时间选择器
            TimePickerRow(
                hour = settings.earlyMorningReminderHour,
                minute = settings.earlyMorningReminderMinute,
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

        // 上课前提醒设置
        ReminderSection(
            title = "上课前提醒",
            description = "上课前提前通知",
            icon = Icons.Default.Notifications,
            enabled = settings.beforeClassReminderEnabled,
            onEnabledChange = { enabled ->
                onSettingsChange(settings.copy(beforeClassReminderEnabled = enabled))
            }
        ) {
            var showTimeDialog by remember { mutableStateOf(false) }

            OutlinedButton(
                onClick = { showTimeDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("上课前 ${settings.beforeClassReminderMinutes} 分钟")
            }

            if (showTimeDialog) {
                BeforeClassTimeDialog(
                    currentValue = settings.beforeClassReminderMinutes,
                    onDismiss = { showTimeDialog = false },
                    onConfirm = { minutes ->
                        onSettingsChange(settings.copy(beforeClassReminderMinutes = minutes))
                        showTimeDialog = false
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "将在上课前提醒即将开始的课程",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // 系统日历集成
        ReminderSection(
            title = "系统日历",
            description = "同步课程到手机日历",
            icon = Icons.Default.DateRange,
            enabled = settings.calendarSyncEnabled,
            onEnabledChange = { enabled ->
                if (enabled) {
                    // 请求日历权限
                    onRequestCalendarPermission()
                }
                onSettingsChange(settings.copy(calendarSyncEnabled = enabled))
            }
        ) {
            // [v98] 同步按钮
            OutlinedButton(
                onClick = onCalendarSyncClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.DateRange, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("同步课程到日历")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // [v98] 删除日历按钮
            OutlinedButton(
                onClick = onDeleteCalendarClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("删除日历事件")
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
                    Icons.Default.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("提醒声音")
            }
            Switch(
                checked = settings.reminderSoundEnabled,
                onCheckedChange = { enabled ->
                    android.util.Log.d("SettingsDrawer", "[Debug] 声音开关变化: $enabled")
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
                    Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("振动")
            }
            Switch(
                checked = settings.reminderVibrationEnabled,
                onCheckedChange = { enabled ->
                    android.util.Log.d("SettingsDrawer", "[Debug] 振动开关变化: $enabled")
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
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium
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

        if (enabled) {
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

/**
 * 时间选择器行
 */
@Composable
private fun TimePickerRow(
    hour: Int,
    minute: Int,
    onTimeChange: (Int, Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = { showDialog = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Settings, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(String.format("%02d:%02d", hour, minute))
    }

    if (showDialog) {
        TimePickerDialog(
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
 * 时间选择对话框
 */
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    var selectedHour by remember { mutableIntStateOf(initialHour) }
    var selectedMinute by remember { mutableIntStateOf(initialMinute) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择提醒时间") },
        text = {
            Column {
                // 小时选择
                Text(
                    text = "小时",
                    style = MaterialTheme.typography.labelMedium
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    (0..23).forEach { h ->
                        FilterChip(
                            selected = selectedHour == h,
                            onClick = { selectedHour = h },
                            label = { Text(String.format("%02d", h)) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 分钟选择
                Text(
                    text = "分钟",
                    style = MaterialTheme.typography.labelMedium
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    (0..59 step 5).forEach { m ->
                        FilterChip(
                            selected = selectedMinute == m,
                            onClick = { selectedMinute = m },
                            label = { Text(String.format("%02d", m)) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedHour, selectedMinute) }) {
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
 * 上课前时间选择对话框
 */
@Composable
private fun BeforeClassTimeDialog(
    currentValue: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val options = listOf(5, 10, 15, 20, 30, 45, 60)
    var selected by remember { mutableIntStateOf(currentValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择提醒时间") },
        text = {
            Column {
                options.forEach { minutes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = minutes }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected == minutes,
                            onClick = { selected = minutes }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("上课前 $minutes 分钟")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) {
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
 * 过滤芯片
 */
@Composable
private fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            label()
        }
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
                icon = Icons.Default.Notifications,
                label = "通知权限",
                isGranted = hasNotificationPermission,
                onClick = if (!hasNotificationPermission) onRequestNotificationPermission else null
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 日历权限
            PermissionRow(
                icon = Icons.Default.DateRange,
                label = "日历权限",
                isGranted = hasCalendarPermission,
                onClick = if (!hasCalendarPermission) onRequestCalendarPermission else null
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 精确闹钟权限
            PermissionRow(
                icon = Icons.Default.Warning,
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
                            imageVector = Icons.Default.CheckCircle,
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
                    imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Close,
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
