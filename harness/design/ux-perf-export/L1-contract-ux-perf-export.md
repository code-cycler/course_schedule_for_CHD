# L1-contract-ux-perf-export.md · 契约层（feature: ux-perf-export）

> 导览：① 位置与职责 = 契约层（跨实现阶段稳定的架构骨架：模块边界/接口契约/全局选型）② 覆盖：A/B/C/D 四模块 ③ 上下游：继承 L0 验收（`L0-vision-ux-perf-export.md`）；约束 L2-build ④ 契约项声明：**「接口契约」§8 条、「全局选型」表、「模块与边界」表 = L2 硬约束**，L2 偏差须走层间裁决（ADR 偏差路径）。
>
> 决策来源：L1 W00 preview（2026-09-13，19 要点全采纳）+ 补充声明「静默同步应该设置独立开关由用户控制」。

## 模块与边界（新增/修改哪些）

| 模块 | 文件 | 动作 | 职责一句话 |
|---|---|---|---|
| A | `ui/theme/Theme.kt` | 修改 | `DarkColorScheme`/`LightColorScheme` 从 private 提为 `internal`，作为导出固定配色基准 |
| A | `ui/screens/schedule/ScheduleScreen.kt` | 修改 | 导出调用点：`OffscreenScheduleCapture` 内容外显式 `MaterialTheme(colorScheme) { Surface(colorScheme.background) { … } }` |
| B | `ui/screens/schedule/ScheduleViewModel.kt` | 修改 | 重计算块 `withContext(Default)`；三场景计时打点；统计派生；静默同步检查方法 |
| B | `data/repository/CourseRepositoryImpl.kt` | 修改 | `precomputeAndCacheConflicts` 的 DataStore JSON 序列化/写盘 `withContext(IO)` |
| B | `ui/util/ScheduleImageExporter.kt` | 修改 | `captureToFile` 的 compress+写盘 `withContext(Dispatchers.IO)` |
| B | `domain/model/Course.kt` | 修改 | 标 `@Immutable`（单模块工程，无跨模块依赖代价；如坚持 domain 零 Compose 注解，备选 = UI 侧稳定化，效果弱） |
| C | `ui/components/ScheduleSkeleton.kt` | 新增 | 手写课表形状骨架屏（灰块 + alpha 动画） |
| C | `ui/components/ScheduleGrid.kt` | 修改 | Modifier 层视觉打磨（圆角/边框/背景/间距/今日列高亮）+ 周切换过渡微调 |
| C | `ui/components/SettingsDrawer.kt` | 修改 | 新增「课表统计」第四区 + 「静默同步」开关行 |
| C | `res/values/strings.xml` 等 | 修改 | 文案中文化统一、contentDescription 汉化补全（values-en 保留不投入） |
| D | `domain/repository/ICourseRepository.kt` | 修改 | 新增「拉取不入库」接口方法 |
| D | `data/repository/CourseRepositoryImpl.kt` | 修改 | 实现该方法：`getRemoteSemesterOptions()` 解析 remoteId → `getCourseTableHtml(remoteId)` + 解析，零入库；入库段从在用路径抽公共方法供确认后复用 |
| D | `util/ScheduleDiffCalculator.kt` | 新增 | 纯函数 diff（无 IO，可单测） |
| D | `data/local/preferences/UserPreferences.kt` | 修改 | 新增 `silentSyncEnabled`（默认 true）、`lastSilentSyncAt` 键 |

## 接口契约（硬约束）

