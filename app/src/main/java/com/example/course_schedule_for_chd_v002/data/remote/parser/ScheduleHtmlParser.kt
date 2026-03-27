package com.example.course_schedule_for_chd_v002.data.remote.parser

import com.example.course_schedule_for_chd_v002.data.local.database.entity.CourseEntity
import com.example.course_schedule_for_chd_v002.domain.model.CourseType
import com.example.course_schedule_for_chd_v002.util.AppLogger
import com.example.course_schedule_for_chd_v002.util.WebViewLogger
import org.jsoup.Jsoup
import org.json.JSONObject

/**
 * 周数类型枚举
 */
enum class WeekType {
    ALL,    // 每周（连续周）
    ODD,    // 单周
    EVEN;   // 双周

    fun toDisplayString(): String = when (this) {
        ALL -> ""
        ODD -> "单"
        EVEN -> "双"
    }
}

/**
 * 周数解析结果
 */
data class WeekParseResult(
    val startWeek: Int,
    val endWeek: Int,
    val weekType: WeekType
)

/**
 * 课程表 HTML 解析器 (v91)
 *
 * 解析长安大学教务系统课表页面的 JavaScript 数据
 *
 * v59: 修复合并逻辑 - 按 课程名+星期 分组，正确合并连续节次
 *      解决连续节次被分成独立卡片的问题
 *
 * v87: 新增单双周识别功能
 *      通过分析周数位图识别单周/双周模式
 *
 * v90: 新增同一位置多班合并功能
 *      当同一课程在同一时间位置有多条不同周类型的记录时（如C语言有多个教学班）
 *      合并所有活跃周，重新判断周类型，合并教室信息
 *
 * v91: 修复多教学班课程缺失问题
 *      修改 parseActivitiesJson 的分组 key：使用 教室+周次位图 替代 周次范围
 *      确保同一教学班的连续节次被正确合并，不同教学班分开处理
 *
 * HTML 结构：
 * ```javascript
 * var teachers = [{id:3613,name:"朱依水",lab:false}];
 * var actTeachers = [{id:3613,name:"朱依水",lab:false}];
 * var actTeacherId = [];
 * var actTeacherName = [];
 * for (var i = 0; i < actTeachers.length; i++) {
 *     actTeacherId.push(actTeachers[i].id);
 *     actTeacherName.push(actTeachers[i].name);
 * }
 *
 * var courseName = "知识表征与推理(双语)(24ZY1816.01)";
 *
 * activity = new TaskActivity(
 *     actTeacherId.join(','),           // 参数0: 教师ID
 *     actTeacherName.join(','),         // 参数1: 教师名
 *     "59153(24ZY1816.01)",            // 参数2: 课程ID
 *     courseName,                       // 参数3: 课程名变量
 *     "24ZY1816.01",                    // 参数4: 课程代码
 *     "633",                            // 参数5: 教室ID
 *     "*WH2201",                        // 参数6: 教室名 (*普通 #实验室)
 *     "00000000111111111100000000000000000000000000000000000",  // 参数7: 周数位图(53字符)
 *     null,                             // 参数8
 *     "",                               // 参数9
 *     assistantName,                    // 参数10: 助教
 *     ""                                // 参数11
 * );
 *
 * index = 3*unitCount+4;  // dayOfWeek=3(周四), nodeIndex=4(第5节)
 * index = 3*unitCount+5;  // dayOfWeek=3(周四), nodeIndex=5(第6节)
 * table0.activities[index][table0.activities[index].length]=activity;
 * ```
 *
 * index 计算公式: index = dayOfWeek * unitCount + nodeIndex
 * - unitCount = 11 (每天11节课)
 * - dayOfWeek: 0=周一, 1=周二, ..., 6=周日
 * - nodeIndex: 0=第1节, 1=第2节, ..., 10=第11节
 *
 * 周数位图: 53个字符，每个字符代表一周，'1'表示有课，'0'表示无课
 */
class ScheduleHtmlParser {

    companion object {
        private const val TAG = "ScheduleHtmlParser"
        private const val UNIT_COUNT = 11  // 每天节次数
    }

