# B5 前置调研：畅课（TronClass）签到 API 静态分析

> **目的**：裁决 L0 假设台账 B/C（轮询通道与自动签到方向的共同前置闸门）。
> **方法**：真机拉取畅课 APK（`com.wisdomgarden.trpc`，70.8 MB，2026-10-15）→ 解包静态分析。
> 无需抓包：畅课是 **Capacitor 混合应用**，签到业务逻辑全部在 `assets/public/js/` 的
> Web bundle **明文**里（主包 `app.6c7f91e6.js` 4.0 MB），API 路由表、鉴权头、登录链路直接可读。

## 1. 发现签到（假设 B 核心证据）

学生端「进行中签到列表」与详情端点（前端路由 `/ongoing-rollcall-list` 消费）：

| 用途 | 请求 | 备注 |
|---|---|---|
| **进行中签到列表** | `GET /api/radar/rollcalls?api_version=1.1.0` | 响应 `rollcalls` 数组，项含 `courseId`（页面会再批量拉课程名/封面） |
| 签到详情（轻量） | `GET /api/rollcall/{id}/lite` | 单个签到的类型与状态 |
| 合并点名登记（教务考勤同步） | `PUT /api/rollcall/merged-rollcall/student-rollcalls`，body `{course_id}` | 「中间库导入课程 + 考勤率」学校专用，非老师手动点名 |

签到类型（与 ADR-0001「强校验排除」边界呼应）：二维码、数字、自主（手动点按钮）、雷达（位置/beacon）；
有设备共用检测（「已有学生使用该设备签到」）与 beacon nonce 校验。

**提交签到端点**（仅记录，自动签到仍按 ADR-0001 悬置，不实现）：
`POST /api/rollcall/{id}/answer_qr_rollcall`、`answer_number_rollcall`、`answer?api_version=1.76`、`answer_self_registration_rollcall`。

## 2. 鉴权与登录（假设 C 核心证据）

- **鉴权头**：axios 请求拦截器统一注入 `Authorization: Bearer <token>`（rms 类 API 另加 `X-CUSTOM-ID: <org.code>`）。
- **token 持久化**：畅课 Web 的 `localStorage["token"]`（代码铁证：登录浏览器流程里有
  `executeScript({code:'localStorage.removeItem("token");'})`）。
- **登录链路**（org 配置决定，`casApi` 存在即走 CAS）：`GET https://api-org.tronclass.com.cn/orgs?keywords=<校名>`
  查学校 → `POST {org.casApi}/cas/v1/tickets`（TGT）→ `POST .../tickets/{TGT}`（ST）→
  `GET /api/cas-login?ticket=<ST>` 换会话。**与教务系统同一套学校 CAS**。
  另有 `/api/login`（账密）、identity 服务 `access_token` 换登录等旁路。
- **API host**（2026-10-15 实测校准）：CHD = `https://course-online.chd.edu.cn`（orgs 接口 `apiUrl` 字段，学校自有域名）；orgs 查询需客户端固定头 `X-LC-Id`/`X-LC-Key`（APK 公开硬编码），响应结构 `{results:[{orgName, apiUrl, identityConfig:{auth-server-url, realm}}]}`。**CHD 登录走 identity 服务**（`identity.chd.edu.cn/auth`，OAuth 风格 realm=chd），非 CAS、无 webUrl/casApi 字段——静态分析阶段的 CAS 判断在此校准修正。

## 3. 假设判定

| 假设 | 判定 | 依据 |
|---|---|---|
| B：存在可复用登录态的「进行中签到列表」HTTP 接口 | **verified-通过** | `GET /api/radar/rollcalls` 端点、参数、响应结构（`rollcalls[].courseId`）全明文 |
| C：登录态来源与 Web 域名 | **verified-有条件通过** | 实测：CHD 走 identity 服务（identity.chd.edu.cn/auth，OAuth 风格）；token = `localStorage["token"]` 的 Bearer。剩余运行时校准点：token 有效期/刷新 |

## 4. 对 B5 实现的指引

- **轮询端点**：`GET {host}/api/radar/rollcalls?api_version=1.1.0`，发现非空 `rollcalls` → 组装
  `CheckInTriggerEvent(source = Polling, courseHint = 课程名映射)` 进现有触发管线（与通知源共用去重与会话逻辑）。
