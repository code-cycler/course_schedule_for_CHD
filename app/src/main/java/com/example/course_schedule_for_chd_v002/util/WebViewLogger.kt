package com.example.course_schedule_for_chd_v002.util

/**
 * WebView 日志工具类 (v90)
 *
 * 提供 WebView 调试日志的统一管理
 * v49: 新增资源加载监控日志
 * v52: 新增解析流程日志（统一输出到 CHD_WebView 标签）
 * v54: 新增页面类型检测日志（基于内容的检测）
 * v88: 新增单双周识别专用日志
 * v90: 新增课程合并日志（处理多教学班冲突）
 */
object WebViewLogger {

    private const val TAG = "CHD_WebView"
    private const val WEEK_TYPE_TAG = "CHD_WeekType"  // [v88] 单双周识别专用标签

    /**
     * 记录页面加载状态
     */
    fun logPageLoad(url: String?, isLoading: Boolean) {
        val status = if (isLoading) "开始加载" else "加载完成"
        android.util.Log.d(TAG, "[页面] $status: $url")
    }

    /**
     * 记录页面类型检测结果
     */
    fun logPageType(pageInfo: String) {
        android.util.Log.i(TAG, "[页面类型] $pageInfo")
    }

    /**
     * 记录 JavaScript 注入状态
     */
    fun logInjection(success: Boolean, message: String) {
        if (success) {
            android.util.Log.i(TAG, "[注入] [OK] $message")
        } else {
            android.util.Log.e(TAG, "[注入] [X] $message")
        }
    }

    /**
     * 记录数据就绪检测
     */
    fun logDataCheck(attempt: Int, isReady: Boolean, maxAttempts: Int) {
        val status = if (isReady) "就绪" else "等待中"
        android.util.Log.d(TAG, "[数据检测] 第${attempt}次/$maxAttempts: $status")
    }

    /**
     * 记录 JavaScript 错误
     */
    fun logJsError(message: String, source: String?, line: Int) {
        android.util.Log.e(TAG, "[JS错误] $message (at ${source ?: "unknown"}:${line})")
    }

    /**
     * 记录 JavaScript 日志
     */
    fun logJsLog(message: String) {
        android.util.Log.d(TAG, "[JS] $message")
    }

    /**
     * 记录导航事件
     */
    fun logNavigation(action: String, url: String?) {
        android.util.Log.i(TAG, "[导航] $action -> $url")
    }

    /**
     * 记录状态变化
     */
    fun logState(component: String, state: String, details: String? = null) {
        val message = if (details != null) {
            "[$component] $state = $details"
        } else {
            "[$component] $state"
        }
        android.util.Log.d(TAG, message)
    }

    /**
     * 记录警告
     */
    fun logWarn(component: String, message: String) {
        android.util.Log.w(TAG, "[$component] [!] $message")
    }

