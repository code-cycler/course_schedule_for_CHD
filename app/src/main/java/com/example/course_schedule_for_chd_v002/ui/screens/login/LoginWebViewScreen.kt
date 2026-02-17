package com.example.course_schedule_for_chd_v002.ui.screens.login

import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.webkit.CookieManager
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.course_schedule_for_chd_v002.util.Constants
import okhttp3.OkHttpClient
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

/**
 * WebView 登录界面
 *
 * 新流程：
 * 1. 直接加载教务系统主页
 * 2. 未登录时系统自动跳转到统一认证页面
 * 3. 用户完成登录后自动返回教务系统
 * 4. 用户手动进入"学生课表"页面
 * 5. 用户点击"获取课表"按钮开始爬取数据
 *
 * @param onFetchCourseTable 用户点击"获取课表"按钮时的回调
 */
@Composable
fun LoginWebViewScreen(
    onFetchCourseTable: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var currentUrl by remember { mutableStateOf("") }
    var isOnCourseTablePage by remember { mutableStateOf(false) }
    // 保存 WebView 引用以便刷新
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    // 跟踪导航状态
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    // 跟踪用户是否已登录（访问过教务系统）
    var isLoggedIn by remember { mutableStateOf(false) }
    // 跟踪课表数据是否已加载
    var isCourseDataLoaded by remember { mutableStateOf(false) }
    // 跟踪检测次数（用于轮询）
    var detectionAttempts by remember { mutableStateOf(0) }
    // 检测结果详情（用于调试）
    var detectionDetails by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 顶部状态栏（添加系统状态栏内边距）
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
                        } else {
                            Text(
                                text = when {
                                    isOnCourseTablePage && isCourseDataLoaded -> "[OK] 课表数据已加载"
                                    isOnCourseTablePage -> "[OK] 已进入课表页面"
                                    isLoggedIn -> "[OK] 已登录 - 可跳转课表"
                                    else -> "[!] 请登录系统"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = when {
                                    isOnCourseTablePage -> MaterialTheme.colorScheme.primary
                                    isLoggedIn -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                                }
                            )
                        }
                    }

                    // 显示当前 URL（截断显示）
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
                        webViewRef?.let { webView ->
                            // 刷新前先保存 cookie
                            CookieManager.getInstance().flush()
                            // 执行刷新
                            webView.reload()
                        }
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
                    onClick = {
                        webViewRef?.goBack()
                    },
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
                    onClick = {
                        webViewRef?.goForward()
                    },
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
                        // 保存 WebView 引用
                        webViewRef = this

                        // 配置 WebView 设置
                        settings.apply {
                            // ===== 基础设置 =====
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false

                            // ===== 动态内容支持（AJAX/JavaScript）=====
                            // 允许 JavaScript 打开新窗口
                            javaScriptCanOpenWindowsAutomatically = true
                            // 启用 DOM 缓存（支持动态内容）
                            domStorageEnabled = true
                            // 不阻止网络加载
                            blockNetworkLoads = false
                            // 允许内容 URL 访问
                            allowContentAccess = true
                            // 允许通用访问（某些 AJAX 需要）
                            allowUniversalAccessFromFileURLs = true
                            allowFileAccessFromFileURLs = true

                            // ===== 网络和缓存设置 =====
                            cacheMode = WebSettings.LOAD_DEFAULT
                            // 允许混合内容（HTTP 和 HTTPS）
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            // 允许文件访问
                            allowFileAccess = true
                            allowContentAccess = true

                            // ===== 确保 CSS 和资源正确加载 =====
                            // 自动加载图片
                            loadsImagesAutomatically = true
                            // 不阻止网络图片
                            blockNetworkImage = false
                            // 启用数据库（某些 CSS 可能需要）
                            databaseEnabled = true
                            // 启用地理定位（某些页面可能需要）
                            setGeolocationEnabled(true)

                            // ===== 强制使用电脑端布局（关键设置）=====
                            // 设置桌面版 User-Agent，让服务器返回电脑版页面
                            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                            // 使用宽视口，不缩放以适应屏幕
                            useWideViewPort = true
                            // 不使用概览模式加载（避免自动缩放）
                            loadWithOverviewMode = false
                            // 禁用文本缩放（保持原始大小）
                            minimumFontSize = 1
                            // 设置文本缩放为100%
                            textZoom = 100

                            // ===== 媒体和插件支持 =====
                            // 启用插件（某些动态内容可能需要）
                            pluginState = WebSettings.PluginState.ON_DEMAND
                            // 允许文件访问
                            allowFileAccess = true
                        }

                        // ===== 启用 WebView 调试（帮助排查问题）=====
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                            WebView.setWebContentsDebuggingEnabled(true)
                        }

                        // ===== 记录 WebView Provider 信息（兼容性调试）=====
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val webViewPackage = WebView.getCurrentWebViewPackage()
                            android.util.Log.i("WebView", "Provider: ${webViewPackage?.packageName}")
                            android.util.Log.i("WebView", "Version: ${webViewPackage?.versionName}")
                        } else {
                            android.util.Log.i("WebView", "Provider: (Android < O, using default WebView)")
                        }

                        // ===== 记录 User-Agent（调试资源加载问题）=====
                        android.util.Log.i("WebView", "User-Agent: ${settings.userAgentString}")

                        // ===== 添加 WebChromeClient 处理动态内容 =====
                        webChromeClient = object : WebChromeClient() {
                            // 处理 JavaScript Alert
                            override fun onJsAlert(
                                view: WebView?,
                                url: String?,
                                message: String?,
                                result: JsResult?
                            ): Boolean {
                                // 允许 alert 继续执行
                                result?.confirm()
                                return true
                            }

                            // 处理 JavaScript Confirm
                            override fun onJsConfirm(
                                view: WebView?,
                                url: String?,
                                message: String?,
                                result: JsResult?
                            ): Boolean {
                                // 默认确认
                                result?.confirm()
                                return true
                            }

                            // 处理 JavaScript Prompt
                            override fun onJsPrompt(
                                view: WebView?,
                                url: String?,
                                message: String?,
                                defaultValue: String?,
                                result: JsPromptResult?
                            ): Boolean {
                                // 默认取消
                                result?.cancel()
                                return true
                            }

                            // 处理控制台消息（用于调试）
                            override fun onConsoleMessage(message: String?, lineNumber: Int, sourceID: String?) {
                                // 可以在这里打印日志帮助调试
                                super.onConsoleMessage(message, lineNumber, sourceID)
                            }

                            // 处理加载进度
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                super.onProgressChanged(view, newProgress)
                                // 进度小于100时显示加载中
                                if (newProgress < 100) {
                                    isLoading = true
                                }
                            }
                        }

                        // ===== Cookie 配置（PHP Session 关键）=====
                        val webCookieManager = CookieManager.getInstance()
                        webCookieManager.setAcceptCookie(true)
                        webCookieManager.setAcceptThirdPartyCookies(this@apply, true)

                        // 配置 WebViewClient
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(
                                view: WebView?,
                                url: String?,
                                favicon: Bitmap?
                            ) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                url?.let { currentUrl = it }
                                // v9: 移除 onPageStarted 中的注入（时机太早，JS 环境未就绪）
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                url?.let {
                                    currentUrl = it
                                    // 检测是否在课表页面
                                    isOnCourseTablePage = it.contains("courseTableForStd")
                                    // 检测是否已登录（在教务系统内，且不在登录页面）
                                    isLoggedIn = it.contains("bkjw.chd.edu.cn") &&
                                            !it.contains("ids.chd.edu.cn") &&
                                            !it.contains("cas.chd.edu.cn")

                                    // ===== v10: 直接从 HTML 解析课表数据（绕过 JavaScript 依赖）=====
                                    // 根因: Google WebView 不加载页面外部 JS 资源（jQuery、underscore、bg）
                                    // 方案: 直接从 HTML 源码中解析 table0.activities 数据
                                    if (isOnCourseTablePage) {
                                        // 重置状态
                                        detectionAttempts = 0
                                        isCourseDataLoaded = false

                                        android.util.Log.d("LoginWebView", "v10: 开始获取 HTML 并解析课表数据...")

                                        // 解析 TaskActivity 参数的辅助函数（定义在lambda外部）
                                        fun parseTaskActivityParams(paramsStr: String): List<String> {
                                            val params = mutableListOf<String>()
                                            val current = StringBuilder()
                                            var inString = false
                                            var stringChar = ' '

                                            for (char in paramsStr) {
                                                when {
                                                    !inString && (char == '"' || char == '\'') -> {
                                                        inString = true
                                                        stringChar = char
                                                        current.append(char)
                                                    }
                                                    inString && char == stringChar -> {
                                                        inString = false
                                                        current.append(char)
                                                    }
                                                    !inString && char == ',' -> {
                                                        params.add(current.toString())
                                                        current.clear()
                                                    }
                                                    else -> current.append(char)
                                                }
                                            }
                                            if (current.isNotEmpty()) {
                                                params.add(current.toString())
                                            }
                                            return params
                                        }

                                        // 解析课表数据的函数
                                        fun doParseCourseData(html: String, attemptNum: Int) {
                                            var courseCount = 0
                                            val foundCourses = mutableListOf<String>()
                                            var hasTaskActivity = false

                                            // 实际 HTML 格式：
                                            // activity = new TaskActivity(actTeacherId.join(','),actTeacherName.join(','),"59153(24ZY1816.01)",courseName,"24ZY1816.01","633","*WH2201","周次字符串",null,"",assistantName,"");
                                            // table0.activities[index][table0.activities[index].length]=activity;

                                            // 方法1: 直接搜索 new TaskActivity(...) 模式
                                            // 格式: new TaskActivity(参数1,参数2,"任务ID(课程代码)",courseName,"课程代码","教室ID","教室名","周次",null,"",assistantName,"")
                                            val taskPattern = """new\s+TaskActivity\s*\(([^;]+)\)""".toRegex()
                                            val taskMatches = taskPattern.findAll(html)

                                            for (taskMatch in taskMatches) {
                                                hasTaskActivity = true
                                                val paramsStr = taskMatch.groupValues[1]
                                                val params = parseTaskActivityParams(paramsStr)

                                                // TaskActivity 参数格式（11个参数）：
                                                // 0: actTeacherId.join(',') - 教师ID
                                                // 1: actTeacherName.join(',') - 教师名称
                                                // 2: "任务ID(课程代码)" - 如 "59153(24ZY1816.01)"
                                                // 3: courseName - 课程名称变量
                                                // 4: "课程代码" - 如 "24ZY1816.01"
                                                // 5: "教室ID" - 如 "633"
                                                // 6: "教室名" - 如 "*WH2201"
                                                // 7: "周次字符串" - 如 "00000000111111111100000..."
                                                // 8: null
                                                // 9: ""
                                                // 10: assistantName
                                                if (params.size >= 7) {
                                                    val teacherName = params[1].trim().trim('"')
                                                    val taskInfo = params[2].trim().trim('"')  // "59153(24ZY1816.01)"
                                                    val courseNameOrVar = params[3].trim()
                                                    val courseCode = params[4].trim().trim('"')
                                                    val roomName = params[6].trim().trim('"')

                                                    // 提取课程名：如果 params[3] 是变量名（如 courseName），则从 taskInfo 提取
                                                    val courseName = if (courseNameOrVar == "courseName" || courseNameOrVar.startsWith("courseName")) {
                                                        // 从 taskInfo 提取课程代码作为标识
                                                        courseCode
                                                    } else {
                                                        courseNameOrVar.trim('"')
                                                    }

                                                    if (courseName.isNotEmpty()) {
                                                        courseCount++
                                                        foundCourses.add("$courseName - $teacherName - $roomName")
                                                    }
                                                }
                                            }

                                            // 方法2: 备选 - 检查 table0.activities 赋值语句
                                            val activityAssignPattern = """table0\.activities\[[^\]]+\]\[table0\.activities\[[^\]]+\]\.length\]=activity""".toRegex()
                                            val hasActivityAssign = activityAssignPattern.containsMatchIn(html)

                                            android.util.Log.i("LoginWebView", "v10: 尝试#$attemptNum TaskActivity=$hasTaskActivity, 活动赋值=$hasActivityAssign, 解析课程数=$courseCount, HTML长度=${html.length}")

                                            // 输出前5门课程用于调试
                                            foundCourses.take(5).forEach {
                                                android.util.Log.d("LoginWebView", "v10: 课程 $it")
                                            }

                                            // 如果 HTML 太短，可能是中间加载页面
                                            if (html.length < 10000) {
                                                android.util.Log.w("LoginWebView", "v10: HTML 长度过短(${html.length})，可能页面未完全加载")
                                            }

                                            if (courseCount > 0) {
                                                isCourseDataLoaded = true
                                                detectionAttempts = attemptNum
                                                detectionDetails = "OK (v10 $courseCount 门)"
                                                android.util.Log.i("LoginWebView", "v10: 解析成功，共 $courseCount 门课程")
                                            } else if (attemptNum < 5) {
                                                // 延迟重试（增加到5次）
                                                android.util.Log.d("LoginWebView", "v10: 未找到数据，稍后重试")
                                                view?.postDelayed({
                                                    if (!isCourseDataLoaded) {
                                                        val nextAttempt = attemptNum + 1
                                                        detectionAttempts = nextAttempt
                                                        view?.evaluateJavascript("(function(){return document.documentElement.outerHTML;})();") { retryResult ->
                                                            val retryHtml = retryResult.trim().trim('"')
                                                                .replace("\\u003C", "<")
                                                                .replace("\\u003E", ">")
                                                                .replace("\\u0026", "&")
                                                                .replace("\\u0022", "\"")
                                                                .replace("\\u0027", "'")
                                                                .replace("\\n", "\n")
                                                                .replace("\\r", "\r")
                                                                .replace("\\t", "\t")
                                                                .replace("\\/", "/")
                                                            doParseCourseData(retryHtml, nextAttempt)
                                                        }
                                                    }
                                                }, 2000)  // 增加延迟到2秒
                                            } else {
                                                android.util.Log.w("LoginWebView", "v10: 达到最大重试次数(5)")
                                                detectionDetails = "解析失败"
                                            }
                                        }

                                        // 首次获取 HTML 并解析
                                        // v11: 检查 iframe 和动态加载的内容
                                        val jsGetContent = """
                                            (function() {
                                                var result = {
                                                    mainHtml: '',
                                                    iframeHtml: '',
                                                    hasIframe: false,
                                                    contentDivHtml: '',
                                                    bodyHtml: ''
                                                };

                                                // 获取主页面 HTML
                                                result.mainHtml = document.documentElement.outerHTML || '';

                                                // 检查 iframe
                                                var iframes = document.getElementsByTagName('iframe');
                                                if (iframes.length > 0) {
                                                    result.hasIframe = true;
                                                    try {
                                                        result.iframeHtml = iframes[0].contentDocument ? iframes[0].contentDocument.documentElement.outerHTML : '';
                                                    } catch(e) {
                                                        result.iframeHtml = 'cross-origin';
                                                    }
                                                }

                                                // 获取 contentDiv 内容
                                                var contentDiv = document.getElementById('contentDiv');
                                                if (contentDiv) {
                                                    result.contentDivHtml = contentDiv.innerHTML || '';
                                                }

                                                // 获取 body 完整内容
                                                result.bodyHtml = document.body ? document.body.innerHTML : '';

                                                return JSON.stringify(result);
                                            })();
                                        """.trimIndent()

                                        // v14: 使用 OkHttp 直接请求课表页面（绕过 WebView JS 依赖）
                                        android.util.Log.d("LoginWebView", "v14: 使用 OkHttp 请求课表页面...")

                                        // WebView 获取的回退方法（定义在使用之前）
                                        fun fetchViaWebView() {
                                            android.util.Log.d("LoginWebView", "v14: 回退到 WebView 获取")
                                            val jsGetSimple = "(function(){return document.body?document.body.innerHTML:'';})();"
                                            view?.evaluateJavascript(jsGetSimple) { result ->
                                                val html = result.trim().trim('"')
                                                    .replace("\\u003C", "<")
                                                    .replace("\\u003E", ">")
                                                    .replace("\\u0026", "&")
                                                    .replace("\\u0022", "\"")
                                                    .replace("\\\"", "\"")
                                                    .replace("\\n", "\n")
                                                    .replace("\\r", "\r")
                                                    .replace("\\t", "\t")
                                                doParseCourseData(html, 1)
                                            }
                                        }

                                        // 从 WebView CookieManager 获取 Cookie
                                        val webViewCookies = webCookieManager.getCookie(currentUrl)
                                        android.util.Log.d("LoginWebView", "v14: WebView Cookies: ${webViewCookies?.take(100)}...")

                                        // v15: 先从 WebView 获取表单参数，再使用 POST 请求
                                        android.util.Log.d("LoginWebView", "v15: 从 WebView 获取表单参数...")

                                        // JavaScript 获取表单参数
                                        // 注意: CSS 选择器中点号需要转义
                                        val jsGetFormParams = """
                                            (function() {
                                                var params = {};
                                                // 方法1: 使用属性选择器转义
                                                var semesterInput = document.querySelector('input[name="semester\\.id"]');
                                                if (!semesterInput) {
                                                    // 方法2: 遍历所有 input 查找
                                                    var inputs = document.querySelectorAll('input[type="hidden"]');
                                                    for (var i = 0; i < inputs.length; i++) {
                                                        if (inputs[i].name === 'semester.id') {
                                                            semesterInput = inputs[i];
                                                            break;
                                                        }
                                                    }
                                                }
                                                if (semesterInput) params.semesterId = semesterInput.value;

                                                // 获取 ids (学生ID)
                                                var idsInput = document.querySelector('input[name="ids"]');
                                                if (idsInput) params.ids = idsInput.value;

                                                // 获取 ignoreHead
                                                var ignoreHeadInput = document.querySelector('input[name="ignoreHead"]');
                                                if (ignoreHeadInput) params.ignoreHead = ignoreHeadInput.value;

                                                // 尝试从 Cookie 解析 semester.id
                                                if (!params.semesterId) {
                                                    var cookies = document.cookie.split(';');
                                                    for (var i = 0; i < cookies.length; i++) {
                                                        var cookie = cookies[i].trim();
                                                        if (cookie.indexOf('semester.id=') === 0) {
                                                            params.semesterId = cookie.substring('semester.id='.length);
                                                            break;
                                                        }
                                                    }
                                                }

                                                return JSON.stringify(params);
                                            })();
                                        """.trimIndent()

                                        view?.evaluateJavascript(jsGetFormParams) { formParamsResult ->
                                            android.util.Log.d("LoginWebView", "v15: 表单参数原始结果=$formParamsResult")

                                            // 解析表单参数
                                            val formParams = try {
                                                if (formParamsResult != "null" && formParamsResult.isNotBlank()) {
                                                    val jsonStr = formParamsResult.trim().trim('"')
                                                        .replace("\\\"", "\"")
                                                        .replace("\\\\", "\\")
                                                    android.util.Log.d("LoginWebView", "v15: JSON字符串=$jsonStr")
                                                    org.json.JSONObject(jsonStr)
                                                } else null
                                            } catch (e: Exception) {
                                                android.util.Log.e("LoginWebView", "v15: 解析表单参数失败: ${e.message}")
                                                null
                                            }

                                            val semesterId = formParams?.optString("semesterId") ?: ""
                                            val studentIds = formParams?.optString("ids") ?: ""
                                            val ignoreHead = formParams?.optString("ignoreHead") ?: "1"

                                            android.util.Log.d("LoginWebView", "v15: semesterId=$semesterId, ids=$studentIds, ignoreHead=$ignoreHead")

                                            // 构建课表 URL (POST 请求)
                                            val courseTableUrl = currentUrl

                                            // 在后台线程执行 POST 请求
                                            Thread {
                                                try {
                                                    val client = OkHttpClient.Builder()
                                                        .followRedirects(true)
                                                        .build()

                                                    // 构建表单数据
                                                    val formBody = okhttp3.FormBody.Builder()
                                                        .add("ignoreHead", ignoreHead)
                                                        .apply {
                                                            if (semesterId.isNotEmpty()) {
                                                                add("semester.id", semesterId)
                                                            }
                                                            if (studentIds.isNotEmpty()) {
                                                                add("ids", studentIds)
                                                            }
                                                        }
                                                        .build()

                                                    // 根据目标 URL 确定协议
                                                    val protocol = if (courseTableUrl.startsWith("https://")) "https" else "http"

                                                    val request = okhttp3.Request.Builder()
                                                        .url(courseTableUrl)
                                                        .post(formBody)
                                                        .apply {
                                                            if (!webViewCookies.isNullOrBlank()) {
                                                                header("Cookie", webViewCookies)
                                                            }
                                                            header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
                                                            header("Accept", "text/html,application/xhtml+xml")
                                                            header("Referer", "$protocol://bkjw.chd.edu.cn/")
                                                            header("Content-Type", "application/x-www-form-urlencoded")
                                                        }
                                                        .build()

                                                    val response = client.newCall(request).execute()
                                                    val html = response.body?.string() ?: ""

                                                    android.util.Log.i("LoginWebView", "v15: POST 响应码=${response.code}, HTML长度=${html.length}")

                                                    // 在主线程解析
                                                    view?.post {
                                                        if (html.contains("TaskActivity") || html.contains("table0")) {
                                                            android.util.Log.d("LoginWebView", "v15: HTML 包含课表数据!")
                                                            doParseCourseData(html, 1)
                                                        } else {
                                                            android.util.Log.w("LoginWebView", "v15: HTML 不包含课表数据，尝试直接解析")
                                                            // 显示 HTML 内容前 500 字符用于调试
                                                            android.util.Log.d("LoginWebView", "v15: HTML预览=${html.take(500)}")
                                                            doParseCourseData(html, 1)
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    android.util.Log.e("LoginWebView", "v15: POST 请求失败: ${e.message}")
                                                    view?.post {
                                                        fetchViaWebView()
                                                    }
                                                }
                                            }.start()
                                        }
                                    } else {
                                        isCourseDataLoaded = false
                                        detectionAttempts = 0
                                    }
                                }
                            // 更新导航状态
                            view?.let { wv ->
                                canGoBack = wv.canGoBack()
                                canGoForward = wv.canGoForward()
                            }
                            // ===== 强制持久化 Cookie（解决 PHP Session 丢失）=====
                            webCookieManager.flush()
                            // 页面加载完成后设置 isLoading 为 false
                            isLoading = false
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            url: String?
                        ): Boolean {
                            // 让 WebView 处理所有 URL 加载
                            // 重定向时会自动携带已有的 cookie
                            return false
                        }

                        // 处理 SSL 错误，确保 HTTPS 资源能加载
                        override fun onReceivedSslError(
                            view: WebView?,
                            handler: SslErrorHandler?,
                            error: SslError?
                        ) {
                            // 继续加载（注意：生产环境应验证证书）
                            handler?.proceed()
                        }

                        // v8: 移除 shouldInterceptRequest 中的 OkHttp 请求
                        // 原因: OkHttp 重复请求会触发服务器"不要过快点击"警告
                        // 现在只依赖 onPageStarted 中的脚本注入

                        // ===== 处理加载错误（避免白屏）=====
                        override fun onReceivedError(
                            view: WebView?,
                            errorCode: Int,
                            description: String?,
                            failingUrl: String?
                        ) {
                            super.onReceivedError(view, errorCode, description, failingUrl)
                            android.util.Log.e("WebView", "加载错误: $failingUrl, 错误: $description ($errorCode)")
                            // 即使出错也标记加载完成，避免无限 loading
                            isLoading = false
                        }

                        // ===== 诊断资源加载问题 =====
                        override fun onLoadResource(view: WebView?, url: String?) {
                            super.onLoadResource(view, url)
                            // 记录 JavaScript 资源加载
                            if (url != null && (url.contains(".js") || url.contains("script"))) {
                                android.util.Log.d("WebView", "加载 JS 资源: $url")
                            }
                        }

                        // Android 6.0+ 的错误回调
                        override fun onReceivedHttpError(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?,
                            errorResponse: android.webkit.WebResourceResponse?
                        ) {
                            super.onReceivedHttpError(view, request, errorResponse)
                            android.util.Log.e("WebView", "HTTP 错误: ${request?.url}, 状态码: ${errorResponse?.statusCode}")
                        }

                        // Android 5.0+ 的错误回调
                        override fun onReceivedError(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?,
                            error: android.webkit.WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                android.util.Log.e("WebView", "资源错误: ${request?.url}, 错误: ${error?.description} (${error?.errorCode})")
                            }
                        }
                    }

                    // ===== 加载教务系统主页 =====
                    // 未登录会自动跳转到认证页面
                    loadUrl(Constants.CasUrls.WEBVIEW_ENTRY_URL)
                    }
            },
            update = { webView ->
                    // 可以在这里更新 WebView 配置
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // ===== 组件销毁时保存 Cookie =====
        DisposableEffect(Unit) {
            onDispose {
                // 确保在组件销毁时保存所有 cookie
                CookieManager.getInstance().flush()
                // 清理 WebView 引用
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
                // 使用说明
                Text(
                    text = when {
                        isOnCourseTablePage && isCourseDataLoaded -> "课表数据已加载，可获取"
                        isOnCourseTablePage -> "正在加载课表数据..."
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
                            // 使用 JavaScript 点击侧边栏的课表链接
                            // 教务系统的侧边栏链接结构
                            val jsClickCourseTable = """
                                (function() {
                                    console.log('[跳转课表] 开始查找课表链接...');

                                    // 方法1: 查找包含 courseTableForStd 的链接（最精确）
                                    var links = document.querySelectorAll('a[href*="courseTableForStd"]');
                                    if (links.length > 0) {
                                        console.log('[跳转课表] 找到 courseTableForStd 链接');
                                        // 使用 bg.Go 方法点击（教务系统框架方法）
                                        if (typeof bg !== 'undefined' && bg.Go) {
                                            bg.Go(links[0], 'main');
                                        } else {
                                            links[0].click();
                                        }
                                        return true;
                                    }

                                    // 方法2: 查找文本为"我的课表"的链接（注意：是"我的课表"不是"学生课表"）
                                    var menuLinks = document.querySelectorAll('ul.menu a.p_1');
                                    for (var i = 0; i < menuLinks.length; i++) {
                                        var text = (menuLinks[i].textContent || menuLinks[i].innerText || '').trim();
                                        if (text === '我的课表' || text === '课程表' || text.indexOf('课表') !== -1) {
                                            console.log('[跳转课表] 找到文本匹配链接: ' + text);
                                            if (typeof bg !== 'undefined' && bg.Go) {
                                                bg.Go(menuLinks[i], 'main');
                                            } else {
                                                menuLinks[i].click();
                                            }
                                            return true;
                                        }
                                    }

                                    // 方法3: 先展开"我的学业"菜单，再查找课表链接
                                    var myStudyMenu = null;
                                    var firstMenus = document.querySelectorAll('a.first_menu');
                                    for (var i = 0; i < firstMenus.length; i++) {
                                        var text = (firstMenus[i].textContent || firstMenus[i].innerText || '').trim();
                                        if (text.indexOf('我的学业') !== -1 || text.indexOf('学业') !== -1) {
                                            myStudyMenu = firstMenus[i];
                                            break;
                                        }
                                    }

                                    if (myStudyMenu) {
                                        // 展开"我的学业"菜单
                                        var parentLi = myStudyMenu.parentElement;
                                        if (parentLi && !parentLi.classList.contains('expand')) {
                                            myStudyMenu.click();
                                            console.log('[跳转课表] 展开"我的学业"菜单');
                                        }

                                        // 等待菜单展开后再次查找课表链接
                                        setTimeout(function() {
                                            var courseLinks = parentLi.querySelectorAll('a[href*="courseTableForStd"]');
                                            if (courseLinks.length > 0) {
                                                console.log('[跳转课表] 在"我的学业"菜单中找到课表链接');
                                                if (typeof bg !== 'undefined' && bg.Go) {
                                                    bg.Go(courseLinks[0], 'main');
                                                } else {
                                                    courseLinks[0].click();
                                                }
                                            }
                                        }, 500);
                                        return true;
                                    }

                                    // 方法4: 直接访问课表页面 URL
                                    console.log('[跳转课表] 未找到课表链接，直接跳转');
                                    window.location.href = '/eams/courseTableForStd.action';
                                    return false;
                                })();
                            """.trimIndent()

                            webView.evaluateJavascript(jsClickCourseTable) { result ->
                                android.util.Log.d("LoginWebView", "跳转课表 JS 结果: $result")
                            }
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
                    onClick = onFetchCourseTable,
                    enabled = isOnCourseTablePage && isCourseDataLoaded && !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isOnCourseTablePage && isCourseDataLoaded)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outline
                    )
                ) {
                    Text(
                        text = if (isOnCourseTablePage && !isCourseDataLoaded) "加载中..." else "获取课表",
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
