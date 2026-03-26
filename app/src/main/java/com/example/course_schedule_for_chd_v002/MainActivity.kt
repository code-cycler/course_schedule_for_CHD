package com.example.course_schedule_for_chd_v002

import android.os.Bundle
import android.util.Log
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
     * [健壮性优化] 启动时初始化提醒
     *
     * 使用前台服务确保提醒初始化成功
     */
    private fun initializeRemindersOnStartup() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "[健壮性优化] 启动提醒初始化...")

                // 延迟1秒确保 Koin 初始化完成
                delay(1000)

                // 启动前台服务进行提醒初始化
                ReminderForegroundService.start(this@MainActivity)

                Log.d(TAG, "[健壮性优化] 提醒初始化服务已启动")
            } catch (e: Exception) {
                Log.e(TAG, "[健壮性优化] 提醒初始化失败", e)

                // 如果前台服务失败，尝试直接初始化
                try {
                    val reminderManager = ReminderManager(this@MainActivity)
                    reminderManager.initializeReminders()
                    Log.d(TAG, "[健壮性优化] 使用备用方案初始化提醒")
                } catch (e2: Exception) {
                    Log.e(TAG, "[健壮性优化] 备用方案也失败", e2)
                }
            }
        }
    }
}
