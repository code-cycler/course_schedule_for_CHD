# 切换学期功能 · 设计笔记

> **状态：已决策，待实现**（2026-06 第二轮 grill 确定）。
> **触发实现**：解决 AGP 9.0 命令行构建问题后，或在 Android Studio 中实现时。
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
- 依赖 WebView 登录后的 Cookie（未登录抓不到）

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

- 实现后**无法命令行编译验证**（AGP 9.0 问题），需在 Android Studio 中测试。
- `semester.id` 映射若解析不准，会把课表存到错误的 semester 键下——抓取后要校验课程数 > 0。
