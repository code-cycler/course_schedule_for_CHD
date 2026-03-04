package com.example.course_schedule_for_chd_v002.data.remote.parser

import com.example.course_schedule_for_chd_v002.data.local.database.entity.CourseEntity
import com.example.course_schedule_for_chd_v002.domain.model.CourseType
import com.example.course_schedule_for_chd_v002.util.WebViewLogger
import org.jsoup.Jsoup
import org.json.JSONObject

/**
 * 课程表 HTML 解析器 (v52)
 *
 * 解析长安大学教务系统课表页面的 JavaScript 数据
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

        // 去重：同一课程在同一时间只保留一个
        val distinctCourses = courses.distinctBy { "${it.name}-${it.dayOfWeek}-${it.startNode}-${it.startWeek}" }

        WebViewLogger.logParseDetail("去重前: ${courses.size}, 去重后: ${distinctCourses.size}")
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

                // 解析周数范围
                val (startWeek, endWeek) = parseWeeksBitmap(weeksBitmap)

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
                                remark = if (weeksBitmap.isNotEmpty()) "weeksBitmap:$weeksBitmap" else "",
                                semester = semester
                            )
                            courses.add(course)
                            WebViewLogger.logParseDetail("[$blockIndex] 添加课程: ${course.name}, 周:${course.startWeek}-${course.endWeek}, 周${course.dayOfWeek} 第${course.startNode}-${course.endNode}节")
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
     * [v36] 解析周数位图
     * 修复周次偏移+1的问题（学校显示1-17周，APP显示2-18周）
     * @param bitmap 53个字符的位图，'1'表示有课，'0'表示无课
     * @return (startWeek, endWeek) 有课的周数范围
     */
    private fun parseWeeksBitmap(bitmap: String): Pair<Int, Int> {
        if (bitmap.isEmpty()) return Pair(1, 16)
        if (bitmap.length < 53) {
            WebViewLogger.logParseDetail("[WARN] 周数位图长度不足: ${bitmap.length}")
            return Pair(1, 16)
        }

        var startWeek = Int.MAX_VALUE
        var endWeek = 0

        for ((index, char) in bitmap.withIndex()) {
            if (char == '1') {
                // [v36] 原逻辑: week = index + 1
                // 但学校系统位图有+1偏移，需要减1修正
                val week = index + 1
                if (week < startWeek) {
                    startWeek = week
                }
                if (week > endWeek) {
                    endWeek = week
                }
            }
        }

        // [v36] 应用偏移修正：减1以匹配学校系统的周次
        val correctedStartWeek = if (startWeek != Int.MAX_VALUE) startWeek - 1 else 1
        val correctedEndWeek = if (endWeek > 0) endWeek - 1 else 16

        return if (startWeek != Int.MAX_VALUE) {
            WebViewLogger.logParseDetail("[v36] 周数位图解析: 原始=$startWeek-$endWeek, 修正后=$correctedStartWeek-$correctedEndWeek")
            Pair(correctedStartWeek, correctedEndWeek)
        } else {
            WebViewLogger.logParseDetail("[WARN] 周数位图全为0")
            Pair(1, 16)
        }
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

                    // 解析周数
                    val (startWeek, endWeek) = parseWeeksBitmap(vaildWeeks)

                    WebViewLogger.logParseDetail("[$i] $courseName: day=$dayOfWeek, node=$nodeIndex, weeks=$startWeek-$endWeek")

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
                        remark = if (vaildWeeks.isNotEmpty()) "weeksBitmap:$vaildWeeks" else "",
                        semester = semester
                    )

                    // 使用 key 分组以便合并连续节次
                    val key = "${courseName}_${dayOfWeek}_${startWeek}_${endWeek}"
                    courseMap.getOrPut(key) { mutableListOf() }.add(course)

                } catch (e: Exception) {
                    WebViewLogger.logParseDetail("[ERROR] Failed to parse item $i: ${e.message}")
                }
            }

            // 合并同一课程的连续节次
            for ((_, courseList) in courseMap) {
                if (courseList.isEmpty()) continue

                // 按节次排序
                val sorted = courseList.sortedBy { it.startNode }

                // 合并连续节次
                var merged = sorted[0]
                for (j in 1 until sorted.size) {
                    val current = sorted[j]
                    if (current.startNode == merged.endNode + 1) {
                        // 连续节次，扩展结束节次
                        merged = merged.copy(endNode = current.endNode)
                    } else {
                        // 不连续，保存当前合并结果，开始新的合并
                        courses.add(merged)
                        merged = current
                    }
                }
                courses.add(merged)
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
}
