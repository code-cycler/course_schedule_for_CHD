package com.example.course_schedule_for_chd_v002.ui.screens.checkinassist

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.ArrowDropDown
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
import kotlinx.coroutines.launch
import com.example.course_schedule_for_chd_v002.domain.model.CheckInLocation
import com.example.course_schedule_for_chd_v002.util.AppLogger
import org.koin.androidx.compose.koinViewModel

/**
 * [v114] 签到辅助主界面：位置列表 + 设置区 + 权限状态 + 模拟触发
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CheckInAssistScreen(
    onBack: () -> Unit,
    onNavigateToConnect: () -> Unit = {},
    viewModel: CheckInAssistViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // [v128] 自启动回报确认框的 UI 侧开关（ON_RESUME 时若 ViewModel 标记待确认则弹）
    var showAutoStartConfirm by remember { mutableStateOf(false) }

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
                viewModel.checkNlsHealth()
                // [v128] 刚从自启动设置页回来 → 弹回报确认框（无 API 可查，只能问用户）
                if (viewModel.uiState.value.autoStartConfirmPending) showAutoStartConfirm = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // [v126] 位置数据导入（换机/重装迁移，ACTION_OPEN_DOCUMENT 选 JSON；结果走 message 提示）
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importLocations) }

    // [v127] 位置数据导出（SAF 另存为：用户亲自选保存位置，与导入对称；不再写私有目录+分享面板）
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::exportLocationsTo) }

    // 编辑器
    if (state.editorOpen) {
        LocationEditorSheet(
            editing = state.editing,
            courseRoomMap = state.courseRoomMap,
            onSave = viewModel::saveLocation,
            onDismiss = viewModel::closeEditor
        )
    }

    // [v126] 畅课通知原文记录查看（最近 50 条，长按可复制）
    if (state.notificationLogOpen) {
        AlertDialog(
            onDismissRequest = viewModel::closeNotificationLog,
            title = { Text("畅课通知记录（最近 ${state.notificationLog.size} 条）") },
            text = {
                Column {
                    if (state.notificationLog.isEmpty()) {
                        Text("还没有收到过畅课通知（含营销/普通推送都会记录在此）")
                    } else {
                        LazyColumn(modifier = Modifier.height(360.dp)) {
                            items(state.notificationLog.reversed()) { record ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {},
                                            onLongClick = {
                                                val cm = context.getSystemService(
                                                    android.content.Context.CLIPBOARD_SERVICE
                                                ) as android.content.ClipboardManager
                                                cm.setPrimaryClip(
                                                    android.content.ClipData.newPlainText(
                                                        "畅课通知",
                                                        "${record.title.orEmpty()} ${record.text.orEmpty()}"
                                                    )
                                                )
                                            }
                                        )
                                        .padding(vertical = 6.dp)
                                ) {
                                    Text(
                                        record.title.orEmpty().ifBlank { "（无标题）" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        record.text.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "${java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault())
                                            .format(java.util.Date(record.timestamp))} · 渠道 ${record.channelId.orEmpty().ifBlank { "-" }}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Text(
                            "长按单条复制原文（关键字校准用）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::closeNotificationLog) { Text("关闭") }
            }
        )
    }

    // [v128] 自启动回报确认（从系统「应用启动管理」回来后弹；无标准 API 可查，只能问用户）
    if (showAutoStartConfirm) {
        AlertDialog(
            onDismissRequest = {
                showAutoStartConfirm = false
                viewModel.dismissAutoStartConfirm()
            },
            title = { Text("自启动开启了吗？") },
            text = {
                Text(
                    "在系统的自启动/应用启动管理里找到本应用，允许自启动（部分厂商需关闭「自动管理」改手动管理）。" +
                        "开启后手机重启时监听才能自动恢复。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showAutoStartConfirm = false
                    viewModel.answerAutoStartConfirm(true)
                }) { Text("已开启") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAutoStartConfirm = false
                    viewModel.answerAutoStartConfirm(false)
                }) { Text("还没开启") }
            }
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
            // ===== [v126] NLS 疑似未绑定警示条（>24h 无心跳） =====
            if (state.nlsSuspectUnbound) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "⚠ 通知监听可能未生效",
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            "超过 24 小时没有收到通知心跳。可尝试重连，或在「通知使用权」里关掉本应用再重新打开。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { viewModel.verifyNlsBinding() }) { Text("验证监听") }
                            TextButton(onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }) { Text("去设置") }
                        }
                    }
                }
            }

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
            // [v126] 电池优化白名单（荣耀省电管控杀后台风险；点击直申系统对话框，失败跳设置列表）
            PermissionStatusRow(
                ok = state.batteryWhitelisted,
                label = "电池优化白名单",
                hint = if (state.batteryWhitelisted) "已豁免，后台不易被杀" else "点击申请「忽略电池优化」，降低监听被系统杀掉的概率",
                onClick = {
                    if (context is android.app.Activity) {
                        if (!com.example.course_schedule_for_chd_v002.util.PowerSaveWhitelist.request(context)) {
                            com.example.course_schedule_for_chd_v002.util.PowerSaveWhitelist.openSettings(context)
                        }
                        // 回到前台后 refreshPermissions 会刷新状态
                    }
                }
            )
            // [v128→v130] 自启动/后台启动（各厂商私有设置无查询 API：深链直达，回前台回报确认）
            PermissionStatusRow(
                ok = state.settings.autoStartConfirmed,
                label = "自启动/后台启动",
                hint = if (state.settings.autoStartConfirmed) {
                    "已记录开启；系统更新可能重置该设置，建议点进来复查"
                } else {
                    "点击进入系统设置检查：本应用的自启动是否已允许（厂商私有设置，App 无法自动检测）"
                },
                onClick = {
                    val opened = com.example.course_schedule_for_chd_v002.util.AutoStartLauncher.openSettings(context)
                    if (opened) viewModel.onAutoStartSettingVisited()
                    else viewModel.notifyMessage("无法直达自启动设置，请在应用详情页手动查找")
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

            // ===== [v129] 轮询通道（B5 第二监听：课时有窗时主动查畅课签到）=====
            Text("轮询通道（第二监听）", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (!state.tronClassConnected) {
                OutlinedButton(
                    onClick = onNavigateToConnect,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("连接畅课账号（轮询前置）") }
                Text(
                    "畅课通知被吞/文案不命中时的兜底通道：登录一次畅课，上课时段自动查询签到状态",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("畅课已连接")
                    TextButton(onClick = viewModel::disconnectTronClass) { Text("断开") }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("课时有窗轮询畅课签到")
                        Text(
                            "课前 10 分钟至课后 5 分钟，每 60 秒查一次；无课时段不请求",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.pollingEnabled,
                        onCheckedChange = viewModel::setPollingEnabled
                    )
                }
            }

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
                    onClick = viewModel::stopMock,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("■  停止虚拟定位", color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(Modifier.height(20.dp))

            // ===== [v126] 测试与调试（全部手机端自助，不依赖电脑） =====
            Text("测试与调试", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = viewModel::sendTestNotification,
                    modifier = Modifier.weight(1f)
                ) { Text("端到端测试通知") }
                OutlinedButton(
                    onClick = viewModel::verifyNlsBinding,
                    modifier = Modifier.weight(1f)
                ) { Text("验证监听绑定") }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = viewModel::openNotificationLog,
                    modifier = Modifier.weight(1f)
                ) { Text("畅课通知记录（${state.notificationLog.size}）") }
                OutlinedButton(
                    onClick = {
                        val ts = java.text.SimpleDateFormat(
                            "yyyyMMdd_HHmmss", java.util.Locale.getDefault()
                        ).format(java.util.Date())
                        exportLauncher.launch("checkin_locations_$ts.json")
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("位置导出") }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json")) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("位置导入（换机迁移）") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val result = com.example.course_schedule_for_chd_v002.util.LogExporter.exportLogs(context)
                        if (result.success) {
                            viewModel.notifyMessage("日志已导出：${result.file?.name}（${result.formattedSize}）")
                        } else {
                            viewModel.notifyMessage("日志导出失败：${result.error ?: "未知错误"}")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("导出日志（含触发管线事件）") }

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

            // [v126] 位置选用下拉（签到位置改下拉：选已有位置立即使用；新建走右上角 +）
            if (state.locations.isNotEmpty()) {
                var useMenuExpanded by remember { mutableStateOf(false) }
                val selectedName = state.locations
                    .firstOrNull { it.id == state.selectedLocationId }?.name ?: "选择要使用的位置"
                androidx.compose.material3.ExposedDropdownMenuBox(
                    expanded = useMenuExpanded,
                    onExpandedChange = { useMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedName,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("签到位置") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        trailingIcon = {
                            IconButton(onClick = { useMenuExpanded = !useMenuExpanded }) {
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = "选择位置")
                            }
                        }
                    )
                    androidx.compose.material3.DropdownMenu(
                        expanded = useMenuExpanded,
                        onDismissRequest = { useMenuExpanded = false },
                        modifier = Modifier.exposedDropdownSize()
                    ) {
                        state.locations.forEach { loc ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(loc.name) },
                                onClick = {
                                    viewModel.onSelectedLocationChanged(loc.id)
                                    useMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = viewModel::useSelectedLocation,
                    enabled = state.selectedLocationId != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("使用该位置（立即触发）")
                }
                Spacer(Modifier.height(8.dp))
            }

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
                    loc.linkedRoomNames.takeIf { it.isNotEmpty() }?.joinToString("/")
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
