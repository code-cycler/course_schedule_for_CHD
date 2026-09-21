---
mode: feature
wave: 0
stage: L1-contract
created: 2026-09-13
status: archived
---
# 问卷 L1-contract W00 · Preview（决策默认值 yes/no 速答）

> **本波是 preview 预答层**（独立 wave 0）：把 AI 有明确默认倾向的契约决策逐条列出，人只做 yes/no 速答。
>
> **作答规则**：
>
> - **预勾选 = opt-in 开关**（opt-in 默认关）：全部 `[ ]`，人逐条作答；勾 `[x]` = 采纳默认按默认落盘
> - **取消勾选（留空）= 不采纳** → 该要点转入 L1 W01 单独拷问（出选项深究）
> - 本批无单向门要点（发布/删除/花钱/脱敏类）
> - 本波**不用 🤔**；真定不了 → 取消勾选即可，转 W01 深究
> - 若「大体同意但要改一两处」→ 取消勾选，W01 深究题里给自定义值
>
> 默认来源标注于〔〕。

## 背景（L1 契约层盘问域）

L0 已裁决：A 导出修复（跟随系统深浅 + 仅导出链路固定两套配色）/ B 主线程计算迁移 / C 六项 UI 修缮 / D 静默同步（先展示后确认）。L1 定「与现有架构的契合点与差异」：模块边界（新增/修改哪些）、接口契约、全局选型。以下默认均基于已核实代码事实。

## 决策默认值清单

### A · 导出修复契约

- [X]  **1 主题包裹方式 = 在 `OffscreenScheduleCapture` 内容外显式套 `MaterialTheme(colorScheme = 固定浅/深)` + `Surface` 填底色**：深浅取 `isSystemInDarkTheme()`；导出内容内所有 `MaterialTheme.colorScheme.*` 取色随之解析为固定值，根容器不再透明 〔W00/Q2 已定「固定两套」；现状唯一主题点 `MainActivity.kt:36` 跟随系统是 bug 载体，离屏内容必须自带主题〕
- [X]  **2 固定配色基准 = 复用 `Theme.kt:14-34` 现有 `DarkColorScheme`/`LightColorScheme` 紫色系常量**：与 App 非动态取色设备（API<31）的观感一致，零新配色设计成本 〔现状 `Color.kt` 仅 6 常量，导出图无需独立品牌色〕
- [X]  **3 课程卡颜色体系零改动**：`CourseCard.kt` 的 HSL 哈希底色（L=0.55）+ 白字在深浅两套皮肤下对比度均可读，不动 〔`CourseCard.kt:37/89/102/117-121`，白字配中明度底色与主题无关〕
- [X]  **4 A 的验证方式 = 真机深浅两模式截图人工核验 + 代码审查**（导出子树内无裸 `MaterialTheme` 取色/无透明背景），不写 Robolectric 像素级单测 〔渲染断言成本高，A4 验收以人工核验为准〕

### B · 性能契约

- [X]  **5 线程分工原则 = CPU 计算走 `Dispatchers.Default`、文件/DataStore IO 走 `Dispatchers.IO`**：`loadSchedule`/`refreshAfterEdit` 的 coursesByWeek 全周索引与冲突预计算包 `withContext(Default)`；DataStore JSON 序列化与写盘在 IO 〔协程官方分工；现状 `ScheduleViewModel.kt:192/687/171/681` 全在 Main〕
- [X]  **6 PNG 压缩迁移 = `captureToFile` 的 `bitmap.compress` + 文件写入包 `withContext(Dispatchers.IO)`** 〔`ScheduleImageExporter.kt:94-106` 现在 Main 执行〕
- [X]  **7 Compose 重组优化边界 = `Course` 标 `@Immutable` + `ScheduleGrid` 按周课程列表参数稳定化（`remember` 派生），不重构网格布局结构**（Column+Row 保持） 〔W00 第 5 条「渐进式」；Course 全 val 不可变字段，标注安全〕
- [X]  **8 计时日志 = 三场景（加载/切周/导出）入口出口 `Log.d` + `System.currentTimeMillis()` 打点**，tag 统一如 `PerfLog` 〔W00 第 12 条轻量计时；不引 trace 库〕

### C · UI 修缮契约

