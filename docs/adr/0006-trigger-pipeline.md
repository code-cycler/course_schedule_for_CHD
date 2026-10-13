# ADR 0006：触发管线多源抽象（Coordinator → 触发管线）

**状态：** Accepted（设计定稿 2026-10-13，checkin-assist-v2 L1 契约；实现归 L2）

## 背景

签到辅助 v1 的触发链路是单源的：`CheckInNotificationListener` 收到畅课通知后直接调 `CheckInTriggerCoordinator`（权限门禁 → 选位置 → Mock → 引导通知），防抖是 15 秒纯时间窗。v2 的 L0 决策引入多通道：NLS 通知监听为主 + 课时有窗轮询为兜底（pending 假设 B/C）+ 手动触发 + 预留自动签到回调位。多源并存后，若继续在 Listener 内堆叠逻辑，去重语义会散落各源、每加一个源都要改所有既有源。

## 决策

1. **触发源封闭抽象**：`CheckInTriggerSource` 为 sealed interface（Notification / Polling / Manual / Reserved[Auto]），配 `CheckInTriggerEvent(source, timestamp, courseHint?, rawText?)`。sealed 限定源集合，编译期穷尽检查。
2. **Coordinator 管线化**：改造为「收事件 → 会话级去重 → 响应动作序列」，保持其「UI 模拟触发与后台监听共用汇聚点」的既有地位；响应动作序列（权限门禁 → `LocationSelection` → Mock → 引导通知 → 可选拉起畅课）与 `TriggerResult` 语义不变，新增 `DebouncedBySession` 分支。
3. **会话级去重**：`CheckInDeduper` 以「归一化课程名（trim + 大小写不敏感）+ 2 分钟时间窗」为签到会话键，同键多源命中只响应一次；轮询事件携带签到 ID 时优先按 ID 去重。替换 v1 的 15 秒纯时间防抖。

## 后果

### 正面
- 新增触发源（含未来的自动签到回调）只加 sealed 分支，不改管线结构与既有源。
- 去重集中一处，连堂课/同时多门课签到的误杀场景由「分课程名」解决。
- 管线各环节可注入任意源做单测（L2 的测试策略基础）。

### 负面
- 一次结构性重构，v1 的 Listener/Coordinator 现有测试需适配。
- 「2 分钟窗 + 课程名归一」是启发式：通知不含课程名时（courseHint 为空），去重退化为纯时间窗（此时同窗内两门课的签到可能被误并——接受此权衡，靠轮询带回的签到 ID 补偿）。

## 备选方案

| 方案 | 结果 |
|------|------|
| 在 Listener 内继续堆叠多源判断 | 拒绝——单点膨胀，去重语义散落 |
| EventBus / 广播总线解耦各源 | 拒绝——过度设计，个人项目引入总线复杂度不值 |

## 相关文档

- [L0-vision-checkin-assist-v2.md](../../harness/design/checkin-assist-v2/L0-vision-checkin-assist-v2.md)（范围第 2 项）
- [L1-contract-checkin-assist-v2.md](../../harness/design/checkin-assist-v2/L1-contract-checkin-assist-v2.md)
- [ADR-0001](0001-checkin-assist-scope.md)（v1 边界，自动签到修订走其 OD 闸门）
