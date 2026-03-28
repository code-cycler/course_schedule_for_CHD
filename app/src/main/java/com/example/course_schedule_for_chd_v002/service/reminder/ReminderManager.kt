package com.example.course_schedule_for_chd_v002.service.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.course_schedule_for_chd_v002.data.local.database.AppDatabase
import com.example.course_schedule_for_chd_v002.data.local.preferences.UserPreferences
import com.example.course_schedule_for_chd_v002.domain.model.Campus
import com.example.course_schedule_for_chd_v002.domain.model.Course
import com.example.course_schedule_for_chd_v002.domain.model.ReminderSettings
import com.example.course_schedule_for_chd_v002.util.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 课程提醒调度管理器
 *
 * 负责调度早八提醒和上课前提醒
 */
class ReminderManager(private val context: Context) {

    companion object {
        private const val TAG = "ReminderManager"
        private const val REQUEST_CODE_SCHEDULE_EXACT = 1001
        private const val REQUEST_CODE_SCHEDULE_BEFORE_CLASS = 1002

        // 早八提醒的 requestCode 匨量
        private const val PENDING_INTENT_ID_EARLY_MORNING = 10001
        // 上课前提醒的 requestCode 基数 (每个课程一个唯一ID)
        private const val PENDING_INTENT_ID_BEFORE_CLASS_BASE = 20000
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // 跟踪已调度的上课前提醒 notificationId，用于取消
    private val scheduledBeforeClassIds = mutableSetOf<Int>()

    /**
     * 调度次日早八提醒
     *
     * @param settings 提醒设置
     */
    fun scheduleEarlyMorningReminder(settings: ReminderSettings) {
        if (!settings.earlyMorningReminderEnabled) {
            cancelEarlyMorningReminder()
            Log.d(TAG, "早八提醒已禁用，取消调度")
            return
        }

        // 计算下次提醒时间
        val now = LocalDateTime.now()
        var reminderTime = now
            .withHour(settings.earlyMorningReminderHour)
            .withMinute(settings.earlyMorningReminderMinute)
            .withSecond(0)
            .withNano(0)

        // 如果今天的提醒时间已过，调度到明天
        if (!reminderTime.isAfter(now)) {
            reminderTime = reminderTime.plusDays(1)
        }

        scheduleEarlyMorningReminderAt(reminderTime)
        Log.d(TAG, "已调度早八提醒: ${reminderTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}")
    }

    /**
     * 在指定时间调度早八提醒
     */
    private fun scheduleEarlyMorningReminderAt(reminderTime: LocalDateTime) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_EARLY_MORNING_REMINDER
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            PENDING_INTENT_ID_EARLY_MORNING,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = reminderTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ 需要检查精确闹钟权限
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    // 没有精确闹钟权限，使用非精确闹钟
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                    Log.w(TAG, "没有精确闹钟权限，使用非精确闹钟")
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "调度早八提醒失败: ${e.message}")
        }
    }

    /**
     * 取消次日早八提醒
     */
    fun cancelEarlyMorningReminder() {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_EARLY_MORNING_REMINDER
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            PENDING_INTENT_ID_EARLY_MORNING,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "已取消早八提醒")
    }

    /**
     * 调度上课前提醒
     *
     * @param courses 课程列表
     * @param settings 提醒设置
     * @param semesterStartDate 学期开始日期
     * @param currentWeek 当前教学周
     */
    fun scheduleBeforeClassReminders(
        courses: List<Course>,
        settings: ReminderSettings,
        semesterStartDate: String,
        currentWeek: Int,
        campus: Campus = Campus.BENBU  // [v108] 校区参数
    ) {
        if (!settings.beforeClassReminderEnabled) {
            cancelAllBeforeClassReminders()
            Log.d(TAG, "上课前提醒已禁用，取消所有调度")
            return
        }

        // 获取今天的课程
        val today = LocalDate.now()
        val todayDayOfWeek = CourseReminderUtils.convertToAppDayOfWeek(today.dayOfWeek)

        val todayCourses = courses.filter { course ->
            course.dayOfWeek == todayDayOfWeek &&
            course.isWeekInRange(currentWeek)
        }

        // 取消所有旧的上课前提醒
        cancelAllBeforeClassReminders()

        // 为每节课调度提醒
        val startTimes = CourseReminderUtils.getClassStartTimes(campus)  // [v108] 使用校区感知时间
        todayCourses.forEach { course ->
            val classStartTime = startTimes[course.startNode]
            if (classStartTime != null) {
                val reminderTime = LocalDateTime.of(today, classStartTime)
                    .minusMinutes(settings.beforeClassReminderMinutes.toLong())

                val now = LocalDateTime.now()
                if (reminderTime.isAfter(now)) {
                    scheduleBeforeClassReminder(course, reminderTime, settings)
                }
            }
        }

        Log.d(TAG, "已调度${todayCourses.size}个上课前提醒")
    }

    /**
     * 调度单个课程的上课前提醒
     */
    private fun scheduleBeforeClassReminder(
        course: Course,
        reminderTime: LocalDateTime,
        settings: ReminderSettings
    ) {
        val notificationId = (course.id.toInt() and 0xFFFF) + PENDING_INTENT_ID_BEFORE_CLASS_BASE
        scheduledBeforeClassIds.add(notificationId)

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_BEFORE_CLASS_REMINDER
            putExtra(ReminderReceiver.EXTRA_COURSE_NAME, course.name)
            putExtra(ReminderReceiver.EXTRA_COURSE_LOCATION, course.location)
            putExtra(ReminderReceiver.EXTRA_COURSE_TIME,
                CourseReminderUtils.getCourseStartTimeDisplay(course.startNode))
            putExtra(ReminderReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = reminderTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "调度上课前提醒失败: ${e.message}")
        }
    }

    /**
     * 取消所有上课前提醒
     */
    fun cancelAllBeforeClassReminders() {
        val ids = scheduledBeforeClassIds.toList()
        ids.forEach { id ->
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = ReminderReceiver.ACTION_BEFORE_CLASS_REMINDER
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, id, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
        scheduledBeforeClassIds.clear()
        Log.d(TAG, "已取消 ${ids.size} 个上课前提醒")
    }

    /**
     * 取消所有提醒
     */
    fun cancelAllReminders() {
        cancelEarlyMorningReminder()
        cancelAllBeforeClassReminders()
        Log.d(TAG, "已取消所有提醒")
    }

    /**
     * 初始化提醒服务
     * 在应用启动时调用，检查并调度提醒
     */
    fun initializeReminders() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userPreferences = UserPreferences(context)
                val settings = userPreferences.getReminderSettingsOnce()
                val semesterStartDate = userPreferences.getSemesterStartDateOnce()
                val campusName = userPreferences.getCampusOnce()  // [v108] 读取校区
                val campus = Campus.fromName(campusName)

                if (semesterStartDate != null) {
                    val currentWeek = TimeUtils.calculateCurrentWeek(semesterStartDate) ?: 1

                    // 获取课程数据
                    val database = AppDatabase.getDatabase(context)
                    val semester = userPreferences.currentSemester.first()
                    val courses = database.courseDao().getCoursesBySemesterSync(semester)
                        .map { it.toDomainModel() }

                    // 调度早八提醒
                    scheduleEarlyMorningReminder(settings)

                    // 调度上课前提醒 [v108] 传递校区参数
                    scheduleBeforeClassReminders(courses, settings, semesterStartDate, currentWeek, campus)

                    Log.d(TAG, "提醒服务初始化完成, 校区: ${campus.displayName}")
                } else {
                    // 没有学期开始日期，只调度早八提醒
                    scheduleEarlyMorningReminder(settings)
                    Log.d(TAG, "没有学期开始日期，只调度早八提醒")
                }
            } catch (e: Exception) {
                Log.e(TAG, "初始化提醒服务失败: ${e.message}")
            }
        }
    }

    /**
     * 检查是否有精确闹钟权限
     */
    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }
}
