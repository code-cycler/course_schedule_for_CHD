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
     * [v121] 语义更新：两班活跃周不相交（[5,7,9] vs [1,2,3,4,6,8]）→ 不再合并，
     *         各自独立成卡、各带自己的教室+位图；周覆盖断言不变（任一卡覆盖即有课）。
     */
    @Test
    fun parse_taskActivity多班单双周_周次不相交拆分独立成卡() {
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

        val cCourses = result.filter { it.name.contains("C语言") && it.dayOfWeek == 2 && it.startNode == 5 }
        assertEquals("周次不相交应拆为两条独立记录", 2, cCourses.size)

        val oddCard = cCourses.find { it.location == "*WM2104机房" }
        val mixedCard = cCourses.find { it.location == "*WM3105" }
        assertNotNull("单5-9周机房卡应存在", oddCard)
        assertNotNull("1-4+双6-8周教室卡应存在", mixedCard)

        // 学期传递
        assertEquals("2024-2025-1", oddCard!!.toDomainModel().semester)

        val oddDomain = oddCard.toDomainModel()
        val mixedDomain = mixedCard!!.toDomainModel()
        // 单5-9 班的周次必须保留（修复前会丢失）
        assertTrue("第5周（单5-9）应有课", oddDomain.isWeekInRange(5))
        assertTrue("第7周（单5-9）应有课", oddDomain.isWeekInRange(7))
        assertTrue("第9周（单5-9）应有课", oddDomain.isWeekInRange(9))
        // 班2 的周次
        assertTrue("第1周（班2）应有课", mixedDomain.isWeekInRange(1))
        assertTrue("第6周（班2 双6-8）应有课", mixedDomain.isWeekInRange(6))
        // 两卡各管各周，互不越界
        assertFalse("第6周不属于机房卡（属另一卡）", oddDomain.isWeekInRange(6))
        assertFalse("第5周不属于班2卡", mixedDomain.isWeekInRange(5))
        // 周次不相交 → 第1-9周每周恰有一张卡活跃，第10周无课
        for (week in 1..9) {
            assertEquals("第${week}周应恰有一张卡活跃", 1,
                listOf(oddDomain, mixedDomain).count { it.isWeekInRange(week) })
        }
        assertFalse("第10周应无课", oddDomain.isWeekInRange(10) || mixedDomain.isWeekInRange(10))
    }

    // ================ TaskActivity 分周切换线上/线下（v121 回归）================

    /**
     * 回归测试：分周切换线上/线下课程被并卡 bug（用户报告 2026-09-15，心理健康教育）
     *
     * 大学生心理健康教育 周三第5-6节，同一教学班（61603）两条记录：
     *   - 线上「虚拟教室-同步学习平台」周 [3,5-8,10-11,13-14,16-18]
     *   - 线下「*WM1403」周 [4,9,12,15]
     * 周次完全不相交（逐周交替切换线上线下）。
     *
     * 修复前：v90 无条件合并 → 周次拉平成 3-18 连续区间、教室拼接
     *         「虚拟教室-同步学习平台,*WM1403」、「哪周在哪上课」映射丢失
     *         （第4周线下课详情也显示成线上教室拼接）。
     * 修复后：各自独立成卡，各带各的教室+位图。
     */
    @Test
    fun parse_taskActivity分周切换线上线下_不合并各带教室位图() {
        val bitmapOnline = "0001011110110110111" + "0".repeat(34)  // 周3,5-8,10-11,13-14,16-18
        val bitmapOffline = "0000100001001001" + "0".repeat(37)    // 周4,9,12,15
        val html = """
            <script>
            var unitCount = 11;
            var table0 = new CourseTable(2026, 77);
            var teachers = [{id:3804,name:"郭羽熙",lab:false}];
            var courseName = "大学生心理健康教育(TB206004.J1)";
            activity = new TaskActivity("3804","郭羽熙","61603(TB206004.J1)",courseName,"TB206004.J1","1459","虚拟教室-同步学习平台","$bitmapOnline",null,"","","");
            index = 2*unitCount+4;
            table0.activities[index][table0.activities[index].length] = activity;
            index = 2*unitCount+5;
            table0.activities[index][table0.activities[index].length] = activity;
            var teachers = [{id:3804,name:"郭羽熙",lab:false}];
            var courseName = "大学生心理健康教育(TB206004.J1)";
            activity = new TaskActivity("3804","郭羽熙","61603(TB206004.J1)",courseName,"TB206004.J1","210","*WM1403","$bitmapOffline",null,"","","");
            index = 2*unitCount+4;
            table0.activities[index][table0.activities[index].length] = activity;
            index = 2*unitCount+5;
            table0.activities[index][table0.activities[index].length] = activity;
            </script>
        """.trimIndent()

        val result = parser.parse(html, "2026-2027-1")

        val psyCourses = result.filter { it.name.contains("大学生心理健康教育") && it.dayOfWeek == 3 && it.startNode == 5 }
        assertEquals("周次不相交应拆为两条独立记录", 2, psyCourses.size)

        val onlineCard = psyCourses.find { it.location == "虚拟教室-同步学习平台" }
        val offlineCard = psyCourses.find { it.location == "*WM1403" }
        assertNotNull("线上卡应存在", onlineCard)
        assertNotNull("线下卡应存在", offlineCard)

        // 各带各的位图，周次不拉平，不再标「多班合并」
        assertTrue(onlineCard!!.remark.contains("weeksBitmap:$bitmapOnline"))
        assertTrue(offlineCard!!.remark.contains("weeksBitmap:$bitmapOffline"))
        assertFalse("不应再标多班合并", psyCourses.any { it.remark.contains("多班合并") })

        val onlineDomain = onlineCard.toDomainModel()
        val offlineDomain = offlineCard.toDomainModel()
        // 第3周只有线上卡活跃，第4周只有线下卡活跃
        assertTrue("第3周线上卡应有课", onlineDomain.isWeekInRange(3))
        assertFalse("第3周线下卡应无课", offlineDomain.isWeekInRange(3))
        assertFalse("第4周线上卡应无课", onlineDomain.isWeekInRange(4))
        assertTrue("第4周线下卡应有课", offlineDomain.isWeekInRange(4))
        // 周范围各是各的（修复前被拉平成 3-18 连续）
        assertEquals(3, onlineCard.startWeek)
        assertEquals(18, onlineCard.endWeek)
        assertEquals(4, offlineCard.startWeek)
        assertEquals(15, offlineCard.endWeek)
    }

    // ================ TaskActivity 多教室 + 解析降级合并回退（v120 回归）================

    /**
     * 回归测试：多教室课程整卡丢失 bug（用户报告 2026-09-11，周四人工智能课消失）
     *
     * 计算机与人工智能 周四第5-6节，教务系统挂两个机房教室（逗号连接）：
     *   - 教室参数 "*WM2303机房,*WM2301机房"，位图 3-14 周
     *
     * 修复前：教室分组 [^",]* 不允许逗号 → 主/备正则双双失配 → 教室和位图一起丢失，
     *         课程按默认周 1-16 入库且 remark 无 weeksBitmap，再被合并逻辑丢弃 → 周四近乎空白。
     * 修复后：教室原样解析（含逗号），位图正常提取。
     */
    @Test
    fun parse_taskActivity多教室逗号分隔_教室与位图不丢失() {
        val bitmap = "000" + "1".repeat(12) + "0".repeat(38)  // 3-14周
        val html = """
            <script>
            var unitCount = 11;
            var table0 = new CourseTable(2026, 77);
            var teachers = [{id:1,name:"刘海英",lab:false}];
            var courseName = "计算机与人工智能(TH506001.33)";
            activity = new TaskActivity("1","刘海英","8965(TH506001.33)",courseName,"TH506001.33","800,801","*WM2303机房,*WM2301机房","$bitmap",null,"","","");
            index = 3*unitCount+4;
            table0.activities[index][table0.activities[index].length] = activity;
            index = 3*unitCount+5;
            table0.activities[index][table0.activities[index].length] = activity;
            </script>
        """.trimIndent()

        val result = parser.parse(html, "2026-2027-1")

        val course = result.find { it.name.contains("计算机与人工智能") && it.dayOfWeek == 4 && it.startNode == 5 }
        assertNotNull("人工智能 周四第5-6节应被解析", course)

        assertEquals("多教室应原样保留（含逗号）", "*WM2303机房,*WM2301机房", course!!.location)
        assertEquals(3, course.startWeek)
        assertEquals(14, course.endWeek)
        assertTrue("remark 应包含位图", course.remark.contains("weeksBitmap:$bitmap"))

        val domain = course.toDomainModel()
        assertTrue("第3周（3-14周段）应有课", domain.isWeekInRange(3))
        assertTrue("第14周（3-14周段）应有课", domain.isWeekInRange(14))
        assertFalse("第2周应无课", domain.isWeekInRange(2))
    }

    /**
     * 回归测试：解析降级记录（教室+位图提取失败，按默认周 1-16 入库、remark 无位图）
     * 参与合并时不再被静默丢弃。
     *
     * 场景同 2026-09-11 日志：周四第5-6节两条记录——
     *   - 记录1：位图提取失败（bitmap 参数为空串）→ 降级为默认周 1-16、remark 无 weeksBitmap
     *   - 记录2：仅第 13 周（*WM3305）
     *
     * 修复前：合并只从 remark 位图提周次 → 记录1 贡献为空 → 合并后周次只剩 [13]。
     * 修复后：记录1 回退用 startWeek..endWeek（1-16）参与并集 → 合并后 1-16 周；
     *         空教室被过滤，合并教室列表不再出现 ", *WM3305"。
     */
    @Test
    fun parse_合并时无位图记录回退周次范围_不被丢弃() {
        val bitmapWeek13 = "0".repeat(13) + "1" + "0".repeat(39)  // 仅第13周
        val html = """
            <script>
            var unitCount = 11;
            var table0 = new CourseTable(2026, 77);
            var teachers = [{id:1,name:"刘海英",lab:false}];
            var courseName = "计算机与人工智能(TH506001.33)";
            activity = new TaskActivity("1","刘海英","8965(TH506001.33)",courseName,"TH506001.33","800","*WM2303机房","");
            index = 3*unitCount+4;
            table0.activities[index][table0.activities[index].length] = activity;
            index = 3*unitCount+5;
            table0.activities[index][table0.activities[index].length] = activity;
            var teachers = [{id:1,name:"刘海英",lab:false}];
            var courseName = "计算机与人工智能(TH506001.33)";
            activity = new TaskActivity("1","刘海英","8965(TH506001.33)",courseName,"TH506001.33","801","*WM3305","$bitmapWeek13",null,"","","");
            index = 3*unitCount+4;
            table0.activities[index][table0.activities[index].length] = activity;
            index = 3*unitCount+5;
            table0.activities[index][table0.activities[index].length] = activity;
            </script>
        """.trimIndent()

        val result = parser.parse(html, "2026-2027-1")

        val course = result.find { it.name.contains("计算机与人工智能") && it.dayOfWeek == 4 && it.startNode == 5 }
        assertNotNull("周四第5-6节应有一条合并记录", course)

        // 修复前：合并后周次只剩 [13]；修复后：默认周 1-16 参与并集
        assertEquals(1, course!!.startWeek)
        assertEquals(16, course.endWeek)
        // 空教室被过滤，教室列表干净
        assertEquals("*WM3305", course.location)
        assertTrue("应标注多班合并", course.remark.contains("多班合并"))

        val domain = course.toDomainModel()
        assertTrue("第3周应有课（修复前丢失）", domain.isWeekInRange(3))
        assertTrue("第13周（记录2）应有课", domain.isWeekInRange(13))
        assertFalse("第17周应无课", domain.isWeekInRange(17))
    }

    @Test
    fun `parseSemesterString converts eams label to local semester`() {
        // 标准 label → 本地串
        assertEquals("2025-2026-2", ScheduleHtmlParser.parseSemesterString("2025-2026学年第2学期"))
        assertEquals("2024-2025-1", ScheduleHtmlParser.parseSemesterString("2024-2025学年第1学期"))
        // 在更长文本中也能匹配（parseCurrentWeek 场景）
        assertEquals(
            "2025-2026-2",
            ScheduleHtmlParser.parseSemesterString("本周为 2025-2026学年第2学期的 第1教学周")
        )
        // 非法输入返回 null
        assertNull(ScheduleHtmlParser.parseSemesterString("无效文本"))
        assertNull(ScheduleHtmlParser.parseSemesterString(""))
    }
}
