# TODO

> 来自 [docs/retro/checkin-assist_v1.md](docs/retro/checkin-assist_v1.md) 的行动项。格式：问题 → 行动 → 核验时机。

## minSdk 28（Android 9）兼容（design 2026-09-01，轻量模式）

- [x] **实现**（2026-09-01）：minSdk 31→28；清理 8 处 ObsoleteSdkInt 恒真守卫；补 2 条 values-en 水课翻译；README/AGENTS.md/CLAUDE.md 版本行同步。`test + lintDebug + assembleDebug` 全绿，APK minSdkVersion=28。
- [x] **API 28 模拟器实测**（2026-09-01 通过，api28-test AVD）：A4 安装/启动/课表 UI（静态色 fallback）；A5 签到辅助页 + 位置 CRUD（Room 2.7.0-alpha 运行时正常 + 持久化）；A6 Mock 定位触发全链路（**addTestProvider 反射旧签名在 API 28 成功** + 前台服务 + 通知 + 停止 Action）；A7 日志导出（私有目录回退路径按设计工作）。
- [ ] **Android 9 真机验收**（用户执行，需校园网/VPN + 教务账号）：WebView 登录教务系统 → 课表抓取渲染（脚本注入链路在真实 WebView 内核上的表现，模拟器无法覆盖）。——触发：拿到 Android 9 真机时。若旧 WebView 内核白屏，先引导用户更新 Android System WebView 再复测。

## README 更新（design 2026-09-01，轻量模式）

- [ ] **补「快速上手」章节截图**：README 中 6 处「📷 截图待补」占位（登录页 / 课表主界面 / 课程详情与编辑面板 / 学期选择器与新学期横幅 / 设置抽屉日历同步区 / 章首总占位），真机截图后替换占位标记。——核验：README 中 grep 不到「截图待补」。

## 签到辅助

- [x] **App 内加「停止虚拟定位」入口**（2026-07-28 实现）：签到辅助页「模拟签到触发」下方加了「停止虚拟定位」按钮，`MockLocationService.isRunning` 标志控制其仅在会话激活时显示，点击调 `MockLocationController.stop`。代码已编译通过，待真机重连验证。
- [ ] **开学后校准签到关键字**：收到真实畅课签到通知（雷达/数字/二维码）后，若不命中默认「签到/考勤/点名」，在签到辅助设置页直接改（免重编译）。——触发：开学首次签到。

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

- [ ] **实现 D1–D6**：`onCasLoginSuccess` 只升不降 + 解析失败中止 + Step3.5/0 门 toast；`parseHtmlToCourses`:348 / `fetchCourseTableWithOkHttp`:219 / `fetchRemoteSchedule`:406 移除 `saveCurrentSemester`；`AppNavigation`:114/141/169 回退改「当前学期→最近入库学期→空态」；`LoginUiState`:20 默认置空；`promoteCurrentSemester` 加只升守卫；删除表单登录死代码 4 处（login/onWebViewLoginSuccess/onFetchCourseTable/fetchCourseTableAndNavigate）。——核验：`./gradlew test` 全绿 + 新增 onCasLoginSuccess 场景单测（教务旧+用户新 → 不降级；教务新+用户旧 → 升级；解析失败 → 零写入）。
- [ ] **静态清点**：`grep -rn "2024-2025-1" app/src/main/java` 仅剩注释/语义说明，无活动路径。——核验：grep 时。
- [ ] **真机验证跨期窗口同步**（真实账号，校园网/VPN）：过渡期点「同步」不踢回旧学期、toast 正确；教务已切新学期时同步自动升级。——核验：下一个跨期窗口首次同步时。
