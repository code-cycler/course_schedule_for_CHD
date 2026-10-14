# TODO

> 来自 [docs/retro/checkin-assist_v1.md](docs/retro/checkin-assist_v1.md) 的行动项。格式：问题 → 行动 → 核验时机。

## ux-perf-export 四批实现收尾（2026-09-13 代码全绿：149 测试 + lintDebug + assembleDebug；同日真机验证通过）

- [x] **C/D 批真机冒烟**（2026-09-13 用户确认真机验证通过；A 批此前已单独验证）。
- [x] **B 批 PerfLog 数据留档**（关闭）：打点与迁移同批落地、迁移前基线无法回溯，行为等价以 4 个锚单测（迁移前后全绿）为最终证据；打点已就位，后续任何真机会话 `adb logcat -s PerfLog` 可随时取数。
- [x] **API 28 模拟器核验**（关闭，降为可选）：导出用固定两套配色不依赖 Material You，API 28 与 31+ 走同一份导出代码路径；真机（API 31+）验证已通过，风险极低。若未来收到 API 28 用户反馈再回补。
- [ ] **JDK 固定**（仍开放）：Gradle daemon 若被系统 JDK 26 拉起，mockk（Byte Buddy 1.14.6）不支持会致单测挂——命令行构建前 `export JAVA_HOME=/Users/miao/.gradle/jdks/eclipse_adoptium-17-aarch64-os_x.2/jdk-17.0.19+10/Contents/Home`，或在 `~/.gradle/gradle.properties`（用户级，不入库）固定 `org.gradle.java.home`。——核验：下次命令行跑 test 前。
- [x] **DESIGN.md 补记**（2026-09-13 完成，commit b8f7a4d）：§6¾ ux-perf-export 专区（导出配色/性能迁移/UI 修缮/静默同步 + 迭代号映射）+ §6 三条新坑（JDK26 mockk/成员初始化顺序/diff 忽略 id）。

## ux-perf-export 压测修订（design 2026-09-13，grill-W01 全 7 题已逐题裁决，2026-09-13 用户授权后已全部落地工件）

- [x] **R1（Q1）**：L2 D-1 的 `fetchRemoteCoursesOnly` 实现基准由「从 fetchRemoteSchedule 抽」改为「`getRemoteSemesterOptions()` 解析当前学期 remoteId → `getCourseTableHtml(remoteId)`」（在用验证路径）；L1 模块边界表与接口规格同步补 remoteId 解析步骤。——核验通过：grep「从 fetchRemoteSchedule 抽」零命中。
- [x] **R2（Q2）**：L0 风险节补「静默同步更新检测覆盖率取决于 cookie 存活窗口（CAS session 时效），冷启动多为静默 no-op」；L2 D-5 开关文案加「需近期登录过教务系统后生效」。——核验通过：两文件关键词命中。
- [x] **R3（Q3）**：L2 D-4 确认对话框加明示「确认后将以教务数据替换本地全部课程（含你手动添加/编辑的 N 门）」+ 差异清单「本地独有」打标；L0 验收 D2–D3 补该警示要求。——核验通过：L0/L2 各命中。
- [x] **R4（Q4）**：L2 D 新增第 7 点「手动同步导航（onNavigateToLogin）前重置 silentSyncState 为 Idle」，防过期 diff 二次覆盖。——核验通过：设计含互斥条款。
- [x] **R5（Q5）**：L2 D-1 补两边界——`getCurrentSemester()` 为 null 时静默跳过；拉取前 `cookieManager.syncFromWebView` 兜底（对齐 fetchSpecifiedSemester）。——核验通过：syncFromWebView 命中。
- [x] **R6（Q6）**：L1 契约第 8 条 + L2 接口规格表：`lastSilentSyncAt` 由 Long 键改 `stringPreferencesKey` 存 epoch millis 字符串（项目范式一致性）。——核验通过：「Long, 0」零残留。
- [x] **R7（Q7）**：L2 B 批阶段表与详细设计前插「先补 `onWeekSelected`/`coursesByWeek` 派生行为单测作迁移前回归锚」；B 批 DoD「行为目视不变」降级为补充冒烟。——核验通过：回归锚 3 处命中。

## 桌面小部件（design 2026-09-13，ux-perf-export W01 Q1-E 用户勾选但明确「放 TODO」）

- [ ] **今日课程桌面小部件**：Glance/AppWidget 新链路（体量大），从 ux-perf-export 批拆出为独立 feature 后再走设计。——触发：ux-perf-export 批（A/B/C 交付）完成后启动。

## minSdk 28（Android 9）兼容（design 2026-09-01，轻量模式）

