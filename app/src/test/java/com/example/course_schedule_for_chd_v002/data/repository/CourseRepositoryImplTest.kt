package com.example.course_schedule_for_chd_v002.data.repository

import com.example.course_schedule_for_chd_v002.data.local.database.CourseDao
import com.example.course_schedule_for_chd_v002.data.local.database.entity.CourseEntity
import com.example.course_schedule_for_chd_v002.data.local.preferences.UserPreferences
import com.example.course_schedule_for_chd_v002.data.remote.api.EamsApi
import com.example.course_schedule_for_chd_v002.data.remote.client.CookieManager
import com.example.course_schedule_for_chd_v002.data.remote.parser.ScheduleHtmlParser
import com.example.course_schedule_for_chd_v002.domain.model.Course
import com.example.course_schedule_for_chd_v002.domain.model.CourseType
import com.example.course_schedule_for_chd_v002.domain.model.DayOfWeek
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * CourseRepositoryImpl 单元测试
 * 测试 Repository 层的业务逻辑整合
 *
 * 注：账号密码表单登录（login/CasApi/LoginResult）已随 LoginScreen 死代码一并移除，
 * 对应测试同步删除。WebView 登录路径由 WebViewScreen + LoginViewModel.onCasLoginSuccess 覆盖。
 */
class CourseRepositoryImplTest {

    // Mock 依赖
    private lateinit var mockEamsApi: EamsApi
    private lateinit var mockCookieManager: CookieManager
    private lateinit var mockHtmlParser: ScheduleHtmlParser
    private lateinit var mockUserPreferences: UserPreferences
    private lateinit var mockCourseDao: CourseDao

    // 被测试对象
    private lateinit var repository: CourseRepositoryImpl

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        mockEamsApi = mockk()
        mockCookieManager = mockk()
        mockHtmlParser = mockk()
        mockUserPreferences = mockk(relaxed = true)
        mockCourseDao = mockk()

        repository = CourseRepositoryImpl(
            eamsApi = mockEamsApi,
            cookieManager = mockCookieManager,
            htmlParser = mockHtmlParser,
            userPreferences = mockUserPreferences,
            courseDao = mockCourseDao
        )
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    // ================ 登录状态检查测试 ================

    @Test
    fun isLoggedIn_allChecksPass_returnsTrue() = runTest {
        // Given
        every { mockUserPreferences.isLoggedIn } returns flowOf(true)
        every { mockCookieManager.hasSessionCookie() } returns true
        coEvery { mockEamsApi.accessHomePage() } returns Result.success(true)

        // When
        val result = repository.isLoggedIn()

        // Then
        assertTrue(result)
    }

    @Test
    fun isLoggedIn_savedStateFalse_returnsFalse() = runTest {
        // Given
        every { mockUserPreferences.isLoggedIn } returns flowOf(false)

        // When
        val result = repository.isLoggedIn()

        // Then
        assertFalse(result)
    }

    @Test
    fun isLoggedIn_noSessionCookie_returnsFalse() = runTest {
        // Given
        every { mockUserPreferences.isLoggedIn } returns flowOf(true)
        every { mockCookieManager.hasSessionCookie() } returns false

        // When
        val result = repository.isLoggedIn()

        // Then
        assertFalse(result)
    }

    @Test
    fun isLoggedIn_serverVerificationFails_returnsFalse() = runTest {
        // Given
        every { mockUserPreferences.isLoggedIn } returns flowOf(true)
        every { mockCookieManager.hasSessionCookie() } returns true
        coEvery { mockEamsApi.accessHomePage() } returns Result.success(false)

        // When
        val result = repository.isLoggedIn()

        // Then
        assertFalse(result)
    }

    // ================ 登出测试 ================

    @Test
    fun logout_clearsAllState() = runTest {
        // Given
        every { mockCookieManager.clearAll() } just Runs
        coEvery { mockUserPreferences.clear() } just Runs

        // When
        repository.logout()

        // Then
        verify { mockCookieManager.clearAll() }
        coVerify { mockUserPreferences.clear() }
    }

    // ================ 获取远程课表测试 ================

