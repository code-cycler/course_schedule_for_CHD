package com.example.course_schedule_for_chd_v002.domain.model

/**
 * [获取新学期] 教务系统学期选项
 *
 * @param remoteId 教务系统内部 semester.id（下拉框 option.value，数字字符串，如 "42"）
 * @param label 教务系统显示文本（option.text，如 "2025-2026学年第2学期"）
 *
 * 本地学期串（"2025-2026-2"）由调用方用 `ScheduleHtmlParser.parseSemesterString(label)` 转换，
 * 这里不放转换逻辑以免 domain 层反向依赖 data 层。
 */
data class SemesterOption(
    val remoteId: String,
    val label: String
)
