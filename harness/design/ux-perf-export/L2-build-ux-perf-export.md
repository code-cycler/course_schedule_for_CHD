# L2-build-ux-perf-export.md · 构建层（feature: ux-perf-export）

> 导览：① 位置与职责 = 构建层（分几步建/每步做什么/何时算完）② 覆盖：四实现批次 A/B/C/D ③ 上下游：受 L1 契约约束（接口 8 条 + 选型表 + 模块边界表 = 硬约束）④ 契约项声明：**「每阶段 DoD」为验收硬约束**。
>
> 决策来源：L2 W00 preview（2026-09-13，16 要点：15 采纳 / 1 留空——迭代号段待快问）。

## 阶段拆分（阶段表 + 依赖链 + 每阶段独立 DoD）

| 阶段 | 内容（内部顺序） | 依赖 | 预估 |
|---|---|---|---|
| **A 导出修复** | ① Theme.kt 配色常量提可见性 → ② 导出调用点显式主题包裹 + Surface 填底 → ③ 真机深浅两模式截图核验 | 无 | 0.5 天 |
| **B 性能** | ⓪ 补 `onWeekSelected`/`coursesByWeek` 行为单测（迁移前回归锚，grill-W01 Q7）→ ① PerfLog 打点建基线 → ② 线程迁移（Default/IO，注入式）→ ③ 重组优化（@Immutable + 参数稳定化）→ ④ 复测对比留档 | 无（与 A 无耦合；实现按 A→B 串行） | 1 天 |
| **C UI 修缮** | ① 骨架屏 → ② 网格视觉打磨 → ③ 周切换过渡 → ④ 统计面板（纯函数 + UI）→ ⑤ 文案中文化与无障碍补全 | 无 | 1.5 天 |
| **D 静默同步** | ① Repository 只读接口 → ② ScheduleDiffCalculator + 单测 → ③ ViewModel 状态机 + 节流 → ④ 差异 AlertDialog → ⑤ 开关 UI + DataStore 键 | **B**（同动 `ScheduleViewModel`，串行防冲突） | 1.5 天 |

依赖链：`A → B → C → D`（唯一硬依赖 D←B）；每批独立 git commit + 独立 DoD，支持断点续作（可分四次会话）。

## 详细设计（按阶段）

### A · 导出修复

1. `Theme.kt`：`private val DarkColorScheme/LightColorScheme` 提为 `internal`；新增 internal composable `ExportScheduleTheme(content)` = `MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColorScheme else LightColorScheme) { Surface(color = colorScheme.background) { content() } }`。
2. `ScheduleScreen` 导出调用点（现 L655–674）：`OffscreenScheduleCapture { ExportScheduleTheme { ExportableScheduleGrid(…) } }`——导出子树内所有 `MaterialTheme.colorScheme.*` 取色随之固定，根容器不再透明。
3. 验证：真机深/浅模式各导出一张，核底色、表头文字、课程卡、边框、今日高亮（A1–A4）。

### B · 性能

0. **回归锚（先行）**：给 `onWeekSelected` 与 `coursesByWeek` 派生逻辑补行为单测（现状 `ScheduleViewModelTest` 7 用例全是 fetch/clear 类，切周逻辑零覆盖）——迁移前写、迁移后必须仍绿（grill-W01 Q7）。
1. **打点**：`PerfLog` tag 统一；三场景 = `load_schedule`（`loadSchedule` 全程）/ `week_switch`（`onWeekSelected` + `updateConflictsForWeek`）/ `export`（`captureToFile` 全程）；入口/出口各一条 `Log.d`。
2. **线程迁移（注入式，不硬编码 Dispatchers）**：`ScheduleViewModel` 构造注入 `defaultDispatcher`/`ioDispatcher`（Koin module 统一提供，单测替换 TestDispatcher）——
   - `loadSchedule`/`refreshAfterEdit`：maxWeek 计算 + `coursesByWeek` associateWith + 冲突预计算 → `withContext(defaultDispatcher)`
   - `precomputeAndCacheConflicts` 内 DataStore JSON 序列化/写盘 → `withContext(ioDispatcher)`；`HtmlCache` 文件写同理
   - `captureToFile`：`bitmap.compress` + 文件写入 → `withContext(ioDispatcher)`
