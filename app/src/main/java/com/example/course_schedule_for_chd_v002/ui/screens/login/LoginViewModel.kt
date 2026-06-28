package com.example.course_schedule_for_chd_v002.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.course_schedule_for_chd_v002.data.local.preferences.UserPreferences
import com.example.course_schedule_for_chd_v002.domain.repository.ICourseRepository
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
 *
 * 唯一职责：WebView CAS 登录成功后，解析课表 HTML + 首页教学周，保存后通知导航。
 * （表单账号密码登录分支已移除——实际登录只走 WebViewScreen）
 *
 * @param repository 课程仓库接口
 * @param userPreferences 用户偏好设置
 */
private const val TAG = "LoginViewModel"

class LoginViewModel(
    private val repository: ICourseRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // 一次性导航事件，避免状态标志的问题
    private val _navigateBackEvent = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val navigateBackEvent: SharedFlow<Unit> = _navigateBackEvent.asSharedFlow()

    /**
     * CAS 登录成功后的处理
     * WebView 完成登录、首页（教学周）、课表抓取后回调
     *
     * @param courseTableHtml 课表页面的 HTML 内容
     * @param homePageHtml 首页 HTML 内容（包含教学周信息），可能为 null
     */
    fun onCasLoginSuccess(courseTableHtml: String, homePageHtml: String?) {
        android.util.Log.i("CHD_CurrentWeek", "========== [LoginViewModel] onCasLoginSuccess 开始 ==========")
        WebViewLogger.logSuccess("CAS", "登录成功，开始解析...")
        WebViewLogger.logDebug(TAG, "课表 HTML 长度: ${courseTableHtml.length}, 首页 HTML: ${homePageHtml?.length ?: "null"}")

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            // 步骤1：解析课表 HTML
            val defaultSemester = "2024-2025-1"
            android.util.Log.i("CHD_CurrentWeek", "[Step1] 开始解析课表 HTML...")
            val result = repository.parseHtmlToCourses(courseTableHtml, defaultSemester)

            result.fold(
                onSuccess = { courses ->
                    WebViewLogger.logSuccess("课表", "解析成功，共 ${courses.size} 门课程")
                    android.util.Log.i("CHD_CurrentWeek", "[Step2] 课表解析成功，课程数: ${courses.size}")

                    // 步骤2：从首页 HTML 解析当前教学周
                    android.util.Log.i("CHD_CurrentWeek", "[Step3] 开始从首页解析当前教学周...")
                    if (homePageHtml != null) {
                        android.util.Log.i("CHD_CurrentWeek", "[Step3.0] 首页 HTML 长度: ${homePageHtml.length}")

                        val currentWeekInfo = repository.parseCurrentWeekFromHtml(homePageHtml)

                        if (currentWeekInfo != null) {
                            val (semester, week) = currentWeekInfo
                            android.util.Log.i("CHD_CurrentWeek", "[Step3.1] 解析成功: 学期=$semester, 周次=$week")
                            WebViewLogger.logSuccess("教学周", "当前: $semester 第${week}周")

                            // 反推学期开始日期并保存
                            val semesterStartDate = TimeUtils.calculateSemesterStartDate(week)
                            android.util.Log.i("CHD_Semester", "[新功能] 反推学期开始日期: $semesterStartDate (当前周=$week)")

                            userPreferences.saveCurrentWeek(week)
                            userPreferences.saveCurrentSemester(semester)
                            userPreferences.saveSemesterStartDate(semesterStartDate)
                            userPreferences.saveLastParsedWeek(week)
                            android.util.Log.i("CHD_CurrentWeek", "[Step3.2] 已保存到 UserPreferences: week=$week, semester=$semester, startDate=$semesterStartDate")
                        } else {
                            android.util.Log.w("CHD_CurrentWeek", "[Step3.1] 解析失败，首页 HTML 可能不包含教学周信息")
                            val weekInfoIndex = homePageHtml.indexOf("本周为")
                            if (weekInfoIndex >= 0) {
                                val start = maxOf(0, weekInfoIndex - 50)
                                val end = minOf(homePageHtml.length, weekInfoIndex + 200)
                                android.util.Log.w("CHD_CurrentWeek", "找到'本周为'位置: $weekInfoIndex, 内容: ${homePageHtml.substring(start, end)}")
                            } else {
                                android.util.Log.w("CHD_CurrentWeek", "未找到'本周为'关键字")
                            }
                        }
                    } else {
                        android.util.Log.w("CHD_CurrentWeek", "[Step3] 首页 HTML 为 null，跳过教学周解析")
                    }

                    // 步骤3：发射导航事件
                    val navResult = _navigateBackEvent.tryEmit(Unit)
                    android.util.Log.i("CHD_CurrentWeek", "[Step4] 导航事件已发射: $navResult")

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoggedIn = true,
                            showWebView = false,
                            currentSemester = defaultSemester
                        )
                    }
                    android.util.Log.i("CHD_CurrentWeek", "========== [LoginViewModel] onCasLoginSuccess 成功结束 ==========")
                },
                onFailure = { error ->
                    android.util.Log.e("CHD_CurrentWeek", "[ERROR] 课表解析失败: ${error.message}")
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
