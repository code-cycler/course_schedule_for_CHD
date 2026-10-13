---
mode: feature
wave: 0
stage: L2-build
created: 2026-10-13
status: archived
---
# 问卷 L2-build W00 · Preview（决策默认值 yes/no 速答）

> **本波是 preview 预答层**（独立 wave 0）：把 AI 有明确默认倾向的决策点逐条列出，人只做 yes/no 速答。
>
> **作答规则**：
>
> - **预勾选 = opt-in 开关**（opt-in 默认关）：仅当你启动本 skill 时明确说「预勾选」，要点才预勾 `[x]`（勾 = 采纳默认）；未启用时全部 `[ ]`，人逐条作答
> - **取消勾选（留空）= 不采纳** → 该要点转入 W01 单独拷问（出选项深究）
> - 本波无单向门要点；要点 1（阶段拆分）是后续全部阶段的骨架，改动代价最大
> - 本波**不用 🤔**（yes/no 二选一，无中间态）；真定不了 → 取消勾选即可，转 W01 深究
> - 若「大体同意但要改一两处」→ 取消勾选，转 W01 时在深究题里给自定义值
>
> 默认来源标注于〔〕。

## 背景（出题依据）

### 上游契约（L1 已定，本层不得偏离）

- 模块边界表 13 行、接口契约 11 条（含提问波补齐的 courseHint 子串匹配 / 当周活跃卡匹配 / 记录器收录全部畅课通知）、选型 3 决策、Manifest 清单——见 [L1-contract-checkin-assist-v2.md](../../design/checkin-assist-v2/L1-contract-checkin-assist-v2.md)。
- L0 验收 A1–A9 为全链硬约束；轮询阶段以「假设 B/C 闸门通过」为启动前提。

### 出题前核实的代码事实

- `JsonUtils` 为**手写 JSON 字符串拼接**（无 kotlinx.serialization / Gson 依赖）——TypeConverter 不适合跟随手写风格，用 Android 平台内置 `org.json.JSONArray` 做 `List<String>` 互转最干净且零新依赖。
- 日志体系 = `AppLogger`（object 通用日志器）+ `LogExporter`（导出）——测试入口④「复用现有日志导出体系」的落点确认。
- 现有测试基线：Matcher / LocationSelection / ScheduleViewModel 等单测已存在（134+ 用例全绿是 L0 验收 A3 基线）。

## 决策默认值清单

### 阶段拆分（骨架）

- [X] **1 五阶段拆分 + 单向依赖链**：**B1 数据迁移 → B2 触发管线与响应升级 → B3 保活与自检 → B4 UI 重构 → B5 轮询通道（条件，闸门后启动）**；每阶段独立可交付、可验证、可单独 commit——推理：B1 是一切的地基（tag 集合模型），B2/B3 消费新模型与管线，B4 是纯 UI 面可最后做，B5 挂在假设 B/C 闸门后不阻塞前面任何阶段 〔L1 模块边界表的自然拓扑 + long-running 反推 feature_list 需要「可独立交付」粒度〕
- [X] **2 B1 · 数据层迁移**：`CheckInLocationEntity` 集合化 + TypeConverter（`org.json.JSONArray`）+ `MIGRATION_2_3`（旧单值→单元素集合，空→空集合）+ 位置记忆迁移（按课程名分组取最新，幂等）+ repository「当前学期 → Map<课程名, Set<教室名>>」并集查询 + `LocationSelection` 升级（任一 tag 命中 + 当周活跃卡优先，无活跃卡回退全并集）——DoD：迁移单测（三场景 + 记忆幂等）+ LocationSelection 新规则单测（含 v121 拆卡用例）+ `./gradlew test` 全绿 〔ADR-0007；匹配逻辑与模型同批改同批测〕
- [X] **3 B2 · 触发管线与响应升级**：`CheckInTriggerSource`/`CheckInTriggerEvent`（**L2 细化：Event 增加 `checkInId: String?` 字段**——轮询源填、通知源 null，补 L1 契约字段清单）+ `CheckInDeduper`（归一化课程名 + 2 分钟窗，ID 优先）+ Coordinator 管线化 + `TriggerResult.DebouncedBySession` + Matcher 本包测试白名单 + courseHint 子串提取器 + **新 channel `checkin_assist_alert_high` 与 postGuidance 升级**（响应序列属契约 3，随管线同批）——DoD：Deduper 单测（同课程去重 / 跨课程不去重 / 无 courseHint 退化 / ID 优先）+ courseHint 提取单测 + 现有 Matcher 测试扩展白名单用例 〔ADR-0006；「不能独立验证 = 粒度太粗」的反向应用：响应升级并入使本阶段可用测试通知端到端验证〕
- [X] **4 B3 · 保活与自检**：`NlsHealthMonitor`（心跳写读 + 24h 疑似未绑判定 + `requestRebind`）+ `BootRebindReceiver` + 电池白名单 intent 申请（拒绝 fallback 手动页）+ 通知原文记录器（全量畅课通知 50 条环形，挂 `onNotificationPosted`）+ 管线日志事件行（触发源/去重/选位置/Mock/心跳，经 `AppLogger`）——DoD：心跳判定单测 + 记录器环形淘汰单测 + **真机装机验证**（装新包 → 开机 receiver 触发 requestRebind → 测试通知端到端） 〔Manifest 变更全部在本阶段落地；真机验证窗口是 TODO 流程规范的硬要求〕
- [X] **5 B4 · UI 重构**：`LocationEditorSheet` 下拉化（位置下拉选已有 + 新建入口；课程下拉仅当前学期，历史关联原样保留）+ 教室 tag chip 增删改 + 选课预填全教室并集 + 测试入口按钮组（端到端测试通知 / NLS 绑定验证 / 通知记录查看 / 日志导出经 `LogExporter`）+ 疑似未绑定警示条（消费 B3 心跳）——DoD：A6 验收清单逐条过 + 真机冒烟（含 B2 测试通知端到端走 UI 触发） 〔纯 UI 面收尾；警示条与测试入口是 B3 能力的展示层，必须在 UI 阶段可见〕
- [X] **6 B5 · 轮询通道（条件节，闸门后启动）**：`ClassWindowScheduler` + `TronClassPoller`/`TronClassSessionProvider`（端点/鉴权 pending 抓包校准）+ `dataSync` 前台服务（窗口结束/无课自停）+ 轮询手动触发按钮——DoD：Scheduler 单测（有窗/无窗/无课日/跨天窗口）+ 真机课中轮询冒烟 + SessionExpired 引导通知验证 〔假设 B/C 闸门 = TODO 行动项；闸门未过则本阶段整体不做（OD 条目已有）〕

