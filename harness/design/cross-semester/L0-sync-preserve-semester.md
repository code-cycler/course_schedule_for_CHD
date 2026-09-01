# L0-sync-preserve-semester.md · 同步不再把展示学期打回旧学期

> 导览：① 本层位置与职责 = 目标层（L0，feature 轻量单层交付，人 2026-09-01 定模拍板）② 覆盖：同步链路 currentSemester 写权限（只升不降）、解析失败中止、硬编码回退消灭、反馈 toast ③ 上下游：实现期直接依赖本文件验收标准；姊妹篇 [L0-cross-semester.md](./L0-cross-semester.md)（v115）为同链原始设计。
> 决策来源：2026-09-01 design-questionnaire 轻量管道（入口校准 + 两波 AskUserQuestion 共 6 题，人拍板；处理摘要见对话记录）。

## 目标与受众

- **受众**：已在本 App 用过至少一个学期、跨学期窗口（暑假/寒假）会点顶栏「同步」的 CHD 学生。
- **目标**：同步退化为**纯数据更新**，不再具备「把用户已选定的当前学期悄悄打回旧学期」的破坏力；解析失败不再错写两年前的学期 key。
- **一句话验收**：用户切到 `2026-2027-1` 后，无论教务首页显示什么、无论解析成败，再按「同步」都不会让 App 停回旧学期。

## 根因（已核实代码 + 日志，2026-09-01）

用户反馈（原话）：「26-27 第一学期切出来成功后，在同步一下又变成上学期的还切不回去，直接覆盖了。」日志 `devfiles/chd_schedule_log_20260901_152857.txt` 实证：**应用启动 currentSemester=2024-2025-1、结束态导航停在 2024-2025-1**，而 Room 里语义已存在 `2026-2027-1`（22–24 门课）。

| # | 根因 | 代码出处 | 日志证据 |
|---|------|---------|---------|
| 1 | 同步无条件把 currentSemester 覆盖成教务首页显示学期；跨学期窗口（教务未切）时 = 打回旧学期 | `parseHtmlToCourses` saveCurrentSemester(:348) + `onCasLoginSuccess` Step3(:215/221) | 过渡期同步后停在旧学期 |
| 2 | 首页「本周为」解析失败 → 回退硬编码 `2024-2025-1` | `LoginViewModel.kt:183` | 启动/结束态停在 2024-2025-1（两年前！） |
| 3 | Step3.5 补抓失败/0 门静默 → currentSemester 已被 2/3 覆盖不恢复 =「切不回去」 | `LoginViewModel.kt:229-261` | 0 门静默降级 |
| 4 | 导航层三道 fallback 也是硬编码，放大症状 | `AppNavigation.kt:114/141/169` | 导航到 2024-2025-1 |

## 决策（2026-09-01 人拍板）

| # | 决策点 | 拍板结果 |
|---|--------|---------|
| D1 | **currentSemester 写权限** | **只升不降**：自动路径（同步 Step3 / Step3.5 / banner）仅在候选学期编码 ≥ 当前编码时写入；候选 < 当前（过渡期）不写。学期选择器只改 VIEW、不写（维持现状）。 |
| D2 | 首页解析失败处置 | **本次同步中止**：不写课表、不碰学期，toast「未能识别教务学期，请重试」。消灭 `2024-2025-1` 回退。 |
| D3 | 同步后展示 | 维持现状：导航到 currentSemester（升级后或原学期）。 |
| D4 | Step3.5 补抓提示 | 失败/0 门给 toast（「教务尚未发布新学期课表」/「自动获取新学期失败，可从顶部横幅重试」），不再静默。 |
| D5 | 时间线绑定 | 仅当候选学期最终成为 currentSemester（候选 ≥ 当前）才写 week/startDate/lastParsedWeek；候选 < 当前不动时间线（v113 非当前学期不存开始日期的镜像）。 |
| D6 | 硬编码清点 | 全清：AppNavigation 回退改「DataStore currentSemester → 本地最近入库学期 → 空态引导」；LoginUiState 默认置空；表单登录死代码路径删除。 |

## 实现要点（改动面）

