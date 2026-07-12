# 切换学期功能 · 设计笔记

> **状态**：本地切换已实现（commit 10f4ef1）；「获取新学期」已实现（2026-07）。
> **触发实现**：随时可（命令行构建已恢复，`./gradlew assembleDebug` 可用）。
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

### 获取新学期（有坑，重点）

- `EamsApi.getSemesters()` 从课表页 HTML 下拉框拿学期列表，`value` 即教务系统内部 `semester.id`
- ⚠ **显示文本要单独解析**：`getSemesters()` 目前只返回 value（id），需补 Jsoup 解析 `<option>` 的文本（"2025-2026学年第2学期"）供用户看
- ⚠ **ID ↔ 本地字符串映射**：教务系统 `semester.id`（数字，如 `42`）≠ 本地 `semester` 字段（`"2025-2026-2"`）
  - 抓取：用 `semester.id` 调 `EamsApi.getCourseTableHtml(semester.id, studentId)`
  - 入库：`semester` 字段用本地格式（从下拉框文本 `(\d{4})-(\d{4})学年第(\d)学期` 转换，复用 `ScheduleHtmlParser.parseCurrentWeek` 的正则思路）
- 依赖 WebView 登录后的 Cookie（未登录抓不到；Cookie 过期时抓取失败，提示「登录已过期，请重新同步」）

### 获取新学期 · 最终决策（2026-07 第三轮 grill）

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

有了切换功能 + `getAllSemesters`，首次启动可默认选「最近学期」（`getAllSemesters` 降序取第一个），**不再用硬编码 `"2024-2025-1"`**。→ OPEN-DECISIONS 的「默认学期硬编码」条因此移除。

## 涉及文件（预估）

| 文件 | 改动 |
|------|------|
| `ui/screens/schedule/ScheduleScreen.kt` | 学期选择器 UI（FilterChip + Dialog + 「获取其它学期」入口） |
| `ui/screens/schedule/ScheduleViewModel.kt` | `semester` 改内部状态；`onSemesterChanged`；`fetchNewSemester(semesterId)` |
| `ui/screens/schedule/ScheduleUiState.kt` | 加 `allSemesters` / `currentSemester` 状态 |
| `data/remote/api/EamsApi.kt` | `getSemesters()` 补 option 文本解析，返回 `List<SemesterOption(id, label)>` |
| `data/repository/CourseRepositoryImpl.kt` | `fetchSpecifiedSemester(semesterId, localSemester)` |
| `domain/repository/ICourseRepository.kt` | 接口方法 |
| `AppNavigation` / 首次启动 | 默认学期改为「最近学期」 |

## 风险

- 命令行构建已恢复（`./gradlew assembleDebug` 可用），实现后可直接验证。
- `semester.id` 映射若解析不准，会把课表存到错误的 semester 键下——抓取后要校验课程数 > 0。
