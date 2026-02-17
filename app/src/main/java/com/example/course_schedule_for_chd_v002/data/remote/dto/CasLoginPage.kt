package com.example.course_schedule_for_chd_v002.data.remote.dto

/**
 * CAS 登录页面信息
 * 包含登录表单需要的隐藏字段
 */
data class CasLoginPage(
    val lt: String,            // 登录令牌
    val execution: String,     // 执行标识
    val eventId: String        // 事件ID，通常是 "submit"
)
