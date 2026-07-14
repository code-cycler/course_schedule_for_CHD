# 切换学期功能 · 设计笔记

> **状态**：本地切换 + 获取新学期已实现（2026-07）；**2026-07-12 用 playwright MCP 实测 `bkjw.chd.edu.cn`，确认真实 DOM 与数据源，修正原"从课表页 HTML `<option>` 解析"的错误假设**。
> 诞生于 [OPEN-DECISIONS](./OPEN-DECISIONS.md) 的「默认学期硬编码」条——决定用这个功能顺带解决它。

## 需求

完整的多学期管理（用户选定「两者都要」）：
1. **本地切换**：查看 Room 里已存的不同学期课表
2. **获取新学期**：抓取一个尚未入库的学期课表

## 功能形态

课表页顶部加学期选择器（复用现有校区切换的 `FilterChip + AlertDialog` 模式）：

```
┌──────────────────────────────────┐
│ 📅 2025-2026 第2学期 ▼        🔄 │   ← 学期选择器（新增）
├──────────────────────────────────┤
│ [渭水]   第12周 ◀▶   [周末]       │
│   课表网格...                     │
└──────────────────────────────────┘

点 ▼ 弹出：
  ○ 2024-2025 第2学期（本地）
  ○ 2025-2026 第1学期（本地）
  ● 2025-2026 第2学期（当前）
  ──────────────────────
  + 获取其它学期...      ← 走抓取
```

## 技术设计

### 本地切换（简单）

- `ScheduleScreen` 加学期 `FilterChip`（放在 TopAppBar 或周选择器行）
- `ScheduleViewModel`：`semester` 从「导航参数」改为「内部可变状态」，新增 `onSemesterChanged(semester)`；切换时 `loadSchedule(newSemester)` + 重算冲突缓存
- 数据源：`CourseDao.getAllSemesters()`（已有）

### 获取新学期（重点 · 2026-07-12 MCP 实测修正）

#### 课表页学期切换的真实机制

课表页顶部 `courseTableForm` 表单里，"学年学期"**不是 `<select>` 下拉框**，而是 jQuery `semesterCalendar` 组件：

```html
<form id="courseTableForm" method="post" action=".../courseTableForStd.action">
  <input type="hidden" name="semester.id" value="262" id="semesterCalendar_target">  ← 提交用
  <input class="calendar-text" title="学年学期" readonly value="2026-2027学年1学期">  ← 显示用
  <input type="submit" value="切换学期" onclick="searchTable();return false;">        ← 提交按钮
  <input type="hidden" name="ids" value="201702">                                     ← 学生ID
</form>
```

点"切换学期" → `searchTable()` → `bg.form.submit(form, "courseTableForStd!courseTable.action")`，POST `ids` + hidden 的 `semester.id`。**这与 App 现有 `getCourseTableHtml()` 的 POST 逻辑完全一致**（`ids` + `semester.id`），所以"抓指定学期课表"这条路本来就对。

> **2026-07-13 补 getStudentId 坑（Bug1 根因）**：`getCourseTableHtml()` 的 POST 是对的，但它内部先调 `getStudentId()` 拿 `ids`，而 `getStudentId()` 原本 GET `courseTableForStd!courseTable.action`——这个 action 必须 POST，GET 直接 **500**，导致"获取其他学期"一直失败（被误报成"Cookie 过期"）。修复：`getStudentId()` 改 GET 入口页 `courseTableForStd.action`（不带感叹号），返回的 HTML 含 `<input name="ids" value="201702">`，现有正则 `name="ids"[^>]*value="(\d+)"` 可直接匹配（playwright MCP 实测确认）。同步流程走 WebView、不碰这条 OkHttp 死路径，所以一直没暴露。

