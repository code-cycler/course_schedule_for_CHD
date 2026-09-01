package com.example.course_schedule_for_chd_v002.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.course_schedule_for_chd_v002.data.local.preferences.UserPreferences
import com.example.course_schedule_for_chd_v002.data.remote.parser.ScheduleHtmlParser
import com.example.course_schedule_for_chd_v002.domain.repository.ICourseRepository
import com.example.course_schedule_for_chd_v002.ui.screens.schedule.inferCurrentSemester
import com.example.course_schedule_for_chd_v002.ui.screens.schedule.isSemesterOutdated
import com.example.course_schedule_for_chd_v002.ui.screens.schedule.semesterCode
import com.example.course_schedule_for_chd_v002.util.AppLogger
import com.example.course_schedule_for_chd_v002.util.Constants
import com.example.course_schedule_for_chd_v002.util.TimeUtils
import com.example.course_schedule_for_chd_v002.util.WebViewLogger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 登录界面 ViewModel
 * 管理登录流程和 UI 状态。
 *
 * v67: WebView 完成整个流程（CAS 登录 → 首页教学周 → 课表提取 → 回调 onCasLoginSuccess）。
 * v117: 表单登录死代码删除（登录只走 WebView，见 CLAUDE.md 铁律）；onCasLoginSuccess 学期写入
 *      改为「只升不降」，同步不再把 currentSemester 打回旧学期（跨学期窗口修复，ADR-0005）。
 */
private const val TAG = "LoginViewModel"

