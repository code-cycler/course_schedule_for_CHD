package com.example.course_schedule_for_chd_v002.service.mocklocation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.example.course_schedule_for_chd_v002.MainActivity
import com.example.course_schedule_for_chd_v002.R
import com.example.course_schedule_for_chd_v002.util.AppLogger
import com.example.course_schedule_for_chd_v002.util.GeoConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * [v114] Mock 定位前台服务
 *
 * - 收到带坐标的启动 Intent → startForeground(LOCATION) → 设置测试定位
 *   → 每 1s 刷新防止被真实定位覆盖 → 到时长后自动停止并恢复真实定位。
 * - 收到 [MockLocationController.ACTION_STOP] → 立即停止。
 */
class MockLocationService : Service() {

    companion object {
        private const val TAG = "MockLocationService"
        private const val CHANNEL_ID = "checkin_mock_channel"
        private const val NOTIFICATION_ID = 0x1141
        private const val DEFAULT_DURATION_MIN = 10

        /** [v114] 当前是否有 Mock 会话在跑（供 UI 显示「停止」按钮状态） */
        @Volatile
        var isRunning: Boolean = false
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var refreshJob: Job? = null
    private var timeoutJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == MockLocationController.ACTION_STOP) {
            AppLogger.i(TAG, "[v114] 收到手动停止")
            cleanupAndStop()
            return START_NOT_STICKY
        }

        val name = intent?.getStringExtra(MockLocationController.EXTRA_NAME) ?: "签到位置"
        val lat = intent?.getDoubleExtra(MockLocationController.EXTRA_LAT, 0.0) ?: 0.0
        val lng = intent?.getDoubleExtra(MockLocationController.EXTRA_LNG, 0.0) ?: 0.0
        val durationMin = intent?.getIntExtra(MockLocationController.EXTRA_DURATION_MIN, DEFAULT_DURATION_MIN)
            ?: DEFAULT_DURATION_MIN

        startForegroundWithNotification(name, durationMin)

        // 权限/前置条件不满足时直接退出（应由 UI 层提前拦截，这里兜底）
        return try {
            MockLocationController.applyMock(this, lat, lng)
            startRefreshing(lat, lng)
            scheduleTimeout(durationMin)
            START_NOT_STICKY
        } catch (e: SecurityException) {
            AppLogger.e(TAG, "[v114] 无模拟定位权限，停止服务", e)
            cleanupAndStop()
            START_NOT_STICKY
        } catch (e: Exception) {
            AppLogger.e(TAG, "[v114] 启动 Mock 失败", e)
            cleanupAndStop()
            START_NOT_STICKY
        }
    }

    /** 每 1s 刷新一次测试定位，防止被真实定位覆盖 */
    private fun startRefreshing(lat: Double, lng: Double) {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            while (true) {
                try {
                    MockLocationController.applyMock(this@MockLocationService, lat, lng)
                } catch (e: Exception) {
                    AppLogger.w(TAG, "[v114] 刷新定位失败: ${e.message}")
                }
                delay(GeoConstants.MOCK_REFRESH_INTERVAL_MS)
            }
        }
    }

    /** 到时长后自动恢复真实定位并停止 */
    private fun scheduleTimeout(durationMin: Int) {
        timeoutJob?.cancel()
        val timeoutMs = durationMin.coerceAtLeast(1).toLong() * 60_000L
        timeoutJob = scope.launch {
            delay(timeoutMs)
            AppLogger.i(TAG, "[v114] 到时自动停止（${durationMin}分钟）")
            cleanupAndStop()
        }
    }

    private fun startForegroundWithNotification(name: String, durationMin: Int) {
        ensureChannel()
        val notification = buildNotification(name, durationMin)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // API 34+ 必须显式声明前台服务类型
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(name: String, durationMin: Int): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, MockLocationService::class.java).apply {
                action = MockLocationController.ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("虚拟定位已开启")
            .setContentText("已定位到 $name · ${durationMin}分钟后自动关闭")
            .setContentIntent(contentIntent)
            .addAction(0, "停止虚拟定位", stopIntent)
            .setOngoing(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "签到虚拟定位",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "签到辅助：虚拟定位开启时的持久通知"
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun cleanupAndStop() {
        refreshJob?.cancel()
        timeoutJob?.cancel()
        MockLocationController.clearMock(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanupAndStop()
        scope.cancel()
        isRunning = false
    }
}
