---
mode: feature
wave: 1
stage: grill
created: 2026-09-13
status: archived
---
# 问卷 grill W01 · ux-perf-export 三层设计压测（D7 现实矛盾 + D 批边界 + B 批回归锚）

> 填写规则：
>
> 1. 每题勾选 `[x]`；默认单选，标「(多选)」可勾多个
> 2. ★ = 推荐选项，附推荐理由；选项排序 = 非推荐在前 → 🤔 逃生舱 → ❌ 跑偏标注 → ★推荐最后
> 3. 每题末尾固定出口：🤔（定不了 → 降风险协议，绝不重问）；❌（这题建立在 AI 对工件的误解上、问错对象，≥2 题被标 → 停波回炉）
> 4. 选项都不合适 → ✍️ 自由书写
> 5. 预勾选开关未启用，全部 `[ ]`

> **维度覆盖说明**：本卷 7 题覆盖 D1(Q2)/D2(Q3)/D4(Q3,Q4)/D5(Q1,Q5)/D6(Q7)/D7(Q1,Q6)；D3（替代方案）已审视——L1 选型表含 10 项被否决记录，无发现；D8（术语）已审视——「当前学期」等术语与代码语义一致，无冲突。这两维度无题不等于未压。

## Q1. D 批 `fetchRemoteCoursesOnly` 的实现基准：工件指向了死代码   [落盘: 工件修订建议 → L2-build#D 详细设计 + L1#模块边界表]

出题依据：核实发现 `fetchRemoteSchedule` / `fetchCourseTableWithOkHttp` 全仓**零调用点**（死代码）；在用的 OkHttp 拉取路径是 `fetchSpecifiedSemester` → `eamsApi.getCourseTableHtml(remoteId, null)`（`CourseRepositoryImpl.kt:469-501`，且前置 `cookieManager.syncFromWebView` 兜底）；`EamsApi.getCourseTablePage()`（GET 无参、默认当前学期）同样无调用点、真实可用性**未验证**。另：在用路径入参是远程 semesterId，需「本地学期串 → remoteId」解析（`getRemoteSemesterOptions`），工件接口规格未含此转换。

- [ ]  A. 改基于 `getCourseTablePage()`（GET 默认当前学期，最直接）—— 但它是从未在真实教务环境跑过的死代码路径，需实现期先真机实测
- [ ]  B. 维持工件原文（从 `fetchRemoteSchedule` 抽拉取段）—— 该方法零调用点，抽出来同样未经验证
- [ ]  🤔 我定不了 → 推迟/降风险
- [ ]  ❌ 这题跑偏(对象/框架理解错误)
- [X]  C. 改基于「`getRemoteSemesterOptions()` 解析当前学期 remoteId → `getCourseTableHtml(remoteId)`」—— 全程复用在用验证过的路径，代价是多一次学期列表请求 ★推荐（理由：静默场景第一条要求是可靠，fetchSpecifiedSemester 天天在用；接口规格补 remoteId 解析步骤）

- ✍️ 自定义: __________

## Q2. cookie 存活窗口：静默同步的实际覆盖率可能远低于直觉   [落盘: 工件修订建议 → L0#风险与约束]

出题依据：v113 修的是「cookie 重启不丢」（持久化写盘），但 CAS session 本身有时效（数小时到数天）。用户冷启动 App 时 cookie 大概率已过期 → 静默失败 no-op——功能实际只在「近期登录过」的窗口内有效。这是 D1 未言明假设，工件未提示。

- [ ]  A. 接受限制，L0 风险节补「更新检测覆盖率取决于 cookie 存活窗口」的预期说明（不加用户侧提示）
- [ ]  🤔 我定不了 → 推迟/降风险
- [ ]  ❌ 这题跑偏(对象/框架理解错误)
- [X]  C. A + 设置抽屉开关的描述文案写明「需近期登录过教务系统后生效」，用户预期对齐 ★推荐（理由：纯文档成本，把「功能时好时坏」的困惑源提前拆掉）

- ✍️ 自定义: __________

（曾考虑过的 B = 「cookie 过期时轻提示引导重登」——破坏你已拍板的「失败无动作」需求，不列为候选；若想要此行为请走 ✍️。）

## Q3. 静默确认入库会连带删除用户手动编辑/自定义课程（D4 爆炸半径 + D2 数据单向性）   [落盘: 工件修订建议 → L0#验收 D 节 + L2#D 详细设计第 4 步]

出题依据：确认入库 = `deleteBySemester` + `insertAll` 全量替换（与现有手动同步同款，`CourseRepositoryImpl.kt:397-406`）。用户经 `CourseEditorSheet` 手动改过/新增的课程在教务端不存在 → diff 判「删除」→ 用户点确认后被物理清除；`Course` 无来源字段，diff 无法区分「教务删了」与「本地自加的」。现有手动同步本就如此，但静默检查把该场景的暴露频率放大了。

- [ ]  A. 入库改「合并语义」（保留本地自定义课程）—— 要改现有入库链路 + 合并/冲突规则复杂化，违背 L1「复用现有入库」契约
- [ ]  B. 保持现状（删除清单里自然能看到自己的课，用户自行判断）—— 有数据但无警示，误确认即丢
- [ ]  🤔 我定不了 → 推迟/降风险
- [ ]  ❌ 这题跑偏(对象/框架理解错误)
- [X]  C. 确认对话框明示「确认后将以教务数据替换本地全部课程（含你手动添加/编辑的 N 门）」+ 差异清单对「本地独有」条目打标 ★推荐（理由：最小改动、零链路变更、用户知情后自主决策；与 ADR-0007「最后一道门留给人」原则一致)

