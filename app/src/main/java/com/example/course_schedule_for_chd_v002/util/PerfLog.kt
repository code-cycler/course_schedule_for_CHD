package com.example.course_schedule_for_chd_v002.util

import android.util.Log

/**
 * [v123] 性能打点：关键场景入口/出口耗时日志，tag 统一 PerfLog。
 * 真机 `adb logcat -s PerfLog` 可核读三场景耗时（L2-build B 批 DoD 依据）。
 */
object PerfLog {
    private const val TAG = "PerfLog"
    const val SCENE_LOAD = "load_schedule"
    const val SCENE_WEEK = "week_switch"
    const val SCENE_EXPORT = "export"

    inline fun <T> timed(scene: String, block: () -> T): T {
        val start = begin(scene)
        try {
            return block()
        } finally {
            end(scene, start)
        }
    }

    /** 手动打点（body 过长不便包 lambda 时用）：返回开始时刻 */
    fun begin(scene: String): Long {
        Log.d(TAG, "[$scene] start")
        return System.currentTimeMillis()
    }

    /** 手动打点收尾：输出耗时 */
    fun end(scene: String, start: Long) {
        Log.d(TAG, "[$scene] took ${System.currentTimeMillis() - start}ms")
    }
}
