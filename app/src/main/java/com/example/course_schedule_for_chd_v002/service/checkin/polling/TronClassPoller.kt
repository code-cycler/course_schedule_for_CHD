package com.example.course_schedule_for_chd_v002.service.checkin.polling

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * [v129] 畅课轮询结果（L2-build B5 规格 + 实现期细化 ParseError）。
 */
sealed interface TronClassPollResult {
    /** 请求成功且无进行中签到 */
    data object NoActiveCheckIn : TronClassPollResult

    /** 发现进行中签到（courseId 为畅课课程 id，与教务课程名不同源——courseHint 由管线按当前时间推断） */
    data class FoundCheckIn(val rollcalls: List<OngoingRollcall>, val rawJson: String) : TronClassPollResult

    /** 401/403：token 失效，需用户重新连接畅课 */
    data object SessionExpired : TronClassPollResult

    data class NetworkError(val message: String) : TronClassPollResult

    /** 2xx 但结构不认识（畅课改版）：保留原文供日志排查 */
    data class ParseError(val message: String, val rawJson: String) : TronClassPollResult
}

/** 进行中签到项（仅提取轮询所需的最小字段） */
data class OngoingRollcall(val rollcallId: String?, val courseId: String?, val rawFields: String)

/**
 * [v129] 畅课「进行中签到」轮询器（B5；端点与鉴权依据 B5-trpc-api-research.md）。
 *
 * `GET {baseUrl}/api/radar/rollcalls?api_version=1.1.0`，鉴权 `Authorization: Bearer <token>`。
 * baseUrl/token 以函数注入（DataStore 持有），SessionExpired 由 UI 引导重连。
 */
class TronClassPoller(
    private val client: OkHttpClient = OkHttpClient(),
    private val baseUrl: () -> String?,
    private val token: () -> String?
) {

    /** 同步请求（调用方放 IO 调度器） */
    fun pollOnce(): TronClassPollResult {
        val base = baseUrl()?.trimEnd('/')
            ?: return TronClassPollResult.NetworkError("未配置畅课域名")
        val tok = token()?.takeIf { it.isNotBlank() }
            ?: return TronClassPollResult.SessionExpired
        val request = Request.Builder()
            .url("$base/api/radar/rollcalls?api_version=1.1.0")
            // [v130] 鉴权头按二轮逆向实锤加固：畅课 /api 主链路用 x-session-id（Authorization
            // 仅 RMS 子系统）——双发并存，待首次真实登录后按日志校准收敛
            .header("x-session-id", tok)
            .header("Authorization", "Bearer $tok")
            .header("X-Requested-With", "XMLHttpRequest")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { resp ->
                when {
                    resp.code == 401 || resp.code == 403 -> TronClassPollResult.SessionExpired
                    !resp.isSuccessful -> {
                        // [v130] 非 2xx 留 body 前 200 字符（校准期排错：鉴权头形态/参数形态）
                        val body = runCatching { resp.body?.string()?.take(200) }.getOrNull().orEmpty()
                        TronClassPollResult.NetworkError("HTTP ${resp.code} $body")
                    }
                    else -> parseOngoingRollcalls(resp.body?.string().orEmpty())
                }
            }
        } catch (e: IOException) {
            TronClassPollResult.NetworkError(e.message ?: e.javaClass.simpleName)
        } catch (e: Exception) {
            TronClassPollResult.NetworkError(e.message ?: e.javaClass.simpleName)
        }
    }

    companion object {
        /**
         * 解析 `{"rollcalls":[{...}]}`（顶层直接是数组也兼容——api 1.1.0 实际形态未实测）。
         * 手写字符串解析（org.json 在 JVM 单测不可用，项目惯例见 RoomsJson/NotificationLogJson）。
         */
        fun parseOngoingRollcalls(json: String): TronClassPollResult {
            val s = json.trim()
            if (s.isEmpty()) return TronClassPollResult.ParseError("空响应", json)
            // 找 "rollcalls" 键后的数组段；顶层裸数组直接整体当数组段
            val arrayRaw = if (s.startsWith("[")) {
                s
            } else {
                val keyIdx = JsonScan.findKey(s, listOf("rollcalls"))
                    ?: return if (JsonScan.findKey(s, listOf("error", "message")) != null) {
                        TronClassPollResult.ParseError("响应含 error/message 键：${s.take(200)}", s)
                    } else {
                        TronClassPollResult.NoActiveCheckIn
                    }
                val open = s.indexOf('[', keyIdx)
                if (open < 0) return TronClassPollResult.ParseError("rollcalls 键后无数组", s)
                val close = JsonScan.matchBracket(s, open)
                    ?: return TronClassPollResult.ParseError("数组未闭合", s)
                s.substring(open + 1, close)
            }
            val items = JsonScan.splitObjects(arrayRaw).map { obj ->
                OngoingRollcall(
                    rollcallId = JsonScan.stringValue(obj, listOf("id", "rollcallId", "rollcall_id")),
                    courseId = JsonScan.stringValue(obj, listOf("courseId", "course_id")),
                    rawFields = obj
                )
            }
            return if (items.isEmpty()) TronClassPollResult.NoActiveCheckIn
            else TronClassPollResult.FoundCheckIn(items, s)
        }
    }
}

