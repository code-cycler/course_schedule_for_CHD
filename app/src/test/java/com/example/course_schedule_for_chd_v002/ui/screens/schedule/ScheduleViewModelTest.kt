package com.example.course_schedule_for_chd_v002.ui.screens.schedule

import com.example.course_schedule_for_chd_v002.data.local.preferences.UserPreferences
import com.example.course_schedule_for_chd_v002.domain.model.Campus
import com.example.course_schedule_for_chd_v002.domain.model.Course
import com.example.course_schedule_for_chd_v002.domain.model.CourseType
import com.example.course_schedule_for_chd_v002.domain.model.DayOfWeek
import com.example.course_schedule_for_chd_v002.domain.repository.ICourseRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * ScheduleViewModel 单元测试
 *
 * 基于当前 API：构造含 `userPreferences`，`loadSchedule` 加载课程 + allSemesters +
 * 冲突缓存 + 当前教学周 + 水课。覆盖 init/onWeekSelected/onCampusChanged/
 * onCourseSelected/logout/dismissError。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var mockRepository: ICourseRepository
    private lateinit var mockUserPreferences: UserPreferences
    private lateinit var viewModel: ScheduleViewModel

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        mockRepository = mockk(relaxed = true)
        mockUserPreferences = mockk(relaxed = true)
        Dispatchers.setMain(testDispatcher)

        // loadSchedule / loadCampus 依赖的默认 mock
        coEvery { mockRepository.getLocalSchedule(any()) } returns emptyList()
        coEvery { mockRepository.getAllSemesters() } returns listOf("2024-2025-1")
        coEvery { mockRepository.getConflictCache(any()) } returns emptyMap()
        coEvery { mockRepository.getConflictsForWeek(any(), any()) } returns null
        coEvery { mockRepository.precomputeAndCacheConflicts(any(), any()) } just Runs
        coEvery { mockUserPreferences.getSemesterStartDateOnce() } returns null
        coEvery { mockUserPreferences.getLastParsedWeekOnce() } returns null
        coEvery { mockUserPreferences.getWaterCoursesForSemester(any()) } returns emptySet()
        coEvery { mockUserPreferences.getCurrentWeekOnce() } returns 1
        coEvery { mockUserPreferences.getCampusOnce() } returns "WEISHUI"
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun TestScope.createViewModel(semester: String = "2024-2025-1"): ScheduleViewModel {
        viewModel = ScheduleViewModel(mockRepository, mockUserPreferences, semester)
        advanceUntilIdle()
        return viewModel
    }

    private fun course(name: String = "测试", startWeek: Int = 1, endWeek: Int = 16) = Course(
        name = name, teacher = "", location = "", dayOfWeek = DayOfWeek.MONDAY,
        startWeek = startWeek, endWeek = endWeek, startNode = 1, endNode = 2,
        courseType = CourseType.OTHER, credit = 0.0, semester = "2024-2025-1"
    )

    @Test
    fun init_loadsCoursesForSemester() = runTest {
        coEvery { mockRepository.getLocalSchedule("2024-2025-1") } returns listOf(course("高数"))

        createViewModel("2024-2025-1")

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(1, viewModel.uiState.value.courses.size)
    }

    @Test
    fun init_loadsAllSemesters() = runTest {
        coEvery { mockRepository.getAllSemesters() } returns listOf("2024-2025-1", "2024-2025-2")

        createViewModel()

        assertEquals(listOf("2024-2025-1", "2024-2025-2"), viewModel.uiState.value.allSemesters)
    }

    @Test
    fun onWeekSelected_updatesCurrentWeek() = runTest {
        createViewModel()
        viewModel.onWeekSelected(5)
        assertEquals(5, viewModel.uiState.value.currentWeek)
    }

    @Test
    fun onWeekSelected_clampsToMaxWeeks() = runTest {
        // 无课程时 maxWeeks = MAX_WEEKS(25)
        createViewModel()
        viewModel.onWeekSelected(0)
        assertEquals(1, viewModel.uiState.value.currentWeek)
        viewModel.onWeekSelected(100)
        assertEquals(25, viewModel.uiState.value.currentWeek)
    }

    @Test
    fun onCampusChanged_updatesCampus() = runTest {
        createViewModel()
        viewModel.onCampusChanged(Campus.BENBU)
        assertEquals(Campus.BENBU, viewModel.uiState.value.campus)
    }

    @Test
    fun onCourseSelected_updatesAndClearsSelection() = runTest {
        createViewModel()
        val c = course("测试")
        viewModel.onCourseSelected(c)
        assertEquals(c, viewModel.uiState.value.selectedCourse)
        viewModel.onCourseSelected(null)
        assertNull(viewModel.uiState.value.selectedCourse)
    }

    @Test
    fun logout_setsLoggedOut() = runTest {
        coEvery { mockRepository.logout() } just Runs
        createViewModel()
        viewModel.logout()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isLoggedOut)
    }

    @Test
    fun dismissError_clearsErrorMessage() = runTest {
        createViewModel()
        viewModel.dismissError()
        assertNull(viewModel.uiState.value.errorMessage)
    }
}
