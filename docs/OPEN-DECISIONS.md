# Open Decisions

本文档治理（2026-06）期间识别出、但**未在本次解决**的问题。每条都有可逆的当前处置和明确的 revisit 触发条件——目的是让"以后再说"不会变成"永远没说"。

本次治理范围是「文档 + 修事实性错误」，不动架构与依赖，因此下列代码层面的历史遗留只做记录、不改动。

---

## 导入导出 JSON — 后端做完了，UI 没接线，要不要补入口？

**Status:** deferred
**Why deferred:** can't anticipate（不确定个人使用场景下是否真需要导入导出）
**Current placeholder:** `JsonUtils` + `ScheduleViewModel.exportSchedule()/importSchedule()` + `CourseRepositoryImpl` 实现完整保留；README/DESIGN 如实记录"后端完整、UI 未接线"，不当完整功能宣传。
**Reversibility:** 高 — 补 UI 只是加按钮 + 文件选择器，约几小时。
**Trigger — revisit when:** 想做课表备份 / 换机迁移 / 分享给同学时。

---

## 内部迭代版本号 [vXX] — 与发布版本号两套并存，要不要统一？

**Status:** deferred
**Why deferred:** can't anticipate（不确定后续是否还想维护内部递增号）
**Current placeholder:** 代码注释里的 `[v25]…[v97]` 内部增量编号保留不动；README/DESIGN 明确区分「发布版本 v2.2（git tag）」与「内部迭代号 v97（代码注释，仅追踪开发改动）」是两套独立体系。
**Reversibility:** 高 — 只是注释/文档表述，随时可改。
**Trigger — revisit when:** 版本号混淆导致维护困难，或决定正式废弃内部号、改用 git commit 追踪改动。

---

## GeckoView 注释残留 — v47 已切系统 WebView，旧注释仍在误导

**Status:** deferred
**Why deferred:** experience gap（清理需逐一确认每处注释是否真无害，超出本次"修事实性错误"范围）
**Current placeholder:** 注释保留不动；DESIGN 的「已知坑与历史遗留」章节明确指出 `ICourseRepository` / `CourseRepositoryImpl` / `AppNavigation` / `app/build.gradle.kts:57` 的 GeckoView 字样是历史残留，实际登录用系统 WebView。
**Reversibility:** 高 — 删注释零风险。
**Trigger — revisit when:** 重构登录/网络层时顺手清理。

---

## 未用的 Retrofit 依赖 + 空 EamsService.kt — 引了却没用

**Status:** deferred
**Why deferred:** can't anticipate（不确定未来是否用 Retrofit 重构网络层）
**Current placeholder:** Retrofit 依赖与 `EamsService.kt`（`// TODO: 阶段二实现` 空壳）保留不动；DESIGN 如实说明实际网络经 `EamsClient` 封装的 OkHttp（`CasApi` / `EamsApi` 裸调），README 技术栈不再写 Retrofit。
**Reversibility:** 中 — 移除依赖要确认无反射引用；保留则轻微增加 APK 体积。
**Trigger — revisit when:** 决定网络层是否引入 Retrofit，或确认永远不用则删依赖 + 删空文件。

---

## LoginScreen + 表单登录是死代码 — 未接入导航，实际只走 WebView

**Status:** deferred
**Why deferred:** experience gap（删除前需确认 `LoginViewModel.login()` / `onWebViewLoginSuccess()` / `onFetchCourseTable()` 等表单时代方法确实无人调用，超出本次范围）
**Current placeholder:** `LoginScreen.kt`（全英文账号密码表单）及其触发的 `CasApi.login()` 表单登录路径保留不动。经 grep 确认 `LoginScreen` 在 `app/src/main` 内零调用——`AppNavigation` 的 Login 路由直接渲染 `WebViewScreen`。README/DESIGN 如实说明"唯一登录路径是 WebView CAS 登录"。
**Reversibility:** 高 — 删死代码零功能影响，但需顺手清 `LoginViewModel` 里无人调用的方法。
**Trigger — revisit when:** 下次清理登录模块，或决定彻底移除表单登录分支时。

---

## 默认学期硬编码 "2024-2025-1" — 过时占位散落多处

**Status:** deferred
**Why deferred:** can't anticipate（不确定要不要做成"学期选择器"或"自动推断"）
**Current placeholder:** `AppNavigation` / `LoginViewModel` / `Screen.kt` 中多处 `?: "2024-2025-1"` 占位保留不动。实际登录后 `parseCurrentWeekFromHtml` 会从首页解析真实学期（如 `2025-2026-2`）覆盖保存；只有"从未登录"的首次启动才会落到这个过时默认值。DESIGN 记录此行为。
**Reversibility:** 高 — 改字符串/改推断逻辑都很局部。
**Trigger — revisit when:** 出现"新学期开学后默认学期不对"的用户感知问题，或决定加学期选择 UI。
