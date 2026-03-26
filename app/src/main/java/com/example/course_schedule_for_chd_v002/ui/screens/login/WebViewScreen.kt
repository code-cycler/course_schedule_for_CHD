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
 * [v73] 登录步骤状态机
 */
private sealed class LoginStep {
    object CAS_LOGIN : LoginStep()
    object EAMS_HOME : LoginStep()
    object EXTRACT_HOME_HTML : LoginStep()
    object COURSE_TABLE : LoginStep()
    object DONE : LoginStep()
}

/**
 * CAS 登录界面 (v73)
 *
 * v73: 三步获取教学周和课表
 *      - [新增] 进入 eams 首页后，先提取首页 HTML（获取教学周）
 *      - 等待 1.5 秒让 jquery/beangle 渲染教学周信息
 *      - 然后跳转到课表页面，提取课表 HTML
 *      - 回调传递两个 HTML：courseTableHtml + homePageHtml
 *
 * v72: 优化课表加载等待逻辑
 *      - 将固定 3 秒等待改为基于状态的动态检测
 *      - 初始等待 500ms，然后每 200ms 检测一次
 *      - 最多等待 5 秒，检测 table0.activities 是否存在
 *      - 平均节省 1-2 秒加载时间
 *
 * 流程：
 * 1. 用户在 WebView 中登录 CAS
 * 2. 登录成功后，URL 变为 eams home.action
 * 3. [v73 新增] 等待首页 JS 渲染，提取首页 HTML（教学周信息）
 * 4. 自动加载课表页面 courseTableForStd.action
 * 5. 动态等待页面渲染完成（最多 5 秒）
 * 6. 用 JavaScript 提取课表 HTML 并回调
 *
 * @param onLoginSuccess 登录成功回调，参数为 (courseTableHtml, homePageHtml)
 */
