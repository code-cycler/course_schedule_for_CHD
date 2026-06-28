# Open Decisions

未决问题的 living list。每条有可逆的当前处置和明确的 revisit 触发条件——让"以后再说"不变成"永远没说"。

> **已解决 / 已决策（已移出本清单，详见 git log）：**
> - 未用 Retrofit 依赖 + 空 `EamsService.kt` — 2026-06 第二轮 grill 删除（grep 证代码零引用）
> - GeckoView 注释残留 — 2026-06 第二轮 grill 清理（`CourseRepositoryImpl` / `ICourseRepository` / `AppNavigation` / `build.gradle.kts` 的误导注释；`WebViewScreen` 的 UA 字符串 `Mozilla/5.0…Gecko…` 属正常，保留）
> - 默认学期硬编码 `"2024-2025-1"` — 决策用「切换学期功能」顺带解决，功能设计见 [SEMESTER-SWITCH.md](./SEMESTER-SWITCH.md)（已决策，待有编译环境后实现）

---

## LoginScreen + 表单登录是死代码 — 未接入导航，实际只走 WebView

**Status:** deferred
**Why deferred:** experience gap（删除前需确认 `LoginViewModel.login()` / `onWebViewLoginSuccess()` / `onFetchCourseTable()` 等表单时代方法确实无人调用；且牵涉 `ICourseRepository.login` 接口与 `CasApi` 整个类的连带删除，无编译验证下风险偏高）
**Current placeholder:** `LoginScreen.kt`（全英文账号密码表单）及其触发的 `CasApi.login()` 表单登录路径保留不动。经 grep 确认 `LoginScreen` 在 `app/src/main` 内零调用——`AppNavigation` 的 Login 路由直接渲染 `WebViewScreen`。README/DESIGN/CLAUDE 如实说明"唯一登录路径是 WebView CAS 登录"。
**Reversibility:** 高 — 删死代码零功能影响，但需顺手清 `LoginViewModel` 里无人调用的方法、`ICourseRepository.login`、`CasApi` 与对应 DI 配置。
**Trigger — revisit when:** 有可用的编译环境（解决 AGP 9.0 命令行构建问题）后，或下次清理登录模块时。

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