1. **`ICourseRepository` 新增**：`suspend fun fetchRemoteCoursesOnly(semester: String): Result<List<Course>>` —— 硬约束：**不得写 Room、不得写 DataStore 学期状态**（静默检查只读远端）。实现基准 = **在用验证路径**：`getRemoteSemesterOptions()` 解析当前学期 remoteId → `getCourseTableHtml(remoteId)`（grill-W01 Q1 裁决；`fetchRemoteSchedule`/`getCourseTablePage()` 为零调用点死代码，不得作为基准）；拉取前须 `cookieManager.syncFromWebView` 兜底。
2. **`ScheduleDiffCalculator`**：`fun computeDiff(local: List<Course>, remote: List<Course>): ScheduleDiff`，`ScheduleDiff(added: List<Course>, removed: List<Course>, changed: List<Pair<Course, Course>>)` —— 硬约束：**纯函数零 IO**；比对键 = 课程全字段（名称/教师/教室/星期/节次/周位图/类型/学分），不做字段级 diff。
3. **静默检查状态机（ViewModel 内 StateFlow 字段）**：`Idle / Checking / NoUpdate / HasUpdate(diff) / Failed` —— 硬约束：`Failed` 与 `NoUpdate` 在 UI 层**同表现（完全静默）**。
4. **确认入库**：用户在差异 AlertDialog 点确认 → 复用现有「全量替换该学期 + 冲突缓存重算」链路（从 `fetchRemoteSchedule` 抽出的公共方法）；取消 → 零写入。
5. **导出配色包裹**：导出子树 = `MaterialTheme(colorScheme = 固定深/浅) { Surface(color = colorScheme.background) }` —— 硬约束：**导出子树内禁止 `dynamicColorScheme`、禁止无背景容器**；深浅取 `isSystemInDarkTheme()`。
6. **计时日志**：tag `PerfLog`，格式「[PerfLog] <scene> start」/「[PerfLog] <scene> took Xms」，scene ∈ {load_schedule, week_switch, export}。
7. **统计契约**：`ScheduleStats(totalCredits: Float, courseCount: Int, totalNodes: Int, waterCourseCount: Int)`，由当前学期课程 + 水课列表派生，纯函数可单测。
8. **静默同步节流**：触发 = `ON_RESUME`；条件 = `silentSyncEnabled == true` 且 `now - lastSilentSyncAt ≥ 15 分钟`；**独立开关默认开**（W00 补充声明强调：由用户控制），开关描述文案注明「需近期登录过教务系统后生效」（grill-W01 Q2 预期管理）。`lastSilentSyncAt` 用 `stringPreferencesKey` 存 epoch millis 字符串（grill-W01 Q6：项目无 `longPreferencesKey` 先例，String 时间戳为既有范式）；`currentSemester` 为 null 时静默跳过不检查。

## 全局选型（含被否决项）

| 决策点 | 选型 | 被否决项（出处） |
|---|---|---|
| 导出配色 | 固定深浅两套（复用紫色系常量） | ✗ 动态取色/随壁纸（L0-W01 Q2）；✗ 固定仅浅色（L0-W00 补充声明「跟随系统」） |
| 导出捕获 | 沿用 GraphicsLayer.record | ✗ View 截图 / Canvas 手绘（现状即最优，不动） |
| 线程迁移 | `withContext` + 官方 Dispatchers | ✗ 自建线程池 / 字面「双线程渲染」（入口校准：瓶颈在数据计算不在绘制） |
| 重组优化 | `@Immutable` + 参数稳定化 | ✗ 网格重构为 Lazy/自绘 Canvas（L0-W00 第 5 条） |
| 骨架屏 | 手写 Compose 占位 | ✗ accompanist-placeholder（已废弃）/第三方库（L0-W00 第 10 条） |
| 性能测量 | `Log.d` 打点 | ✗ Macrobenchmark/Baseline Profile 模块（L0-W00 第 12 条） |
| D 同步通道 | 仅 OkHttp + 持久化 cookie | ✗ WebView（无法静默，且有 UI 依赖） |
| D 入库策略 | 先展示后确认 | ✗ 自动入库（L0 闸门裁决：防假更新覆盖） |
| D 触发 | ON_RESUME + 15 分钟节流 + 独立开关 | ✗ 每次无条件触发（W00 补充声明：开关由用户控制） |
| D 确认页 | AlertDialog 差异清单 | ✗ 专门屏幕/新路由（最轻形态） |

## 部署运维（App 语境）

- minSdk 28 不动；**零新增第三方依赖**；APK 体积影响 ≈ 0。
- 导出固定配色与设备 Material You 无关 → API 28 静态色设备与 API 31+ 设备导出观感一致。
- 版本：随下一个 git tag 发布；内部迭代号 `[vXX]` 续接现有序列。

## ADR 识别（三条件核对）

- **ADR-0006：导出链路固定深浅两套配色（不随 Material You）** —— 权衡真实（传播物一致性 vs 个性化）、半年后回看需上下文。→ [docs/adr/0006-export-fixed-color-scheme.md](../../docs/adr/0006-export-fixed-color-scheme.md)
- **ADR-0007：静默同步先展示后确认（不自动入库）** —— 权衡真实（数据安全 vs 顺滑）、与「静默失败」组合的行为易困惑。→ [docs/adr/0007-silent-sync-confirm-before-save.md](../../docs/adr/0007-silent-sync-confirm-before-save.md)
- 其余决策（线程分工、骨架屏手写、Log.d 打点等）不满足「难逆转 + 缺上下文困惑」双条件，不立 ADR，以本层选型表为准。

## 契约项声明（下层硬约束汇总）

「接口契约」8 条、「全局选型」表、「模块与边界」表为 L2-build 硬约束；L2 实现若需偏离（如接口签名变化、模块归属调整），必须走层间裁决（暂停 → 更新本层 → ADR 偏差记录 → 再继续）。
