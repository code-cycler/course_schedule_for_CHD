package com.example.course_schedule_for_chd_v002.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * [v128] 荣耀/华为「应用启动管理（自启动）」深链引导。
 *
 * 背景：真机实证（假设 E）荣耀 MagicOS 的 BootProxy 会扣留 BOOT_COMPLETED
 * （"don't meet cpuload"），开机重绑监听依赖用户在系统里允许本应用自启动；
 * 而该开关属厂商私有设置，无标准 API 查询状态，只能深链引导 + 用户回报确认。
 *
 * 深链候选按优先级排列（真机荣耀 SER-AN00 / MagicOS 实测第一项直达
 * 「应用启动管理」列表页，2026-10-14 验证 topResumedActivity 命中）。
 */
object HonorAutoStart {

    private val CANDIDATES = listOf(
        // 荣耀 MagicOS：应用启动管理列表（实测有效）
        ComponentName(
            "com.hihonor.systemmanager",
            "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
        ),
        // 华为 EMUI（老机型同源组件）
        ComponentName(
            "com.huawei.systemmanager",
            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
        ),
        // 荣耀备选入口（应用管控）
        ComponentName(
            "com.hihonor.systemmanager",
            "com.hihonor.systemmanager.appcontrol.activity.StartupAppControlActivity"
        )
    )

    /**
     * 依次尝试启动厂商自启动管理页；全部失败返回 false（调用方回退应用详情页）。
     */
    fun openSettings(context: Context): Boolean {
        for (component in CANDIDATES) {
            val intent = Intent(Intent.ACTION_MAIN)
                .setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent) }.isSuccess) return true
        }
        return false
    }
}
