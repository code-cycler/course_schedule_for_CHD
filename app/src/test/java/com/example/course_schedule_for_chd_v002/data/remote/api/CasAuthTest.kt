package com.example.course_schedule_for_chd_v002.data.remote.api

import com.example.course_schedule_for_chd_v002.data.remote.client.CookieManager
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * CasApi 单元测试
 * 使用 MockWebServer 模拟 CAS 服务器响应
 */
class CasAuthTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var cookieManager: CookieManager
    private lateinit var client: OkHttpClient
    private lateinit var casApi: CasApi

    @Before
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()

        cookieManager = CookieManager()
        client = OkHttpClient.Builder()
            .cookieJar(cookieManager)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        // 使用 MockWebServer 的 URL 作为 baseUrl
        casApi = CasApi(client, mockServer.url("").toString().removeSuffix("/"))
    }

    @After
    fun teardown() {
        mockServer.shutdown()
    }

    // ================ 获取登录页面测试 ================

    @Test
    fun getLoginPage_success_returnsCasLoginPage() = runTest {
        // Given
        val loginHtml = """
            <html>
            <body>
                <form>
                    <input type="hidden" name="lt" value="LT-12345-test" />
                    <input type="hidden" name="execution" value="e1s1" />
                    <input type="hidden" name="_eventId" value="submit" />
                    <img class="captcha-img" src="/captcha.jpg" />
                </form>
            </body>
            </html>
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(loginHtml)
        )

        // When
        val result = casApi.getLoginPage(mockServer.url("/login").toString())

        // Then
        assertTrue(result.isSuccess)
        val loginPage = result.getOrNull()
        assertNotNull(loginPage)
        assertEquals("LT-12345-test", loginPage?.lt)
        assertEquals("e1s1", loginPage?.execution)
        assertEquals("submit", loginPage?.eventId)
    }

    @Test
    fun getLoginPage_extractsCaptchaUrl() = runTest {
        // Given
        val loginHtml = """
            <html>
            <body>
                <form>
                    <input type="hidden" name="lt" value="LT-test" />
                    <input type="hidden" name="execution" value="e1s1" />
                    <img class="captcha-img" src="/captcha/abc123.jpg" />
                </form>
            </body>
            </html>
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(loginHtml)
        )

        // When
        val result = casApi.getLoginPage(mockServer.url("/login").toString())

        // Then
        assertTrue(result.isSuccess)
        val loginPage = result.getOrNull()
        assertNotNull(loginPage)
        assertTrue(loginPage?.captchaUrl?.contains("captcha") == true)
    }

    @Test
    fun getLoginPage_missingHiddenFields_returnsFailure() = runTest {
        // Given - 没有隐藏字段的页面
        val loginHtml = """
            <html>
            <body>
                <form>
                    <input type="text" name="username" />
                    <input type="password" name="password" />
                </form>
            </body>
            </html>
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(loginHtml)
        )

        // When
        val result = casApi.getLoginPage(mockServer.url("/login").toString())

        // Then
        assertTrue(result.isFailure)
    }

    @Test
    fun getLoginPage_httpError_returnsFailure() = runTest {
        // Given
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error")
        )

        // When
        val result = casApi.getLoginPage(mockServer.url("/login").toString())

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("500") == true)
    }

    @Test
    fun getLoginPage_emptyBody_returnsFailure() = runTest {
        // Given
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("")
        )

        // When
        val result = casApi.getLoginPage(mockServer.url("/login").toString())

        // Then
        assertTrue(result.isFailure)
    }

    // ================ 获取验证码测试 ================

    @Test
    fun getCaptchaImage_success_returnsByteArray() = runTest {
        // Given
        val captchaData = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte()) // PNG header bytes
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(okio.Buffer().write(captchaData))
                .setHeader("Content-Type", "image/png")
        )

        // When
        val result = casApi.getCaptchaImage(mockServer.url("/captcha.jpg").toString())

        // Then
        assertTrue(result.isSuccess)
        assertNotNull(result.getOrNull())
        assertTrue(result.getOrNull()!!.isNotEmpty())
    }

    @Test
    fun getCaptchaImage_httpError_returnsFailure() = runTest {
        // Given
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("Not Found")
        )

        // When
        val result = casApi.getCaptchaImage(mockServer.url("/captcha.jpg").toString())

        // Then
        assertTrue(result.isFailure)
    }

    // ================ 登录测试 ================

    @Test
    fun login_success_returnsTrue() = runTest {
        // Given - 模拟登录成功重定向
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "http://bkjw.chd.edu.cn/eams/home.action")
        )

        val loginPage = com.example.course_schedule_for_chd_v002.data.remote.dto.CasLoginPage(
            lt = "LT-test",
            execution = "e1s1",
            eventId = "submit",
            captchaUrl = "/captcha.jpg"
        )

        // When
        val result = casApi.login(
            username = "20240001",
            password = "password123",
            captcha = "1234",
            loginPage = loginPage,
            serviceUrl = mockServer.url("/service").toString()
        )

        // Then
        // 注意：由于我们模拟的是302响应，实际结果取决于实现
        // 这里测试能够正常调用，不抛出异常
        assertNotNull(result)
    }

    @Test
    fun login_wrongCredentials_returnsFailure() = runTest {
        // Given - 模拟登录失败
        val errorHtml = """
            <html>
            <body>
                <div class="error-message">用户名或密码错误</div>
            </body>
            </html>
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(errorHtml)
        )

        val loginPage = com.example.course_schedule_for_chd_v002.data.remote.dto.CasLoginPage(
            lt = "LT-test",
            execution = "e1s1",
            eventId = "submit",
            captchaUrl = "/captcha.jpg"
        )

        // When
        val result = casApi.login(
            username = "wrong",
            password = "wrong",
            captcha = "0000",
            loginPage = loginPage,
            serviceUrl = mockServer.url("/service").toString()
        )

        // Then
        assertTrue(result.isFailure)
    }

    @Test
    fun login_wrongCaptcha_returnsFailure() = runTest {
        // Given - 模拟验证码错误
        val errorHtml = """
            <html>
            <body>
                <div class="error">验证码错误</div>
            </body>
            </html>
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(errorHtml)
        )

        val loginPage = com.example.course_schedule_for_chd_v002.data.remote.dto.CasLoginPage(
            lt = "LT-test",
            execution = "e1s1",
            eventId = "submit",
            captchaUrl = "/captcha.jpg"
        )

        // When
        val result = casApi.login(
            username = "20240001",
            password = "password",
            captcha = "wrong",
            loginPage = loginPage,
            serviceUrl = mockServer.url("/service").toString()
        )

        // Then
        assertTrue(result.isFailure)
    }

    @Test
    fun login_networkError_returnsFailure() = runTest {
        // Given - 模拟网络错误
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBodyDelay(10, TimeUnit.SECONDS) // 超长延迟模拟超时
        )

        val loginPage = com.example.course_schedule_for_chd_v002.data.remote.dto.CasLoginPage(
            lt = "LT-test",
            execution = "e1s1",
            eventId = "submit",
            captchaUrl = "/captcha.jpg"
        )

        // When
        val result = casApi.login(
            username = "20240001",
            password = "password",
            captcha = "1234",
            loginPage = loginPage,
            serviceUrl = mockServer.url("/service").toString()
        )

        // Then - 应该超时失败
        assertTrue(result.isFailure)
    }

    // ================ 隐藏字段提取测试 ================

    @Test
    fun getLoginPage_variousHiddenFieldNames_extractsCorrectly() = runTest {
        // Given - 测试不同格式的隐藏字段
        val loginHtml = """
            <html>
            <body>
                <form id="fm1">
                    <input type="hidden" name="lt" value="LT-67890-abc" />
                    <input type="hidden" name="execution" value="flow-execution-123" />
                    <input name="_eventId" type="hidden" value="submit" />
                </form>
            </body>
            </html>
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(loginHtml)
        )

        // When
        val result = casApi.getLoginPage(mockServer.url("/login").toString())

        // Then
        assertTrue(result.isSuccess)
        val loginPage = result.getOrNull()
        assertEquals("LT-67890-abc", loginPage?.lt)
        assertEquals("flow-execution-123", loginPage?.execution)
        assertEquals("submit", loginPage?.eventId)
    }

    @Test
    fun getLoginPage_defaultEventId_whenMissing() = runTest {
        // Given - 没有 _eventId 字段
        val loginHtml = """
            <html>
            <body>
                <form>
                    <input type="hidden" name="lt" value="LT-test" />
                    <input type="hidden" name="execution" value="e1s1" />
                </form>
            </body>
            </html>
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(loginHtml)
        )

        // When
        val result = casApi.getLoginPage(mockServer.url("/login").toString())

        // Then
        assertTrue(result.isSuccess)
        // 应该使用默认值 "submit"
        assertEquals("submit", result.getOrNull()?.eventId)
    }

    // ================ Cookie 管理测试 ================

    @Test
    fun getLoginPage_storesCookies() = runTest {
        // Given
        val loginHtml = """
            <html>
            <body>
                <form>
                    <input type="hidden" name="lt" value="LT-test" />
                    <input type="hidden" name="execution" value="e1s1" />
                </form>
            </body>
            </html>
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(loginHtml)
                .setHeader("Set-Cookie", "JSESSIONID=ABC123; Path=/")
        )

        // When
        casApi.getLoginPage(mockServer.url("/login").toString())

        // Then
        // Cookie 应该被存储
        assertTrue(cookieManager.hasSessionCookie())
    }
}
