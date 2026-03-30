package com.example.course_schedule_for_chd_v002

import android.app.Application
import android.util.Log
import com.example.course_schedule_for_chd_v002.di.appModule
import com.example.course_schedule_for_chd_v002.di.databaseModule
import com.example.course_schedule_for_chd_v002.di.networkModule
import com.example.course_schedule_for_chd_v002.util.AppLogger
import com.example.course_schedule_for_chd_v002.util.CrashHandler
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * 自定义 Application 类
 *
 * 职责:
 * - 最先安装 CrashHandler 捕获未处理异常
 * - 检测上次是否非正常退出
 * - 初始化 AppLogger 并开启会话日志持久化
 * - 初始化 Koin 依赖注入
 */
class CourseApplication : Application() {

    companion object {
        private const val TAG = "CourseApplication"

        @Volatile
        private var wasCrashLastSession: Boolean = false

        /**
         * 检查上次是否非正常退出
         * 供 UI 层调用以决定是否显示崩溃报告弹窗
         */
        fun wasLastSessionCrash(): Boolean = wasCrashLastSession
    }

    override fun onCreate() {
        super.onCreate()

        // 1. 最先安装 CrashHandler
        CrashHandler.install(this)

        // 2. 检测上次是否崩溃（基于堆栈文件是否存在）
        wasCrashLastSession = CrashHandler.wasLastSessionCrash(this)
        if (wasCrashLastSession) {
            Log.w(TAG, "检测到上次崩溃，将在 UI 层显示崩溃报告")
        }

        // 3. 初始化 AppLogger 并开启新会话
        AppLogger.init(this)
        AppLogger.startNewSession(this)
        AppLogger.i(TAG, "========== 应用启动 (CourseApplication) ==========")
        AppLogger.i(TAG, "上次是否崩溃: $wasCrashLastSession")

        // 4. 初始化 Koin 依赖注入（从 MainActivity 移至此处）
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@CourseApplication)
            modules(
                networkModule,
                databaseModule,
                appModule
            )
        }
        AppLogger.i(TAG, "Koin 依赖注入初始化完成")
    }
}
