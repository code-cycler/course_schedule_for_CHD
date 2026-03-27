package com.example.course_schedule_for_chd_v002.service.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.course_schedule_for_chd_v002.data.local.database.AppDatabase
import com.example.course_schedule_for_chd_v002.data.local.preferences.UserPreferences
import com.example.course_schedule_for_chd_v002.domain.model.Campus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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

                    // [健壮性优化] 获取学期开始日期和当前周次
                    val semesterStartDate = userPreferences.getSemesterStartDateOnce()
                    val currentWeek = userPreferences.getCurrentWeekOnce()
                    val campus = Campus.fromName(userPreferences.getCampusOnce())

                    // 创建提醒管理器
                    val reminderManager = ReminderManager(context)

                    // 恢复早八提醒
                    if (settings.earlyMorningReminderEnabled) {
                        reminderManager.scheduleEarlyMorningReminder(settings)
                        Log.i(TAG, "已恢复早八提醒调度")
                    }

                    // [健壮性优化] 恢复上课前提醒
                    if (settings.beforeClassReminderEnabled && semesterStartDate != null && currentWeek != null) {
                        // 获取课程数据
                        val database = AppDatabase.getDatabase(context)
                        val semester = userPreferences.currentSemester.first()
                        val courses = database.courseDao().getCoursesBySemesterSync(semester)
                            .map { it.toDomainModel() }

                        reminderManager.scheduleBeforeClassReminders(courses, settings, semesterStartDate, currentWeek, campus)
                        Log.i(TAG, "已恢复上课前提醒调度，共${courses.size}节课")
                    }

                    Log.i(TAG, "提醒调度恢复完成")
                } catch (e: Exception) {
                    Log.e(TAG, "恢复提醒调度失败", e)
                }
            }
        }
    }
}
