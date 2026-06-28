# Open Decisions

未决问题的 living list。每条有可逆的当前处置和明确的 revisit 触发条件——让"以后再说"不变成"永远没说"。

> **已解决 / 已决策（已移出本清单，详见 git log）：**
> - 未用 Retrofit 依赖 + 空 `EamsService.kt` — 2026-06 删除（grep 证零引用）
> - GeckoView 注释残留 — 2026-06 清理
> - 默认学期硬编码 `"2024-2025-1"` — 决策用「切换学期功能」顺带解决，设计见 [SEMESTER-SWITCH.md](./SEMESTER-SWITCH.md)（待实现）
> - LoginScreen + 表单登录死代码 — 2026-06 删除（`LoginScreen.kt` / `CasApi.kt` / `CasLoginPage.kt` + `LoginViewModel` 表单方法 + `ICourseRepository.login` + `LoginResult` + 对应测试）。命令行编译验证通过。
> - AGP 9.0 命令行构建失败 — 2026-06 解决。根因：Gradle daemon 缓存了旧 JVM 的代理配置（`127.0.0.1:7890`）；处置：`./gradlew --stop` 重启 daemon + `settings.gradle.kts` 加阿里云镜像 + 补 `local.properties`（SDK 路径，gitignore）。

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

## ScheduleViewModelTest 测试套件腐烂 — 基于 v37/v61 之前的旧 API，无法编译

**Status:** deferred
**Why deferred:** experience gap（整个文件基于旧 API：构造缺 `userPreferences`（v61 加）、调 `refreshSchedule()`（v37 已删）、引用 `isRefreshing` 字段（已移除）；修复 = 基于当前 `ScheduleViewModel` 重写整套测试，需先理清 `loadSchedule` 的冲突缓存/教学周/校区逻辑再逐个 mock，工作量较大）
**Current placeholder:** `ScheduleViewModelTest.kt` 保留不动。**注意：这会导致 `./gradlew compileDebugUnitTestKotlin`（unit test 编译）失败**；但 `./gradlew assembleDebug`（main + APK）不受影响、编译通过，`CourseRepositoryImplTest` 等其它测试也正常。
**Reversibility:** 高 — 纯测试代码，重写不影响任何功能。
**Trigger — revisit when:** 想恢复 unit test 覆盖（如接 CI 跑 test），或下次大改 `ScheduleViewModel` 时顺手重写。
