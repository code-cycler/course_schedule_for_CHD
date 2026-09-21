# ADR 0006：课表分享图导出用固定深浅两套配色（不随 Material You 动态取色）

**状态：** Accepted（2026-09-13，ux-perf-export L1 契约，W00 preview 用户采纳）

## 背景

v2.x 分享课表功能在深色系统模式下导出的图片颜色异常（用户报告）。根因：`ExportableScheduleGrid` 根容器无背景（PNG 透明底）+ 文字/边框色取自 `MaterialTheme.colorScheme`（深色模式下解析为浅白色）——透明底叠浅白字，导出即花。

修复方向用户已裁决：**导出配色跟随系统深浅**（深色系统导深色皮肤图、浅色导浅色图）。引出分支：App 界面动态取色（Material You，`Theme.kt:44-46`，Android 12+ 默认开）是否也作用于导出图。

## 决策

**仅导出链路使用固定深浅两套配色**（复用 `Theme.kt` 现有 `DarkColorScheme`/`LightColorScheme` 紫色系常量）；App 界面照旧动态取色。导出子树 = `MaterialTheme(colorScheme = 固定深/浅) { Surface(colorScheme.background) { … } }`，深浅由 `isSystemInDarkTheme()` 决定，禁止 `dynamicColorScheme` 进入导出子树。

## 理由

分享图是**传播物**：发到 QQ/微信/微博后脱离本机环境，颜色承担「这张图来自哪个 App、可读性如何」的一致性职能。动态取色下，同一用户换壁纸、不同设备导出的图颜色各异——个性化对界面是优点，对传播物是不稳定因素。仅导出链路固定，App 内观感不受影响，改动面小（一个显式主题包裹）。

## 后果

### 正面

- 分享图跨设备、跨时间颜色稳定可预期。
- 顺带修复深色导出 bug：Surface 填底消除透明底，固定 colorScheme 使深色图深底浅字完整自洽。
- API 28（无 Material You）与 API 31+ 设备导出观感一致。

### 负面 / 已知限制

- 用户壁纸色不会体现在分享图里（接受：图的内容是课表不是壁纸）。
- 若未来导出图要加品牌水印/主题定制，需重审此决策。

## 相关文档

- [L0-vision-ux-perf-export.md](../../harness/design/ux-perf-export/L0-vision-ux-perf-export.md)（目标与验收 A1–A4）
- [L1-contract-ux-perf-export.md](../../harness/design/ux-perf-export/L1-contract-ux-perf-export.md)（接口契约第 5 条）