1. **[LoginViewModel] `onCasLoginSuccess` 重构**：
   - Step1：`parseCurrentWeekFromHtml` 失败（null）→ 直接 return abort + toast（D2），**不再有 `targetSemester = realSemester ?: "2024-2025-1"`**。
   - Step2：照旧 `parseHtmlToCourses(html, realSemester)`（真实学期 key）。
   - Step3 改「只升不降」：
     ```
     current = repository.getCurrentSemester()
     if (current == null || semesterCode(realSemester) >= semesterCode(current)) {
         saveCurrentSemester(realSemester)                    // 升级/持平（D1）
         if (currentWeekInfo != null) saveWeek/startDate/lastParsedWeek  // D5：候选成为 current 才写时间线
     } else {
         // 候选 < 当前：过渡期，不写 currentSemester、不动时间线
         toast「教务仍显示 {realSemester}，已更新其课表；当前仍展示 {current}」
     }
     ```
     `semesterCode(realSemester)` 为 null（极端格式异常）→ 保守不升级。
   - Step3.5：保留自动补抓（`isSemesterOutdated(realSemester, inferred)` 触发）；成功 count>0 且 `inferred > current` 才 `promoteCurrentSemester`（**promote 也套只升不降**，防 pre-fetch 后错过日期边界的降级）；0 门/失败 → toast（D4）。
2. **[CourseRepositoryImpl] 数据方法去「current」职责**：`parseHtmlToCourses`(:348) / `fetchCourseTableWithOkHttp`(:219) / `fetchRemoteSchedule`(:406) 移除 `saveCurrentSemester`——「当前学期」是调用方（onCasLoginSuccess / promoteCurrentSemester / 用户操作）的决策，不是解析入库的副作用。`precomputeAndCacheConflicts` 不动。
3. **[AppNavigation]** :141 导航目标 / :114 ScheduleRoot 跳板 / :169 schedule composable fallback：改为 `getCurrentSemester() ?: 本地最近入库学期(getAllSemesters 降序第一个) ?: 空态引导`（D6），移除裸 `2024-2025-1`。
4. **[LoginUiState]** :20 默认 `currentSemester` 置空字符串，UI 展示逻辑已有空态兜底。
5. **死代码清理**（D6）：`LoginViewModel.login()` / `onWebViewLoginSuccess()` / `onFetchCourseTable()` / `fetchCourseTableAndNavigate()` 及其余 `2024-2025-1` 散点。⚠ 按 CLAUDE.md 铁律：**登录只走 WebView，不重新引入表单登录**。
6. **`promoteCurrentSemester`（防御）**：内部加只升不降守卫（新编码 > 旧编码才 `saveCurrentSemester`），banner/Step3.5 调用语义不变。

## 验收（可独立验证）

1. **单测**（`./gradlew test` 全绿，含新增/改造）：
   - `onCasLoginSuccess`：教务学期 < 用户当前学期 → currentSemester 不降、timeline 不动、课程入教务学期 key、toast 触发。
   - 教务学期 > 用户当前学期 → 升级 currentSemester + timeline 写。
   - 首页解析失败 → 零写入 + toast。
   - 回归：现有 SemesterInferenceTest 9 / ScheduleViewModelTest 7 / LoginViewModelTest 6 / CourseRepositoryImplTest 8 不破坏。
2. **静态**：`grep -rn "2024-2025-1" app/src/main/java` 仅剩注释样本/语义说明（无活动代码路径）。
3. **手动回归**：正常同步（同学期）刷新、跨期窗口同步不踢回、横幅获取/关闭/次日重现、「获取其它学期」、本地切换、非当前学期表头降级；启动进 DataStore 真实学期。

## 风险 / 约束

- **未验证假设**（沿用 v115 台账）：教务首页「本周为」在 8/15–开学间的实际显示；新学期课表发布时点（决定 0 门 toast 频率）。
- **与 v115 张力**：Step3.5 的「0 门静默降级」语义 → 改为 toast（D4）；`promoteCurrentSemester` 增只升守卫，不破坏既有 banner/Step3.5 升级调用（它们本就升级）。
- **约束**：登录仍只走 WebView；网络仍全走 OkHttp；不引入新依赖。
- **行为边界**（特性非缺陷）：候选 < 当前时同步课表按真实学期入老 key；用户故意回看旧学期并同步 → 不会被踢向新。

## 相关文档

- [L0-cross-semester.md](./L0-cross-semester.md)（v115 原始跨学期设计）
- [ADR-0005](../../../docs/adr/0005-sync-current-semester-monotonic.md)（只升不降决策）
- [SEMESTER-SWITCH.md](../../../docs/SEMESTER-SWITCH.md)（切换学期设计，已补本节）