package com.example.course_schedule_for_chd_v002.ui.screens.login

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
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebRequestError

/**
 * GeckoView 登录界面 (v17)
 *
 * 使用 Firefox 内核替代系统 WebView，解决 Google WebView 140 兼容性问题
 *
 * @param onFetchCourseTable 用户点击"获取课表"按钮时的回调
 */
@Composable
fun GeckoViewScreen(
    onFetchCourseTable: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var currentUrl by remember { mutableStateOf("") }
    var isOnCourseTablePage by remember { mutableStateOf(false) }
    var geckoViewRef by remember { mutableStateOf<GeckoView?>(null) }
    var geckoSessionRef by remember { mutableStateOf<GeckoSession?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isLoggedIn by remember { mutableStateOf(false) }
    var isCourseDataLoaded by remember { mutableStateOf(false) }

    // GeckoRuntime 单例
    var geckoRuntime by remember { mutableStateOf<GeckoRuntime?>(null) }

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
                        } else {
                            Text(
                                text = when {
                                    isOnCourseTablePage && isCourseDataLoaded -> "[OK] 课表数据已加载"
                                    isOnCourseTablePage -> "[OK] 已进入课表页面 (GeckoView)"
                                    isLoggedIn -> "[OK] 已登录 - 可跳转课表"
                                    else -> "[!] 请登录系统 (GeckoView)"
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
                    onClick = { geckoSessionRef?.reload() },
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
                    onClick = { geckoSessionRef?.goBack() },
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
                    onClick = { geckoSessionRef?.goForward() },
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

        // GeckoView 区域
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                factory = { context ->
                    GeckoView(context).apply {
                        geckoViewRef = this

                        // 创建 GeckoRuntime（单例）
                        if (geckoRuntime == null) {
                            geckoRuntime = GeckoRuntime.create(context)
                        }

                        // 创建 GeckoSession
                        val session = GeckoSession()

                        // 配置 Session 设置
                        session.settings.apply {
                            setUserAgentMode(GeckoSessionSettings.USER_AGENT_MODE_DESKTOP)
                            setUserAgentOverride("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            setUseTrackingProtection(false)
                            setAllowJavascript(true)
                            setViewportMode(GeckoSessionSettings.VIEWPORT_MODE_DESKTOP)
                        }

                        // 设置导航代理
                        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
                            override fun onLocationChange(
                                session: GeckoSession,
                                url: String?,
                                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                                hasUserGesture: Boolean
                            ) {
                                url?.let {
                                    currentUrl = it
                                    isOnCourseTablePage = it.contains("courseTableForStd")
                                    isLoggedIn = it.contains("bkjw.chd.edu.cn") &&
                                            !it.contains("ids.chd.edu.cn") &&
                                            !it.contains("cas.chd.edu.cn")

                                    android.util.Log.d("GeckoView", "URL 变化: $it, 课表页=$isOnCourseTablePage, 已登录=$isLoggedIn")

                                    // 如果进入课表页面，重置状态
                                    if (isOnCourseTablePage) {
                                        isCourseDataLoaded = false
                                    }
                                }
                            }

                            override fun onCanGoBack(session: GeckoSession, canGoBackParam: Boolean) {
                                canGoBack = canGoBackParam
                            }

                            override fun onCanGoForward(session: GeckoSession, canGoForwardParam: Boolean) {
                                canGoForward = canGoForwardParam
                            }

                            override fun onLoadRequest(
                                session: GeckoSession,
                                request: GeckoSession.NavigationDelegate.LoadRequest
                            ): GeckoResult<AllowOrDeny>? {
                                android.util.Log.d("GeckoView", "加载请求: ${request.uri}")
                                return null // 允许加载
                            }

                            override fun onLoadError(
                                session: GeckoSession,
                                uri: String?,
                                error: WebRequestError
                            ): GeckoResult<String>? {
                                android.util.Log.e("GeckoView", "加载错误: $uri, code=${error.code}")
                                isLoading = false
                                return null
                            }
                        }

                        // 设置进度代理
                        session.progressDelegate = object : GeckoSession.ProgressDelegate {
                            override fun onPageStart(session: GeckoSession, url: String) {
                                isLoading = true
                                currentUrl = url
                                android.util.Log.d("GeckoView", "页面开始加载: $url")
                            }

                            override fun onPageStop(session: GeckoSession, success: Boolean) {
                                isLoading = false
                                android.util.Log.d("GeckoView", "页面加载完成: success=$success")

                                // 如果在课表页面且加载成功，标记数据已加载
                                if (isOnCourseTablePage && success) {
                                    // GeckoView 应该能正常渲染 JavaScript
                                    // 给一点延迟让 JS 执行完成
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        isCourseDataLoaded = true
                                        android.util.Log.i("GeckoView", "课表页面加载完成，数据已就绪")
                                    }, 1000)
                                }
                            }
                        }

                        // 设置内容代理
                        session.contentDelegate = object : GeckoSession.ContentDelegate {
                            override fun onTitleChange(session: GeckoSession, title: String?) {
                                android.util.Log.d("GeckoView", "标题变化: $title")
                            }

                            override fun onCrash(session: GeckoSession) {
                                android.util.Log.e("GeckoView", "GeckoView 崩溃!")
                                isLoading = false
                            }
                        }

                        // 打开 Session
                        session.open(geckoRuntime!!)
                        setSession(session)
                        geckoSessionRef = session

                        // 加载首页
                        android.util.Log.i("GeckoView", "加载入口 URL: ${Constants.CasUrls.WEBVIEW_ENTRY_URL}")
                        session.loadUri(Constants.CasUrls.WEBVIEW_ENTRY_URL)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 组件销毁时清理
        DisposableEffect(Unit) {
            onDispose {
                geckoViewRef = null
                geckoSessionRef = null
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
                        geckoSessionRef?.let { session ->
                            val jsClickCourseTable = """
                                (function() {
                                    var links = document.querySelectorAll('a[href*="courseTableForStd"]');
                                    if (links.length > 0) {
                                        if (typeof bg !== 'undefined' && bg.Go) {
                                            bg.Go(links[0], 'main');
                                        } else {
                                            links[0].click();
                                        }
                                        return true;
                                    }
                                    window.location.href = '/eams/courseTableForStd.action';
                                    return false;
                                })();
                            """.trimIndent()
                            session.loadUri("javascript:$jsClickCourseTable")
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
