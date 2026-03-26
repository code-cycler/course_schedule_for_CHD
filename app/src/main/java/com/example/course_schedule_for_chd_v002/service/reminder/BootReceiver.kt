package com.example.course_schedule_for_chd_v002.service.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.course_schedule_for_chd_v002.data.local.preferences.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 开机启动广播接收器
 *
 * 用于在设备重启后恢复提醒闹钟调度
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i(TAG, "设备启动完成，开始恢复提醒调度")

            // 使用协程异步处理
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // 加载用户设置
                    val userPreferences = UserPreferences(context)
                    val settings = userPreferences.getReminderSettingsOnce()

                    // 创建提醒管理器并重新调度提醒
                    val reminderManager = ReminderManager(context)

                    if (settings.earlyMorningReminderEnabled) {
                        reminderManager.scheduleEarlyMorningReminder(settings)
                        Log.i(TAG, "已恢复早八提醒调度")
                    }

                    Log.i(TAG, "提醒调度恢复完成")
                } catch (e: Exception) {
                    Log.e(TAG, "恢复提醒调度失败", e)
                }
            }
        }
    }
}
