# CLAUDE.md（项目级 · course_schedule_for_CHD）

> 给 Claude Code 和「未来的自己」：快速上手 + 避坑。只记本项目特有的，通用规则见全局 `~/.claude/CLAUDE.md`。

## 这是什么

长安大学（CHD）课表 Android App。把教务系统（`bkjw.chd.edu.cn`，beangle 框架）的课表搬到手机、可离线查看。核心 hack：用 WebView 登录 + 注入自写 JS 环境模拟，抠出渲染后的 HTML 再解析。

技术栈：Kotlin · Jetpack Compose · Material 3 · Room · Koin · OkHttp · Navigation Compose · DataStore。

## 构建

```bash
./gradlew assembleDebug        # Debug APK
./gradlew assembleRelease      # Release APK（混淆+资源压缩）
./gradlew test                 # 单元测试
./gradlew connectedAndroidTest # 仪器测试（需连真机/模拟器）
```

- JDK 17 · AGP 9.0.0 · Kotlin 2.0.21 · Compose BOM 2024.09.00
- compileSdk/targetSdk 36 · minSdk 31（Android 12+）
- **只打 arm64-v8a**（其它架构被 packaging 排除）
- APK 自动命名：`课程表_CHD_v{versionName}_{buildType}_{yyyyMMdd}_{HHmmss}.apk`
- **版本号真相**：以 git tag 为准（当前 `v2.2`）。`app/build.gradle.kts` 的 `versionName` 必须与最新 tag 对齐；`versionCode` 暂未随发布递增。

## 架构速览（详见 [docs/DESIGN.md](docs/DESIGN.md)）

```
ui/        Compose 屏幕 + ViewModel(StateFlow)。启动直接进 Schedule，"同步"按钮进 Login
domain/    纯模型(Course/Campus/...) + ICourseRepository 接口
data/      remote(EamsClient/CasApi/EamsApi/CookieManager/ScheduleHtmlParser)
           local(database: Room / preferences: DataStore)
           repository/CourseRepositoryImpl(整合)
di/        Koin: networkModule / databaseModule / appModule
util/      ScriptInjector / TimeUtils / JsonUtils / Constants / WebViewLogger ...
```

登录数据流：`WebViewScreen`(状态机 CAS_LOGIN→…→DONE) → `ScriptInjector` 注入假 jQuery/beangle/underscore → 抠 HTML → `ScheduleHtmlParser.parse` → Room 入库 → `parseCurrentWeek` 得学期+周 → Schedule 展示。

## ⚠ 改代码前必知的坑

1. **周次位图是周次判断的真值来源**。`Course.remark` 里存 `weeksBitmap:0101…`（53 位）。**bitmap[0] = 第 0 周（预备周），位图下标即周次**。`Course.isWeekInRange/hasTimeConflict/getWeeksDisplayText` 都从它提取；改 `ScheduleHtmlParser.parseWeeksBitmap` 和 `Course` 的周次逻辑前，务必先看 `docs/DESIGN.md` §6 的「位图偏移」。
2. **登录只走 WebView**。`AppNavigation` 的 Login 路由直接渲染 `WebViewScreen`；`LoginScreen.kt` + `LoginViewModel.login()` 表单登录路径是**未接入的死代码**。别在表单登录上加功能。
3. **网络全是 OkHttp**（`EamsClient` 封装，绑 `CookieManager` 作 CookieJar）。没有 Retrofit——别找 Retrofit 接口（曾经有 `EamsService` 空壳和 Retrofit 依赖，已删除）。
4. **两套版本号**：发布版（git tag `v2.x`）≠ 代码注释里的内部迭代号 `[v97]`。后者只追踪开发改动。
## 文档导航

| 文档 | 内容 |
|------|------|
| [README.md](README.md) | 是什么 / 怎么用（用户向） |
| [docs/DESIGN.md](docs/DESIGN.md) | 设计备忘：愿景 / 架构 / 决策理由 / 坑（改代码先看这个） |
| [docs/OPEN-DECISIONS.md](docs/OPEN-DECISIONS.md) | 未决问题与历史遗留（含 revisit 触发条件） |
| [docs/eams-urls.txt](docs/eams-urls.txt) | 教务系统关键 URL（亦见 `util/Constants.kt`） |

## 工作约定

- **中文回复**（继承全局）。
- 改动若触及 `OPEN-DECISIONS.md` 列出的历史遗留，先看该条的「Trigger」是否满足，不满足就继续 defer、只在文档里如实记录现状。
- 内部迭代号 `[vXX]` 注释风格保留——它是改动追踪的辅助线索，但**不等于发布版本**。
- 文件版本命名、废弃目录等规则沿用全局 `~/.claude/CLAUDE.md`。
