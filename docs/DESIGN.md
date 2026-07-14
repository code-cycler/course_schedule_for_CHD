# 设计备忘（DESIGN.md）

> 本项目的「构想文档」。定位是**回顾性设计文档**（项目已实现完整），不是从零的产品 PRD。
> 给「半年后的自己」看：为什么这么做、系统怎么搭、踩过什么坑。技术细节为主，不官僚。
> 配套阅读：[README.md](../README.md)（是什么 / 怎么用）、[OPEN-DECISIONS.md](./OPEN-DECISIONS.md)（未决问题）、[CLAUDE.md](../CLAUDE.md)（AI 改代码须知）。

---

## 1. 这是什么 & 为什么做

**一句话**：一个把长安大学（CHD）教务系统课表「搬」到手机上、并能离线查看的 Android App。

**要解决的问题**：
- 教务系统（`bkjw.chd.edu.cn`，beangle 框架）的课表页**重度依赖 JavaScript 动态渲染**，安卓系统 WebView 直接打开会白屏（外部 JS 资源加载不全）。
- 课表数据不在原始 HTML 里，而是由页面里的 JS（`table0.activities`、`TaskActivity`）运行后才生成。
- 登录要走学校 CAS 单点登录（`ids.chd.edu.cn`），还要处理 Cookie。

**不做什么（非目标）**：
- 不做账号密码的纯接口登录——CAS 有验证码/风控，纯接口不稳定，**实际登录只走 WebView**（让用户在 WebView 里登录，App 偷取渲染后的 HTML）。
- 不做实时教务（选课、查成绩）——只做课表展示。
- 不做跨校通用——校区时间表只内置了「渭水 / 本部」两个。

---

## 2. 核心难点（为什么不能简单抓接口）

| 难点 | 具体表现 |
|------|---------|
| JS 依赖重 | 页面依赖 jQuery、beangle(`bg`)、underscore(`_`) 等库 |
| 动态渲染 | 课表数据靠 JS 运行后注入 `table0.activities`，原始 HTML 里没有 |
| WebView 加载不全 | 系统 WebView 无法正确加载教务系统的外部 JS 资源 → 白屏 |
| CAS 单点登录 | 要在 `ids.chd.edu.cn` 登录，再把会话带到 `bkjw.chd.edu.cn`，Cookie 跨域 |
| 课表编码诡异 | 时间用「53 位周数位图」表示，还有 +1 偏移（见 §6 的坑） |

---

## 3. 整体方案（一图流）

**核心思路：既然 WebView 能登录，那就让 WebView 把课表「渲染出来」，再把渲染后的 HTML 抠出来自己解析。** 外部 JS 加载不全？那就在 HTML 里**注入自己写的 JS 环境模拟**（假 jQuery / beangle / underscore），骗过页面让它正常渲染。

```
用户在 WebView 登录 CAS
        │
        ▼  (onPageFinished 状态机驱动)
┌─────────────────────────────────────────────┐
│ CAS_LOGIN → EAMS_HOME → EXTRACT_HOME_HTML  │
│          → COURSE_TABLE → DONE             │
└─────────────────────────────────────────────┘
        │
        │  shouldInterceptRequest 拦截 home.action / courseTableForStd 的 HTML
        │  └─ ScriptInjector 注入 jQuery/bg/_/CourseTable/TaskActivity 模拟脚本
        ▼
  页面正常 JS 渲染（table0.activities 被填充）
        │
        │  waitForPageReady 轮询检测 table0.activities 就绪
        │  evaluateJavascript 抠出 document.documentElement.outerHTML
        ▼
  onCasLoginSuccess(courseTableHtml, homePageHtml)
        │
        ├─ ScheduleHtmlParser.parse(课表HTML) → List<CourseEntity> → Room 入库
        └─ parseCurrentWeek(首页HTML) → (学期, 当前周) → 反推学期开始日期 → DataStore
        ▼
  导航到 ScheduleScreen，离线展示
```

---

## 4. 架构蓝图

### 4.1 分层（标准 clean architecture 三层 + DI）

