package com.example.course_schedule_for_chd_v002.service.checkin.polling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [v129] B5 轮询响应手写解析单测（org.json JVM 不可用 → JsonScan 纯 Kotlin）。
 */
class TronClassPollParseTest {

    @Test
    fun `standard rollcalls array parsed with fields`() {
        val json = """{"rollcalls":[{"id":123,"courseId":456,"type":"qr"},{"id":124,"courseId":457}],"other":1}"""
        val result = TronClassPoller.parseOngoingRollcalls(json)
        assertTrue(result is TronClassPollResult.FoundCheckIn)
        val found = result as TronClassPollResult.FoundCheckIn
        assertEquals(2, found.rollcalls.size)
        assertEquals("123", found.rollcalls[0].rollcallId)
        assertEquals("456", found.rollcalls[0].courseId)
        assertEquals("457", found.rollcalls[1].courseId)
    }

    @Test
    fun `empty rollcalls means no active checkin`() {
        val result = TronClassPoller.parseOngoingRollcalls("""{"rollcalls":[]}""")
        assertTrue(result is TronClassPollResult.NoActiveCheckIn)
    }

    @Test
    fun `no rollcalls key no error key means no active`() {
        val result = TronClassPoller.parseOngoingRollcalls("""{"data":{"a":1}}""")
        assertTrue(result is TronClassPollResult.NoActiveCheckIn)
    }

    @Test
    fun `error payload is parse error with raw kept`() {
        val json = """{"error":{"code":401,"message":"expired"}}"""
        val result = TronClassPoller.parseOngoingRollcalls(json)
        assertTrue(result is TronClassPollResult.ParseError)
        assertTrue((result as TronClassPollResult.ParseError).rawJson.contains("expired"))
    }

    @Test
    fun `top level bare array accepted`() {
        val result = TronClassPoller.parseOngoingRollcalls("""[{"id":9,"courseId":8}]""")
        assertTrue(result is TronClassPollResult.FoundCheckIn)
        assertEquals("9", (result as TronClassPollResult.FoundCheckIn).rollcalls[0].rollcallId)
    }

    @Test
    fun `snake_case course_id fallback`() {
        val json = """{"rollcalls":[{"id":1,"course_id":77}]}"""
        val found = TronClassPoller.parseOngoingRollcalls(json) as TronClassPollResult.FoundCheckIn
        assertEquals("77", found.rollcalls[0].courseId)
    }

    @Test
    fun `bracket inside string does not close array`() {
        // 对象字段值里带 ] 与引号转义，配对括号必须跳过字符串内字符
        val json = """{"rollcalls":[{"id":1,"note":"a]b\"c,d"}],"x":2}"""
        val found = TronClassPoller.parseOngoingRollcalls(json) as TronClassPollResult.FoundCheckIn
        assertEquals(1, found.rollcalls.size)
        assertEquals("1", found.rollcalls[0].rollcallId)
    }

    @Test
    fun `empty body is parse error`() {
        assertTrue(TronClassPoller.parseOngoingRollcalls("  ") is TronClassPollResult.ParseError)
    }

    @Test
    fun `null literal value skipped`() {
        val json = """{"rollcalls":[{"id":null,"courseId":5}]}"""
        val found = TronClassPoller.parseOngoingRollcalls(json) as TronClassPollResult.FoundCheckIn
        assertNull(found.rollcalls[0].rollcallId)
        assertEquals("5", found.rollcalls[0].courseId)
    }
}

/**
 * [v129] org（学校租户）解析单测（结构按 2026-10-15 CHD 实测响应校准）。
 */
class TronClassOrgResolverTest {

    @Test
    fun `typical chd results payload parsed`() {
        // 实测响应骨架（脱敏缩减）
        val json = """
            {"results":[{"deliveryOrg":"CHD","apiUrl":"https://course-online.chd.edu.cn",
              "orgName":"长安大学","area":"cn",
              "identityConfig":{"isApiLogin":false,"resource":"tronclass-app",
                "auth-server-url":"https://identity.chd.edu.cn/auth","realm":"chd"}}]}
        """.trimIndent()
        val org = TronClassOrgResolver.parseOrg(json)
        assertNotNull(org)
        assertEquals("https://course-online.chd.edu.cn", org!!.apiUrl)
        assertEquals("长安大学", org.orgName)
        assertEquals("https://identity.chd.edu.cn/auth", org.authServerUrl)
    }

    @Test
    fun `orgs key fallback still works`() {
        val json = """{"orgs":[{"name":"x","apiUrl":"https://w.example.cn"}]}"""
        val org = TronClassOrgResolver.parseOrg(json)
        assertNotNull(org)
        assertEquals("https://w.example.cn", org!!.apiUrl)
    }

    @Test
    fun `no results key returns null`() {
        assertNull(TronClassOrgResolver.parseOrg("""{"error":"x"}"""))
    }

    @Test
    fun `org without apiurl skipped`() {
        val json = """{"results":[{"name":"a"},{"name":"b","apiUrl":"https://b.cn"}]}"""
        val org = TronClassOrgResolver.parseOrg(json)
        assertEquals("https://b.cn", org!!.apiUrl)
    }
}
