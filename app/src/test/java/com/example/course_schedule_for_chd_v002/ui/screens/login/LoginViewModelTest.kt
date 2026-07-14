package com.example.course_schedule_for_chd_v002.ui.screens.login

import com.example.course_schedule_for_chd_v002.data.local.preferences.UserPreferences
import com.example.course_schedule_for_chd_v002.domain.repository.ICourseRepository
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * LoginViewModel 单元测试
 *
 * 重点测 [defaultSemester 修复]：onCasLoginSuccess 应把课程存入首页解析出的真实学期，
 * 而非硬编码 "2024-2025-1"。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: ICourseRepository
    private lateinit var userPreferences: UserPreferences
    private lateinit var viewModel: LoginViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        userPreferences = mockk(relaxed = true)
        viewModel = LoginViewModel(repository, userPreferences)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `onCasLoginSuccess uses real semester from homePage for storage key`() = runTest {
        val courseHtml = "<html>课表</html>"
        val homeHtml = "<html>本周为第19教学周 2025-2026学年第2学期</html>"
        every { repository.parseCurrentWeekFromHtml(homeHtml) } returns ("2025-2026-2" to 19)
        coEvery { repository.parseHtmlToCourses(any(), any()) } returns Result.success(emptyList())

        viewModel.onCasLoginSuccess(courseHtml, homeHtml)
        advanceUntilIdle()

        // 关键：课程应存入真实学期 "2025-2026-2"，而非硬编码 "2024-2025-1"
        coVerify { repository.parseHtmlToCourses(courseHtml, "2025-2026-2") }
        coVerify { userPreferences.saveCurrentSemester("2025-2026-2") }
        assertEquals("2025-2026-2", viewModel.uiState.first().currentSemester)
    }

    @Test
    fun `onCasLoginSuccess saves week and semesterStartDate when homePage parsed`() = runTest {
        val homeHtml = "<html>本周为第19教学周 2025-2026学年第2学期</html>"
        every { repository.parseCurrentWeekFromHtml(homeHtml) } returns ("2025-2026-2" to 19)
        coEvery { repository.parseHtmlToCourses(any(), any()) } returns Result.success(emptyList())

        viewModel.onCasLoginSuccess("<html>课表</html>", homeHtml)
        advanceUntilIdle()

        coVerify { userPreferences.saveCurrentWeek(19) }
        coVerify { userPreferences.saveCurrentSemester("2025-2026-2") }
        coVerify { userPreferences.saveLastParsedWeek(19) }
    }

    @Test
    fun `onCasLoginSuccess falls back to default when homePage null`() = runTest {
        coEvery { repository.parseHtmlToCourses(any(), any()) } returns Result.success(emptyList())

        viewModel.onCasLoginSuccess("<html>课表</html>", null)
        advanceUntilIdle()

        // 首页 null 时回退到默认学期，且 currentSemester 与入库 key 一致
        coVerify { repository.parseHtmlToCourses(any(), "2024-2025-1") }
        coVerify { userPreferences.saveCurrentSemester("2024-2025-1") }
    }

    @Test
    fun `onCasLoginSuccess falls back when homePage lacks week info`() = runTest {
        val homeHtml = "<html>无教学周信息</html>"
        every { repository.parseCurrentWeekFromHtml(homeHtml) } returns null
        coEvery { repository.parseHtmlToCourses(any(), any()) } returns Result.success(emptyList())

        viewModel.onCasLoginSuccess("<html>课表</html>", homeHtml)
        advanceUntilIdle()

        coVerify { repository.parseHtmlToCourses(any(), "2024-2025-1") }
        coVerify { userPreferences.saveCurrentSemester("2024-2025-1") }
    }

    @Test
    fun `onCasLoginSuccess emits navigate event on success`() = runTest {
        every { repository.parseCurrentWeekFromHtml(any()) } returns ("2025-2026-2" to 19)
        coEvery { repository.parseHtmlToCourses(any(), any()) } returns Result.success(emptyList())

        // turbine 先订阅，避免 SharedFlow(replay=0) 在订阅前 tryEmit 丢值
        viewModel.navigateBackEvent.test {
            viewModel.onCasLoginSuccess("<html>课表</html>", "<html>home</html>")
            assertNotNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onCasLoginSuccess parse failure shows error and no navigate`() = runTest {
        every { repository.parseCurrentWeekFromHtml(any()) } returns ("2025-2026-2" to 19)
        coEvery { repository.parseHtmlToCourses(any(), any()) } returns Result.failure(Exception("解析失败"))

        viewModel.navigateBackEvent.test {
            viewModel.onCasLoginSuccess("<html>坏课表</html>", "<html>home</html>")
            expectNoEvents()
        }
        assertNotNull(viewModel.uiState.first().errorMessage)
    }
}