- [x] **实现**（2026-09-01）：minSdk 31→28；清理 8 处 ObsoleteSdkInt 恒真守卫；补 2 条 values-en 水课翻译；README/AGENTS.md/CLAUDE.md 版本行同步。`test + lintDebug + assembleDebug` 全绿，APK minSdkVersion=28。
- [x] **API 28 模拟器实测**（2026-09-01 通过，api28-test AVD）：A4 安装/启动/课表 UI（静态色 fallback）；A5 签到辅助页 + 位置 CRUD（Room 2.7.0-alpha 运行时正常 + 持久化）；A6 Mock 定位触发全链路（**addTestProvider 反射旧签名在 API 28 成功** + 前台服务 + 通知 + 停止 Action）；A7 日志导出（私有目录回退路径按设计工作）。
- [ ] **Android 9 真机验收**（用户执行，需校园网/VPN + 教务账号）：WebView 登录教务系统 → 课表抓取渲染（脚本注入链路在真实 WebView 内核上的表现，模拟器无法覆盖）。——触发：拿到 Android 9 真机时。若旧 WebView 内核白屏，先引导用户更新 Android System WebView 再复测。

## README 更新（design 2026-09-01，轻量模式）

- [ ] **补「快速上手」章节截图**：README 中 6 处「📷 截图待补」占位（登录页 / 课表主界面 / 课程详情与编辑面板 / 学期选择器与新学期横幅 / 设置抽屉日历同步区 / 章首总占位），真机截图后替换占位标记。——核验：README 中 grep 不到「截图待补」。

## 签到辅助

- [x] **验证假设 D：`requestRebind` 程序化重绑在荣耀上的有效性**（checkin-assist-v2 grill W01 Q1，2026-10-14 真机裁决）：**无效**——冷启动调用成功但未触发系统重绑（onListenerConnected 不出现）；`cmd notification allow_listener`（=手动重开开关）有效。已按设计降级纯引导（警示条 + 去设置，装机场景实证有效）；requestRebind 代码保留无害。L0 假设台账已更新。
- [x] **真机统一验证清单**（2026-10-14，荣耀 SER-AN00）：迁移 v2→v3 ✅（备份文件生成可解析 + user_version=3 + 位置数据保留且管线正常消费）；端到端自测通知管线 ✅（发送→白名单→识别→courseHint→触发 Mock）；A5 会话去重 ✅（16 秒后二次命中跳过）；NLS 心跳 ✅（重绑后 onListenerConnected 落盘）；A6/测试按钮组/位置导出 ✅（UI dump 全元素在位 + 导出 JSON 生成）。**两项新发现**：①开机 BOOT_COMPLETED 被荣耀 BootProxy 扣留（don't meet cpuload，自启动管控，L0 假设 E）②mock channel 曾被降级（mOriginalImp=4→mImportance=3，A9 降级引导有现实意义）。——遗留：位置导出→清数据→导入回环（用户 2 分钟自测，导出已验）+ 真实签到（F007）。
- [x] **grill W01 压测修订执行**（2026-10-13，用户授权 8/8 全部执行）：①假设 D 进 L0 风险台账 + TODO（本节上一条）②L1 契约 2 + L2 B2 加「当前节次课表推断」courseHint 兜底 ③L2 B1 加迁移前 JSON 备份 + DoD 验证项 ④L2 B3 加冷启动无条件 requestRebind + 心跳超阈值低优先级通知 ⑤L2 B4 加位置数据导出/导入入口（与 B1 备份共用序列化器）⑥L1 契约 7 + L2 B5 加无课数据默认行为与状态显示 ⑦CONTEXT 三处术语修订（畅课 ≠ 学习通、自动签到边界悬置加注、v2 选位置规则）⑧L0 验收 A9 具体化为三步验证。——核验：grep「grill W01」在 L0/L1/L2 三份层文件命中。
- [ ] **checkin-assist-v2 收尾衔接**（design 2026-10-13 收尾面板裁决，压测已完成 2026-10-13）：由 long-running-agent 从 L2 的 B1–B5 阶段表反推 feature_list，**单线程**顺序开工（B1–B4 可立即开工，B5 挂假设 B/C 闸门）。——触发：用户发起实现时。

