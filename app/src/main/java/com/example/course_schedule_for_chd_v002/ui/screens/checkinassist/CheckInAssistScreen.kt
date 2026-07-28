package com.example.course_schedule_for_chd_v002.ui.screens.checkinassist

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.course_schedule_for_chd_v002.domain.model.CheckInLocation
import com.example.course_schedule_for_chd_v002.util.AppLogger
import org.koin.androidx.compose.koinViewModel

/**
 * [v114] 签到辅助主界面：位置列表 + 设置区 + 权限状态 + 模拟触发
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInAssistScreen(
    onBack: () -> Unit,
    viewModel: CheckInAssistViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // [v114] 跳转本应用详情页（权限被永久拒绝时，运行时申请器不弹窗，统一跳设置页）
    val openAppDetailsSettings = {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(android.net.Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    // 一次性提示
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    // 运行时权限请求：定位 + 通知
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.refreshPermissions()
    }
    // 进入页面时请求一次
    LaunchedEffect(Unit) {
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    // [v114] 回到前台时刷新权限状态——模拟定位应用、通知使用权是在外部设置页改的，
    // 运行时权限申请器管不到，需在 ON_RESUME 重新检测
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 编辑器
    if (state.editorOpen) {
        LocationEditorSheet(
            editing = state.editing,
            onSave = viewModel::saveLocation,
            onDismiss = viewModel::closeEditor
        )
    }

    // 手动选位置
    if (state.pendingManualSelection.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = viewModel::dismissManualSelection,
            title = { Text("选择签到位置") },
            text = {
                Column {
                    state.pendingManualSelection.forEach { loc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onManualSelected(loc) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.LocationOn, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text(loc.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissManualSelection) { Text("取消") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("签到辅助") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ===== 权限状态区 =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("权限状态", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { viewModel.refreshPermissions() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新权限状态")
                }
            }
            Spacer(Modifier.height(8.dp))
            PermissionStatusRow(
                ok = state.mockAppEnabled,
                label = "模拟位置应用",
                hint = "开发者选项 → 选择模拟位置信息应用 → 选本应用",
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            )
            PermissionStatusRow(
                ok = state.locationPermissionGranted,
                label = "定位权限",
                hint = "点击跳转应用详情，在「权限」中开启定位",
                onClick = { openAppDetailsSettings() }
            )
            PermissionStatusRow(
                ok = state.notificationPermissionGranted,
                label = "通知权限",
                hint = "点击跳转应用详情，开启通知",
                onClick = { openAppDetailsSettings() }
            )
            PermissionStatusRow(
                ok = state.notificationListenerEnabled,
                label = "通知使用权（监听签到）",
                hint = "设置 → 通知 → 通知使用权 → 开启本应用",
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            )

            Spacer(Modifier.height(16.dp))

            // ===== 设置区 =====
            Text("设置", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("收到签到触发后自动打开畅课")
                Switch(
                    checked = state.settings.autoOpenChaoqing,
                    onCheckedChange = { viewModel.updateSettings(autoOpenChaoqing = it) }
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("虚拟定位保持时长：${state.settings.mockDurationMinutes} 分钟")
            Slider(
                value = state.settings.mockDurationMinutes.toFloat(),
                onValueChange = { viewModel.updateSettings(mockDurationMinutes = it.toInt()) },
                valueRange = 1f..10f,
                steps = 8
            )

            Spacer(Modifier.height(8.dp))
            // 签到通知关键字（可配置）：本地 state 避免 DataStore 往返导致光标跳动
            var keywordsInput by remember { mutableStateOf<String?>(null) }
            OutlinedTextField(
                value = keywordsInput ?: state.keywordsText,
                onValueChange = {
                    keywordsInput = it
                    viewModel.updateKeywords(it)
                },
                label = { Text("签到通知关键字（逗号分隔）") },
                placeholder = { Text("签到,考勤,点名") },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "命中任一关键字即视为签到通知；开学收到真实签到后可按实际文案调整",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            // ===== 模拟触发 =====
            Button(
                onClick = {
                    viewModel.refreshPermissions()
                    viewModel.triggerMock()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.LocationOn, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("模拟签到触发")
            }
            // [v114] 手动停止虚拟定位（会话激活时可用）
            if (state.mockLocationActive) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.stopMock() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("■  停止虚拟定位", color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(Modifier.height(20.dp))

            // ===== 位置列表 =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("签到位置（${state.locations.size}）", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { viewModel.openEditor(null) }) {
                    Icon(Icons.Filled.Add, contentDescription = "新增")
                }
            }
            Spacer(Modifier.height(8.dp))

            if (state.locations.isEmpty()) {
                Text(
                    "还没有签到位置，点右上角 + 添加一个",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.locations.forEach { loc -> LocationCard(loc, viewModel) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LocationCard(loc: CheckInLocation, viewModel: CheckInAssistViewModel) {
    var confirmDelete by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(loc.name, fontWeight = FontWeight.Medium)
                Text(
                    "%.6f, %.6f".format(loc.latitude, loc.longitude),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val assoc = listOfNotNull(
                    loc.linkedCourseId?.takeIf { it.isNotBlank() },
                    loc.linkedRoomName?.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                if (assoc.isNotEmpty()) {
                    Text(
                        "关联：$assoc",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = { viewModel.openEditor(loc) }) {
                Icon(Icons.Filled.Edit, contentDescription = "编辑")
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除「${loc.name}」？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteLocation(loc.id)
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
}
