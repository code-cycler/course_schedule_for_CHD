package com.example.course_schedule_for_chd_v002.util

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * NetworkUtils 单元测试
 * 测试网络请求重试和错误处理功能
 */
class NetworkUtilsTest {

    // ================ retryWith 测试 ================

    @Test
    fun `retryWith succeeds on first attempt`() = runTest {
        var attempts = 0
        val result = NetworkUtils.retryWith(maxRetries = 3) {
            attempts++
            Result.success("success")
        }

        assertTrue(result.isSuccess)
        assertEquals("success", result.getOrNull())
        assertEquals(1, attempts)
    }

    @Test
    fun `retryWith retries on failure and succeeds`() = runTest {
        var attempts = 0
        val result = NetworkUtils.retryWith(maxRetries = 3, initialDelay = 0) {
            attempts++
            if (attempts < 3) {
                Result.failure(Exception("Temporary error"))
            } else {
                Result.success("success")
            }
        }

        assertTrue(result.isSuccess)
        assertEquals("success", result.getOrNull())
        assertEquals(3, attempts)
    }

    @Test
    fun `retryWith fails after max retries`() = runTest {
        var attempts = 0
        val result: Result<String> = NetworkUtils.retryWith(maxRetries = 3, initialDelay = 0) {
            attempts++
            Result.failure(Exception("Permanent error"))
        }

        assertTrue(result.isFailure)
        assertEquals(4, attempts) // 初始尝试 + 3 次重试
    }

    @Test
    fun `retryWith with zero retries only tries once`() = runTest {
        var attempts = 0
        val result: Result<String> = NetworkUtils.retryWith(maxRetries = 0, initialDelay = 0) {
            attempts++
            Result.failure(Exception("Error"))
        }

        assertTrue(result.isFailure)
        assertEquals(1, attempts)
    }

    @Test
    fun `retryWith preserves success result`() = runTest {
        val result = NetworkUtils.retryWith(maxRetries = 3) {
            Result.success(42)
        }

        assertTrue(result.isSuccess)
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun `retryWith preserves failure exception message`() = runTest {
        val result: Result<String> = NetworkUtils.retryWith(maxRetries = 2, initialDelay = 0) {
            Result.failure(Exception("Custom error message"))
        }

        assertTrue(result.isFailure)
        assertEquals("Custom error message", result.exceptionOrNull()?.message)
    }

    // ================ getFriendlyErrorMessage 测试 ================

    @Test
    fun `getFriendlyErrorMessage maps network errors correctly`() {
        val message = NetworkUtils.getFriendlyErrorMessage("Network error occurred")
        assertEquals("Network connection error, please check your network", message)
    }

    @Test
    fun `getFriendlyErrorMessage maps connection errors correctly`() {
        val message = NetworkUtils.getFriendlyErrorMessage("Connection refused")
        assertEquals("Network connection error, please check your network", message)
    }

    @Test
    fun `getFriendlyErrorMessage maps timeout errors correctly`() {
        val message = NetworkUtils.getFriendlyErrorMessage("timeout of 30000ms")
        assertEquals("Request timed out, please try again later", message)
    }

    @Test
    fun `getFriendlyErrorMessage maps 401 errors correctly`() {
        val message = NetworkUtils.getFriendlyErrorMessage("HTTP 401 Unauthorized")
        assertEquals("Login expired, please login again", message)
    }

    @Test
    fun `getFriendlyErrorMessage maps 403 errors correctly`() {
        val message = NetworkUtils.getFriendlyErrorMessage("HTTP 403 Forbidden")
        assertEquals("Access denied, please check your permissions", message)
    }

    @Test
    fun `getFriendlyErrorMessage maps 404 errors correctly`() {
        val message = NetworkUtils.getFriendlyErrorMessage("HTTP 404 Not Found")
        assertEquals("Requested resource not found", message)
    }

    @Test
    fun `getFriendlyErrorMessage maps 500 errors correctly`() {
        val message = NetworkUtils.getFriendlyErrorMessage("HTTP 500 Internal Server Error")
        assertEquals("Server error, please try again later", message)
    }

    @Test
    fun `getFriendlyErrorMessage maps server errors correctly`() {
        val message = NetworkUtils.getFriendlyErrorMessage("Server unavailable")
        assertEquals("Server error, please try again later", message)
    }

    @Test
    fun `getFriendlyErrorMessage maps captcha errors correctly`() {
        val message = NetworkUtils.getFriendlyErrorMessage("Invalid captcha code")
        assertEquals("Captcha error, please try again", message)
    }

    @Test
    fun `getFriendlyErrorMessage returns default for unknown errors`() {
        val message = NetworkUtils.getFriendlyErrorMessage("Some unknown error")
        assertEquals("An error occurred, please try again", message)
    }

    @Test
    fun `getFriendlyErrorMessage handles null-like input`() {
        val message = NetworkUtils.getFriendlyErrorMessage("")
        assertEquals("An error occurred, please try again", message)
    }

    @Test
    fun `getFriendlyErrorMessage is case insensitive`() {
        val message1 = NetworkUtils.getFriendlyErrorMessage("NETWORK ERROR")
        val message2 = NetworkUtils.getFriendlyErrorMessage("network error")
        assertEquals(message1, message2)
    }
}
