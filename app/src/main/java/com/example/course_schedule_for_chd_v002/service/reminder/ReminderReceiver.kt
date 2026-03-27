package com.example.course_schedule_for_chd_v002.service.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.course_schedule_for_chd_v002.MainActivity
import com.example.course_schedule_for_chd_v002.R
import com.example.course_schedule_for_chd_v002.domain.model.Campus
import com.example.course_schedule_for_chd_v002.domain.model.ReminderSettings
import com.example.course_schedule_for_chd_v002.data.local.database.AppDatabase
import com.example.course_schedule_for_chd_v002.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 课程提醒广播接收器
 *
 * 接收 AlarmManager 的提醒广播并显示通知
 */
class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_EARLY_MORNING_REMINDER = "com.example.course_schedule_for_chd_v002.EARLY_MORNING_REMINDER"
        const val ACTION_BEFORE_CLASS_REMINDER = "com.example.course_schedule_for_chd_v002.BEFORE_CLASS_REMINDER"
        const val ACTION_RESCHEDULE_REMINDERS = "com.example.course_schedule_for_chd_v002.RESCHEDULE_REMINDERS"

        const val EXTRA_COURSE_NAME = "course_name"
        const val EXTRA_COURSE_LOCATION = "course_location"
        const val EXTRA_COURSE_TIME = "course_time"
        const val EXTRA_NOTIFICATION_ID = "notification_id"

        private const val CHANNEL_ID_EARLY_MORNING = "course_reminder_early_morning"
        private const val CHANNEL_ID_BEFORE_CLASS = "course_reminder_before_class"
        private const val CHANNEL_NAME_EARLY_MORNING = "早八提醒"
        private const val CHANNEL_NAME_BEFORE_CLASS = "上课提醒"
        private const val NOTIFICATION_ID_EARLY_MORNING = 1001
        private const val NOTIFICATION_ID_BEFORE_CLASS_BASE = 2000
    }

    override fun onReceive(context: Context, intent: Intent) {
        AppLogger.d("ReminderReceiver", "收到广播: ${intent.action}")

        when (intent.action) {
            ACTION_EARLY_MORNING_REMINDER -> {
                handleEarlyMorningReminder(context, intent)
                // 重新调度下一天的提醒
                rescheduleNextDayReminder(context)
            }
            ACTION_BEFORE_CLASS_REMINDER -> {
                handleBeforeClassReminder(context, intent)
            }
            ACTION_RESCHEDULE_REMINDERS -> {
                // 系统重启后重新调度提醒
                rescheduleAllReminders(context)
            }
        }
    }

    /**
     * 处理早八提醒
     */
    private fun handleEarlyMorningReminder(context: Context, intent: Intent) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 获取数据库和设置
                val database = AppDatabase.getDatabase(context)
                val courseDao = database.courseDao()

                // 获取当前学期（从偏好设置中获取）
                val prefs = context.getSharedPreferences("user_preferences", Context.MODE_PRIVATE)
                val semester = prefs.getString("current_semester", "") ?: ""

                if (semester.isEmpty()) {
                    AppLogger.w("ReminderReceiver", "没有当前学期信息，跳过早八提醒")
                    return@launch
                }

                // 获取当前周次
                val currentWeek = prefs.getInt("current_week", 1)

                // 获取当前学期的所有课程
                val courses = courseDao.getCoursesBySemesterFlow(semester).first()
                    .map { it.toDomainModel() }

                // 检查明天是否有早八课程
                val earlyMorningCourses = CourseReminderUtils.checkEarlyMorningCourses(
                    courses = courses,
                    currentWeek = currentWeek,
                    today = LocalDate.now()
                )

                if (earlyMorningCourses.isNotEmpty()) {
                    // 获取提醒设置
                    val settingsJson = prefs.getString("reminder_settings", "") ?: ""
                    val settings = if (settingsJson.isNotEmpty()) {
                        ReminderSettings.fromJson(settingsJson)
                    } else {
                        ReminderSettings.DEFAULT
                    }

                    // 显示通知
                    showEarlyMorningNotification(context, earlyMorningCourses, settings)
                } else {
                    AppLogger.d("ReminderReceiver", "明天没有早八课程，不发送通知")
                }
            } catch (e: Exception) {
                AppLogger.e("ReminderReceiver", "处理早八提醒失败", e)
            }
        }
    }

    /**
     * 处理上课前提醒
     */
    private fun handleBeforeClassReminder(context: Context, intent: Intent) {
        val courseName = intent.getStringExtra(EXTRA_COURSE_NAME) ?: return
        val courseLocation = intent.getStringExtra(EXTRA_COURSE_LOCATION) ?: ""
        val courseTime = intent.getStringExtra(EXTRA_COURSE_TIME) ?: ""
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, NOTIFICATION_ID_BEFORE_CLASS_BASE)

        // 获取提醒设置
        val prefs = context.getSharedPreferences("user_preferences", Context.MODE_PRIVATE)
        val settingsJson = prefs.getString("reminder_settings", "") ?: ""
        val settings = if (settingsJson.isNotEmpty()) {
            ReminderSettings.fromJson(settingsJson)
        } else {
            ReminderSettings.DEFAULT
        }

        showBeforeClassNotification(
            context = context,
            courseName = courseName,
            courseLocation = courseLocation,
            courseTime = courseTime,
            notificationId = notificationId,
            settings = settings
        )
    }

    /**
     * 显示早八提醒通知
     */
    private fun showEarlyMorningNotification(
        context: Context,
        courses: List<com.example.course_schedule_for_chd_v002.domain.model.Course>,
        settings: ReminderSettings
    ) {
        createNotificationChannel(context, CHANNEL_ID_EARLY_MORNING, CHANNEL_NAME_EARLY_MORNING)

        val content = CourseReminderUtils.formatEarlyMorningNotificationContent(courses)
        val title = "早八提醒"

        val pendingIntent = createMainActivityPendingIntent(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_EARLY_MORNING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .apply {
                if (settings.reminderSoundEnabled) {
                    setDefaults(NotificationCompat.DEFAULT_SOUND)
                }
                if (settings.reminderVibrationEnabled) {
                    setDefaults(NotificationCompat.DEFAULT_VIBRATE)
                }
            }
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_EARLY_MORNING, notification)
            AppLogger.d("ReminderReceiver", "早八提醒通知已发送: $content")
        } catch (e: SecurityException) {
            AppLogger.e("ReminderReceiver", "没有通知权限", e)
        }
    }

    /**
     * 显示上课前提醒通知
     */
    private fun showBeforeClassNotification(
        context: Context,
        courseName: String,
        courseLocation: String,
        courseTime: String,
        notificationId: Int,
        settings: ReminderSettings
    ) {
        createNotificationChannel(context, CHANNEL_ID_BEFORE_CLASS, CHANNEL_NAME_BEFORE_CLASS)

        val title = "即将上课"
        val content = "$courseName @ $courseLocation ($courseTime)"

        val pendingIntent = createMainActivityPendingIntent(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_BEFORE_CLASS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .apply {
                if (settings.reminderSoundEnabled) {
                    setDefaults(NotificationCompat.DEFAULT_SOUND)
                }
                if (settings.reminderVibrationEnabled) {
                    setDefaults(NotificationCompat.DEFAULT_VIBRATE)
                }
            }
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            AppLogger.d("ReminderReceiver", "上课提醒通知已发送: $content")
        } catch (e: SecurityException) {
            AppLogger.e("ReminderReceiver", "没有通知权限", e)
        }
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel(context: Context, channelId: String, channelName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "课程表提醒通知"
                enableLights(true)
                enableVibration(true)
                // [v104] 锁屏显示完整通知内容
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                // [v104] 绕过勿扰模式（需要用户在系统设置中授权）
                setBypassDnd(true)
                // [v104] 显示横幅通知
                setShowBadge(true)
            }

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 创建点击通知后打开 MainActivity 的 PendingIntent
     */
    private fun createMainActivityPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * 重新调度下一天的早八提醒
     */
    private fun rescheduleNextDayReminder(context: Context) {
        val intent = Intent(ACTION_RESCHEDULE_REMINDERS).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }

    /**
     * 重新调度所有提醒
     * [健壮性优化] 同时恢复早八提醒和上课前提醒
     */
    private fun rescheduleAllReminders(context: Context) {
        // 启动 ReminderManager 重新调度
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminderManager = ReminderManager(context)
                val prefs = context.getSharedPreferences("user_preferences", Context.MODE_PRIVATE)
                val settingsJson = prefs.getString("reminder_settings", "") ?: ""
                val settings = if (settingsJson.isNotEmpty()) {
                    ReminderSettings.fromJson(settingsJson)
                } else {
                    ReminderSettings.DEFAULT
                }

                // 调度早八提醒
                reminderManager.scheduleEarlyMorningReminder(settings)
                AppLogger.d("ReminderReceiver", "已重新调度早八提醒")

                // [健壮性优化] 调度上课前提醒
                if (settings.beforeClassReminderEnabled) {
                    val semesterStartDate = prefs.getString("semester_start_date", null)
                    val currentWeek = prefs.getInt("current_week", 1)
                    val semester = prefs.getString("current_semester", "") ?: ""
                    val campusName = prefs.getString("campus", "WEISHUI") ?: "WEISHUI"  // [v108] 读取校区
                    val campus = Campus.fromName(campusName)

                    if (!semesterStartDate.isNullOrEmpty() && semester.isNotEmpty()) {
                        val database = AppDatabase.getDatabase(context)
                        val courses = database.courseDao().getCoursesBySemesterSync(semester)
                            .map { it.toDomainModel() }

                        reminderManager.scheduleBeforeClassReminders(
                            courses, settings, semesterStartDate, currentWeek, campus  // [v108] 传递校区
                        )
                        AppLogger.d("ReminderReceiver", "已重新调度上课前提醒，共${courses.size}节课, 校区: ${campus.displayName}")
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("ReminderReceiver", "重新调度提醒失败", e)
            }
        }
    }
}
