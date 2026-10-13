package com.example.course_schedule_for_chd_v002.ui.screens.schedule

import com.example.course_schedule_for_chd_v002.data.local.preferences.UserPreferences
import com.example.course_schedule_for_chd_v002.domain.model.Course
import com.example.course_schedule_for_chd_v002.domain.model.CourseType
import com.example.course_schedule_for_chd_v002.domain.model.DayOfWeek
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
        // [v123] 显式传 TestDispatcher：loadSchedule 内 withContext(defaultDispatcher) 若用真实
        // Dispatchers.Default，advanceUntilIdle 等不到真实线程恢复（经典 runTest 竞态），故注入替换
        viewModel = ScheduleViewModel(
            repository, userPreferences, "2025-2026-2", calendarSyncService,
            defaultDispatcher = testDispatcher, ioDispatcher = testDispatcher
        )
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

    // ================ [v123] B 批回归锚：coursesByWeek 派生与切周行为（线程迁移前快照） ================
    // 迁移 withContext(Dispatchers.Default/IO) 后这些测试必须不加修改地保持全绿（L2-build B 批 ⓪ 步）。

    private fun anchorCourse(
        id: Long, name: String, day: DayOfWeek,
        startWeek: Int, endWeek: Int,
        startNode: Int = 1, endNode: Int = 2, remark: String = ""
    ) = Course(
        id = id, name = name, teacher = "T$id", location = "WM-$id", dayOfWeek = day,
        startWeek = startWeek, endWeek = endWeek, startNode = startNode, endNode = endNode,
        courseType = CourseType.REQUIRED, credit = 2.0, remark = remark, semester = "2025-2026-2"
    )

    private suspend fun stubNeutralPreferences() {
        // 中性偏好：无学期开始日期 / 无保存周次 / 无水课，让锚断言不受 relaxed mock 默认值影响
        coEvery { userPreferences.getSemesterStartDateOnce() } returns null
        coEvery { userPreferences.getLastParsedWeekOnce() } returns null
        coEvery { userPreferences.getCurrentWeekOnce() } returns 0
        coEvery { userPreferences.getWaterCoursesForSemester(any()) } returns emptySet()
        coEvery { repository.getCurrentSemester() } returns "2025-2026-2"
        coEvery { repository.getConflictCache(any()) } returns emptyMap()
    }

    @Test
    fun `loadSchedule builds coursesByWeek per week and picks first week with course`() = runTest {
        val c1 = anchorCourse(1, "高数", DayOfWeek.MONDAY, 1, 16)
        val c2 = anchorCourse(2, "英语", DayOfWeek.TUESDAY, 1, 8)
        val c3 = anchorCourse(3, "实训", DayOfWeek.WEDNESDAY, 17, 18)
        coEvery { repository.getLocalSchedule("2025-2026-2") } returns listOf(c1, c2, c3)
        stubNeutralPreferences()

        viewModel.reload()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(18, state.maxWeeks)
        assertEquals(setOf(1L, 2L), state.coursesByWeek[1]!!.map { it.id }.toSet())
        assertEquals(listOf(1L), state.coursesByWeek[9]!!.map { it.id })   // 第 9 周：英语已结束
        assertEquals(listOf(3L), state.coursesByWeek[17]!!.map { it.id })
        assertFalse(state.coursesByWeek.containsKey(19))
        assertEquals(1, state.currentWeek)                                 // 无日期/保存周 → 回退第一个有课周
        assertEquals(setOf(1L, 2L), state.displayCourses.map { it.id }.toSet())
        // 冲突缓存为空 → 必须触发预计算
        coVerify(exactly = 1) { repository.precomputeAndCacheConflicts(listOf(c1, c2, c3), "2025-2026-2") }
    }

    @Test
    fun `loadSchedule honors weeksBitmap semantics in weekly split`() = runTest {
        // 位图 "0101"：bitmap 下标=周次（bitmap[0]=预备周）→ 第 1、3 周有课，第 2 周无课
        val bitmapCourse = anchorCourse(1, "单双周课", DayOfWeek.MONDAY, 1, 3, remark = "weeksBitmap:0101")
        coEvery { repository.getLocalSchedule("2025-2026-2") } returns listOf(bitmapCourse)
        stubNeutralPreferences()

        viewModel.reload()
        advanceUntilIdle()

        val byWeek = viewModel.uiState.value.coursesByWeek
        assertEquals(listOf(1L), byWeek[1]!!.map { it.id })
        assertTrue("第 2 周不应有课（位图[2]=0）", byWeek[2]!!.isEmpty())
        assertEquals(listOf(1L), byWeek[3]!!.map { it.id })
    }

    @Test
    fun `onWeekSelected switches displayCourses from weekly index and refresh conflicts`() = runTest {
        val c1 = anchorCourse(1, "高数", DayOfWeek.MONDAY, 1, 16)
        val c2 = anchorCourse(2, "英语", DayOfWeek.TUESDAY, 1, 8)
        coEvery { repository.getLocalSchedule("2025-2026-2") } returns listOf(c1, c2)
        stubNeutralPreferences()
        coEvery { repository.getConflictsForWeek("2025-2026-2", 9) } returns setOf(1L)

        viewModel.reload()
        advanceUntilIdle()

        viewModel.onWeekSelected(9)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(9, state.currentWeek)
        assertEquals(listOf(1L), state.displayCourses.map { it.id })      // 第 9 周只剩高数
        assertEquals(setOf(1L), state.conflictingCourseIds)
    }

    @Test
    fun `onWeekSelected clamps out-of-range week into maxWeeks`() = runTest {
        coEvery { repository.getLocalSchedule("2025-2026-2") } returns listOf(
            anchorCourse(1, "高数", DayOfWeek.MONDAY, 1, 18)
        )
        stubNeutralPreferences()
        viewModel.reload()
        advanceUntilIdle()

        viewModel.onWeekSelected(99)
        advanceUntilIdle()
        assertEquals(18, viewModel.uiState.value.currentWeek)

        viewModel.onWeekSelected(0)
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.currentWeek)
    }
}
