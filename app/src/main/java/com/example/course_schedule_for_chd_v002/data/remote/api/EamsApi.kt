package com.example.course_schedule_for_chd_v002.data.remote.api

import com.example.course_schedule_for_chd_v002.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

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
            val request = Request.Builder()
                .url(Constants.EamsUrls.HOME_PAGE)
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.success(false)
            }

            val html = response.body?.string() ?: return@withContext Result.success(false)

            // 检查是否包含登录后的特征
            val isLoggedIn = html.contains("logout") ||
                    html.contains("signOut") ||
                    html.contains("courseTableForStd") ||
                    html.contains("个人信息") ||
                    html.contains("退出")

            Result.success(isLoggedIn)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取学生 ID（访问课表需要）
     * @return 学生ID，失败返回 null
     */
    suspend fun getStudentId(): Result<Long> = withContext(Dispatchers.IO) {
        try {
            // 先访问课表页面获取学生 ID
            val request = Request.Builder()
                .url(Constants.EamsUrls.COURSE_TABLE)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string()
                ?: return@withContext Result.failure(Exception("[X] Empty response"))

            // 从页面中提取学生 ID
            // 格式通常类似: var studentId = 123456; 或 "ids","123456"
            val patterns = listOf(
                """var\s+studentId\s*=\s*(\d+)""".toRegex(),
                """"ids"\s*,\s*"?(\d+)"?""".toRegex(),
                """studentId\s*=\s*['"]?(\d+)['"]?""".toRegex(),
                """id="studentId"[^>]*value="(\d+)"""".toRegex()
            )

            for (pattern in patterns) {
                val match = pattern.find(html)
                if (match != null) {
                    val studentId = match.groupValues[1].toLong()
                    return@withContext Result.success(studentId)
                }
            }

            Result.failure(Exception("[X] Cannot extract student ID from page"))
        } catch (e: Exception) {
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
            // 如果没有提供学生ID，先获取
            val sid = studentId ?: getStudentId().getOrNull()
                ?: return@withContext Result.failure(Exception("[X] Cannot get student ID"))

            // 构建请求
            val formBuilder = FormBody.Builder()
                .add("ids", sid.toString())

            // 如果提供了学期ID，添加到请求中
            if (semester != null) {
                formBuilder.add("semester.id", semester)
            }

            val request = Request.Builder()
                .url(Constants.EamsUrls.COURSE_TABLE)
                .post(formBuilder.build())
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
     * 获取当前用户姓名
     * @return 学生姓名
     */
    suspend fun getStudentName(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(Constants.EamsUrls.HOME_PAGE)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string()
                ?: return@withContext Result.failure(Exception("[X] Empty response"))

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
                if (name.isNotEmpty()) {
                    // 清理可能的前后缀
                    val cleanName = name.replace("欢迎您，", "")
                        .replace("同学", "")
                        .trim()
                    if (cleanName.isNotEmpty()) {
                        return@withContext Result.success(cleanName)
                    }
                }
            }

            Result.failure(Exception("[X] Cannot get student name"))
        } catch (e: Exception) {
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