3. **重组优化**：`Course` 标 `@Immutable`（单模块无跨模块代价）；`ScheduleGrid` 周课程列表参数天然稳定（来自 StateFlow 的 Map 值，state 变化才换实例）；详情弹窗 `getWeeksDisplayText()` 包 `remember(course.id)`。
4. **复测**：三场景重打点，前后耗时对比写入 commit message。

### C · UI 修缮

1. **骨架屏**（`ui/components/ScheduleSkeleton.kt` 新增）：网格形状灰块（节次列 + 7 列表头 + 11 行格子，Surface 灰块 + 无限 alpha 呼吸动画）；仅首载 `isLoading` 显示，切周不显示。
2. **网格视觉打磨**（`ScheduleGrid.kt`，只动 Modifier 层）：课程卡圆角约 8.dp、格线降 alpha 弱化平铺感、今日列底色 `primaryContainer` 透明度分层、卡片间距与节次列背景微调——具体值实现期视觉微调，C4 人工核验。
3. **周切换过渡**：`animateScrollToPage` animationSpec 微调（ease 曲线）；页面内容不加 crossfade（克制）。
4. **统计面板**：`util/ScheduleStatsCalculator.kt` 纯函数 `calculate(courses: List<Course>, waterCourseNames: Set<String>): ScheduleStats`（totalCredits/courseCount/totalNodes/waterCourseCount）；`ScheduleUiState` 加 `stats` 字段（loadSchedule 时派生）；`SettingsDrawer` 新增「课表统计」第四区。
5. **文案与无障碍**：`WeekSelector` "Previous/Next week" → 「上一周/下一周」；`ScheduleGrid` "Conflict" → 「时间冲突」；周几表头缩写 Mon/Tue → 中文单字（一/二/…/日，今日加粗高亮已有）；课程卡加 contentDescription（课程名+教室+时间）；新字符串只进 `values/strings.xml` 不进 values-en。

### D · 静默同步

1. **Repository**：`ICourseRepository.fetchRemoteCoursesOnly(semester): Result<List<Course>>`——**基于在用验证路径**（grill-W01 Q1 裁决）：前置 `cookieManager.syncFromWebView` cookie 兜底（对齐 `fetchSpecifiedSemester`，`CourseRepositoryImpl.kt:472`）→ `getRemoteSemesterOptions()` 解析当前学期 remoteId → `getCourseTableHtml(remoteId)` → 解析，**零入库零学期写入**；「入库段」公共方法从在用路径（`fetchSpecifiedSemester`/死代码 `fetchRemoteSchedule` 的同款逻辑，`deleteBySemester`+`insertAll`）抽出供确认后复用。调用方边界：`getCurrentSemester()` 为 null 时直接跳过静默检查（grill-W01 Q5）。
2. **Diff**（`util/ScheduleDiffCalculator.kt`）：`computeDiff(local, remote): ScheduleDiff(added, removed, changed)`——按课程全字段 equals 判不变，local 独有为 removed、remote 独有为 added，同名其余字段异为 changed（old/new 配对）；纯函数零 IO。
3. **ViewModel**：`silentSyncState: StateFlow<SilentSyncState>`（sealed：Idle/Checking/NoUpdate/HasUpdate(diff)/Failed）；`checkForUpdatesSilently()`：开关开 且 距 `lastSilentSyncAt ≥ 15 分钟` → 拉取 → 与本地 diff → 空 = NoUpdate、非空 = HasUpdate、异常 = Failed；**每次尝试（含失败）都更新 `lastSilentSyncAt`**（防失败后 ON_RESUME 反复重试轰炸）。用户取消确认后，下个节流窗再次提示（特性）。
4. **确认 AlertDialog**：标题「检测到课表更新」，三行汇总（新增 X 门/删除 Y 门/变更 Z 门）+ 可展开明细（课程名 + 关键变化，**「本地独有」条目打标**——grill-W01 Q3：全量替换语义下用户手动编辑/自定义课程会被清除，diff 无法自动区分）；对话框固定明示「确认后将以教务数据替换本地全部课程（含你手动添加/编辑的 N 门）」；「更新」→ `confirmUpdate()` 走入库链路（全量替换学期 + 冲突缓存重算 + 刷新）；「取消」→ dismiss 数据不动。
5. **开关与键**：`SettingsDrawer` 「静默同步」Switch（独立开关默认开），**描述文案注明「需近期登录过教务系统后生效」**（grill-W01 Q2 预期管理）；`UserPreferences` 新增 `silentSyncEnabled`(Boolean, true)/`lastSilentSyncAt`(**`stringPreferencesKey` 存 epoch millis 字符串**，grill-W01 Q6：项目无 `longPreferencesKey` 先例)。
6. **挂点**：现有 ON_RESUME `DisposableEffect`（`refreshCurrentTimeInfo` 处）追加 `checkForUpdatesSilently()`，不新增生命周期监听。
7. **与手动同步互斥**（grill-W01 Q4）：`onNavigateToLogin`（顶栏「同步」进 WebView）前置重置 `silentSyncState` 为 Idle——差异框随状态消失，防「WebView 同步返回后过期 diff 二次覆盖」竞态。