- **token 获取路径**（与教务登录架构同构，零新依赖）：本 App 内嵌 WebView 打开畅课 Web 首页 →
  用户经学校 CAS 登录（教务 CAS 会话可能已存在，实际体验或为一次跳转）→ 注入 JS 读
  `localStorage.getItem("token")` 存 DataStore。`SessionExpired` 轮询结果 → 提示用户重连。
- **克制约束**（L0 风险节沿用）：课时有窗轮询（`ClassWindowScheduler` 纯函数控闸），无课表数据显示原因不轮询。

## 5. 边界声明

本调研仅为只读静态分析；「提交签到」端点记录在案但按 ADR-0001 与单向门三条件悬置，
本期轮询通道只做「发现 + 提醒 + Mock 定位」，不代点签到。

## 6. 二轮深挖：签到提交机制与雷达可行性（2026-10-15，仍只记录不实现）

方法：同批解包材料，聚焦学生端提交链路的 chunk 级分析（雷达 2230 / 数字 386 / 自主 4413 /
二维码 1384 chunk + 主 bundle 服务层模块 65655 与拦截器模块 20699）。

### 6.1 四类提交全部是明文 PUT（无签名/无设备指纹）

| 类型 | 端点（PUT） | 请求体 |
|---|---|---|
| 雷达 | `/api/rollcall/{id}/answer?api_version=1.76` | `{deviceId, latitude, longitude, speed, accuracy, altitude, altitudeAccuracy, heading}`（GCJ02，客户端上报并已做 WGS84→GCJ02 转换） |
| 数字 | `/api/rollcall/{id}/answer_number_rollcall` | `{deviceId, numberCode}`（4 位码） |
| 自主 | `/api/rollcall/{id}/answer_self_registration_rollcall` | `{deviceId}` |
| 二维码 | `/api/rollcall/{id}/answer_qr_rollcall` | `{data: 二维码 JSON 内的 data, deviceId}`（服务端校验时效+hash） |

deviceId = Capacitor Device UUID（应用级明文字段，客户端可任意填）；`rollcall_device_already_in_use`
要求一台设备只绑一个账号。

### 6.2 雷达的 useBeacon 分叉（自动化可行性的分水岭）

先 `GET /api/rollcall/{id}/lite`（学生端仅返回 `{id, useBeacon}` 两字段）：

- **useBeacon=false（纯 GPS 雷达）**：技术可自动化——body 仅上表定位字段，坐标信任客户端，
  服务端算距离（响应含 `distance`；超范围错误 `radar_out_of_rollcall_scope` 可换坐标重试）。
- **useBeacon=true（蓝牙雷达）**：**纯软件不可行**——教师端开始点名时服务端下发 `beacon_nonce`
  （学生端 /lite 拿不到），教师手机原生 BLE 广播 `{rollcallId, nonce}`；学生必须原生 BLE 收到
  message 后本地算 `radarSignal = md5(message + deviceId + userId + timestamp) + "," + timestamp`，
  无 beacon 则客户端拒发。Web 层 BLE 是空 stub。注：签到成功后会用原 message 复播（「同学转发」
  机制），message 是静态串——但获取它需要物理在场收一次广播。

### 6.3 鉴权头实锤（修正一轮推断）

主 bundle 拦截器（模块 20699）：所有 `/api` 请求统一带 `X-Requested-With: XMLHttpRequest`、
`x-session-id: <登录 session>`、`Accept-Language`；`Authorization: Bearer` 仅 RMS 子系统使用。
→ B5 轮询已按此加固请求头（x-session-id 主 + Bearer 双发，[v130]），待首次真实登录后按日志收敛。

### 6.4 结论

- 单向门条件①（提交接口存在且可复用登录态）**完全满足**（含全参数）。
- 可自动化范围：**GPS 雷达 / 自主 / 数字 / 二维码（拿到码内容后）**；不可自动化：**蓝牙雷达**。
- 无请求签名、无设备指纹、无原生加密参数；防作弊实质 = beacon 带外分发（仅蓝牙）+
  服务端信任客户端坐标 + deviceId 一致性。
- ②用户显式二次确认、③新 ADR 修订 ADR-0001 维持未动（2026-10-15 用户裁决：暂不实现，只落本报告）。