/**
 * [v129] 畅课 org（学校租户）查询：`GET https://api-org.tronclass.com.cn/orgs?keywords=<校名>`，
 * 需带客户端固定头 X-LC-Id/X-LC-Key（APK 公开硬编码）。
 *
 * 字段结构 2026-10-15 实测（CHD）：`{"results":[{orgName, apiUrl, ntfUrl, identityConfig:{auth-server-url, realm}, area, keywords}]}`。
 * CHD 无 webUrl/casApi——API 域名 = apiUrl（course-online.chd.edu.cn），登录走 identity 服务
 * （浏览器跳转 auth-server-url，OAuth 风格 realm=chd），WebView 连接页加载 apiUrl 即可。
 */
object TronClassOrgResolver {

    data class OrgInfo(
        val orgName: String?,
        /** API 与 Web 共用域名（轮询 baseUrl 与 WebView 入口） */
        val apiUrl: String?,
        /** identity 服务地址（登录跳转用，仅展示；实际跳转由 Web 自己完成） */
        val authServerUrl: String?
    )

    fun resolve(
        client: OkHttpClient = OkHttpClient(),
        keywords: String = "长安大学"
    ): OrgInfo? {
        val request = Request.Builder()
            .url("https://api-org.tronclass.com.cn/orgs?keywords=$keywords")
            .header("X-LC-Id", "s0QbLDG5u6IrBSx9dC4yFiLr-gzGzoHsz")
            .header("X-LC-Key", "jEivEsoel0KxBdX4gDuO5Sak")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                parseOrg(resp.body?.string().orEmpty())
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 从 results（兼容 orgs）数组提取第一个含 apiUrl 的组织；结构不符返回 null */
    fun parseOrg(json: String): OrgInfo? {
        val arrBody = extractArrayBody(json, listOf("results", "orgs")) ?: return null
        for (obj in JsonScan.splitObjects(arrBody)) {
            val apiUrl = JsonScan.stringValue(obj, listOf("apiUrl", "api_url"))
            if (!apiUrl.isNullOrBlank()) {
                val identity = JsonScan.objectValue(obj, listOf("identityConfig", "identity_config"))
                    ?.let { JsonScan.stringValue(it, listOf("auth-server-url", "authServerUrl")) }
                return OrgInfo(
                    orgName = JsonScan.stringValue(obj, listOf("orgName", "org_name", "name")),
                    apiUrl = apiUrl,
                    authServerUrl = identity
                )
            }
        }
        return null
    }

    /** 找到键后的数组体（不含外层 []）；找不到返回 null */
    private fun extractArrayBody(json: String, keys: List<String>): String? {
        val idx = JsonScan.findKey(json, keys) ?: return null
        val open = json.indexOf('[', idx)
        if (open < 0) return null
        val close = JsonScan.matchBracket(json, open) ?: return null
        return json.substring(open + 1, close)
    }
}

/**
 * [v129] 手写 JSON 最小扫描工具（只做「键定位 / 数组切分 / 字符串值提取」，
 * 不构造树；转义处理覆盖 \ 与 "，供轮询解析与单测共用）。
 */
object JsonScan {

