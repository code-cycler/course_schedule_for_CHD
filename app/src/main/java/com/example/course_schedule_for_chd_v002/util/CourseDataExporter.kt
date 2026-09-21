package com.example.course_schedule_for_chd_v002.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.example.course_schedule_for_chd_v002.domain.model.Course
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 课程数据导出工具类 [v120]
 *
 * 把指定学期的课程数据打包成 ZIP，供用户备份/分享/远程排障：
 * - courses_<semester>.json      识别后课程数据（全字段，含 remark/weeksBitmap，可用 JsonUtils 导回）
 * - raw_html_<semester>.html     原始课表 HTML（HtmlCache 每学期缓存，即解析器的输入）
 * - raw_html_<semester>.meta.txt 缓存元数据（抓取时间/原始长度/是否截断）
 * - README.txt                   导出信息与字段说明
 *
 * 存储位置：优先公共下载目录 Download/CHD课程表/，回退应用外部私有目录 exports/。
 * 注意：ZIP 内含姓名/学号等个人信息（原始 HTML 未脱敏），仅限自行保管或主动分享。
 */
object CourseDataExporter {

    private const val TAG = "CourseDataExporter"
    private const val FILE_PREFIX = "chd_course_data"
    private const val EXPORT_DIR_NAME = "CHD课程表"

    /**
     * 导出结果
     */
    data class ExportResult(
        val success: Boolean,
        val file: File? = null,
        val error: String? = null,
        val semester: String = "",
        val courseCount: Int = 0,
        val hasRawHtml: Boolean = false,
        val fileSize: Long = 0
    ) {
        val formattedSize: String
            get() = when {
                fileSize < 1024 -> "$fileSize B"
                fileSize < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f KB", fileSize / 1024.0)
                else -> String.format(Locale.getDefault(), "%.1f MB", fileSize / (1024.0 * 1024.0))
            }
    }

