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
import androidx.navigation.compose.rememberNavController
import com.example.course_schedule_for_chd_v002.ui.navigation.AppNavigation
import com.example.course_schedule_for_chd_v002.ui.theme.Course_schedule_for_CHD_v002Theme
import com.example.course_schedule_for_chd_v002.util.AppLogger

/**
 * 应用主Activity
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        logAppStartup()

        AppLogger.i(TAG, "MainActivity 启动，Koin 已在 Application 中初始化")

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
}