    /** 返回 `"key"` 冒号后值的起始下标；找不到返回 null */
    fun findKey(json: String, keys: List<String>): Int? {
        for (key in keys) {
            var from = 0
            while (true) {
                val idx = json.indexOf("\"$key\"", from)
                if (idx < 0) break
                // 必须形如 "key": 才算键（值内出现同词跳过）
                var i = idx + key.length + 2
                while (i < json.length && json[i].isWhitespace()) i++
                if (i < json.length && json[i] == ':') return i + 1
                from = idx + 1
            }
        }
        return null
    }

    /** openIdx 为 '[' 或 '{' 下标，返回配对闭合下标（考虑字符串内转义）；不配对返回 null */
    fun matchBracket(json: String, openIdx: Int): Int? {
        val open = json[openIdx]
        val close = if (open == '[') ']' else '}'
        var depth = 0
        var inString = false
        var i = openIdx
        while (i < json.length) {
            val c = json[i]
            if (inString) {
                when (c) {
                    '\\' -> i++ // 跳过转义字符
                    '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '[', '{' -> depth++
                    ']', '}' -> {
                        depth--
                        if (depth == 0) return if (c == close) i else null
                    }
                }
            }
            i++
        }
        return null
    }

    /** 数组原文（不含外层 []）内切分顶层对象块；空数组返回空列表 */
    fun splitObjects(arrayBody: String): List<String> {
        val result = mutableListOf<String>()
        var i = 0
        while (i < arrayBody.length) {
            if (arrayBody[i] == '{') {
                val close = matchBracket(arrayBody, i) ?: break
                result.add(arrayBody.substring(i, close + 1))
                i = close + 1
            } else {
                i++
            }
        }
        return result
    }

    /** 对象块内提取字符串/数字值（unquote + 常见反转义）；键不存在返回 null */
    fun stringValue(obj: String, keys: List<String>): String? {
        val valueStart = findKey(obj, keys) ?: return null
        var i = valueStart
        while (i < obj.length && obj[i].isWhitespace()) i++
        if (i >= obj.length) return null
        return when (obj[i]) {
            '"' -> {
                val sb = StringBuilder()
                i++
                while (i < obj.length) {
                    val c = obj[i]
                    if (c == '\\' && i + 1 < obj.length) {
                        when (val next = obj[i + 1]) {
                            'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                            else -> sb.append(next)
                        }
                        i += 2
                    } else if (c == '"') break
                    else { sb.append(c); i++ }
                }
                sb.toString()
            }
            else -> {
                val end = obj.indexOfFirst(i) { it == ',' || it == '}' || it == ']' }
                val raw = if (end < 0) obj.substring(i) else obj.substring(i, end)
                raw.trim().takeIf { it.isNotEmpty() && it != "null" }
            }
        }
    }

    /** 对象块内提取嵌套对象原文（含外层 {}）；键不存在或值非对象返回 null */
    fun objectValue(obj: String, keys: List<String>): String? {
        val valueStart = findKey(obj, keys) ?: return null
        var i = valueStart
        while (i < obj.length && obj[i].isWhitespace()) i++
        if (i >= obj.length || obj[i] != '{') return null
        val close = matchBracket(obj, i) ?: return null
        return obj.substring(i, close + 1)
    }

    private inline fun String.indexOfFirst(from: Int, predicate: (Char) -> Boolean): Int {
        for (j in from until length) if (predicate(this[j])) return j
        return -1
    }
}
