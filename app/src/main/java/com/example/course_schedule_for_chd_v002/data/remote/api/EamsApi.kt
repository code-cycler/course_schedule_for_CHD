package com.example.course_schedule_for_chd_v002.data.remote.api

import com.example.course_schedule_for_chd_v002.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

private const val TAG = "EamsApi"

/**
 * 教务系统 API
 * 处理长安大学教务系统（EAMS）的课表获取等功能
 *
 * 主要功能：
 * - 验证登录状态
 * - 获取学生ID
 * - 获取课表HTML
 * - 获取学生信息
 */
class EamsApi(private val client: OkHttpClient) {

    /**
     * 访问教务系统首页，验证登录状态
     * @return 是否已登录
     */
    suspend fun accessHomePage(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d(TAG, "=== accessHomePage 开始 ===")
            android.util.Log.d(TAG, "请求 URL: ${Constants.EamsUrls.HOME_PAGE}")

            val request = Request.Builder()
                .url(Constants.EamsUrls.HOME_PAGE)
                .get()
                .build()

            val response = client.newCall(request).execute()
            android.util.Log.d(TAG, "响应状态: ${response.code}")

            if (!response.isSuccessful) {
                android.util.Log.w(TAG, "HTTP 错误: ${response.code}")
                return@withContext Result.success(false)
            }

            val html = response.body?.string() ?: return@withContext Result.success(false)
            android.util.Log.d(TAG, "HTML 长度: ${html.length}")

            // 检查是否包含登录后的特征
            val checks = mapOf(
                "logout" to html.contains("logout"),
                "signOut" to html.contains("signOut"),
                "courseTableForStd" to html.contains("courseTableForStd"),
                "个人信息" to html.contains("个人信息"),
                "退出" to html.contains("退出")
            )
            android.util.Log.d(TAG, "登录特征检查: $checks")

            val isLoggedIn = checks.values.any { it }
            android.util.Log.i(TAG, "accessHomePage 结果: $isLoggedIn")

            Result.success(isLoggedIn)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "accessHomePage 异常: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 获取学生 ID（访问课表需要）
     * @return 学生ID，失败返回 null
     */
    suspend fun getStudentId(): Result<Long> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d(TAG, "=== getStudentId 开始 ===")
            android.util.Log.d(TAG, "请求 URL: ${Constants.EamsUrls.COURSE_TABLE}")

            // 先访问课表页面获取学生 ID
            val request = Request.Builder()
                .url(Constants.EamsUrls.COURSE_TABLE)
                .get()
                .build()

            val response = client.newCall(request).execute()
            android.util.Log.d(TAG, "响应状态: ${response.code}")

            val html = response.body?.string()
                ?: return@withContext Result.failure(Exception("[X] Empty response"))

            android.util.Log.d(TAG, "HTML 长度: ${html.length}")

            // 打印 HTML 片段用于调试（查找 studentId 相关内容）
            val studentIdPatterns = listOf(
                "studentId", "ids", "student_id", "student"
            )
            for (keyword in studentIdPatterns) {
                val index = html.indexOf(keyword, ignoreCase = true)
                if (index >= 0) {
                    val start = maxOf(0, index - 50)
                    val end = minOf(html.length, index + 100)
                    android.util.Log.d(TAG, "找到 '$keyword' 附近内容: ${html.substring(start, end)}")
                }
            }

            // 从页面中提取学生 ID
            // 格式通常类似: var studentId = 123456; 或 "ids","123456"
            val patterns = listOf(
                """var\s+studentId\s*=\s*(\d+)""".toRegex(),
                """"ids"\s*,\s*"?(\d+)"?""".toRegex(),
                """studentId\s*=\s*['"]?(\d+)['"]?""".toRegex(),
                """id="studentId"[^>]*value="(\d+)"""".toRegex(),
                // 新增: 查找 form 中的 ids input
                """name="ids"[^>]*value="(\d+)"""".toRegex(),
                """value="(\d+)"[^>]*name="ids"""".toRegex(),
                // 新增: 查找 jQuery grid 中的 ids
                """\bg\.ids\s*=\s*["']?(\d+)["']?""".toRegex()
            )

            for ((index, pattern) in patterns.withIndex()) {
                val match = pattern.find(html)
                android.util.Log.d(TAG, "正则 $index (${pattern.pattern}): ${if (match != null) "匹配成功: ${match.groupValues[1]}" else "未匹配"}")
                if (match != null) {
                    val studentId = match.groupValues[1].toLong()
                    android.util.Log.i(TAG, "[OK] 提取到 studentId: $studentId")
                    return@withContext Result.success(studentId)
                }
            }

            android.util.Log.e(TAG, "[X] 所有正则都无法提取 studentId")
            Result.failure(Exception("[X] Cannot extract student ID from page"))
        } catch (e: Exception) {
            android.util.Log.e(TAG, "getStudentId 异常: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 获取课程表 HTML
     * @param semester 学期ID（教务系统内部ID，非学期字符串）
     * @param studentId 学生ID
     * @return 课表页面的HTML
     */
    suspend fun getCourseTableHtml(
        semester: String? = null,
        studentId: Long? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d(TAG, "=== getCourseTableHtml 开始 ===")
            android.util.Log.d(TAG, "参数: semester=$semester, studentId=$studentId")

            // 如果没有提供学生ID，先获取
            android.util.Log.d(TAG, "获取学生 ID...")
            val sid = studentId ?: getStudentId().getOrNull()
            android.util.Log.d(TAG, "学生 ID 结果: sid=$sid")

            if (sid == null) {
                android.util.Log.e(TAG, "[X] 无法获取学生 ID")
                return@withContext Result.failure(Exception("[X] Cannot get student ID"))
            }

            // 构建请求
            val formBuilder = FormBody.Builder()
                .add("ids", sid.toString())

            // 如果提供了学期ID，添加到请求中
            if (semester != null) {
                formBuilder.add("semester.id", semester)
                android.util.Log.d(TAG, "添加学期参数: semester.id=$semester")
            }

            android.util.Log.d(TAG, "POST 请求 URL: ${Constants.EamsUrls.COURSE_TABLE}")
            android.util.Log.d(TAG, "表单参数: ids=$sid, semester.id=$semester")

            val request = Request.Builder()
                .url(Constants.EamsUrls.COURSE_TABLE)
                .post(formBuilder.build())
                .build()

            val response = client.newCall(request).execute()
            android.util.Log.d(TAG, "响应状态: ${response.code}")

            if (!response.isSuccessful) {
                android.util.Log.e(TAG, "[X] HTTP 错误: ${response.code}")
                return@withContext Result.failure(Exception("[X] HTTP ${response.code}"))
            }

            val html = response.body?.string()
            android.util.Log.d(TAG, "响应 HTML 长度: ${html?.length ?: "null"}")

            if (html == null) {
                android.util.Log.e(TAG, "[X] 响应为空")
                return@withContext Result.failure(Exception("[X] Empty response"))
            }

            // 检查是否包含课表内容
            val hasCourseTable = html.contains("courseTable") || html.contains("课程表") || html.contains("周")
            android.util.Log.d(TAG, "包含课表内容: $hasCourseTable")

            android.util.Log.i(TAG, "[OK] getCourseTableHtml 成功")
            Result.success(html)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "getCourseTableHtml 异常: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 获取当前用户姓名
     * @return 学生姓名
     */
    suspend fun getStudentName(): Result<String> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d(TAG, "=== getStudentName 开始 ===")

            val request = Request.Builder()
                .url(Constants.EamsUrls.HOME_PAGE)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string()
                ?: return@withContext Result.failure(Exception("[X] Empty response"))

            android.util.Log.d(TAG, "HTML 长度: ${html.length}")

            val doc = Jsoup.parse(html)

            // 尝试多种选择器查找学生姓名
            val nameSelectors = listOf(
                ".user-name",
                "#userName",
                "span[class*=name]",
                ".userinfo .name",
                ".navbar .username"
            )

            for (selector in nameSelectors) {
                val name = doc.select(selector).text()
                android.util.Log.d(TAG, "选择器 '$selector': '$name'")
                if (name.isNotEmpty()) {
                    // 清理可能的前后缀
                    val cleanName = name.replace("欢迎您，", "")
                        .replace("同学", "")
                        .trim()
                    if (cleanName.isNotEmpty()) {
                        android.util.Log.i(TAG, "[OK] 找到姓名: $cleanName")
                        return@withContext Result.success(cleanName)
                    }
                }
            }

            // 如果选择器都失败，打印 HTML 中可能包含姓名的部分
            val namePatterns = listOf("欢迎", "同学", "姓名", "user", "name")
            for (pattern in namePatterns) {
                val index = html.indexOf(pattern, ignoreCase = true)
                if (index >= 0) {
                    val start = maxOf(0, index - 30)
                    val end = minOf(html.length, index + 50)
                    android.util.Log.d(TAG, "找到 '$pattern' 附近: ${html.substring(start, end)}")
                }
            }

            android.util.Log.e(TAG, "[X] 无法提取学生姓名")
            Result.failure(Exception("[X] Cannot get student name"))
        } catch (e: Exception) {
            android.util.Log.e(TAG, "getStudentName 异常: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 获取完整的课表页面（包含学期列表等）
     * @return 课表页面HTML
     */
    suspend fun getCourseTablePage(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(Constants.EamsUrls.COURSE_TABLE)
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("[X] HTTP ${response.code}"))
            }

            val html = response.body?.string()
                ?: return@withContext Result.failure(Exception("[X] Empty response"))

            Result.success(html)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取可用学期列表
     * @return 学期ID列表
     */
    suspend fun getSemesters(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val html = getCourseTablePage().getOrNull()
                ?: return@withContext Result.failure(Exception("[X] Cannot get course table page"))

            val doc = Jsoup.parse(html)

            // 从学期下拉框中提取学期列表
            val options = doc.select("#semester option, select[name=semester] option")
            val semesters = options.mapNotNull { option ->
                option.attr("value").takeIf { it.isNotEmpty() }
            }

            Result.success(semesters)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
