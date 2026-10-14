package com.example.course_schedule_for_chd_v002.ui.screens.checkinassist

import com.example.course_schedule_for_chd_v002.domain.model.CheckInAssistSettings
import com.example.course_schedule_for_chd_v002.domain.model.CheckInLocation
import com.example.course_schedule_for_chd_v002.util.TronClassNotificationRecord

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
    /** [v126] 是否已在电池优化白名单（荣耀省电管控杀后台风险） */
    val batteryWhitelisted: Boolean = false,
    /** [v126] NLS 疑似未绑定（>24h 无心跳，警示条显示） */
    val nlsSuspectUnbound: Boolean = false,
    /** [v126] 课程 → 教室并集（当前学期；课程下拉数据源 + 选课预填教室 tag） */
    val courseRoomMap: Map<String, Set<String>> = emptyMap(),
    /** [v126] 畅课通知原文记录（最近 50 条，全量含未命中关键字） */
    val notificationLog: List<TronClassNotificationRecord> = emptyList(),
    /** [v126] 通知记录查看对话框是否打开 */
    val notificationLogOpen: Boolean = false,
    /** [v114] 当前是否有 Mock 定位会话在跑（控制「停止」按钮） */
    val mockLocationActive: Boolean = false,
    /** 编辑器是否打开；editing 非空表示编辑模式 */
    val editorOpen: Boolean = false,
    val editing: CheckInLocation? = null,
    /** 需要用户手动选择位置时，给出候选；空表示无待选 */
    val pendingManualSelection: List<CheckInLocation> = emptyList(),
    /** 一次性提示消息（显示后由 UI 置空） */
    val message: String? = null,
    /** [v126] 位置选用下拉当前选中项（null = 未选） */
    val selectedLocationId: Long? = null
)
