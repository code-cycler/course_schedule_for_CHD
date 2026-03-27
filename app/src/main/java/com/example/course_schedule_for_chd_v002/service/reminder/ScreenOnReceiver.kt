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
 * 屏幕亮起广播接收器
 *
 * [健壮性优化] 在屏幕亮起时检查并恢复提醒
 *
 * 注意: Android 8.0+ 限制了对隐式广播接收器的注册
 * 需要在 AndroidManifest.xml 中显式注册
 */
class ScreenOnReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScreenOnReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_SCREEN_ON) {
            Log.d(TAG, "屏幕亮起，检查提醒状态")

            // 使用协程异步处理
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // 加载用户设置
                    val userPreferences = UserPreferences(context)
                    val settings = userPreferences.getReminderSettingsOnce()

                    // 获取学期开始日期和当前周次
                    val semesterStartDate = userPreferences.getSemesterStartDateOnce()
                    val currentWeek = userPreferences.getCurrentWeekOnce()
                    val campus = Campus.fromName(userPreferences.getCampusOnce())

                    // 创建提醒管理器
                    val reminderManager = ReminderManager(context)

                    // 恢复早八提醒
                    if (settings.earlyMorningReminderEnabled) {
                        reminderManager.scheduleEarlyMorningReminder(settings)
                        Log.d(TAG, "已恢复早八提醒")
                    }

                    // 恢复上课前提醒
                    if (settings.beforeClassReminderEnabled && semesterStartDate != null && currentWeek != null) {
                        // 获取课程数据
                        val database = AppDatabase.getDatabase(context)
                        val semester = userPreferences.currentSemester.first()
                        val courses = database.courseDao().getCoursesBySemesterSync(semester)
                            .map { it.toDomainModel() }

                        reminderManager.scheduleBeforeClassReminders(courses, settings, semesterStartDate, currentWeek, campus)
                        Log.d(TAG, "已恢复上课前提醒， 共${courses.size} 节课")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "恢复提醒失败", e)
            }
        }
    }
}
}
