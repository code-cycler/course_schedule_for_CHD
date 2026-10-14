package com.example.course_schedule_for_chd_v002.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * [v126] 电池优化白名单申请（checkin-assist-v2 B3）。
 *
 * 直申路径：系统对话框一次点击完成（个人 App 无 Play 政策约束）；
 * 拒绝/失败 fallback：跳系统电池优化设置列表页手动处理。
 * 荣耀「自启动」开关为 MagicOS 私有设置，仅深链引导不强求直达（L1 契约 6）。
 */
object PowerSaveWhitelist {

    /** 是否已在电池优化白名单 */
    fun isIgnored(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * 发起白名单申请（需 Activity context 弹系统对话框）。
     * @return false = intent 发送失败（调用方走 fallback）
     */
    fun request(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrElse {
            AppLogger.w(TAG, "[v126] 直申电池白名单失败: ${it.message}")
            false
        }
    }

    /** fallback：跳电池优化设置列表页（手动找本 App 设为不优化） */
    fun openSettings(context: Context) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private const val TAG = "PowerSaveWhitelist"
}
