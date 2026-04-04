package com.example.course_schedule_for_chd_v002.util

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.example.course_schedule_for_chd_v002.domain.model.Course
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 课程识别错误报告生成器
 *
 * 收集原始 HTML、解析后的课程数据、解析日志等 debug 信息，
 * 自动脱敏后生成报告文件，支持通过 Android ShareSheet 分享。
 */
object ReportGenerator {

    private const val TAG = "ReportGenerator"
    private const val FILE_PREFIX = "chd_course_report"
    private const val REPORT_DIR_NAME = "CHD课程表/reports"
    private const val MAX_HTML_EXCERPT = 100 * 1024 // 100KB

    /**
     * 报告配置
     */
    data class ReportConfig(
        val semester: String,
        val userDescription: String,
        val targetCourse: Course? = null,
        val includeCourses: Boolean = true,
        val includeHtml: Boolean = true,
        val includeLogs: Boolean = true
    )

    /**
     * 报告生成结果
     */
    data class ReportResult(
        val success: Boolean,
        val file: File? = null,
        val error: String? = null,
        val fileSize: Long = 0
    ) {
        val formattedSize: String
            get() = when {
                fileSize < 1024 -> "$fileSize B"
                fileSize < 1024 * 1024 -> String.format("%.1f KB", fileSize / 1024.0)
                else -> String.format("%.1f MB", fileSize / (1024.0 * 1024.0))
            }
    }

    // 需要过滤的日志 TAG
    private val REPORT_LOG_TAGS = listOf(
        "CHD_WeekType",
        "CHD_CurrentWeek",
        "ScheduleHtmlParser",
        "WebViewLogger",
        "CourseRepository"
    )

    /**
     * 生成课程识别错误报告
     */
    suspend fun generateReport(
        context: Context,
        courses: List<Course>,
        config: ReportConfig
    ): ReportResult {
        return try {
            AppLogger.i(TAG, "开始生成课程识别错误报告...")

            val content = buildReportContent(context, courses, config)

            val file = getReportFile(context)
            file.parentFile?.mkdirs()

            FileWriter(file).use { writer ->
                writer.write(content)
            }

            val fileSize = file.length()
            AppLogger.i(TAG, "报告生成成功: ${file.absolutePath}, 大小: ${fileSize / 1024}KB")

            ReportResult(
                success = true,
                file = file,
                fileSize = fileSize
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "生成报告失败: ${e.message}", e)
            ReportResult(
                success = false,
                error = "生成报告失败: ${e.message}"
            )
        }
    }

    /**
     * 构建报告内容
     */
    private fun buildReportContent(
        context: Context,
        courses: List<Course>,
        config: ReportConfig
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())

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
            // 文件头
            appendLine("========================================")
            appendLine("CHD 课程识别错误报告")
            appendLine("时间: $timestamp")
            appendLine("应用版本: $versionName ($versionCode)")
            appendLine("设备: $deviceInfo")
            appendLine("学期: ${config.semester}")
            appendLine("[已自动脱敏] 学号/手机号等敏感信息已替换")
            appendLine("========================================")
            appendLine()

            // 用户描述
            appendLine("## 用户描述")
            if (config.userDescription.isNotBlank()) {
                appendLine(config.userDescription)
            } else {
                appendLine("(用户未填写描述)")
            }
            appendLine()

            // 问题课程
            config.targetCourse?.let { course ->
                appendLine("## 问题课程")
                appendLine("课程名: ${course.name}")
                appendLine("教师: ${course.teacher}")
                appendLine("教室: ${course.location}")
                appendLine("星期: ${getDayDisplayName(course.dayOfWeek.value)}")
                appendLine("节次: 第${course.startNode}-${course.endNode}节")
                appendLine("周次: ${course.getWeeksDisplayText()}")
                appendLine("startWeek: ${course.startWeek}, endWeek: ${course.endWeek}")
                appendLine("备注: ${course.remark}")
                appendLine("活跃周: ${course.getActiveWeeks()}")
                appendLine()
            }

            // 已解析课程
            if (config.includeCourses && courses.isNotEmpty()) {
                appendLine("## 已解析课程 (${courses.size} 门)")
                val json = JsonUtils.exportCoursesToJson(courses)
                appendLine(json)
                appendLine()
            }

