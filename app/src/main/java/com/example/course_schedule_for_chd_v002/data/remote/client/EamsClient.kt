package com.example.course_schedule_for_chd_v002.data.remote.client

import com.example.course_schedule_for_chd_v002.util.Constants
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * 统一 HTTP 客户端
 * 封装 OkHttp，提供 Cookie 持久化和统一的网络配置
 */
class EamsClient(private val cookieManager: CookieManager) {

    /**
     * OkHttp 客户端实例
     * 配置了 Cookie 管理、超时设置、日志拦截器
     */
    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieManager)
            .connectTimeout(Constants.Network.CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(Constants.Network.READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(Constants.Network.WRITE_TIMEOUT, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    // 设置日志级别为 BODY 以查看完整请求/响应
                    level = HttpLoggingInterceptor.Level.BODY
                }
            )
            .build()
    }

    /**
     * 检查是否有有效的登录会话
     */
    fun hasSession(): Boolean = cookieManager.hasSessionCookie()

    /**
     * 清除会话（用于登出）
     */
    fun clearSession() {
        cookieManager.clearAll()
    }
}
