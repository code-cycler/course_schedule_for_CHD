package com.example.course_schedule_for_chd_v002.data.remote.api

import com.example.course_schedule_for_chd_v002.domain.model.SemesterOption
import com.example.course_schedule_for_chd_v002.util.AppLogger
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
            AppLogger.d(TAG, "=== accessHomePage 开始 ===")
            AppLogger.d(TAG, "请求 URL: ${Constants.EamsUrls.HOME_PAGE}")

            val request = Request.Builder()
                .url(Constants.EamsUrls.HOME_PAGE)
                .get()
                .build()

            val response = client.newCall(request).execute()
            AppLogger.d(TAG, "响应状态: ${response.code}")

            if (!response.isSuccessful) {
                AppLogger.w(TAG, "HTTP 错误: ${response.code}")
                return@withContext Result.success(false)
            }

            val html = response.body?.string() ?: return@withContext Result.success(false)
            AppLogger.d(TAG, "HTML 长度: ${html.length}")

            // 检查是否包含登录后的特征
            val checks = mapOf(
                "logout" to html.contains("logout"),
                "signOut" to html.contains("signOut"),
                "courseTableForStd" to html.contains("courseTableForStd"),
                "个人信息" to html.contains("个人信息"),
                "退出" to html.contains("退出")
            )
            AppLogger.d(TAG, "登录特征检查: $checks")

            val isLoggedIn = checks.values.any { it }
            AppLogger.i(TAG, "accessHomePage 结果: $isLoggedIn")

            Result.success(isLoggedIn)
        } catch (e: Exception) {
            AppLogger.e(TAG, "accessHomePage 异常: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 获取教务系统首页 HTML
     * 用于解析当前教学周信息
     * @return 首页 HTML
     */
    suspend fun getHomePageHtml(): Result<String> = withContext(Dispatchers.IO) {
        try {
            AppLogger.d(TAG, "=== getHomePageHtml 开始 ===")

            val request = Request.Builder()
                .url(Constants.EamsUrls.HOME_PAGE)
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("[X] HTTP ${response.code}"))
            }

            val html = response.body?.string()
                ?: return@withContext Result.failure(Exception("[X] Empty response"))

            AppLogger.d(TAG, "首页 HTML 长度: ${html.length}")
            Result.success(html)
        } catch (e: Exception) {
            AppLogger.e(TAG, "getHomePageHtml 异常: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 获取学生 ID（访问课表需要）
     * @return 学生ID，失败返回 null
     */
    suspend fun getStudentId(): Result<Long> = withContext(Dispatchers.IO) {
        try {
            AppLogger.d(TAG, "=== getStudentId 开始 ===")
            // [Bug1 修复 2026-07-13] 原 GET Constants.EamsUrls.COURSE_TABLE（= courseTableForStd!courseTable.action）
            // 必返回 500：!courseTable action 必须 POST + ids。改 GET 入口页 courseTableForStd.action（不带感叹号），
            // 返回的 HTML 含 <input name="ids" value="...">（MCP 实测 studentId=201702）。
            // 现有正则 4 name="ids"[^>]*value="(\d+)" 可直接匹配，无需改正则。
            AppLogger.d(TAG, "请求 URL: ${Constants.EamsUrls.BASE_URL}eams/courseTableForStd.action")

            val request = Request.Builder()
                .url("${Constants.EamsUrls.BASE_URL}eams/courseTableForStd.action")
                .get()
                .build()

            val response = client.newCall(request).execute()
            AppLogger.d(TAG, "响应状态: ${response.code}")

            val html = response.body?.string()
                ?: return@withContext Result.failure(Exception("[X] Empty response"))

            AppLogger.d(TAG, "HTML 长度: ${html.length}")

            // 打印 HTML 片段用于调试（查找 studentId 相关内容）
            val studentIdPatterns = listOf(
                "studentId", "ids", "student_id", "student"
            )
            for (keyword in studentIdPatterns) {
                val index = html.indexOf(keyword, ignoreCase = true)
                if (index >= 0) {
                    val start = maxOf(0, index - 50)
                    val end = minOf(html.length, index + 100)
                    AppLogger.d(TAG, "找到 '$keyword' 附近内容: ${html.substring(start, end)}")
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
                AppLogger.d(TAG, "正则 $index (${pattern.pattern}): ${if (match != null) "匹配成功: ${match.groupValues[1]}" else "未匹配"}")
                if (match != null) {
                    val studentId = match.groupValues[1].toLong()
                    AppLogger.i(TAG, "[OK] 提取到 studentId: $studentId")
                    return@withContext Result.success(studentId)
                }
            }

            AppLogger.e(TAG, "[X] 所有正则都无法提取 studentId")
            Result.failure(Exception("[X] Cannot extract student ID from page"))
        } catch (e: Exception) {
            AppLogger.e(TAG, "getStudentId 异常: ${e.message}", e)
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
            AppLogger.d(TAG, "=== getCourseTableHtml 开始 ===")
            AppLogger.d(TAG, "参数: semester=$semester, studentId=$studentId")

            // 如果没有提供学生ID，先获取
            AppLogger.d(TAG, "获取学生 ID...")
            val sid = studentId ?: getStudentId().getOrNull()
            AppLogger.d(TAG, "学生 ID 结果: sid=$sid")

            if (sid == null) {
                AppLogger.e(TAG, "[X] 无法获取学生 ID")
                return@withContext Result.failure(Exception("[X] Cannot get student ID"))
            }

            // 构建请求
            // [Bug1 修复 2026-07-14] 必须带 setting.kind=std（beangle resource type），
            // 否则 POST !courseTable.action 返回 500 "Resource type:null"（playwright XHR 实测确认：
            // 加 setting.kind=std 后 200 + TaskActivity/table0 课表数据齐全）。
            // 同步流程走 WebView 的 bg.form.submit 自动带全字段，OkHttp 这条路径漏了 setting.kind。
            val formBuilder = FormBody.Builder()
                .add("ids", sid.toString())
                .add("setting.kind", "std")

            // 如果提供了学期ID，添加到请求中
            if (semester != null) {
                formBuilder.add("semester.id", semester)
                AppLogger.d(TAG, "添加学期参数: semester.id=$semester")
            }

            AppLogger.d(TAG, "POST 请求 URL: ${Constants.EamsUrls.COURSE_TABLE}")
            AppLogger.d(TAG, "表单参数: ids=$sid, semester.id=$semester")

            val request = Request.Builder()
                .url(Constants.EamsUrls.COURSE_TABLE)
                .post(formBuilder.build())
                .build()

            val response = client.newCall(request).execute()
            AppLogger.d(TAG, "响应状态: ${response.code}")

            if (!response.isSuccessful) {
                AppLogger.e(TAG, "[X] HTTP 错误: ${response.code}")
                return@withContext Result.failure(Exception("[X] HTTP ${response.code}"))
            }

            val html = response.body?.string()
            AppLogger.d(TAG, "响应 HTML 长度: ${html?.length ?: "null"}")

            if (html == null) {
                AppLogger.e(TAG, "[X] 响应为空")
                return@withContext Result.failure(Exception("[X] Empty response"))
            }

            // 检查是否包含课表内容
            val hasCourseTable = html.contains("courseTable") || html.contains("课程表") || html.contains("周")
            AppLogger.d(TAG, "包含课表内容: $hasCourseTable")

            AppLogger.i(TAG, "[OK] getCourseTableHtml 成功")
            Result.success(html)
        } catch (e: Exception) {
            AppLogger.e(TAG, "getCourseTableHtml 异常: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 获取当前用户姓名
     * @return 学生姓名
     */
    suspend fun getStudentName(): Result<String> = withContext(Dispatchers.IO) {
        try {
            AppLogger.d(TAG, "=== getStudentName 开始 ===")

            val request = Request.Builder()
                .url(Constants.EamsUrls.HOME_PAGE)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string()
                ?: return@withContext Result.failure(Exception("[X] Empty response"))

            AppLogger.d(TAG, "HTML 长度: ${html.length}")

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
                AppLogger.d(TAG, "选择器 '$selector': '$name'")
                if (name.isNotEmpty()) {
                    // 清理可能的前后缀
                    val cleanName = name.replace("欢迎您，", "")
                        .replace("同学", "")
                        .trim()
                    if (cleanName.isNotEmpty()) {
                        AppLogger.i(TAG, "[OK] 找到姓名: $cleanName")
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
                    AppLogger.d(TAG, "找到 '$pattern' 附近: ${html.substring(start, end)}")
                }
            }

            AppLogger.e(TAG, "[X] 无法提取学生姓名")
            Result.failure(Exception("[X] Cannot get student name"))
        } catch (e: Exception) {
            AppLogger.e(TAG, "getStudentName 异常: ${e.message}", e)
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
     * [v66] 访问课表数据页面并返回 HTML
     *
     * 流程：
     * 1. 先访问 eams 首页建立会话（如果尚未建立）
     * 2. 访问课表入口页面 courseTableForStd.action
     * 3. 服务器会重定向到 courseTableForStd!courseTable.action
     *
     * eams 系统的课表页面包含：
     * - 学生 ID: <input name="ids" value="xxx" type="hidden">
     * - 课程数据: table0.activities JavaScript 变量
     *
     * @return 课表数据页面 HTML
     */
    suspend fun accessCourseTableEntry(): Result<String> = withContext(Dispatchers.IO) {
        try {
            AppLogger.d(TAG, "=== accessCourseTableEntry 开始 (v66) ===")

            // [v66] 步骤1：先访问 eams 首页建立会话
            AppLogger.d(TAG, "[v66] 步骤1: 访问 eams 首页建立会话...")
            val homeRequest = Request.Builder()
                .url(Constants.EamsUrls.HOME_PAGE)
                .get()
                .build()

            val homeResponse = client.newCall(homeRequest).execute()
            AppLogger.d(TAG, "首页响应状态: ${homeResponse.code}")

            if (!homeResponse.isSuccessful) {
                AppLogger.e(TAG, "[X] 访问首页失败: ${homeResponse.code}")
                return@withContext Result.failure(Exception("[X] Cannot access eams home: HTTP ${homeResponse.code}"))
            }

            val homeHtml = homeResponse.body?.string()
            AppLogger.d(TAG, "首页 HTML 长度: ${homeHtml?.length ?: "null"}")

            // [v66] 步骤2：访问课表入口页面（不带感叹号，让服务器重定向）
            AppLogger.d(TAG, "[v66] 步骤2: 访问课表入口页面...")
            val entryUrl = "${Constants.EamsUrls.BASE_URL}eams/courseTableForStd.action"
            AppLogger.d(TAG, "请求 URL: $entryUrl")

            val courseRequest = Request.Builder()
                .url(entryUrl)
                .get()
                .build()

            val courseResponse = client.newCall(courseRequest).execute()
            AppLogger.d(TAG, "课表响应状态: ${courseResponse.code}")
            AppLogger.d(TAG, "最终 URL: ${courseResponse.request.url}")

            if (!courseResponse.isSuccessful) {
                AppLogger.e(TAG, "[X] HTTP 错误: ${courseResponse.code}")
                return@withContext Result.failure(Exception("[X] HTTP ${courseResponse.code}"))
            }

            val html = courseResponse.body?.string()
            AppLogger.d(TAG, "响应 HTML 长度: ${html?.length ?: "null"}")

            if (html.isNullOrEmpty()) {
                AppLogger.e(TAG, "[X] 响应为空")
                return@withContext Result.failure(Exception("[X] Empty response"))
            }

            // 检查是否包含课表相关内容（验证页面有效性）
            val hasTaskActivity = html.contains("TaskActivity")
            val hasTable0 = html.contains("table0.activities") || html.contains("table0 = new CourseTable")
            AppLogger.d(TAG, "包含课表内容: TaskActivity=$hasTaskActivity, table0=$hasTable0")

            if (!hasTaskActivity && !hasTable0) {
                AppLogger.w(TAG, "[!] HTML 可能不包含课表数据")
            }

            Result.success(html)
        } catch (e: Exception) {
            AppLogger.e(TAG, "accessCourseTableEntry 异常: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * [获取新学期] 获取可用学期选项（含教务系统 semester.id + 显示文本）
     *
     * 2026-07-12 MCP 实测修正：课表页学期切换是 jQuery semesterCalendar 组件（非 <select>），
     * 学期列表经 POST /eams/dataQuery.action {dataType=semesterCalendar} AJAX 拉取，不在课表页静态 HTML。
     * 响应是 JS 对象字面量（key 无引号、yearDom/termDom 含 HTML 引号），用正则针对性提三元组。
     *
     * label 用长格式 "$schoolYear学年第${name}学期"（如 "2025-2026学年第2学期"），
     * 便于 ScheduleHtmlParser.parseSemesterString 转成本地短串 "2025-2026-2"，复用现有 filter/匹配链。
     *
     * @return 学期选项列表，失败返回 failure（通常是 Cookie 过期 / 未登录）
     */
    suspend fun getSemesterOptions(): Result<List<SemesterOption>> = withContext(Dispatchers.IO) {
        try {
            AppLogger.d(TAG, "=== [获取新学期] getSemesterOptions 开始（POST dataQuery.action）===")
            val formBody = FormBody.Builder()
                .add("dataType", "semesterCalendar")
                .build()
            val request = Request.Builder()
                .url("${Constants.EamsUrls.BASE_URL}eams/dataQuery.action")
                .post(formBody)
                .build()

            val response = client.newCall(request).execute()
            AppLogger.d(TAG, "[获取新学期] dataQuery 响应状态: ${response.code}")
            if (!response.isSuccessful) {
                AppLogger.e(TAG, "[获取新学期] [X] HTTP ${response.code}")
                return@withContext Result.failure(Exception("[X] HTTP ${response.code}"))
            }

            val body = response.body?.string()
                ?: return@withContext Result.failure(Exception("[X] Empty response"))
            AppLogger.d(TAG, "[获取新学期] 响应长度: ${body.length}")

            // 解析委托给纯函数 parseSemesterOptionsResponse（便于单测）
            val list = parseSemesterOptionsResponse(body)
            val currentId = CURRENT_SEMESTER_ID_REGEX.find(body)?.groupValues?.get(1)
            AppLogger.i(TAG, "[获取新学期] 解析出 ${list.size} 个学期，教务系统当前 semesterId=$currentId")

            if (list.isEmpty()) {
                // 不含 semesters = Cookie 过期返回了登录页/首页 HTML；含 semesters 但 0 条 = 异常
                if (!body.contains("semesters")) {
                    val preview = body.take(300).replace("\n", " ").replace("\r", "")
                    AppLogger.w(TAG, "[获取新学期] 响应不含 semesters（Cookie 可能已过期）。前300字: $preview")
                    return@withContext Result.failure(Exception("登录已过期，请重新同步"))
                }
                AppLogger.w(TAG, "[获取新学期] 响应含 semesters 但解析出 0 条学期（异常）")
            }

            Result.success(list)
        } catch (e: Exception) {
            AppLogger.e(TAG, "[获取新学期] getSemesterOptions 异常: ${e.message}", e)
            Result.failure(e)
        }
    }

    companion object {
        private val SEMESTER_TRIPLE_REGEX = Regex("""\{id:(\d+),schoolYear:"([^"]+)",name:"(\d+)"\}""")
        private val CURRENT_SEMESTER_ID_REGEX = Regex("""semesterId:"(\d+)"""")

        /**
         * [获取新学期] 解析 dataQuery.action 的 JS 对象字面量响应为学期列表（纯函数，便于单测）。
         *
         * 响应格式（key 无引号、yearDom/termDom 含 HTML 引号，非标准 JSON）：
         * `{yearDom:"...",termDom:"...",semesters:{y0:[{id,schoolYear,name},...]},semesterId:"242"}`
         *
         * @param body dataQuery.action 响应体
         * @return 学期列表（label 长格式 "$schoolYear学年第${name}学期"）；响应不含 semesters 返回空列表
         */
        fun parseSemesterOptionsResponse(body: String): List<SemesterOption> {
            if (!body.contains("semesters")) return emptyList()
            return SEMESTER_TRIPLE_REGEX.findAll(body).map { m ->
                val (id, schoolYear, name) = m.destructured
                SemesterOption(remoteId = id, label = "${schoolYear}学年第${name}学期")
            }.toList()
        }
    }
}
