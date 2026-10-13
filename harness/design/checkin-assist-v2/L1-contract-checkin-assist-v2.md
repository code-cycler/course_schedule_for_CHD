# L1-contract-checkin-assist-v2.md · 签到辅助 v2 契约层

> **导览**
> ① 位置与职责：L1 契约层——跨实现阶段稳定的架构骨架：模块边界、接口契约、全局选型、部署面。
> ② 覆盖：触发管线 / 保活自检 / 轮询条件节（pending 假设 B/C）/ 数据契约 / 测试与日志 / Manifest 变更。
> ③ 上下游：继承 L0 验收标准 A1–A9（[L0-vision-checkin-assist-v2.md](L0-vision-checkin-assist-v2.md)）；约束 L2 阶段拆分与详细设计。
> ④ 契约项声明（下层硬约束）：模块边界表全部行 / 接口契约第 1–5、8 条 / 全局选型表全部决策 / Manifest 清单全部条目——L2 不得偏离；轮询条件节各条以「假设 B/C 闸门通过」为生效前提。
>
> 决策来源：L1 W00（2026-10-13，19/19 采纳，补充声明空）；L0 闸门拍板「主体先行 + 轮询条件节」。

## 模块与边界（新增/修改清单 + 与现有架构的契合点）

| 模块 | 类型 | 职责（一句话） |
|---|---|---|
| `service/checkin/CheckInTriggerSource` + `CheckInTriggerEvent` | 新增 | 触发源 sealed 抽象与事件数据类（Notification / Polling / Manual / Reserved[Auto]） |
| `service/checkin/CheckInTriggerCoordinator` | **修改** | 管线化：「收事件 → 会话级去重 → 响应动作序列」，保持 UI/后台共用汇聚点地位 |
| `service/checkin/CheckInDeduper` | 新增 | 签到会话级去重（归一化课程名 + 时间窗；轮询有签到 ID 时优先 ID） |
| `service/checkin/NlsHealthMonitor` | 新增 | NLS 心跳记录与自检（24h 无心跳警示 + `requestRebind` 重连尝试） |
| `service/checkin/BootRebindReceiver` | 新增 | `BOOT_COMPLETED` 后仅对 NLS 组件 `requestRebind()`，零其它副作用 |
| `service/checkin/polling/ClassWindowScheduler` | 新增（条件） | 课时有窗纯函数判窗（课表 + now → 是否窗口内） |
| `service/checkin/polling/TronClassPoller` + `TronClassSessionProvider` | 新增（条件） | 轮询执行与登录态供给（端点/鉴权字段 pending 假设 B/C） |
| `service/checkin/polling/PollingForegroundService` | 新增（条件） | 课时有窗前台服务载体（`foregroundServiceType=dataSync`），窗口结束自停 |
| `service/checkin/TronClassNotificationLog` | 新增 | 畅课通知原文环形记录（存储经 UserPreferences 新键，业务入口在 Listener） |
| `service/checkin/CheckInNotificationMatcher` | **修改** | 增加本包测试通知受控白名单（走真实匹配路径） |
| `ui/screens/checkinassist/LocationEditorSheet` | **重构** | 下拉（位置/课程）+ 教室 tag 增删改 + 选课预填 |
| `data/local/database/`：`CheckInLocationEntity` + TypeConverter + `MIGRATION_2_3` | **修改** | `linkedRoomName: String` → `linkedRoomNames: List<String>`（JSON 数组） |
| `data/local/preferences/UserPreferences` | **修改** | 新键：NLS 心跳时间戳、通知记录环形缓冲、新格式位置记忆 |

**与现有架构的契合点**：Coordinator 管线化是既有「UI 模拟触发与后台监听共用」设计的自然演进，不是重建；DataStore JSON 范式（位置记忆、周次位图先例）沿用至教室集合与通知记录；新服务类经 Koin `appModule` 注册；`service/mocklocation/` 与 `MockLocationService`、既有权限声明全部不动。

## 接口契约

1. **触发源与事件**（硬约束）：`CheckInTriggerSource` 为 sealed interface，源集合封闭为四类（Notification / Polling / Manual / Reserved[Auto]）；`CheckInTriggerEvent(source, timestamp, courseHint?, rawText?)`。未来新增源只加 sealed 分支，不改管线结构。
2. **去重契约**（硬约束）：签到会话键 = 归一化课程名（`.trim()` + 大小写不敏感）+ 2 分钟时间窗——同键多源命中只响应一次；轮询事件携带签到 ID 时优先按 ID 去重。替换现有 15 秒纯时间防抖。窗口默认值 L2 可调。**courseHint 提取规则**（提问波裁决 2026-10-13）：通知标题/正文与当前学期课表课程名做双向子串匹配（contains），命中即取该课程名；**提取为空时的兜底**（grill W01 Q2 修订）：用「当前时间（星期 + 节次）在课表中推断正在上的课」作为 fallback courseHint（多节并发取节次匹配者），推断结果标注「推断」来源以与通知提取区分；兜底仍空才退化为纯时间窗（接受误并权衡，轮询签到 ID 补偿）。
3. **响应动作序列**（硬约束）：去重 → 权限门禁 → `LocationSelection` 选位置 → Mock 启动 → 高优先级引导通知（新 channel）→ 可选拉起畅课（现有开关，默认关）。`TriggerResult` 语义保留，新增 `DebouncedBySession` 分支。
4. **NLS 心跳**（硬约束）：`onListenerConnected` 与每次 `onNotificationPosted` 写 DataStore「最后心跳时间戳」；App 冷启动与签到辅助页打开时对比，超 24 小时无心跳 = 疑似未绑定 → 页面警示 + `requestRebind(组件名)`（API 36 javap 已验证存在）+ 引导重开通知使用权。
5. **开机重绑**（硬约束）：`BOOT_COMPLETED` receiver 只调 `requestRebind()`，不做其它初始化。
6. 电池白名单：`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` intent 直申（系统对话框）+ 拒绝后 fallback 手动设置页；荣耀「自启动」仅深链引导。
7. **轮询条件节**（生效前提 = 假设 B/C 闸门通过）：
   - `ClassWindowScheduler`：无副作用纯函数，窗口默认课前 10 分钟至课后 5 分钟、间隔默认 60 秒；**无课数据时的默认行为**（grill W01 Q6 修订）：当前学期无课表数据 → 轮询保持关闭（维持课时有窗初衷，不放宽为全天兜底），但签到辅助页显示原因（「当前学期无课表数据，轮询通道未启用」），静默失效变可见状态；
   - `TronClassPoller.pollOnce(): PollResult`（sealed：`NoActiveCheckIn` / `FoundCheckIn(签到信息)` / `SessionExpired` / `NetworkError`）；
   - `TronClassSessionProvider`：登录态供给占位接口；端点 URL、鉴权头、请求体字段**全部 pending**，抓包后只校准字段值、不推翻模块边界；
   - 载体 = 课时有窗前台服务（`dataSync` 型，Android 15+ 约 6 小时累计时限对单课堂窗口无影响），窗口结束/无课自停。
