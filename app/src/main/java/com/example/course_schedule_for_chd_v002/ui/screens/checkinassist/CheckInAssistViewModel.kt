package com.example.course_schedule_for_chd_v002.ui.screens.checkinassist

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.course_schedule_for_chd_v002.data.local.preferences.UserPreferences
import com.example.course_schedule_for_chd_v002.domain.model.CheckInLocation
import com.example.course_schedule_for_chd_v002.domain.repository.ICheckInLocationRepository
import com.example.course_schedule_for_chd_v002.service.checkin.CheckInTriggerCoordinator
import com.example.course_schedule_for_chd_v002.service.checkin.CheckInTriggerCoordinator.TriggerResult
import com.example.course_schedule_for_chd_v002.service.mocklocation.MockLocationController
import com.example.course_schedule_for_chd_v002.service.mocklocation.MockLocationService
import com.example.course_schedule_for_chd_v002.util.PowerSaveWhitelist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * [v114] 签到辅助 ViewModel
 *
 * 触发逻辑（选位置 + 启动 Mock + 可选打开畅课）委托给 [CheckInTriggerCoordinator]，
 * 与后台通知监听服务共用同一套逻辑；本 ViewModel 只负责把 [TriggerResult] 映射到 UI 状态。
 */
class CheckInAssistViewModel(
    private val app: Application,
    private val locationRepository: ICheckInLocationRepository,
    private val userPreferences: UserPreferences,
    private val triggerCoordinator: CheckInTriggerCoordinator,
    private val courseRepository: com.example.course_schedule_for_chd_v002.domain.repository.ICourseRepository,
    private val nlsHealth: com.example.course_schedule_for_chd_v002.service.checkin.NlsHealthMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow(CheckInAssistUiState())
    val uiState: StateFlow<CheckInAssistUiState> = _uiState.asStateFlow()

    init {
        observeLocations()
        observeSettings()
        observeKeywords()
        observeNotificationLog()
        refreshPermissions()
        loadCourseRoomMap()
        checkNlsHealth()
    }

    private fun observeLocations() {
        viewModelScope.launch {
            locationRepository.observeAll().collect { list ->
                _uiState.update { it.copy(locations = list) }
            }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            userPreferences.checkInAssistSettings.collect { s ->
                _uiState.update { it.copy(settings = s) }
            }
        }
    }

    private fun observeKeywords() {
        viewModelScope.launch {
            userPreferences.checkInKeywordsRaw.collect { raw ->
                _uiState.update { it.copy(keywordsText = raw) }
            }
        }
    }

    /** [v126] 畅课通知原文记录（最近 50 条） */
    private fun observeNotificationLog() {
        viewModelScope.launch {
            userPreferences.tronClassNotificationLog.collect { log ->
                _uiState.update { it.copy(notificationLog = log) }
            }
        }
    }

    /** [v126] 课程 → 教室并集（当前学期；课程下拉数据源，选课预填教室 tag） */
    private fun loadCourseRoomMap() {
        viewModelScope.launch {
            val map = runCatching {
                courseRepository.getCurrentSemester()
                    ?.let { courseRepository.getCourseRoomUnion(it) } ?: emptyMap()
            }.getOrDefault(emptyMap())
            _uiState.update { it.copy(courseRoomMap = map) }
        }
    }

    /** [v126] NLS 健康检查（>24h 无心跳 → 警示条） */
    fun checkNlsHealth() {
        viewModelScope.launch {
            val suspect = runCatching { nlsHealth.isSuspectUnbound() }.getOrDefault(false)
            _uiState.update { it.copy(nlsSuspectUnbound = suspect) }
        }
    }

    /** 刷新各项权限的实际状态（从系统查询） */
    fun refreshPermissions() {
        val ctx: Context = app
        _uiState.update {
            it.copy(
                mockAppEnabled = MockLocationController.isMockLocationAppEnabled(ctx),
                locationPermissionGranted = ContextCompat.checkSelfPermission(
                    ctx, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED,
                notificationPermissionGranted = notificationGranted(ctx),
                notificationListenerEnabled = isNotificationListenerEnabled(ctx),
                batteryWhitelisted = PowerSaveWhitelist.isIgnored(ctx),
                mockLocationActive = MockLocationService.isRunning
            )
        }
    }

    private fun notificationGranted(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** 本应用是否被授予「通知使用权」（NotificationListenerService 能否收到通知） */
    private fun isNotificationListenerEnabled(ctx: Context): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    }

    // ================ 位置 CRUD ================

    fun openEditor(editing: CheckInLocation? = null) {
        _uiState.update { it.copy(editorOpen = true, editing = editing) }
    }

    fun closeEditor() {
        _uiState.update { it.copy(editorOpen = false, editing = null) }
    }

    fun saveLocation(location: CheckInLocation) {
        viewModelScope.launch {
            if (location.id == 0L) {
                locationRepository.insert(location)
                _uiState.update { it.copy(message = "已添加：${location.name}") }
            } else {
                locationRepository.update(location)
                _uiState.update { it.copy(message = "已更新：${location.name}") }
            }
            closeEditor()
        }
    }

    fun deleteLocation(id: Long) {
        viewModelScope.launch {
            locationRepository.deleteById(id)
            _uiState.update { it.copy(message = "已删除") }
        }
    }

    fun updateSettings(autoOpenChaoqing: Boolean? = null, mockDurationMinutes: Int? = null) {
        viewModelScope.launch {
            val current = _uiState.value.settings
            val updated = current.copy(
                autoOpenChaoqing = autoOpenChaoqing ?: current.autoOpenChaoqing,
                mockDurationMinutes = mockDurationMinutes ?: current.mockDurationMinutes
            )
            userPreferences.saveCheckInAssistSettings(updated)
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    /** [v126] 通用一次性提示（测试入口按钮结果反馈） */
    fun notifyMessage(text: String) = _uiState.update { it.copy(message = text) }

    /** 保存签到通知识别关键字（逗号分隔原始串） */
    fun updateKeywords(raw: String) {
        viewModelScope.launch {
            userPreferences.saveCheckInKeywords(raw)
        }
    }

    // ================ 触发 Mock 定位 ================

    /** 模拟触发按钮 / 真实通知（第二阶段）入口 */
    fun triggerMock() {
        viewModelScope.launch {
            applyResult(triggerCoordinator.trigger())
        }
    }

    /** [v126] 位置选用下拉：选中变化 */
    fun onSelectedLocationChanged(id: Long?) {
        _uiState.update { it.copy(selectedLocationId = id) }
    }

    /** [v126] 使用下拉选中的位置立即触发（走 startForLocation：记忆 + Mock） */
    fun useSelectedLocation() {
        val id = _uiState.value.selectedLocationId ?: return
        val loc = _uiState.value.locations.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            applyResult(triggerCoordinator.startForLocation(loc))
        }
    }

    /** [v126] 端到端测试通知（走真实「通知 → 匹配 → 触发」管线） */
    fun sendTestNotification() {
        com.example.course_schedule_for_chd_v002.service.checkin.CheckInTestTools
            .sendSelfTestNotification(app)
        _uiState.update { it.copy(message = "已发送测试通知（走真实管线），观察触发结果与通知") }
    }

    /** [v126] NLS 绑定实时验证：发探针通知 → 3 秒后查 Listener 是否收到（探测荣耀「授权但未绑定」） */
    fun verifyNlsBinding() {
        viewModelScope.launch {
            val before = com.example.course_schedule_for_chd_v002.service.checkin.CheckInTestTools.lastSelfTestReceivedAt
            com.example.course_schedule_for_chd_v002.service.checkin.CheckInTestTools
                .sendSelfTestNotification(app, title = "【绑定验证】签到探针")
            kotlinx.coroutines.delay(3000)
            val after = com.example.course_schedule_for_chd_v002.service.checkin.CheckInTestTools.lastSelfTestReceivedAt
            val received = after > before
            _uiState.update {
                it.copy(
                    message = if (received) "监听回调正常（探针已收到）"
                    else "3 秒内未收到探针回调——通知使用权可能被授权但未绑定，尝试重开开关"
                )
            }
            checkNlsHealth()
        }
    }

    /** [v126] 通知记录查看对话框 */
    fun openNotificationLog() = _uiState.update { it.copy(notificationLogOpen = true) }
    fun closeNotificationLog() = _uiState.update { it.copy(notificationLogOpen = false) }

    /**
     * [v127] 导出全部位置数据到用户选定位置（SAF 另存为；换机迁移用途）。
     * 旧实现先写应用私有目录再弹分享面板，用户不选目标就找不到文件；改为 UI 端
     * ACTION_CREATE_DOCUMENT 由用户亲自选保存位置，这里只负责把 JSON 写入返回的 Uri。
     */
    fun exportLocationsTo(uri: android.net.Uri) {
        viewModelScope.launch {
            runCatching {
                val locations = locationRepository.getAll()
                val json = com.example.course_schedule_for_chd_v002.util.CheckInLocationBackup.serialize(locations)
                app.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                    out.flush()
                } ?: throw IllegalStateException("无法打开所选保存位置")
                locations.size
            }.onSuccess { n ->
                _uiState.update {
                    it.copy(message = "已导出 $n 个位置到：${queryDisplayName(uri)}")
                }
            }.onFailure {
                _uiState.update { it.copy(message = "导出失败：${it.message}") }
            }
        }
    }

    /** [v127] 从 SAF Uri 查显示文件名（提示用户导出到哪了；查不到退化为通用名） */
    private fun queryDisplayName(uri: android.net.Uri): String {
        return runCatching {
            app.contentResolver.query(
                uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: "所选位置"
    }

    /** [v126] 从 JSON 导入位置（合并插入，不覆盖已有；结果走 message 通道） */
    fun importLocations(uri: android.net.Uri) {
        viewModelScope.launch {
            runCatching {
                val json = app.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalStateException("无法读取所选文件")
                val (locations, _) = com.example.course_schedule_for_chd_v002.util.CheckInLocationBackup.deserialize(json)
                locations.forEach { locationRepository.insert(it.copy(id = 0)) }
                locations.size
            }.onSuccess { n ->
                _uiState.update { it.copy(message = "已导入 $n 个位置") }
            }.onFailure {
                _uiState.update { it.copy(message = "导入失败：${it.message}") }
            }
        }
    }

    /** [v114] 手动停止虚拟定位会话 */
    fun stopMock() {
        MockLocationController.stop(app)
        _uiState.update { it.copy(mockLocationActive = false, message = "已停止虚拟定位") }
    }

    /** 用户从手动选择列表选了某个位置 */
    fun onManualSelected(location: CheckInLocation) {
        viewModelScope.launch {
            _uiState.update { it.copy(pendingManualSelection = emptyList()) }
            applyResult(triggerCoordinator.startForLocation(location))
        }
    }

    fun dismissManualSelection() {
        _uiState.update { it.copy(pendingManualSelection = emptyList()) }
    }

    /** 把协调器结果映射到 UI 状态 */
    private fun applyResult(result: TriggerResult) {
        when (result) {
            is TriggerResult.MockStarted ->
                _uiState.update {
                    it.copy(
                        mockLocationActive = true,
                        message = "已切换虚拟定位到「${result.locationName}」，${result.durationMin}分钟后自动关闭"
                    )
                }
            is TriggerResult.NoLocation ->
                _uiState.update { it.copy(message = "还没有保存的签到位置，请先添加") }
            is TriggerResult.PermissionMissing ->
                _uiState.update { it.copy(message = result.reason) }
            is TriggerResult.NeedsManualSelection ->
                _uiState.update { it.copy(pendingManualSelection = result.candidates) }
            is TriggerResult.DebouncedBySession ->
                _uiState.update { it.copy(message = "同一签到刚已处理过，本次已跳过") }
        }
    }
}
