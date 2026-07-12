package com.example.course_schedule_for_chd_v002.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * HTML 缓存管理器
 *
 * 在解析课程表时缓存原始 HTML 到磁盘，供错误报告使用。
 * 每个学期保留最新一份缓存，覆盖写入。
 *
 * 存储位置: filesDir/html_cache/{semester}.html
 * 元数据: filesDir/html_cache/{semester}.meta
 */
object HtmlCache {

    private const val TAG = "HtmlCache"
    private const val CACHE_DIR = "html_cache"
    private const val MAX_HTML_SIZE = 500 * 1024 // 500KB

    private var filesDir: File? = null
    private var isInitialized: Boolean = false

    /**
     * 初始化，传入 Application context
     * 应在 CourseRepositoryImpl 首次使用前调用
     */
    fun init(context: Context) {
        if (isInitialized) {
            Log.d(TAG, "HtmlCache 已经初始化，跳过")
            return
        }
        filesDir = context.filesDir
        isInitialized = true
        Log.d(TAG, "HtmlCache 初始化完成, filesDir=${filesDir?.absolutePath}")
        AppLogger.d("ScheduleViewModel", "[HtmlCache] 初始化完成, filesDir=${filesDir?.absolutePath}")
    }

    /**
     * 保存原始 HTML 到缓存
     *
     * @param semester 学期标识（如 "2025-2026-2"）
     * @param html 原始 HTML 内容
     */
    fun save(semester: String, html: String) {
        if (!isInitialized) {
            Log.w(TAG, "HtmlCache 未初始化，跳过保存")
            AppLogger.e("ScheduleViewModel", "[HtmlCache] save() 失败: 未初始化! semester=$semester, htmlLen=${html.length}")
            return
        }

        AppLogger.d("ScheduleViewModel", "[HtmlCache] save() 开始: semester=$semester, htmlLen=${html.length}")

        val dir = File(filesDir, CACHE_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }

        try {
            // 保存 HTML（超长截断）
            val htmlFile = File(dir, "${semester}.html")
            val content = if (html.length > MAX_HTML_SIZE) {
                Log.w(TAG, "HTML 超过 ${MAX_HTML_SIZE / 1024}KB，截断保存")
                html.substring(0, MAX_HTML_SIZE)
            } else {
                html
            }
            htmlFile.writeText(content)

            // 保存元数据
            val metaFile = File(dir, "${semester}.meta")
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            metaFile.writeText(buildString {
                appendLine("timestamp=${dateFormat.format(Date())}")
                appendLine("originalLength=${html.length}")
                appendLine("savedLength=${content.length}")
                appendLine("truncated=${html.length > MAX_HTML_SIZE}")
            })

            Log.d(TAG, "HTML 已缓存: semester=$semester, 长度=${html.length}")
            AppLogger.d("ScheduleViewModel", "[HtmlCache] save() 成功: semester=$semester, 长度=${html.length}, 文件=${htmlFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "缓存 HTML 失败", e)
        }
    }

    /**
     * 读取缓存的 HTML
     *
     * @param semester 学期标识
     * @return HTML 内容，不存在返回 null
     */
    fun getHtml(semester: String): String? {
        if (!isInitialized) return null

        val dir = File(filesDir, CACHE_DIR)
        val file = File(dir, "${semester}.html")
        return if (file.exists()) {
            try {
                file.readText()
            } catch (e: Exception) {
                Log.e(TAG, "读取缓存 HTML 失败", e)
                null
            }
        } else {
            null
        }
    }

    /**
     * 读取缓存元数据
     *
     * @param semester 学期标识
     * @return 元数据 Map，不存在返回 null
     */
    fun getMeta(semester: String): Map<String, String>? {
        if (!isInitialized) return null

        val dir = File(filesDir, CACHE_DIR)
        val file = File(dir, "${semester}.meta")
        return if (file.exists()) {
            try {
                file.readLines()
                    .filter { it.contains("=") }
                    .associate {
                        val parts = it.split("=", limit = 2)
                        parts[0] to parts.getOrElse(1) { "" }
                    }
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    /**
     * 检查是否存在缓存
     */
    fun hasHtml(semester: String): Boolean {
        if (!isInitialized) {
            AppLogger.d("ScheduleViewModel", "[HtmlCache] hasHtml() 返回 false: 未初始化")
            return false
        }
        val dir = File(filesDir, CACHE_DIR)
        val exists = File(dir, "${semester}.html").exists()
        AppLogger.d("ScheduleViewModel", "[HtmlCache] hasHtml($semester) = $exists")
        return exists
    }

    /**
     * 获取缓存 HTML 的大小
     */
    fun getHtmlSize(semester: String): Long {
        if (!isInitialized) return 0L
        val dir = File(filesDir, CACHE_DIR)
        val file = File(dir, "${semester}.html")
        return if (file.exists()) file.length() else 0L
    }

    /**
     * 清理指定学期的缓存
     */
    fun delete(semester: String) {
        if (!isInitialized) return

        val dir = File(filesDir, CACHE_DIR)
        File(dir, "${semester}.html").delete()
        File(dir, "${semester}.meta").delete()
        Log.d(TAG, "已清理缓存: $semester")
    }

    /**
     * 清理所有缓存
     */
    fun deleteAll() {
        if (!isInitialized) return

        val dir = File(filesDir, CACHE_DIR)
        dir.listFiles()?.forEach { it.delete() }
        Log.d(TAG, "已清理所有 HTML 缓存")
    }
}
