# ADR 0002：使用高德地图 SDK 作为位置标定方案，内部以 WGS-84 存储

**状态：** Superseded（地图选点范式最终被「采集真实 GPS」取代，见 [ADR-0004](./0004-collect-current-location.md)）

> **反转历程**：
> 1. **2026-07-15 ①**：原定高德 SDK，因需 Android Key（写入 Manifest 会被反编译看到）改用 **OSMDroid**（免 Key、WGS-84、免坐标转换）。
> 2. **2026-07-15 ②**：OSMDroid 仅用一次后**再次弃用**——位置采集范式从「地图点选」改为「采集设备当前真实 GPS」（见 [ADR-0004](./0004-collect-current-location.md)）。最终方案彻底不需要任何地图 SDK、任何 Key、任何坐标转换。
>
> `CoordinateConverter`（GCJ-02⇄WGS-84）随之删除。当前方案以 [ADR-0004](./0004-collect-current-location.md) 与 [DESIGN.md §7½](../DESIGN.md) 为准；本 ADR 保留作历史决策记录。

---

以下为原始决策（已过时，保留供回顾）：

## 背景

签到辅助需要让用户「标定」签到位置。可选的输入方式包括：手动输入经纬度、调用系统地图 Intent、内嵌高德/百度/腾讯地图 SDK。不同方案在交互体验、依赖体积、坐标系处理上差异明显。

## 决策

1. **默认使用高德地图（AMap）SDK** 作为内嵌地图选点方案。
2. **所有签到位置在本地统一以 WGS-84 坐标系存储**（Android 系统定位与 Mock 定位使用的坐标系）。
3. **坐标转换**：从高德地图获取的坐标为 GCJ-02（火星坐标），保存前转换为 WGS-84；Mock 定位时直接下发 WGS-84。
4. **抽象隔离**：定义 `LocationPicker` 接口，地图选点作为其一个实现；后续更换 SDK 或 fallback 到手动输入时，不影响数据层和 Mock 服务。
5. **高德 Key**：由用户在本地 `AndroidManifest.xml` 中配置；发布版本需补充相应 ProGuard keep 规则。

## 后果

### 正面
- 内嵌地图交互体验最好，用户可以直接在地图上点选/拖拽 Marker。
- WGS-84 作为内部标准，使 Mock 定位代码与地图 SDK 解耦。
- `LocationPicker` 抽象让未来切换到百度/腾讯/手动输入成为可能，无需迁移已有数据。

### 负面
- 引入 AMap SDK 依赖，增加 APK 体积和构建配置复杂度。
- 需要申请并维护高德 API Key；Key 缺失或错误会导致地图黑屏。
- GCJ-02 → WGS-84 转换存在公开近似算法，可能引入米级误差；对签到场景可接受。
- Release 构建启用代码压缩后，需要为 AMap SDK 补充 keep 规则。

## 备选方案

| 方案 | 结果 |
|------|------|
| 手动输入经纬度 | 作为无 Key 或测试时的 fallback / 初始占位 |
| 百度地图 SDK（BD-09 坐标系） | **未选**，需两次坐标转换（BD-09 → GCJ-02 → WGS-84），转换链更长 |
| 腾讯地图 SDK（GCJ-02） | **未选**，市场占有率与文档丰富度在本场景下不如高德 |

## 相关文档

- [CONTEXT.md](../../CONTEXT.md)
- [OPEN-DECISIONS.md](../OPEN-DECISIONS.md)
