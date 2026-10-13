# ADR 0007：教室关联集合化与位置记忆迁移（Room v2→v3）

**状态：** Accepted（设计定稿 2026-10-13，checkin-assist-v2 L1 契约；实现归 L2）

## 背景

v120/v121 修复揭示了教务数据的多教室现实：一门课可挂多个教室（逗号连接）、分周换教室（线上线下交替）。v1 的 `CheckInLocation.linkedRoomName` 是单字符串，位置记忆键是「课程名|教室名 → locationId」。L0 裁决教室关联升级为 tag 集合（增删改 + 选课预填全部教室 + 任一命中即命中），数据模型与记忆格式必须随之变更，且用户已有真实标定数据，迁移不可轻率。

## 决策

1. **`linkedRoomNames: List<String>`** 取代 `linkedRoomName: String`，以 Room TypeConverter 存 JSON 数组字符串（`["WM2303","WM1403"]`），**不建关联表**。
2. **`MIGRATION_2_3`**：旧单值 → 单元素集合；空/ null → 空集合。
3. **位置记忆迁移**：旧 DataStore JSON「课程名|教室名 → locationId」按课程名分组、每组取**最新**一条 → 新格式「课程名 → locationId」；随 Room 升级流程一次性执行，幂等（新格式已存在则跳过）。
4. **匹配语义**：`LocationSelection` 与课程匹配升级为「任一教室 tag 命中即命中」。

## 后果

### 正面
- 多教室课可挂一个位置（任一教室命中），匹配不被单值字段锁死。
- 记忆键简化为课程名，与新匹配语义一致，减少一次教室名比对。

### 负面
- 迁移不可逆：执行后回退 v2 需自写反向脚本（集合 → 首元素）。
- 记忆迁移「取最新」丢弃同课程名下不同教室的旧区分——接受此损失（tag 化后教室不再是选择依据，课程名才是）。

## 备选方案

| 方案 | 结果 |
|------|------|
| 教室关联表（locationId ↔ roomId 多对多） | 拒绝——对「一位置挂几个 tag」是过度设计，CRUD 全链路复杂化 |
| 保留旧记忆键格式（课程名\|教室名） | 拒绝——教室名已非唯一标识，保留意味着永远维护一个退化的第二键 |

## 相关文档

- [L0-vision-checkin-assist-v2.md](../../harness/design/checkin-assist-v2/L0-vision-checkin-assist-v2.md)（范围第 4 项、验收 A4）
- [L1-contract-checkin-assist-v2.md](../../harness/design/checkin-assist-v2/L1-contract-checkin-assist-v2.md)（数据契约第 8 条）