- [X]  **9 骨架屏 = 手写 Compose 占位（课表网格形状灰块 + 轻量 alpha 动画），不引入第三方 placeholder 库**（accompanist-placeholder 已废弃） 〔W00 第 10 条不新增依赖〕
- [X]  **10 网格视觉打磨边界 = 只动 Modifier 层（圆角/边框/背景/间距/今日列高亮），不动布局测量结构** 〔与 7 同理：渐进式，防动效回归前科〕
- [X]  **11 统计面板指标最小集 = 总学分、课程门数、总学时（节次数）、水课门数**；具体计算在 ViewModel 层派生、纯函数可单测 〔探索现状：水课概念已有（DataStore 水课列表），学分/节次在 Course 字段可得〕
- [X]  **12 统计面板位置 = `SettingsDrawer` 新增「课表统计」区块**（你指定「侧边栏」） 〔`SettingsDrawer.kt` 现有 课表/日历同步/数据 三区，加第四区〕

### D · 静默同步契约

- [X]  **13 同步通道 = 仅 OkHttp 链路**（`EamsClient` + 持久化 cookie）；cookie 失效/网络异常 → 静默失败。不走 WebView（无法静默） 〔v113 cookie 持久化已修；同步主路径现状走 WebView 是因为有 UI，静默场景只能 OkHttp〕
- [X]  **14 触发时机 = `ON_RESUME` + 距上次静默同步 ≥ 15 分钟节流**（防切换应用反复触发）；设置抽屉加「静默同步」开关，默认开 〔「每次进软件」语义 + 防抖常识；开关给退出通道，成本一个 Switch〕
- [X]  **15 diff 粒度 = 课程级三态（新增/删除/变更）**，比对键 = 学期内课程全字段（名称/教师/教室/星期/节次/周位图/类型/学分） 〔Room 里有学期全量数据可比；不做到字段级 diff（对用户无意义）〕
- [X]  **16 确认页形态 = `AlertDialog` 差异清单**（新增 N 门/删除 N 门/变更 N 门，可展开明细），确认 → 走现有入库链路（全量替换该学期 + 冲突缓存重算）+ 刷新；取消 → 数据不动 〔L0 D2–D4 验收的直接实现形态；对话框最轻，不新增屏幕/路由〕
- [X]  **17 静默同步实现落点 = `ScheduleViewModel` 新增后台检查方法 + Repository 新增「拉取不入库」接口**（复用现有解析，diff 在 ViewModel 层纯函数） 〔现有 `fetchRemoteSchedule` 是「拉取+入库」一体，拆出「只拉取」变体；解析零改动符合红线〕

### 跨切面

- [X]  **18 交付四阶段 A→B→C→D，每阶段独立 DoD（`./gradlew test` 全绿 + 真机回归项）**；C 与 D 无相互依赖可并行设计 〔W00 第 1/2 条顺序裁决 + 层分配原则「每层一个功能」精神〕
- [X]  **19 出两条 ADR**：①「导出链路固定深浅两套配色（不随 Material You）」②「静默同步先展示后确认（不自动入库）」——均满足难逆转性弱但缺上下文会困惑 + 真实权衡 〔ADR 三条件核对：这两条半年后回看都需要「为什么不是另一种」的上下文〕

## 补充声明

<任何想补充的话……没有就留空。agent 处理时必读>静默同步应该设置独立开关由用户控制

---

## 处理报告（尾部追加 · 2026-09-13 归档留存）

- **落盘**：19/19 要点全部采纳 → `harness/design/ux-perf-export/L1-contract-ux-perf-export.md`（模块边界表 / 接口契约 8 条 / 全局选型含 10 项被否决记录 / 部署运维 / ADR 识别 / 契约项声明）；要点 19 生成两条 ADR：`docs/adr/0006-export-fixed-color-scheme.md`、`docs/adr/0007-silent-sync-confirm-before-save.md`（编号顺延 0005）。
- **补充声明处置**：「静默同步应该设置独立开关由用户控制」→ 判定为对要点 14 的强调补遗，落盘强化为接口契约第 8 条「独立开关默认开」+ 选型表被否决项「✗ 每次无条件触发（开关由用户控制）」。
- **新增文件**：L1-contract-ux-perf-export.md、ADR-0006、ADR-0007。
- **异常**：无。**逃生舱**：无。
- **preview 统计**：勾选采纳 19 / 留空 0 / 转正式题 0；单向门 0；全采纳率 19/19 = 100%（首次全采纳，质量信号观测中：连续 ≥3 波全采纳才提示疲劳复核）。
- **下一波候选**：L1 无留空深究项，骨架必含项均已在文档闭环 → 建议 L1 无需 W01，直接过层闸门。
- **覆盖度**：L1 骨架 6/6 全覆盖（模块边界/接口契约/全局选型含被否决/部署运维/ADR 识别/契约项声明）。
