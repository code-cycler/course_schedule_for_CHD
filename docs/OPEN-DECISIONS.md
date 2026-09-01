# Open Decisions

未决问题的 living list。每条有可逆的当前处置和明确的 revisit 触发条件——让"以后再说"不变成"永远没说"。

> **已解决 / 已决策（已移出本清单，详见 git log）：**
> - 未用 Retrofit 依赖 + 空 `EamsService.kt` — 2026-06 删除（grep 证零引用）
> - GeckoView 注释残留 — 2026-06 清理
> - 默认学期硬编码 `"2024-2025-1"` — 2026-07 分两步彻底解决：①同步入库用真实学期（`LoginViewModel.onCasLoginSuccess` 先 `parseCurrentWeekFromHtml` 再入库，07-12）；②启动读真实学期不再靠 NavHost startDestination 带路径参数（首次组合 `{semester}` 绑不上 → fallback 硬编码），改无参 `schedule_root` 跳板读 DataStore 后 navigate（07-13）。设计见 [SEMESTER-SWITCH.md](./SEMESTER-SWITCH.md)。
> - Navigation Compose startDestination 路径参数首次组合绑不上 — 2026-07-13 规避。`NavHost(startDestination="schedule/2025-2026-2")` + `composable("schedule/{semester}")` 首次组合时 `arguments.getString("semester")` 返回 null（正常 navigate 不复现，故长期未发现）。根因未深究（疑 Navigation 版本行为）；处置：加无参 `Screen.ScheduleRoot` 作 startDestination，读 DataStore 后 navigate 规避。可逆——升级 Navigation 后想还原，直接回退 `AppNavigation.kt`。
> - getStudentId 用错 URL 拿 500（"获取其他学期"长期失败、误报 Cookie 过期）— 2026-07-13 修复。`getStudentId()` 原 GET `courseTableForStd!courseTable.action`（须 POST），改 GET `courseTableForStd.action`（不带感叹号）。详见 [SEMESTER-SWITCH.md](./SEMESTER-SWITCH.md)「getStudentId 坑」。
> - POST `!courseTable.action` 缺 `setting.kind=std` 致 500（`Resource type:null`）— 2026-07-14 修复。`getCourseTableHtml` formBuilder 加 `.add("setting.kind", "std")`。详见 [SEMESTER-SWITCH.md](./SEMESTER-SWITCH.md)「setting.kind 坑」。
> - ScheduleViewModelTest 套件腐烂（原基于 v37/v61 旧 API、整个类 @Ignore）— 2026-07 重写（commit 419ea3c），基于当前 API，7 个 @Test 全绿，去掉 @Ignore。
> - LoginScreen + 表单登录死代码 — 2026-06 删除（`LoginScreen.kt` / `CasApi.kt` / `CasLoginPage.kt` + `LoginViewModel` 表单方法 + `ICourseRepository.login` + `LoginResult` + 对应测试）。命令行编译验证通过。
> - AGP 9.0 命令行构建失败 — 2026-06 解决。根因：Gradle daemon 缓存了旧 JVM 的代理配置（`127.0.0.1:7890`）；处置：`./gradlew --stop` 重启 daemon + `settings.gradle.kts` 加阿里云镜像 + 补 `local.properties`（SDK 路径，gitignore）。
> - 重启后"获取其他学期"提示登录已过期 — 2026-07-14 修复（v113）。根因：OkHttp `cookieStore` 纯内存重启丢 + 登录未 `CookieManager.flush()` 写盘。处置：`syncFromWebView` 内 `flush()` + ScheduleRoot 跳板启动同步 + `fetchSpecifiedSemester` 前兜底同步。详见 [SEMESTER-SWITCH.md](./SEMESTER-SWITCH.md)「cookie 持久化坑」。
> - 非当前学期表头日期错 — 2026-07-14 修复（v113）。根因：`semesterStartDate` 全局单值只存当前学期的，`fetchSpecifiedSemester` 不存指定学期开始日期。处置：删 `fetchSpecifiedSemester` 的 `saveCurrentSemester`；`ScheduleViewModel` 判断 `semester == currentSemester`，非当前学期 `weekStartDate=null`+`actualCurrentWeek=null`（表头只显示周几）。详见 [SEMESTER-SWITCH.md](./SEMESTER-SWITCH.md)「非当前学期表头日期坑」。
> - 签到辅助真实 TronClass 通知监听 — 2026-07 实现（阶段二）。第一阶段以设置页「模拟触发」按钮占位、本条 deferred；阶段一 Mock 验证稳定后接入 `CheckInNotificationListener`（NotificationListenerService，包名 `com.wisdomgarden.trpc` + 关键字「签到/考勤/点名」识别，15s 防抖）。触发逻辑抽到 `CheckInTriggerCoordinator`，UI 模拟触发与后台通知监听共用同一套。

---

## 导入导出 JSON — 后端做完了，UI 没接线，要不要补入口？

**Status:** deferred（de-risk 评估为 two-way door，不强迫现在决定）
**Why deferred:** can't anticipate（不确定个人使用场景下是否真需要导入导出）
**Current placeholder:** `JsonUtils` + `ScheduleViewModel.exportSchedule()/importSchedule()` + `CourseRepositoryImpl` 实现完整保留；README/DESIGN 如实记录"后端完整、UI 未接线"，不当完整功能宣传。
**Reversibility:** 高 — 补 UI / 删后端 / 将来启动都随时可做、可逆。
**Trigger — revisit when:** 想做课表备份 / 换机迁移 / 分享给同学时。

---

## 内部迭代版本号 [vXX] — 与发布版本号两套并存，要不要统一？

**Status:** deferred
**Why deferred:** can't anticipate（不确定后续是否还想维护内部递增号）
**Current placeholder:** 代码注释里的 `[v25]…[v97]` 内部增量编号保留不动；README/DESIGN 明确区分「发布版本 v2.2（git tag）」与「内部迭代号 v97（代码注释，仅追踪开发改动）」是两套独立体系。
**Reversibility:** 高 — 只是注释/文档表述，随时可改。
**Trigger — revisit when:** 版本号混淆导致维护困难，或决定正式废弃内部号、改用 git commit 追踪改动。


---

## 另一台荣耀设备日历同步「未生效」— 根因未诊断

**Status:** deferred（等待设备侧信息）
**Why deferred:** 信息不足——无该设备日志与日历 app 截图，无法区分「同步未成功执行」（权限/版本/流程问题）与「同步成功但日历 app 不显示」（本地日历可见性问题，与 Pixel 同因或 MagicOS 版本差异）。
**Current placeholder:** v119 修复（目标日历选择器 + 默认 Google 主日历）已覆盖「显示问题」分支；该设备升级新版后若仍不生效即指向「同步未成功」分支，走日志排查。参照物：验证成功的荣耀机在系统「日历账户管理」能看到本软件注册的账户（course_schedule_chd），不生效的设备可对照检查该入口。
**Reversibility:** 高——诊断出分支后按分支处置，无代码依赖。
**Trigger — revisit when:** 拿到该设备的导出日志或日历 app 截图；或该设备安装 v119+ 后复测的结果。
