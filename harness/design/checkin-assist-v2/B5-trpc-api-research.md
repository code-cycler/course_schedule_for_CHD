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