    /**
     * 记录错误
     */
    fun logError(component: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            android.util.Log.e(TAG, "[$component] [X] $message", throwable)
        } else {
            android.util.Log.e(TAG, "[$component] [X] $message")
        }
    }

    /**
     * 记录成功
     */
    fun logSuccess(component: String, message: String) {
        android.util.Log.i(TAG, "[$component] [OK] $message")
    }

    /**
     * 记录调试信息
     */
    fun logDebug(component: String, message: String) {
        android.util.Log.d(TAG, "[$component] $message")
    }

    /**
     * 记录 HTML 获取
     */
    fun logHtmlFetch(length: Int, url: String) {
        android.util.Log.i(TAG, "[HTML] 获取成功: 长度=$length, URL=${url.take(80)}...")
    }

    /**
     * 记录 Cookie 同步
     */
    fun logCookieSync(cookieCount: Int, domain: String) {
        android.util.Log.d(TAG, "[Cookie] 同步 $cookieCount 个 Cookie: $domain")
    }

    // ==================== v49 资源加载监控 ====================

    /**
     * 记录资源加载
     */
    fun logResourceLoad(url: String?) {
        // 只记录关键资源（JS、CSS），过滤图片等
        if (url != null && (url.contains(".js") || url.contains(".css") || url.contains("/static/"))) {
            android.util.Log.d(TAG, "[资源] 加载: ${url.take(100)}")
        }
    }

    /**
     * 记录资源加载错误
     */
    fun logResourceError(url: String?, error: String?) {
        android.util.Log.e(TAG, "[资源] [X] 失败: ${url?.take(80)} - $error")
    }

    /**
     * 记录资源请求（详细模式）
     */
    fun logResourceRequest(url: String?) {
        // 仅在需要详细调试时启用
        // android.util.Log.v(TAG, "[请求] $url")
    }

    /**
     * 记录预注入状态
     */
    fun logPreInjection(success: Boolean) {
        if (success) {
            android.util.Log.i(TAG, "[预注入] [OK] jQuery/beangle 模拟注入成功")
        } else {
            android.util.Log.e(TAG, "[预注入] [X] 注入失败")
        }
    }

    // ==================== v50 HTML 拦截注入 ====================

    /**
     * 记录 HTML 拦截注入状态
     */
    fun logHtmlInterception(url: String, success: Boolean, htmlLength: Int = 0) {
        if (success) {
            android.util.Log.i(TAG, "[拦截] [OK] HTML 注入成功: ${url.take(80)} (长度: $htmlLength)")
        } else {
            android.util.Log.e(TAG, "[拦截] [X] HTML 注入失败: ${url.take(80)}")
        }
    }

    /**
     * 记录 HTML 拦截错误
     */
    fun logHtmlInterceptionError(url: String, error: String) {
        android.util.Log.e(TAG, "[拦截] [X] 错误: ${url.take(60)} - $error")
    }

    /**
     * 记录请求拦截
     */
    fun logRequestIntercept(url: String, isMainFrame: Boolean) {
        android.util.Log.d(TAG, "[请求拦截] ${if (isMainFrame) "主框架" else "子资源"}: ${url.take(80)}")
    }

    // ==================== v52 解析流程日志 ====================

    /**
     * 记录解析开始
     */
    fun logParseStart(htmlLength: Int) {
        android.util.Log.i(TAG, "[解析] 开始解析 HTML，长度: $htmlLength")
    }

    /**
     * 记录解析结果
     */
    fun logParseResult(courseCount: Int, method: String) {
        if (courseCount > 0) {
            android.util.Log.i(TAG, "[解析] [OK] 解析成功: $courseCount 门课程 (方法: $method)")
        } else {
            android.util.Log.e(TAG, "[解析] [X] 解析失败: 未找到课程数据 (方法: $method)")
        }
    }

    /**
     * 记录解析详情
     */
    fun logParseDetail(message: String) {
        android.util.Log.d(TAG, "[解析] $message")
    }

    /**
     * 记录数据库保存
     */
    fun logDatabaseSave(count: Int, success: Boolean) {
        if (success) {
            android.util.Log.i(TAG, "[数据库] [OK] 已保存 $count 门课程")
        } else {
            android.util.Log.e(TAG, "[数据库] [X] 保存失败")
        }
    }

    /**
     * 记录导航事件发射
     */
    fun logNavigationEventEmit(success: Boolean) {
        if (success) {
            android.util.Log.i(TAG, "[导航] [OK] 发射导航事件")
        } else {
            android.util.Log.e(TAG, "[导航] [X] 导航事件发射失败")
        }
    }

    // ==================== v54 页面类型检测日志 ====================

    /**
     * [v54] 记录页面类型检测结果
     */
    fun logPageTypeDetection(url: String, isLoginPage: Boolean, isLoggedIn: Boolean, details: String = "") {
        val status = when {
            isLoginPage -> "登录页面"
            isLoggedIn -> "已登录"
            else -> "未登录/未知"
        }
        if (details.isNotEmpty()) {
            android.util.Log.i(TAG, "[页面检测] [v54] $status - ${url.take(60)}... ($details)")
        } else {
            android.util.Log.i(TAG, "[页面检测] [v54] $status - ${url.take(60)}...")
        }
    }

    /**
     * [v54] 记录页面检测详情
     */
    fun logPageDetectionDetail(message: String) {
        android.util.Log.d(TAG, "[页面检测] $message")
    }

    // ==================== v88 单双周识别专用日志 ====================

    /**
     * [v88] 记录单双周识别过程
     */
    fun logWeekTypeParse(courseName: String, bitmap: String, activeWeeks: List<Int>, weekType: String) {
        android.util.Log.i(WEEK_TYPE_TAG, "=== 单双周识别 ===")
        android.util.Log.i(WEEK_TYPE_TAG, "课程: $courseName")
        android.util.Log.i(WEEK_TYPE_TAG, "位图: $bitmap")
        android.util.Log.i(WEEK_TYPE_TAG, "活跃周: $activeWeeks")
        android.util.Log.i(WEEK_TYPE_TAG, "识别结果: $weekType")
    }

    /**
     * [v88] 记录周数位图详情
     */
    fun logWeekBitmapDetail(bitmap: String) {
        if (bitmap.length >= 53) {
            // 分段显示位图，便于阅读
            val chunk1 = bitmap.substring(0, 18)   // 第1-18周
            val chunk2 = bitmap.substring(18, 36)  // 第19-36周
            val chunk3 = bitmap.substring(36, 53)  // 第37-53周
            android.util.Log.d(WEEK_TYPE_TAG, "位图分段:")
            android.util.Log.d(WEEK_TYPE_TAG, "  1-18周:  $chunk1")
            android.util.Log.d(WEEK_TYPE_TAG, "  19-36周: $chunk2")
            android.util.Log.d(WEEK_TYPE_TAG, "  37-53周: $chunk3")
        }
    }

    /**
     * [v88] 记录单双周判断详情
     */
    fun logWeekTypeCheck(week: Int, isOdd: Boolean) {
        val type = if (isOdd) "单周" else "双周"
        android.util.Log.v(WEEK_TYPE_TAG, "  周$week -> $type")
    }

    /**
     * [v88] 记录最终的单双周识别结果
     */
    fun logWeekTypeResult(courseName: String, startWeek: Int, endWeek: Int, weekType: String, remark: String) {
        android.util.Log.i(WEEK_TYPE_TAG, "[最终结果] $courseName: 周$startWeek-$endWeek, $weekType, remark=$remark")
    }

    // ==================== v90 课程合并日志 ====================

    /**
     * [v90] 记录课程合并信息
     *
     * 当同一课程在同一时间位置有多条不同周类型的记录时（如多个教学班），
     * 会合并这些记录并记录合并日志
     *
     * @param courseName 课程名称
     * @param position 时间位置（如"周2第5-6节"）
     * @param originalCount 原始记录数量
     * @param mergedWeeks 合并后的活跃周列表
     * @param mergedRooms 合并后的教室列表
     */
    fun logCourseMerge(courseName: String, position: String, originalCount: Int, mergedWeeks: List<Int>, mergedRooms: List<String>) {
        android.util.Log.i(WEEK_TYPE_TAG, "========== 课程合并 ==========")
        android.util.Log.i(WEEK_TYPE_TAG, "[合并] $courseName @ $position")
        android.util.Log.i(WEEK_TYPE_TAG, "  原始记录数: $originalCount")
        android.util.Log.i(WEEK_TYPE_TAG, "  合并后周次: $mergedWeeks")
        android.util.Log.i(WEEK_TYPE_TAG, "  合并后教室: $mergedRooms")
        android.util.Log.i(WEEK_TYPE_TAG, "===============================")
    }
}
