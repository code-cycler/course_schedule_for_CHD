package com.example.course_schedule_for_chd_v002.ui.screens.schedule

/**
 * [获取新学期] 学期推断工具（纯函数，独立于 ViewModel，便于单测与后续移植）
 *
 * 注：原内联在 ScheduleViewModel.kt 末尾（master 74930a0），merge dev 后提取至此，
 * 供 SemesterInferenceTest 引用；功能移植时 ViewModel 直接调用。
 */

/**
 * 学期本地串的单调编码：startY*2+(n-1)，用于学期先后比较。
 * 例："2025-2026-2" → 2025*2+1；"2026-2027-1" → 2026*2。自然覆盖"秋季→春季→秋季"序列。
 * 格式非 "YYYY-YYYY-N"（N∈1..2）时返回 null。
 */
fun semesterCode(semester: String): Int? {
    val parts = semester.split("-")
    if (parts.size != 3) return null
    val startY = parts[0].toIntOrNull() ?: return null
    val n = parts[2].toIntOrNull() ?: return null
    if (n !in 1..2) return null
    return startY * 2 + (n - 1)
}

/**
 * 根据日期推断"正在进行的学期"的本地串。
 *
 * [跨学期] 时间判断规则（2026-08-31 人拍板：按校历 2/15、8/15 分界，含 15 日当天；边界归旧学期）：
 * - 1/1 – 2/14  → 秋季收尾      (year-1)-year-1（去年 9 月开学的那个学期）
 * - 2/15 – 8/14 → 春季第二学期  (year-1)-year-2
 * - 8/15 – 12/31 → 秋季第一学期 year-(year+1)-1
 *
 * 例：2026/2/14 → "2025-2026-1"；2026/2/15 → "2025-2026-2"；
 *     2026/8/14 → "2025-2026-2"；2026/8/15 → "2026-2027-1"；2027/1/20 → "2026-2027-1"
 */
fun inferCurrentSemester(year: Int, month: Int, day: Int): String {
    val inFallTail = month == 1 || (month == 2 && day < 15)
    val inNewFall = month >= 8 && !(month == 8 && day < 15)
    return when {
        inFallTail -> "${year - 1}-$year-1"
        inNewFall -> "$year-${year + 1}-1"
        else -> "${year - 1}-$year-2"
    }
}

/**
 * [跨学期] 判断本地存储学期是否落后于日期推断学期（local < inferred）。
 * 任一编码解析失败返回 false（保守：格式异常不触发提醒）。
 */
fun isSemesterOutdated(localSemester: String, inferredSemester: String): Boolean {
    val local = semesterCode(localSemester) ?: return false
    val inferred = semesterCode(inferredSemester) ?: return false
    return local < inferred
}

/**
 * 由当前学期算出 4 个候选学期本地串：[前2, 前1, 当前, 往后1]。
 * 用 startY*2+(n-1) 单调编码后取 base-{2,1,0} 和 base+1 反解，自然覆盖"秋季→春季→秋季"序列。
 * 例：current="2025-2026-2" → ["2024-2025-2","2025-2026-1","2025-2026-2","2026-2027-1"]
 */
fun candidateSemesters(current: String): List<String> {
    val base = semesterCode(current) ?: return listOf(current)
    fun decode(code: Int): String {
        val sy = code / 2
        val num = (code % 2) + 1
        return "$sy-${sy + 1}-$num"
    }
    return listOf(base - 2, base - 1, base, base + 1).map { decode(it) }
}
