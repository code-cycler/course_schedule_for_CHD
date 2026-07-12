package com.example.course_schedule_for_chd_v002.ui.screens.schedule

/**
 * [获取新学期] 学期推断工具（纯函数，独立于 ViewModel，便于单测与后续移植）
 *
 * 注：原内联在 ScheduleViewModel.kt 末尾（master 74930a0），merge dev 后提取至此，
 * 供 SemesterInferenceTest 引用；功能移植时 ViewModel 直接调用。
 */

/**
 * 根据日期推断"正在进行的学期"的本地串。
 *
 * 时间判断规则（严格按「8 月往后=下半学期=秋季第一学期」）：
 * - 2–7月  → 春季第二学期  (year-1)-year-2
 * - 8–12月 → 秋季第一学期  year-(year+1)-1（8 月起切新学年秋季）
 * - 1月    → 秋季收尾      (year-1)-year-1（去年 9 月开学的那个学期）
 *
 * 例：2026/7 → "2025-2026-2"；2026/8 → "2026-2027-1"；2026/12 → "2026-2027-1"；2027/1 → "2026-2027-1"
 */
fun inferCurrentSemester(year: Int, month: Int): String = when (month) {
    in 2..7  -> "${year - 1}-$year-2"
    in 8..12 -> "$year-${year + 1}-1"
    1        -> "${year - 1}-$year-1"
    else     -> "${year - 1}-$year-2"
}

/**
 * 由当前学期算出 4 个候选学期本地串：[前2, 前1, 当前, 往后1]。
 * 用 startY*2+(n-1) 单调编码后取 base-{2,1,0} 和 base+1 反解，自然覆盖"秋季→春季→秋季"序列。
 * 例：current="2025-2026-2" → ["2024-2025-2","2025-2026-1","2025-2026-2","2026-2027-1"]
 */
fun candidateSemesters(current: String): List<String> {
    val parts = current.split("-")
    if (parts.size != 3) return listOf(current)
    val startY = parts[0].toIntOrNull() ?: return listOf(current)
    val n = parts[2].toIntOrNull() ?: return listOf(current)
    fun encode(sy: Int, num: Int) = sy * 2 + (num - 1)
    fun decode(code: Int): String {
        val sy = code / 2
        val num = (code % 2) + 1
        return "$sy-${sy + 1}-$num"
    }
    val base = encode(startY, n)
    return listOf(base - 2, base - 1, base, base + 1).map { decode(it) }
}
