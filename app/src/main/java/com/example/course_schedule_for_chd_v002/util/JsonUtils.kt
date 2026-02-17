package com.example.course_schedule_for_chd_v002.util

import com.example.course_schedule_for_chd_v002.domain.model.Course
import com.example.course_schedule_for_chd_v002.domain.model.CourseType
import com.example.course_schedule_for_chd_v002.domain.model.DayOfWeek

/**
 * JSON 工具类
 * 提供课程数据的导入导出功能
 */
object JsonUtils {

    /**
     * 导出课程列表为 JSON 字符串
     *
     * @param courses 课程列表
     * @return JSON 字符串
     */
    fun exportCoursesToJson(courses: List<Course>): String {
        if (courses.isEmpty()) return "[]"

        val courseJsons = courses.map { course ->
            """{"id":${course.id},"name":"${escapeJson(course.name)}","teacher":"${escapeJson(course.teacher)}","location":"${escapeJson(course.location)}","dayOfWeek":"${course.dayOfWeek.name}","startWeek":${course.startWeek},"endWeek":${course.endWeek},"startNode":${course.startNode},"endNode":${course.endNode},"courseType":"${course.courseType.name}","credit":${course.credit},"remark":"${escapeJson(course.remark)}","semester":"${escapeJson(course.semester)}"}"""
        }

        return "[${courseJsons.joinToString(",")}]"
    }

    /**
     * 从 JSON 字符串导入课程
     *
     * @param jsonString JSON 字符串
     * @return 课程列表，解析失败返回空列表
     */
    fun importCoursesFromJson(jsonString: String): List<Course> {
        return try {
            parseCourseArray(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 转义 JSON 字符串中的特殊字符
     */
    private fun escapeJson(str: String): String {
        return str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    /**
     * 反转义 JSON 字符串中的特殊字符
     */
    private fun unescapeJson(str: String): String {
        return str
            .replace("\\t", "\t")
            .replace("\\r", "\r")
            .replace("\\n", "\n")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    /**
     * 解析课程数组 JSON
     */
    private fun parseCourseArray(json: String): List<Course> {
        val trimmed = json.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return emptyList()
        }

        // 移除最外层的 []
        val content = trimmed.substring(1, trimmed.length - 1).trim()
        if (content.isEmpty()) {
            return emptyList()
        }

        // 分割各个课程对象
        val courseObjects = splitJsonObjects(content)

        return courseObjects.mapNotNull { parseCourseObject(it) }
    }

    /**
     * 分割 JSON 对象字符串
     */
    private fun splitJsonObjects(content: String): List<String> {
        val objects = mutableListOf<String>()
        var depth = 0
        var start = -1

        for (i in content.indices) {
            when (content[i]) {
                '{' -> {
                    if (depth == 0) start = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        objects.add(content.substring(start, i + 1))
                        start = -1
                    }
                }
            }
        }

        return objects
    }

    /**
     * 解析单个课程对象
     */
    private fun parseCourseObject(json: String): Course? {
        return try {
            val fields = parseJsonFields(json)

            Course(
                id = fields["id"]?.toLongOrNull() ?: 0,
                name = unescapeJson(fields["name"] ?: ""),
                teacher = unescapeJson(fields["teacher"] ?: ""),
                location = unescapeJson(fields["location"] ?: ""),
                dayOfWeek = DayOfWeek.valueOf(fields["dayOfWeek"] ?: "MONDAY"),
                startWeek = fields["startWeek"]?.toIntOrNull() ?: 1,
                endWeek = fields["endWeek"]?.toIntOrNull() ?: 16,
                startNode = fields["startNode"]?.toIntOrNull() ?: 1,
                endNode = fields["endNode"]?.toIntOrNull() ?: 2,
                courseType = CourseType.valueOf(fields["courseType"] ?: "OTHER"),
                credit = fields["credit"]?.toDoubleOrNull() ?: 0.0,
                remark = unescapeJson(fields["remark"] ?: ""),
                semester = unescapeJson(fields["semester"] ?: "")
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 解析 JSON 对象的字段
     */
    private fun parseJsonFields(json: String): Map<String, String> {
        val fields = mutableMapOf<String, String>()
        var i = 0
        val len = json.length

        while (i < len) {
            // 查找字段名
            val nameStart = json.indexOf('"', i)
            if (nameStart == -1) break

            val nameEnd = json.indexOf('"', nameStart + 1)
            if (nameEnd == -1) break

            val fieldName = json.substring(nameStart + 1, nameEnd)

            // 查找冒号
            val colonPos = json.indexOf(':', nameEnd)
            if (colonPos == -1) break

            // 查找值
            i = colonPos + 1
            while (i < len && (json[i] == ' ' || json[i] == '\t' || json[i] == '\n')) i++

            if (i >= len) break

            val fieldValue = when (json[i]) {
                '"' -> {
                    // 字符串值
                    val valueStart = i + 1
                    var valueEnd = valueStart
                    var escaped = false

                    while (valueEnd < len) {
                        if (escaped) {
                            escaped = false
                        } else if (json[valueEnd] == '\\') {
                            escaped = true
                        } else if (json[valueEnd] == '"') {
                            break
                        }
                        valueEnd++
                    }

                    val rawValue = json.substring(valueStart, valueEnd)
                    i = valueEnd + 1
                    rawValue
                }
                else -> {
                    // 数字或枚举值
                    val valueStart = i
                    while (i < len && json[i] != ',' && json[i] != '}') i++
                    json.substring(valueStart, i).trim()
                }
            }

            fields[fieldName] = fieldValue

            // 跳过逗号
            while (i < len && json[i] != ',' && json[i] != '}') i++
            if (i < len && json[i] == ',') i++
        }

        return fields
    }
}
