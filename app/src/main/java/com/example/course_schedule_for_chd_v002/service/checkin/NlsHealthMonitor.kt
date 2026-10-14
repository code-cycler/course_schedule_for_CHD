package com.example.course_schedule_for_chd_v002.service.checkin

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import com.example.course_schedule_for_chd_v002.MainActivity
import com.example.course_schedule_for_chd_v002.R
import com.example.course_schedule_for_chd_v002.data.local.preferences.UserPreferences
import com.example.course_schedule_for_chd_v002.util.AppLogger

/**
 * [v126] NLS（通知监听）健康监控（checkin-assist-v2 B3，grill W01 Q1/Q4）。
 *
 * - 心跳：`onListenerConnected` 必写；`onNotificationPosted` 合批写（距上次落盘超
 *   [HEARTBEAT_PERSIST_MIN_INTERVAL_MS] 才写，防 DataStore 高频写放大）。
 * - 疑似未绑判定：超过 [SUSPECT_THRESHOLD_MS] 无心跳（荣耀「授权但未绑定」坑的唯一
 *   应用层可观测信号）。requestRebind 程序化重绑在 MagicOS 上是否生效为**未验证假设 D**
 *   （B3 真机装机验证；无效则降级纯引导）。
 * - 冷启动预防性重绑：App 每次 onCreate 无条件 requestRebind 一次（幂等无害）。
 * - 心跳超阈值低优先级通知：覆盖「一直不打开 App 也能看到监听可能失效」（低优先级不扰）。
 */
class NlsHealthMonitor(
    private val appContext: Context,
    private val userPreferences: UserPreferences
) {

    /** 内存合批：上次心跳落盘时间（进程内） */
    @Volatile
    private var lastPersistAt = 0L

    /** onListenerConnected：必写心跳 */
    suspend fun onListenerConnected() {
        val now = System.currentTimeMillis()
        lastPersistAt = now
        userPreferences.updateNlsHeartbeat(now)
        AppLogger.i(TAG, "[v126] NLS 已连接，心跳写入: $now")
    }

    /** onNotificationPosted：合批心跳（5 分钟内不重复落盘） */
    suspend fun onNotificationPosted() {
        val now = System.currentTimeMillis()
        if (now - lastPersistAt >= HEARTBEAT_PERSIST_MIN_INTERVAL_MS) {
            lastPersistAt = now
            userPreferences.updateNlsHeartbeat(now)
            AppLogger.d(TAG, "[v126] NLS 心跳落盘（合批）: $now")
        }
    }

    /** 疑似未绑：超 24h 无心跳（含从未心跳） */
    suspend fun isSuspectUnbound(): Boolean {
        val last = userPreferences.getNlsHeartbeatOnce()
        return shouldSuspect(last, System.currentTimeMillis())
    }

    /**
     * 冷启动入口：无条件预防性重绑（grill W01 Q4，补「打开后」面）+ 疑似未绑时发低优先级
     * 可见通知（补「一直不打开」面；24h 内不重复提醒）。
     */
    suspend fun onAppColdStart() {
        tryRebind()
        if (isSuspectUnbound()) {
            notifySuspectIfNeeded()
        }
    }

    /** 程序化请求系统重绑 NLS 组件（未验证假设 D：MagicOS 行为装机验证） */
    fun tryRebind(): Boolean {
        val ok = runCatching {
            NotificationListenerService.requestRebind(
                ComponentName(appContext, CheckInNotificationListener::class.java)
            )
        }.isSuccess
        AppLogger.i(TAG, "[v126] requestRebind 发起（生效性=假设 D 待真机验证）: $ok")
        return ok
    }

    /** 疑似未绑低优先级通知（去重：距上次提醒 < 24h 不重复发） */
    private suspend fun notifySuspectIfNeeded() {
        val now = System.currentTimeMillis()
        val lastNotified = userPreferences.getNlsSuspectNotifiedAtOnce()
        if (lastNotified != null && now - lastNotified < SUSPECT_THRESHOLD_MS) return

        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(HEALTH_CHANNEL_ID, "签到监听健康提醒", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "通知监听可能未生效时的低优先级提醒" }
        )
        val contentIntent = PendingIntent.getActivity(
            appContext, 0,
            Intent(appContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = androidx.core.app.NotificationCompat.Builder(appContext, HEALTH_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("签到监听可能未生效")
            .setContentText("点按打开 App 处理（重开「通知使用权」通常可恢复）")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        nm.notify(HEALTH_NOTIFICATION_ID, notification)
        userPreferences.updateNlsSuspectNotifiedAt(now)
        AppLogger.w(TAG, "[v126] 心跳超阈值，已发疑似未绑提醒")
    }

    companion object {
        private const val TAG = "NlsHealth"

        /** 疑似未绑阈值：24 小时无心跳 */
        const val SUSPECT_THRESHOLD_MS = 24 * 60 * 60 * 1000L

        /** 心跳落盘最小间隔：5 分钟（合批） */
        const val HEARTBEAT_PERSIST_MIN_INTERVAL_MS = 5 * 60 * 1000L

        const val HEALTH_CHANNEL_ID = "checkin_assist_health_channel"
        private const val HEALTH_NOTIFICATION_ID = 0x1143

        /** 纯函数判定（可测）：lastHeartbeatAt 为 null = 从未连接/收到 → 疑似 */
        fun shouldSuspect(lastHeartbeatAt: Long?, now: Long, thresholdMs: Long = SUSPECT_THRESHOLD_MS): Boolean {
            if (lastHeartbeatAt == null) return true
            return now - lastHeartbeatAt > thresholdMs
        }
    }
}
