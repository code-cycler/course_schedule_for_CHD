package com.example.course_schedule_for_chd_v002.service.checkin

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import com.example.course_schedule_for_chd_v002.util.AppLogger

/**
 * [v126] 开机拉活监听（checkin-assist-v2 B3）：BOOT_COMPLETED 后只做一件事——对本 App 的
 * NLS 组件调 requestRebind() 触发系统重绑（治荣耀「装机/系统更新后不重绑」坑）。
 * 零其它初始化副作用，避免拖慢开机。
 */
class BootRebindReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val ok = runCatching {
            NotificationListenerService.requestRebind(
                ComponentName(context, CheckInNotificationListener::class.java)
            )
        }.isSuccess
        AppLogger.i(TAG, "[v126] 开机重绑 NLS: requestRebind=$ok")
    }

    companion object {
        private const val TAG = "BootRebindReceiver"
    }
}
