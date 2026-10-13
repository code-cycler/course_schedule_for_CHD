# L2-build-checkin-assist-v2.md · 签到辅助 v2 构建层

> **导览**
> ① 位置与职责：L2 构建层——分几步建、每步做什么、何时算完。
> ② 覆盖：阶段 B1–B5（B5 为条件节）。
> ③ 上下游：全部实现受 [L1-contract-checkin-assist-v2.md](L1-contract-checkin-assist-v2.md) 契约约束；验收锚定 [L0-vision-checkin-assist-v2.md](L0-vision-checkin-assist-v2.md) A1–A9。
> ④ 契约项声明：各阶段 DoD（含通用条目）为实现期验收硬约束；B5 整阶段以「假设 B/C 闸门通过」为生效前提。
>
> 决策来源：L2 W00（2026-10-13，13/13 采纳）；L2 细化项（Event 增加 `checkInId` 字段）已标注。

## 阶段拆分

| 阶段 | 主题 | 依赖 | 预估（个人节奏，含真机验证） | 独立交付物 |
|---|---|---|---|---|
| B1 | 数据层迁移（Room v3 + tag 集合 + 记忆迁移 + 匹配升级） | 无 | 约半天 | 迁移可用、单测全绿、可独立 commit |
| B2 | 触发管线与响应升级 | B1 | 约一天 | 管线重构完成、测试通知端到端可验 |
| B3 | 保活与自检 | B2 | 约一天 | Manifest 变更落地、真机装机验证通过 |
| B4 | UI 重构 | B1/B2/B3 | 约一至两天 | A6 验收清单逐条通过 + 真机冒烟 |
| B5 | 轮询通道（**条件节**） | B2 + 假设 B/C 闸门 | 约一天 | 闸门通过才启动；未过则整阶段不做（见 OD） |

依赖链单向：B1 → B2 → B3 → B4；B5 挂闸门，不阻塞 B1–B4。B1–B4 **不依赖**畅课 API 验证，可立即开工。

## 每阶段详细设计

### B1 · 数据层迁移

**改动文件与要点**：
- **迁移前置备份**（grill W01 Q3 修订，落实 L0 风险节「迁移须有前置导出」承诺）：`MIGRATION_2_3` 执行前，自动把全部 `checkin_locations` + 位置记忆导出为带时间戳的 JSON 备份文件（应用私有目录；序列化器与 B4「位置数据导出/导入」共用同一实现）。
- `CheckInLocationEntity`：`linkedRoomName: String?` → `linkedRoomNames: List<String>`；注册 TypeConverter（`org.json.JSONArray` 互转，零新依赖，不跟随 JsonUtils 手写风格）。
- `AppDatabase`：version 2→3，`MIGRATION_2_3`：SQL 把旧列单值搬为 JSON 数组（`"WM2303"` → `["WM2303"]`，null/空 → `[]`）；迁移流程内调用位置记忆迁移（读旧 DataStore JSON「课程名|教室名 → locationId」→ 按课程名分组、每组取最新 → 写新格式「课程名 → locationId」，幂等：新格式已存在则跳过）。
- `CheckInLocation`（domain）：字段同步集合化。
- `ICourseRepository` / `CourseRepositoryImpl`：新增「当前学期 → `Map<课程名, Set<教室名>>`」查询；同课程名多卡（v121 拆卡）教室取并集。
- `LocationSelection`：匹配升级——课程名命中后，教室匹配取「当前周活跃卡的教室集合」（无活跃卡回退全并集）与位置 tag 求「任一命中」。
- `CheckInLocationDao` / Repository CRUD：随集合字段适配。

**DoD**：**迁移备份验证（备份文件存在且可解析）** + 迁移单测（旧单值→单元素集合 / 空值→空集合 / 记忆迁移分组取最新 + 幂等）+ `LocationSelection` 新规则单测（含 v121 拆卡、当周无课回退用例）+ `./gradlew test` 全绿 + 通用 DoD（见下）。

### B2 · 触发管线与响应升级

