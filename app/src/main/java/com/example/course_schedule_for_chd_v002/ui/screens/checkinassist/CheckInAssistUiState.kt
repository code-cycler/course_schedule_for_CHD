package com.example.course_schedule_for_chd_v002.ui.screens.checkinassist

import com.example.course_schedule_for_chd_v002.domain.model.CheckInAssistSettings
import com.example.course_schedule_for_chd_v002.domain.model.CheckInLocation

/**
 * [v114] 签到辅助界面状态
 */
data class CheckInAssistUiState(
    val locations: List<CheckInLocation> = emptyList(),
    val settings: CheckInAssistSettings = CheckInAssistSettings.DEFAULT,
    /** 签到通知识别关键字（逗号分隔，设置页可编辑） */
    val keywordsText: String = "签到,考勤,点名",
    val mockAppEnabled: Boolean = false,
    val locationPermissionGranted: Boolean = false,
    val notificationPermissionGranted: Boolean = false,
    /** 是否被授予「通知使用权」（能否监听畅课签到通知） */
    val notificationListenerEnabled: Boolean = false,
    /** [v114] 当前是否有 Mock 定位会话在跑（控制「停止」按钮） */
    val mockLocationActive: Boolean = false,
    /** 编辑器是否打开；editing 非空表示编辑模式 */
    val editorOpen: Boolean = false,
    val editing: CheckInLocation? = null,
    /** 需要用户手动选择位置时，给出候选；空表示无待选 */
    val pendingManualSelection: List<CheckInLocation> = emptyList(),
    /** 一次性提示消息（显示后由 UI 置空） */
    val message: String? = null
)
