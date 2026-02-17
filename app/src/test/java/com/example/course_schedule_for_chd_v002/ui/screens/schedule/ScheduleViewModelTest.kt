package com.example.course_schedule_for_chd_v002.ui.screens.schedule

import com.example.course_schedule_for_chd_v002.domain.repository.ICourseRepository
import com.example.course_schedule_for_chd_v002.util.TestDataFactory
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * ScheduleViewModel 单元测试
 * 遵循 TDD 开发规范，测试先行
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var mockRepository: ICourseRepository
    private lateinit var viewModel: ScheduleViewModel

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        mockRepository = mockk()
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // ================ 初始化测试 ================

    @Test
    fun `init loads schedule for given semester`() = runTest {
        // Given
        val semester = "2024-2025-1"
        val courses = listOf(
            TestDataFactory.createCourse(name = "Math", semester = semester)
        )
        coEvery { mockRepository.getLocalSchedule(semester) } returns courses

        // When
        viewModel = ScheduleViewModel(mockRepository, semester)
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.courses.size)
        assertEquals(semester, state.semester)
    }

    @Test
    fun `init with empty schedule shows empty state`() = runTest {
        // Given
        val semester = "2024-2025-1"
        coEvery { mockRepository.getLocalSchedule(semester) } returns emptyList()

        // When
        viewModel = ScheduleViewModel(mockRepository, semester)
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.courses.isEmpty())
    }

    // ================ 周次选择测试 ================

    @Test
    fun `onWeekSelected updates currentWeek`() = runTest {
        // Given
        coEvery { mockRepository.getLocalSchedule(any()) } returns emptyList()
        viewModel = ScheduleViewModel(mockRepository, "2024-2025-1")
        advanceUntilIdle()

        // When
        viewModel.onWeekSelected(5)

        // Then
        assertEquals(5, viewModel.uiState.value.currentWeek)
    }

    @Test
    fun `onWeekSelected filters courses for that week`() = runTest {
        // Given
        val courses = listOf(
            TestDataFactory.createCourse(name = "Course1", startWeek = 1, endWeek = 8),
            TestDataFactory.createCourse(name = "Course2", startWeek = 9, endWeek = 16)
        )
        coEvery { mockRepository.getLocalSchedule(any()) } returns courses
        viewModel = ScheduleViewModel(mockRepository, "2024-2025-1")
        advanceUntilIdle()

        // When
        viewModel.onWeekSelected(5)

        // Then
        val displayedCourses = viewModel.uiState.value.getDisplayCourses()
        assertEquals(1, displayedCourses.size)
        assertEquals("Course1", displayedCourses[0].name)
    }

    @Test
    fun `onWeekSelected with week10 filters correctly`() = runTest {
        // Given
        val courses = listOf(
            TestDataFactory.createCourse(name = "Course1", startWeek = 1, endWeek = 8),
            TestDataFactory.createCourse(name = "Course2", startWeek = 9, endWeek = 16)
        )
        coEvery { mockRepository.getLocalSchedule(any()) } returns courses
        viewModel = ScheduleViewModel(mockRepository, "2024-2025-1")
        advanceUntilIdle()

        // When
        viewModel.onWeekSelected(10)

        // Then
        val displayedCourses = viewModel.uiState.value.getDisplayCourses()
        assertEquals(1, displayedCourses.size)
        assertEquals("Course2", displayedCourses[0].name)
    }

    @Test
    fun `onWeekSelected clamps week to valid range`() = runTest {
        // Given
        coEvery { mockRepository.getLocalSchedule(any()) } returns emptyList()
        viewModel = ScheduleViewModel(mockRepository, "2024-2025-1")
        advanceUntilIdle()

        // When - try to set week below minimum
        viewModel.onWeekSelected(0)

        // Then
        assertEquals(1, viewModel.uiState.value.currentWeek)

        // When - try to set week above maximum
        viewModel.onWeekSelected(20)

        // Then
        assertEquals(16, viewModel.uiState.value.currentWeek)
    }

    // ================ 刷新课表测试 ================

    @Test
    fun `refreshSchedule fetches remote data and updates state`() = runTest {
        // Given
        val semester = "2024-2025-1"
        val remoteCourses = listOf(
            TestDataFactory.createCourse(name = "Remote Course", semester = semester)
        )
        coEvery { mockRepository.getLocalSchedule(semester) } returns emptyList()
        coEvery { mockRepository.fetchRemoteSchedule(semester) } returns Result.success(remoteCourses)

        viewModel = ScheduleViewModel(mockRepository, semester)
        advanceUntilIdle()

        // When
        viewModel.refreshSchedule()
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertFalse(state.isRefreshing)
        coVerify { mockRepository.fetchRemoteSchedule(semester) }
    }

    @Test
    fun `refreshSchedule with network error shows error message`() = runTest {
        // Given
        val semester = "2024-2025-1"
        coEvery { mockRepository.getLocalSchedule(semester) } returns emptyList()
        coEvery { mockRepository.fetchRemoteSchedule(semester) } returns Result.failure(Exception("Network error"))

        viewModel = ScheduleViewModel(mockRepository, semester)
        advanceUntilIdle()

        // When
        viewModel.refreshSchedule()
        advanceUntilIdle()

        // Then - 验证友好的错误消息
        val state = viewModel.uiState.value
        assertTrue(state.errorMessage?.contains("Network connection error") == true)
    }

    @Test
    fun `refreshSchedule sets isRefreshing state correctly`() = runTest {
        // Given
        val semester = "2024-2025-1"
        coEvery { mockRepository.getLocalSchedule(semester) } returns emptyList()
        coEvery { mockRepository.fetchRemoteSchedule(semester) } coAnswers {
            delay(100)
            Result.success(emptyList())
        }

        viewModel = ScheduleViewModel(mockRepository, semester)
        advanceUntilIdle()

        // When
        viewModel.refreshSchedule()

        // Then - check refreshing state during refresh
        assertTrue(viewModel.uiState.value.isRefreshing)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    // ================ 登出测试 ================

    @Test
    fun `logout calls repository logout and updates state`() = runTest {
        // Given
        coEvery { mockRepository.getLocalSchedule(any()) } returns emptyList()
        coEvery { mockRepository.logout() } just Runs

        viewModel = ScheduleViewModel(mockRepository, "2024-2025-1")
        advanceUntilIdle()

        // When
        viewModel.logout()
        advanceUntilIdle()

        // Then
        coVerify { mockRepository.logout() }
        assertTrue(viewModel.uiState.value.isLoggedOut)
    }

    // ================ 课程筛选测试 ================

    @Test
    fun `getDisplayCourses filters by currentWeek correctly`() = runTest {
        // Given
        val courses = listOf(
            TestDataFactory.createCourse(
                name = "Monday Course",
                startWeek = 1,
                endWeek = 16,
                startNode = 1,
                endNode = 2
            ),
            TestDataFactory.createCourse(
                name = "Tuesday Course",
                startWeek = 1,
                endWeek = 8,
                startNode = 3,
                endNode = 4
            ),
            TestDataFactory.createCourse(
                name = "Week 9-16 Course",
                startWeek = 9,
                endWeek = 16,
                startNode = 1,
                endNode = 2
            )
        )
        coEvery { mockRepository.getLocalSchedule(any()) } returns courses
        viewModel = ScheduleViewModel(mockRepository, "2024-2025-1")
        advanceUntilIdle()

        // When
        viewModel.onWeekSelected(5)

        // Then
        val displayed = viewModel.uiState.value.getDisplayCourses()
        assertEquals(2, displayed.size) // Monday Course + Tuesday Course
    }

    @Test
    fun `getDisplayCourses returns empty for week with no courses`() = runTest {
        // Given
        val courses = listOf(
            TestDataFactory.createCourse(startWeek = 1, endWeek = 8)
        )
        coEvery { mockRepository.getLocalSchedule(any()) } returns courses
        viewModel = ScheduleViewModel(mockRepository, "2024-2025-1")
        advanceUntilIdle()

        // When
        viewModel.onWeekSelected(10)

        // Then
        val displayed = viewModel.uiState.value.getDisplayCourses()
        assertTrue(displayed.isEmpty())
    }

    // ================ 课程选择测试 ================

    @Test
    fun `onCourseSelected updates selectedCourse`() = runTest {
        // Given
        val course = TestDataFactory.createCourse(name = "Selected Course")
        coEvery { mockRepository.getLocalSchedule(any()) } returns listOf(course)
        viewModel = ScheduleViewModel(mockRepository, "2024-2025-1")
        advanceUntilIdle()

        // When
        viewModel.onCourseSelected(course)

        // Then
        assertEquals(course, viewModel.uiState.value.selectedCourse)
    }

    @Test
    fun `onCourseSelected with null clears selectedCourse`() = runTest {
        // Given
        val course = TestDataFactory.createCourse()
        coEvery { mockRepository.getLocalSchedule(any()) } returns listOf(course)
        viewModel = ScheduleViewModel(mockRepository, "2024-2025-1")
        advanceUntilIdle()
        viewModel.onCourseSelected(course)

        // When
        viewModel.onCourseSelected(null)

        // Then
        assertNull(viewModel.uiState.value.selectedCourse)
    }

    // ================ 错误处理测试 ================

    @Test
    fun `dismissError clears errorMessage`() = runTest {
        // Given
        coEvery { mockRepository.getLocalSchedule(any()) } returns emptyList()
        coEvery { mockRepository.fetchRemoteSchedule(any()) } returns Result.failure(Exception("Error"))
        viewModel = ScheduleViewModel(mockRepository, "2024-2025-1")
        advanceUntilIdle()
        viewModel.refreshSchedule()
        advanceUntilIdle()

        // When
        viewModel.dismissError()

        // Then
        assertNull(viewModel.uiState.value.errorMessage)
    }
}
