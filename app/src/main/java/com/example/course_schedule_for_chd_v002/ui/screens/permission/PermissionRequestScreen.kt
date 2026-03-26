package com.example.course_schedule_for_chd_v002.ui.screens.permission

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

/**
 * [权限管理] 权限请求引导页
 *
 * 首次启动时显示，引导用户授权必要权限
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionRequestScreen(
    onPermissionsHandled: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // 权限状态
    var hasNotification by remember { mutableStateOf(checkNotificationPermission(context)) }
    var hasCalendar by remember { mutableStateOf(checkCalendarPermission(context)) }
    var hasExactAlarm by remember { mutableStateOf(checkExactAlarmPermission(context)) }

    // 当前请求阶段
    var currentRequestStep by remember { mutableStateOf<PermissionStep?>(null) }

    // 通知权限请求 Launcher
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        android.util.Log.d("PermissionRequestScreen", "[权限] 通知权限结果: $isGranted")
        hasNotification = isGranted
        currentRequestStep = null
    }

    // 日历权限请求 Launcher
    val calendarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        android.util.Log.d("PermissionRequestScreen", "[权限] 日历权限结果: $allGranted")
        hasCalendar = allGranted
        currentRequestStep = null
    }

    // 精确闹钟设置返回监听
    val exactAlarmLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        android.util.Log.d("PermissionRequestScreen", "[权限] 从精确闹钟设置返回")
        hasExactAlarm = checkExactAlarmPermission(context)
        currentRequestStep = null
    }

    // 刷新权限状态
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            hasNotification = checkNotificationPermission(context)
            hasCalendar = checkCalendarPermission(context)
            hasExactAlarm = checkExactAlarmPermission(context)
        }
    }

    // 请求通知权限
    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            currentRequestStep = PermissionStep.NOTIFICATION
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // 请求日历权限
    fun requestCalendarPermission() {
        currentRequestStep = PermissionStep.CALENDAR
        calendarLauncher.launch(
            arrayOf(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR
            )
        )
    }

    // 请求精确闹钟权限
    fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            currentRequestStep = PermissionStep.EXACT_ALARM
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            exactAlarmLauncher.launch(intent)
        }
    }

    // 一键请求所有权限（依次请求）
    fun requestAllPermissions() {
        when {
            !hasNotification -> requestNotificationPermission()
            !hasCalendar -> requestCalendarPermission()
            !hasExactAlarm -> requestExactAlarmPermission()
        }
    }

    // 检查是否所有权限都已处理
    val allHandled = hasNotification && hasCalendar && hasExactAlarm
    val missingCount = listOf(hasNotification, hasCalendar, hasExactAlarm).count { !it }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("权限设置") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 图标
            Icon(
                imageVector = Icons.Default.Lock,  // 使用 Lock 作为安全图标
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 标题
            Text(
                text = "需要权限才能使用全部功能",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 说明
            Text(
                text = "为了提供课程提醒和日历同步功能，应用需要以下权限",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 权限列表
            PermissionItem(
                icon = Icons.Default.Notifications,
                title = "通知权限",
                description = "用于发送课程提醒通知",
                isGranted = hasNotification,
                isRequesting = currentRequestStep == PermissionStep.NOTIFICATION,
                onClick = { if (!hasNotification) requestNotificationPermission() }
            )

            Spacer(modifier = Modifier.height(12.dp))

            PermissionItem(
                icon = Icons.Default.DateRange,
                title = "日历权限",
                description = "用于同步课程到系统日历",
                isGranted = hasCalendar,
                isRequesting = currentRequestStep == PermissionStep.CALENDAR,
                onClick = { if (!hasCalendar) requestCalendarPermission() }
            )

            Spacer(modifier = Modifier.height(12.dp))

            PermissionItem(
                icon = Icons.Default.Info,  // 使用 Info 替代 Alarm
                title = "精确闹钟权限",
                description = "用于在指定时间发送提醒",
                isGranted = hasExactAlarm,
                isRequesting = currentRequestStep == PermissionStep.EXACT_ALARM,
                onClick = { if (!hasExactAlarm) requestExactAlarmPermission() }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 状态显示和按钮
            if (allHandled) {
                // 所有权限已授权
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "所有权限已授权",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 开始使用按钮
                Button(
                    onClick = onPermissionsHandled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ArrowForward, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("开始使用")
                }
            } else {
                // 一键授权按钮
                Button(
                    onClick = { requestAllPermissions() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = currentRequestStep == null
                ) {
                    if (currentRequestStep != null) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("正在请求权限...")
                    } else {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("一键获取所需权限 ($missingCount)")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "点击后将依次请求各个权限",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 跳过按钮
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(
                onClick = onPermissionsHandled
            ) {
                Text("暂时跳过")
            }
        }
    }
}

/**
 * 权限项组件
 */
@Composable
private fun PermissionItem(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    isRequesting: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (!isGranted && !isRequesting) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(12.dp),
        color = if (isGranted)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else
            MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isGranted) "已授权" else description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isGranted)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 状态图标
            if (isRequesting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = if (isGranted)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 权限请求步骤
 */
private enum class PermissionStep {
    NOTIFICATION,
    CALENDAR,
    EXACT_ALARM
}

/**
 * 检查通知权限
 */
private fun checkNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true // Android 13 以下不需要运行时权限
    }
}

/**
 * 检查日历权限
 */
private fun checkCalendarPermission(context: Context): Boolean {
    val hasRead = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_CALENDAR
    ) == PackageManager.PERMISSION_GRANTED
    val hasWrite = ContextCompat.checkSelfPermission(
        context, Manifest.permission.WRITE_CALENDAR
    ) == PackageManager.PERMISSION_GRANTED
    return hasRead && hasWrite
}

/**
 * 检查精确闹钟权限
 */
private fun checkExactAlarmPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.canScheduleExactAlarms()
    } else {
        true
    }
}
