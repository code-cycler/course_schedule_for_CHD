---
mode: feature
wave: 0
stage: L1-contract
created: 2026-10-13
status: archived
---
# 问卷 L1-contract W00 · Preview（决策默认值 yes/no 速答）

> **本波是 preview 预答层**（独立 wave 0）：把 AI 有明确默认倾向的决策点逐条列出，人只做 yes/no 速答。
>
> **作答规则**：
>
> - **预勾选 = opt-in 开关**（opt-in 默认关）：仅当你启动本 skill 时明确说「预勾选」，要点才预勾 `[x]`（勾 = 采纳默认）；未启用时全部 `[ ]`，人逐条作答
> - **取消勾选（留空）= 不采纳** → 该要点转入 W01 单独拷问（出选项深究）
> - 本波无单向门要点（全部为可逆架构决策）；涉及 Manifest 权限的要点（7/18）请留意隐私面
> - 本波**不用 🤔**（yes/no 二选一，无中间态）；真定不了 → 取消勾选即可，转 W01 深究
> - 若「大体同意但要改一两处」→ 取消勾选，转 W01 时在深究题里给自定义值
>
> 默认来源标注于〔〕。

## 背景（出题依据）

### L0 闸门已拍板的 L1 策略

**主体先行 + 轮询条件节**：触发管线 / 保活自检 / UI 数据流 / Room 迁移 / 测试入口的契约不依赖畅课 API，先行定稿；轮询单列一节标「pending 假设 B/C」，接口形状先定、端点与鉴权字段抓包后校准，模块边界不推翻。

### 契约事实（代码现状，已核实）

- `CheckInTriggerCoordinator` 现为「权限门禁 → LocationSelection 选位置 → Mock 启动 → 可选拉起畅课」直线流程，返回 `TriggerResult`（MockStarted / NoLocation / PermissionMissing / NeedsManualSelection），UI 模拟触发与后台监听共用。
- `CheckInNotificationListener` 内置 15 秒防抖（`DEBOUNCE_MS`），去重仅按时间，不区分签到会话。
- 位置记忆存 DataStore JSON，键「课程名|教室名 → locationId」（UserPreferences.kt:686–744）；`checkin_locations` 为 Room v2 单表，`linkedRoomName` 单字符串列。
- 引导通知 channel id = `checkin_assist_alert_channel`（普通优先级）；Mock 前台服务 type=location。
- 日志导出体系已存在（minSdk 28 TODO A7 记录「私有目录回退路径按设计工作」）。

### 系统 API 验证（javap android-36，2026-10-13）

- `NotificationListenerService.requestRebind(ComponentName)`（静态）、`onListenerConnected()` 存在——要点 5/6 依据。
- `NotificationChannel.setImportance(int)` 方法存在，但平台行为是 channel 创建后程序化**调高**优先级不可靠（Android 官方文档行为变更记载）——要点 15 依据。
- `foregroundServiceType=dataSync` 在 Android 15+ 有约 6 小时累计时限（单次课堂窗口远小于此，不影响）——要点 9 依据。
- WorkManager 周期任务最小间隔 15 分钟——签到窗口通常几十秒到几分钟，粒度不满足——要点 9 被否决项依据。

## 决策默认值清单

### 模块边界

- [X] **1 模块划分沿用现有包结构**：新增/修改收敛在 `service/checkin/`（触发管线 + 保活自检）、`service/checkin/polling/`（轮询条件节）、`ui/screens/checkinassist/`（UI 重构）、`data/local/`（Room v3 + DataStore 新键），**不新建顶层包、不动 `service/mocklocation/`** 〔现有分层清晰，mocklocation 已稳定（v114 + API 28 实测）〕
- [X] **2 触发源抽象契约**：新增 `CheckInTriggerSource`（sealed interface：Notification / Polling / Manual / Reserved[Auto]）+ 事件数据类 `CheckInTriggerEvent(source, timestamp, courseHint?, rawText?)`；Coordinator 改为管线入口「收事件 → 去重 → 响应」，未来加源只加分支不改管线 〔L0 范围第 2 项的契约化；sealed 限定源集合，编译期穷尽〕

### 接口契约

