# 课程表 CHD（Course Schedule for CHD）

一款把长安大学（CHD）教务系统课表搬到手机、并支持离线查看的 Android 应用。

教务系统（beangle 框架）的课表页重度依赖 JavaScript 动态渲染，系统 WebView 直接打开会白屏。本应用的做法是：**在内置 WebView 里完成 CAS 登录，注入自写的 JS 环境模拟让页面正常渲染，再把渲染后的课表 HTML 抠出来解析入库**。技术细节见 [docs/DESIGN.md](docs/DESIGN.md)。

## 功能特性

**课表获取与展示**
- **WebView CAS 登录** — 在内置浏览器登录学校统一身份认证，登录后自动抓取课表与当前教学周
- **当前教学周自动识别** — 从教务系统首页解析，App 启动自动跳到当前周；浏览其它周时显示「回到第 X 周」
- **今日高亮** — 课表中今日所在列高亮（仅在当前教学周）
- **周次浏览** — 左右滑动（HorizontalPager）或顶部周选择器切周，最大周数按实际课表动态确定
- **课程详情** — 点击课程卡片查看教师 / 地点 / 周次 / 节次
- **本地离线缓存** — Room 数据库，登录获取一次后即可离线查看

**课表智能处理**
- **课程冲突检测** — 自动检测时间冲突并高亮；用周数位图精确判断，正确处理单双周 / 非连续周；导入时预计算缓存，切周不卡
- **单双周识别** — 根据周数位图识别单周 / 双周 / 每周
- **多教学班合并** — 同一课程的多个教学班（如多个机房班）自动合并
- **连续节次合并** — 同一课程相邻节次合并为一张卡片

**个性化**
- **水课标注** — 在课程详情里标注 / 取消「水课」，按学期保存
- **校区切换** — 渭水 / 本部两个校区，切换后上课时间表相应变化
- **周末折叠** — 周末无课时自动折叠，并显示周六 / 周日「是否有课」指示器

> **已实现但界面暂未接入**：课表的 JSON 导入 / 导出（后端逻辑完整，UI 入口尚未提供）。

## 技术栈

| 技术 | 说明 |
| --- | --- |
| Kotlin | 主要开发语言 |
| Jetpack Compose + Material 3 | UI 框架与设计规范 |
| Room | 课程数据本地数据库 |
| OkHttp（经 `EamsClient` 封装） | 网络请求 |
| Jsoup | HTML 解析 |
| System WebView | CAS 登录与课表页面渲染 |
| Koin | 依赖注入 |
| ViewModel + StateFlow | 状态管理 |
| DataStore | 偏好与缓存（校区 / 教学周 / 水课 / 冲突缓存） |
| Navigation Compose | 页面导航 |

## 项目结构

```
app/src/main/java/com/example/course_schedule_for_chd_v002/
├── MainActivity.kt                 # 入口，初始化 Koin 与导航
├── data/
│   ├── local/
│   │   ├── database/               # Room：AppDatabase / CourseDao / entity/CourseEntity
│   │   └── preferences/            # DataStore：UserPreferences
│   ├── remote/
│   │   ├── api/                    # CasApi、EamsApi（OkHttp 裸调）
│   │   ├── client/                 # EamsClient（OkHttp 封装）、CookieManager
│   │   ├── dto/                    # CasLoginPage
│   │   ├── parser/                 # ScheduleHtmlParser
│   │   └── EamsService.kt          # （空壳，未使用）
│   └── repository/                 # CourseRepositoryImpl
├── di/                             # Koin：AppModule / NetworkModule / DatabaseModule
├── domain/
│   ├── model/                      # Course、Campus、CourseType、DayOfWeek、Semester
│   └── repository/                 # ICourseRepository（接口）
├── ui/
│   ├── components/                 # ScheduleGrid、CourseCard、WeekSelector
│   ├── navigation/                 # AppNavigation、Screen
│   ├── screens/login/              # WebViewScreen（登录入口）、LoginViewModel …
│   ├── screens/schedule/           # ScheduleScreen、ScheduleViewModel、ScheduleUiState
│   └── theme/                      # Color、Theme、Type
└── util/                           # ScriptInjector、TimeUtils、JsonUtils、Constants、WebViewLogger …
```

> 设计与决策详见 [docs/DESIGN.md](docs/DESIGN.md)，未决问题见 [docs/OPEN-DECISIONS.md](docs/OPEN-DECISIONS.md)。

## 环境要求

- Android Studio（需支持 **AGP 9.0** 的较新版本）
- JDK 17
- Android SDK 36（Android 15）
- 最低支持：Android 12（API 31）
- 目标 ABI：**仅 arm64-v8a**（其它架构已被 packaging 排除）

## 构建项目

```bash
# 克隆
git clone https://github.com/code-cycler/course_schedule_for_CHD.git
cd course_schedule_for_CHD

# Debug / Release
./gradlew assembleDebug
./gradlew assembleRelease
```

- APK 输出：`app/build/outputs/apk/`
- 自动命名：`课程表_CHD_v{版本}_{构建类型}_{日期}_{时间}.apk`

## 使用说明

1. **首次使用** — 打开 App 直接进入课表页（此时为空），点击右上角「**同步**」。
2. **登录** — 在弹出的 WebView 中登录学校统一身份认证（需连接校园网或 VPN）。
3. **自动获取** — 登录成功后，App 会自动获取首页（解析当前教学周）和课表页面，解析后入库，并自动返回课表页。
4. **日常使用** — 之后无需再次登录，离线即可查看；新学期再点「同步」重新获取。

## 版本说明

- **当前发布版本：v2.2**（git tag）
- ⚠ 代码注释里的 `[vXX]`（如 `[v97]`）是**内部迭代编号**，仅用于追踪开发改动，**不是发布版本**。发布版本以 git tag（`v1.0` / `v2.0` / `v2.1` / `v2.2`）为准。
- 版本演进与设计决策见 [docs/DESIGN.md](docs/DESIGN.md)。

## 注意事项

- 仅供学习参考和个人使用，**严禁商业用途**。
- 使用前请确保手机已连接校园网或 VPN，能访问 `bkjw.chd.edu.cn`。
- 登录遇到问题多为网络/Cookie 失效，可在课表页点「同步」重新登录获取。
- 本应用为**非官方**项目，与长安大学无关；使用产生的任何问题，开发者不承担责任。

## 许可证

见 [LICENSE](LICENSE)：仅供学习参考和个人使用，严禁商业用途，未经授权不得修改或用于其他目的。
