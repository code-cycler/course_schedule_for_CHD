package com.example.course_schedule_for_chd_v002.ui.screens.schedule

import com.example.course_schedule_for_chd_v002.data.local.preferences.UserPreferences
import com.example.course_schedule_for_chd_v002.domain.model.SemesterOption
import com.example.course_schedule_for_chd_v002.domain.repository.ICourseRepository
import com.example.course_schedule_for_chd_v002.service.calendar.CalendarSyncService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * ScheduleViewModel 单元测试
 *
 * 重点测 [获取新学期] 链路：fetchRemoteSemesterOptions（筛候选 + 错误传播）、
 * fetchSpecifiedSemester（onDone 回调 + 0 课程 + 失败）、clearAllSchedules。
 *
 * 候选筛选的纯函数逻辑（inferCurrentSemester/candidateSemesters）见 SemesterInferenceTest。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: ICourseRepository
    private lateinit var userPreferences: UserPreferences
    private lateinit var calendarSyncService: CalendarSyncService
    private lateinit var viewModel: ScheduleViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        userPreferences = mockk(relaxed = true)
        calendarSyncService = mockk(relaxed = true)
        // init 会跑 loadCampus/loadSchedule/loadReminderSettings；relaxed mock 让它们跑空不崩
        viewModel = ScheduleViewModel(repository, userPreferences, "2025-2026-2", calendarSyncService)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // ================ fetchRemoteSemesterOptions ================

    @Test
    fun `fetchRemoteSemesterOptions success updates state and clears error`() = runTest {
        coEvery { repository.getRemoteSemesterOptions() } returns Result.success(
            listOf(SemesterOption("242", "2025-2026学年第2学期"))
        )
        viewModel.fetchRemoteSemesterOptions()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isFetchingSemester)
        assertNull(state.fetchSemesterError)
        // candidateSemesters 上限 4
        assertTrue("候选数应 <= 4", state.remoteSemesterOptions.size <= 4)
    }

    @Test
    fun `fetchRemoteSemesterOptions filters out history semesters beyond candidate window`() = runTest {
        // 教务返回全部学期（含远历史）；候选只取当前±2学年，远历史不应出现
        coEvery { repository.getRemoteSemesterOptions() } returns Result.success(listOf(
            SemesterOption("80", "2019-2020学年第1学期"),   // 远历史
            SemesterOption("82", "2020-2021学年第1学期"),   // 远历史
            SemesterOption("242", "2025-2026学年第2学期"),  // 当前附近
            SemesterOption("262", "2026-2027学年第1学期")   // 当前附近
        ))
        viewModel.fetchRemoteSemesterOptions()
        advanceUntilIdle()

        val labels = viewModel.uiState.value.remoteSemesterOptions.map { it.label }
        assertTrue(
            "远历史学期不应出现在候选",
            labels.none { it.contains("2019-2020") || it.contains("2020-2021") }
        )
    }

    @Test
    fun `fetchRemoteSemesterOptions failure sets error`() = runTest {
        coEvery { repository.getRemoteSemesterOptions() } returns Result.failure(Exception("登录已过期"))
        viewModel.fetchRemoteSemesterOptions()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isFetchingSemester)
        assertEquals("登录已过期", state.fetchSemesterError)
    }

    // ================ fetchSpecifiedSemester ================

    @Test
    fun `fetchSpecifiedSemester success triggers onDone with count`() = runTest {
        coEvery { repository.fetchSpecifiedSemester("222", "2025-2026-1") } returns Result.success(5)

        var doneSemester: String? = null
        var doneCount: Int? = null
        viewModel.fetchSpecifiedSemester("222", "2025-2026-1") { sem, count ->
            doneSemester = sem; doneCount = count
        }
        advanceUntilIdle()

        assertEquals("2025-2026-1", doneSemester)
        assertEquals(5, doneCount)
    }

    @Test
    fun `fetchSpecifiedSemester zero courses sets error and skips onDone`() = runTest {
        coEvery { repository.fetchSpecifiedSemester(any(), any()) } returns Result.success(0)

        var called = false
        viewModel.fetchSpecifiedSemester("222", "2025-2026-1") { _, _ -> called = true }
        advanceUntilIdle()

        assertFalse(called)
        assertEquals("该学期暂无课程", viewModel.uiState.value.fetchSemesterError)
    }

    @Test
    fun `fetchSpecifiedSemester failure sets error and skips onDone`() = runTest {
        coEvery { repository.fetchSpecifiedSemester(any(), any()) } returns Result.failure(Exception("登录已过期"))

        var called = false
        viewModel.fetchSpecifiedSemester("222", "2025-2026-1") { _, _ -> called = true }
        advanceUntilIdle()

        assertFalse(called)
        assertEquals("登录已过期", viewModel.uiState.value.fetchSemesterError)
    }

    // ================ clearAllSchedules ================

    @Test
    fun `clearAllSchedules calls repository clearAllSchedules`() = runTest {
        viewModel.clearAllSchedules()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.clearAllSchedules() }
    }
}