**新类与改造**：
- `CheckInTriggerSource`（sealed interface：`Notification` / `Polling` / `Manual` / `ReservedAuto`）+ `CheckInTriggerEvent(source, timestamp, courseHint: String?, rawText: String?, checkInId: String?)`——`checkInId` 为 **L2 细化补 L1 契约**：轮询源填、通知源 null。
- `CheckInDeduper`：`shouldSuppress(event): Boolean`——键 = 轮询 ID（非空时优先）否则归一化课程名（`.trim()` + 大小写不敏感）+ 2 分钟窗；会话记录仅内存。
- `CourseHintExtractor`：通知标题/正文与当前学期课表课程名双向子串匹配（contains），命中取课程名；**提取为空的兜底**（grill W01 Q2 修订）：纯函数「当前时间（星期 + 节次）在课表中推断正在上的课」（多节并发取节次匹配者）作为 fallback courseHint，推断结果标「推断」来源；兜底仍空才退化为纯时间窗去重。
- `CheckInTriggerCoordinator` 管线化：收事件 → Deduper → 既有响应序列（权限门禁 → LocationSelection → Mock → 引导通知 → 可选拉起畅课）；`TriggerResult` 新增 `DebouncedBySession`。
- `CheckInNotificationMatcher`：测试通知白名单 = 本包名 **且** 私有 extras 标记 `checkin_test=true`（双条件，不按文案放行）。
- 新 channel `checkin_assist_alert_high`（`IMPORTANCE_HIGH`）+ `postGuidance` 升级迁移至新 channel；旧 channel 保留不再使用。

**DoD**：Deduper 单测（同课程 2 分钟窗去重 / 跨课程不去重 / 无 courseHint 退化纯时间窗 / ID 优先）+ 提取器单测（命中/未命中/多课程歧义取最长名）+ Matcher 白名单用例扩展 + debug 测试通知端到端走通管线 + 通用 DoD。

### B3 · 保活与自检

**新组件与 Manifest（全部变更在本阶段落地）**：
- `NlsHealthMonitor`：`onListenerConnected` 必写心跳、`onNotificationPosted` 合批写（内存缓存，距上次落盘超 5 分钟才写 DataStore）；`isSuspectUnbound()`（>24h 无心跳）；警示时尝试 `requestRebind(组件名)`（有效性为**未验证假设 D**，见 L0 风险台账）；**App 每次冷启动无条件 `requestRebind()` 一次**（预防性重绑，不看心跳判定，幂等无害——grill W01 Q4 修订）；**心跳超阈值时发一条低优先级通知**（不打开 App 也可见「签到监听可能未生效，点按处理」，区别于签到提醒的高优先级——补「一直不打开 App」的静默失效面）。
- `BootRebindReceiver`（`BOOT_COMPLETED` + `RECEIVE_BOOT_COMPLETED` 权限）：仅调 `requestRebind()`，零其它副作用。
- 电池白名单：`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` intent 直申（+ Manifest 权限），拒绝后 fallback 手动设置页；荣耀「自启动」仅深链引导。
- `TronClassNotificationLog`：`onNotificationPosted` 记录**全部**畅课通知（渠道 id/标题/正文/时间戳），DataStore JSON 环形缓冲 50 条。
- 管线日志事件行：触发源 / 去重命中与键 / LocationSelection 结果 / Mock 启停 / NLS 心跳，经 `AppLogger` 写入，`LogExporter` 可导出。

**DoD**：心跳判定单测（阈值边界 + 合批逻辑注入时钟）+ 记录器环形淘汰单测 + **真机装机验证**（clean assembleDebug 装机 → 重启触发 receiver → 测试通知端到端 → **「断开重连」场景验证 `requestRebind` 程序化重绑是否生效（裁决未验证假设 D；无效则降级纯引导并更新 L0 台账）** → **A9 三步验证**（channel 实际 importance 未被降级 / 目视横幅弹出 / 降级则引导手动设紧急））+ 通用 DoD。

### B4 · UI 重构