> **2026-07-14 补 setting.kind 坑（Bug1 第二层根因）**：`getStudentId` URL 修好后，`getCourseTableHtml` 的 POST 仍 500。错误页：`Resource type:null / class java.lang.RuntimeException not supported`。根因：POST 表单漏 `setting.kind=std`（beangle 的 resource type，区分学生/班级/教师）。网页 form 真实字段 = `ignoreHead + setting.kind=std + startWeek + project.id + semester.id + ids`；OkHttp 原本只带 `ids + semester.id`。playwright XHR 实测：加 `setting.kind=std` 后 200 + TaskActivity/table0 课表齐全（min 集 `ids+semester.id+setting.kind` 即可，`project.id` 非必需）。修复：`getCourseTableHtml` formBuilder 加 `.add("setting.kind", "std")`。

#### 学期列表数据源 = `POST /eams/dataQuery.action`

⚠ **不是抠课表页 HTML！** 学期列表是 `semesterCalendar` 组件初始化时 AJAX 拉取的（`schoolYear` 不在 `document.documentElement.outerHTML` 里，已实测）。

- 请求：`POST http://bkjw.chd.edu.cn/eams/dataQuery.action`，body = `dataType=semesterCalendar`（最小参数，已验证；原页面带 `tagId&value&empty` 但非必需）
- 响应：**JS 对象字面量**（key 无引号，非标准 JSON）：
  ```
  {yearDom:"<tr>...", termDom:"<tr>...",
   semesters:{y0:[{id:72,schoolYear:"2015-2016",name:"1"},{id:73,schoolYear:"2015-2016",name:"2"}],
              y1:..., y6:[{id:202,...,"1"},{id:203,...,"2"}], y7:[{id:222,...},{id:242,...}], y8:[{id:262,...}]},
   yearIndex:"7", termIndex:"1", semesterId:"242"}
  ```
  - `semesters.yN[]`：每个 `{id, schoolYear, name}` = 教务系统一个学期（N=学年序号）
  - `semesterId`：**当前真实学期 id**（实测 = 242 = 2025-2026-2，与 App 时间推断一致）

#### 学期 id 不规律，必须查表（2026-07-12 实测）

| schoolYear-name | id |   | schoolYear-name | id |
|---|---|---|---|---|
| 2023-2024-1 | 162 |   | 2024-2025-1 | 202 |
| 2023-2024-2 | 182 |   | 2024-2025-2 | 203 |
| 2025-2026-1 | 222 |   | 2026-2027-1 | 262 |
| 2025-2026-2 | 242 |   |  |  |

相邻学期 id 差值不定（2024-2025-2=203 → 2025-2026-1=222 跳 +19；有的 +1 有的 +20），**不能靠规律推算**，必须每次查 dataQuery.action。新学期会动态新增 id。

#### 解析方式

响应非标准 JSON，用正则针对性提取三元组 + 当前 id，比"补引号转 JSON"健壮（响应里 `yearDom`/`termDom` 含 HTML 单/双引号会破坏整体 JSON 解析）：

```kotlin
// 每个 {id,schoolYear,name}
Regex("""\{id:(\d+),schoolYear:"([^"]+)",name:"(\d+)"\}").findAll(resp)
// 当前学期 id
Regex("""semesterId:"(\d+)"""").find(resp)
```

#### App 实现路径（"复用同步课程的 OkHttp+Cookie"）

- `EamsApi.getSemesterOptions()`：POST dataQuery.action（走 `EamsClient` + `CookieManager`，登录态 Cookie 自动带），正则提三元组，返回 `SemesterOption(remoteId=id, label="schoolYear-name")`；label 已是本地串格式
- 选定学期 → `getCourseTableHtml(remoteId, null)`（已实现且正确）
- 入库：`semester` 字段 = label（"2025-2026-2" 格式，无需再转）
- 依赖登录 Cookie（未登录/Cookie 过期 → dataQuery 返回空或重定向登录页 → 提示「登录已过期，请重新同步」）

> **已废弃的错误假设**：~~从课表页 HTML 的 `<select name=semester>` 解析 `<option>` 的 value+text~~。课表页无此 `<select>`，原 `getSemesterOptions` 找 `#semester option / select[name=semester] option` 必然返回空——这就是"无法获取其他学期"的根因。

### 获取新学期 · 最终决策（2026-07 grill）