class LoginViewModel(
    private val repository: ICourseRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // [v28] 使用一次性事件进行导航，避免状态标志的问题
    private val _navigateBackEvent = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val navigateBackEvent: SharedFlow<Unit> = _navigateBackEvent.asSharedFlow()

    // [v117] 同步结果反馈（toast 由 AppNavigation Login composable 收集展示，跨导航存活）
    private val _uiMessage = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 4)
    val uiMessage: SharedFlow<String> = _uiMessage.asSharedFlow()

    init {
        AppLogger.d(TAG, "=== LoginViewModel 初始化 ===")
    }

    /**
     * 重置登录状态
     * [v27] 在导航成功后调用，防止下次进入时状态残留
     */
    fun resetLoginState() {
        _uiState.update { it.copy(isLoggedIn = false, isLoading = false, errorMessage = null) }
    }

    /**
     * [v73] CAS 登录成功后的处理
     * WebView 完成登录、首页（教学周）、课表获取。
     *
     * [v117 只升不降，ADR-0005] 同步退化为「按真实学期入库课表」：currentSemester 仅当候选
     * 学期编码 ≥ 当前编码时更新（允许升级/持平，禁止降级）。跨学期窗口教务首页仍显示旧学期时，
     * 不再把用户已切换的新学期打回旧学期。首页教学周解析失败/homePage null → 中止同步（零写入）。
     *
     * @param courseTableHtml 课表页面的 HTML 内容
     * @param homePageHtml 首页 HTML 内容（包含教学周信息），可能为 null
     */
    fun onCasLoginSuccess(courseTableHtml: String, homePageHtml: String?) {
        AppLogger.i("CHD_CurrentWeek", "========== [LoginViewModel] onCasLoginSuccess 开始 ==========")
        WebViewLogger.logSuccess("CAS", "登录成功，开始解析...")
        WebViewLogger.logDebug(TAG, "课表 HTML 长度: ${courseTableHtml.length}, 首页 HTML: ${homePageHtml?.length ?: "null"}")

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            // [Cookie 同步]（不变）WebView 登录成功后把 Cookie 灌进 OkHttp，供 OkHttp 请求用
            val cookieSynced = repository.syncCookiesFromWebView(Constants.EamsUrls.HOME_PAGE, "")
            AppLogger.i("CHD_CurrentWeek", "[Cookie] 同步 WebView→OkHttp: success=$cookieSynced")

            // 步骤1：从首页 HTML 解析真实学期+教学周（决定课程入哪个学期 key）
            // [v117 D2] 首页缺失或「本周为」解析失败 → 无法确定课表所属学期 → 中止同步（零写入 + toast），
            // 消灭原硬编码回退 "2024-2025-1"（曾把课表写进两年前学期 key、currentSemester 打回老学期）。
            val currentWeekInfo = homePageHtml?.let { repository.parseCurrentWeekFromHtml(it) }
            if (homePageHtml == null || currentWeekInfo == null) {
                AppLogger.w("CHD_CurrentWeek", "[Step1] 无法识别教务学期（首页=${homePageHtml != null} 解析=${currentWeekInfo != null}），中止同步")
                _uiState.update { it.copy(isLoading = false, errorMessage = "未能识别教务学期，请重试") }
                _uiMessage.tryEmit("未能识别教务学期，请重试")
                return@launch
            }
            val realSemester = currentWeekInfo.first
            AppLogger.i("CHD_CurrentWeek", "[Step1] 真实学期=$realSemester，入库用 targetSemester=$realSemester")

            // 步骤2：用真实学期解析+入库课表
            AppLogger.i("CHD_CurrentWeek", "[Step2] 开始解析课表 HTML，存入学期=$realSemester")
            val result = repository.parseHtmlToCourses(courseTableHtml, realSemester)

            result.fold(
                onSuccess = { courses ->
                    WebViewLogger.logSuccess("课表", "解析成功，共 ${courses.size} 门课程")
                    AppLogger.i("CHD_CurrentWeek", "[Step2] 课表解析成功，课程数: ${courses.size}, 学期: $realSemester")

                    // 步骤3：[v117 D1 只升不降 + D5 时间线绑定] 候选学期编码 ≥ 当前编码才更新
                    // currentSemester + 时间线；教务学期落后于当前（跨学期窗口）→ 不动，只入库 + toast。
                    val current = repository.getCurrentSemester()
                    val rc = semesterCode(realSemester)
                    val cc = current?.let { semesterCode(it) }
                    val shouldWriteCurrent = when {
                        current.isNullOrBlank() -> true     // 首次同步/无当前学期 → 直接建立
                        rc == null -> false                  // 候选串格式异常，保守不写
                        cc == null -> false                  // 当前串格式异常，保守不写
                        else -> rc >= cc                     // 只升不降
                    }

                    if (shouldWriteCurrent) {
                        val (semester, week) = currentWeekInfo
                        val semesterStartDate = TimeUtils.calculateSemesterStartDate(week)
                        AppLogger.i("CHD_Semester", "[新功能] 反推学期开始日期: $semesterStartDate (当前周=$week)")
                        userPreferences.saveCurrentWeek(week)
                        userPreferences.saveCurrentSemester(realSemester)
                        userPreferences.saveSemesterStartDate(semesterStartDate)
                        userPreferences.saveLastParsedWeek(week)
                        AppLogger.i("CHD_CurrentWeek", "[Step3] 已保存: week=$week, semester=$realSemester, startDate=$semesterStartDate")
                    } else {
                        AppLogger.w("CHD_Semester", "[Step3] 教务学期($realSemester) 落后于当前($current)，只升不降：不写 currentSemester/timeline")
                        _uiMessage.tryEmit("教务仍显示 $realSemester，已更新其课表；当前仍展示 $current")
                    }

                    // [跨学期] 步骤3.5：教务返回学期落后于日期推断学期（2/15、8/15 规则）时，自动追加
                    // 抓取推断学期课表。抓到 >0 门且推断学期新于当前 → 只升 promote；0 门/失败 → toast（D4，不再静默）。
                    val today = java.time.LocalDate.now()
                    val inferredSemester = inferCurrentSemester(today.year, today.monthValue, today.dayOfMonth)
                    if (isSemesterOutdated(realSemester, inferredSemester)) {
                        AppLogger.i("CHD_Semester", "[Step3.5] 教务学期($realSemester) 落后于推断学期($inferredSemester)，尝试自动追加抓取")
                        runCatching {
                            val remoteId = repository.getRemoteSemesterOptions().getOrNull()
                                ?.firstOrNull { ScheduleHtmlParser.parseSemesterString(it.label) == inferredSemester }
                                ?.remoteId
                            if (remoteId == null) {
                                AppLogger.w("CHD_Semester", "[Step3.5] 教务学期列表中无 $inferredSemester，跳过追加")
                            } else {
                                repository.fetchSpecifiedSemester(remoteId, inferredSemester)
                                    .fold(
                                        onSuccess = { count ->
                                            if (count > 0) {
                                                val currentAfter = repository.getCurrentSemester()
                                                val ci = currentAfter?.let { semesterCode(it) }
                                                val ii = semesterCode(inferredSemester)
                                                if (!currentAfter.isNullOrBlank() && ci != null && ii != null && ii <= ci) {
                                                    // [D1 只升不降] 推断学期不新于当前 → 课程已按推断学期入库，不 promote（防 pre-fetch 边界降级）
                                                    AppLogger.i("CHD_Semester", "[Step3.5] $inferredSemester 不新于当前($currentAfter)，课程已入库不升级")
                                                } else {
                                                    repository.promoteCurrentSemester(inferredSemester)
                                                    AppLogger.i("CHD_Semester", "[Step3.5] 已自动获取 $inferredSemester（$count 门课）并升级为当前学期")
                                                }
                                            } else {
                                                AppLogger.w("CHD_Semester", "[Step3.5] 教务尚未发布 $inferredSemester 课表（0 门），跳过升级")
                                                _uiMessage.tryEmit("教务尚未发布 $inferredSemester 课表")
                                            }
                                        },
                                        onFailure = { e ->
                                            AppLogger.w("CHD_Semester", "[Step3.5] 自动追加抓取失败: ${e.message}")
                                            _uiMessage.tryEmit("自动获取 $inferredSemester 失败，可从顶部横幅重试")
                                        }
                                    )
                            }
                        }
                    }

                    // 步骤4：发射导航事件 + UI 状态（显示学期 = 当前 currentSemester 真实值）
                    val navResult = _navigateBackEvent.tryEmit(Unit)
                    AppLogger.i("CHD_CurrentWeek", "[Step4] 导航事件已发射: $navResult")
                    val displaySemester = repository.getCurrentSemester() ?: realSemester
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoggedIn = true,
                            currentSemester = displaySemester
                        )
                    }
                    AppLogger.i("CHD_CurrentWeek", "========== [LoginViewModel] onCasLoginSuccess 成功结束 ==========")
                },
                onFailure = { error ->
                    AppLogger.e("CHD_CurrentWeek", "[ERROR] 课表解析失败: ${error.message}")
                    WebViewLogger.logError("课表", "解析失败: ${error.message}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to parse course table: ${error.message}"
                        )
                    }
                }
            )
        }
    }
}