            // 原始 HTML 摘录
            if (config.includeHtml) {
                appendLine("## 原始 HTML 摘录 (已脱敏)")
                val html = HtmlCache.getHtml(config.semester)
                if (html != null) {
                    val meta = HtmlCache.getMeta(config.semester)
                    val originalLength = meta?.get("originalLength")?.toIntOrNull() ?: html.length
                    val wasTruncated = meta?.get("truncated")?.toBoolean() ?: false

                    appendLine("原始长度: $originalLength 字符${if (wasTruncated) " (已截断至 500KB)" else ""}")

                    // 如果有目标课程，尝试定位相关 HTML 片段
                    val excerpt = if (config.targetCourse != null) {
                        findRelevantHtmlExcerpt(html, config.targetCourse.name)
                    } else {
                        html
                    }

                    val finalExcerpt = if (excerpt.length > MAX_HTML_EXCERPT) {
                        excerpt.substring(0, MAX_HTML_EXCERPT) + "\n... (截断，共 ${excerpt.length} 字符)"
                    } else {
                        excerpt
                    }

                    appendLine(anonymize(finalExcerpt))
                } else {
                    appendLine("(无缓存的 HTML 数据。需要重新登录获取课表后才能包含原始 HTML。)")
                }
                appendLine()
            }

            // 解析日志
            if (config.includeLogs) {
                appendLine("## 解析日志 (已脱敏)")
                val logs = AppLogger.getAllLogs()
                    .filter { it.tag in REPORT_LOG_TAGS }

                if (logs.isNotEmpty()) {
                    for (entry in logs) {
                        appendLine("${entry.timestamp} ${entry.level}/${entry.tag}: ${anonymize(entry.message)}")
                    }
                } else {
                    appendLine("(无相关解析日志)")
                }
                appendLine()
            }
        }
    }

    /**
     * 查找与目标课程名相关的 HTML 片段
     */
    private fun findRelevantHtmlExcerpt(html: String, courseName: String): String {
        // 尝试找到包含课程名的片段
        val searchName = courseName.substringBefore("(").trim()
        val index = html.indexOf(searchName)

        return if (index >= 0) {
            // 找到课程名，提取周围 20KB 的上下文
            val start = maxOf(0, index - 5000)
            val end = minOf(html.length, index + 15000)
            buildString {
                if (start > 0) append("...(前文省略)...\n")
                append(html.substring(start, end))
                if (end < html.length) append("\n...(后文省略)...")
            }
        } else {
            // 未找到课程名，返回前 50KB
            if (html.length > 50 * 1024) {
                html.substring(0, 50 * 1024) + "\n...(截断)"
            } else {
                html
            }
        }
    }

    /**
     * 自动脱敏
     *
     * 替换以下敏感信息：
     * - 学号（10-13位纯数字）
     * - 手机号（1开头11位数字）
     * - HTML 中的中文姓名（name:"xxx" 模式）
     */
    private fun anonymize(text: String): String {
        var result = text

        // 替换手机号（1开头的11位数字，前后非数字）
        result = result.replace(
            Regex("""(?<!\d)1[3-9]\d{9}(?!\d)"""),
            "[REDACTED_PHONE]"
        )

        // 替换学号（10-13位纯数字，前后非数字，排除年份模式如 2025-2026）
        result = result.replace(
            Regex("""(?<!\d)(?!\d{4}-\d{4})\d{10,13}(?!\d)"""),
            "[REDACTED_ID]"
        )

        // 替换 HTML 中 JavaScript 的 name 字段值（中文姓名）
        result = result.replace(
            Regex("""name\s*:\s*"([\u4e00-\u9fff]{2,4})""""),
            """name:"[REDACTED_NAME]""""
        )

        // 替换 actTeacherName 中的中文姓名
        result = result.replace(
            Regex("""actTeacherName\.push\(\s*actTeachers\[i\]\.name\s*\)"""),
            "actTeacherName.push(\"[REDACTED_NAME]\")"
        )

        return result
    }

    /**
     * 获取报告文件路径
     */
    private fun getReportFile(context: Context): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "${FILE_PREFIX}_$timestamp.txt"

        // 优先使用公共下载目录
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )
            val reportDir = File(downloadsDir, REPORT_DIR_NAME)
            if (reportDir.exists() || reportDir.mkdirs()) {
                return File(reportDir, fileName)
            }
        }

        // 回退到应用私有目录
        val logDir = File(context.getExternalFilesDir(null), "reports")
        logDir.mkdirs()
        return File(logDir, fileName)
    }

    /**
     * 创建分享报告文件的 Intent
     */
    fun shareReport(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "CHD课程识别错误报告")
            putExtra(Intent.EXTRA_TEXT, "请查看附件中的课程识别错误报告")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * 获取星期显示名
     */
    private fun getDayDisplayName(dayValue: Int): String {
        return when (dayValue) {
            1 -> "周一"
            2 -> "周二"
            3 -> "周三"
            4 -> "周四"
            5 -> "周五"
            6 -> "周六"
            7 -> "周日"
            else -> "未知"
        }
    }
}