8. **数据契约**（硬约束）：
   - `linkedRoomNames` 以 Room TypeConverter 存 JSON 数组字符串，不建关联表；触发匹配升级为「任一教室 tag 命中即命中」；**LocationSelection 教室匹配规则**（提问波裁决 2026-10-13）：按当前周次过滤出该课的活跃卡（v121 拆卡语义：同课程同时间多张周次不相交卡，当前周仅一张活跃），取活跃卡的教室集合与位置 tag 匹配；课程本周无活跃卡时回退全教室并集——S6（分周换教室）场景的完整支撑；
   - repository 新增「当前学期 → `Map<课程名, Set<教室名>>`」查询，同课程名多卡（v121 拆卡）教室取并集；预填用全并集，当前周过滤只发生在触发匹配时；
   - 位置记忆迁移：旧「课程名|教室名 → locationId」按课程名分组、每组取最新一条 → 新「课程名 → locationId」；随 Room v2→v3 升级一次性执行，幂等（新格式已存在则跳过）；
   - 通知原文记录器：DataStore JSON 环形缓冲最近 50 条**全部**畅课通知（含未命中关键字的普通/营销推送——校准关键字的价值恰在看到「未命中」的真实文案；字段：渠道 id / 标题 / 正文 / 时间戳），不建新表。
9. 通知 channel：新建 `checkin_assist_alert_high`（`IMPORTANCE_HIGH`，横幅 + 声音 + 振动）；旧 `checkin_assist_alert_channel` 保留不再使用（平台行为：已建 channel 程序化调高不可靠）。
10. 测试通知：正式版以本 App 包名发「格式模拟畅课」通知（标题/正文含默认关键字），Matcher 增加受控白名单放行——走真实匹配 → 触发管线全路径，不绕过任何环节。
11. 管线日志：复用现有日志导出体系，新增结构化事件行（触发源 / 去重命中与键 / LocationSelection 结果 / Mock 启停 / NLS 心跳），格式与现有日志行风格一致。

## 全局选型（含被否决项）

| 决策点 | 选型 | 被否决项（理由） |
|---|---|---|
| 轮询执行载体 | 课时有窗前台服务（`dataSync`） | WorkManager 周期任务（最小 15 分钟粒度，不满足分钟级签到窗口）；AlarmManager 自调度循环（复杂度高 + 荣耀省电管控下更易被断） |
| 教室集合持久化 | TypeConverter JSON 数组 | 教室关联表（对「一位置挂几个 tag」是过度设计，CRUD 全链路变复杂） |
| 通知优先级升级 | 新建高优 channel | 调高旧 channel importance（平台行为不可靠，javap 验证方法存在但升级无效） |

## 部署运维（Manifest 变更清单）

- 新增权限：`RECEIVE_BOOT_COMPLETED`、`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`；`FOREGROUND_SERVICE_DATA_SYNC`（随轮询闸门启用）。
- 新增组件：`BOOT_COMPLETED` receiver（`BootRebindReceiver`）、轮询前台服务注册（`foregroundServiceType="dataSync"`，条件节）。
- 不动：`MockLocationService`、`CheckInNotificationListener`、既有全部权限。

## ADR 识别（三条件核对：难逆转 + 缺上下文困惑 + 真实权衡）

| 候选 | 三条件 | 处置 |
|---|---|---|
| ① 触发管线多源抽象 | 难逆转（中，结构重构）✓ 困惑 ✓ 权衡 ✓ | **落 [ADR-0006](../../../docs/adr/0006-trigger-pipeline.md)** |
| ② 轮询前台服务载体 | 难逆转 ✗（轮询未实现，纸面可逆） | 不写 ADR；选型与被否决项落本契约文档，并在 OPEN-DECISIONS 轮询条目补注「若实现载体已定」 |
| ③ Room v3 + 位置记忆迁移 | 难逆转 ✓（用户数据形态迁移） 困惑 ✓ 权衡 ✓ | **落 [ADR-0007](../../../docs/adr/0007-room-v3-room-tags.md)** |
