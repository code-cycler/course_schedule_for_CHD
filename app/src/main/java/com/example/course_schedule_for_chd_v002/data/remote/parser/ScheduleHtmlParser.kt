package com.example.course_schedule_for_chd_v002.data.remote.parser

import com.example.course_schedule_for_chd_v002.data.local.database.entity.CourseEntity
import com.example.course_schedule_for_chd_v002.domain.model.CourseType
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * 课程表 HTML 解析器
 * 解析教务系统返回的课程表 HTML，提取课程信息
 *
 * 支持的课表格式：
 * 1. 表格格式（table#courseTable 或 table.gridtable）
 * 2. JavaScript 嵌入的数据（var taskActivities）
 */
class ScheduleHtmlParser {

    /**
     * 解析课程表 HTML
     * @param html 课程表页面 HTML
     * @param semester 学期标识
     * @return 解析出的课程实体列表
     */
    fun parse(html: String, semester: String): List<CourseEntity> {
        val doc = Jsoup.parse(html)

        // 首先尝试解析表格格式
        val tableCourses = parseTableFormat(doc, semester)
        if (tableCourses.isNotEmpty()) {
            return tableCourses
        }

        // 如果表格解析失败，尝试解析 JavaScript 数据
        return parseJsData(doc, semester)
    }

    /**
     * 解析表格格式的课表
     */
    private fun parseTableFormat(doc: Document, semester: String): List<CourseEntity> {
        val courses = mutableListOf<CourseEntity>()

        // 查找课表表格（尝试多种选择器）
        val table = doc.select("table#courseTable").first()
            ?: doc.select("table.gridtable").first()
            ?: doc.select("table[id*=course]").first()
            ?: return emptyList()

        val rows = table.select("tr")

        // 遍历每一行
        for ((rowIndex, row) in rows.withIndex()) {
            // 跳过表头
            if (rowIndex == 0) continue

            val cells = row.select("td")

            // 遍历每个单元格
            for ((cellIndex, cell) in cells.withIndex()) {
                // 跳过第一节次列（通常是"第1-2节"这样的标签）
                if (cellIndex == 0) continue

                // 计算星期几（cellIndex 从1开始，对应周一）
                val dayOfWeek = cellIndex

                // 计算节次（根据行索引）
                // 通常每行代表2个节次，如第1-2节、第3-4节等
                val startNode = (rowIndex - 1) * 2 + 1
                val endNode = startNode + 1

                // 解析单元格中的课程
                val cellCourses = parseCell(cell, dayOfWeek, startNode, endNode, semester)
                courses.addAll(cellCourses)
            }
        }

        return courses
    }

    /**
     * 解析单个单元格
     */
    private fun parseCell(
        cell: Element,
        dayOfWeek: Int,
        defaultStartNode: Int,
        defaultEndNode: Int,
        semester: String
    ): List<CourseEntity> {
        val courses = mutableListOf<CourseEntity>()

        // 获取单元格的完整文本
        val cellText = cell.text()
        if (cellText.isBlank()) return emptyList()

        // 尝试解析单元格内的多个课程
        // 通常课程之间用换行或特定分隔符分隔
        val courseTexts = splitCourseTexts(cell)

        for (courseText in courseTexts) {
            val course = parseCourseText(courseText, dayOfWeek, defaultStartNode, defaultEndNode, semester)
            if (course != null) {
                courses.add(course)
            }
        }

        return courses
    }

    /**
     * 分割单元格文本为多个课程文本
     */
    private fun splitCourseTexts(cell: Element): List<String> {
        val texts = mutableListOf<String>()

        // 尝试按 div 或 p 标签分割
        val divs = cell.select("div")
        if (divs.isNotEmpty()) {
            for (div in divs) {
                val text = div.text().trim()
                if (text.isNotEmpty()) {
                    texts.add(text)
                }
            }
            return texts
        }

        // 尝试按 <br> 分割
        val html = cell.html()
        if (html.contains("<br")) {
            val parts = html.split("<br\\s*/?>".toRegex())
                .map { Jsoup.parse(it).text().trim() }
                .filter { it.isNotEmpty() && it != "----------------" }
            return parts
        }

        // 如果没有明显的分隔符，把整个单元格作为一个课程
        val text = cell.text().trim()
        if (text.isNotEmpty()) {
            texts.add(text)
        }

        return texts
    }