    /**
     * 解析课程表 HTML
     * @param html 课程表页面 HTML
     * @param semester 学期标识
     * @return 解析出的课程实体列表
     */
    fun parse(html: String, semester: String): List<CourseEntity> {
        WebViewLogger.logParseStart(html.length)

        val courses = mutableListOf<CourseEntity>()

        // 方法1：解析 TaskActivity JavaScript 数据
        val jsCourses = parseTaskActivityJs(html, semester)
        if (jsCourses.isNotEmpty()) {
            courses.addAll(jsCourses)
            WebViewLogger.logParseResult(jsCourses.size, "TaskActivity")
        } else {
            WebViewLogger.logParseDetail("TaskActivity 解析失败，尝试解析 infoTitle 单元格")
            // 方法2：解析 JavaScript 渲染后的 td.infoTitle 单元格
            val doc = Jsoup.parse(html)
            val infoTitleCells = doc.select("td.infoTitle")
            WebViewLogger.logParseDetail("找到 ${infoTitleCells.size} 个 infoTitle 单元格")

            for (cell in infoTitleCells) {
                val cellCourses = parseInfoTitleCell(cell, semester)
                courses.addAll(cellCourses)
            }
            WebViewLogger.logParseResult(courses.size, "infoTitle")
        }

        // [v90] 合并同一位置的同名课程记录（处理多教学班情况）
        val mergedCourses = mergeSamePositionCourses(courses)

        // 去重：同一课程在同一时间只保留一个
        // [v89] 扩展去重 key，包含 endNode 和 endWeek，避免不同时长的课程被错误去重
        val distinctCourses = mergedCourses.distinctBy {
            "${it.name}-${it.dayOfWeek}-${it.startNode}-${it.endNode}-${it.startWeek}-${it.endWeek}"
        }

        WebViewLogger.logParseDetail("原始: ${courses.size}, 合并后: ${mergedCourses.size}, 去重后: ${distinctCourses.size}")
        return distinctCourses
    }

