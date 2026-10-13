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

## 静默同步更新检测覆盖率受 cookie 存活窗口限制 — 预期管理已落文档，实际覆盖率待实测

**Status:** deferred（grill-W01 Q2，2026-09-13）
**Why deferred:** cannot anticipate（CAS session 实际存活时长未知——数小时到数天不等，且教务系统策略可变）。v113 修的只是「cookie 重启不丢」，session 时效本身无法由 App 延长。
**Current placeholder:** 设计已按用户裁决落地预期管理——L0 风险节说明「覆盖率取决于 cookie 存活窗口，冷启动多为静默 no-op」+ 设置开关文案「需近期登录过教务系统后生效」（TODO R2）。静默失败语义不变（无 toast/无引导）。
**Reversibility:** 高——若实测覆盖率过低，可改「过期轻提示引导重登」（破坏静默）或接受现状，纯行为开关级改动。
**Trigger — revisit when:** D 批真机实测后发现 cookie 存活期过短（如登录后不足半天即失效）导致静默同步实际命中率极低时；或用户反馈「功能感觉没生效」时。

---

## 另一台荣耀设备日历同步「未生效」— 根因未诊断

**Status:** deferred（等待设备侧信息）
**Why deferred:** 信息不足——无该设备日志与日历 app 截图，无法区分「同步未成功执行」（权限/版本/流程问题）与「同步成功但日历 app 不显示」（本地日历可见性问题，与 Pixel 同因或 MagicOS 版本差异）。
**Current placeholder:** v119 修复（目标日历选择器 + 默认 Google 主日历）已覆盖「显示问题」分支；该设备升级新版后若仍不生效即指向「同步未成功」分支，走日志排查。参照物：验证成功的荣耀机在系统「日历账户管理」能看到本软件注册的账户（course_schedule_chd），不生效的设备可对照检查该入口。
**Reversibility:** 高——诊断出分支后按分支处置，无代码依赖。
**Trigger — revisit when:** 拿到该设备的导出日志或日历 app 截图；或该设备安装 v119+ 后复测的结果。

---

## 签到辅助 v2 轮询通道做不做 — 等待假设 B/C 抓包验证

**Status:** deferred（双向门推迟；checkin-assist-v2 L0 W00 要点 3 采纳，2026-10-13）
**Why deferred:** 知识缺口——畅课签到 API（假设 B）与登录态来源（假设 C）均未抓包验证；TronClass 社区通用逆向资料与 CHD 实际部署（畅课 v2.16.0）可能不一致。
**Current placeholder:** NLS 通知监听为主通道（现有 `CheckInNotificationListener` 加固）；轮询通道在 L1/L2 中以「验证闸通过」为前提条件性存在，闸门不过则整通道不做（触发源管线抽象保留，未来可插）。若闸门通过后实现，**执行载体已定**：课时有窗前台服务（`dataSync` 型），WorkManager（15 分钟粒度不足）与 AlarmManager 链（复杂易杀）已否决——见 [L1-contract-checkin-assist-v2.md](../harness/design/checkin-assist-v2/L1-contract-checkin-assist-v2.md) 选型表。验证行动项见 TODO.md「验证假设 B/C」。
**Reversibility:** 高——双向门；不做轮询 = 退化为纯通知监听（现状加固版），随时可重启该方向零沉没成本。
**Trigger — revisit when:** 假设 B/C 抓包/调研完成——接口存在且登录态可复用 → 重启轮询设计；确认不存在/不可复用 → 本条移入「已解决」记录结论。

---

## 自动签到（调畅课接口完成签到）是否最终实现 — 单向门悬置

**Status:** deferred（单向门推迟，ADR-0001「不调用畅课签到接口」条款**继续有效**；checkin-assist-v2 L0 W00 要点 4 采纳，2026-10-13）
**Why deferred:** 双重不确定——技术侧假设 B（畅课签到提交接口）未验证；用户侧对学术诚信/封号风险明确犹豫（入口校准裁决「侧重监听健壮性，自动签到作为可选方向保留」）。
**Current placeholder:** 本期仅做触发源架构预留（触发管线留自动签到回调位）；不写任何签到提交代码；实验方向默认关。
**Reversibility:** 低——单向门；自动提交一旦发生不可撤回（已发出的签到请求无法收回），故闸门后置。
**Trigger — revisit when:** 三条件**同时**满足才开工：① 假设 B 抓包验证通过（存在可复用登录态的签到提交接口）；② 用户显式二次确认接受学术诚信与封号风险；③ 落新 ADR 修订 ADR-0001 对应条款并明示风险。强校验签到（二维码/手势/人脸/验证码）**永久排除**在自动完成之外（L0 范围硬约束）。
