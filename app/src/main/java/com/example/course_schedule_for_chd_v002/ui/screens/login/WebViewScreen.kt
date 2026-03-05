package com.example.course_schedule_for_chd_v002.ui.screens.login

import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.course_schedule_for_chd_v002.util.ScriptInjector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.course_schedule_for_chd_v002.util.Constants
import com.example.course_schedule_for_chd_v002.util.WebViewLogger

private const val TAG = "WebViewScreen"

/**
 * CAS 登录界面 (v71)
 *
 * v71: 修复 shouldInterceptRequest 的关键问题
 *      - 添加详细调试日志定位问题
 *      - 为 URL.openConnection() 添加 Cookie 头（解决原始 HTML 为空的问题）
 *      - 处理空响应和登录页面重定向
 *      - 设置必要的请求头（Accept, User-Agent 等）
 *
 * v70: 使用 shouldInterceptRequest 注入脚本（只拦截 eams）
 *      - 不拦截 CAS 登录页面 (ids.chd.edu.cn)
 *      - 只拦截 eams 页面 (bkjw.chd.edu.cn)
 *      - 注入完整的 jQuery/beangle/underscore.js 脚本
 *      - 等待页面完全渲染后提取 HTML
 *
 * 流程：
 * 1. 用户在 WebView 中登录 CAS
 * 2. 登录成功后，URL 变为 eams home.action
 * 3. 自动加载课表页面 courseTableForStd.action
 * 4. 等待 3 秒让页面完全渲染
 * 5. 用 JavaScript 提取课表 HTML 并回调
 *
 * @param onLoginSuccess 登录成功回调，参数为课表 HTML 内容
 */