    /**
     * 解析 TaskActivity JavaScript 数据
     */
    private fun parseTaskActivityJs(html: String, semester: String): List<CourseEntity> {
        val courses = mutableListOf<CourseEntity>()
        WebViewLogger.logParseDetail("开始解析 TaskActivity JavaScript 数据")

        // 查找所有 courseName 定义
        val courseNamePattern = """var\s+courseName\s*=\s*"([^"]+)"""".toRegex()
        val courseNames = courseNamePattern.findAll(html).map { it.groupValues[1] }.toList()
        WebViewLogger.logParseDetail("找到 ${courseNames.size} 个 courseName 定义")

        if (courseNames.isEmpty()) {
            WebViewLogger.logParseDetail("[WARN] 未找到 courseName 定义，可能 HTML 结构不同")
            // 打印 HTML 中包含 "courseName" 的部分用于调试
            val courseNameIndex = html.indexOf("courseName")
            if (courseNameIndex >= 0) {
                val start = maxOf(0, courseNameIndex - 50)
                val end = minOf(html.length, courseNameIndex + 100)
                WebViewLogger.logParseDetail("courseName 附近内容: ${html.substring(start, end)}")
            }
            return emptyList()
        }

        // 按课程块分割
        // 每个块以 "var teachers" 开始，包含教师、课程名、TaskActivity、index
        val courseBlockPattern = """var\s+teachers\s*=\s*\[([^\]]*)\][\s\S]*?var\s+courseName\s*=\s*"([^"]+)"([\s\S]*?)(?=var\s+teachers|$)""".toRegex()

        val courseBlocks = courseBlockPattern.findAll(html).toList()
        WebViewLogger.logParseDetail("找到 ${courseBlocks.size} 个课程块")

        for ((blockIndex, block) in courseBlocks.withIndex()) {
            try {
                val teachersJson = block.groupValues[1]  // 教师JSON
                val courseName = block.groupValues[2]    // 课程名称
                val activityBlock = block.groupValues[3] // 包含 TaskActivity 和 index 的部分

                WebViewLogger.logParseDetail("[$blockIndex] 解析课程块: $courseName")

                // 解析教师姓名
                val teacherNames = extractTeacherNames(teachersJson)

                // [v26] 提取教室名称和周数位图
                // 格式: "...,"教室名","周数位图","..."
                // 教室名可以以 * 或 # 开头（*普通教室 #实验室）
                val roomAndWeeksPattern = ""","([*#]?[^",]*)","([01]{53})",""".toRegex()
                val roomWeeksMatch = roomAndWeeksPattern.find(activityBlock)

                // [v26] 使用 var 以便备用模式可以更新
                var location = roomWeeksMatch?.groupValues?.get(1) ?: ""
                var weeksBitmap = roomWeeksMatch?.groupValues?.get(2) ?: ""

                if (location.isEmpty() || weeksBitmap.isEmpty()) {
                    WebViewLogger.logParseDetail("[$blockIndex] 未找到教室或周数位图，尝试其他模式")
                    // [v26] 尝试另一种模式：直接在 activity 行中查找
                    val altPattern = """new\s+TaskActivity\([^)]+,\s*"([*#]?[^",]*)"\s*,\s*"([01]{53})"""".toRegex()
                    val altMatch = altPattern.find(activityBlock)
                    if (altMatch != null) {
                        location = altMatch.groupValues[1]
                        weeksBitmap = altMatch.groupValues[2]
                        WebViewLogger.logParseDetail("[$blockIndex] 使用备用模式找到: location=$location, bitmap长度=${weeksBitmap.length}")
                    }
                }

                WebViewLogger.logParseDetail("[$blockIndex] 教师: $teacherNames, 教室: $location, 位图长度: ${weeksBitmap.length}")

                // 提取 index 计算
                // 格式: index = X*unitCount+Y
                val indexPattern = """index\s*=\s*(\d+)\s*\*\s*unitCount\s*\+\s*(\d+)""".toRegex()
                val indexMatches = indexPattern.findAll(activityBlock).toList()

                WebViewLogger.logParseDetail("[$blockIndex] 找到 ${indexMatches.size} 个 index 定义")

                // 解析周数范围和类型
                val weekResult = parseWeeksBitmap(weeksBitmap)
                val startWeek = weekResult.startWeek
                val endWeek = weekResult.endWeek
                val weekTypeStr = weekResult.weekType.toDisplayString()

                // [v25] 收集所有 index 并按星期分组
                val indexList = indexMatches.map { match ->
                    val dayOfWeekMultiplier = match.groupValues[1].toIntOrNull() ?: 0
                    val nodeOffset = match.groupValues[2].toIntOrNull() ?: 0
                    Pair(dayOfWeekMultiplier + 1, nodeOffset + 1)  // (dayOfWeek, nodeIndex)
                }

                // 按星期分组
                val groupedByDay = indexList.groupBy { it.first }

                // [v25] 对每个星期的节次进行合并（连续节次合并为一门课）
                for ((dayOfWeek, nodes) in groupedByDay) {
                    val sortedNodes = nodes.map { it.second }.sorted().distinct()

                    if (sortedNodes.isEmpty()) continue

                    // 找出连续的节次区间
                    val ranges = mutableListOf<Pair<Int, Int>>()
                    var rangeStart = sortedNodes.first()
                    var rangeEnd = rangeStart

                    for (i in 1 until sortedNodes.size) {
                        if (sortedNodes[i] == rangeEnd + 1) {
                            // 连续节次，扩展区间
                            rangeEnd = sortedNodes[i]
                        } else {
                            // 不连续，保存当前区间，开始新区间
                            ranges.add(Pair(rangeStart, rangeEnd))
                            rangeStart = sortedNodes[i]
                            rangeEnd = rangeStart
                        }
                    }
                    ranges.add(Pair(rangeStart, rangeEnd))

                    // 为每个节次区间创建一门课程
                    for ((startNode, endNode) in ranges) {
                        if (courseName.isNotEmpty() && startWeek <= endWeek) {
                            // [v87] 构建备注信息，包含单双周标识
                            val remarkParts = mutableListOf<String>()
                            if (weekTypeStr.isNotEmpty()) {
                                remarkParts.add(weekTypeStr)
                            }
                            if (weeksBitmap.isNotEmpty()) {
                                remarkParts.add("weeksBitmap:$weeksBitmap")
                            }
                            val remark = remarkParts.joinToString(";")

                            val course = CourseEntity(
                                name = courseName,
                                teacher = teacherNames,
                                location = location,
                                dayOfWeek = dayOfWeek,
                                startWeek = startWeek,
                                endWeek = endWeek,
                                startNode = startNode,
                                endNode = endNode,  // [v25] 使用合并后的结束节次
                                courseType = determineCourseType(courseName),
                                credit = 0.0,
                                remark = remark,
                                semester = semester
                            )
                            courses.add(course)
                            val weekTypeLog = if (weekTypeStr.isNotEmpty()) " [$weekTypeStr]" else ""
                            WebViewLogger.logParseDetail("[$blockIndex] 添加课程: ${course.name}, 周:${course.startWeek}-${course.endWeek}$weekTypeLog, 周${course.dayOfWeek} 第${course.startNode}-${course.endNode}节")
                            // [v88] 使用专用日志方法记录单双周识别结果
                            WebViewLogger.logWeekTypeResult(courseName, startWeek, endWeek, weekTypeLog.trim(), remark)
                        }
                    }
                }
            } catch (e: Exception) {
                WebViewLogger.logParseDetail("[ERROR][$blockIndex] 解析课程块失败: ${e.message}")
            }
        }

        WebViewLogger.logParseDetail("TaskActivity 解析完成，共 ${courses.size} 门课程")
        return courses
    }

    /**
     * 从 JSON 字符串提取教师姓名
     * 输入: {id:3613,name:"朱依水",lab:false},{id:1234,name:"张三",lab:false}
     * 输出: "朱依水,张三"
     */
    private fun extractTeacherNames(json: String): String {
        if (json.isBlank()) return ""

        val namePattern = """name\s*:\s*"([^"]+)"""".toRegex()
        val names = namePattern.findAll(json).map { it.groupValues[1] }.toList()
        return names.joinToString(",")
    }

