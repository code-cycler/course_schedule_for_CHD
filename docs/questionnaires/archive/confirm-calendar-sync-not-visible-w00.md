---
mode: feature
wave: 0
stage: confirm
created: 2026-09-01
status: pending
---
# 问卷 confirm W00 · 细节确认清单（AI 汇报理解，人核对）

> **本波是细节确认清单**（独立 wave 0）：把 AI 对本次行动细节的理解逐条列出，人只做「对/不对」核对。
>
> **作答规则**：
>
> - 勾 `[x]` = **理解正确**（按该理解执行）
> - 留空 `[ ]` = **理解有误或要改** → 该要点转正式题深究（或小波直接问）
> - 本波**不用 🤔**（对/不对二选一，无中间态）；「大体对但要改一两处」→ 留空，转正式题时在深究题里给正确值
>
> 来源标注于〔〕：**〔推断〕= AI 填的，重点核对**；〔用户原话 / 代码 / 文档〕= 有据。

## 细节确认清单

### 目标

- [X]  **1 本次行动的性质**：先诊断确认「日历里看不到课程」的根因（需要你在 Pixel 上实测一步），根因确认后再决定修复方案；**不是现在直接改代码**。〔skill 流程 + 推断〕
- [X]  **2 症状理解**：App 内同步流程本身报成功（日志 `SyncResult(successCount=204, failCount=0, reminderCount=204, earlyMorningCount=27)`），但你在系统日历软件里看不到任何课程事件。〔用户原话 + 日志 L938〕

### 输入（事实基础）

- [X]  **3 设备与版本**：Google Pixel 9a · Android 17 · 应用 v2.4 (1)。〔日志 L5-6〕
- [X]  **4 「系统日历软件」= Pixel 预装的 Google 日历（Google Calendar app）**——Pixel 出厂没有 AOSP 日历，默认只有它。〔推断〕
- [X]  **5 写入目标（关键代码事实）**：App 同步写入的是**应用自建的本地日历**（`ACCOUNT_TYPE_LOCAL`，账户名 `course_schedule_chd`，显示名「长安大学课程表」），**不是你 Google 账户下的日历**。〔CalendarSyncService.kt:46-51〕
- [X]  **6 日历创建方式合规**：日历通过 `CALLER_IS_SYNCADAPTER=true` + 匹配的账户参数创建，符合官方文档要求——创建路径本身没有缺陷。〔CalendarSyncService.kt:270-280 + developer.android.com Calendar Provider 文档核实〕
- [X]  **7 数据确实写进了系统**：204 条课程事件 + 204 条课前提醒 + 27 条早八提醒全部插入成功、0 失败 → `WRITE_CALENDAR` 权限已授予、数据在系统 CalendarProvider 里。〔日志 L938 + insert 全程无异常〕
- [X]  **8 设置里的 `calendarId=null` 无关**：这个字段是遗留物，全仓只有序列化/反序列化代码、没有任何业务逻辑读它，同步服务自己查/建日历——它不是故障原因。〔grep 全仓核实〕

### 依赖（外部事实）

- [X]  **9 外部行为事实**：`ACCOUNT_TYPE_LOCAL` 本地日历不同步到任何服务器；Google 官方帮助页明确「不能切换到本地账户」，Google 日历 app 对本地日历支持有限——已知典型表现就是**日历在系统里存在，但 app 不勾选显示、甚至不列出**。〔developer.android.com + support.google.com 搜索核实〕

### 初步诊断（假设，待你实测）

- [ ]  **10 主假设 H1**：Google 日历 app 的日历列表中，「长安大学课程表」未被勾选显示（或被归到「设备日历/Device」分组被忽略）——事件其实都在，勾选即见。〔推断〕
- [X]  **11 备选假设 H2**：你这版 Google 日历 app 根本不列出 `ACCOUNT_TYPE_LOCAL` 本地日历 → 必须改代码（写入你的 Google 账户日历）。〔推断〕

### 验证步骤（请你实测后在补充声明里写结果）

- [ ]  **12 验证路径**：打开 Google 日历 app → 点左上角 ☰ 三横线（或底部「日历」入口）→ 展开完整日历列表（往下翻，留意「设备日历 / Device」分组）→ 找「长安大学课程表」：
  - 找到了 → 勾选它，回到日历视图看课程是否出现（结果支持 H1）
  - 完全找不到 → 记下列表里实际有哪些日历（结果支持 H2）

### 边界

- [X]  **13 修复方案候选（根因确认后再选）**：A. 指引用户手动勾选（零代码）/ B. 默认改写入设备上主 Google 账户日历 / C. 设置里加「目标日历选择器」（顺带激活遗留的 calendarId 字段）。本次确认阶段不预先锁定方案。
- [X]  **14 不动的东西**：登录/课表同步主流程、上课时间计算、提醒事件的生成逻辑（若最终换目标日历，课程事件与提醒事件一起迁移）。

### 约束

