package com.example.course_schedule_for_chd_v002.ui.screens.login

import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.course_schedule_for_chd_v002.util.JsCompatibilityPolyfill
import com.example.course_schedule_for_chd_v002.util.WebViewLogger

private const val TAG = "WebViewScreen"

/**
 * 系统 WebView 登录界面 (v58)
 *
 * 使用系统 WebView 替代 GeckoView，包装为 Edge 浏览器
 * 添加 beangle 框架 JavaScript 兼容性支持
 *
 * v58: 恢复 HTML 拦截注入（参考 v51 的成功做法）
 *      在 shouldInterceptRequest 中拦截主 HTML 页面，注入预脚本
 *      确保脚本在任何内联脚本执行前完成
 *      只拦截 HTML 页面，不拦截 CSS/JS/图片等资源
 * v57: 添加 jQuery 方法（change, empty, submit, select, keydown, keyup, keypress 等）
 *      完善 struts2_jquery（添加 require 方法）
 *      在脚本开头立即定义 bg 对象（解决时机问题）
 * v56: 完善 jQuery 模拟（get, prev, next, cookie, Deferred 等）
 *      完善 beangle 模拟（namespace, require, struts2_jquery 等）
 *      强制桌面模式渲染
 *      在 WebView 初始化时立即注入预脚本
 * v55: 移除 shouldInterceptRequest 中的 HTML 拦截逻辑，让浏览器自然处理所有请求
 *      参考 GeckoView 和 Playwright 的做法，解决登录页面 CSS 加载失败问题
 *      分离脚本注入函数：injectBasicScripts(), injectCourseTableScripts()
 * v54: 基于页面内容检测页面类型，而非仅 URL
 * v51: 改进数据检测逻辑，检测实际课程数据而非空数组
 * v50: 使用 shouldInterceptRequest 在 HTML 中注入脚本，解决异步时序问题
 * v49: 新增 jQuery/beangle 预注入，解决外部资源加载失败问题
 *
 * GeckoView 版本保留在 GeckoViewScreen.kt 作为备份
 *
 * @param onFetchCourseTable 用户点击"获取课表"按钮时的回调
 *        参数1: 当前页面 URL
 *        参数2: 页面 HTML 内容
 */