    /**
     * 解析课程文本
     * 典型格式: "高等数学\n张老师\nA101\n1-16周\n1-2节"
     */
    private fun parseCourseText(
        text: String,
        dayOfWeek: Int,
        defaultStartNode: Int,
        defaultEndNode: Int,
        semester: String
    ): CourseEntity? {
        if (text.isBlank()) return null

        // 按空白字符分割
        val lines = text.split("\\s+".toRegex())
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "----------------" }

        if (lines.isEmpty()) return null

        var name = ""
        var teacher = ""
        var location = ""
        var startWeek = 1
        var endWeek = 16
        var startNode = defaultStartNode
        var endNode = defaultEndNode
        var courseType = CourseType.OTHER

        for (line in lines) {
            when {
                // 周次: "1-16周" 或 "1-8周(单)" 或 "9-16周(双)"
                line.contains("周") && line.contains("-") -> {
                    val weekPattern = """(\d+)-(\d+)周(?:\((单|双)\))?""".toRegex()
                    val match = weekPattern.find(line)
                    if (match != null) {
                        startWeek = match.groupValues[1].toIntOrNull() ?: 1
                        endWeek = match.groupValues[2].toIntOrNull() ?: 16
                    }
                }

                // 节次: "1-2节" 或 "第1-2节"
                line.contains("节") -> {
                    val nodePattern = """(\d+)-(\d+)节""".toRegex()
                    val match = nodePattern.find(line)
                    if (match != null) {
                        startNode = match.groupValues[1].toIntOrNull() ?: defaultStartNode
                        endNode = match.groupValues[2].toIntOrNull() ?: defaultEndNode
                    }
                }

                // 地点: 通常包含数字和字母组合
                line.matches(Regex(".*[A-Za-z].*\\d+.*")) && location.isEmpty() -> {
                    location = line
                }

                // 课程类型
                line.contains("必修") -> courseType = CourseType.REQUIRED
                line.contains("选修") -> courseType = CourseType.ELECTIVE
                line.contains("公选") -> courseType = CourseType.PUBLIC_ELECTIVE
                line.contains("体育") -> courseType = CourseType.PHYSICAL_EDUCATION
                line.contains("实践") -> courseType = CourseType.PRACTICE

                // 其他情况：可能是课程名或教师名
                else -> {
                    if (name.isEmpty()) {
                        name = line
                    } else if (teacher.isEmpty() && !line.matches(Regex("\\d+.*"))) {
                        // 教师名通常不包含数字
                        teacher = line
                    }
                }
            }
        }

        // 如果没有解析到课程名，返回 null
        if (name.isEmpty()) return null

        return CourseEntity(
            name = name,
            teacher = teacher,
            location = location,
            dayOfWeek = dayOfWeek,
            startWeek = startWeek,
            endWeek = endWeek,
            startNode = startNode,
            endNode = endNode,
            courseType = courseType.displayName,
            credit = 0.0, // 课表页面通常没有学分信息
            remark = "",
            semester = semester
        )
    }

    /**
     * 解析 JavaScript 嵌入的课表数据
     */
    private fun parseJsData(doc: Document, semester: String): List<CourseEntity> {
        val courses = mutableListOf<CourseEntity>()

        val scripts = doc.select("script")
        for (script in scripts) {
            val content = script.html()

            // 查找 taskActivities 变量
            if (content.contains("var taskActivities") || content.contains("taskActivities =")) {
                // 提取 JS 数组数据
                // 格式通常为: var taskActivities = [["课程名", "教师", ...], ...]
                val arrayPattern = """taskActivities\s*=\s*(\[[\s\S]*?\]);""".toRegex()
                val match = arrayPattern.find(content)

                if (match != null) {
                    // 解析 JSON 数组（需要进一步处理）
                    // 这里简化处理，实际可能需要更复杂的解析
                    val parsedCourses = parseJsArray(match.groupValues[1], semester)
                    return parsedCourses
                }
            }
        }

        return courses
    }

    /**
     * 解析 JavaScript 数组格式的课表数据
     */
    private fun parseJsArray(arrayStr: String, semester: String): List<CourseEntity> {
        // 这是一个简化的实现
        // 实际的教务系统数据格式可能更复杂
        val courses = mutableListOf<CourseEntity>()

        // 匹配数组元素
        val elementPattern = """"([^"]+)"""".toRegex()
        val matches = elementPattern.findAll(arrayStr)

        // 每5个元素为一组（课程名、教师、地点、时间等）
        val elements = matches.map { it.groupValues[1] }.toList()

        // 简化处理：直接返回空列表
        // 实际实现需要根据教务系统的具体数据格式调整

        return courses
    }
}
