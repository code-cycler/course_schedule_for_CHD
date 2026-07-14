package com.example.course_schedule_for_chd_v002.data.remote.api

import com.example.course_schedule_for_chd_v002.data.remote.parser.ScheduleHtmlParser
import com.example.course_schedule_for_chd_v002.domain.model.SemesterOption
import org.junit.Assert.*
import org.junit.Test

/**
 * EamsApi 单元测试
 *
 * 重点测 [获取新学期] 的 dataQuery.action 响应解析（纯函数 [EamsApi.parseSemesterOptionsResponse]）。
 * 该响应是 JS 对象字面量（key 无引号、yearDom/termDom 含 HTML 引号），非标准 JSON，
 * 用正则提 (id,schoolYear,name) 三元组。
 */
class EamsApiTest {

    /** 2026-07-12 MCP 实测的真实响应样本（节选，含 yearDom/termDom 的 HTML 引号干扰） */
    private val realResponse = """{yearDom:"<tr><td class='calendar-bar-td-blankBorder' index='0'>2015-2016</td><td index='8'>2026-2027</td></tr>",termDom:"<tr><td class='calendar-bar-td-blankBorder' val='222'>学期<span>1</span></td></tr>",semesters:{y0:[{id:72,schoolYear:"2015-2016",name:"1"},{id:73,schoolYear:"2015-2016",name:"2"}],y6:[{id:202,schoolYear:"2024-2025",name:"1"},{id:203,schoolYear:"2024-2025",name:"2"}],y7:[{id:222,schoolYear:"2025-2026",name:"1"},{id:242,schoolYear:"2025-2026",name:"2"}],y8:[{id:262,schoolYear:"2026-2027",name:"1"}]},yearIndex:"7",termIndex:"1",semesterId:"242"}"""

    @Test
    fun `parseSemesterOptionsResponse parses standard dataQuery response`() {
        val result = EamsApi.parseSemesterOptionsResponse(realResponse)
        // y0(2) + y6(2) + y7(2) + y8(1) = 7
        assertEquals(7, result.size)
        assertEquals(SemesterOption("72", "2015-2016学年第1学期"), result[0])
        assertEquals(SemesterOption("242", "2025-2026学年第2学期"), result[5])
        assertEquals(SemesterOption("262", "2026-2027学年第1学期"), result[6])
    }

    @Test
    fun `parseSemesterOptionsResponse returns empty for login page (cookie expired)`() {
        val loginPage = "<html><title>统一身份认证</title>请登录</html>"
        assertTrue(EamsApi.parseSemesterOptionsResponse(loginPage).isEmpty())
    }

    @Test
    fun `parseSemesterOptionsResponse survives html quotes in yearDom`() {
        // yearDom 含 HTML 单引号、termDom 含 <span>，都不应破坏正则
        val body = """{yearDom:"<tr><td class='x' index='0'>2025-2026</td></tr>",semesters:{y0:[{id:262,schoolYear:"2026-2027",name:"1"}]},semesterId:"262"}"""
        val result = EamsApi.parseSemesterOptionsResponse(body)
        assertEquals(1, result.size)
        assertEquals("262", result[0].remoteId)
        assertEquals("2026-2027学年第1学期", result[0].label)
    }

    @Test
    fun `parseSemesterOptionsResponse returns empty for empty body`() {
        assertTrue(EamsApi.parseSemesterOptionsResponse("").isEmpty())
    }

    @Test
    fun `parseSemesterOptionsResponse label is long format parseable by parseSemesterString`() {
        // label 必须是 "YYYY-YYYY学年第N学期" 长格式，才能被 parseSemesterString 转成本地短串
        val result = EamsApi.parseSemesterOptionsResponse(realResponse)
        val parsed = ScheduleHtmlParser.parseSemesterString(result.first().label)
        assertNotNull(parsed)
        assertEquals("2015-2016-1", parsed)
    }
}
