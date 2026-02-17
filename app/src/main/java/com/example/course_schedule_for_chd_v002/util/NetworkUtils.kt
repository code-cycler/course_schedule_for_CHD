package com.example.course_schedule_for_chd_v002.util

import kotlinx.coroutines.delay
import kotlin.math.min

/**
 * 网络工具类
 * 提供网络请求重试、错误处理等功能
 */
object NetworkUtils {

    /**
     * 带重试的执行器（指数退避）
     *
     * @param maxRetries 最大重试次数（默认3次）
     * @param initialDelay 初始延迟（毫秒）
     * @param maxDelay 最大延迟（毫秒）
     * @param block 要执行的代码块
     * @return 执行结果
     */
    suspend fun <T> retryWith(
        maxRetries: Int = 3,
        initialDelay: Long = 500,
        maxDelay: Long = 3000,
        block: suspend () -> Result<T>
    ): Result<T> {
        var currentDelay = initialDelay
        var lastResult: Result<T>? = null

        // 初始尝试 + maxRetries 次重试
        repeat(maxRetries + 1) { attempt ->
            val result = block()

            if (result.isSuccess) {
                return result
            }

            lastResult = result

            // 最后一次尝试不再等待
            if (attempt < maxRetries) {
                delay(currentDelay)
                currentDelay = min(currentDelay * 2, maxDelay)  // 指数退避
            }
        }

        return lastResult ?: Result.failure(Exception("Unknown error"))
    }

    /**
     * 获取用户友好的错误消息
     *
     * @param technicalMessage 技术错误消息
     * @return 用户友好的错误消息
     */
    fun getFriendlyErrorMessage(technicalMessage: String): String {
        val lowerMessage = technicalMessage.lowercase()

        return when {
            lowerMessage.contains("network") ||
            lowerMessage.contains("connection") ||
            lowerMessage.contains("connect") ->
                "Network connection error, please check your network"

            lowerMessage.contains("timeout") ->
                "Request timed out, please try again later"

            lowerMessage.contains("401") ||
            lowerMessage.contains("unauthorized") ->
                "Login expired, please login again"

            lowerMessage.contains("403") ||
            lowerMessage.contains("forbidden") ->
                "Access denied, please check your permissions"

            lowerMessage.contains("404") ||
            lowerMessage.contains("not found") ->
                "Requested resource not found"

            lowerMessage.contains("500") ||
            lowerMessage.contains("502") ||
            lowerMessage.contains("503") ||
            lowerMessage.contains("server") ->
                "Server error, please try again later"

            lowerMessage.contains("captcha") ->
                "Captcha error, please try again"

            technicalMessage.isBlank() ->
                "An error occurred, please try again"

            else -> "An error occurred, please try again"
        }
    }
}
