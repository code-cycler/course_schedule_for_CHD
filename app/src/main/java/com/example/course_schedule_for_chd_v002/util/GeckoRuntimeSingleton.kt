 package com.example.course_schedule_for_chd_v002.util

import android.content.Context
import org.mozilla.geckoview.GeckoRuntime

/**
 * GeckoRuntime 单例管理器 (v43)
 *
 * GeckoRuntime 只允许一个实例存在，使用全局单例模式管理。
 * 解决 GeckoViewScreen 使用 Compose remember 状态导致页面销毁后状态丢失，
 * 再次进入时尝试创建新实例崩溃的问题。
 *
 * 错误日志：
 * java.lang.IllegalStateException: Only one GeckoRuntime instance is allowed
 */
object GeckoRuntimeSingleton {
    @Volatile
    private var runtime: GeckoRuntime? = null

    /**
     * 获取或创建 GeckoRuntime 实例
     * 线程安全，确保只创建一次
     *
     * @param context Android Context（建议使用 ApplicationContext）
     * @return GeckoRuntime 单例实例
     */
    fun getOrCreate(context: Context): GeckoRuntime {
        return runtime ?: synchronized(this) {
            runtime ?: GeckoRuntime.create(context.applicationContext).also {
                runtime = it
                android.util.Log.i("GeckoRuntimeSingleton", "[v43] GeckoRuntime instance created")
            }
        }
    }

    /**
     * 检查是否已创建实例
     *
     * @return true 如果已初始化，false 否则
     */
    fun isInitialized(): Boolean = runtime != null

    /**
     * 获取当前实例（如果已初始化）
     *
     * @return GeckoRuntime 实例，如果未初始化则返回 null
     */
    fun getInstance(): GeckoRuntime? = runtime
}
