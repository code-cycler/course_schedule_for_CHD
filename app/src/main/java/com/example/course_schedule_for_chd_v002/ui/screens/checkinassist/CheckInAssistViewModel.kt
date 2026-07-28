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
    private val triggerCoordinator: CheckInTriggerCoordinator
) : ViewModel() {

    private val _uiState = MutableStateFlow(CheckInAssistUiState())
    val uiState: StateFlow<CheckInAssistUiState> = _uiState.asStateFlow()

    init {
        observeLocations()
        observeSettings()
        observeKeywords()
        refreshPermissions()
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

    /** 刷新四项权限的实际状态（从系统查询） */
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
        }
    }
}
