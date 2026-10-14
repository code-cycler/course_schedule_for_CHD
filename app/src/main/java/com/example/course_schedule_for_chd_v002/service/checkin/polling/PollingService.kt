package com.example.course_schedule_for_chd_v002.service.checkin.polling

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.example.course_schedule_for_chd_v002.service.checkin.CheckInTriggerCoordinator
import com.example.course_schedule_for_chd_v002.service.checkin.CheckInTriggerEvent
import com.example.course_schedule_for_chd_v002.service.checkin.CheckInTriggerSource
import com.example.course_schedule_for_chd_v002.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import java.time.LocalDateTime

/**
 * [v129] B5 轮询通道载体：dataSync 前台服务（L2 选型，绕开 location 类型与 Mock 服务的权限耦合）。
 *
 * 循环纪律（L0「克制约束」）：60 秒 tick；每 tick 先过 ClassWindowScheduler 课时有窗闸门，
 * 窗口外不请求畅课；学期/周次/课表缓存 30 分钟刷新一次（避免高频打教务）。
 * 发现签到 → 组装 Polling 源事件进现有触发管线（与通知源共用去重与会话逻辑）。
 */
class PollingService : Service() {

    companion object {
        private const val TAG = "CheckIn_Polling"
        const val CHANNEL_ID = "checkin_polling_channel"
        private const val NOTIFICATION_ID = 4129
        private const val TICK_MS = 60_000L
        private const val SCHEDULE_REFRESH_MS = 30 * 60_000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, PollingService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PollingService::class.java))
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 课表缓存（学期/周次/当日课程），30 分钟一刷 */
    private data class ScheduleCache(
        val semester: String?,
        val week: Int,
        val courses: List<com.example.course_schedule_for_chd_v002.domain.model.Course>,
        val refreshedAt: Long
    )

    private var cache: ScheduleCache? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        startForeground(
            NOTIFICATION_ID,
            buildNotification("轮询监听中：课时有窗时查询畅课签到"),
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        serviceScope.launch { pollingLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private suspend fun pollingLoop() {
        val koin = GlobalContext.get()
        val prefs = koin.get<com.example.course_schedule_for_chd_v002.data.local.preferences.UserPreferences>()
        val repo = koin.get<com.example.course_schedule_for_chd_v002.domain.repository.ICourseRepository>()
        val poller = koin.get<TronClassPoller>()
        val coordinator = koin.get<CheckInTriggerCoordinator>()

        while (serviceScope.isActive) {
            val enabled = prefs.pollingEnabled.first()
            val token = prefs.tronClassToken.first()
            if (!enabled || token.isNullOrBlank()) {
                AppLogger.i(TAG, "轮询开关已关或 token 失效，服务自停")
                stopSelf()
                return
            }
            try {
                val now = LocalDateTime.now()
                val schedule = ensureScheduleCache(repo, now)
                if (schedule != null) {
                    val inWindow = ClassWindowScheduler.inAnyClassWindow(
                        courses = schedule.courses,
                        currentWeek = schedule.week,
                        today = com.example.course_schedule_for_chd_v002.domain.model.DayOfWeek.fromValue(now.dayOfWeek.value),
                        now = now.toLocalTime()
                    )
                    if (inWindow) {
                        val result = withContext(Dispatchers.IO) { poller.pollOnce() }
                        AppLogger.i(TAG, "窗口内轮询结果：$result")
                        when (result) {
                            is TronClassPollResult.FoundCheckIn -> coordinator.onEvent(
                                CheckInTriggerEvent(
                                    source = CheckInTriggerSource.Polling,
                                    timestamp = System.currentTimeMillis(),
                                    courseHint = null, // 畅课 courseId 与教务课名不同源，由管线按当前时间推断
                                    rawText = "轮询发现 ${result.rollcalls.size} 个进行中签到",
                                    checkInId = result.rollcalls.firstNotNullOfOrNull { it.rollcallId }
                                )
                            )
                            is TronClassPollResult.SessionExpired -> {
                                AppLogger.w(TAG, "token 失效，停止轮询待用户重连")
                                stopSelf()
                                return
                            }
                            else -> Unit // NoActiveCheckIn / NetworkError / ParseError：下个 tick 再试
                        }
                    } else {
                        AppLogger.d(TAG, "窗口外待命（week=${schedule.week}）")
                    }
                } else {
                    AppLogger.w(TAG, "无课表数据或周次不可得，服务自停（UI 显示原因）")
                    stopSelf()
                    return
                }
            } catch (t: Throwable) {
                AppLogger.e(TAG, "轮询循环异常：${t.message}")
            }
            delay(TICK_MS)
        }
    }

    /** 缓存超过 30 分钟或为空时刷新（fetchCurrentWeek 走教务网络，不能每 tick 打） */
    private suspend fun ensureScheduleCache(
        repo: com.example.course_schedule_for_chd_v002.domain.repository.ICourseRepository,
        now: LocalDateTime
    ): ScheduleCache? {
        val existing = cache
        if (existing != null && System.currentTimeMillis() - existing.refreshedAt < SCHEDULE_REFRESH_MS) {
            return existing
        }
        val fetched = runCatching { repo.fetchCurrentWeek() }.getOrNull()
        val semester = fetched?.first ?: repo.getCurrentSemester() ?: return null
        val week = fetched?.second ?: 0
        if (week <= 0) {
            // 教务不可达时的兜底：用本地课表 + 缓存周次不可得 → 视为无窗口（宁可不轮询不误轮询）
            return null
        }
        val courses = runCatching { repo.getLocalSchedule(semester) }.getOrDefault(emptyList())
        if (courses.isEmpty()) return null
        return ScheduleCache(semester, week, courses, System.currentTimeMillis()).also { cache = it }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "签到轮询（第二监听通道）",
                NotificationManager.IMPORTANCE_MIN
            ).apply { description = "课时有窗时轮询畅课签到状态的前台服务" }
        )
    }

    private fun buildNotification(text: String): Notification {
        return androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("课程表 · 签到轮询")
            .setContentText(text)
            .setOngoing(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MIN)
            .build()
    }
}