@Composable
fun WebViewScreen(
    onLoginSuccess: (String) -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var currentUrl by remember { mutableStateOf("") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isLoggedIn by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // Edge 浏览器 User-Agent (桌面版)
    val edgeUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0"

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 顶部状态栏
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "加载中...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else if (loadError != null) {
                            Text(
                                text = "[X] $loadError",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else if (isLoggedIn) {
                            Text(
                                text = "[OK] 登录成功，正在获取课表...",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = "[!] 请登录系统",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    if (currentUrl.isNotEmpty()) {
                        Text(
                            text = currentUrl,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                // 刷新按钮
                IconButton(
                    onClick = {
                        webViewRef?.reload()
                    },
                    enabled = !isLoading
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "刷新页面",
                        tint = if (isLoading)
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                        else
                            MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                // 返回按钮
                IconButton(
                    onClick = { webViewRef?.goBack() },
                    enabled = canGoBack && !isLoading
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回上一页",
                        tint = if (canGoBack && !isLoading)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                    )
                }

                // 前进按钮
                IconButton(
                    onClick = { webViewRef?.goForward() },
                    enabled = canGoForward && !isLoading
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "前进下一页",
                        tint = if (canGoForward && !isLoading)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // WebView 区域
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        webViewRef = this

                        // 启用调试模式
                        WebView.setWebContentsDebuggingEnabled(true)

                        // 配置 WebView 设置
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            userAgentString = edgeUserAgent
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            setSupportZoom(true)
                            blockNetworkImage = false
                            loadsImagesAutomatically = true
                            javaScriptCanOpenWindowsAutomatically = true
                            defaultTextEncodingName = "UTF-8"
                            cacheMode = WebSettings.LOAD_DEFAULT
                            allowFileAccess = true
                            allowContentAccess = true
                        }

                        // 启用 Cookie
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        // 添加 JavaScript 接口用于日志
                        addJavascriptInterface(WebViewJsInterface(), "AndroidLogger")

                        // 设置 WebViewClient
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                loadError = null
                                url?.let { currentUrl = it }
                                WebViewLogger.logPageLoad(url, true)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                url?.let {
                                    currentUrl = it

                                    // [v67] 新逻辑：三步处理
                                    // 步骤1：检测 CAS 登录成功 -> 手动跳转到 eams
                                    if (it.contains("authserver") && !it.contains("login")) {
                                        WebViewLogger.logSuccess("CAS", "CAS 登录成功，手动跳转到 eams...")
                                        view?.loadUrl(Constants.EamsUrls.HOME_PAGE)
                                        return
                                    }

                                    // 步骤2：进入 eams 首页 -> 继续加载课表页面
                                    if (it.contains("bkjw.chd.edu.cn/eams/home.action")) {
                                        WebViewLogger.logSuccess("eams", "进入 eams 首页，继续加载课表页面...")
                                        // 加载课表页面
                                        val courseTableUrl = "${Constants.EamsUrls.BASE_URL}eams/courseTableForStd.action"
                                        WebViewLogger.logNavigation("eams", "加载课表页面: $courseTableUrl")
                                        view?.loadUrl(courseTableUrl)
                                        return
                                    }

                                    // 步骤3：课表页面加载完成 -> 等待渲染后提取 HTML 并回调
                                    // [v69] 移除拦截，让页面正常加载所有资源（jQuery、beangle、underscore.js）
                                    // 等待 3 秒让 JavaScript 完全执行完成
                                    if (it.contains("courseTableForStd")) {
                                        isLoggedIn = true
                                        WebViewLogger.logSuccess("课表", "课表页面加载完成，等待 3 秒让页面完全渲染...")

                                        // [v69] 使用 postDelayed 等待页面渲染完成
                                        view?.postDelayed({
                                            WebViewLogger.logDebug("课表", "等待完成，开始提取 HTML...")

                                            // 使用 JavaScript 提取完整的 HTML
                                            view?.evaluateJavascript(
                                                "(function() { return document.documentElement.outerHTML; })();"
                                            ) { htmlResult ->
                                                // JavaScript 返回的是 JSON 编码的字符串，需要解码
                                                val html = if (htmlResult.startsWith("\"")) {
                                                    // 移除首尾引号并处理转义字符
                                                    htmlResult.substring(1, htmlResult.length - 1)
                                                        .replace("\\u003C", "<")
                                                        .replace("\\u003E", ">")
                                                        .replace("\\u0026", "&")
                                                        .replace("\\n", "\n")
                                                        .replace("\\r", "\r")
                                                        .replace("\\t", "\t")
                                                        .replace("\\\"", "\"")
                                                        .replace("\\\\", "\\")
                                                } else {
                                                    htmlResult
                                                }

                                                WebViewLogger.logParseDetail("HTML 提取成功，长度: ${html.length}")

                                                // 检查是否包含课表数据
                                                val hasTaskActivity = html.contains("TaskActivity")
                                                val hasTable0 = html.contains("table0")
                                                val hasCourseName = html.contains("var courseName")
                                                WebViewLogger.logParseDetail("HTML 包含 TaskActivity: $hasTaskActivity, table0: $hasTable0, courseName: $hasCourseName")

                                                // 调用登录成功回调，传递 HTML
                                                if (html.isNotEmpty()) {
                                                    onLoginSuccess(html)
                                                }
                                            }
                                        }, 3000)  // [v69] 等待 3 秒
                                    }
                                }

                                WebViewLogger.logPageLoad(url, false)
                                canGoBack = view?.canGoBack() ?: false
                                canGoForward = view?.canGoForward() ?: false
                            }

                            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                super.onReceivedError(view, request, error)
                                if (request?.isForMainFrame == true) {
                                    isLoading = false
                                    loadError = "加载失败: ${error?.description}"
                                    WebViewLogger.logError("WebView", "加载错误: ${error?.description}")
                                }
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                return false
                            }

                            // [v71] 使用 shouldInterceptRequest 注入脚本（只拦截 eams）
                            // [!] 不拦截 CAS 登录页面 (ids.chd.edu.cn)，确保 CAS 完全原生渲染
                            // [v71] 关键改进：
                            // 1. 添加详细调试日志
                            // 2. 为网络请求添加 Cookie 头（解决原始 HTML 为空的问题）
                            // 3. 处理空响应
                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
                                val host = request.url.host ?: ""

                                // [v71] 添加调试日志 - 记录所有请求
                                WebViewLogger.logDebug("拦截检查", "URL: $url")
                                WebViewLogger.logDebug("拦截检查", "Host: $host")

                                // [v70 fix] 首先检查是否为 CAS 登录页面（使用字符串匹配，更可靠）
                                if (url.contains("ids.chd.edu.cn")) {
                                    // CAS 登录页面，不拦截
                                    WebViewLogger.logDebug("拦截检查", "CAS 页面，跳过")
                                    return super.shouldInterceptRequest(view, request)
                                }

                                // 检查是否为 eams 页面
                                val isEamsDomain = host.contains("bkjw.chd.edu.cn")
                                val isHtml = ScriptInjector.isHtmlRequest(url)

                                // [v71] 添加调试日志 - 记录检查结果
                                WebViewLogger.logDebug("拦截检查", "isEamsDomain: $isEamsDomain, isHtml: $isHtml")

                                if (isEamsDomain && isHtml) {
                                    try {
                                        WebViewLogger.logDebug("拦截", "拦截 eams 页面: $url")

                                        // [v71] 获取 WebView 的 Cookie - 关键修复！
                                        val cookies = CookieManager.getInstance().getCookie(url)
                                        WebViewLogger.logDebug("拦截", "Cookie 长度: ${cookies?.length ?: 0}")

                                        // 获取原始 HTML
                                        val connection = java.net.URL(url).openConnection()
                                        connection.connectTimeout = 10000
                                        connection.readTimeout = 10000

                                        // [v71] 设置 Cookie 头 - 解决原始 HTML 为空的问题
                                        if (!cookies.isNullOrEmpty()) {
                                            connection.setRequestProperty("Cookie", cookies)
                                            WebViewLogger.logDebug("拦截", "已设置 Cookie 头")
                                        } else {
                                            WebViewLogger.logError("拦截", "Cookie 为空，可能无法获取数据")
                                        }

                                        // 设置其他必要的请求头
                                        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                                        connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                                        connection.setRequestProperty("User-Agent", edgeUserAgent)

                                        val html = connection.inputStream.bufferedReader().readText()

                                        // [v71] 处理空响应
                                        if (html.isEmpty()) {
                                            WebViewLogger.logError("拦截", "原始 HTML 为空，返回 null 让 WebView 正常加载")
                                            return super.shouldInterceptRequest(view, request)
                                        }

                                        WebViewLogger.logDebug("拦截", "原始 HTML 长度: ${html.length}")

                                        // 检查是否包含登录页面（可能 Cookie 失效）
                                        if (html.contains("authserver/login") || html.contains("ids.chd.edu.cn")) {
                                            WebViewLogger.logError("拦截", "HTML 包含登录页面，Cookie 可能已失效")
                                            return super.shouldInterceptRequest(view, request)
                                        }

                                        // 注入脚本（v70: 包含 underscore.js 支持）
                                        val modifiedHtml = ScriptInjector.injectIntoHtml(html)

                                        WebViewLogger.logDebug("拦截", "注入后 HTML 长度: ${modifiedHtml.length}")

                                        return WebResourceResponse(
                                            "text/html",
                                            "UTF-8",
                                            modifiedHtml.byteInputStream()
                                        )
                                    } catch (e: Exception) {
                                        WebViewLogger.logError("拦截", "拦截失败: ${e.message}")
                                        e.printStackTrace()
                                        return super.shouldInterceptRequest(view, request)
                                    }
                                }

                                return super.shouldInterceptRequest(view, request)
                            }
                        }

                        // 设置 WebChromeClient
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                WebViewLogger.logDebug("WebView", "加载进度: $newProgress%")
                            }

                            override fun onConsoleMessage(message: android.webkit.ConsoleMessage?): Boolean {
                                message?.let {
                                    val logLevel = when (it.messageLevel()) {
                                        android.webkit.ConsoleMessage.MessageLevel.ERROR -> "ERROR"
                                        android.webkit.ConsoleMessage.MessageLevel.WARNING -> "WARN"
                                        android.webkit.ConsoleMessage.MessageLevel.LOG -> "LOG"
                                        else -> "DEBUG"
                                    }
                                    WebViewLogger.logJsLog("[$logLevel] ${it.message()} (${it.sourceId()}:${it.lineNumber()})")
                                }
                                return true
                            }
                        }

                        // [v63] 使用带 service 参数的 CAS 登录 URL，登录成功后自动跳转到 eams
                        WebViewLogger.logNavigation("加载 CAS 登录页面", Constants.CasUrls.FULL_LOGIN_URL)
                        loadUrl(Constants.CasUrls.FULL_LOGIN_URL)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 底部提示
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = if (isLoggedIn) {
                    "登录成功，正在获取课表数据..."
                } else {
                    "请在上方页面中登录系统，登录成功后将自动获取课表"
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // 组件销毁时清理
    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.apply {
                stopLoading()
                settings.javaScriptEnabled = false
                clearHistory()
                clearCache(true)
                loadUrl("about:blank")
            }
            webViewRef = null
        }
    }
}

/**
 * JavaScript 接口类
 */
class WebViewJsInterface {
    @JavascriptInterface
    fun onLog(message: String) {
        WebViewLogger.logJsLog(message)
    }

    @JavascriptInterface
    fun onError(message: String, source: String, line: Int) {
        WebViewLogger.logJsError(message, source, line)
    }
}