- **入口可见性**：学期选择对话框的「+获取其它学期」仅在本地已有 ≥1 个学期（即登录过）时显示。
- **列表范围 = 智能筛选 4 个**，不是教务系统全部历史学期：
  - **当前学期**由当前日期推断（时间判断规则，见下）
  - **前 2 个** + **当前** + **往后 1 个**，共 4 个
  - 其余历史学期（如 2023-2024-x）不列出
- **时间判断规则**（注意命名歧义，见下方备忘；**8 月归秋季**）：
  - 2–7 月 → 春季第二学期 `(year-1)-year-2`
  - 8–12 月 → 秋季第一学期 `year-(year+1)-1`（8 月起即切新学年秋季）
  - 1 月 → 秋季收尾 `(year-1)-year-1`（去年 9 月开学的那个学期）
  - 示例：今天 2026/7/11 → 当前 = `2025-2026-2`；列出 `[2024-2025-2, 2025-2026-1, 2025-2026-2, 2026-2027-1]`
- **已入库的学期**：标「已入库」灰显，不可重复抓取。
- **抓取方式**：手动点一个抓一个（不批量）。
- **抓取后**：自动切换到新学期 + Toast「已获取 X，N 门课程」；校验课程数 > 0，否则提示抓取失败。

> ⚠ **命名歧义备忘**：「上半学期/下半学期」按用户口径 = 上半年/下半年进行的学期（≠ 常规的「第一学期=上学期」）。本地 `semester` 字段一律用规范串 `"2025-2026-2"`，代码与存储中不使用「上半/下半」叫法，仅在 UI 文案与本文档里解释规则时提及。

### 顺带解决

有了切换功能 + `getAllSemesters`，首次启动可默认选「最近学期」（`getAllSemesters` 降序取第一个），**不再用硬编码 `"2024-2025-1"`**。

> **2026-07-12 补**：`LoginViewModel.onCasLoginSuccess` 里 `defaultSemester = "2024-2025-1"` 硬编码导致同步时课程全存进 `2024-2025-1` key，而真实学期只存进偏好——课程 key ≠ 偏好学期，启动错位。本次修复：同步时先解析真实学期（首页 `parseCurrentWeekFromHtml`）再用真实 key 入库。→ OPEN-DECISIONS 的「默认学期硬编码」条因此移除。

## 涉及文件

| 文件 | 改动 |
|------|------|
| `ui/screens/schedule/ScheduleScreen.kt` | 学期选择器 UI（FilterChip + Dialog + 「获取其它学期」入口） |
| `ui/screens/schedule/ScheduleViewModel.kt` | `fetchRemoteSemesterOptions` / `fetchSpecifiedSemester` |
| `ui/screens/schedule/SemesterInference.kt` | `inferCurrentSemester` / `candidateSemesters`（纯函数） |
| `data/remote/api/EamsApi.kt` | `getSemesterOptions()` **POST dataQuery.action + 正则解析 JS 字面量** |
| `data/repository/CourseRepositoryImpl.kt` | `fetchSpecifiedSemester(semesterId, localSemester)` |
| `ui/screens/login/LoginViewModel.kt` | **修 defaultSemester 硬编码**：真实学期入库 |
| `domain/repository/ICourseRepository.kt` | 接口方法 |

## 风险

- 命令行构建可用（`./gradlew assembleDebug`）。
- `semester.id` 映射若解析不准，会把课表存到错误的 semester 键下——抓取后要校验课程数 > 0。
- dataQuery.action 响应格式若教务系统升级后变化（如 key 加引号、结构改），正则需相应调整——靠单测 `getSemesterOptions` 解析固定样本兜底。
- Cookie 过期时 dataQuery.action 可能返回登录页 HTML（不含 semesters）→ 解析得 0 条 → UI 提示「登录已过期」。
- `getStudentId()` 必须 GET `courseTableForStd.action`（不带感叹号）；误用 `!courseTable.action` 会 500（2026-07-13 已修，见上「getStudentId 坑」）。
- POST `!courseTable.action` 必须带 `setting.kind=std`（beangle resource type），否则 500 `Resource type:null`（2026-07-14 已修，见上「setting.kind 坑」）。
