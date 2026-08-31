# TODO

> 来自 [docs/retro/checkin-assist_v1.md](docs/retro/checkin-assist_v1.md) 的行动项。格式：问题 → 行动 → 核验时机。

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
