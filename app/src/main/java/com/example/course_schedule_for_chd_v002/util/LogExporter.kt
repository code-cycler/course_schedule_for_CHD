package com.example.course_schedule_for_chd_v002.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日志导出工具类
 *
 * 功能：
 * - 从 logcat 获取本应用的日志
 * - 将日志导出到文件
 * - 提供分享日志文件的功能
 *
 * 存储位置：
 * - 优先：公共下载目录 (Download/CHD课程表/logs/)
 * - 回退：应用私有目录 (Android/data/包名/files/logs/)
 */
object LogExporter {

    private const val TAG = "LogExporter"

    // 需要收集的日志 TAG
    private val LOG_TAGS = listOf(
        "CHD_WebView",
        "CHD_WeekType",
        "ScheduleScreen",
        "ScheduleViewModel",
        "MainActivity",
        "ReminderManager",
        "ReminderForegroundService",
        "BootReceiver",
        "ScreenOnReceiver",
        "ChargingReceiver",
        "LogExporter"
    )

    // 日志文件名前缀
    private const val FILE_PREFIX = "chd_schedule_log"
    private const val LOG_DIR_NAME = "CHD课程表/logs"

    /**
     * 导出结果
     */
    data class ExportResult(
        val success: Boolean,
        val file: File?,
        val error: String?,
        val lineCount: Int,
        val fileSize: Long
    ) {
        val formattedSize: String
            get() = formatFileSize(fileSize)
    }

    /**
     * 导出应用日志到文件
     *
     * @param context Context
     * @return ExportResult 包含文件路径和状态
     */
    suspend fun exportLogs(context: Context): ExportResult {
        return try {
            Log.d(TAG, "开始导出日志...")

            // 1. 获取日志内容
            val logContent = captureLogs(context)

            if (logContent.isBlank()) {
                Log.w(TAG, "日志内容为空")
                return ExportResult(
                    success = false,
                    file = null,
                    error = "未捕获到日志内容，可能需要先进行一些操作",
                    lineCount = 0,
                    fileSize = 0
                )
            }

            // 2. 添加文件头信息
            val header = buildLogFileHeader(context)
            val fullContent = header + logContent

            // 3. 写入文件
            val file = getLogFile(context)
            file.parentFile?.mkdirs()

            FileWriter(file).use { writer ->
                writer.write(fullContent)
            }

            val lineCount = fullContent.count { it == '\n' }
            val fileSize = file.length()

            Log.i(TAG, "日志导出成功: ${file.absolutePath}, 大小: ${formatFileSize(fileSize)}, 行数: $lineCount")

            ExportResult(
                success = true,
                file = file,
                error = null,
                lineCount = lineCount,
                fileSize = fileSize
            )
        } catch (e: Exception) {
            Log.e(TAG, "导出日志失败", e)
            ExportResult(
                success = false,
                file = null,
                error = "导出失败: ${e.message}",
                lineCount = 0,
                fileSize = 0
            )
        }
    }

    /**
     * 从 logcat 捕获日志
     */
    private fun captureLogs(context: Context): String {
        return try {
            val processId = Process.myPid()

            // 构建日志 TAG 过滤参数
            val tagFilters = LOG_TAGS.joinToString(" ") { tag ->
                "$tag:V"
            }

            // 使用 logcat 命令获取日志
            // -d: dump 模式，只输出现有日志然后退出
            // -v time: 显示时间戳
            // --pid: 只获取当前进程的日志
            // -s: 只显示指定 TAG 的日志
            val command = "logcat -d -v time --pid=$processId -s $tagFilters"

            Log.d(TAG, "执行命令: $command")

            val process = Runtime.getRuntime().exec(command)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            reader.close()
            process.waitFor()

            output.toString()
        } catch (e: Exception) {
            Log.e(TAG, "捕获日志失败", e)
            ""
        }
    }

    /**
     * 构建日志文件头部信息
     */
    private fun buildLogFileHeader(context: Context): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val exportTime = dateFormat.format(Date())

        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = packageInfo.versionName ?: "未知"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }

        val deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"

        return buildString {
            appendLine("========================================")
            appendLine("CHD 课程表应用日志")
            appendLine("导出时间: $exportTime")
            appendLine("应用版本: $versionName ($versionCode)")
            appendLine("设备: $deviceInfo")
            appendLine("========================================")
            appendLine()
        }
    }

    /**
     * 获取日志文件
     *
     * 优先使用公共下载目录，失败时回退到应用私有目录
     */
    fun getLogFile(context: Context): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "${FILE_PREFIX}_$timestamp.txt"

        // 尝试公共下载目录
        val publicFile = getPublicLogFile(fileName)
        if (publicFile != null) {
            Log.d(TAG, "使用公共下载目录: ${publicFile.absolutePath}")
            return publicFile
        }

        // 回退到应用私有目录
        val privateFile = getPrivateLogFile(context, fileName)
        Log.d(TAG, "使用应用私有目录: ${privateFile.absolutePath}")
        return privateFile
    }

    /**
     * 获取公共下载目录中的日志文件
     * @return 如果可用则返回文件，否则返回 null
     */
    private fun getPublicLogFile(fileName: String): File? {
        // Android 10+ 不需要权限即可访问 Download 目录
        // Android 9 及以下需要检查权限
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Android 9 及以下需要存储权限
            // 如果没有权限，返回 null 使用私有目录
            return null
        }

        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val logDir = File(downloadsDir, LOG_DIR_NAME)

            if (logDir.exists() || logDir.mkdirs()) {
                File(logDir, fileName)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "创建公共目录失败", e)
            null
        }
    }

    /**
     * 获取应用私有目录中的日志文件
     */
    private fun getPrivateLogFile(context: Context, fileName: String): File {
        val logDir = File(context.getExternalFilesDir(null), "logs")
        logDir.mkdirs()
        return File(logDir, fileName)
    }

    /**
     * 检查是否有存储权限（用于 Android 9 及以下）
     */
    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            true  // Android 10+ 不需要存储权限
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 创建分享日志文件的 Intent
     */
    fun shareLogFile(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "CHD课程表日志")
            putExtra(Intent.EXTRA_TEXT, "请查看附件中的日志文件")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return shareIntent
    }

    /**
     * 获取日志文件目录
     */
    fun getLogDirectory(context: Context): File {
        // 优先返回公共目录
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            return File(downloadsDir, LOG_DIR_NAME)
        }

        // 否则返回私有目录
        return File(context.getExternalFilesDir(null), "logs")
    }

    /**
     * 格式化文件大小
     */
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}