    @Test
    fun fetchRemoteSchedule_success_returnsCourses() = runTest {
        // Given
        val semester = "2024-2025-1"
        val html = "<table><tr><td>课程</td></tr></table>"
        val entities = listOf(
            createTestCourseEntity(name = "高等数学"),
            createTestCourseEntity(name = "大学英语")
        )

        every { mockUserPreferences.isLoggedIn } returns flowOf(true)
        every { mockCookieManager.hasSessionCookie() } returns true
        coEvery { mockEamsApi.accessHomePage() } returns Result.success(true)
        coEvery { mockEamsApi.getCourseTableHtml(semester) } returns Result.success(html)
        every { mockHtmlParser.parse(html, semester) } returns entities
        coEvery { mockCourseDao.deleteBySemester(semester) } just Runs
        coEvery { mockCourseDao.insertAll(any<List<CourseEntity>>()) } just Runs
        coEvery { mockUserPreferences.saveCurrentSemester(semester) } just Runs

        // When
        val result = repository.fetchRemoteSchedule(semester)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.size)
    }

    @Test
    fun fetchRemoteSchedule_getHtmlFailure_returnsFailure() = runTest {
        // Given
        every { mockUserPreferences.isLoggedIn } returns flowOf(true)
        every { mockCookieManager.hasSessionCookie() } returns true
        coEvery { mockEamsApi.accessHomePage() } returns Result.success(true)
        coEvery { mockEamsApi.getCourseTableHtml(any()) } returns Result.failure(Exception("Network error"))

        // When
        val result = repository.fetchRemoteSchedule("2024-2025-1")

        // Then
        assertTrue(result.isFailure)
    }

    @Test
    fun fetchRemoteSchedule_savesToDatabase() = runTest {
        // Given
        val semester = "2024-2025-1"
        val html = "<table><tr><td>课程</td></tr></table>"
        val entities = listOf(createTestCourseEntity(name = "高等数学"))

        every { mockUserPreferences.isLoggedIn } returns flowOf(true)
        every { mockCookieManager.hasSessionCookie() } returns true
        coEvery { mockEamsApi.accessHomePage() } returns Result.success(true)
        coEvery { mockEamsApi.getCourseTableHtml(semester) } returns Result.success(html)
        every { mockHtmlParser.parse(html, semester) } returns entities
        coEvery { mockCourseDao.deleteBySemester(semester) } just Runs
        coEvery { mockCourseDao.insertAll(any<List<CourseEntity>>()) } just Runs
        coEvery { mockUserPreferences.saveCurrentSemester(semester) } just Runs

        // When
        repository.fetchRemoteSchedule(semester)

        // Then
        coVerify { mockCourseDao.deleteBySemester(semester) }
        coVerify { mockCourseDao.insertAll(entities) }
        coVerify { mockUserPreferences.saveCurrentSemester(semester) }
    }

    // ================ 获取本地课表测试 ================

    @Test
    fun getLocalSchedule_returnsCoursesFromDao() = runTest {
        // Given
        val entities = listOf(
            createTestCourseEntity(name = "高等数学", semester = "2024-2025-1"),
            createTestCourseEntity(name = "大学英语", semester = "2024-2025-1")
        )

        coEvery { mockCourseDao.getBySemester("2024-2025-1") } returns entities

        // When
        val result = repository.getLocalSchedule("2024-2025-1")

        // Then
        assertEquals(2, result.size)
        assertEquals("高等数学", result[0].name)
        assertEquals("大学英语", result[1].name)
    }

    @Test
    fun getLocalSchedule_emptyResult_returnsEmptyList() = runTest {
        // Given
        coEvery { mockCourseDao.getBySemester(any()) } returns emptyList()

        // When
        val result = repository.getLocalSchedule("2024-2025-1")

        // Then
        assertTrue(result.isEmpty())
    }

    // ================ 保存课表测试 ================

    @Test
    fun saveSchedule_convertsAndSavesEntities() = runTest {
        // Given
        val courses = listOf(
            createTestCourse(name = "高等数学"),
            createTestCourse(name = "大学英语")
        )
        coEvery { mockCourseDao.insertAll(any<List<CourseEntity>>()) } just Runs

        // When
        repository.saveSchedule(courses)

        // Then
        coVerify { mockCourseDao.insertAll(any<List<CourseEntity>>()) }
    }

    // ================ 删除课表测试 ================

    @Test
    fun deleteSchedule_callsDaoDelete() = runTest {
        // Given
        val semester = "2024-2025-1"
        coEvery { mockCourseDao.deleteBySemester(semester) } just Runs

        // When
        repository.deleteSchedule(semester)

        // Then
        coVerify { mockCourseDao.deleteBySemester(semester) }
    }

    // ================ 获取所有学期测试 ================

    @Test
    fun getAllSemesters_returnsFromDao() = runTest {
        // Given
        val semesters = listOf("2024-2025-1", "2024-2025-2", "2023-2024-2")
        coEvery { mockCourseDao.getAllSemesters() } returns semesters

        // When
        val result = repository.getAllSemesters()

        // Then
        assertEquals(3, result.size)
        assertEquals(semesters, result)
    }

    // ================ 辅助方法 ================

    private fun createTestCourseEntity(
        id: Long = 0,
        name: String = "测试课程",
        teacher: String = "张老师",
        location: String = "A101",
        dayOfWeek: Int = 1,
        startWeek: Int = 1,
        endWeek: Int = 16,
        startNode: Int = 1,
        endNode: Int = 2,
        courseType: String = "必修",
        credit: Double = 2.0,
        remark: String = "",
        semester: String = "2024-2025-1"
    ): CourseEntity {
        return CourseEntity(
            id = id,
            name = name,
            teacher = teacher,
            location = location,
            dayOfWeek = dayOfWeek,
            startWeek = startWeek,
            endWeek = endWeek,
            startNode = startNode,
            endNode = endNode,
            courseType = courseType,
            credit = credit,
            remark = remark,
            semester = semester
        )
    }

    private fun createTestCourse(
        id: Long = 0,
        name: String = "测试课程",
        teacher: String = "张老师",
        location: String = "A101",
        dayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
        startWeek: Int = 1,
        endWeek: Int = 16,
        startNode: Int = 1,
        endNode: Int = 2,
        courseType: CourseType = CourseType.REQUIRED,
        credit: Double = 2.0,
        remark: String = "",
        semester: String = "2024-2025-1"
    ): Course {
        return Course(
            id = id,
            name = name,
            teacher = teacher,
            location = location,
            dayOfWeek = dayOfWeek,
            startWeek = startWeek,
            endWeek = endWeek,
            startNode = startNode,
            endNode = endNode,
            courseType = courseType,
            credit = credit,
            remark = remark,
            semester = semester
        )
    }
}
