package com.example.course_schedule_for_chd_v002.service.checkin

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.example.course_schedule_for_chd_v002.R
import com.example.course_schedule_for_chd_v002.util.AppLogger

/**
 * [v126] 签到辅助手机端测试工具（checkin-assist-v2 B4，L0 测试入口①②）。
 *
 * 正式版自测通知：本包名 + `checkin_test=true` 私有 extras 标记 + 标题含默认关键字——
 * 走**真实**「通知 → 匹配 → 触发管线」全路径，不绕过任何环节（Matcher 双条件白名单放行）。
 */
object CheckInTestTools {

    private const val TAG = "CheckInTestTools"

    const val TEST_CHANNEL_ID = "checkin_assist_test_channel"
    private const val TEST_NOTIFICATION_ID = 0x1144

    /** NLS 绑定探针：Listener 收到自测通知时刷新（进程内 volatile，验证按钮轮询） */
    @Volatile
    var lastSelfTestReceivedAt: Long = 0L

    /** 发送端到端测试通知（标题含「签到」关键字，走真实匹配与触发管线） */
    fun sendSelfTestNotification(context: Context, title: String = "【测试】签到提醒") {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(TEST_CHANNEL_ID, "签到辅助自测", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "端到端测试通知（模拟畅课签到通知格式）" }
        )
        val notification = androidx.core.app.NotificationCompat.Builder(context, TEST_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText("这是一条本机自测通知：验证「通知 → 匹配 → 触发」全链路（不含课程信息）")
            .setAutoCancel(true)
            .build()
        notification.extras.putBoolean(CheckInNotificationMatcher.TEST_EXTRA_KEY, true)
        nm.notify(TEST_NOTIFICATION_ID, notification)
        AppLogger.i(TAG, "[v126] 已发送自测通知（走真实管线）")
    }
}