- [X] **3 去重契约**：签到会话键 =「归一化课程名 + 时间窗」——同一课程名（.trim + 大小写不敏感归一）在 2 分钟内的多源命中视为同一签到会话，只响应一次，替换现有 15 秒防抖；若轮询带回签到 ID 则优先按 ID 去重（条件节补充） 〔多源并存后时间防抖不区分课程会误杀连堂/多门课场景；窗口 2 分钟为默认值，L2 可调〕
- [X] **4 响应动作序列保持**：去重 → 权限门禁 → LocationSelection 选位置 → Mock 启动 → 高优先级引导通知 → 可选拉起畅课；`TriggerResult` 语义保留、新增 `DebouncedBySession` 一个分支 〔现有直线流程已被验证，只插入去重与升级通知，不动其余环节〕
- [X] **5 NLS 心跳自检契约**：`onListenerConnected` 与每次 `onNotificationPosted` 写 DataStore「最后心跳时间戳」；App 冷启动与签到辅助页打开时对比——超过 24 小时无心跳 = 疑似未绑定 → 页面警示 + 尝试 `requestRebind(组件名)`（javap 已验证静态方法存在）+ 引导重开通知使用权 〔荣耀「授权但未绑定」坑的唯一应用层可观测信号就是心跳缺失〕
- [X] **6 开机拉活契约**：新增 `BOOT_COMPLETED` receiver，收到后只做一件事——对本 App 的 NLS 组件调 `requestRebind()` 触发系统重绑，不做其它初始化 〔治「装机/系统更新后不重绑」；receiver 保持零副作用避免拖慢开机〕
- [X] **7 电池白名单申请方式**：直接发 `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` intent 弹系统对话框（需在 Manifest 声明 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 权限）+ 用户拒绝后 fallback 引导手动设置页；荣耀「自启动」开关仅做深链引导不强求直达 〔系统对话框一次点击完成 vs 手动翻设置页；个人 App 无 Play 政策约束〕

### 轮询条件节（pending 假设 B/C）

- [X] **8 轮询调度纯函数契约**：`ClassWindowScheduler`（输入 = 本地课表 + 当前时间，输出 = 是否在轮询窗口内）无副作用可单测；窗口默认 = 课前 10 分钟至课后 5 分钟、轮询间隔默认 60 秒（均可配置，配置项细节归 L2） 〔L0 要点 9 留给 L1 的定值；判窗逻辑与执行载体解耦，载体变不影响〕
- [X] **9 轮询执行载体（高 stakes）**：课时有窗内启动**前台服务**（`foregroundServiceType=dataSync`，Android 15+ 的 6 小时累计时限对单课堂窗口无影响，服务在窗口结束/学期无课时自停）——推理：签到窗口是分钟级实时性需求，WorkManager 最小 15 分钟周期粒度根本不满足，精确闹钟链（AlarmManager + 自调度循环）则复杂度远超前台服务且荣耀省电管控下更易被断——**被否决项：WorkManager 周期轮询（粒度不足）、AlarmManager 自调度循环（复杂 + 易被杀）** 〔签到短窗口的实时性硬需求；前台服务通知栏可见对个人工具反而是诚实的存在感〕
- [X] **10 轮询模块契约形状先定**：`TronClassPoller`（`pollOnce(): PollResult` sealed：NoActiveCheckIn / FoundCheckIn(签到信息) / SessionExpired / NetworkError）+ `TronClassSessionProvider`（登录态供给，占位接口）；**端点 URL、鉴权头、请求体字段全部标 pending，抓包后只校准这些字段、不推翻模块边界** 〔L0 闸门拍板的条件节策略；接口形状（四种结果）不依赖 API 细节〕

### 数据契约

- [X] **11 `linkedRoomNames` 持久化 = Room TypeConverter 存 JSON 数组字符串**（`["WM2303","WM1403"]`），**不建关联表**——**被否决项：教室关联表（对「一位置挂几个教室标签」的简单需求是过度设计，且 CRUD 全链路变复杂）** 〔项目既有 JSON 范式（位置记忆/周次位图都是 JSON 字符串）；集合元素无独立查询需求〕
- [X] **12 课程下拉数据查询契约**：repository 新增「当前学期 → `Map<课程名, Set<教室名>>`」查询；v121 拆卡后同课程名多卡的教室取**并集**；预填用全并集（L0 已裁决「全部带出」），当前周过滤只发生在触发匹配时（LocationSelection） 〔拆卡语义（不相交周各自带教室）在数据层天然形成并集；预填全量 + 匹配时按周是两层职责，不能混〕
- [X] **13 位置记忆迁移契约**：旧 DataStore JSON「课程名|教室名 → locationId」按课程名分组、每组取**最新**一条 → 写入新格式「课程名 → locationId」；在 Room v2→v3 升级流程中一次性执行（幂等：新格式已存在则跳过） 〔教室 tag 化后教室名退出记忆键；「取最新」保留最近的使用习惯〕
- [X] **14 通知原文记录器契约**：DataStore JSON 环形缓冲，默认保留最近 **50** 条畅课通知（字段：渠道 id / 标题 / 正文 / 时间戳），不建新 Room 表；页面查看 + 长按复制 〔轻量查询无结构化过滤需求；50 条足够覆盖数周签到事件〕
- [X] **15 高优先级通知 channel = 新建 `checkin_assist_alert_high`（IMPORTANCE_HIGH）**，旧 `checkin_assist_alert_channel` 保留但不再使用——**被否决项：直接调高旧 channel importance（平台行为：channel 创建后程序化调高不可靠，javap 已验证方法存在但行为受限）** 〔平台行为约束下新建是唯一可靠路径；旧 channel 遗留无副作用〕