    /**
     * [v87] 解析周数位图（支持单双周识别）
     * [v88] 添加详细 debug log
     * @param bitmap 53个字符的位图，'1'表示有课，'0'表示无课
     * @return WeekParseResult 包含起始周、结束周和周类型（单周/双周/每周）
     */
    private fun parseWeeksBitmap(bitmap: String): WeekParseResult {
        // [v88] 打印原始位图（使用 Log.i 确保可见）
        AppLogger.i("CHD_WeekType", "========== 周数解析开始 ==========")
        AppLogger.i("CHD_WeekType", "原始位图: $bitmap")
        AppLogger.i("CHD_WeekType", "位图长度: ${bitmap.length}")
        // [v88] 使用专用方法显示分段位图
        WebViewLogger.logWeekBitmapDetail(bitmap)

        if (bitmap.isEmpty()) {
            AppLogger.w("CHD_WeekType", "[WARN] 位图为空，返回默认值")
            return WeekParseResult(1, 16, WeekType.ALL)
        }
        if (bitmap.length < 53) {
            AppLogger.w("CHD_WeekType", "[WARN] 周数位图长度不足: ${bitmap.length}")
            return WeekParseResult(1, 16, WeekType.ALL)
        }

        val activeWeeks = mutableListOf<Int>()

        for ((index, char) in bitmap.withIndex()) {
            if (char == '1') {
                // [v36] 原逻辑: week = index + 1
                // 但学校系统位图有+1偏移，需要减1修正
                val week = index + 1
                activeWeeks.add(week)
                // [v88] 打印每个活跃周的提取过程
                AppLogger.d("CHD_WeekType", "  index=$index, char='$char' -> week=$week")
            }
        }

        // [v88] 打印活跃周列表（使用 Log.i 强制显示）
        if (activeWeeks.isNotEmpty()) {
            val weekStr = activeWeeks.joinToString(",")
            AppLogger.i("CHD_WeekType", "活跃周列表(index+1): [$weekStr]")
            AppLogger.i("CHD_WeekType", "活跃周数量: ${activeWeeks.size}")
        }

        if (activeWeeks.isEmpty()) {
            AppLogger.w("CHD_WeekType", "[WARN] 周数位图全为0")
            return WeekParseResult(1, 16, WeekType.ALL)
        }

        val startWeek = activeWeeks.minOrNull()!!
        val endWeek = activeWeeks.maxOrNull()!!
        AppLogger.i("CHD_WeekType", "原始范围(修正前): $startWeek-$endWeek")

        // [v87] 识别单双周模式
        val weekType = determineWeekType(activeWeeks)

        // [v96] 恢复偏移修正：学校系统位图有+1偏移
        // 位图结构：bitmap[0]=第0周(预备周), bitmap[1]=第1周, ...
        // 解析时 week = index + 1，所以 bitmap[1] -> week=2
        // 需要减1修正为实际的第1周
        val correctedStartWeek = startWeek - 1
        val correctedEndWeek = endWeek - 1

        val typeStr = when (weekType) {
            WeekType.ODD -> "单周"
            WeekType.EVEN -> "双周"
            WeekType.ALL -> "每周"
        }
        AppLogger.i("CHD_WeekType", "最终结果: 修正后=$correctedStartWeek-$correctedEndWeek, 类型=$typeStr")
        AppLogger.i("CHD_WeekType", "========== 周数解析结束 ==========")

        return WeekParseResult(correctedStartWeek, correctedEndWeek, weekType)
    }

    /**
     * [v87] 判断周数类型（单周/双周/每周）
     * [v88] 添加详细 debug log
     * @param activeWeeks 有课的周数列表
     * @return WeekType 单周/双周/每周
     */
    private fun determineWeekType(activeWeeks: List<Int>): WeekType {
        WebViewLogger.logParseDetail("[单双周] ===== 开始判断 =====")

        if (activeWeeks.isEmpty()) {
            WebViewLogger.logParseDetail("[单双周] activeWeeks 为空，返回 ALL")
            return WeekType.ALL
        }

        // [v88] 打印活跃周列表（强制打印，使用 Log.i 确保可见）
        AppLogger.i("CHD_WeekType", "[单双周] 活跃周列表: $activeWeeks (共 ${activeWeeks.size} 周)")

        // 检查是否全是奇数周（单周）
        val allOdd = activeWeeks.all { it % 2 == 1 }
        // 检查是否全是偶数周（双周）
        val allEven = activeWeeks.all { it % 2 == 0 }

        // [v88] 逐个打印判断过程
        activeWeeks.forEach { week ->
            val isOdd = week % 2 == 1
            AppLogger.d("CHD_WeekType", "[单双周]   周$week % 2 = ${week % 2} -> ${if (isOdd) "奇数(单)" else "偶数(双)"}")
        }

        // [v88] 打印判断结果（使用 Log.i 确保可见）
        AppLogger.i("CHD_WeekType", "[单双周] 判断: allOdd=$allOdd, allEven=$allEven")

        val result = when {
            allOdd -> WeekType.ODD
            allEven -> WeekType.EVEN
            else -> WeekType.ALL
        }

        val resultStr = when (result) {
            WeekType.ODD -> "单周"
            WeekType.EVEN -> "双周"
            WeekType.ALL -> "每周"
        }
        AppLogger.i("CHD_WeekType", "[单双周] 最终结果: $resultStr")
        WebViewLogger.logParseDetail("[单双周] ===== 判断结束: $resultStr =====")

        return result
    }

