package com.example.course_schedule_for_chd_v002.service.checkin

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.course_schedule_for_chd_v002.MainActivity
import com.example.course_schedule_for_chd_v002.R
import com.example.course_schedule_for_chd_v002.data.local.preferences.UserPreferences
import com.example.course_schedule_for_chd_v002.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * [v114] 畅课签到通知监听服务（阶段二）
 *
 * 监听系统通知，识别来自畅课（TronClass）的签到通知（包名 + 关键字），
 * 命中后委托 [CheckInTriggerCoordinator] 触发虚拟定位。
 *
 * - 需用户在「设置 → 通知 → 通知使用权」中授权本应用。
 * - 签到关键字可配置（DataStore，设置页可编辑），默认「签到/考勤/点名」。
 * - debug 构建额外放行 `com.android.shell`（adb 测试通知来源包），便于无真实签到时测试。
 * - 15s 内重复触发会被防抖跳过（避免同一条通知更新导致的重复触发）；
 *   真正的连续新签到通知间隔通常 > 15s，仍会按设计重新触发。
 */
class CheckInNotificationListener : NotificationListenerService(), KoinComponent {

    private val triggerCoordinator: CheckInTriggerCoordinator by inject()
    private val userPreferences: UserPreferences by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var lastTriggerAt = 0L

    /** 是否 debug 构建（放行 adb shell 测试通知，便于无真实签到时端到端测试） */
    private val isDebuggable: Boolean
        get() = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val pkg = sbn.packageName
        // 廉价包名预检（不挂起）：非目标包直接忽略
        if (!CheckInNotificationMatcher.isTargetPackage(pkg, isDebuggable)) return

        AppLogger.d(TAG, "[v114] 收到目标通知: pkg=$pkg")
        val content = extractContent(notification)
        scope.launch {
            val keywords = userPreferences.getCheckInKeywordsOnce()
            if (!CheckInNotificationMatcher.matchesKeywords(content, keywords)) {
                // 记录非签到的畅课通知原文，便于开学收到真实签到通知后校准关键字
                AppLogger.d(TAG, "[v114] 畅课通知(非签到关键字)，忽略。原文: $content")
                return@launch
            }
            // 防抖：15s 内不重复触发
            val now = System.currentTimeMillis()
            if (now - lastTriggerAt < DEBOUNCE_MS) {
                AppLogger.d(TAG, "[v114] 防抖跳过（距上次触发 ${now - lastTriggerAt}ms）")
                return@launch
            }
            lastTriggerAt = now
            AppLogger.i(TAG, "[v114] 识别到签到通知: $content")
            handleTrigger()
        }
    }

    /** 提取通知的标题 + 正文 + 大文本，拼成一段用于关键字匹配 */
    private fun extractContent(notification: Notification): String {
        val extras = notification.extras ?: return ""
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        return "$title $text $bigText"
    }

    private suspend fun handleTrigger() {
        when (val result = triggerCoordinator.trigger()) {
            is CheckInTriggerCoordinator.TriggerResult.MockStarted -> {
                // Mock 服务自身已显示持久通知，此处无需额外通知
                AppLogger.i(TAG, "[v114] 已由签到通知触发 Mock: ${result.locationName}")
            }
            is CheckInTriggerCoordinator.TriggerResult.NoLocation ->
                postGuidance("检测到签到，但还没有签到位置", "打开 App，在「签到辅助」中添加一个位置")
            is CheckInTriggerCoordinator.TriggerResult.PermissionMissing ->
                postGuidance("检测到签到，但无法切换虚拟定位", result.reason)
            is CheckInTriggerCoordinator.TriggerResult.NeedsManualSelection ->
                postGuidance("检测到签到，有多个候选位置", "打开 App，在「签到辅助」中选择要使用的位置")
        }
    }

    /** 引导用户打开 App 处理的通知 */
    private fun postGuidance(title: String, text: String) {
        ensureChannel()
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(GUIDANCE_NOTIFICATION_ID, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "签到辅助提醒", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "签到辅助：检测到签到但需用户操作时的提醒" }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private const val TAG = "CheckInNotifListener"
        private const val CHANNEL_ID = "checkin_assist_alert_channel"
        private const val GUIDANCE_NOTIFICATION_ID = 0x1142
        private const val DEBOUNCE_MS = 15_000L
    }
}
