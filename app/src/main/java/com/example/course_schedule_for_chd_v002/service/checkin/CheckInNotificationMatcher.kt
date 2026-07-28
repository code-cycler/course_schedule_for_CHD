package com.example.course_schedule_for_chd_v002.service.checkin

/**
 * [v114] 畅课签到通知识别（纯函数，可单测）
 *
 * 判定一条系统通知是否属于「畅课签到通知」：来源包名为畅课（TronClass），
 * 且标题/正文包含任一签到关键字。
 *
 * - 关键字可配置（存 DataStore，设置页可编辑），默认 [DEFAULT_KEYWORDS]。
 * - debug 构建可额外放行 [SHELL_PACKAGE]（adb `cmd notification post` 的来源包），
 *   便于在没有真实签到通知时端到端测试整条链路。
 *
 * 独立成纯对象（不继承任何 Android 类），便于 JVM 单测直接调用。
 */
object CheckInNotificationMatcher {

    /** 畅课（TronClass）包名 */
    const val TRONCLASS_PACKAGE = "com.wisdomgarden.trpc"

    /** adb 测试通知的来源包；仅 debug 构建放行 */
    const val SHELL_PACKAGE = "com.android.shell"

    /** 默认签到关键字 */
    val DEFAULT_KEYWORDS = listOf("签到", "考勤", "点名")

    /** 默认关键字的逗号分隔文本（设置页默认值，与 UserPreferences 默认保持一致） */
    const val DEFAULT_KEYWORDS_TEXT = "签到,考勤,点名"

    /** 包名是否为目标：畅课包，或（debug 放行时）adb shell 包 */
    fun isTargetPackage(packageName: String?, allowShell: Boolean): Boolean {
        if (packageName == TRONCLASS_PACKAGE) return true
        return allowShell && packageName == SHELL_PACKAGE
    }

    /** 内容是否包含任一关键字（空白关键字忽略） */
    fun matchesKeywords(content: String, keywords: List<String>): Boolean =
        keywords.any { it.isNotBlank() && content.contains(it) }

    /**
     * 综合判定一条通知是否为签到通知。
     * @param packageName 通知来源包名
     * @param content 标题 + 正文拼接后的文本
     * @param keywords 关键字列表（默认 [DEFAULT_KEYWORDS]）
     * @param allowShell 是否放行 adb shell 包（debug 测试用）
     */
    fun isCheckInNotification(
        packageName: String?,
        content: String,
        keywords: List<String> = DEFAULT_KEYWORDS,
        allowShell: Boolean = false
    ): Boolean = isTargetPackage(packageName, allowShell) && matchesKeywords(content, keywords)
}
