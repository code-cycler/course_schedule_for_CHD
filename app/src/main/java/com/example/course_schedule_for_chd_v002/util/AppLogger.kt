package com.example.course_schedule_for_chd_v002.util

import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedList
import java.util.Locale

/**
 * [v107] 应用日志缓存管理器
 *
 * 功能:
 * - 在内存中缓存应用启动后的所有日志
 * - 同时写入 Android logcat
 * - 持久化日志到磁盘（每 50 条刷写一次）
 * - 会话管理：保留最近 5 次使用日志
 * - 崩溃时可恢复上次会话日志
 *
 * 使用方式:
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

    // 最大缓存条数（放宽存储限制，从 5000 提升到 20000）
    private const val MAX_CACHE_SIZE = 20000

    // ================ 磁盘持久化相关 ================

    private const val SESSION_LOGS_DIR = "session_logs"
    private const val MAX_SESSIONS = 5
    private const val FLUSH_INTERVAL = 50 // 每 50 条日志刷写一次磁盘

    private var sessionLogsDir: File? = null
    private var currentSessionFile: File? = null
    private var currentSessionId: String = ""
    private var lastFlushedIndex: Int = 0
    private var logCountSinceLastFlush: Int = 0
    private var isInitialized: Boolean = false

    /**
     * 初始化 AppLogger，传入 Application context
     * 必须在 Application.onCreate() 中调用
     */
    fun init(context: Context) {
        if (isInitialized) return

        val dir = File(context.filesDir, SESSION_LOGS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        sessionLogsDir = dir
        isInitialized = true
        Log.i(TAG, "AppLogger 初始化完成, 日志目录: ${dir.absolutePath}")
    }

    /**
     * 开始新的会话日志文件
     * 在 Application.onCreate() 中 init() 之后调用
     */
    fun startNewSession(context: Context) {
        if (!isInitialized) return

        // 生成会话 ID
        val sessionFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        currentSessionId = "session_${sessionFormat.format(Date())}"

        // 创建会话文件
        currentSessionFile = File(sessionLogsDir, "$currentSessionId.log")
        lastFlushedIndex = 0

        // 写入会话头部信息
        try {
            val header = buildSessionHeader(context)
            FileWriter(currentSessionFile!!, false).use { writer ->
                writer.write(header)
            }
        } catch (e: Exception) {
            Log.e(TAG, "写入会话头部失败", e)
        }

        // 清理旧会话
        rotateSessions()

        Log.i(TAG, "新会话开始: $currentSessionId")
    }

    /**
     * 构建会话头部信息
     */
    private fun buildSessionHeader(context: Context): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val startTime = dateFormat.format(Date())

        var versionName = "未知"
        var versionCode = 0L
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            versionName = packageInfo.versionName ?: "未知"
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (_: Exception) {}

        val deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"

        return buildString {
            appendLine("========== SESSION START ==========")
            appendLine("App: CHD课程表 $versionName ($versionCode)")
            appendLine("Device: $deviceInfo")
            appendLine("Session: $currentSessionId")
            appendLine("Start: $startTime")
            appendLine("Process ID: ${Process.myPid()}")
            appendLine("====================================")
            appendLine()
        }
    }

    /**
     * 将内存中的日志刷新到磁盘
     * 每累积 FLUSH_INTERVAL 条日志自动调用，崩溃时也调用
     */
    fun flushToDisk() {
        if (!isInitialized) return

        val file = currentSessionFile ?: return

        try {
            synchronized(logCache) {
                if (lastFlushedIndex >= logCache.size) return

                val newLogs = logCache.subList(lastFlushedIndex, logCache.size)
                FileWriter(file, true).use { writer ->
                    for (entry in newLogs) {
                        writer.write("${entry.timestamp} ${entry.level}/${entry.tag}: ${entry.message}\n")
                    }
                }
                lastFlushedIndex = logCache.size
                logCountSinceLastFlush = 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "刷新日志到磁盘失败", e)
        }
    }

    /**
     * 清理旧会话，只保留最近 MAX_SESSIONS 个
     */
    private fun rotateSessions() {
        val dir = sessionLogsDir ?: return

        try {
            val sessionFiles = dir.listFiles { file ->
                file.name.startsWith("session_") && file.name.endsWith(".log")
            }?.sortedBy { it.name } ?: return

            if (sessionFiles.size >= MAX_SESSIONS) {
                val toDelete = sessionFiles.dropLast(MAX_SESSIONS - 1)
                for (file in toDelete) {
                    file.delete()
                    Log.d(TAG, "清理旧会话日志: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理旧会话失败", e)
        }
    }

    /**
     * 获取上一个会话的日志（崩溃时用于恢复）
     * 返回倒数第二个会话文件的路径
     */
    fun getPreviousSessionLogFile(): File? {
        val dir = sessionLogsDir ?: return null

        val sessionFiles = dir.listFiles { file ->
            file.name.startsWith("session_") && file.name.endsWith(".log")
        }?.sortedBy { it.name } ?: return null

        // 排除当前会话，取最后一个（即上一次的会话）
        val previousSessions = sessionFiles.filter { it.name != "$currentSessionId.log" }
        return previousSessions.lastOrNull()
    }

    /**
     * 获取上一个会话的日志内容
     */
    fun getPreviousSessionLog(): String? {
        return try {
            getPreviousSessionLogFile()?.readText()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取崩溃堆栈内容
     */
    fun getLatestCrashStackTrace(): String? {
        val dir = sessionLogsDir ?: return null
        val crashFile = File(dir, "crash_stacktrace.txt")
        return if (crashFile.exists()) {
            try { crashFile.readText() } catch (_: Exception) { null }
        } else {
            null
        }
    }

    /**
     * 获取所有会话 ID 列表
     */
    fun getAllSessionIds(): List<String> {
        val dir = sessionLogsDir ?: return emptyList()
        return dir.listFiles { file ->
            file.name.startsWith("session_") && file.name.endsWith(".log")
        }?.map { it.name }?.sorted() ?: emptyList()
    }

    /**
     * 获取会话日志目录下的文件总大小
     */
    fun getSessionLogsTotalSize(): Long {
        val dir = sessionLogsDir ?: return 0L
        return dir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    // ================ 日志记录方法 ================

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
     * 添加日志到缓存，并定期刷写磁盘
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
                // 调整 flush 索引
                if (lastFlushedIndex > 0) lastFlushedIndex--
            }
            logCache.add(entry)
        }

        // 定期刷写磁盘
        logCountSinceLastFlush++
        if (logCountSinceLastFlush >= FLUSH_INTERVAL && isInitialized) {
            // 使用后台线程刷写，避免阻塞调用方
            Thread { flushToDisk() }.start()
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
            lastFlushedIndex = 0
            logCountSinceLastFlush = 0
        }
        Log.i(TAG, "日志缓存已清空")
    }
}
