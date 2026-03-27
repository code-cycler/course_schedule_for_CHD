package com.example.course_schedule_for_chd_v002.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedList
import java.util.Locale

/**
 * [v107] 应用日志缓存管理器
 *
 * 功能：
 * - 在内存中缓存应用启动后的所有日志
 * - 同时写入 Android logcat
 * - 应用退出后日志自动销毁
 *
 * 使用方式：
 * ```kotlin
 * AppLogger.d("TAG", "调试信息")
 * AppLogger.i("TAG", "普通信息")
 * AppLogger.w("TAG", "警告信息")
 * AppLogger.e("TAG", "错误信息", exception)
 * ```
 */
object AppLogger {

    private const val TAG = "AppLogger"

    /**
     * 日志条目数据类
     */
    data class LogEntry(
        val timestamp: String,
        val level: String,
        val tag: String,
        val message: String
    )

    // 内存日志缓存（线程安全）
    private val logCache = java.util.Collections.synchronizedList(LinkedList<LogEntry>())

    // 时间格式化
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())

    // 最大缓存条数（防止内存溢出）
    private const val MAX_CACHE_SIZE = 5000

    /**
     * 记录 DEBUG 日志
     */
    fun d(tag: String, message: String) {
        addLog("D", tag, message)
        Log.d(tag, message)
    }

    /**
     * 记录 INFO 日志
     */
    fun i(tag: String, message: String) {
        addLog("I", tag, message)
        Log.i(tag, message)
    }

    /**
     * 记录 WARNING 日志
     */
    fun w(tag: String, message: String) {
        addLog("W", tag, message)
        Log.w(tag, message)
    }

    /**
     * 记录 ERROR 日志
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val fullMessage = if (throwable != null) {
            "$message\n${Log.getStackTraceString(throwable)}"
        } else {
            message
        }
        addLog("E", tag, fullMessage)
        Log.e(tag, message, throwable)
    }

    /**
     * 添加日志到缓存
     */
    private fun addLog(level: String, tag: String, message: String) {
        val entry = LogEntry(
            timestamp = dateFormat.format(Date()),
            level = level,
            tag = tag,
            message = message
        )

        synchronized(logCache) {
            // 超过上限时移除最旧的日志
            if (logCache.size >= MAX_CACHE_SIZE) {
                logCache.removeAt(0)
            }
            logCache.add(entry)
        }
    }

    /**
     * 获取所有缓存日志
     */
    fun getAllLogs(): List<LogEntry> {
        synchronized(logCache) {
            return logCache.toList()
        }
    }

    /**
     * 获取格式化的日志内容（用于导出）
     */
    fun getFormattedLogs(): String {
        return buildString {
            synchronized(logCache) {
                for (entry in logCache) {
                    append("${entry.timestamp} ${entry.level}/${entry.tag}: ${entry.message}\n")
                }
            }
        }
    }

    /**
     * 获取日志统计信息
     */
    fun getStats(): String {
        synchronized(logCache) {
            return "缓存日志数: ${logCache.size}/$MAX_CACHE_SIZE"
        }
    }

    /**
     * 获取缓存大小
     */
    fun getCacheSize(): Int {
        synchronized(logCache) {
            return logCache.size
        }
    }

    /**
     * 清空缓存
     */
    fun clear() {
        synchronized(logCache) {
            logCache.clear()
        }
        Log.i(TAG, "日志缓存已清空")
    }
}
