package com.example.course_schedule_for_chd_v002

import android.os.Build
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.example.course_schedule_for_chd_v002.di.appModule
import com.example.course_schedule_for_chd_v002.di.databaseModule
import com.example.course_schedule_for_chd_v002.di.networkModule
import com.example.course_schedule_for_chd_v002.service.reminder.ReminderForegroundService
import com.example.course_schedule_for_chd_v002.service.reminder.ReminderManager
import com.example.course_schedule_for_chd_v002.ui.navigation.AppNavigation
import com.example.course_schedule_for_chd_v002.ui.theme.Course_schedule_for_CHD_v002Theme
import com.example.course_schedule_for_chd_v002.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * 应用主Activity
 * 作为应用入口点，初始化依赖注入和导航
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // [v107] 记录应用启动（必须在最开始，使用 AppLogger 缓存日志）
        logAppStartup()

        // 初始化 Koin 依赖注入
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@MainActivity)
            modules(
                networkModule,
                databaseModule,
                appModule
            )
        }
        AppLogger.i(TAG, "Koin 依赖注入初始化完成")

        // [健壮性优化] 启动时初始化提醒
        initializeRemindersOnStartup()

        enableEdgeToEdge()
        setContent {
            Course_schedule_for_CHD_v002Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    AppNavigation(navController = navController)
                }
            }
        }
    }

    /**
     * [v107] 记录应用启动信息
     */
    private fun logAppStartup() {
        AppLogger.i(TAG, "========== 应用启动 ==========")
        AppLogger.i(TAG, "进程ID: ${Process.myPid()}")
        AppLogger.i(TAG, "设备: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})")

        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName ?: "未知"
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            AppLogger.i(TAG, "应用版本: $versionName ($versionCode)")
        } catch (e: Exception) {
            AppLogger.e(TAG, "获取应用版本失败", e)
        }

        AppLogger.i(TAG, "日志缓存状态: ${AppLogger.getStats()}")
    }

    /**
     * [健壮性优化] 启动时初始化提醒
     *
     * 使用前台服务确保提醒初始化成功
     */
    private fun initializeRemindersOnStartup() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                AppLogger.d(TAG, "[健壮性优化] 启动提醒初始化...")

                // 延迟1秒确保 Koin 初始化完成
                delay(1000)

                // 启动前台服务进行提醒初始化
                ReminderForegroundService.start(this@MainActivity)

                AppLogger.i(TAG, "[健壮性优化] 提醒初始化服务已启动")
            } catch (e: Exception) {
                AppLogger.e(TAG, "[健壮性优化] 提醒初始化失败", e)

                // 如果前台服务失败，尝试直接初始化
                try {
                    val reminderManager = ReminderManager(this@MainActivity)
                    reminderManager.initializeReminders()
                    AppLogger.i(TAG, "[健壮性优化] 使用备用方案初始化提醒")
                } catch (e2: Exception) {
                    AppLogger.e(TAG, "[健壮性优化] 备用方案也失败", e2)
                }
            }
        }
    }
}
