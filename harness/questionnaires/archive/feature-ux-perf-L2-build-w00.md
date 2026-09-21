---
mode: feature
wave: 0
stage: L2-build
created: 2026-09-13
status: archived
---
# 问卷 L2-build W00 · Preview（决策默认值 yes/no 速答）

> **本波是 preview 预答层**（独立 wave 0）：把 AI 有明确默认倾向的构建层决策逐条列出，人只做 yes/no 速答。
>
> **作答规则**：
>
> - **预勾选 = opt-in 开关**（opt-in 默认关）：全部 `[ ]`，人逐条作答；勾 `[x]` = 采纳默认按默认落盘
> - **取消勾选（留空）= 不采纳** → 该要点转入 L2 W01 单独拷问
> - 本批无单向门要点（每批独立 git commit，全部可回退）
> - 本波**不用 🤔**；真定不了 → 取消勾选即可，转 W01 深究
>
> 默认来源标注于〔〕。

## 背景（L2 构建层盘问域）

L0 验收（A1–A4/B1–B3/C1–C6/D1–D5/回归）与 L1 契约（接口 8 条 + 选型表）已冻结。L2 定：分几步建、每步做什么、何时算完（阶段拆分 + 详细设计 + 接口规格 + 每阶段 DoD + 依赖预估）。

## 决策默认值清单

### 阶段拆分与内部顺序

- [X]  **1 四个实现批次 = A→B→C→D，每批独立 git commit + 独立 DoD**；A/B 相互独立、C 独立、D 依赖 B 完成（同动 `ScheduleViewModel`，串行防冲突）〔L1 第 18 条交付顺序 + 依赖事实〕
- [X]  **2 A 批步骤 = ① `Theme.kt` 配色常量提可见性 → ② 导出调用点显式主题包裹 + Surface 填底 → ③ 真机深浅两模式截图核验**（半天，含验证）〔L1 契约第 5 条的直接实现序〕
- [X]  **3 B 批步骤 = ① 先加 PerfLog 打点建立基线 → ② 线程迁移（Default/IO）→ ③ 重组优化（@Immutable + 参数稳定化）→ ④ 复测对比留档**（约 1 天）——先测量后优化，防「优化了不存在的问题」〔B1–B3 验收依赖前后对比数据〕
- [X]  **4 C 批步骤 = ① 骨架屏 → ② 网格视觉打磨 → ③ 周切换过渡 → ④ 统计面板（纯函数 + UI）→ ⑤ 文案中文化与无障碍补全**（1–1.5 天）〔C2–C6 顺序：视觉冲击大的先做，文案收尾〕
- [X]  **5 D 批步骤 = ① Repository 只读接口 → ② `ScheduleDiffCalculator` + 单测 → ③ ViewModel 状态机 + 节流 → ④ 差异 AlertDialog → ⑤ 开关 UI + DataStore 键**（1–1.5 天，含单测）〔契约 1–4/8 的实现序，纯函数先行可测〕

### 接口规格与测试策略

- [X]  **6 `fetchRemoteCoursesOnly` 失败不细分错误码**：`Result.failure(exception)` 透传，调用方只关心「成功/失败」两态（静默场景无错误分类需求）〔L1 契约第 3 条 Failed=NoUpdate 同表现〕
- [X]  **7 `ScheduleDiffCalculator` 单测五用例 = 全同 / 全新增 / 全删除 / 部分变更 / 多教学班合并形态对比**（合并后 Course 与旧库记录的对比是 v90 合并逻辑下的真实形态）〔红线：解析不动，diff 只对比最终形态〕
- [X]  **8 统计派生纯函数单测 = 正常课表 / 空课表 / 含水课标记 三用例**；不写 UI 仪器测试（项目无仪器测试先例，`connectedAndroidTest` 空转）〔契约第 7 条可单测要求〕
- [X]  **9 B 批可测性 = ViewModel 注入 dispatcher**（Koin 构造注入 Main/Default/IO 三个 dispatcher，单测用 TestDispatcher 替换），不在代码里硬编码 `Dispatchers.Default` 字面量〔withContext(硬编码) 会让 JVM 单测出现真实线程切换，现有 ScheduleViewModelTest 7 用例可能闪退/超时——标准做法注入〕

### 每批 DoD（全部含「不破坏现有功能」回归）

- [X]  **10 A 批 DoD**：深浅两模式真机截图人工核验（A1–A4 逐条）+ `./gradlew test` 全绿 + 分享 Intent/文件命名/FileProvider 路径回归不变〔L0 验收 A 节 + 回归〕
- [X]  **11 B 批 DoD**：PerfLog 三场景前后耗时对比留档（写入 commit message）+ `./gradlew test` 全绿（含 ScheduleViewModelTest 7 用例）+ 切周/加载/导出行为目视不变〔B1–B3〕
- [X]  **12 C 批 DoD**：C1–C6 逐条真机核验 + 新增单测全绿 + `./gradlew test` 全绿〔C 节验收〕
- [X]  **13 D 批 DoD**：D1–D5 逐条真机核验（含 cookie 失效静默分支、确认/取消两路径）+ DiffCalculator/统计单测全绿 + 手动同步按钮路径回归不变〔D 节验收〕
- [X]  **14 全批收尾 DoD**：`./gradlew test` + `assembleDebug` + `lintDebug` 全绿；真机冒烟（启动/切周/切学期/分享导出/设置抽屉）；minSdk 28 模拟器过一遍 A/D 关键路径（API 28 静态色 fallback 下导出配色一致性）〔L0 回归约束〕

### 依赖与预估

- [X]  **15 人力预估合计 ≈ 3.5–4.5 个工作日**（A 0.5 / B 1 / C 1.5 / D 1.5，含单测与真机验证）——无外部依赖阻塞，可单会话推进或分四次会话〔阶段独立 DoD 支持断点续作〕
- [ ]  **16 内部迭代号续接 [vXX]**：四批分别记 `[v121]`–`[v124]`（导出修复/性能/修缮/静默同步），发布版本随下一个 git tag〔CLAUDE.md 两套版本号约定〕

## 补充声明

<任何想补充的话……没有就留空。agent 处理时必读>

---

## 处理报告（尾部追加 · 2026-09-13 归档留存）

- **落盘**：要点 1–15（15 条采纳）→ `harness/design/ux-perf-export/L2-build-ux-perf-export.md`（阶段拆分表 + 依赖链 / 四阶段详细设计 / 接口规格表 + 单测清单 / 每阶段 DoD / 依赖预估）；要点 16（迭代号 [v121]–[v124]）留空 → 转 L2 闸门面板快问（已知最新 [v120]，起始号顺延待用户确认）。
- **新增文件**：L2-build-ux-perf-export.md。
- **异常**：无。**逃生舱**：无。**补充声明**：空。
- **preview 统计**：勾选采纳 15 / 留空 1（转快问）/ 转正式题 0；单向门 0；全采纳率 15/16 ≈ 94%。
- **下一波候选**：迭代号快问 + L2 层闸门；闸门过后进单次收尾面板（多线程开工/dogfood/压测/long-running 四项衔接）。
- **覆盖度**：L2 骨架 5/5 全覆盖（阶段拆分/详细设计/接口规格/每阶段 DoD/依赖与预估——迭代号为预估小节待定项）。