    /**
     * [v90] 合并同一位置的同名课程记录
     *
     * 处理场景：同一课程在同一时间位置有多条不同周类型的记录
     * 例如：C语言程序设计在周2第5节有"双周机房"和"每周普通教室"两条记录
     * 这是因为课程有多个教学班，教务系统将所有教学班数据合并显示
     *
     * @param courses 原始课程列表
     * @return 合并后的课程列表
     */
    private fun mergeSamePositionCourses(courses: List<CourseEntity>): List<CourseEntity> {
        if (courses.isEmpty()) return courses

        // 按 课程名+星期+开始节次+结束节次 分组
        // 这样可以识别同一位置的多条记录
        val groupedCourses = courses.groupBy {
            Triple(it.name, it.dayOfWeek, Pair(it.startNode, it.endNode))
        }

        val mergedList = mutableListOf<CourseEntity>()
        var mergeCount = 0

        for ((key, group) in groupedCourses) {
            if (group.size == 1) {
                // 只有一条记录，直接添加
                mergedList.add(group.first())
            } else {
                // 多条记录，需要合并
                val merged = mergeCourseGroup(group)
                mergedList.add(merged)
                mergeCount++

                // 记录合并日志
                val (courseName, dayOfWeek, nodes) = key
                val (startNode, endNode) = nodes
                val position = "周${dayOfWeek}第${startNode}-${endNode}节"

                // 提取合并后的教室列表
                val rooms = group.map { it.location }.distinct()
                // 提取合并后的周次
                val mergedWeeks = extractActiveWeeksFromRemark(merged.remark)

                WebViewLogger.logCourseMerge(
                    courseName = courseName,
                    position = position,
                    originalCount = group.size,
                    mergedWeeks = mergedWeeks,
                    mergedRooms = rooms
                )
            }
        }

        if (mergeCount > 0) {
            WebViewLogger.logParseDetail("[v90] 共合并 $mergeCount 组课程记录")
        }

        return mergedList
    }

    /**
     * [v90] 合并一组同一位置的课程记录
     *
     * @param courses 同一位置的同名课程记录列表
     * @return 合并后的课程记录
     */
    private fun mergeCourseGroup(courses: List<CourseEntity>): CourseEntity {
        val first = courses.first()

        // 收集所有活跃周（从 remark 中的 weeksBitmap 提取）
        val allActiveWeeks = mutableSetOf<Int>()
        val allRooms = mutableSetOf<String>()

        for (course in courses) {
            allRooms.add(course.location)

            // 从 remark 中提取周数位图
            val weeks = extractActiveWeeksFromRemark(course.remark)
            allActiveWeeks.addAll(weeks)
        }

        // 如果没有提取到位图，使用原有的周范围
        if (allActiveWeeks.isEmpty()) {
            // 合并周范围
            val minWeek = courses.minOf { it.startWeek }
            val maxWeek = courses.maxOf { it.endWeek }
            return first.copy(
                startWeek = minWeek,
                endWeek = maxWeek,
                location = allRooms.joinToString(","),
                remark = "多班合并"
            )
        }

        // [v96] 计算合并后的周范围（恢复偏移修正）
        val sortedWeeks = allActiveWeeks.sorted()
        val mergedStartWeek = sortedWeeks.first() - 1  // 应用偏移修正
        val mergedEndWeek = sortedWeeks.last() - 1

        // 重新判断周类型
        val weekType = determineWeekType(sortedWeeks)
        val weekTypeStr = when (weekType) {
            WeekType.ODD -> "单周"
            WeekType.EVEN -> "双周"
            WeekType.ALL -> ""
        }

        // 构建合并后的位图
        val mergedBitmap = CharArray(53) { '0' }
        for (week in sortedWeeks) {
            if (week in 1..53) {
                mergedBitmap[week - 1] = '1'
            }
        }

        // 合并教室信息
        val mergedLocation = allRooms.joinToString(",")

        // 构建备注
        val remarkParts = mutableListOf<String>()
        if (weekTypeStr.isNotEmpty()) {
            remarkParts.add(weekTypeStr)
        }
        remarkParts.add("多班合并")  // 标注这是合并后的记录
        remarkParts.add("weeksBitmap:${String(mergedBitmap)}")

        return first.copy(
            startWeek = mergedStartWeek,
            endWeek = mergedEndWeek,
            location = mergedLocation,
            remark = remarkParts.joinToString(";")
        )
    }

