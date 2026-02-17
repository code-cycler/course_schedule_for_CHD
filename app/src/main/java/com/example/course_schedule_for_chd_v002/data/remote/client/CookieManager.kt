package com.example.course_schedule_for_chd_v002.data.remote.client

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Cookie 管理器
 * 用于管理 CAS 登录后的会话 Cookie
 * 支持跨域 Cookie 共享（CAS 和教务系统）
 */
class CookieManager : CookieJar {

    // Cookie 存储，按 host 分组
    private val cookieStore: MutableMap<String, MutableList<Cookie>> = mutableMapOf()

    /**
     * 从响应中保存 Cookie
     */
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val existingCookies = cookieStore.getOrPut(host) { mutableListOf() }

        for (cookie in cookies) {
            // 移除同名旧 Cookie，避免重复
            existingCookies.removeAll { it.name == cookie.name }
            existingCookies.add(cookie)
        }
    }

    /**
     * 为请求加载 Cookie
     * 同时加载当前域名和统一认证域名的 Cookie，支持跨域请求
     */
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val cookies = cookieStore[host] ?: emptyList()

        // 同时加载统一认证域名的 Cookie（用于 CAS 登录后的跨域请求）
        val idsCookies = cookieStore["ids.chd.edu.cn"] ?: emptyList()

        // 同时加载教务系统域名的 Cookie
        val bkjwCookies = cookieStore["bkjw.chd.edu.cn"] ?: emptyList()

        return cookies + idsCookies + bkjwCookies
    }

    /**
     * 清除所有 Cookie（用于登出）
     */
    fun clearAll() {
        cookieStore.clear()
    }

    /**
     * 清除指定域名的 Cookie
     */
    fun clearForHost(host: String) {
        cookieStore.remove(host)
    }

    /**
     * 检查是否存在有效的会话 Cookie
     */
    fun hasSessionCookie(): Boolean {
        return cookieStore.values.flatten().any { cookie ->
            cookie.name.contains("JSESSIONID", ignoreCase = true) ||
            cookie.name.contains("CASTGC", ignoreCase = true) ||
            cookie.name.contains("TGC", ignoreCase = true)
        }
    }

    /**
     * 获取所有 Cookie（用于调试）
     */
    fun getAllCookies(): List<Cookie> {
        return cookieStore.values.flatten()
    }

    /**
     * 获取指定域名的 Cookie
     */
    fun getCookiesForHost(host: String): List<Cookie> {
        return cookieStore[host] ?: emptyList()
    }

    /**
     * 从 Android WebView CookieManager 同步 Cookie 到 OkHttp
     * 在 WebView 登录成功后调用
     *
     * @param url 登录成功后的 URL（通常是教务系统首页）
     */
    fun syncFromWebView(url: String) {
        try {
            val webViewCookieManager = android.webkit.CookieManager.getInstance()
            val cookiesString = webViewCookieManager.getCookie(url) ?: return

            // 解析 URL
            val httpUrl = url.toHttpUrlOrNull() ?: return

            // 解析 Cookie 字符串并保存
            cookiesString.split(";").forEach { cookiePair ->
                val parts = cookiePair.trim().split("=", limit = 2)
                if (parts.size == 2) {
                    val name = parts[0].trim()
                    val value = parts[1].trim()

                    // 构建 OkHttp Cookie
                    val cookie = Cookie.Builder()
                        .name(name)
                        .value(value)
                        .domain(httpUrl.host)
                        .path("/")
                        .build()

                    // 保存到 store
                    val host = httpUrl.host
                    val existingCookies = cookieStore.getOrPut(host) { mutableListOf() }
                    existingCookies.removeAll { it.name == name }
                    existingCookies.add(cookie)
                }
            }
        } catch (e: Exception) {
            // 忽略解析错误
            e.printStackTrace()
        }
    }
}
