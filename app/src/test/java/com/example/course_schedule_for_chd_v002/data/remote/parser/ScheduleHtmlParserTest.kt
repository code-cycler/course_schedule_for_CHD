package com.example.course_schedule_for_chd_v002.data.remote.parser

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * ScheduleHtmlParser 单元测试
 *
 * 说明：parser 实际抓取的是 TaskActivity JS（主路径）和 `td.infoTitle` 的 title 属性（fallback），
 * 不支持早期遗留的 `<div>课程 教师 教室 周次</div>` 通用格式——那批用例已移除（预存腐烂）。
 * 这里保留边界用例 + TaskActivity 多班合并回归（单双周）。
 */
class ScheduleHtmlParserTest {

    private lateinit var parser: ScheduleHtmlParser

    @Before
    fun setup() {
        parser = ScheduleHtmlParser()
    }

    // ================ 边界情况 ================

    @Test
    fun parse_emptyHtml_returnsEmptyList() {
        assertTrue(parser.parse("<html><body></body></html>", "2024-2025-1").isEmpty())
    }

    @Test
    fun parse_tableWithNoCourses_returnsEmptyList() {
        val html = """
            <html><body><table id="courseTable">
                <tr><th>节次</th><th>周一</th></tr>
                <tr><td>第1-2节</td><td></td></tr>
            </table></body></html>
        """.trimIndent()
        assertTrue(parser.parse(html, "2024-2025-1").isEmpty())
    }

    @Test
    fun parse_tableWithHeaderOnly_returnsEmptyList() {
        val html = """
            <html><body><table id="courseTable">
                <tr><th>节次</th><th>周一</th></tr>
            </table></body></html>
        """.trimIndent()
        assertTrue(parser.parse(html, "2024-2025-1").isEmpty())
    }

    @Test
    fun parse_courseWithEmptyName_returnsEmptyList() {
        val html = """
            <html><body><table id="courseTable">
                <tr><th>节次</th><th>周一</th></tr>
                <tr><td>第1-2节</td><td><div>1-16周</div></td></tr>
            </table></body></html>
        """.trimIndent()
        assertTrue(parser.parse(html, "2024-2025-1").isEmpty())
    }

    // ================ TaskActivity JS 多班合并（单双周回归）================

    /**
     * 回归测试：单双周课程消失 bug（用户报告 chd_course_report_20260405）
     *
     * C语言 周二第5-6节有两个教学班：
     *   - 班1：单5-9周（*WM2104机房）  → bitmap index 5,7,9
     *   - 班2：1-4周 + 双6-8周（*WM3105）→ bitmap index 1,2,3,4,6,8
     *
     * 修复前：位图提取正则尾部多了 `,"` 对所有 TaskActivity 都不匹配 → 班1位图提取失败 →
     *         合并时单5-9 整段丢失 → 第5/7/9周 C语言 消失。
     * 修复后：两班位图都正确提取 → 合并活跃周 = [1..9]。
     */
    @Test
    fun parse_taskActivity多班合并_单双周班不丢失() {
        val bitmapOdd = "0000010101" + "0".repeat(43)    // 单5,7,9周
        val bitmapMixed = "0111101010" + "0".repeat(43)  // 1,2,3,4,6,8周
        val html = """
            <script>
            var unitCount = 11;
            var table0 = new CourseTable(2026, 77);
            var teachers = [{id:1,name:"薛晶晶",lab:false}];
            var courseName = "C语言程序设计(24XK1706.32)";
            activity = new TaskActivity("1","薛晶晶","123(24XK1706.32)",courseName,"24XK1706.32)","700","*WM2104机房","$bitmapOdd",null,"","","");
            index = 1*unitCount+4;
            table0.activities[index][table0.activities[index].length] = activity;
            index = 1*unitCount+5;
            table0.activities[index][table0.activities[index].length] = activity;
            var teachers = [{id:1,name:"薛晶晶",lab:false}];
            var courseName = "C语言程序设计(24XK1706.32)";
            activity = new TaskActivity("1","薛晶晶","123(24XK1706.32)",courseName,"24XK1706.32)","701","*WM3105","$bitmapMixed",null,"","","");
            index = 1*unitCount+4;
            table0.activities[index][table0.activities[index].length] = activity;
            index = 1*unitCount+5;
            table0.activities[index][table0.activities[index].length] = activity;
            </script>
        """.trimIndent()

        val result = parser.parse(html, "2024-2025-1")

        val cCourse = result.find { it.name.contains("C语言") && it.dayOfWeek == 2 && it.startNode == 5 }
        assertNotNull("C语言 周二第5-6节应被解析", cCourse)

        val domain = cCourse!!.toDomainModel()
        // 学期传递
        assertEquals("2024-2025-1", domain.semester)
        // 单5-9 班的周次必须保留（修复前会丢失）
        assertTrue("第5周（单5-9）应有课", domain.isWeekInRange(5))
        assertTrue("第7周（单5-9）应有课", domain.isWeekInRange(7))
        assertTrue("第9周（单5-9）应有课", domain.isWeekInRange(9))
        // 班2 的周次
        assertTrue("第1周（班2）应有课", domain.isWeekInRange(1))
        assertTrue("第6周（班2 双6-8）应有课", domain.isWeekInRange(6))
        // 不在范围内的周
        assertFalse("第10周应无课", domain.isWeekInRange(10))
    }
}