    /**
     * [v90] 从 remark 字段中提取活跃周列表
     *
     * @param remark 备注字段，格式如 "双周;weeksBitmap:000001010100000..."
     * @return 活跃周列表（1-indexed，未修正偏移）
     */
    private fun extractActiveWeeksFromRemark(remark: String): List<Int> {
        val activeWeeks = mutableListOf<Int>()

        // 查找 weeksBitmap:后面的位图
        val bitmapMatch = """weeksBitmap:([01]+)""".toRegex().find(remark)
        if (bitmapMatch != null) {
            val bitmap = bitmapMatch.groupValues[1]
            for ((index, char) in bitmap.withIndex()) {
                if (char == '1') {
                    activeWeeks.add(index + 1)  // 1-indexed
                }
            }
        }

        return activeWeeks
    }

    /**
     * [v53] 解析 table0.activities JSON 数据
     * 直接从 WebView 提取的 JSON 数据解析课程
     *
     * @param json JSON 字符串，格式: {"unitCount": 11, "data": [...]}
     * @param semester 学期标识
     * @return 解析出的课程实体列表
     */
    fun parseActivitiesJson(json: String, semester: String): List<CourseEntity> {
        WebViewLogger.logParseDetail("Starting activities JSON parsing")
        val courses = mutableListOf<CourseEntity>()

        try {
            val jsonObject = JSONObject(json)
            val unitCount = jsonObject.optInt("unitCount", 11)
            val dataArray = jsonObject.optJSONArray("data")

            if (dataArray == null || dataArray.length() == 0) {
                WebViewLogger.logParseDetail("[WARN] JSON data array is empty")
                return emptyList()
            }

            WebViewLogger.logParseDetail("JSON contains ${dataArray.length()} activity items")

            // 临时存储：(courseName, dayOfWeek, startNode) -> list of courses
            val courseMap = mutableMapOf<String, MutableList<CourseEntity>>()

            for (i in 0 until dataArray.length()) {
                try {
                    val item = dataArray.getJSONObject(i)

                    val index = item.optInt("index", 0)
                    val courseName = item.optString("courseName", "")
                    val teacherName = item.optString("teacherName", "")
                    val roomName = item.optString("roomName", "")
                    val vaildWeeks = item.optString("vaildWeeks", "")

                    if (courseName.isEmpty()) continue

                    // 计算 dayOfWeek 和 nodeIndex
                    // index = dayOfWeek * unitCount + nodeIndex
                    val dayOfWeek = index / unitCount + 1
                    val nodeIndex = index % unitCount + 1

                    // 解析周数和类型
                    val weekResult = parseWeeksBitmap(vaildWeeks)
                    val startWeek = weekResult.startWeek
                    val endWeek = weekResult.endWeek
                    val weekTypeStr = weekResult.weekType.toDisplayString()

                    // [v87] 构建备注信息，包含单双周标识
                    val remarkParts = mutableListOf<String>()
                    if (weekTypeStr.isNotEmpty()) {
                        remarkParts.add(weekTypeStr)
                    }
                    if (vaildWeeks.isNotEmpty()) {
                        remarkParts.add("weeksBitmap:$vaildWeeks")
                    }
                    val remark = remarkParts.joinToString(";")

                    WebViewLogger.logParseDetail("[$i] $courseName: day=$dayOfWeek, node=$nodeIndex, weeks=$startWeek-$endWeek, type=$weekTypeStr")

                    val course = CourseEntity(
                        name = courseName,
                        teacher = teacherName,
                        location = roomName,
                        dayOfWeek = dayOfWeek,
                        startWeek = startWeek,
                        endWeek = endWeek,
                        startNode = nodeIndex,
                        endNode = nodeIndex,
                        courseType = determineCourseType(courseName),
                        credit = 0.0,
                        remark = remark,
                        semester = semester
                    )

                    // [v91] 修改分组逻辑：不再使用周次范围作为 key
                    // 问题：当同一位置有多条不同周次的记录（多教学班）时，
                    // 使用周次范围分组会导致连续节次无法正确合并
                    // 解决：使用 课程名+星期+教室+周次位图 作为 key
                    // 这样每个教学班的每段连续节次都能被独立处理
                    val key = "${courseName}_${dayOfWeek}_${roomName}_${vaildWeeks}"
                    courseMap.getOrPut(key) { mutableListOf() }.add(course)

                } catch (e: Exception) {
                    WebViewLogger.logParseDetail("[ERROR] Failed to parse item $i: ${e.message}")
                }
            }

            // [v59] 合并同一课程的连续节次
            for ((_, courseList) in courseMap) {
                if (courseList.isEmpty()) continue

                // 按节次排序
                val sorted = courseList.sortedBy { it.startNode }

                // 按节次分组，连续的节次放在一起
                val groups = mutableListOf<MutableList<CourseEntity>>()
                var currentGroup = mutableListOf(sorted[0])

                for (j in 1 until sorted.size) {
                    val prev = currentGroup.last()
                    val curr = sorted[j]

                    // 如果当前节次与前一个连续，加入同一组
                    if (curr.startNode == prev.endNode + 1) {
                        currentGroup.add(curr)
                    } else {
                        // 不连续，保存当前组，开始新组
                        groups.add(currentGroup)
                        currentGroup = mutableListOf(curr)
                    }
                }
                groups.add(currentGroup)

                // 为每组创建合并后的课程
                for (group in groups) {
                    val first = group.first()
                    val last = group.last()

                    // 合并周数：取所有周数的并集
                    val allWeeks = group.flatMap { (it.startWeek..it.endWeek).toList() }
                    val minWeek = allWeeks.minOrNull() ?: first.startWeek
                    val maxWeek = allWeeks.maxOrNull() ?: first.endWeek

                    val merged = first.copy(
                        startNode = first.startNode,
                        endNode = last.endNode,
                        startWeek = minWeek,
                        endWeek = maxWeek
                    )
                    courses.add(merged)
                }
            }

            WebViewLogger.logParseDetail("JSON parsing complete: ${courses.size} courses after merge")

        } catch (e: Exception) {
            WebViewLogger.logParseDetail("[ERROR] JSON parsing failed: ${e.message}")
        }

        return courses
    }

