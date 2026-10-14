package com.example.course_schedule_for_chd_v002.service.checkin

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
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
    private val courseRepository: com.example.course_schedule_for_chd_v002.domain.repository.ICourseRepository by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 是否 debug 构建（放行 adb shell 测试通知，便于无真实签到时端到端测试） */
    private val isDebuggable: Boolean
        get() = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val pkg = sbn.packageName
        // 廉价包名预检（不挂起）：非目标包直接忽略。
        // [v126] 正式版自测通知白名单：本包名 + 私有 extras 标记双条件（走真实匹配→触发管线，不绕过环节）
        val isSelfTest = CheckInNotificationMatcher.isSelfTestNotification(
            pkg, packageName,
            notification.extras?.getBoolean(CheckInNotificationMatcher.TEST_EXTRA_KEY) == true
        )
        if (!CheckInNotificationMatcher.isTargetPackage(pkg, isDebuggable) && !isSelfTest) return

        AppLogger.d(TAG, "[v126] 收到目标通知: pkg=$pkg, selfTest=$isSelfTest")
        val content = extractContent(notification)
        scope.launch {
            val keywords = userPreferences.getCheckInKeywordsOnce()
            if (!CheckInNotificationMatcher.matchesKeywords(content, keywords)) {
                // 记录非签到的畅课通知原文，便于开学收到真实签到通知后校准关键字
                AppLogger.d(TAG, "[v114] 畅课通知(非签到关键字)，忽略。原文: $content")
                return@launch
            }
            // [v126] 构造触发事件（source=Notification，courseHint 通知子串提取，去重由管线内会话级 Deduper 接管）
            val event = CheckInTriggerEvent(
                source = CheckInTriggerSource.Notification,
                courseHint = extractCourseHint(content),
                rawText = content
            )
            AppLogger.i(TAG, "[v126] 识别到签到通知（courseHint=${event.courseHint}）: $content")
            handleTrigger(event)
        }
    }

    /** [v126] 通知文本与当前学期课表课程名子串匹配提取 courseHint；学期未设置返回 null（由管线推断兜底） */
    private suspend fun extractCourseHint(content: String): String? {
        return try {
            val semester = courseRepository.getCurrentSemester() ?: return null
            CourseHintExtractor.extract(content, courseRepository.getCourseRoomUnion(semester).keys)
        } catch (e: Exception) {
            AppLogger.w(TAG, "[v126] courseHint 提取失败: ${e.message}")
            null
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

    private suspend fun handleTrigger(event: CheckInTriggerEvent) {
        when (val result = triggerCoordinator.onEvent(event)) {
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
            is CheckInTriggerCoordinator.TriggerResult.DebouncedBySession -> {
                // 会话级去重命中：同一签到近期已响应过，静默跳过不打扰
                AppLogger.d(TAG, "[v126] 会话级去重命中，跳过本次触发")
            }
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
        val channel = NotificationChannel(
            CHANNEL_ID, "签到辅助提醒", NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "签到辅助：检测到签到但需用户操作时的提醒" }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private const val TAG = "CheckInNotifListener"
        private const val CHANNEL_ID = "checkin_assist_alert_channel"
        private const val GUIDANCE_NOTIFICATION_ID = 0x1142
        // [v126] 15s 纯时间防抖已由管线内会话级 CheckInDeduper（课程名/签到 ID + 2 分钟窗）取代
    }
}