### 测试与日志契约

- [X] **16 测试通知按钮契约**：正式版用**本 App 自身包名**发「格式模拟畅课」通知（标题/正文含默认关键字），`CheckInNotificationMatcher` 增加受控放行本包测试通知的白名单机制——测试通知走**真实**匹配 → 触发管线全路径，不绕过任何环节 〔端到端验证的意义就在走全链路；现有 debug shell 包钩子是 debug-only，此为正式版等价物〕
- [X] **17 触发管线日志契约**：复用现有日志导出体系，新增结构化事件行（触发源 / 去重命中与键 / LocationSelection 选位置结果 / Mock 启动停止 / NLS 心跳），格式与现有日志行风格一致 〔复用导出链路零新增成本；手机端自助排查的主证据源（L0 测试入口④）〕

### 部署运维（Manifest 变更清单）

- [X] **18 Manifest 变更全清单**：新增 `RECEIVE_BOOT_COMPLETED` + `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 权限、`BOOT_COMPLETED` receiver 注册、轮询前台服务注册（`foregroundServiceType="dataSync"` + `FOREGROUND_SERVICE_DATA_SYNC` 权限，条件节——随闸门启用）；`MockLocationService` / `CheckInNotificationListener` / 既有权限全部不动 〔变更最小化；Mock 服务与监听器已稳定不动〕

### ADR 识别（L1 处理时按三条件核对）

- [X] **19 ADR 候选预判**：①触发管线多源抽象（Coordinator → 管线，模块形态变化，未来读者需知为什么）②轮询前台服务载体（若闸门通过；WorkManager/AlarmManager 被否决的权衡）③Room v3 + 位置记忆迁移（数据模型不可逆变更的前置导出/回退考量）——三条件（难逆转 + 缺上下文困惑 + 真实权衡）核对通过的落 `docs/adr/`，编号顺延 〔ADR-0005 之后顺延为 0006 起〕

## 补充声明

<任何想补充的话……没有就留空。agent 处理时必读>

---

## 处理报告（尾部追加 · 2026-10-13 归档留存）

> 摘要速览：19/19 全采纳 → [L1-contract-checkin-assist-v2.md](../../design/checkin-assist-v2/L1-contract-checkin-assist-v2.md) + [ADR-0006](../../../docs/adr/0006-trigger-pipeline.md)（触发管线多源抽象）+ [ADR-0007](../../../docs/adr/0007-room-v3-room-tags.md)（Room v3 教室集合 + 记忆迁移）+ OD 轮询条目补注载体已定。

- **落盘**：要点 1–2 → L1#模块边界表；3–7 → L1#接口契约 1–6；8–10 → L1#接口契约 7（轮询条件节，pending 假设 B/C）；11–15 → L1#接口契约 8–9 + 选型表；16–17 → L1#接口契约 10–11；18 → L1#部署运维；19 → ADR 三条件核对：候选①③ 通过落 ADR-0006/0007，候选②（轮询载体）不满足「难逆转」不写 ADR、落契约选型表 + OD 轮询条目补注。
- **异常**：无。**逃生舱**：无（W00 形态）。
- **preview 统计**：采纳 19 / 留空 0；全采纳率 100%（连续第 2 波 100%，护栏阈值 3 波未触发，已向用户口头报备）。
- **未验证假设台账更新**：假设 B/C 影响面进一步收敛为「轮询条件节的端点/鉴权字段」，模块边界已冻结不依赖验证结果。
- **剩余动态盲点（3 个，小波直问）**：①courseHint 提取规则（去重键的现实基础，通知不含课程名时退化路径）②LocationSelection 当周教室匹配规则（S6 契约未落）③通知记录器收录范围（全部 vs 仅命中）。

---

## 提问波（≤3 题，2026-10-13 小波直问，问答摘要）

- **Q1 courseHint 提取规则**：答「子串匹配+退化时间窗（推荐）」——通知标题/正文与当前学期课表课程名双向子串匹配，命中取课程名；无命中退化纯时间窗（接受误并权衡，轮询签到 ID 补偿）。落 L1#接口契约 2。
- **Q2 LocationSelection 当周教室匹配**：答「当前周活跃卡优先（推荐）」——按当前周过滤活跃卡取教室集合匹配，无活跃卡回退全并集。落 L1#接口契约 8。
- **Q3 通知记录器范围**：答「全部畅课通知（推荐）」——含未命中关键字的营销/普通推送，50 条环形。落 L1#接口契约 8。
- 异常：无。逃生舱：无。三题均采纳推荐，落盘后 L1 动态盲点清零。