## 接口规格（实现期签名基线；偏离走层间裁决）

| 接口 | 签名 | 错误语义 |
|---|---|---|
| 只读拉取 | `suspend fun ICourseRepository.fetchRemoteCoursesOnly(semester: String): Result<List<Course>>` | `Result.failure(e)` 透传不细分（调用方只看成功/失败） |
| diff | `fun ScheduleDiffCalculator.computeDiff(local: List<Course>, remote: List<Course>): ScheduleDiff` | 纯函数无异常路径 |
| 静默状态 | `sealed SilentSyncState { Idle; Checking; NoUpdate; HasUpdate(diff); Failed }` | Failed 与 NoUpdate 在 UI 同表现（静默） |
| 统计 | `fun ScheduleStatsCalculator.calculate(courses: List<Course>, waterCourseNames: Set<String>): ScheduleStats` | 空课表返回全 0 |
| 导出主题 | `@Composable internal fun ExportScheduleTheme(content: @Composable () -> Unit)` | 无（组合函数） |
| 偏好键 | `UserPreferences.silentSyncEnabled`(Boolean) / `lastSilentSyncAt`(String，epoch millis，grill-W01 Q6) | 缺省 true / "0" |
| 计时 | `PerfLog` 常量 tag + scene 枚举（load_schedule/week_switch/export） | 无 |

**单测清单**：`ScheduleDiffCalculatorTest` 五用例（全同/全新增/全删除/部分变更/多教学班合并形态）；`ScheduleStatsCalculatorTest` 三用例（正常/空/含水课）；现有 `ScheduleViewModelTest` 7 用例在注入 dispatcher 下保持全绿。

## 每阶段 DoD（验收硬约束）

- **A**：深浅两模式真机截图人工核验 A1–A4 + `./gradlew test` 全绿 + 分享 Intent/文件命名/FileProvider 路径回归不变。
- **B**：PerfLog 三场景前后耗时对比留档（commit message）+ `./gradlew test` 全绿（含 ScheduleViewModelTest 7 用例 + **新增 `onWeekSelected`/`coursesByWeek` 行为单测保持全绿**，grill-W01 Q7 回归锚）+ 切周/加载/导出行为一致（补充冒烟，弱判据已由单测锚替代）。
- **C**：C1–C6 逐条真机核验 + 新增单测全绿 + `./gradlew test` 全绿。
- **D**：D1–D5 逐条真机核验（含 cookie 失效静默分支、确认/取消两路径）+ DiffCalculator/统计单测全绿 + 手动同步按钮路径回归不变。
- **全批收尾**：`./gradlew test` + `assembleDebug` + `lintDebug` 全绿；真机冒烟（启动/切周/切学期/分享导出/设置抽屉）；minSdk 28 模拟器过 A/D 关键路径（API 28 静态色 fallback 下导出配色一致性）。

## 依赖与预估

- 人力：A 0.5 / B 1 / C 1.5 / D 1.5 天，合计约 3.5–4.5 工作日（含单测与真机验证）；无外部依赖阻塞。
- 内部迭代号：四批分别记 **[v121]–[v124]**（导出修复/性能/修缮/静默同步），与代码注释最新 [v120] 顺接〔2026-09-13 W00 留空 → 降风险协议采用推荐项（顺延），**provisional：用户未显式确认**，实现期如改主意仅影响 commit 注释，零代码代价〕。
