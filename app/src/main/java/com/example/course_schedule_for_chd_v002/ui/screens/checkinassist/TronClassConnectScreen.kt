package com.example.course_schedule_for_chd_v002.ui.screens.checkinassist

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.course_schedule_for_chd_v002.data.local.preferences.UserPreferences
import com.example.course_schedule_for_chd_v002.service.checkin.polling.TronClassOrgResolver
import com.example.course_schedule_for_chd_v002.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * [v129] B5 畅课连接 ViewModel：org 域名解析 + token 捕获落盘。
 * token 读取方式依据 B5-trpc-api-research.md：畅课 Web 登录态存 `localStorage["token"]`。
 */
class TronClassConnectViewModel(
    private val prefs: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(TronClassConnectUiState())
    val uiState: StateFlow<TronClassConnectUiState> = _uiState.asStateFlow()

    /** 已保存的连接（回显用） */
    init {
        viewModelScope.launch {
            val token = prefs.tronClassToken.first()
            if (!token.isNullOrBlank()) {
                _uiState.value = TronClassConnectUiState(
                    baseUrl = prefs.tronClassBaseUrl.first().orEmpty(),
                    stage = ConnectStage.Connected,
                    connectedAt = prefs.tronClassConnectedAt.first()
                )
            } else {
                resolveOrg()
            }
        }
    }

    /** org 接口查 CHD 租户 apiUrl（失败可手输） */
    fun resolveOrg(keywords: String = "长安大学") {
        _uiState.value = _uiState.value.copy(stage = ConnectStage.ResolvingOrg)
        viewModelScope.launch {
            val org = withContext(Dispatchers.IO) {
                runCatching { TronClassOrgResolver.resolve(OkHttpClient(), keywords) }.getOrNull()
            }
            if (org?.apiUrl != null) {
                _uiState.value = TronClassConnectUiState(
                    baseUrl = org.apiUrl!!,
                    stage = ConnectStage.WaitingLogin,
                    orgName = org.orgName
                )
            } else {
                _uiState.value = TronClassConnectUiState(stage = ConnectStage.ManualInput)
            }
        }
    }

    /** 手输域名加载 */
    fun loadManual(url: String) {
        val normalized = url.trim().let { if (it.startsWith("http")) it else "https://$it" }
        _uiState.value = TronClassConnectUiState(baseUrl = normalized, stage = ConnectStage.WaitingLogin)
    }

    /** WebView 注入回调：捕获到非空 token */
    fun onTokenCaptured(token: String, originUrl: String) {
        viewModelScope.launch {
            prefs.saveTronClassConnection(token, originUrl)
            _uiState.value = _uiState.value.copy(
                stage = ConnectStage.Connected,
                connectedAt = System.currentTimeMillis()
            )
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            prefs.clearTronClassConnection()
            _uiState.value = TronClassConnectUiState(stage = ConnectStage.ManualInput)
        }
    }
}

data class TronClassConnectUiState(
    val baseUrl: String = "",
    val stage: ConnectStage = ConnectStage.ResolvingOrg,
    val orgName: String? = null,
    val connectedAt: Long = 0L
)

enum class ConnectStage { ResolvingOrg, ManualInput, WaitingLogin, Connected }

/**
 * [v129] B5 畅课连接页：WebView 打开学校租户畅课 Web（学校 CAS 登录，与教务同源），
 * 登录完成后注入 JS 读 `localStorage.getItem("token")` 捕获登录态存 DataStore。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TronClassConnectScreen(
    onBack: () -> Unit,
    viewModel: TronClassConnectViewModel = org.koin.androidx.compose.koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("连接畅课账号") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            when (state.stage) {
                ConnectStage.ResolvingOrg -> {
                    Text("正在查询学校畅课域名…")
                }
                ConnectStage.ManualInput -> {
                    Text(
                        "自动查询学校域名失败，请手动输入畅课网址（通常形如 https://xxx.tronclass.com.cn）",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    var url by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("畅课网址") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadManual(url) }, modifier = Modifier.fillMaxWidth()) {
                        Text("加载并登录")
                    }
                }
                ConnectStage.WaitingLogin -> {
                    Text(
                        "在下方登录畅课（学校统一认证）。登录成功后本页会自动捕获登录态并返回。" +
                            "当前域名：${state.baseUrl}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    TokenCaptureWebView(
                        baseUrl = state.baseUrl,
                        onTokenCaptured = viewModel::onTokenCaptured
                    )
                }
                ConnectStage.Connected -> {
                    val timeText = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(state.connectedAt))
                    Text(
                        "✅ 畅课已连接（$timeText）。\n轮询通道可用：开关在「签到辅助」页的「轮询通道」区。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onBack, modifier = Modifier.weight(1f)) { Text("返回签到辅助") }
                        TextButton(onClick = viewModel::disconnect, modifier = Modifier.weight(1f)) { Text("断开连接") }
                    }
                }
            }
        }
    }
}

/**
 * WebView + 注入：每次页面加载完成后读 localStorage token；SPA 登录不触发新加载，
 * 故再以 3 秒间隔补注（最多 10 分钟）。捕获标志用 AtomicBoolean——JSInterface 回调在
 * WebView JS 线程，不能直写 Compose 状态。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TokenCaptureWebView(
    baseUrl: String,
    onTokenCaptured: (token: String, originUrl: String) -> Unit
) {
    val captured = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val readJs = { wv: WebView? ->
        wv?.evaluateJavascript("androidBridge.readToken(localStorage.getItem('token'));", null)
    }
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true // localStorage 必需
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun readToken(token: String?) {
                            if (!token.isNullOrBlank() && !captured.get()) {
                                captured.set(true)
                                AppLogger.i("TronClassConnect", "token 已捕获（长度 ${token.length}）")
                                onTokenCaptured(token, url.orEmpty())
                            }
                        }
                    },
                    "androidBridge"
                )
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        readJs(view)
                    }
                }
                webViewRef.value = this
                loadUrl(baseUrl)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
    // SPA 兜底：登录成功不触发 onPageFinished 时轮询补读（主线程 evaluateJavascript 安全）
    LaunchedEffect(baseUrl) {
        val start = System.currentTimeMillis()
        while (!captured.get() && System.currentTimeMillis() - start < 10 * 60_000) {
            kotlinx.coroutines.delay(3000)
            readJs(webViewRef.value)
        }
    }
}
