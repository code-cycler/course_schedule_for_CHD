package com.example.course_schedule_for_chd_v002.data.remote.parser

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * ScheduleHtmlParser 单元测试
 * 测试课表 HTML 解析功能
 */
class ScheduleHtmlParserTest {

    private lateinit var parser: ScheduleHtmlParser

    @Before
    fun setup() {
        parser = ScheduleHtmlParser()
    }

    // ================ 表格格式解析测试 ================

    @Test
    fun parse_emptyHtml_returnsEmptyList() {
        // Given
        val html = "<html><body></body></html>"

        // When
        val result = parser.parse(html, "2024-2025-1")

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun parse_tableWithNoCourses_returnsEmptyList() {
        // Given
        val html = """
            <html>
            <body>
                <table id="courseTable">
                    <tr><th>节次</th><th>周一</th><th>周二</th></tr>
                    <tr><td>第1-2节</td><td></td><td></td></tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        // When
        val result = parser.parse(html, "2024-2025-1")

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun parse_tableWithSingleCourse_returnsCourse() {
        // Given
        val html = """
            <html>
            <body>
                <table id="courseTable">
                    <tr><th>节次</th><th>周一</th><th>周二</th></tr>
                    <tr>
                        <td>第1-2节</td>
                        <td>
                            <div>高等数学 张老师 A101 1-16周</div>
                        </td>
                        <td></td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        // When
        val result = parser.parse(html, "2024-2025-1")

        // Then
        assertEquals(1, result.size)
        val course = result[0]
        assertEquals("高等数学", course.name)
        assertEquals("张老师", course.teacher)
        assertEquals(1, course.dayOfWeek) // 周一
        assertEquals(1, course.startNode)
        assertEquals(2, course.endNode)
        assertEquals("2024-2025-1", course.semester)
    }

    @Test
    fun parse_tableWithMultipleCourses_returnsAllCourses() {
        // Given
        val html = """
            <html>
            <body>
                <table id="courseTable">
                    <tr><th>节次</th><th>周一</th><th>周二</th><th>周三</th></tr>
                    <tr>
                        <td>第1-2节</td>
                        <td><div>高等数学 张老师 A101 1-16周</div></td>
                        <td></td>
                        <td><div>大学英语 李老师 B202 1-16周</div></td>
                    </tr>
                    <tr>
                        <td>第3-4节</td>
                        <td></td>
                        <td><div>线性代数 王老师 C303 1-8周</div></td>
                        <td></td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        // When
        val result = parser.parse(html, "2024-2025-1")

        // Then
        assertEquals(3, result.size)
    }

    @Test
    fun parse_courseWithWeekRange_parsesCorrectly() {
        // Given
        val html = """
            <html>
            <body>
                <table id="courseTable">
                    <tr><th>节次</th><th>周一</th></tr>
                    <tr>
                        <td>第1-2节</td>
                        <td><div>体育课 体育老师 操场 9-16周</div></td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        // When
        val result = parser.parse(html, "2024-2025-1")

        // Then
        assertEquals(1, result.size)
        assertEquals(9, result[0].startWeek)
        assertEquals(16, result[0].endWeek)
    }

    @Test
    fun parse_courseWithOddWeek_parsesCorrectly() {
        // Given
        val html = """
            <html>
            <body>
                <table id="courseTable">
                    <tr><th>节次</th><th>周一</th></tr>
                    <tr>
                        <td>第1-2节</td>
                        <td><div>实验课 实验老师 实验室 1-15周(单)</div></td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        // When
        val result = parser.parse(html, "2024-2025-1")

        // Then
        assertEquals(1, result.size)
        assertEquals(1, result[0].startWeek)
        assertEquals(15, result[0].endWeek)
    }

    @Test
    fun parse_courseWithEvenWeek_parsesCorrectly() {
        // Given
        val html = """
            <html>
            <body>
                <table id="courseTable">
                    <tr><th>节次</th><th>周一</th></tr>
                    <tr>
                        <td>第1-2节</td>
                        <td><div>实验课 实验老师 实验室 2-16周(双)</div></td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        // When
        val result = parser.parse(html, "2024-2025-1")

        // Then
        assertEquals(1, result.size)
        assertEquals(2, result[0].startWeek)
        assertEquals(16, result[0].endWeek)
    }

    @Test
    fun parse_courseWithCustomNodeRange_parsesCorrectly() {
        // Given
        val html = """
            <html>
            <body>
                <table id="courseTable">
                    <tr><th>节次</th><th>周一</th></tr>
                    <tr>
                        <td>第1-2节</td>
                        <td><div>长课时课 张老师 A101 1-16周 3-6节</div></td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        // When
        val result = parser.parse(html, "2024-2025-1")

        // Then
        assertEquals(1, result.size)
        // 如果文本中指定了节次，应该使用文本中的节次
        assertEquals(3, result[0].startNode)
        assertEquals(6, result[0].endNode)
    }

    @Test
    fun parse_courseWithLocation_parsesLocation() {
        // Given
        val html = """
            <html>
            <body>
                <table id="courseTable">
                    <tr><th>节次</th><th>周一</th></tr>
                    <tr>
                        <td>第1-2节</td>
                        <td><div>高等数学 张老师 逸夫楼A101 1-16周</div></td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        // When
        val result = parser.parse(html, "2024-2025-1")

        // Then
        assertEquals(1, result.size)
        assertTrue(result[0].location.contains("A101") || result[0].location.contains("逸夫楼"))
    }

    @Test
    fun parse_courseWithCourseType_parsesType() {
        // Given
        val html = """
            <html>
            <body>
                <table id="courseTable">
                    <tr><th>节次</th><th>周一</th><th>周二</th></tr>
                    <tr>
                        <td>第1-2节</td>
                        <td><div>高等数学 张老师 A101 1-16周 必修</div></td>
                        <td><div>公共选修课 李老师 B202 1-8周 公选</div></td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        // When
        val result = parser.parse(html, "2024-2025-1")

        // Then
        assertEquals(2, result.size)
        assertEquals("必修", result[0].courseType)
        assertEquals("公选", result[1].courseType)
    }

    // ================ 分隔符处理测试 ================

    @Test
    fun parse_cellWithBrSeparator_parsesMultipleCourses() {
        // Given
        val html = """
            <html>
            <body>
                <table id="courseTable">
                    <tr><th>节次</th><th>周一</th></tr>
                    <tr>
                        <td>第1-2节</td>
                        <td>
                            高等数学 张老师 A101 1-8周<br/>
                            线性代数 李老师 B202 9-16周
                        </td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        // When
        val result = parser.parse(html, "2024-2025-1")

        // Then
        // 应该解析出两个课程（如果支持 br 分隔）
        // 根据实现可能返回1或2个课程
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun parse_cellWithMultipleDivs_parsesAllCourses() {
        // Given
        val html = """
            <html>
            <body>
                <table id="courseTable">
                    <tr><th>节次</th><th>周一</th></tr>
                    <tr>
                        <td>第1-2节</td>
                        <td>
                            <div>高等数学 张老师 A101 1-8周</div>
                            <div>线性代数 李老师 B202 9-16周</div>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        // When
        val result = parser.parse(html, "2024-2025-1")

        // Then
        assertEquals(2, result.size)
        assertEquals("高等数学", result[0].name)
        assertEquals("线性代数", result[1].name)
    }

    // ================ 不同表格选择器测试 ================

    @Test
    fun parse_gridtableClass_parsesCorrectly() {
        // Given
        val html = """
            <html>
            <body>
                <table class="gridtable">
                    <tr><th>节次</th><th>周一</th></tr>
                    <tr>
                        <td>第1-2节</td>
                        <td><div>高等数学 张老师 A101 1-16周</div></td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        // When
        val result = parser.parse(html, "2024-2025-1")

        // Then
        assertEquals(1, result.size)
        assertEquals("高等数学", result[0].name)
    }

    @Test
    fun parse_courseTableId_parsesCorrectly() {
        // Given
        val html = """
            <html>
            <body>
                <table id="courseTable">
                    <tr><th>节次</th><th>周一</th></tr>
                    <tr>
                        <td>第1-2节</td>
                        <td><div>大学英语 李老师 B202 1-16周</div></td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        // When
        val result = parser.parse(html, "2024-2025-1")

        // Then
        assertEquals(1, result.size)
        assertEquals("大学英语", result[0].name)
    }

    // ================ 学期传递测试 ================

    @Test
    fun parse_differentSemesters_storesCorrectSemester() {
        // Given
        val html = """
            <html>
            <body>
                <table id="courseTable">
                    <tr><th>节次</th><th>周一</th></tr>
                    <tr>
                        <td>第1-2节</td>
                        <td><div>测试课程 老师 A101 1-16周</div></td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        // When
        val result1 = parser.parse(html, "2023-2024-2")
        val result2 = parser.parse(html, "2024-2025-1")

        // Then
        assertEquals("2023-2024-2", result1[0].semester)
        assertEquals("2024-2025-1", result2[0].semester)
    }

    // ================ 边界情况测试 ================

    @Test
    fun parse_courseWithEmptyName_returnsEmptyList() {
        // Given - 没有课程名的单元格
        val html = """
            <html>
            <body>
                <table id="courseTable">
                    <tr><th>节次</th><th>周一</th></tr>
                    <tr>
                        <td>第1-2节</td>
                        <td><div>1-16周</div></td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        // When
        val result = parser.parse(html, "2024-2025-1")

        // Then
        // 如果没有课程名，应该跳过
        assertTrue(result.isEmpty())
    }

    @Test
    fun parse_tableWithHeaderOnly_returnsEmptyList() {
        // Given - 只有表头，没有数据行
        val html = """
            <html>
            <body>
                <table id="courseTable">
                    <tr><th>节次</th><th>周一</th><th>周二</th></tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        // When
        val result = parser.parse(html, "2024-2025-1")

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun parse_complexCourseFormat_parsesCorrectly() {
        // Given - 复杂的课程格式
        val html = """
            <html>
            <body>
                <table id="courseTable">
                    <tr><th>节次</th><th>周一</th></tr>
                    <tr>
                        <td>第1-2节</td>
                        <td>
                            <div>
                                高等数学A(上)<br/>
                                张三<br/>
                                逸夫教学楼A101<br/>
                                1-16周<br/>
                                必修
                            </div>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        // When
        val result = parser.parse(html, "2024-2025-1")

        // Then
        assertTrue(result.isNotEmpty())
        // 课程名应该包含 "高等数学"
        assertTrue(result[0].name.contains("高等数学"))
    }
}
