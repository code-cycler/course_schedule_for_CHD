package com.example.course_schedule_for_chd_v002.service.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.course_schedule_for_chd_v002.MainActivity
import com.example.course_schedule_for_chd_v002.R
import com.example.course_schedule_for_chd_v002.data.local.database.AppDatabase
import com.example.course_schedule_for_chd_v002.data.local.preferences.UserPreferences
import com.example.course_schedule_for_chd_v002.domain.model.Campus
import com.example.course_schedule_for_chd_v002.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 提醒前台服务
 *
 * [健壮性优化] 通过前台服务提高提醒的可靠性
 * - 在应用启动时初始化提醒
 * - 保持服务在前台运行，防止被系统杀死
 *
 * 注意： 此服务只在必要时启动，不需要长期运行
 */
class ReminderForegroundService : Service() {

    companion object {
        private const val TAG = "ReminderForegroundService"
        private const val CHANNEL_ID = "reminder_service_channel"
        private const val CHANNEL_NAME = "提醒服务"
        private const val NOTIFICATION_ID = 3001

        /**
         * 启动服务
         */
        fun start(context: Context) {
            val intent = Intent(context, ReminderForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            AppLogger.d(TAG, "启动提醒前台服务")
        }

        /**
         * 停止服务
         */
        fun stop(context: Context) {
            val intent = Intent(context, ReminderForegroundService::class.java)
            context.stopService(intent)
            AppLogger.d(TAG, "停止提醒前台服务")
        }
    }

    // [优化] 使用 SupervisorJob 确保子协程失败不会影响其他协程
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onCreate() {
        super.onCreate()
        AppLogger.d(TAG, "服务创建")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.d(TAG, "服务启动命令收到")

        // 启动前台服务
        startForegroundService()

        // [优化] 在初始化完成后自动停止服务（移除固定5秒延迟）
        serviceScope.launch {
            try {
                initializeReminders()
            } catch (e: Exception) {
                AppLogger.e(TAG, "初始化提醒异常", e)
            } finally {
                // 确保初始化完成后（无论成功失败）才停止服务
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        AppLogger.d(TAG, "服务销毁")
    }

    /**
     * 启动前台服务
     */
    private fun startForegroundService() {
        createNotificationChannel()

        val notification = createForegroundNotification()

        startForeground(NOTIFICATION_ID, notification)
        AppLogger.d(TAG, "已启动前台服务")
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "课程提醒后台服务"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 创建前台服务通知
     */
    private fun createForegroundNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("课程提醒")
            .setContentText("正在初始化提醒服务...")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    /**
     * 初始化提醒
     * [优化] 改为 suspend 函数，让调用者等待完成
     */
    private suspend fun initializeReminders() {
        try {
            val userPreferences = UserPreferences(this@ReminderForegroundService)
            val settings = userPreferences.getReminderSettingsOnce()

            // 获取学期开始日期和当前周次
            val semesterStartDate = userPreferences.getSemesterStartDateOnce()
            val currentWeek = userPreferences.getCurrentWeekOnce()
            val campus = Campus.fromName(userPreferences.getCampusOnce())

            // 创建提醒管理器
            val reminderManager = ReminderManager(this@ReminderForegroundService)

            // 调度早八提醒
            if (settings.earlyMorningReminderEnabled) {
                reminderManager.scheduleEarlyMorningReminder(settings)
                AppLogger.d(TAG, "已调度早八提醒")
            }

            // 调度上课前提醒
            if (settings.beforeClassReminderEnabled && semesterStartDate != null && currentWeek != null) {
                // 获取课程数据
                val database = AppDatabase.getDatabase(this@ReminderForegroundService)
                val semester = userPreferences.currentSemester.first()
                val courses = database.courseDao().getCoursesBySemesterSync(semester)
                    .map { it.toDomainModel() }

                reminderManager.scheduleBeforeClassReminders(courses, settings, semesterStartDate, currentWeek, campus)
                AppLogger.d(TAG, "已调度上课前提醒，共${courses.size}节课")
            }

            AppLogger.d(TAG, "提醒初始化完成")
        } catch (e: Exception) {
            AppLogger.e(TAG, "初始化提醒失败", e)
        }
    }
}
