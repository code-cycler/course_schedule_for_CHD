package com.example.course_schedule_for_chd_v002.service.checkin

/**
 * [v126] 签到触发源抽象（checkin-assist-v2 B2 / ADR-0006）。
 *
 * sealed 限定源集合（编译期穷尽）：未来加源（如自动签到回调启用）只加分支不改管线。
 */
sealed interface CheckInTriggerSource {
    /** 畅课系统通知（NotificationListenerService） */
    data object Notification : CheckInTriggerSource

    /** 畅课 API 轮询（条件节：假设 B/C 闸门通过后实现） */
    data object Polling : CheckInTriggerSource

    /** 用户手动触发（设置页「模拟签到触发」按钮等） */
    data object Manual : CheckInTriggerSource

    /** 预留：自动签到完成回调（OD 单向门三条件闸门后启用，本期不实现） */
    data object ReservedAuto : CheckInTriggerSource
}

/**
 * [v126] 触发事件。任一源命中签到后构造此事件送入 [CheckInTriggerCoordinator] 管线。
 *
 * @param source 触发源
 * @param timestamp 事件时间戳
 * @param courseHint 课程提示（通知子串提取或课表推断；空则去重退化为纯时间窗）
 * @param rawText 原始文本（通知标题+正文 / 轮询 payload，用于日志与记录）
 * @param checkInId 轮询源带回的签到会话 ID（通知源为 null；非空时去重优先用它）
 */
data class CheckInTriggerEvent(
    val source: CheckInTriggerSource,
    val timestamp: Long = System.currentTimeMillis(),
    val courseHint: String? = null,
    val rawText: String? = null,
    val checkInId: String? = null
)
