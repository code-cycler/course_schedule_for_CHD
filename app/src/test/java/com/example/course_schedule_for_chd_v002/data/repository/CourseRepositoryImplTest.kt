package com.example.course_schedule_for_chd_v002.data.repository

import com.example.course_schedule_for_chd_v002.data.local.database.CourseDao
import com.example.course_schedule_for_chd_v002.data.local.database.entity.CourseEntity
import com.example.course_schedule_for_chd_v002.data.local.preferences.UserPreferences
import com.example.course_schedule_for_chd_v002.data.remote.api.CasApi
import com.example.course_schedule_for_chd_v002.data.remote.api.EamsApi
import com.example.course_schedule_for_chd_v002.data.remote.client.CookieManager
import com.example.course_schedule_for_chd_v002.data.remote.parser.ScheduleHtmlParser
import com.example.course_schedule_for_chd_v002.domain.model.SemesterOption
import com.example.course_schedule_for_chd_v002.util.TestDataFactory
import io.mockk.Ordering
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.just
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * CourseRepositoryImpl 单元测试
 *
 * 重点测 [获取新学期] 链路：
 * - getRemoteSemesterOptions（label 过滤、failure 传播）
 * - fetchSpecifiedSemester（成功入库 / Cookie 过期 / 真无课 / eamsApi 失败）
 * - clearAllSchedules
 */
class CourseRepositoryImplTest {

    private lateinit var casApi: CasApi
    private lateinit var eamsApi: EamsApi
    private lateinit var cookieManager: CookieManager
    private lateinit var htmlParser: ScheduleHtmlParser
    private lateinit var userPreferences: UserPreferences
    private lateinit var courseDao: CourseDao
    private lateinit var repository: CourseRepositoryImpl

    @Before
    fun setup() {
        casApi = mockk(relaxed = true)
        eamsApi = mockk(relaxed = true)
        cookieManager = mockk(relaxed = true)
        htmlParser = mockk(relaxed = true)
        userPreferences = mockk(relaxed = true)
        courseDao = mockk(relaxed = true)
        repository = CourseRepositoryImpl(casApi, eamsApi, cookieManager, htmlParser, userPreferences, courseDao)
    }

    // ================ getRemoteSemesterOptions ================

    @Test
    fun `getRemoteSemesterOptions filters out non-parseable labels`() = runTest {
        coEvery { eamsApi.getSemesterOptions() } returns Result.success(listOf(
            SemesterOption("222", "2025-2026学年第1学期"),  // 标准学期，保留
            SemesterOption("202", "2024-2025学年第2学期"),  // 标准学期，保留
            SemesterOption("99", "小学期"),                  // 非标准，过滤
            SemesterOption("100", "暑期学校")               // 非标准，过滤
        ))
        val result = repository.getRemoteSemesterOptions()
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()!!.size)
    }

    @Test
    fun `getRemoteSemesterOptions propagates eamsApi failure`() = runTest {
        coEvery { eamsApi.getSemesterOptions() } returns Result.failure(Exception("登录已过期"))
        val result = repository.getRemoteSemesterOptions()
        assertTrue(result.isFailure)
        assertEquals("登录已过期", result.exceptionOrNull()!!.message)
    }

    @Test
    fun `getRemoteSemesterOptions returns empty when eamsApi returns empty`() = runTest {
        coEvery { eamsApi.getSemesterOptions() } returns Result.success(emptyList())
        val result = repository.getRemoteSemesterOptions()
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.isEmpty())
    }

    // ================ clearAllSchedules ================

    @Test
    fun `clearAllSchedules calls courseDao deleteAll`() = runTest {
        repository.clearAllSchedules()
        coVerify(exactly = 1) { courseDao.deleteAll() }
    }

    // ================ fetchSpecifiedSemester ================

    @Test
    fun `fetchSpecifiedSemester success parses html, replaces and returns count`() = runTest {
        val html = "<html>课表 TaskActivity table0 内容</html>"
        val local = "2025-2026-1"
        coEvery { eamsApi.getCourseTableHtml("222", null) } returns Result.success(html)
        val entity = CourseEntity.fromDomainModel(
            TestDataFactory.createCourse(name = "Math", semester = local)
        )
        every { htmlParser.parse(html, local) } returns listOf(entity)
        coEvery { courseDao.deleteBySemester(local) } just Runs
        coEvery { courseDao.insertAll(any()) } just Runs

        val result = repository.fetchSpecifiedSemester("222", local)

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())
        coVerify(ordering = Ordering.ORDERED) {
            courseDao.deleteBySemester(local)
            courseDao.insertAll(any())
        }
        coVerify { userPreferences.saveCurrentSemester(local) }
    }

    @Test
    fun `fetchSpecifiedSemester returns failure when cookie expired (login page)`() = runTest {
        val html = "<html>请登录 cas 统一身份认证</html>"
        coEvery { eamsApi.getCourseTableHtml("222", null) } returns Result.success(html)
        every { htmlParser.parse(html, any()) } returns emptyList()

        val result = repository.fetchSpecifiedSemester("222", "2025-2026-1")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("登录已过期"))
        coVerify(exactly = 0) { courseDao.insertAll(any()) }
    }

    @Test
    fun `fetchSpecifiedSemester returns success 0 when semester genuinely has no courses`() = runTest {
        // 课表页结构正常（含 TaskActivity/table0），但该学期确实无课
        val html = "<html>课表 TaskActivity table0 空课表</html>"
        coEvery { eamsApi.getCourseTableHtml("222", null) } returns Result.success(html)
        every { htmlParser.parse(html, any()) } returns emptyList()

        val result = repository.fetchSpecifiedSemester("222", "2025-2026-1")

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull())
        coVerify(exactly = 0) { courseDao.insertAll(any()) }
    }

    @Test
    fun `fetchSpecifiedSemester returns failure when eamsApi fails`() = runTest {
        coEvery { eamsApi.getCourseTableHtml(any(), any()) } returns Result.failure(Exception("网络错误"))

        val result = repository.fetchSpecifiedSemester("222", "2025-2026-1")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { courseDao.insertAll(any()) }
    }
}