```
ui/            ← Compose 屏幕 + ViewModel（状态用 StateFlow）
  screens/login/    WebViewScreen（登录入口）、LoginViewModel、LoginUiState
  screens/schedule/ ScheduleScreen、ScheduleViewModel、ScheduleUiState
  components/       ScheduleGrid、CourseCard、WeekSelector
  navigation/       AppNavigation（起始目的地=Schedule）、Screen
domain/        ← 纯领域模型与接口（无 Android 依赖）
  model/      Course、Campus、CourseType、DayOfWeek、Semester
  repository/ ICourseRepository（接口）
data/          ← 实现
  remote/     EamsClient(OkHttp封装)、CasApi、EamsApi、CookieManager、ScheduleHtmlParser、ScriptInjector(在util)
  local/      database(Room: AppDatabase/CourseDao/CourseEntity)、preferences(DataStore: UserPreferences)
  repository/ CourseRepositoryImpl（整合网络+解析+存储）
di/            ← Koin 模块：networkModule / databaseModule / appModule
util/          ← ScriptInjector、TimeUtils、JsonUtils、WebViewLogger、Constants、NetworkUtils、JsCompatibilityPolyfill
```

### 4.2 登录状态机（在 `WebViewScreen.kt`）

```
CAS_LOGIN        加载 CAS 登录页，等用户登录
  │ 登录成功，跳到 eams 首页（首次重定向需 1.5s 后重载，避开"请不要过快点击"）
  ▼
EAMS_HOME        首页加载完成（shouldInterceptRequest 已注入脚本）
  ▼
EXTRACT_HOME_HTML 动态轮询"本周为…教学周"渲染完成 → 抠首页 HTML
  ▼
COURSE_TABLE     加载课表页 → 注入脚本 → waitForPageReady 等 table0.activities 就绪 → 抠课表 HTML
  ▼
DONE             回调 onCasLoginSuccess(课表HTML, 首页HTML)
```

**关键**：`shouldInterceptRequest` 拿到 HTML 后，用 `java.net.URL.openConnection()` 带 WebView 的 Cookie 重新抓一份原始 HTML，注入脚本后再作为响应返回给 WebView。CAS 登录页（`ids.chd.edu.cn`）不拦截。

### 4.3 课表解析（`ScheduleHtmlParser.kt`）

三条解析路径，按优先级 fallback：
1. **TaskActivity JS 数据**（主）：正则切 `var teachers[…]…var courseName="…"…new TaskActivity(…)` 块，提取教师/教室/53 位周位图/`index=day*unitCount+node`。
2. **`td.infoTitle` 单元格**（备）：JS 渲染后的表格单元格 `title` 属性。
3. **`table0.activities` JSON**（备）：`parseActivitiesJson`。

解析后做三步加工：
- **连续节次合并**：同一课同一星期相邻节次合成一张卡。
- **单双周识别**（`determineWeekType`）：活跃周全奇→单周，全偶→双周，否则每周。
- **多教学班合并**（`mergeSamePositionCourses`）：同名同位置多条记录（多教学班）合并活跃周与教室。

### 4.4 数据模型