- [X]  **15 兼容约束**：修复须兼容 Android 9 至 Android 17（minSdk 28），不引入新的第三方库。
- [X]  **16 历史背景（未知项）**：这台设备（或你其他设备）上，日历同步**以前是否成功显示过**？「从来没工作过」和「升级后坏了」指向不同原因。〔未知，请在补充声明告知〕

## 补充声明

<请把第 12 条的实测结果（找到/没找到「长安大学课程表」、勾选后是否可见）和第 16 条的历史背景写在这里；其他想补充的也一并写。>并没有找到，详见图；曾经实机验证可行，荣耀手机

---

## 处理记录（agent 填写，作答时忽略）

### 2026-09-01 解析（agent）

- 作答方式：用户直接编辑问卷文件 + QQ 提供两张实测截图（经 subagent 视觉分析）。
- 逐条去向：
  - 第 1–9、11、13–16 条勾选 → 理解确认，按此执行。
  - 第 10 条（H1「在列表但未勾选」）留空 → **证伪**：截图显示 Google 日历 app 侧边栏与设置页完全没有「长安大学课程表」条目、无「设备日历」分组。无需深究，H2 成立。
  - 第 12 条留空但补充声明给出结果 → 已执行：**找不到该日历**（侧边栏仅 4 个 Google 账户日历：我的日历、任务、生日、节假日，全部已勾选）→ 支持 H2。
  - 第 16 条补充声明：「曾经实机验证可行，荣耀手机」→ 用户先验结论，与本次证据链一致（荣耀系统日历 app 显示 `ACCOUNT_TYPE_LOCAL` 本地日历；Pixel 的 Google 日历 app 不列出），验证通过，作为根因佐证。
- confirm-list 统计：确认正确 14 / 留空纠正 2（第 10、12 条，均已被补充声明 + 截图闭环，无需转正式题）。
- 覆盖度：目标/输入/输出/约束/边界/依赖六要素全部确认；动态盲点（荣耀可行 vs Pixel 不可行）已闭环。
- **根因结论**：App 写入自建本地日历（`ACCOUNT_TYPE_LOCAL`）→ Pixel 的 Google 日历 app 不列出本地日历 → 事件在 CalendarProvider 中存在但不可见。荣耀手机（自家系统日历 app）显示本地日历，故曾可行。
- 遗留动作：修复方案（A 已排除 / B 写 Google 账户日历 / C 日历选择器）→ 小波直接问（≤3 题，AskUserQuestion）。
- status: pending → answered → processed，本波处理完毕后归档。

### 2026-09-01 小波速答转写（AskUserQuestion）

**Q：根因已确认：Google 日历 app（Pixel）不显示本地日历，修复需改写入目标。选哪个方案？**
用户答：**「选择器+默认主日历（推荐）」** —— 设置里加「同步目标日历」选择器（列出设备全部日历），默认写入 Google「我的日历」；荣耀等设备可切回「长安大学课程表」本地日历。顺带激活遗留 calendarId 字段。

（处理报告摘要将在修复实施完成后追加）

### 2026-09-01 行动摘要（修复实施完成）

- 修复方案（用户选定「选择器+默认主日历」）已实施，改动 4 文件：
  - `CalendarSyncService.kt`：新增 `DeviceCalendarInfo` + `queryDeviceCalendars()`（选择器数据源）+ `resolveTargetCalendarId()`（用户指定 → Google 主日历 → 回退自建本地日历）；事件写入加 `CUSTOM_APP_PACKAGE` 标记；同步前清理改为「自建本地日历整清 + 目标日历按标记清」；`deleteCalendar()` 替换为 `deleteAllAppEvents()`（不删日历本身，只删本应用事件）。
  - `ScheduleViewModel.kt`：`deviceCalendars` StateFlow + `loadDeviceCalendars()`；`shouldResyncCalendar` 加 `calendarId` 变更触发；删除按钮改走 `deleteAllAppEvents()`。
  - `SettingsDrawer.kt`：「同步目标日历」选择行 + 单选对话框（自动/设备日历列表，含主日历徽标、本应用本地日历标注）。
  - `ScheduleScreen.kt`：接线 deviceCalendars + onRefreshCalendars。
- 验证：`assembleDebug`/`assembleRelease` 编译通过、`test` 单测全绿（exit 0）。
- 留痕：DESIGN.md §6 新增「本地日历在 Google 日历 app 不可见」坑条目；OPEN-DECISIONS 新增「另一台荣耀设备未生效待诊断」条目。
- 真机验证（Pixel 9a 装新版 → 同步 → Google 日历 app 查看课程、切目标日历、删除事件不误删用户日程）**待做**——OD-13 原则：人类试玩通过才算完成。
- 遗留：`CUSTOM_APP_PACKAGE` 标记在 Android 17 真机上的写入/过滤行为需随真机验证确认（若真机发现标记不生效，回退方案为 description 前缀标记，已在 DESIGN.md 坑条目隐含）。
