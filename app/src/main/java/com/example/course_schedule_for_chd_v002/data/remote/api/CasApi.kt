package com.example.course_schedule_for_chd_v002.data.remote.api

import com.example.course_schedule_for_chd_v002.data.remote.dto.CasLoginPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

/**
 * CAS 统一认证 API
 * 处理长安大学统一身份认证系统的登录流程
 *
 * 登录流程：
 * 1. 获取登录页面，解析隐藏字段（lt, execution, _eventId）
 * 2. 提交登录表单
 * 3. 处理重定向到教务系统
 *
 * @param client OkHttp 客户端
 * @param baseUrl CAS 服务器基础 URL（用于测试时注入 MockWebServer URL）
 */
class CasApi(
    private val client: OkHttpClient,
    private val baseUrl: String = CAS_BASE_URL_DEFAULT
) {

    companion object {
        private const val CAS_BASE_URL_DEFAULT = "https://ids.chd.edu.cn/authserver"
        private const val LOGIN_PATH = "/login"
    }

    /**
     * 获取登录页面信息
     * @param serviceUrl 回调服务地址（登录成功后跳转的目标）
     * @return 登录页面信息，包含隐藏字段
     */
    suspend fun getLoginPage(serviceUrl: String): Result<CasLoginPage> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl$LOGIN_PATH?service=$serviceUrl"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("[X] HTTP ${response.code}"))
            }

            val html = response.body?.string()
                ?: return@withContext Result.failure(Exception("[X] Empty response body"))

            // 使用 Jsoup 解析 HTML 提取隐藏字段
            val doc = Jsoup.parse(html)

            // 提取隐藏字段
            val lt = doc.select("input[name=lt]").attr("value")
            val execution = doc.select("input[name=execution]").attr("value")
            val eventId = doc.select("input[name=_eventId]").attr("value")

            if (lt.isEmpty() || execution.isEmpty()) {
                return@withContext Result.failure(Exception("[X] Cannot extract hidden fields from login page"))
            }

            Result.success(
                CasLoginPage(
                    lt = lt,
                    execution = execution,
                    eventId = eventId.ifEmpty { "submit" }
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 提交登录表单
     * @param username 用户名（学号）
     * @param password 密码
     * @param loginPage 登录页面信息（包含隐藏字段）
     * @param serviceUrl 回调服务地址
     * @return 登录结果
     */
    suspend fun login(
        username: String,
        password: String,
        loginPage: CasLoginPage,
        serviceUrl: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val formBody = FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .add("lt", loginPage.lt)
                .add("execution", loginPage.execution)
                .add("_eventId", loginPage.eventId)
                .build()

            val url = "$baseUrl$LOGIN_PATH?service=$serviceUrl"
            val request = Request.Builder()
                .url(url)
                .post(formBody)
                .build()

            val response = client.newCall(request).execute()

            // 检查登录结果
            // 登录成功通常会重定向到 serviceUrl
            val finalUrl = response.request.url.toString()

            if (finalUrl.contains("bkjw.chd.edu.cn") || response.code == 302) {
                // 登录成功，重定向到教务系统
                Result.success(true)
            } else {
                // 检查是否有错误信息
                val html = response.body?.string() ?: ""
                val errorMsg = extractLoginError(html)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 从响应HTML中提取登录错误信息
     */
    private fun extractLoginError(html: String): String {
        val doc = Jsoup.parse(html)

        // 尝试多种错误信息选择器
        val errorSelectors = listOf(
            ".error-message",
            ".alert-error",
            ".login-error",
            "#errorMsg",
            ".msg",
            ".error"
        )

        for (selector in errorSelectors) {
            val errorText = doc.select(selector).text()
            if (errorText.isNotEmpty()) {
                return errorText
            }
        }

        // 默认错误信息
        return "[X] Login failed, please check username and password"
    }
}