@Composable
fun WebViewScreen(
    onFetchCourseTable: (String, String) -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var currentUrl by remember { mutableStateOf("") }
    var isOnCourseTablePage by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isLoggedIn by remember { mutableStateOf(false) }
    var isCourseDataLoaded by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var pageType by remember { mutableStateOf("unknown") }
    var dataCheckAttempts by remember { mutableIntStateOf(0) }

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
                        } else {
                            Text(
                                text = when {
                                    isOnCourseTablePage && isCourseDataLoaded -> "[OK] 课表数据已加载"
                                    isOnCourseTablePage -> "[...] 等待数据 (${dataCheckAttempts}/${Constants.BeangleConfig.MAX_RETRY_ATTEMPTS})"
                                    isLoggedIn -> "[OK] 已登录 - 可跳转课表"
                                    pageType == Constants.BeangleConfig.PAGE_TYPE_CAS_LOGIN -> "[!] CAS 登录页面"
                                    else -> "[!] 请登录系统 (WebView/Edge)"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = when {
                                    isOnCourseTablePage && isCourseDataLoaded -> MaterialTheme.colorScheme.primary
                                    isLoggedIn -> MaterialTheme.colorScheme.primary
                                    loadError != null -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                                }
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
                        dataCheckAttempts = 0
                        isCourseDataLoaded = false
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
                            builtInZoomControls = true  // [v54] 启用缩放
                            displayZoomControls = false  // [v54] 不显示缩放按钮
                            setSupportZoom(true)  // [v54] 支持缩放
                            blockNetworkImage = false
                            loadsImagesAutomatically = true
                            javaScriptCanOpenWindowsAutomatically = true
                            defaultTextEncodingName = "UTF-8"
                            cacheMode = WebSettings.LOAD_DEFAULT
                            allowFileAccess = true
                            allowContentAccess = true

                            // [v56] 桌面模式渲染设置
                            layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL  // 使用 NORMAL 以获得更准确的桌面渲染
                            setEnableSmoothTransition(true)
                            // 确保正确渲染 CSS
                            setRenderPriority(WebSettings.RenderPriority.HIGH)
                            // [v56] 禁用移动端适配
                            mediaPlaybackRequiresUserGesture = false
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
                                dataCheckAttempts = 0
                                isCourseDataLoaded = false
                                url?.let { currentUrl = it }
                                WebViewLogger.logPageLoad(url, true)

                                // [v49] 在页面开始加载时预注入 jQuery 和 beangle 模拟
                                view?.evaluateJavascript(JsCompatibilityPolyfill.getPreInjectionScript()) { result ->
                                    WebViewLogger.logPreInjection(result != null)
                                }
                            }

                            // [v49] 监控资源加载
                            override fun onLoadResource(view: WebView?, url: String?) {
                                super.onLoadResource(view, url)
                                WebViewLogger.logResourceLoad(url)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                url?.let {
                                    currentUrl = it

                                    // [v56] 使用基于内容的页面类型检测
                                    view?.let { webView ->
                                        detectPageTypeFromContent(webView, it) { detectedType, isLogin, isCourseTable ->
                                            pageType = detectedType
                                            isLoggedIn = isLogin
                                            isOnCourseTablePage = isCourseTable

                                            when (detectedType) {
                                                Constants.BeangleConfig.PAGE_TYPE_CAS_LOGIN -> {
                                                    // [v56] 登录页面，不注入脚本，避免干扰
                                                    WebViewLogger.logDebug(TAG, "[v56] 登录页面，跳过脚本注入")
                                                }
                                                Constants.BeangleConfig.PAGE_TYPE_EAMS_HOME -> {
                                                    // [v56] 教务主页，注入基础脚本
                                                    WebViewLogger.logDebug(TAG, "[v56] 教务主页，注入基础脚本")
                                                    injectBasicScripts(webView)
                                                }
                                                Constants.BeangleConfig.PAGE_TYPE_COURSE_TABLE -> {
                                                    // [v56] 课表页面，注入完整脚本并开始数据检测
                                                    WebViewLogger.logDebug(TAG, "[v56] 课表页面，注入完整脚本")
                                                    injectCourseTableScripts(webView)
                                                    startDataCheck(this@apply, dataCheckAttempts, { attempts ->
                                                        dataCheckAttempts = attempts
                                                    }, { ready ->
                                                        isCourseDataLoaded = ready
                                                    })
                                                }
                                                else -> {
                                                    // [v56] 其他页面，注入基础脚本
                                                    WebViewLogger.logDebug(TAG, "[v56] 其他页面，注入基础脚本")
                                                    injectBasicScripts(webView)
                                                }
                                            }
                                        }
                                    }

                                    WebViewLogger.logPageLoad(url, false)
                                }

                                canGoBack = view?.canGoBack() ?: false
                                canGoForward = view?.canGoForward() ?: false
                            }

                            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                super.onReceivedError(view, request, error)
                                if (request?.isForMainFrame == true) {
                                    isLoading = false
                                    loadError = "加载失败: ${error?.description}"
                                    WebViewLogger.logError("WebView", "加载错误: ${error?.description}")
                                } else {
                                    // [v49] 记录子资源加载错误
                                    WebViewLogger.logResourceError(request?.url?.toString(), error?.description?.toString())
                                }
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                return false
                            }

                            // [v58] 恢复 HTML 拦截注入（参考 v51 的成功做法）
                            // 只拦截主 HTML 页面，不拦截 CSS/JS/图片等资源
                            // 确保预注入脚本在任何内联脚本执行前完成
                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)

                                // 只拦截主框架的 HTML 页面请求
                                if (request.isForMainFrame && isHtmlPage(url)) {
                                    try {
                                        // 使用 OkHttp 获取 HTML
                                        val htmlContent = fetchHtmlContent(url)

                                        // [v58] 只有获取到有效 HTML 内容时才注入
                                        if (htmlContent.isNotEmpty() && htmlContent.contains("<", ignoreCase = true)) {
                                            // 在 HTML 的 <head> 标签后立即注入预脚本
                                            val injectedHtml = injectScriptIntoHtml(htmlContent)

                                            WebViewLogger.logHtmlInterception(url, true, injectedHtml.length)

                                            // 返回修改后的 HTML
                                            return WebResourceResponse(
                                                "text/html",
                                                "UTF-8",
                                                injectedHtml.byteInputStream()
                                            )
                                        } else {
                                            // [v58] 获取失败，让 WebView 自然加载
                                            WebViewLogger.logHtmlInterceptionError(url, "Empty or invalid HTML content")
                                        }
                                    } catch (e: Exception) {
                                        WebViewLogger.logHtmlInterceptionError(url, e.message ?: "Unknown error")
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

                        // 加载入口 URL
                        WebViewLogger.logNavigation("加载入口", Constants.CasUrls.WEBVIEW_ENTRY_URL)
                        loadUrl(Constants.CasUrls.WEBVIEW_ENTRY_URL)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
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

        // 底部操作栏
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        isOnCourseTablePage && isCourseDataLoaded -> "课表数据已加载，可获取"
                        isOnCourseTablePage -> "正在检测课表数据..."
                        isLoggedIn -> "已登录，可跳转课表页面"
                        else -> "1. 登录系统\n2. 进入「学生课表」页面\n3. 点击右侧按钮获取"
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // 跳转课表按钮
                Button(
                    onClick = {
                        webViewRef?.let { webView ->
                            val jsNavigate = "window.location.href = '/eams/courseTableForStd.action';"
                            webView.evaluateJavascript(jsNavigate, null)
                            WebViewLogger.logNavigation("跳转课表", "/eams/courseTableForStd.action")
                            isCourseDataLoaded = false
                            dataCheckAttempts = 0
                        }
                    },
                    enabled = isLoggedIn && !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLoggedIn)
                            MaterialTheme.colorScheme.secondary
                        else
                            MaterialTheme.colorScheme.outline
                    )
                ) {
                    Text(
                        text = "跳转课表",
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 获取课表按钮
                Button(
                    onClick = {
                        webViewRef?.let { webView ->
                            WebViewLogger.logDebug(TAG, "获取课表按钮点击，当前 URL: $currentUrl")
                            fetchCourseTableWithCheck(webView, currentUrl, onFetchCourseTable)
                        }
                    },
                    enabled = isOnCourseTablePage && isCourseDataLoaded && !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isOnCourseTablePage && isCourseDataLoaded)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outline
                    )
                ) {
                    Text(
                        text = when {
                            isOnCourseTablePage && !isCourseDataLoaded -> "检测中..."
                            else -> "获取课表"
                        },
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

// ==================== 辅助函数 ====================

/**
 * 检测页面类型
 */
private fun detectPageType(url: String): String {
    return when {
        url.contains("ids.chd.edu.cn") -> {
            WebViewLogger.logPageType("CAS 登录页面")
            Constants.BeangleConfig.PAGE_TYPE_CAS_LOGIN
        }
        url.contains("bkjw.chd.edu.cn") && url.contains("home.action") -> {
            WebViewLogger.logPageType("教务主页")
            Constants.BeangleConfig.PAGE_TYPE_EAMS_HOME
        }
        url.contains("courseTableForStd") -> {
            WebViewLogger.logPageType("课表页面")
            Constants.BeangleConfig.PAGE_TYPE_COURSE_TABLE
        }
        url.contains("bkjw.chd.edu.cn") -> {
            WebViewLogger.logPageType("教务系统其他页面")
            "eams_other"
        }
        else -> {
            WebViewLogger.logPageType("未知页面: $url")
            Constants.BeangleConfig.PAGE_TYPE_UNKNOWN
        }
    }
}

/**
 * [v54] 基于页面内容检测页面类型
 * 不仅检查 URL，还检查页面实际内容
 */
private fun detectPageTypeFromContent(
    webView: WebView,
    url: String,
    callback: (String, Boolean, Boolean) -> Unit  // (pageType, isLoggedIn, isOnCourseTablePage)
) {
    val detectionScript = JsCompatibilityPolyfill.getLoginPageDetectionScript()

    webView.evaluateJavascript(detectionScript) { result ->
        try {
            // 解码 JSON 结果
            val decoded = result
                .removeSurrounding("\"")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")

            // 简单解析 JSON
            val isLoginPage = decoded.contains("\"isLoginPage\":true")
            val isLoggedIn = decoded.contains("\"isLoggedIn\":true")

            // 提取用户名显示（用于判断是否已登录）
            val usernameMatch = Regex("\"usernameDisplay\":\"([^\"]+)\"").find(decoded)
            val usernameDisplay = usernameMatch?.groupValues?.get(1) ?: ""

            // 确定页面类型
            val detectedType = when {
                isLoginPage -> {
                    WebViewLogger.logPageTypeDetection(url, true, false, "检测到登录页面")
                    Constants.BeangleConfig.PAGE_TYPE_CAS_LOGIN
                }
                url.contains("courseTableForStd") -> {
                    WebViewLogger.logPageTypeDetection(url, false, true, "课表页面")
                    Constants.BeangleConfig.PAGE_TYPE_COURSE_TABLE
                }
                isLoggedIn || usernameDisplay.contains("(") -> {
                    // 已登录特征：有用户名显示（包含学号格式）
                    WebViewLogger.logPageTypeDetection(url, false, true, "用户: $usernameDisplay")
                    Constants.BeangleConfig.PAGE_TYPE_EAMS_HOME
                }
                url.contains("home.action") -> {
                    // URL 是 home.action 但没有登录特征，可能是未登录被重定向
                    if (isLoginPage) {
                        WebViewLogger.logPageTypeDetection(url, true, false, "home.action 但内容是登录页面")
                        Constants.BeangleConfig.PAGE_TYPE_CAS_LOGIN
                    } else {
                        WebViewLogger.logPageTypeDetection(url, false, false, "home.action 状态未知")
                        Constants.BeangleConfig.PAGE_TYPE_EAMS_HOME
                    }
                }
                else -> {
                    WebViewLogger.logPageTypeDetection(url, false, false)
                    detectPageType(url)  // 回退到 URL 检测
                }
            }

            // 确定登录状态和课表页面状态
            val finalIsLoggedIn = !isLoginPage && (isLoggedIn || usernameDisplay.contains("(") || detectedType == Constants.BeangleConfig.PAGE_TYPE_EAMS_HOME || detectedType == Constants.BeangleConfig.PAGE_TYPE_COURSE_TABLE)
            val isOnCourseTablePage = detectedType == Constants.BeangleConfig.PAGE_TYPE_COURSE_TABLE

            callback(detectedType, finalIsLoggedIn, isOnCourseTablePage)

        } catch (e: Exception) {
            WebViewLogger.logError("页面检测", "解析失败: ${e.message}")
            // 回退到 URL 检测
            val fallbackType = detectPageType(url)
            val isLoggedIn = fallbackType != Constants.BeangleConfig.PAGE_TYPE_CAS_LOGIN
            callback(fallbackType, isLoggedIn, fallbackType == Constants.BeangleConfig.PAGE_TYPE_COURSE_TABLE)
        }
    }
}

/**
 * 注入兼容性脚本
 * [v56] 分离为不同的注入函数，根据页面类型选择注入
 */

/**
 * [v56] 注入基础脚本（用于非课表页面）
 */
private fun injectBasicScripts(webView: WebView) {
    // 只注入预注入脚本（jQuery/beangle 模拟）
    webView.evaluateJavascript(JsCompatibilityPolyfill.getPreInjectionScript()) { result ->
        WebViewLogger.logPreInjection(result != null)
    }
}

/**
 * [v56] 注入课表页面脚本（完整版）
 */
private fun injectCourseTableScripts(webView: WebView) {
    // 1. 预注入
    webView.evaluateJavascript(JsCompatibilityPolyfill.getPreInjectionScript()) { result ->
        WebViewLogger.logPreInjection(result != null)
    }

    // 2. 注入完整 polyfill
    val injectionScript = JsCompatibilityPolyfill.getInjectionScript()
    webView.evaluateJavascript(injectionScript) { result ->
        WebViewLogger.logInjection(result != null, "Polyfill v57")
    }

    // 3. 修复 viewport
    injectViewportFix(webView)
}

/**
 * [v56] 修复 viewport - 强制桌面模式
 */
private fun injectViewportFix(webView: WebView) {
    val viewportScript = """
        (function() {
            // 移除现有的 viewport meta 标签
            var viewports = document.querySelectorAll('meta[name="viewport"]');
            viewports.forEach(function(v) { v.remove(); });

            // 创建桌面模式的 viewport
            var viewport = document.createElement('meta');
            viewport.name = 'viewport';
            viewport.content = 'width=1024, initial-scale=1.0, minimum-scale=0.5, maximum-scale=3.0, user-scalable=yes';
            document.head.appendChild(viewport);

            // [v56] 添加强制桌面样式
            var style = document.createElement('style');
            style.textContent = 'html { min-width: 1024px !important; } body { min-width: 1024px !important; overflow-x: auto !important; } * { -webkit-text-size-adjust: 100% !important; }';
            document.head.appendChild(style);
        })();
    """
    webView.evaluateJavascript(viewportScript) {}
}

/**
 * 开始数据就绪检测
 */
private fun startDataCheck(
    webView: WebView,
    currentAttempt: Int,
    onAttemptUpdate: (Int) -> Unit,
    onDataReady: (Boolean) -> Unit
) {
    checkDataReady(webView, currentAttempt, onAttemptUpdate, onDataReady)
}

/**
 * [v51] 递归检测数据是否就绪
 * 改进：检测实际课程数据数量，而非空数组
 */
private fun checkDataReady(
    webView: WebView,
    attempt: Int,
    onAttemptUpdate: (Int) -> Unit,
    onDataReady: (Boolean) -> Unit
) {
    if (attempt >= Constants.BeangleConfig.MAX_RETRY_ATTEMPTS) {
        // 超时，标记为就绪（让用户尝试获取）
        onDataReady(true)
        WebViewLogger.logWarn("数据检测", "超时，允许用户尝试获取")
        return
    }

    val newAttempt = attempt + 1
    onAttemptUpdate(newAttempt)

    // [v51] 使用改进的检测脚本，同时返回就绪状态和数据数量
    val checkScript = """
        (function() {
            var ready = typeof window.checkCourseDataReady === 'function' ? window.checkCourseDataReady() : false;
            var count = typeof window.getActivitiesDataCount === 'function' ? window.getActivitiesDataCount() : 0;
            return ready + '|' + count;
        })();
    """

    webView.evaluateJavascript(checkScript) { result ->
        // 解析结果：格式为 "true|25" 或 "false|0"
        val cleanResult = result?.trim()?.replace("\"", "") ?: "false|0"
        val parts = cleanResult.split("|")
        val isReady = parts.getOrNull(0)?.lowercase() == "true"
        val dataCount = parts.getOrNull(1)?.toIntOrNull() ?: 0

        WebViewLogger.logDataCheck(newAttempt, isReady, Constants.BeangleConfig.MAX_RETRY_ATTEMPTS)
        WebViewLogger.logDebug("数据检测", "实际课程数据: $dataCount 条")

        // [v51] 只要有实际数据就认为就绪
        if (isReady || dataCount > 0) {
            onDataReady(true)
            WebViewLogger.logSuccess("数据检测", "课表数据就绪，共 $dataCount 条")
        } else {
            // 继续等待
            Handler(Looper.getMainLooper()).postDelayed({
                checkDataReady(webView, newAttempt, onAttemptUpdate, onDataReady)
            }, Constants.BeangleConfig.DATA_CHECK_INTERVAL_MS)
        }
    }
}

/**
 * 改进的获取课表逻辑
 */
private fun fetchCourseTableWithCheck(
    webView: WebView,
    url: String,
    callback: (String, String) -> Unit
) {
    // 先检查 JavaScript 错误
    webView.evaluateJavascript(JsCompatibilityPolyfill.getErrorsScript()) { _ ->
        // 获取页面诊断信息
        webView.evaluateJavascript(JsCompatibilityPolyfill.getDiagnosticScript()) { diagnosticJson ->
            WebViewLogger.logDebug(TAG, "页面诊断: $diagnosticJson")

            // [v53] 直接从 table0.activities 提取 JSON 数据
            val extractScript = """
                (function() {
                    try {
                        if (!window.table0 || !window.table0.activities) {
                            return JSON.stringify({error: 'table0 not found', unitCount: 11, data: []});
                        }

                        var activities = [];
                        for (var i = 0; i < window.table0.activities.length; i++) {
                            if (window.table0.activities[i] && window.table0.activities[i].length > 0) {
                                for (var j = 0; j < window.table0.activities[i].length; j++) {
                                    var act = window.table0.activities[i][j];
                                    activities.push({
                                        index: i,
                                        teacherId: act.teacherId || '',
                                        teacherName: act.teacherName || '',
                                        courseId: act.courseId || '',
                                        courseName: act.courseName || '',
                                        courseCode: act.courseCode || '',
                                        roomId: act.roomId || '',
                                        roomName: act.roomName || '',
                                        vaildWeeks: act.weeksBitmap || act.vaildWeeks || '',
                                        assistantName: act.assistantName || ''
                                    });
                                }
                            }
                        }

                        return JSON.stringify({
                            error: null,
                            unitCount: window.unitCount || 11,
                            data: activities
                        });
                    } catch (e) {
                        return JSON.stringify({error: e.message, unitCount: 11, data: []});
                    }
                })();
            """

            webView.evaluateJavascript(extractScript) { result ->
                WebViewLogger.logParseDetail("[v53] Extracting table0.activities data...")

                // JSON 结果需要解码（evaluateJavascript 返回的是 JSON 字符串）
                val decodedJson = result
                    .removeSurrounding("\"")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")

                WebViewLogger.logHtmlFetch(decodedJson.length, url)
                WebViewLogger.logParseDetail("[v53] JSON data length: ${decodedJson.length}")

                // 使用 "JSON:" 前缀标识数据格式
                callback(url, "JSON:$decodedJson")
            }
        }
    }
}

/**
 * JavaScript 接口类 (v49)
 *
 * 用于从 JavaScript 代码向 Android 发送日志和错误信息
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

    @JavascriptInterface
    fun onAjaxComplete(url: String) {
        WebViewLogger.logNavigation("AJAX 完成", url)
    }
}

// ==================== v58 HTML 拦截注入辅助函数 ====================

/**
 * 判断 URL 是否为 HTML 页面
 */
private fun isHtmlPage(url: String): Boolean {
    val path = url.lowercase()
    // 判断是否为 HTML 页面（排除 CSS、JS、图片等资源）
    return (path.contains(".action") ||
            path.contains(".html") ||
            path.contains(".htm") ||
            (path.contains("/eams/") && !path.contains(".js") && !path.contains(".css") &&
             !path.contains(".png") && !path.contains(".jpg") && !path.contains(".gif") &&
             !path.contains(".svg") && !path.contains(".ico") && !path.contains(".woff") &&
             !path.contains(".ttf") && !path.contains(".eot")) ||
            path.endsWith("/"))
}

/**
 * 使用 OkHttp 获取 HTML 内容
 * [v58 修复] 跟随重定向，同步 WebView Cookie
 */
private fun fetchHtmlContent(url: String): String {
    return try {
        // [v58] 从 WebView CookieManager 获取 Cookie
        val webViewCookies = CookieManager.getInstance().getCookie(url)
        android.util.Log.d(TAG, "[fetchHtmlContent] WebView Cookies: ${webViewCookies?.take(100)}...")

        // [v58] 配置 OkHttp 跟随重定向
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)  // 跟随重定向
            .followSslRedirects(true)  // 跟随 SSL 重定向
            .build()

        val requestBuilder = okhttp3.Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")

        // [v58] 添加 WebView 的 Cookie
        if (!webViewCookies.isNullOrEmpty()) {
            requestBuilder.header("Cookie", webViewCookies)
        }

        val response = client.newCall(requestBuilder.build()).execute()
        android.util.Log.d(TAG, "[fetchHtmlContent] URL: $url, Code: ${response.code}, Redirect: ${response.isRedirect}")

        if (response.isSuccessful) {
            val body = response.body?.string() ?: ""
            android.util.Log.d(TAG, "[fetchHtmlContent] 成功获取，长度: ${body.length}")
            body
        } else {
            WebViewLogger.logHtmlInterceptionError(url, "HTTP ${response.code}")
            ""
        }
    } catch (e: Exception) {
        WebViewLogger.logHtmlInterceptionError(url, "Fetch error: ${e.message}")
        android.util.Log.e(TAG, "[fetchHtmlContent] 错误: ${e.message}", e)
        ""
    }
}

/**
 * 在 HTML 中注入预脚本
 * [v58 修复] 使用字面量替换而非正则替换，避免 "Illegal group reference" 错误
 */
private fun injectScriptIntoHtml(html: String): String {
    val preInjectionScript = JsCompatibilityPolyfill.getPreInjectionScript()
    val scriptTag = "<script>$preInjectionScript</script>"

    // [v58] 使用字面量查找和替换，避免正则表达式中的 $ 符号问题
    val headIndex = html.indexOf("<head", ignoreCase = true)
    if (headIndex >= 0) {
        // 找到 <head> 标签的结束位置 (>)
        val headEndIndex = html.indexOf('>', headIndex)
        if (headEndIndex >= 0) {
            // 在 <head> 标签后插入脚本
            return html.substring(0, headEndIndex + 1) + scriptTag + html.substring(headEndIndex + 1)
        }
    }

    val htmlIndex = html.indexOf("<html", ignoreCase = true)
    if (htmlIndex >= 0) {
        // 找到 <html> 标签的结束位置 (>)
        val htmlEndIndex = html.indexOf('>', htmlIndex)
        if (htmlEndIndex >= 0) {
            // 在 <html> 后创建 <head> 并插入脚本
            return html.substring(0, htmlEndIndex + 1) + "<head>$scriptTag</head>" + html.substring(htmlEndIndex + 1)
        }
    }

    // 在文档开头注入
    return scriptTag + html
}