    /**
     * 导出指定学期的课程数据（识别后 JSON + 原始 HTML + 元数据 + README）为 ZIP
     *
     * @param context Context
     * @param semester 学期标识（如 "2026-2027-1"）
     * @param courses 已识别入库的课程列表（当前学期）
     */
    suspend fun exportCourseData(
        context: Context,
        semester: String,
        courses: List<Course>
    ): ExportResult {
        return try {
            Log.i(TAG, "开始导出课程数据: semester=$semester, 课程数=${courses.size}")

            if (courses.isEmpty()) {
                return ExportResult(
                    success = false,
                    error = "当前学期没有课程数据，请先同步课表",
                    semester = semester
                )
            }

            val rawHtml = HtmlCache.getHtml(semester)
            val rawMeta = HtmlCache.getMeta(semester)

            val coursesJson = JsonUtils.exportCoursesToJson(courses)
            val readme = buildReadme(context, semester, courses.size, rawHtml != null, rawMeta)

            val file = getExportFile(context, semester)
            file.parentFile?.mkdirs()

            ZipOutputStream(BufferedOutputStream(FileOutputStream(file))).use { zip ->
                fun writeEntry(name: String, content: String) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }

                writeEntry("courses_$semester.json", coursesJson)

                if (rawHtml != null) {
                    writeEntry("raw_html_$semester.html", rawHtml)

                    val metaText = rawMeta?.entries
                        ?.joinToString("\n") { "${it.key}=${it.value}" }
                        .orEmpty()
                    if (metaText.isNotBlank()) {
                        writeEntry("raw_html_$semester.meta.txt", metaText)
                    }
                }

                writeEntry("README.txt", readme)
            }

            val fileSize = file.length()
            Log.i(
                TAG,
                "课程数据导出成功: ${file.absolutePath}, 大小=$fileSize, 课程数=${courses.size}, 含原始HTML=${rawHtml != null}"
            )

            ExportResult(
                success = true,
                file = file,
                semester = semester,
                courseCount = courses.size,
                hasRawHtml = rawHtml != null,
                fileSize = fileSize
            )
        } catch (e: Exception) {
            Log.e(TAG, "导出课程数据失败", e)
            ExportResult(
                success = false,
                error = "导出失败: ${e.message}",
                semester = semester
            )
        }
    }

    /**
     * 构建 README 内容（导出信息 + 字段/位图语义说明）
     */
    private fun buildReadme(
        context: Context,
        semester: String,
        courseCount: Int,
        hasRawHtml: Boolean,
        rawMeta: Map<String, String>?
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        var versionName = "未知"
        var versionCode = 0L
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            versionName = packageInfo.versionName ?: "未知"
            versionCode = packageInfo.longVersionCode
        } catch (_: Exception) {
        }
        val deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"

        return buildString {
            appendLine("========================================")
            appendLine("CHD 课程表 - 课程数据导出")
            appendLine("导出时间: ${dateFormat.format(Date())}")
            appendLine("应用版本: $versionName ($versionCode)")
            appendLine("设备: $deviceInfo")
            appendLine("学期: $semester")
            appendLine("课程数: $courseCount")
            appendLine("========================================")
            appendLine()
            appendLine("## 文件说明")
            appendLine("- courses_$semester.json: 识别后的课程数据（App 解析教务系统并入库的结果）")
            if (hasRawHtml) {
                val htmlMeta = rawMeta?.let {
                    "（抓取时间: ${it["timestamp"] ?: "?"}, 原始长度: ${it["originalLength"] ?: "?"} 字符" +
                        (if (it["truncated"] == "true") ", 超过 500KB 已截断" else "") + "）"
                } ?: ""
                appendLine("- raw_html_$semester.html: 教务系统课表原始页面 HTML，即解析器的输入$htmlMeta")
                appendLine("- raw_html_$semester.meta.txt: 原始 HTML 的缓存元数据")
            } else {
                appendLine("- raw_html_$semester.html: 缺失（本学期没有缓存的原始 HTML，重新同步课表后再导出即可包含）")
            }
            appendLine()
            appendLine("## courses JSON 字段说明")
            appendLine("- dayOfWeek: 1=周一 ... 7=周日")
            appendLine("- startWeek/endWeek: 起止教学周；startNode/endNode: 起止节次")
            appendLine("- remark: 备注串。weeksBitmap: 后跟 53 位 0/1 位图，bitmap[i] = 第 i 周（i=0 为预备周），'1' 表示该周有课；")
            appendLine("  单双周标记（单/双）与「多班合并」标记以分号分隔")
            appendLine("- 该 JSON 可通过 App 的导入功能（importScheduleFromJson）恢复课程")
            appendLine()
            appendLine("## 隐私提示")
            appendLine("ZIP 内的原始 HTML 未脱敏，包含姓名、学号等个人信息，请自行妥善保管。")
        }
    }

    /**
     * 获取导出文件路径
     *
     * 优先使用公共下载目录 Download/CHD课程表/，失败回退应用外部私有目录 exports/
     */
    private fun getExportFile(context: Context, semester: String): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "${FILE_PREFIX}_${semester}_$timestamp.zip"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
                val exportDir = File(downloadsDir, EXPORT_DIR_NAME)
                if (exportDir.exists() || exportDir.mkdirs()) {
                    return File(exportDir, fileName)
                }
            } catch (e: Exception) {
                Log.e(TAG, "创建公共导出目录失败", e)
            }
        }

        val privateDir = File(context.getExternalFilesDir(null), "exports")
        privateDir.mkdirs()
        return File(privateDir, fileName)
    }

    /**
     * 创建分享课程数据 ZIP 的 Intent
     */
    fun shareCourseDataFile(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "CHD课程表课程数据")
            putExtra(Intent.EXTRA_TEXT, "请查看附件中的课程数据 ZIP（含识别后 JSON 与原始 HTML）")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
