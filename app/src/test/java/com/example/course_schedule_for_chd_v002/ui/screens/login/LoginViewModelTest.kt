package com.example.course_schedule_for_chd_v002.ui.screens.login

import com.example.course_schedule_for_chd_v002.data.local.preferences.UserPreferences
import com.example.course_schedule_for_chd_v002.domain.model.SemesterOption
import com.example.course_schedule_for_chd_v002.domain.repository.ICourseRepository
import com.example.course_schedule_for_chd_v002.ui.screens.schedule.inferCurrentSemester
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * LoginViewModel 单元测试
 *
 * 重点测：
 * - [defaultSemester 修复]：onCasLoginSuccess 把课程存入首页解析出的真实学期，而非硬编码 "2024-2025-1"。
 * - [v117 只升不降，ADR-0005]：同步不把 currentSemester 打回旧学期——
 *   ① 教务学期 < 当前学期 → 不降级、时间线不动、课程按真实学期入库 + 过渡期 toast；
 *   ② 教务学期 > 当前学期 → 升级 currentSemester + 时间线 + Step3.5 自动 promote；
 *   ③ 首页解析失败/null → 中止同步（零写入 + toast + 无导航）。
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
        // 默认：无当前学期（首次同步）；远程学期列表为空（Step3.5 不补抓、无额外 toast）
        coEvery { repository.getCurrentSemester() } returns null
        coEvery { repository.getRemoteSemesterOptions() } returns Result.success(emptyList())
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
    fun `onCasLoginSuccess aborts when homePage null`() = runTest {
        coEvery { repository.parseHtmlToCourses(any(), any()) } returns Result.success(emptyList())

        val (msgs, job) = startCollector(viewModel.uiMessage)
        val (navs, navJob) = startCollector(viewModel.navigateBackEvent)
        viewModel.onCasLoginSuccess("<html>课表</html>", null)
        job.cancel()
        navJob.cancel()

        assertEquals(listOf("未能识别教务学期，请重试"), msgs)   // toast + 零写入 + 无导航
        assertTrue(navs.isEmpty())
        coVerify(exactly = 0) { repository.parseHtmlToCourses(any(), any()) }
        coVerify(exactly = 0) { userPreferences.saveCurrentSemester(any()) }
        coVerify(exactly = 0) { userPreferences.saveCurrentWeek(any()) }
        assertNotNull(viewModel.uiState.first().errorMessage)
    }

    @Test
    fun `onCasLoginSuccess aborts when homePage lacks week info`() = runTest {
        val homeHtml = "<html>无教学周信息</html>"
        every { repository.parseCurrentWeekFromHtml(homeHtml) } returns null
        coEvery { repository.parseHtmlToCourses(any(), any()) } returns Result.success(emptyList())

        viewModel.onCasLoginSuccess("<html>课表</html>", homeHtml)
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.parseHtmlToCourses(any(), any()) }
        coVerify(exactly = 0) { userPreferences.saveCurrentSemester(any()) }
        assertNotNull(viewModel.uiState.first().errorMessage)
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

    // ================ [v117 只升不降，ADR-0005] ================

    @Test
    fun `教务学期落后于当前学期时同步不降级`() = runTest {
        // 用户已切到 2026-2027-1，教务首页仍显示 2025-2026-2（跨学期窗口）
        coEvery { repository.getCurrentSemester() } returns "2026-2027-1"
        val homeHtml = "<html>本周为第1教学周 2025-2026学年第2学期</html>"
        every { repository.parseCurrentWeekFromHtml(homeHtml) } returns ("2025-2026-2" to 1)
        coEvery { repository.parseHtmlToCourses(any(), any()) } returns Result.success(emptyList())

        val (msgs, job) = startCollector(viewModel.uiMessage)
        viewModel.onCasLoginSuccess("<html>课表</html>", homeHtml)
        job.cancel()

        assertEquals(listOf("教务仍显示 2025-2026-2，已更新其课表；当前仍展示 2026-2027-1"), msgs)

        // 课程按真实学期入库，但 currentSemester/时间线不动（只升不降）
        coVerify { repository.parseHtmlToCourses("<html>课表</html>", "2025-2026-2") }
        coVerify(exactly = 0) { userPreferences.saveCurrentSemester("2025-2026-2") }
        coVerify(exactly = 0) { userPreferences.saveCurrentSemester("2026-2027-1") }
        coVerify(exactly = 0) { userPreferences.saveCurrentWeek(any()) }
        coVerify(exactly = 0) { userPreferences.saveLastParsedWeek(any()) }
        // 同步仍完成：导航事件照发
        viewModel.navigateBackEvent.test {
            viewModel.onCasLoginSuccess("<html>课表</html>", homeHtml)
            assertNotNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `教务学期更新于当前时同步升级并自动promote`() = runTest {
        // 用户停在上上期 2014-2015-2，教务已切到 2015-2016-1 且新学期可抓 → 升级 + Step3.5 promote
        coEvery { repository.getCurrentSemester() } returns "2014-2015-2"
        val homeHtml = "<html>本周为第1教学周 2015-2016学年第1学期</html>"
        every { repository.parseCurrentWeekFromHtml(homeHtml) } returns ("2015-2016-1" to 1)
        coEvery { repository.parseHtmlToCourses(any(), any()) } returns Result.success(emptyList())

        // 推断学期随系统日期（robust，不写死）；remote label 用同一推断生成，必然匹配
        val today = LocalDate.now()
        val inferred = inferCurrentSemester(today.year, today.monthValue, today.dayOfMonth)
        val p = inferred.split("-")
        val inferredLabel = "${p[0]}-${p[1]}学年第${p[2]}学期"
        coEvery { repository.getRemoteSemesterOptions() } returns
            Result.success(listOf(SemesterOption(remoteId = "998", label = inferredLabel)))
        coEvery { repository.fetchSpecifiedSemester(any(), any()) } returns Result.success(5)

        viewModel.onCasLoginSuccess("<html>课表</html>", homeHtml)
        advanceUntilIdle()

        // Step3 升级：currentSemester = 教务真实学期 + 时间线
        coVerify { userPreferences.saveCurrentSemester("2015-2016-1") }
        coVerify { userPreferences.saveCurrentWeek(1) }
        coVerify { userPreferences.saveLastParsedWeek(1) }
        // Step3.5 自动补抓推断学期并 promote（只升：inferred 新于当前）
        coVerify { repository.fetchSpecifiedSemester("998", inferred) }
        coVerify { repository.promoteCurrentSemester(inferred) }
    }

    @Test
    fun `Step3-5 补抓 0 门时给出提示且不升级`() = runTest {
        coEvery { repository.getCurrentSemester() } returns "2014-2015-2"
        val homeHtml = "<html>本周为第1教学周 2015-2016学年第1学期</html>"
        every { repository.parseCurrentWeekFromHtml(homeHtml) } returns ("2015-2016-1" to 1)
        coEvery { repository.parseHtmlToCourses(any(), any()) } returns Result.success(emptyList())

        val today = LocalDate.now()
        val inferred = inferCurrentSemester(today.year, today.monthValue, today.dayOfMonth)
        val p = inferred.split("-")
        val inferredLabel = "${p[0]}-${p[1]}学年第${p[2]}学期"
        coEvery { repository.getRemoteSemesterOptions() } returns
            Result.success(listOf(SemesterOption(remoteId = "998", label = inferredLabel)))
        coEvery { repository.fetchSpecifiedSemester(any(), any()) } returns Result.success(0)

        val (msgs, job) = startCollector(viewModel.uiMessage)
        viewModel.onCasLoginSuccess("<html>课表</html>", homeHtml)
        job.cancel()

        assertEquals(listOf("教务尚未发布 $inferred 课表"), msgs)
        coVerify(exactly = 0) { repository.promoteCurrentSemester(any()) }
    }

    /**
     * 手动收集 SharedFlow：Unconfined 收集器在调用前已订阅（inline 启动并悬挂在 collect 上），
     * 同步发射即时送达——规避 turbine 对「发射先于收集器调度」的时序脆弱（嵌套场景尤甚）。
     */
    private fun <T> TestScope.startCollector(flow: Flow<T>): Pair<MutableList<T>, Job> {
        val collected = mutableListOf<T>()
        val job = launch(UnconfinedTestDispatcher()) { flow.collect { collected += it } }
        return collected to job
    }
}