- ✍️ 自定义: __________

## Q4. 静默确认框与手动同步的并发互斥   [落盘: 工件修订建议 → L2#D 详细设计]

出题依据：差异 AlertDialog 挂着时用户点顶栏「同步」进 WebView（`onNavigateToLogin`，`ScheduleScreen.kt:362-364`）→ WebView 同步完成后返回，差异框还挂着但数据已变（diff 过期，确认会二次覆盖）；反向场景同理。工件未覆盖此并发。

- [ ]  A. 不处理（低概率，用户自己会先取消）
- [ ]  🤔 我定不了 → 推迟/降风险
- [ ]  ❌ 这题跑偏(对象/框架理解错误)
- [X]  B. 手动同步导航时自动重置静默状态（差异框随状态消失，`onNavigateToLogin` 前置一行状态置 Idle）★推荐（理由：一行状态重置的成本消灭「过期 diff 二次覆盖」这类数据竞态，符合 D4 失败模式防御）

- ✍️ 自定义: __________

## Q5. 两个未写的边界：`currentSemester` 为 null + 拉取前 cookie 兜底   [落盘: 工件修订建议 → L2#D 详细设计第 1/3 步]

出题依据：`getCurrentSemester()` 返回 `String?`（未设置时 null，`CourseRepositoryImpl.kt:303-306`），工件未写 null 分支（跳过？报错？）；在用路径 `fetchSpecifiedSemester` 拉取前有 `cookieManager.syncFromWebView` 兜底（`CourseRepositoryImpl.kt:472`），工件的 `fetchRemoteCoursesOnly` 未提此前置。

- [ ]  A. null 时不做静默检查直接返回 + 拉取前补 `syncFromWebView` 兜底（与在用路径完全对齐）
- [ ]  🤔 我定不了 → 推迟/降风险
- [ ]  ❌ 这题跑偏(对象/框架理解错误)
- [X]  B. 同 A（两处都补）★推荐（理由：null 跳过是唯一安全语义；cookie 兜底是 v113 之后在用路径的标准动作，不补则静默成功率无谓降低）

- ✍️ 自定义: __________

## Q6. `lastSilentSyncAt` 的键型与项目范式冲突   [落盘: 工件修订建议 → L1#接口契约第 8 条 + L2 接口规格表]

出题依据：L1/L2 写 Long 键，但 `UserPreferences` **无 `longPreferencesKey` 先例**（时间戳均 String 存：`semester_banner_dismissed_date` 为 `LocalDate.toString()`；Int 键仅有 `current_week`）。

- [ ]  A. 新增 `longPreferencesKey`（DataStore 原生支持，只是项目内首例）
- [ ]  🤔 我定不了 → 推迟/降风险
- [ ]  ❌ 这题跑偏(对象/框架理解错误)
- [X]  B. 改 `stringPreferencesKey` 存 epoch millis 字符串（贴合项目既有 String 时间戳范式）★推荐（理由：范式一致性；首例 Long 键也无技术障碍，属低 stakes 双向门，若你偏好类型正确性选 A 同样合理——此题推荐强度弱）

- ✍️ 自定义: __________

## Q7. B 批 DoD「切周/加载/导出行为目视不变」是弱判据，且切周逻辑现无单测   [落盘: 工件修订建议 → L2#B 批 DoD]

出题依据：「目视不变」不可脚本化（D6）；现有 `ScheduleViewModelTest` 7 用例全是 fetch/clear 类，`onWeekSelected`/`coursesByWeek` 派生逻辑**没有直接单测**——线程迁移改的恰是这段代码，却没有迁移前行为锚点。

- [ ]  A. 保持现状（PerfLog 数据 + test 全绿已够）
- [ ]  🤔 我定不了 → 推迟/降风险
- [ ]  ❌ 这题跑偏(对象/框架理解错误)
- [X]  B. B 批先给 `onWeekSelected`/`coursesByWeek` 派生补行为单测（迁移前写、迁移后必须仍绿，作回归锚），DoD 的「目视不变」降级为补充冒烟 ★推荐（理由：迁移类改动最怕「无声变行为」，先立锚再动手是标准做法，多花约半小时）

- ✍️ 自定义: __________

---

## 补充声明

<任何想补充的话：新需求、格式反馈、范围调整、临时想到的风险……没有就留空。agent 处理时必读>

---

## 处理报告摘要（尾部追加 · 2026-09-13 归档留存）

- **逐题去向**：Q1→C、Q2→C、Q3→C、Q4→B、Q5→B、Q6→B、Q7→B，全部采纳 ★推荐；无 🤔/❌/✍️；补充声明空。
- **落盘**：7 项工件修订建议全部写入 TODO.md（R1–R7，含核验条件，待用户授权后落地工件）；Q2 存疑假设另立 OPEN-DECISIONS 条目（cookie 存活窗口，revisit 触发 = 实测命中率极低或用户反馈）。
- **质量信号**：❌ 跑偏率 0 / 🤔 逃生舱率 0 / ✍️ 自定义率 0；全采纳推荐 7/7。
- **覆盖度**：D1–D8 全覆盖（D3/D8 审视无发现）；工件关键声明（导出根因与修复、性能瓶颈与迁移、四批 DoD、D 静默同步全链路、2 条 ADR 立项理由）全部被至少一维度审视。终止条件满足，单波完成。