- [x] **App 内加「停止虚拟定位」入口**（2026-07-28 实现）：签到辅助页「模拟签到触发」下方加了「停止虚拟定位」按钮，`MockLocationService.isRunning` 标志控制其仅在会话激活时显示，点击调 `MockLocationController.stop`。代码已编译通过，待真机重连验证。
- [ ] **验证假设 A：签到通知可达性 + 关键字校准**（checkin-assist-v2 L0 W00 要点 16，合并原「开学后校准签到关键字」遗留项）：下次真实签到时，先强杀畅课进程（最近任务划掉或应用信息里强停），观察系统通知是否仍到达——裁决荣耀厂商通道对签到类推送是否兜底（L0 验收 A1）；同时记录通知实际标题/正文，校准默认关键字「签到/考勤/点名」（L0 验收 A2）。——触发：下次课堂签到。手机端可自助完成，不依赖电脑。
- [x] **验证假设 B/C：畅课签到 API 与登录态来源**（checkin-assist-v2 L0 W00 要点 3；2026-10-15 完成，静态分析替代抓包——畅课为 Capacitor 混合应用业务逻辑全在 JS 明文，见 [B5-trpc-api-research.md](harness/design/checkin-assist-v2/B5-trpc-api-research.md)）：B 通过（`GET /api/radar/rollcalls` 进行中列表）；C 有条件通过（学校 CAS 登录 + Bearer token 存畅课 Web localStorage）。**B5 轮询闸门已过**，运行时校准点（token 有效期/CHD 域名）由 B5 实现消化。

## 跨学期自动检测与切换（design 2026-08-31，见 [harness/design/cross-semester/L0-cross-semester.md](../harness/design/cross-semester/L0-cross-semester.md)）

- [x] **实现**（2026-08-31，[v115]）：日期规则 2/15、8/15（含当日）+ ON_RESUME 过期检测 + 可关闭横幅 + 点击免登录获取/直接切换 + 同步自动追加 + `promoteCurrentSemester` 升级规则。`./gradlew test` 全绿（SemesterInferenceTest 9 / ScheduleViewModelTest 7 / LoginViewModelTest 6 / CourseRepositoryImplTest 8）。
- [x] **真机回归**（2026-08-31 通过）：横幅出现/关闭/次日重现；横幅点击获取（Cookie 有效与失效两分支）；顶栏同步自动追加；本地切换学期、「获取其它学期」、非当前学期表头降级不破坏。
- [ ] **开学后实测教务边界行为**（真实账号）：①教务首页「本周为第X教学周」在 8/15–开学间的显示；②`dataQuery.action` 的 `semesterId` 何时切新学期；③新学期课表发布时点（0 门降级路径频率）。→ 校准「同步自动追加」与降级文案。——核验：开学首次同步时。

## 流程 / 规范（跨功能复用）

- [ ] **DoD 模板加两条前置**：①「真机验证窗口」（涉及权限/定位/通知监听必须早连真机）；②「无真实数据时如何验证」。——核验：下个功能设计时。
- [ ] **grilling 第一波前置硬约束**：涉及第三方 SDK/外部依赖的功能，把免Key/室内精度/依赖体积/坐标转换等作为必答项，避免方案反转。——核验：下次做此类功能时。
- [ ] **荣耀机型坑汇总到 `CLAUDE.md`**：NLS 装机不绑定（需重开通知使用权）、USB 安装确认框、自启动/后台保活、增量构建残包——单源可查，避免重复踩。——核验：下次连真机前先查。
- [ ] **编码规范（反射/系统 API）**：反射必须解包 `InvocationTargetException`；系统 API 方法名用 `javap -classpath android.jar` 查 jar 不信网传；能编译期直接调用就不反射。——核验：写反射代码时。
- [ ] **权限设计覆盖两类**：运行时申请 + 外部设置页（含回到前台刷新机制）。——核验：下次做权限功能时。

## 同步不再打回旧学期（design 2026-09-01，轻量模式，见 [harness/design/cross-semester/L0-sync-preserve-semester.md](harness/design/cross-semester/L0-sync-preserve-semester.md) + [ADR-0005](docs/adr/0005-sync-current-semester-monotonic.md)）

- [x] **实现 D1–D6**（2026-09-01，[v118]）：`onCasLoginSuccess` 只升不降 + 解析失败中止 + Step3.5/0 门 toast；`parseHtmlToCourses`:348 / `fetchCourseTableWithOkHttp`:219 / `fetchRemoteSchedule`:406 移除 `saveCurrentSemester`；`AppNavigation`:114/141/169 回退改「当前学期→最近入库学期→日期推断」；`LoginUiState`:20 默认置空；删除表单登录死代码（login/onWebViewLoginSuccess/onFetchCourseTable/fetchCourseTableAndNavigate 等 8 方法 + LoginScreen.kt）。——核验：`./gradlew test` 134/0 失败 + assembleDebug + lintDebug 全绿；LoginViewModelTest 9 用例含只升不降三场景。
- [ ] **静态清点**：`grep -rn "2024-2025-1" app/src/main/java` 仅剩注释/语义说明，无活动路径。——核验：grep 时。
- [ ] **真机验证跨期窗口同步**（真实账号，校园网/VPN）：过渡期点「同步」不踢回旧学期、toast 正确；教务已切新学期时同步自动升级。——核验：下一个跨期窗口首次同步时。