| 存储 | 内容 |
|------|------|
| **Room**（`courses` 表，`CourseEntity`） | 课表行：name/teacher/location/dayOfWeek(1-7)/startWeek-endWeek/startNode-endNode/courseType/credit/remark/semester。`remark` 里塞 `weeksBitmap:0101…`（53 位） |
| **DataStore**（`UserPreferences`） | 登录态/学号/姓名/**当前学期**/**校区**/**当前教学周**/**学期开始日期**/上次解析周次/**冲突缓存**(JSON)/**水课列表**(JSON,按学期) |

`Course`（domain）↔ `CourseEntity`（data）双向转换。`remark` 里的位图是周次判断的**真值来源**——`Course.isWeekInRange()`、`hasTimeConflict()`、`getWeeksDisplayText()` 都从它提取，没有位图才回退到 `startWeek..endWeek` 范围。

---

## 5. 关键设计决策与理由

> 这节是「代码和 git log 不会告诉你的」部分。改这些地方前先看这里。

### 5.1 为什么用 WebView + 脚本注入，而不是纯接口抓课表？
CAS 有验证码/风控，纯接口登录极不稳定。**让用户在 WebView 里手动登录最可靠**，登录后 WebView 已经是「已登录 + 已渲染」的状态，直接抠 HTML 即可。外部 JS 加载不全就**自己写一套假环境**（`ScriptInjector`：jQuery + beangle(bg) + underscore(_) + CourseTable + TaskActivity）注入进去骗页面渲染。这是个 hack，但有效。

### 5.2 网络层就是 OkHttp（`EamsClient`）
当初规划过用 Retrofit（曾有个 `EamsService` 接口），但实际网络全是 OkHttp 手写 `Request`（`CasApi` / `EamsApi`，经 `EamsClient` 统一封装并绑 `CookieManager`）。文档治理后 Retrofit 依赖与空壳 `EamsService` 已删除——网络层没有别的抽象，改网络请求直接看 `data/remote/api/` 和 `client/EamsClient.kt`。

### 5.3 为什么周次信息塞在 `remark` 字段里当位图，不展开成行？
教务系统原始数据就是 53 位周位图（`000000001111111111000…`，每位代表一周）。展开成多行会丢掉「单双周 / 非连续周」的精确信息。**保留位图**才能精确判断「第 X 周是否有这门课」「两门课周次是否真的重叠」。代价是 `Course` 的多个方法都要从 `remark` 里正则抽数据——略丑但正确。

### 5.4 为什么冲突要预计算缓存（v74）？
课表切换周次时，若每次实时两两比对所有课程算冲突，周次滑动会卡。**导入课表时一次性预计算 1~maxWeek 每周的冲突课程 ID**，存进 DataStore（JSON），切周只读缓存。代价：导入时多花一点时间；缓存不完整（换学期/改课表）会自动重算。

### 5.5 为什么有两套版本号？
- **发布版本**：`v1.0 / v2.0 / v2.1 / v2.2`（git tag，面向用户）。当前 `v2.1` 与 `v2.2` 指向同一 commit。
- **内部迭代号**：代码注释里的 `[v25]…[v97]`，是开发过程中每次小改动的递增编号，**不是发布版本**。

`build.gradle` 的 `versionName` 曾长期停在 `1.0`（已修正为 `2.2`）。两套号并存容易混，详见 [OPEN-DECISIONS](./OPEN-DECISIONS.md#内部迭代版本号-vxx--与发布版本号两套并存要不要统一)。

### 5.6 为什么水课 / 冲突缓存 / 校区存 DataStore，不进 Room？
这些都是「少量、低频、键值型」的用户偏好/派生数据，不是课程实体。Room 适合结构化课程行；DataStore 适合这种零碎状态。水课和冲突缓存用**手写 JSON** 序列化（`UserPreferences` 里自己拼/解 JSON，没用 Gson）——够用就行。

---

## 6. 已知坑与历史遗留

> 这些都是「看着别扭但有原因 / 暂不动」的。完整清单与 revisit 触发条件见 [OPEN-DECISIONS.md](./OPEN-DECISIONS.md)。

- **位图偏移（v96 反复修正过）**：学校系统位图 **bitmap[0] = 第 0 周（预备周）**，不是第 1 周。所以解析时 `week = index + 1` 后还要 `-1` 修正；`Course.isWeekInRange()` 直接用 `bitmap[week]`（因为 bitmap 下标即周次）。改这块务必看 `ScheduleHtmlParser.parseWeeksBitmap` 和 `Course.isWeekInRange` 的注释。
- **`unitCount` = 11，但 `CourseTable` 默认 77**：学校每天 11 节课，解析固定用 11；`ScriptInjector` 里 `CourseTable` 构造的 `unitCounts || 77` 是占位，实际靠 `window.unitCount = 11`。
- **默认学期硬编码 `2024-2025-1`**（已解决 2026-07）：原散落在 `AppNavigation` / `LoginViewModel`，启动时 NavHost startDestination 带路径参数首次组合绑不上，fallback 到此硬编码 → 进错学期。现 `AppNavigation` 用无参 `schedule_root` 跳板读 DataStore 真实学期后 navigate 规避；`LoginViewModel.onCasLoginSuccess` 先解析真实学期再入库。详见 [SEMESTER-SWITCH.md](./SEMESTER-SWITCH.md)。
- **`getStudentId` URL**：必须 GET `courseTableForStd.action`（不带感叹号）；误用 `!courseTable.action` 会 500（该 action 须 POST）。详见 [SEMESTER-SWITCH.md](./SEMESTER-SWITCH.md)「getStudentId 坑」。
- **POST `!courseTable.action` 须带 `setting.kind=std`**：beangle 的 resource type，缺则 500 `Resource type:null`。`getCourseTableHtml` formBuilder 必须加此字段。详见 [SEMESTER-SWITCH.md](./SEMESTER-SWITCH.md)「setting.kind 坑」。
- **NavHost startDestination 带路径参数首次组合绑不上**：`schedule/{semester}` 的 `{semester}` 在 startDestination 首次组合时 `getString` 返回 null（正常 navigate 不复现）。用无参 `schedule_root` 跳板规避。切换学期导航勿用 `launchSingleTop`（会复用 entry、ViewModel 不重建），改 `popUpTo(route){inclusive=true}` 强制新建 entry。
- **OkHttp cookie 内存态重启丢失**（v113 修）：`CookieManager.cookieStore` 纯内存，App 重启即丢；WebView cookie 持久化但登录时未 `flush()` 写盘。重启后"获取其他学期"走 OkHttp 报"登录已过期"（同步走 WebView 不受影响）。处置：`syncFromWebView` 内 `flush()` 写盘 + ScheduleRoot 跳板启动时同步 + `fetchSpecifiedSemester` 前兜底同步。详见 [SEMESTER-SWITCH.md](./SEMESTER-SWITCH.md)「cookie 持久化坑」。
- **非当前学期表头日期错**（v113 修）：`semesterStartDate` 全局单值只存当前学期的，`fetchSpecifiedSemester` 不存指定学期开始日期，非当前学期表头用当前学期的算 → 全错。处置：`fetchSpecifiedSemester` 不再 `saveCurrentSemester`；`ScheduleViewModel` 判断 `semester == currentSemester`，非当前学期 `weekStartDate=null`（表头只显示周几）+ `actualCurrentWeek=null`。详见 [SEMESTER-SWITCH.md](./SEMESTER-SWITCH.md)「非当前学期表头日期坑」。
- **i18n 不完整**：`values-en/strings.xml` 缺水课等字符串，英文环境下回退中文；实际界面以中文为主。

---

## 7. 心智模型（改代码时的地图）

| 我想… | 去哪改 |
|------|--------|
| 加一个新课表相关功能（UI） | `ui/screens/schedule/`（Screen + ViewModel + UiState） |
| 改登录/抓取流程 | `ui/screens/login/WebViewScreen.kt`（状态机）+ `LoginViewModel.onCasLoginSuccess` |
| 改课表解析（数据不对） | `data/remote/parser/ScheduleHtmlParser.kt` + `util/ScriptInjector.kt` |
| 加/改网络请求 | `data/remote/api/`（`CasApi`/`EamsApi`，OkHttp 裸调）+ `data/remote/client/EamsClient.kt` |
| 改本地存储 | 课程→`data/local/database/`（Room）；偏好/缓存→`data/local/preferences/UserPreferences.kt`（DataStore） |
| 改依赖注入 | `di/`（Koin：networkModule/databaseModule/appModule） |
| 改课表格子怎么画 | `ui/components/ScheduleGrid.kt` + `CourseCard.kt` |
| 看常量（URL/超时/周数） | `util/Constants.kt` |
| 看课表怎么判定某周有课/冲突 | `domain/model/Course.kt`（位图逻辑） |

**改解析相关的第一原则**：周次判断永远以 `remark` 里的 `weeksBitmap` 为准，位图下标 = 周次（bitmap[0] 是预备周）。