**交互规格**：
- 位置编辑器：签到位置下拉（已有列表选择 + 「新建位置」入口保留 GPS 采集主方式）；课程下拉数据源 = 仅当前学期的 `Map<课程名, Set<教室名>>`（历史关联原样显示与编辑，不在候选列表）；选课 → 教室 tag 区自动预填该课全部教室（并集），chip 可删可加可改，手输自定义教室允许。
- 测试入口按钮组（签到辅助页内）：端到端测试通知（发 `checkin_test` 标记通知走真实管线）/ NLS 绑定实时验证（发 ping 看 `onNotificationPosted` 回调 + 心跳刷新）/ 通知记录查看（最近 50 条，长按复制）/ 日志导出（`LogExporter`）/**位置数据导出/导入**（grill W01 Q5 修订：JSON 经系统分享与文件选择器，格式与 B1 迁移备份共用同一序列化器——换机/重装不丢现场标定的位置数据）。
- 疑似未绑定警示条：消费 B3 心跳，>24h 无心跳显示「通知监听可能未生效」+ 重连按钮 + 跳转通知使用权设置；若 A9 验证发现 channel 被系统降级，警示条同时承担「引导手动设为紧急/横幅」的入口（grill W01 Q8 修订）。

**DoD**：A6 验收清单逐条通过 + 真机冒烟（含从 UI 按钮触发测试通知的端到端）+ 通用 DoD。B4 完成后立即装真机跑 A5–A8 与全部测试入口自检。

### B5 · 轮询通道（条件节——闸门未过则整阶段不做）

**前置闸门**：TODO「验证假设 B/C」抓包/调研通过（OD 条目三条件另含用户确认与新 ADR——那是自动签到；轮询仅需假设 B/C 通过）。
- `ClassWindowScheduler`：纯函数（课表 + now → 是否窗口内），默认窗口课前 10 分钟至课后 5 分钟、间隔 60 秒；**当前学期无课表数据 → 轮询保持关闭 + 签到辅助页显示原因**（「当前学期无课表数据，轮询通道未启用」，grill W01 Q6 修订，静默失效变可见状态）。
- `TronClassPoller`：`pollOnce(): PollResult`（`NoActiveCheckIn` / `FoundCheckIn(checkInId, courseName, rawPayload)` / `SessionExpired` / `NetworkError`）；端点/鉴权/请求体 pending 抓包校准。
- `TronClassSessionProvider`：登录态供给占位接口。
- `PollingForegroundService`：`foregroundServiceType="dataSync"`（+ `FOREGROUND_SERVICE_DATA_SYNC` 权限），窗口结束/无课自停；错误处理——`NetworkError` 指数退避 1 → 2 → 4 分钟（上限 5 分钟，成功重置）；`SessionExpired` 一次性引导通知（不自动重登）。
- 轮询手动触发按钮（UI，随本阶段加）。

**DoD**：Scheduler 单测（有窗/无窗/无课日/跨天窗口）+ 真机课中轮询冒烟 + `SessionExpired` 引导通知验证 + 通用 DoD。

## 接口规格汇总（关键签名）

| 组件 | 签名/条件 | 错误分支 |
|---|---|---|
| `CheckInTriggerEvent` | `(source, timestamp, courseHint?, rawText?, checkInId?)` | — |
| `CheckInDeduper` | `shouldSuppress(event): Boolean`；键 = checkInId ?? 归一化 courseHint ?? null | null 键退化为纯 2 分钟时间窗 |
| `CourseHintExtractor` | `extract(title, text, courseNames): String?` | 多课程歧义取最长名；无命中 null |
| `NlsHealthMonitor` | `recordHeartbeat(connected: Boolean)` / `isSuspectUnbound(): Boolean` / `tryRebind()` | — |
| `TronClassPoller` | `pollOnce(): PollResult`（B5） | NetworkError 退避；SessionExpired 引导 |
| Matcher 白名单 | 包名 == 本包 && extras `checkin_test=true` | 双条件缺一不放行 |

## 通用 DoD（每阶段必过）

1. `./gradlew test` + `assembleDebug` 全绿（L0 验收 A3：现有 134+ 用例零破坏）。
2. 装真机前按 retro 规范：增量构建不可信 → `clean assembleDebug` + `unzip -l` 校验 APK 完整性。
3. 每阶段末执行 L0 验收 A8 回归（现有通知监听、模拟触发、Mock 定位行为不回退）。
4. 每阶段至少一个独立 commit，消息含阶段号（B1–B5）与验收点；沿用 `[vXX]` 内部迭代号注释；不升 versionName。
5. 真机验收：B4 后立即跑 A5–A8 + 测试入口自检；A1/A2 等真实签到自然触发，靠通知记录器手机端取证（不连电脑）。

## 依赖与预估

- **第三方依赖**：零新增（`org.json` 为平台内置）。
- **系统 API**：`requestRebind` / `BOOT_COMPLETED` / 电池白名单 intent / `dataSync` 前台服务——`requestRebind` 已 javap 验证，其余为标准 API；B3 需真机验证窗口。
- **外部闸门**：B5 依赖 TODO「验证假设 B/C」（抓包）；A1/A2 依赖真实签到事件。
- **总预估**：B1–B4 约 3.5 至 4 个有效工作日（可立即开工）；B5 约一天（闸门后）。
