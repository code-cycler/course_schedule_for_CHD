package com.example.course_schedule_for_chd_v002.util

import com.example.course_schedule_for_chd_v002.domain.model.CourseType
import com.example.course_schedule_for_chd_v002.domain.model.DayOfWeek
import org.junit.Assert.*
import org.junit.Test

/**
 * JsonUtils 单元测试
 * 测试课程数据的导入导出功能
 */
class JsonUtilsTest {

    // ================ exportCoursesToJson 测试 ================

    @Test
    fun `exportCoursesToJson with empty list returns valid json array`() {
        val json = JsonUtils.exportCoursesToJson(emptyList())
        assertEquals("[]", json)
    }

    @Test
    fun `exportCoursesToJson with courses returns valid json`() {
        val courses = listOf(
            TestDataFactory.createCourse(
                id = 1,
                name = "Math",
                teacher = "Dr. Zhang",
                location = "A101"
            )
        )

        val json = JsonUtils.exportCoursesToJson(courses)

        // 验证 JSON 包含关键字段
        assertTrue(json.contains("\"name\":\"Math\""))
        assertTrue(json.contains("\"teacher\":\"Dr. Zhang\""))
        assertTrue(json.contains("\"location\":\"A101\""))
    }

    @Test
    fun `exportCoursesToJson with multiple courses returns array`() {
        val courses = listOf(
            TestDataFactory.createCourse(id = 1, name = "Course1"),
            TestDataFactory.createCourse(id = 2, name = "Course2")
        )

        val json = JsonUtils.exportCoursesToJson(courses)

        assertTrue(json.startsWith("["))
        assertTrue(json.endsWith("]"))
        assertTrue(json.contains("\"name\":\"Course1\""))
        assertTrue(json.contains("\"name\":\"Course2\""))
    }

    // ================ importCoursesFromJson 测试 ================

    @Test
    fun `importCoursesFromJson with valid json returns courses`() {
        val json = """[{"id":1,"name":"Math","teacher":"Dr. Zhang","location":"A101","dayOfWeek":"MONDAY","startWeek":1,"endWeek":16,"startNode":1,"endNode":2,"courseType":"REQUIRED","credit":4.0,"remark":"","semester":"2024-2025-1"}]"""

        val courses = JsonUtils.importCoursesFromJson(json)

        assertEquals(1, courses.size)
        assertEquals("Math", courses[0].name)
        assertEquals(DayOfWeek.MONDAY, courses[0].dayOfWeek)
        assertEquals(CourseType.REQUIRED, courses[0].courseType)
    }

    @Test
    fun `importCoursesFromJson with empty array returns empty list`() {
        val json = "[]"

        val courses = JsonUtils.importCoursesFromJson(json)

        assertTrue(courses.isEmpty())
    }

    @Test
    fun `importCoursesFromJson with invalid json returns empty list`() {
        val json = "invalid json"

        val courses = JsonUtils.importCoursesFromJson(json)

        assertTrue(courses.isEmpty())
    }

    @Test
    fun `importCoursesFromJson with malformed json returns empty list`() {
        val json = """[{"name":"Math"""  // 不完整的 JSON

        val courses = JsonUtils.importCoursesFromJson(json)

        assertTrue(courses.isEmpty())
    }

    // ================ 数据完整性测试 ================

    @Test
    fun `export and import maintains data integrity`() {
        val originalCourses = listOf(
            TestDataFactory.createCourse(
                id = 1,
                name = "Calculus",
                teacher = "Prof. Wang",
                location = "B202",
                dayOfWeek = DayOfWeek.WEDNESDAY,
                startWeek = 1,
                endWeek = 16,
                startNode = 3,
                endNode = 4,
                courseType = CourseType.REQUIRED,
                credit = 3.5,
                remark = "Important course",
                semester = "2024-2025-1"
            )
        )

        val json = JsonUtils.exportCoursesToJson(originalCourses)
        val restoredCourses = JsonUtils.importCoursesFromJson(json)

        assertEquals(1, restoredCourses.size)
        assertEquals(originalCourses[0].name, restoredCourses[0].name)
        assertEquals(originalCourses[0].teacher, restoredCourses[0].teacher)
        assertEquals(originalCourses[0].location, restoredCourses[0].location)
        assertEquals(originalCourses[0].dayOfWeek, restoredCourses[0].dayOfWeek)
        assertEquals(originalCourses[0].startWeek, restoredCourses[0].startWeek)
        assertEquals(originalCourses[0].endWeek, restoredCourses[0].endWeek)
        assertEquals(originalCourses[0].startNode, restoredCourses[0].startNode)
        assertEquals(originalCourses[0].endNode, restoredCourses[0].endNode)
        assertEquals(originalCourses[0].courseType, restoredCourses[0].courseType)
        assertEquals(originalCourses[0].credit, restoredCourses[0].credit, 0.01)
        assertEquals(originalCourses[0].remark, restoredCourses[0].remark)
        assertEquals(originalCourses[0].semester, restoredCourses[0].semester)
    }

    @Test
    fun `export and import with multiple courses maintains all data`() {
        val originalCourses = listOf(
            TestDataFactory.createCourse(
                id = 1,
                name = "Physics",
                dayOfWeek = DayOfWeek.MONDAY,
                courseType = CourseType.REQUIRED
            ),
            TestDataFactory.createCourse(
                id = 2,
                name = "PE",
                dayOfWeek = DayOfWeek.FRIDAY,
                courseType = CourseType.PHYSICAL_EDUCATION
            ),
            TestDataFactory.createCourse(
                id = 3,
                name = "Art",
                dayOfWeek = DayOfWeek.SUNDAY,
                courseType = CourseType.PUBLIC_ELECTIVE
            )
        )

        val json = JsonUtils.exportCoursesToJson(originalCourses)
        val restoredCourses = JsonUtils.importCoursesFromJson(json)

        assertEquals(3, restoredCourses.size)
        assertEquals("Physics", restoredCourses[0].name)
        assertEquals("PE", restoredCourses[1].name)
        assertEquals("Art", restoredCourses[2].name)
    }

    @Test
    fun `import with special characters in fields handles correctly`() {
        val courseWithSpecialChars = TestDataFactory.createCourse(
            name = "Course with \"quotes\" and \\backslash",
            teacher = "Teacher's Name",
            location = "Room 101, Building A"
        )

        val json = JsonUtils.exportCoursesToJson(listOf(courseWithSpecialChars))
        val restoredCourses = JsonUtils.importCoursesFromJson(json)

        assertEquals(1, restoredCourses.size)
        assertEquals(courseWithSpecialChars.name, restoredCourses[0].name)
        assertEquals(courseWithSpecialChars.teacher, restoredCourses[0].teacher)
        assertEquals(courseWithSpecialChars.location, restoredCourses[0].location)
    }
}