### 接口规格细化（L2 层补充）

- [X] **7 错误处理约定**：轮询 `NetworkError` 指数退避（连续失败 1 → 2 → 4 分钟，上限 5 分钟，成功即重置）；`SessionExpired` 发一次性引导通知提示重新登录畅课（不自动重登）；Deduper 会话记录仅内存（进程重启清零，可接受） 〔克制原则：失败不轰炸；会话去重跨进程持久化收益趋零〕
- [X] **8 心跳写入合批**：`onNotificationPosted` 高频到达时心跳时间戳不在每次回调都写 DataStore——内存缓存 + 距上次落盘超 5 分钟才写（`onListenerConnected` 必写） 〔DataStore 高频写放大；心跳精度 24h 阈值下 5 分钟误差无感〕
- [X] **9 测试通知白名单机制**：Matcher 放行条件 = 本包名 **且** 通知带私有 extras 标记（`checkin_test=true`，只有本 App 能加）——不按文案白名单，避免真实畅课通知被误放行 〔受控放行的契约落点：包名 + 私有标记双条件〕

### 交付节奏

- [X] **10 通用 DoD（每阶段）**：`./gradlew test` + `assembleDebug` 全绿；增量构建不可信时按 retro 规范 `clean assembleDebug` + `unzip -l` 校验 APK 完整性再装真机；每阶段末执行 L0 验收 A3/A8 回归（不破坏现有功能） 〔TODO 流程规范「真机验证窗口」+ retro「增量构建残包」坑〕
- [X] **11 真机验收安排**：B4 完成后立即装真机跑 A5–A8 + 全部测试入口自检；A1/A2（真实签到可达性 + 关键字校准）等下次课堂签到自然触发，靠通知记录器手机端取证，不专门等 〔L0 验收 A1/A2 依赖真实签到事件；测试入口的设计初衷即不连电脑取证〕
- [X] **12 版本与提交策略**：沿用 `[vXX]` 内部迭代号注释风格；每阶段至少一个独立 commit（消息含阶段号与验收点）；不升 versionName（发布另议） 〔项目既有约定（CLAUDE.md 工作约定）〕
- [X] **13 工作量预估（个人节奏，含真机验证）**：B1 约半天 / B2 约一天 / B3 约一天 / B4 约一至两天 / B5 约一天（闸门后）——总量约 4 至 5 个有效工作日；B1–B4 不依赖假设 B/C 可立即开工 〔阶段粒度 + 已有代码 familiarity〕

## 补充声明

<任何想补充的话……没有就留空。agent 处理时必读>

---

## 处理报告（尾部追加 · 2026-10-13 归档留存）

> 摘要速览：13/13 全采纳 → [L2-build-checkin-assist-v2.md](../../design/checkin-assist-v2/L2-build-checkin-assist-v2.md)（五阶段 B1–B5 拆分 + 详细设计 + 接口规格表 + 通用 DoD + 依赖预估）。

- **落盘**：要点 1 → L2#阶段拆分表；要点 2–6 → L2#每阶段详细设计（B1–B5，各含改动文件/交互规格/DoD）；要点 7–9 → L2#接口规格汇总 + B5 错误处理；要点 10–11 → L2#通用 DoD（5 条）；要点 12–13 → L2#依赖与预估。
- **异常**：无。**逃生舱**：无（W00 形态）。
- **preview 统计**：采纳 13 / 留空 0；全采纳率 100%——**连续第 3 波 100% 全采纳（L0 W01 → L1 W00 → L2 W00），疲劳顺勾护栏正式触发**：已向用户提示复核（见对话处理报告）；用户在层闸门/收尾面板的答复将作为「是否确实认可全部默认」的复核信号。
- **未验证假设台账**：无新增；B5 启动前提（假设 B/C）已写入阶段表与 OD。
- **覆盖度**：L2 骨架 5/5（阶段拆分 / 详细设计 / 接口规格 / DoD / 依赖预估）。
