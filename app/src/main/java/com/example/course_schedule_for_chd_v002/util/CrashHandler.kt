package com.example.course_schedule_for_chd_v002.util

import android.content.Context
import android.os.Build
import android.os.Process
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局未捕获异常处理器
 *
 * 功能:
 * - 捕获未处理的异常，将崩溃堆栈写入磁盘
 * - 尽力刷新 AppLogger 内存日志到磁盘
 * - 委托给默认处理器让系统显示崩溃对话框
 *
 * 注意: 使用独立的 SharedPreferences 而非 DataStore，
 * 因为 DataStore 是异步的，崩溃场景下不可靠
 */
object CrashHandler {

    private const val TAG = "CrashHandler"
    private const val CRASH_TRACE_FILE = "crash_stacktrace.txt"
    private const val SESSION_LOGS_DIR = "session_logs"

    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var filesDir: String = ""

    /**
     * 安装全局异常处理器
     * 应在 Application.onCreate() 最开始调用
     */
    fun install(context: Context) {
        filesDir = context.filesDir.absolutePath
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleCrash(thread, throwable)
        }
    }

    /**
     * 处理未捕获的异常
     */
    private fun handleCrash(thread: Thread, throwable: Throwable) {
        try {
            // 1. 确保目录存在
            val logDir = File(filesDir, SESSION_LOGS_DIR)
            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            // 2. 写入崩溃堆栈
            writeCrashStackTrace(throwable)

            // 3. 尽力刷新 AppLogger 内存日志到磁盘
            try {
                AppLogger.flushToDisk()
            } catch (_: Exception) {
                // 崩溃时可能无法刷新，忽略
            }

        } catch (_: Exception) {
            // 处理器自身出错不应导致二次崩溃
        } finally {
            // 委托给默认处理器
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * 将崩溃堆栈写入文件
     */
    private fun writeCrashStackTrace(throwable: Throwable) {
        val crashFile = File(filesDir, "$SESSION_LOGS_DIR/$CRASH_TRACE_FILE")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

        try {
            FileWriter(crashFile, false).use { writer ->
                writer.write("========== CRASH REPORT ==========\n")
                writer.write("Time: ${dateFormat.format(Date())}\n")
                writer.write("Thread: ${Thread.currentThread().name}\n")
                writer.write("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})\n")
                writer.write("Process ID: ${Process.myPid()}\n")
                writer.write("==================================\n\n")

                // 写入异常堆栈
                val stringWriter = StringWriter()
                throwable.printStackTrace(PrintWriter(stringWriter))
                writer.write(stringWriter.toString())

                // 如果有 cause，也写入
                var cause = throwable.cause
                while (cause != null) {
                    writer.write("\n\nCaused by:\n")
                    val causeWriter = StringWriter()
                    cause.printStackTrace(PrintWriter(causeWriter))
                    writer.write(causeWriter.toString())
                    cause = cause.cause
                }
            }
        } catch (_: Exception) {
            // 写入失败忽略
        }
    }

    /**
     * 检查上次是否崩溃
     *
     * 以崩溃堆栈文件是否存在作为判断依据，而非 session_active 标志。
     * 原因: onTerminate() 在真机上不会调用，导致 session_active 无法
     * 被重置，用户正常上滑移除 APP 也会被误判为崩溃。
     *
     * crash_stacktrace.txt 仅在 handleCrash() 中写入，而 handleCrash()
     * 只在真正发生未捕获异常时触发，因此是可靠的崩溃指标。
     */
    fun wasLastSessionCrash(context: Context): Boolean {
        val stacktraceFile = File(context.filesDir, "$SESSION_LOGS_DIR/$CRASH_TRACE_FILE")
        return stacktraceFile.exists()
    }

    /**
     * 获取崩溃堆栈文件内容
     */
    fun getCrashStackTrace(filesDir: String): String? {
        val file = File(filesDir, "$SESSION_LOGS_DIR/$CRASH_TRACE_FILE")
        return if (file.exists()) file.readText() else null
    }

    /**
     * 删除崩溃堆栈文件（用户处理完毕后调用）
     */
    fun deleteCrashStackTrace(context: Context) {
        val file = File(context.filesDir, "$SESSION_LOGS_DIR/$CRASH_TRACE_FILE")
        if (file.exists()) {
            file.delete()
        }
    }
}