    /**
     * 解析 infoTitle 单元格（JavaScript 渲染后）
     */
    private fun parseInfoTitleCell(cell: org.jsoup.nodes.Element, semester: String): List<CourseEntity> {
        val courses = mutableListOf<CourseEntity>()
        val title = cell.attr("title")
        if (title.isBlank()) return emptyList()

        WebViewLogger.logParseDetail("解析 infoTitle: ${title.take(80)}...")

        // title 格式：课程名称(课程代码) (教师);;;(周数,地点);实践周：[]
        val parts = title.split(";;;")

        var currentCourseInfo: String? = null
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.contains("(") && trimmed.contains(")") && !trimmed.startsWith("(")) {
                currentCourseInfo = trimmed
            } else if (trimmed.startsWith("(") && currentCourseInfo != null) {
                val course = parseCourseAndTime(currentCourseInfo, trimmed, cell, semester)
                if (course != null) {
                    courses.add(course)
                }
            }
        }

        return courses
    }

    private fun parseCourseAndTime(courseInfo: String, timeLocation: String, cell: org.jsoup.nodes.Element, semester: String): CourseEntity? {
        // 提取课程名称和教师
        val coursePattern = """^(.+?)\([^)]+\)\s*\(([^)]*)\)""".toRegex()
        val courseMatch = coursePattern.find(courseInfo)

        val (name, teacher) = if (courseMatch != null) {
            courseMatch.groupValues[1].trim() to courseMatch.groupValues[2].trim()
        } else {
            val simplePattern = """^(.+?)\s*\(([^)]*)\)""".toRegex()
            val simpleMatch = simplePattern.find(courseInfo)
            if (simpleMatch != null) {
                simpleMatch.groupValues[1].trim() to simpleMatch.groupValues[2].trim()
            } else {
                return null
            }
        }

        // 提取周数和地点
        val weekLocationPattern = """\((\d+)-(\d+),([^)]+)\)""".toRegex()
        val weekLocationMatch = weekLocationPattern.find(timeLocation)

        val startWeek: Int
        val endWeek: Int
        val location: String

        if (weekLocationMatch != null) {
            startWeek = weekLocationMatch.groupValues[1].toIntOrNull() ?: 1
            endWeek = weekLocationMatch.groupValues[2].toIntOrNull() ?: 16
            location = weekLocationMatch.groupValues[3].trim()
        } else {
            startWeek = 1
            endWeek = 16
            location = ""
        }

        // 从单元格 ID 获取节次和星期
        val (dayOfWeek, startNode, endNode) = parseCellPosition(cell)

        return CourseEntity(
            name = name,
            teacher = teacher,
            location = location,
            dayOfWeek = dayOfWeek,
            startWeek = startWeek,
            endWeek = endWeek,
            startNode = startNode,
            endNode = endNode,
            courseType = determineCourseType(name),
            credit = 0.0,
            remark = "",
            semester = semester
        )
    }

    /**
     * 从单元格 ID 解析节次和星期
     */
    private fun parseCellPosition(cell: org.jsoup.nodes.Element): Triple<Int, Int, Int> {
        val id = cell.id()

        // ID 格式：TDXY_0，其中 X 可能与行相关，Y 与列相关
        val pattern = """TD(\d)(\d)_\d+""".toRegex()
        val match = pattern.find(id)

        if (match != null) {
            val num1 = match.groupValues[1].toIntOrNull() ?: 0
            val num2 = match.groupValues[2].toIntOrNull() ?: 0

            // 分析：TD11_0 是周一第1-2节，TD22_0 是周二第1-2节
            val dayOfWeek = num2 + 1  // 星期几 (1-7)
            val startNode = num1 * 2 + 1  // 节次 (1, 3, 5, 7, 9, 11)
            val rowspan = cell.attr("rowspan").toIntOrNull() ?: 2
            val endNode = startNode + rowspan - 1

            return Triple(dayOfWeek, startNode, endNode)
        }

        return Triple(1, 1, 2)
    }

    /**
     * 根据课程名称判断课程类型
     */
    private fun determineCourseType(name: String): String {
        return when {
            name.contains("体育") -> CourseType.PHYSICAL_EDUCATION.displayName
            name.contains("实践") || name.contains("实验") -> CourseType.PRACTICE.displayName
            name.contains("选修") -> CourseType.ELECTIVE.displayName
            name.contains("必修") -> CourseType.REQUIRED.displayName
            name.contains("概论") || name.contains("思想") || name.contains("马克思") -> CourseType.REQUIRED.displayName
            else -> CourseType.OTHER.displayName
        }
    }

    /**
     * 从首页 HTML 解析当前教学周
     *
     * HTML 格式示例:
     * <td>本周为<font color="blue">2025-2026学年第2学期的</font>第<font color="red" size="5">1</font>教学周</td>
     *
     * @param html 首页 HTML
     * @return Pair<学期字符串, 当前教学周>，如 "2025-2026-2" to 1，解析失败返回 null
     */
    fun parseCurrentWeek(html: String): Pair<String, Int>? {
        AppLogger.i("CHD_CurrentWeek", "========== [Parser] parseCurrentWeek 开始 ==========")
        AppLogger.i("CHD_CurrentWeek", "HTML 长度: ${html.length}")

        try {
            val doc = Jsoup.parse(html)

            // 查找包含"本周为"的 td 元素
            val tdElements = doc.select("td")
            AppLogger.i("CHD_CurrentWeek", "找到 ${tdElements.size} 个 td 元素")

            for ((index, td) in tdElements.withIndex()) {
                val text = td.text()
                if (text.contains("本周为") && text.contains("教学周")) {
                    AppLogger.i("CHD_CurrentWeek", "在第 $index 个 td 找到教学周信息: $text")

                    // 提取学期信息（如 "2025-2026学年第2学期"）
                    val semesterPattern = """(\d{4})-(\d{4})学年第(\d)学期""".toRegex()
                    val semesterMatch = semesterPattern.find(text)

                    val semester = if (semesterMatch != null) {
                        val result = "${semesterMatch.groupValues[1]}-${semesterMatch.groupValues[2]}-${semesterMatch.groupValues[3]}"
                        AppLogger.i("CHD_CurrentWeek", "学期匹配成功: $result (正则: ${semesterPattern.pattern})")
                        result
                    } else {
                        AppLogger.w("CHD_CurrentWeek", "学期匹配失败，文本: $text")
                        null
                    }

                    // 提取周次（精确匹配 "第X教学周" 格式，避免匹配 "第X学期"）
                    // [v73 fix4] 修复正则：原来 `第.*?(\d+).*?教学周` 会错误匹配 "第2学期" 的 "2"
                    // 改为 `第(\d+)\s*教学周` 精确匹配 "第4教学周" 的 "4"
                    val weekPattern = """第(\d+)\s*教学周""".toRegex()
                    val weekMatch = weekPattern.find(text)
                    val week = weekMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    AppLogger.i("CHD_CurrentWeek", "周次匹配结果: $week (正则: ${weekPattern.pattern}, 匹配文本: ${weekMatch?.value})")

                    if (semester != null) {
                        AppLogger.i("CHD_CurrentWeek", "========== [Parser] parseCurrentWeek 成功: 学期=$semester, 周次=$week ==========")
                        return semester to week
                    }
                }
            }

            AppLogger.w("CHD_CurrentWeek", "未找到当前教学周信息")
            AppLogger.i("CHD_CurrentWeek", "========== [Parser] parseCurrentWeek 失败 ==========")
            return null
        } catch (e: Exception) {
            AppLogger.e("CHD_CurrentWeek", "解析当前教学周异常: ${e.message}", e)
            AppLogger.i("CHD_CurrentWeek", "========== [Parser] parseCurrentWeek 异常 ==========")
            return null
        }
    }
}