@Composable
fun WebViewScreen(
    onLoginSuccess: (courseTableHtml: String, homePageHtml: String?) -> Unit  // [v73] 新增 homePageHtml 参数
) {
    var isLoading by remember { mutableStateOf(true) }
    var currentUrl by remember { mutableStateOf("") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isLoggedIn by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // [v73] 新增：存储首页 HTML（包含教学周信息）
    var homePageHtml by remember { mutableStateOf<String?>(null) }

    // [v73] 新增：登录步骤状态机
    var currentStep by remember { mutableStateOf<LoginStep>(LoginStep.CAS_LOGIN) }

    // [v73 fix5] 新增：首页是否已重新加载（用于让 shouldInterceptRequest 能拦截到首页）
    var homePageReloaded by remember { mutableStateOf(false) }

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
                        imageVector = Icons.Filled.Refresh,
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
                                    android.util.Log.i(TAG, "[onPageFinished] URL: $it, currentStep: $currentStep")

                                    // [v73 fix] 修复 CAS 自动跳转问题
                                    // CAS 登录成功后可能自动跳转到首页，也可能停留在 authserver 页面
                                    // 需要处理两种情况

                                    // 情况1：CAS 登录成功但停留在 authserver 页面 -> 手动跳转
                                    if (it.contains("authserver") && !it.contains("login") && currentStep == LoginStep.CAS_LOGIN) {
                                        WebViewLogger.logSuccess("CAS", "CAS 登录成功，跳转到 eams 首页...")
                                        currentStep = LoginStep.EAMS_HOME
                                        view?.loadUrl(Constants.EamsUrls.HOME_PAGE)
                                        return
                                    }

                                    // 情况2：CAS 自动跳转到 eams 首页（正常流程）
                                    // 或者从 authserver 手动跳转到首页
                                    if (it.contains("bkjw.chd.edu.cn/eams/home.action") &&
                                        (currentStep == LoginStep.CAS_LOGIN || currentStep == LoginStep.EAMS_HOME)) {

                                        // [v73 fix6] 如果是第一次加载首页（通过重定向），需要重新加载
                                        // 让 shouldInterceptRequest 能拦截并注入脚本
                                        // [v73 fix6] 添加 1.5 秒延迟，避免服务器返回"请不要过快点击"错误
                                        if (currentStep == LoginStep.CAS_LOGIN && !homePageReloaded) {
                                            homePageReloaded = true
                                            WebViewLogger.logDebug("首页", "首次加载（重定向），1.5秒后重新加载以注入脚本...")
                                            android.util.Log.i("CHD_Reload", "[首页] 首次加载（重定向），1.5秒后重新加载 URL: $it")
                                            // 延迟 1.5 秒后重新加载，避免服务器返回"请不要过快点击"
                                            view?.postDelayed({ view?.loadUrl(it) }, 1500L)
                                            return
                                        }

                                        // [v73 fix5] 第二次加载（shouldInterceptRequest 已注入脚本）
                                        WebViewLogger.logSuccess("首页", "eams 首页加载完成，开始动态检测教学周信息...")
                                        currentStep = LoginStep.EXTRACT_HOME_HTML

                                        // 直接开始动态检测（不需要再注入脚本，shouldInterceptRequest 已注入）
                                        extractHomePageHtml(view ?: return) { html ->
                                            homePageHtml = html
                                            android.util.Log.i("CHD_CurrentWeek", "[WebView] 首页 HTML 提取完成，长度: ${html.length}")
                                            WebViewLogger.logSuccess("首页", "首页 HTML 提取完成，跳转到课表页面...")

                                            // 跳转到课表页面
                                            currentStep = LoginStep.COURSE_TABLE
                                            val courseTableUrl = "${Constants.EamsUrls.BASE_URL}eams/courseTableForStd.action"
                                            WebViewLogger.logNavigation("课表", "加载课表页面: $courseTableUrl")
                                            view.loadUrl(courseTableUrl)
                                        }
                                        return
                                    }

                                    // [v73] 步骤3：课表页面加载完成 -> 先注入脚本，再动态等待渲染后提取 HTML 并回调
                                    if (it.contains("courseTableForStd") && currentStep == LoginStep.COURSE_TABLE) {
                                        WebViewLogger.logSuccess("课表", "课表页面加载完成，注入脚本...")

                                        // [v73 fix3] 直接使用 evaluateJavascript 注入脚本
                                        val injectScript = ScriptInjector.getPureJavaScript()
                                        android.util.Log.i("CHD_Inject", "[课表] 注入脚本，长度: ${injectScript.length}")

                                        view?.evaluateJavascript(injectScript) { result ->
                                            android.util.Log.i("CHD_Inject", "[课表] 脚本注入结果: ${result.take(100)}")
                                            WebViewLogger.logDebug("课表", "脚本注入完成，开始动态检测页面状态...")

                                            isLoggedIn = true
                                            currentStep = LoginStep.DONE

                                            // [v72] 动态等待页面渲染完成
                                            waitForPageReady(view ?: return@evaluateJavascript, onReady = {
                                                extractHtmlAndCallback(view, homePageHtml, onLoginSuccess)
                                            }, onTimeout = {
                                                WebViewLogger.logError("课表", "等待超时，尝试提取 HTML...")
                                                extractHtmlAndCallback(view, homePageHtml, onLoginSuccess)
                                            })
                                        }
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

                            // [v73 fix] 使用 shouldInterceptRequest 注入脚本
                            // [!] 不拦截 CAS 登录页面 (ids.chd.edu.cn)
                            // [v73 fix2] 首页也需要注入 jquery/beangle 支持，确保教学周信息能渲染
                            // [!] 拦截首页和课表页面
                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
                                val host = request.url.host ?: ""
                                val method = request.method ?: "GET"

                                // [v73 fix3] 添加详细诊断日志
                                if (url.contains("home.action") || url.contains("courseTableForStd")) {
                                    android.util.Log.i("CHD_Intercept", "========== shouldInterceptRequest ==========")
                                    android.util.Log.i("CHD_Intercept", "URL: $url")
                                    android.util.Log.i("CHD_Intercept", "Host: $host")
                                    android.util.Log.i("CHD_Intercept", "Method: $method")
                                    android.util.Log.i("CHD_Intercept", "Is EAMS: ${host.contains("bkjw.chd.edu.cn")}")
                                    android.util.Log.i("CHD_Intercept", "Is Home: ${url.contains("home.action")}")
                                    android.util.Log.i("CHD_Intercept", "Is CourseTable: ${url.contains("courseTableForStd")}")
                                    android.util.Log.i("CHD_Intercept", "Is Html: ${ScriptInjector.isHtmlRequest(url)}")
                                }

                                // [v73] 检查是否为 CAS 登录页面 - 不拦截
                                if (url.contains("ids.chd.edu.cn")) {
                                    return super.shouldInterceptRequest(view, request)
                                }

                                // 检查是否为 eams 页面的 HTML 请求
                                val isEamsPage = host.contains("bkjw.chd.edu.cn")
                                val isHomePage = url.contains("home.action")
                                val isCourseTablePage = url.contains("courseTableForStd")
                                val isHtml = ScriptInjector.isHtmlRequest(url)

                                // 只拦截首页和课表页面的 HTML 请求
                                if (isEamsPage && (isHomePage || isCourseTablePage) && isHtml) {
                                    try {
                                        val pageType = if (isHomePage) "首页" else "课表"
                                        WebViewLogger.logDebug("拦截", "拦截 $pageType 页面: $url")

                                        // 获取 WebView 的 Cookie
                                        val cookies = CookieManager.getInstance().getCookie(url)

                                        // 获取原始 HTML
                                        val connection = java.net.URL(url).openConnection()
                                        connection.connectTimeout = 10000
                                        connection.readTimeout = 10000

                                        if (!cookies.isNullOrEmpty()) {
                                            connection.setRequestProperty("Cookie", cookies)
                                        }

                                        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                                        connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                                        connection.setRequestProperty("User-Agent", edgeUserAgent)

                                        val html = connection.inputStream.bufferedReader().readText()

                                        if (html.isEmpty()) {
                                            return super.shouldInterceptRequest(view, request)
                                        }

                                        // 检查是否包含登录页面（可能 Cookie 失效）
                                        if (html.contains("authserver/login") || html.contains("ids.chd.edu.cn")) {
                                            WebViewLogger.logError("拦截", "HTML 包含登录页面，Cookie 可能已失效")
                                            return super.shouldInterceptRequest(view, request)
                                        }

                                        // 注入脚本
                                        val modifiedHtml = ScriptInjector.injectIntoHtml(html)

                                        WebViewLogger.logDebug("拦截", "注入后 HTML 长度: ${modifiedHtml.length}")

                                        return WebResourceResponse(
                                            "text/html",
                                            "UTF-8",
                                            modifiedHtml.byteInputStream()
                                        )
                                    } catch (e: Exception) {
                                        WebViewLogger.logError("拦截", "拦截失败: ${e.message}")
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
 * [v96] 立即检测页面状态（取消等待时间）
 *
 * @param view WebView 实例
 * @param onReady 页面准备就绪回调
 * @param onTimeout 等待超时回调
 */
private fun waitForPageReady(view: WebView, onReady: () -> Unit, onTimeout: () -> Unit) {
    var attempts = 0
    val maxAttempts = 1  // [v96] 只检测1次
    val checkInterval = 0L  // [v96] 无间隔
    val initialDelay = 0L  // [v96] 无初始延迟

    val checkRunnable = object : Runnable {
        override fun run() {
            // 使用 JavaScript 检测页面是否准备就绪
            view.evaluateJavascript("""
                (function() {
                    try {
                        const hasTable0 = typeof table0 !== 'undefined';
                        const hasActivities = hasTable0 && table0.activities && table0.activities.length > 0;
                        return hasActivities;
                    } catch(e) {
                        return false;
                    }
                })();
            """) { result ->
                val isReady = result == "true"

                if (isReady) {
                    WebViewLogger.logSuccess("课表", "页面准备就绪 (检测次数: ${attempts + 1})")
                    onReady()
                } else if (attempts >= maxAttempts) {
                    WebViewLogger.logError("课表", "等待超时 (检测次数: ${attempts + 1})")
                    onTimeout()
                } else {
                    attempts++
                    WebViewLogger.logDebug("课表", "页面未就绪，继续等待... (${attempts}/${maxAttempts})")
                    view.postDelayed(this, checkInterval)
                }
            }
        }
    }

    // [v96] 立即开始检测（无延迟）
    WebViewLogger.logDebug("课表", "开始动态检测页面状态...")
    view.postDelayed(checkRunnable, initialDelay)
}

/**
 * [v72] 提取 HTML 并回调
 * [v73] 新增 homePageHtml 参数
 *
 * @param view WebView 实例
 * @param homePageHtml 首页 HTML（包含教学周信息）
 * @param onLoginSuccess 登录成功回调，参数为 (courseTableHtml, homePageHtml)
 */
private fun extractHtmlAndCallback(view: WebView, homePageHtml: String?, onLoginSuccess: (String, String?) -> Unit) {
    WebViewLogger.logDebug("课表", "开始提取课表 HTML...")

    // 使用 JavaScript 提取完整的 HTML
    view.evaluateJavascript(
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

        WebViewLogger.logParseDetail("课表 HTML 提取成功，长度: ${html.length}")

        // 检查是否包含课表数据
        val hasTaskActivity = html.contains("TaskActivity")
        val hasTable0 = html.contains("table0")
        val hasCourseName = html.contains("var courseName")
        WebViewLogger.logParseDetail("HTML 包含 TaskActivity: $hasTaskActivity, table0: $hasTable0, courseName: $hasCourseName")

        // [v73] 检查首页 HTML 是否包含教学周信息
        if (homePageHtml != null) {
            val hasWeekInfo = homePageHtml.contains("本周为") && homePageHtml.contains("教学周")
            android.util.Log.i("CHD_CurrentWeek", "[WebView] 首页 HTML 包含教学周信息: $hasWeekInfo, 长度: ${homePageHtml.length}")
        } else {
            android.util.Log.w("CHD_CurrentWeek", "[WebView] 首页 HTML 为 null")
        }

        // 调用登录成功回调，传递课表 HTML 和首页 HTML
        if (html.isNotEmpty()) {
            onLoginSuccess(html, homePageHtml)
        }
    }
}

/**
 * [v73] 提取首页 HTML（JS 渲染后的，包含教学周信息）
 * [v73 fix] 使用动态检测，等待教学周信息渲染完成
 *
 * @param view WebView 实例
 * @param onResult 提取结果回调
 */
private fun extractHomePageHtml(view: WebView, onResult: (String) -> Unit) {
    WebViewLogger.logDebug("首页", "开始动态检测教学周信息...")

    var attempts = 0
    val maxAttempts = 3   // [v73 fix6] 最多检测 3 次
    val checkInterval = 1000L  // [v73 fix6] 间隔 1 秒

    val checkRunnable = object : Runnable {
        override fun run() {
            // 使用 JavaScript 检测教学周信息是否已渲染
            view.evaluateJavascript("""
                (function() {
                    try {
                        // 检查是否包含教学周信息
                        var allText = document.body ? document.body.innerText : '';
                        var hasWeekInfo = allText.indexOf('本周为') !== -1 && allText.indexOf('教学周') !== -1;
                        if (hasWeekInfo) {
                            return { ready: true, html: document.documentElement.outerHTML };
                        }

                        // 检查 jquery 是否加载
                        var hasJquery = typeof jQuery !== 'undefined' || typeof $ !== 'undefined';
                        var hasBeangle = typeof beangle !== 'undefined';

                        return {
                            ready: false,
                            hasJquery: hasJquery,
                            hasBeangle: hasBeangle,
                            bodyLength: document.body ? document.body.innerHTML.length : 0
                        };
                    } catch(e) {
                        return { ready: false, error: e.message };
                    }
                })();
            """) { result ->
                android.util.Log.d("CHD_CurrentWeek", "[首页检测] 尝试 ${attempts + 1}/$maxAttempts, 结果: ${result.take(200)}")

                // 检查是否已渲染
                if (result.contains("\"ready\":true")) {
                    android.util.Log.i("CHD_CurrentWeek", "[首页检测] 教学周信息已渲染")
                    // 提取 HTML
                    extractHtmlFromWebView(view, onResult)
                } else if (attempts >= maxAttempts) {
                    android.util.Log.w("CHD_CurrentWeek", "[首页检测] 等待超时，尝试提取 HTML...")
                    extractHtmlFromWebView(view, onResult)
                } else {
                    attempts++
                    view.postDelayed(this, checkInterval)
                }
            }
        }
    }

    // [v73 fix6] 初始延迟 1.5 秒，确保页面有足够时间渲染
    view.postDelayed(checkRunnable, 1500L)
}

/**
 * 从 WebView 提取 HTML
 */
private fun extractHtmlFromWebView(view: WebView, onResult: (String) -> Unit) {
    view.evaluateJavascript(
        "(function() { return document.documentElement.outerHTML; })();"
    ) { htmlResult ->
        // 解码 JSON 编码的字符串
        val html = if (htmlResult.startsWith("\"")) {
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

        WebViewLogger.logDebug("首页", "首页 HTML 提取成功，长度: ${html.length}")

        // 检查是否包含教学周信息
        val hasWeekInfo = html.contains("本周为") && html.contains("教学周")
        android.util.Log.i("CHD_CurrentWeek", "[WebView] 首页包含教学周信息: $hasWeekInfo, 长度: ${html.length}")

        if (!hasWeekInfo) {
            android.util.Log.w("CHD_CurrentWeek", "[WebView] 首页未找到教学周信息")
            // 打印 body 内容用于调试
            val bodyStart = html.indexOf("<body")
            val bodyEnd = html.indexOf("</body>")
            if (bodyStart >= 0 && bodyEnd > bodyStart) {
                val bodyContent = html.substring(bodyStart, minOf(bodyEnd + 7, bodyStart + 2000))
                android.util.Log.d("CHD_CurrentWeek", "Body 内容片段: $bodyContent")
            }
        }

        onResult(html)
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
