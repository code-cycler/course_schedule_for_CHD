# 课程表 CHD (Course Schedule for CHD)

一款专为长安大学（CHD）学生设计的课程表 Android 应用。

## 功能特性

### 课表核心
- **统一身份认证登录** - 通过学校 CAS 系统安全登录（System WebView）
- **自动获取课表** - 从教务系统自动获取并解析课程数据
- **当前教学周自动识别** - 登录时自动获取当前教学周，APP 启动自动跳转
- **今日高亮显示** - 课表中今日列高亮显示
- **周次切换** - 支持按周查看课程，快速定位当前周
- **周日期显示** - 课表表头显示每节对应日期
- **本地存储** - 使用 Room 数据库缓存课程数据，离线可查看
- **单双周识别** - 自动识别单周/双周课程
- **多教学班合并** - 同一课程多个教学班自动合并显示

### 课表编辑
- **课程手动编辑** - 点击课程卡片进入详情弹窗，支持编辑教师/教室/周次/节次
- **添加/删除时段** - 为同名课程添加新时段或删除已有时段
- **教师教室建议** - 输入教师/教室时自动提供下拉建议（从已有数据提取）
- **位图周次编辑** - 支持非连续周次精确编辑（53位位图选择器）

### 智能分析
- **课程冲突检测** - 位图精确判断周次重叠，自动检测课程时间冲突
- **冲突预计算缓存** - 导入时预计算所有周冲突，切换周次 O(1) 读取
- **课程类型分类** - 必修/选修/公选/体育/实践分类，支持水课标注
- **双校区支持** - 渭水/本部校区不同上课时间表自动适配

### 系统集成
- **系统日历同步** - 将课程同步到 Android 系统日历
- **课前提醒** - 可配置课前 N 分钟日历提醒
- **早八提醒** - 前一天晚上自动提醒次日早八课程

### 稳定性保障
- **崩溃处理** - 全局未捕获异常处理器，崩溃报告对话框
- **日志系统** - 内存缓存 + 磁盘持久化，保留最近 5 次会话日志
- **日志导出** - 一键导出日志文件，支持系统分享

### UI 设计
- **Material 3 设计** - 现代化的用户界面

## 技术栈

| 技术                  | 说明                     |
| --------------------- | ------------------------ |
| Kotlin                | 主要开发语言             |
| Jetpack Compose       | 现代 UI 框架             |
| Material 3            | UI 设计规范              |
| Room + KSP            | 本地数据库 + 注解处理器  |
| Retrofit + OkHttp     | 网络请求                 |
| Jsoup                 | HTML 解析                |
| System WebView        | CAS 登录 + 教务系统渲染  |
| Koin                  | 依赖注入                 |
| ViewModel + StateFlow | 状态管理                 |
| DataStore             | 轻量级数据存储           |
| CalendarProvider      | 系统日历集成             |

## 项目结构

```
app/src/main/java/com/example/course_schedule_for_chd_v002/
├── data/                       # 数据层
│   ├── local/                  # 本地数据
│   │   ├── database/           # Room 数据库 (AppDatabase, CourseDao, CourseEntity)
│   │   └── preferences/        # DataStore 偏好设置 (UserPreferences)
│   ├── remote/                 # 远程数据
│   │   ├── api/                # API 接口 (CasApi, EamsApi)
│   │   ├── client/             # 网络客户端 (EamsClient, CookieManager)
│   │   ├── dto/                # 数据传输对象
│   │   └── parser/             # HTML 解析器 (ScheduleHtmlParser)
│   └── repository/             # 仓库实现 (CourseRepositoryImpl)
├── di/                         # 依赖注入模块 (AppModule, NetworkModule, DatabaseModule)
├── domain/                     # 领域层
│   ├── model/                  # 领域模型 (Course, Campus, CourseType, DayOfWeek, Semester)
│   └── repository/             # 仓库接口 (ICourseRepository)
├── service/                    # 服务层
│   └── calendar/               # 系统日历同步 (CalendarSyncService)
├── ui/                         # UI 层
│   ├── components/             # 可复用组件 (CourseCard, ScheduleGrid, WeekSelector, SettingsDrawer)
│   │   └── edit/               # 课程编辑组件 (7个编辑器组件)
│   ├── navigation/             # 导航 (AppNavigation, Screen)
│   ├── screens/                # 页面
│   │   ├── login/              # 登录页 (WebViewScreen, LoginViewModel)
│   │   └── schedule/           # 课表页 (ScheduleScreen, ScheduleViewModel)
│   └── theme/                  # Material 3 主题
├── util/                       # 工具类 (ScriptInjector, AppLogger, CrashHandler, TimeUtils 等)
├── CourseApplication.kt        # Application 入口（CrashHandler 初始化、Koin 初始化）
└── MainActivity.kt             # 主 Activity
```

## 环境要求

- Android Studio Hedgehog 或更高版本
- JDK 17
- Android SDK 36 (Android 15)
- 最低支持: Android 12 (API 31)
- 目标设备: arm64-v8a 架构

## 构建项目

1. **克隆仓库**

   ```bash
   git clone https://github.com/your-username/course_schedule_for_CHD_v002.git
   cd course_schedule_for_CHD_v002
   ```
2. **打开项目**

   - 使用 Android Studio 打开项目目录
3. **同步依赖**

   - 等待 Gradle 同步完成
4. **构建 APK**

   ```bash
   # Debug 版本
   ./gradlew assembleDebug

   # Release 版本
   ./gradlew assembleRelease
   ```
5. **输出位置**

   - APK 文件位于: `app/build/outputs/apk/`
   - 自动命名格式: `课程表_CHD_v{版本}_{构建类型}_{日期}_{时间}.apk`

## 使用说明

1. **首次使用**

   - 打开应用，进入登录页面
   - 使用学校统一身份认证账号登录
2. **获取课表**

   - 登录成功后，请点击下方跳转到课表按钮跳转，然后选择正确的学期，再点击获取课表信息，软件自动从教务系统获取课表
   - 数据会缓存到本地，下次打开无需重新登录
3. **查看课表**

   - 主页面显示当前周的课程
   - 点击顶部周次选择器可切换周次
4. **编辑课程**

   - 点击课程卡片进入课程详情弹窗
   - 点击"编辑课程"进入编辑模式
   - 可修改教师、教室、周次（位图选择）、节次范围
   - 支持添加新时段或删除已有时段
5. **日历同步**

   - 从左侧滑出设置抽屉
   - 开启"系统日历同步"
   - 可配置课前提醒（提前分钟数）和早八提醒（前一天晚上提醒）

## 注意事项

- 本应用仅供学习参考和个人使用
- 请勿用于任何商业用途
- 使用前请确保手机已连接校园网或 VPN
- 如遇到登录问题，请检查网络连接

## 常见问题

**Q: 登录失败怎么办？**
A: 请检查网络连接，确保能访问学校教务系统网站。如仍有问题，可通过设置抽屉导出日志排查。

**Q: 课表显示不完整？**
A: 尝试重新登录获取最新数据，或检查是否有网络问题。

**Q: 如何编辑课程信息？**
A: 点击课程卡片 -> 详情弹窗 -> 编辑课程，可修改教师、教室、周次和节次。

**Q: 日历同步不生效？**
A: 请确保已授予日历读写权限。在设置抽屉中检查日历同步是否开启。

**Q: 应用崩溃了怎么办？**
A: 应用会在下次启动时自动弹出崩溃报告对话框，可导出日志文件进行排查。

**Q: 支持哪些设备？**
A: 目前仅支持 arm64-v8a 架构的 Android 设备（大多数现代手机），最低 Android 12。

## 贡献指南

本项目仅供学习参考，暂不接受代码贡献。

## 许可证

本项目采用自定义许可证，详见 [LICENSE](LICENSE) 文件。

- 仅供学习参考和个人使用
- 严禁商业使用
- 未经授权不得修改或用于其他目的

## 免责声明

本应用为非官方项目，与长安大学无关。使用本应用产生的任何问题，开发者不承担责任。

---

## 技术详解

本节详细介绍应用各核心模块的技术实现方案，包括网页渲染、课表解析、冲突检测、课程编辑、日历同步、崩溃处理等。

### 问题背景

长安大学教务系统（beangle 框架）存在以下技术挑战：

1. **JavaScript 依赖重** - 页面依赖 jQuery、beangle (bg)、underscore.js 等库
2. **动态渲染** - 课表数据通过 JavaScript 动态加载，不在原始 HTML 中
3. **WebView 兼容性** - Android WebView 无法正确加载教务系统的外部 JS 资源
4. **CAS 单点登录** - 需要处理 CAS 认证和 Cookie 同步

### 核心解决方案：脚本模拟注入

由于 WebView 无法加载教务系统的外部 JavaScript 库，我们采用**脚本模拟注入**策略，在页面加载前注入完整的 JavaScript 环境模拟。

#### 1. HTML 拦截注入 (shouldInterceptRequest)

```kotlin
// WebViewScreen.kt
override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
    val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)

    // 不拦截 CAS 登录页面
    if (url.contains("ids.chd.edu.cn")) {
        return super.shouldInterceptRequest(view, request)
    }

    // 只拦截首页和课表页面的 HTML 请求
    if (isEamsPage && (isHomePage || isCourseTablePage) && isHtml) {
        // 1. 获取原始 HTML
        val html = fetchOriginalHtml(url, cookies)

        // 2. 注入脚本
        val modifiedHtml = ScriptInjector.injectIntoHtml(html)

        // 3. 返回修改后的 HTML
        return WebResourceResponse("text/html", "UTF-8", modifiedHtml.byteInputStream())
    }
}
```

#### 2. JavaScript 环境模拟 (ScriptInjector.kt)

在 HTML `<head>` 标签后注入以下模拟脚本：

| 模拟对象 | 功能 | 关键方法 |
|---------|------|---------|
| **jQuery** | DOM 操作和 AJAX | `$()`, `.on()`, `.ajax()`, `.cookie()` |
| **beangle (bg)** | 教务系统框架 | `bg.Go()`, `bg.page()`, `bg.form.submit()` |
| **underscore.js** | 集合操作 | `_.filter()`, `_.where()`, `_.reject()` |
| **CourseTable** | 课表容器 | `new CourseTable(year, unitCounts)` |
| **TaskActivity** | 课程活动 | `new TaskActivity(teacherId, ...)` |

```kotlin
// ScriptInjector.kt - jQuery 模拟示例
fun getHeadInjectionScript(): String {
    return """
        if (typeof window.jQuery === 'undefined') {
            var jQuery = function(selector) {
                return new jQuery.fn.init(selector);
            };
            jQuery.fn = {
                each: function(callback) { ... },
                on: function(event, handler) { ... },
                ajax: function(options) { ... },
                cookie: function(name, value, options) { ... }
            };
            window.jQuery = jQuery;
            window.$ = jQuery;
        }
    """
}
```

### 登录流程状态机

登录过程采用状态机管理，确保各步骤正确执行：

```
CAS_LOGIN -> EAMS_HOME -> EXTRACT_HOME_HTML -> COURSE_TABLE -> DONE
```

| 状态 | 触发条件 | 执行动作 |
|------|---------|---------|
| CAS_LOGIN | 加载 CAS 登录页 | 等待用户登录 |
| EAMS_HOME | 登录成功跳转首页 | 重新加载首页以注入脚本 |
| EXTRACT_HOME_HTML | 首页加载完成 | 提取教学周信息，跳转课表页 |
| COURSE_TABLE | 课表页加载完成 | 注入脚本，等待渲染，提取 HTML |
| DONE | HTML 提取完成 | 解析课程数据，导航到课表界面 |

### 动态等待机制

课表页面需要等待 JavaScript 渲染完成后才能提取数据。我们实现了动态检测机制：

```kotlin
// WebViewScreen.kt - 动态等待页面就绪
private fun waitForPageReady(view: WebView, onReady: () -> Unit, onTimeout: () -> Unit) {
    var attempts = 0
    val maxAttempts = 25  // 500ms + 24*200ms = 5.3秒

    val checkRunnable = object : Runnable {
        override fun run() {
            view.evaluateJavascript("""
                (function() {
                    // 检测 table0.activities 是否存在
                    const hasTable0 = typeof table0 !== 'undefined';
                    const hasActivities = hasTable0 && table0.activities && table0.activities.length > 0;
                    return hasActivities;
                })();
            """) { result ->
                if (result == "true") {
                    onReady()  // 页面就绪
                } else if (attempts >= maxAttempts) {
                    onTimeout()  // 超时
                } else {
                    attempts++
                    view.postDelayed(this, 200)  // 200ms 后重试
                }
            }
        }
    }
    view.postDelayed(checkRunnable, 500)  // 初始等待 500ms
}
```

### HTML 解析流程

#### 1. JavaScript 数据结构

教务系统课表数据以 JavaScript 变量形式嵌入 HTML：

```javascript
// 教师信息
var teachers = [{id:3613, name:"朱依水", lab:false}];

// 课程名称
var courseName = "知识表征与推理(双语)(24ZY1816.01)";

// 课程活动对象
activity = new TaskActivity(
    "3613",                          // 教师ID
    "朱依水",                         // 教师名
    "59153(24ZY1816.01)",           // 课程ID
    courseName,                      // 课程名
    "24ZY1816.01",                   // 课程代码
    "633",                           // 教室ID
    "*WH2201",                       // 教室名 (*普通 #实验室)
    "00000000111111111100000...",   // 周数位图(53字符)
    null, "", "", ""
);

// 位置索引: index = dayOfWeek * 11 + nodeIndex
index = 3 * unitCount + 4;  // 周四第5节
table0.activities[index].push(activity);
```

#### 2. 解析算法 (ScheduleHtmlParser.kt)

```kotlin
// 解析课程块
val courseBlockPattern = """var\s+teachers\s*=\s*\[([^\]]*)\][\s\S]*?
                           var\s+courseName\s*=\s*"([^"]+)"([\s\S]*?)
                           (?=var\s+teachers|$)""".toRegex()

// 解析教室和周数位图
val roomAndWeeksPattern = ""","([*#]?[^",]*)","([01]{53})",""".toRegex()

// 解析位置索引
val indexPattern = """index\s*=\s*(\d+)\s*\*\s*unitCount\s*\+\s*(\d+)""".toRegex()
```

#### 3. 索引计算公式

```
index = dayOfWeek * unitCount + nodeIndex

其中:
- unitCount = 11 (每天11节课)
- dayOfWeek: 0=周一, 1=周二, ..., 6=周日
- nodeIndex: 0=第1节, 1=第2节, ..., 10=第11节

反向计算:
- dayOfWeek = index / unitCount + 1
- nodeIndex = index % unitCount + 1
```

### 单双周识别

通过分析 53 位周数位图识别单周/双周课程：

```kotlin
// ScheduleHtmlParser.kt
private fun determineWeekType(activeWeeks: List<Int>): WeekType {
    // 检查是否全是奇数周（单周）
    val allOdd = activeWeeks.all { it % 2 == 1 }
    // 检查是否全是偶数周（双周）
    val allEven = activeWeeks.all { it % 2 == 0 }

    return when {
        allOdd -> WeekType.ODD    // 单周
        allEven -> WeekType.EVEN  // 双周
        else -> WeekType.ALL      // 每周
    }
}
```

**位图示例：**
- 单周课程: `01010000010010001010000...` (第 2,4,9,11,14,16,19,21 周)
- 双周课程: `00101000101000101000101...` (第 3,5,8,10,13,15,18,20 周)
- 每周课程: `00000001111111111111111...` (第 8-18 周连续)

### 多教学班合并

当同一课程在同一时间有多个教学班时（如 C 语言有多个机房班），自动合并：

```kotlin
// 按 课程名+星期+节次 分组
val groupedCourses = courses.groupBy {
    Triple(it.name, it.dayOfWeek, Pair(it.startNode, it.endNode))
}

// 合并同一组的多条记录
for ((key, group) in groupedCourses) {
    if (group.size > 1) {
        // 1. 收集所有活跃周
        val allActiveWeeks = group.flatMap { extractActiveWeeks(it.remark) }
        // 2. 合并教室信息
        val allRooms = group.map { it.location }.distinct()
        // 3. 重新判断周类型
        val weekType = determineWeekType(allActiveWeeks)
        // 4. 创建合并后的课程记录
        mergedList.add(mergedCourse)
    }
}
```

### 当前教学周获取

从教务系统首页 HTML 解析当前教学周信息：

```html
<!-- 首页 HTML 格式 -->
<td>本周为<font color="blue">2025-2026学年第2学期的</font>
    第<font color="red" size="5">1</font>教学周</td>
```

```kotlin
// ScheduleHtmlParser.kt
fun parseCurrentWeek(html: String): Pair<String, Int>? {
    val doc = Jsoup.parse(html)
    val tdElements = doc.select("td")

    for (td in tdElements) {
        val text = td.text()
        if (text.contains("本周为") && text.contains("教学周")) {
            // 提取学期: "2025-2026学年第2学期" -> "2025-2026-2"
            val semesterPattern = """(\d{4})-(\d{4})学年第(\d)学期""".toRegex()
            // 提取周次: "第1教学周" -> 1
            val weekPattern = """第(\d+)\s*教学周""".toRegex()
            // ... 返回 Pair(semester, week)
        }
    }
}
```

获取到当前教学周后，反推学期开始日期并保存：

```kotlin
// LoginViewModel.kt
val semesterStartDate = TimeUtils.calculateSemesterStartDate(currentWeek)
userPreferences.saveSemesterStartDate(semesterStartDate)
userPreferences.saveCurrentWeek(currentWeek)
```

### Cookie 同步

WebView 登录成功后，需要同步 Cookie 到 OkHttp 用于后续请求：

```kotlin
// CookieManager.kt
fun syncCookiesFromWebView() {
    val cookieManager = CookieManager.getInstance()
    val cookies = cookieManager.getCookie(Constants.EamsUrls.BASE_URL)

    // 解析并添加到 OkHttp CookieJar
    cookies.split(";").forEach { cookie ->
        val parts = cookie.split("=")
        if (parts.size >= 2) {
            val name = parts[0].trim()
            val value = parts[1].trim()
            // 添加到 CookieJar...
        }
    }
}
```

### 课程冲突检测与预计算缓存

#### 位图精确周次判断

课程周次信息存储为 53 位位图字符串（`weeksBitmap:010101...`），每一位对应一周。通过位图精确判断两门课程是否有周次重叠，避免传统范围判断导致的误报：

```kotlin
// Course.kt - hasTimeConflict()
fun hasTimeConflict(other: Course): Boolean {
    if (dayOfWeek != other.dayOfWeek) return false

    // 使用缓存位图精确判断周次交集
    val thisBitmap = cachedWeeksBitmap
    val otherBitmap = other.cachedWeeksBitmap

    val hasWeekOverlap = if (thisBitmap != null && otherBitmap != null) {
        val minLen = minOf(thisBitmap.length, otherBitmap.length)
        var hasOverlap = false
        for (i in 0 until minLen) {
            if (thisBitmap[i] == '1' && otherBitmap[i] == '1') {
                hasOverlap = true; break
            }
        }
        hasOverlap
    } else {
        weekRange.intersect(other.weekRange).isNotEmpty()
    }
    if (!hasWeekOverlap) return false

    return nodeRange.intersect(other.nodeRange).isNotEmpty()
}
```

#### 冲突预计算缓存 (v74)

导入课表时一次性预计算所有周（1-25）的冲突信息，缓存到 DataStore。周次切换时直接读取缓存，实现 O(1) 性能：

```kotlin
// CourseRepositoryImpl.kt - precomputeAndCacheConflicts()
suspend fun precomputeAndCacheConflicts(courses: List<Course>, semester: String) {
    val conflictCache = mutableMapOf<Int, Set<Long>>()
    val maxWeek = courses.maxOfOrNull {
        it.getActiveWeeks().maxOrNull() ?: it.endWeek
    } ?: 25

    for (week in 1..maxWeek) {
        val weekCourses = courses.filter { it.isWeekInRange(week) }
        val conflictMap = TimeUtils.findConflicts(weekCourses)
        val conflictIds = conflictMap.keys
        conflictCache[week] = conflictIds
    }

    userPreferences.saveConflictCache(conflictCache, semester)
}
```

**性能对比：**
| 操作 | 修改前 | 修改后 |
|------|--------|--------|
| 周次切换 | O(n^2) 实时计算 | O(1) HashMap 查找 |
| 导入课表 | 无额外开销 | 一次性预计算 |
| 缓存完整性 | 无 | 校验最大周次覆盖 |

缓存完整性校验确保旧缓存（覆盖部分周次）不会导致遗漏：

```kotlin
// ScheduleViewModel.kt - loadSchedule()
val cachedMaxWeek = cache.keys.maxOrNull()
if (cache.isNotEmpty() && cachedMaxWeek != null && cachedMaxWeek >= maxWeek) {
    // 缓存完整，直接使用
} else {
    // 缓存不完整，重新预计算
}
```

#### 冲突详情与 UI 展示

点击课程卡片时，实时计算该课程与同周其他课程的具体冲突信息，包含重叠周次列表：

```kotlin
// ScheduleUiState.kt
data class CourseConflictInfo(
    val course1: Course,
    val course2: Course,
    val overlappingWeeks: List<Int>  // 冲突的具体周次
)
```

### 课程编辑系统

#### 编辑架构

课程编辑采用 ModalBottomSheet 容器，以"同名课程"为编辑单元，显示该课程所有时段：

```
CourseEditorSheet (ModalBottomSheet)
  |-- 课程名 + 类型 + 学分
  |-- CourseInstanceCard x N (每个时段一张卡片)
  |     |-- CourseInstanceEditor
  |     |     |-- SuggestionField (教师，带下拉建议)
  |     |     |-- SuggestionField (教室，带下拉建议)
  |     |     |-- DayOfWeekPicker (星期选择)
  |     |     |-- NodeRangePicker (节次范围选择)
  |     |     |-- WeekBitmapPicker (位图周次选择器)
  |     |-- 删除按钮
  |-- 添加新时段按钮
```

#### 位图周次编辑器

支持非连续周次的精确编辑，每个周次独立可选：

```kotlin
// WeekBitmapPicker.kt
@Composable
fun WeekBitmapPicker(
    maxWeeks: Int,
    initialBitmap: String?,
    onBitmapChanged: (bitmap: String, startWeek: Int, endWeek: Int) -> Unit
) {
    // 解析位图为已选周次集合
    val initialSelectedWeeks = remember(initialBitmap) {
        initialBitmap?.mapIndexedNotNull { index, c ->
            if (c == '1') index else null
        }?.toSet() ?: emptySet()
    }

    // 快捷操作: 全选、清空、单周、双周
    // 每行5个 FilterChip，支持点击切换
    // 输出位图格式: "weeksBitmap:010101..."
}
```

#### 教师/教室智能建议

编辑时自动从现有课程数据中提取教师和教室列表，提供输入建议：

```kotlin
// ScheduleViewModel.kt
val suggestedTeachers: List<String>  // 从同课程名提取
val suggestedLocations: List<String> // 从同学期所有课程提取
```

#### 编辑后缓存刷新

课程编辑（增删改）完成后，自动刷新冲突缓存和预计算缓存，确保数据一致性。

### 系统日历同步

#### 双校区上课时间表

日历同步支持两个校区不同的上课时间配置：

| 节次 | 渭水校区 | 本部校区 |
|------|---------|---------|
| 第1节 | 08:30-09:15 | 08:00-08:45 |
| 第2节 | 09:20-10:05 | 08:55-09:40 |
| 第3节 | 10:25-11:10 | 10:10-10:55 |
| 第4节 | 11:15-12:00 | 11:05-11:50 |
| 第5节 | 14:00-14:45 | 14:00-14:45 |
| ... | ... | ... |

#### 日历事件创建流程

```kotlin
// CalendarSyncService.kt
// 1. 获取/创建应用专用日历 ("长安大学课程表")
// 2. 遍历课程，计算每节课的实际日期
//    日期 = 学期开始日期 + (week-1)*7 + (dayOfWeek-1)
// 3. 根据校区获取上课时间 -> 创建 CalendarContract.Events
// 4. 可选: 创建课前提醒 (CalendarContract.Reminders)
// 5. 可选: 创建早八提醒 (前一天晚上的独立事件)
```

#### 同步结果统计

```kotlin
data class SyncResult(
    val successCount: Int,        // 成功创建的事件数
    val failCount: Int,           // 失败数
    val reminderCount: Int,       // 课前提醒数
    val earlyMorningCount: Int    // 早八提醒数
)
```

### 崩溃处理与日志系统

#### 全局异常处理器

```kotlin
// CrashHandler.kt
object CrashHandler {
    fun install(context: Context) {
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // 1. 写入崩溃堆栈到 crash_stacktrace.txt
            writeCrashStackTrace(throwable)
            // 2. 尽力刷新内存日志到磁盘
            AppLogger.flushToDisk()
            // 3. 委托给默认处理器
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun wasLastSessionCrash(context: Context): Boolean {
        // 以 crash_stacktrace.txt 是否存在作为判断依据
        // (非 session_active 标志，避免误判)
    }
}
```

#### AppLogger 双重写入日志系统

日志同时写入内存缓存和 Android logcat，定期刷写到磁盘会话文件：

```kotlin
// AppLogger.kt
object AppLogger {
    // 内存缓存（线程安全，最大 20000 条）
    private val logCache = Collections.synchronizedList(LinkedList<LogEntry>())

    // 磁盘持久化：保留最近 5 次会话日志
    // 每 50 条日志自动刷写磁盘

    fun d(tag: String, message: String) {
        addLog("D", tag, message)  // 写入缓存
        Log.d(tag, message)         // 写入 logcat
    }

    // 崩溃时调用，将缓存日志刷写到磁盘
    fun flushToDisk()
}
```

**日志导出流程：**
1. 用户触发导出 -> 合并当前会话内存日志 + 磁盘历史日志
2. 生成格式化文本文件
3. 通过 Android ShareSheet 分享

#### 启动时崩溃检测

```kotlin
// AppNavigation.kt
if (CrashHandler.wasLastSessionCrash(context)) {
    // 显示 CrashReportDialog
    // 包含: 崩溃堆栈 + 上次会话日志
    // 选项: 导出日志 / 关闭
}
```

### 当前教学周自动检测

#### 获取流程

```
登录成功 -> 加载首页 -> 解析 "本周为第N教学周" -> 反推学期开始日期 -> 保存
```

#### 日期反推算法

```kotlin
// TimeUtils.kt
fun calculateSemesterStartDate(currentWeek: Int): String {
    val today = LocalDate.now()
    val semesterStart = today.minusDays((currentWeek - 1).toLong() * 7)
    return semesterStart.format(DateTimeFormatter.ISO_LOCAL_DATE)
}

fun calculateCurrentWeek(semesterStartDate: String): Int? {
    val start = LocalDate.parse(semesterStartDate)
    val daysDiff = ChronoUnit.DAYS.between(start, LocalDate.now()).toInt()
    return (daysDiff / 7 + 1).takeIf { it in 1..30 }
}
```

#### UI 自动行为

- **APP 启动** -> 自动计算当前教学周，加载对应周课表
- **标题栏** -> 显示 "学期 + 教学周"（如 "2025-2026-2 第8周"）
- **今日高亮** -> 当前周课表中，今日列显示高亮背景
- **快速跳转** -> 非当前周时显示"回到第N周"按钮

### 课程类型分类与水课标注

#### 课程类型自动识别

```kotlin
enum class CourseType(val displayName: String) {
    REQUIRED("必修"),          // 专业必修
    ELECTIVE("选修"),          // 专业选修
    PUBLIC_ELECTIVE("公选"),   // 公共选修
    PHYSICAL_EDUCATION("体育"),
    PRACTICE("实践"),          // 实验/实习
    OTHER("其他");
}
```

#### 水课标注

用户可在课程详情弹窗中标记/取消"水课"，水课信息按学期存储在 DataStore：

```kotlin
// ScheduleViewModel.kt
fun toggleWaterCourse(courseName: String) {
    val isWaterCourse = courseName in _uiState.value.waterCourseNames
    _uiState.update {
        if (isWaterCourse) it.copy(waterCourseNames = it.waterCourseNames - courseName)
        else it.copy(waterCourseNames = it.waterCourseNames + courseName)
    }
    viewModelScope.launch { userPreferences.saveWaterCourses(...) }
}
```

### 周末折叠与自适应布局

课表网格支持周末折叠/展开，无周末课时自动隐藏以节省空间：

```
[周一] [周二] [周三] [周四] [周五] [展开周末 v]

点击展开后:

[周一] [周二] [周三] [周四] [周五] [周六] [周日]
```

折叠状态由外部控制，表头和课程区域同步折叠/展开。

### 关键文件索引

| 文件 | 功能 |
|------|------|
| **登录与解析** | |
| `ui/screens/login/WebViewScreen.kt` | WebView 登录界面，状态机管理，动态等待 |
| `util/ScriptInjector.kt` | JavaScript 环境模拟（jQuery, beangle, underscore） |
| `data/remote/parser/ScheduleHtmlParser.kt` | HTML 解析，单双周识别，多教学班合并 |
| `ui/screens/login/LoginViewModel.kt` | 登录流程管理，教学周处理 |
| **课表显示** | |
| `ui/screens/schedule/ScheduleScreen.kt` | 课表主界面，课程卡片，侧边栏 |
| `ui/screens/schedule/ScheduleViewModel.kt` | 课表状态管理，冲突预计算，课程编辑 |
| `ui/components/ScheduleGrid.kt` | 课表网格布局，今日高亮 |
| `ui/components/WeekSelector.kt` | 周次选择器 |
| **课程编辑** | |
| `ui/components/edit/CourseEditorSheet.kt` | 课程编辑主面板（ModalBottomSheet） |
| `ui/components/edit/CourseInstanceEditor.kt` | 单个时段编辑器（教师/教室/周次/节次） |
| `ui/components/edit/CourseInstanceCard.kt` | 时段卡片容器（编辑/删除操作） |
| `ui/components/edit/WeekBitmapPicker.kt` | 位图周次选择器（非连续周次） |
| `ui/components/edit/NodeRangePicker.kt` | 节次范围选择器 |
| `ui/components/edit/DayOfWeekPicker.kt` | 星期选择器 |
| `ui/components/edit/SuggestionField.kt` | 带下拉建议的输入框（教师/教室） |
| **系统集成** | |
| `service/calendar/CalendarSyncService.kt` | 系统日历同步，课前提醒，早八提醒 |
| `ui/components/SettingsDrawer.kt` | 设置侧边栏（日历同步、提醒配置） |
| `domain/model/ReminderSettings.kt` | 日历同步设置领域模型 |
| **稳定性** | |
| `CourseApplication.kt` | Application 入口，CrashHandler + AppLogger 初始化 |
| `util/CrashHandler.kt` | 全局未捕获异常处理器 |
| `ui/components/CrashReportDialog.kt` | 崩溃报告对话框 |
| `util/AppLogger.kt` | 日志缓存管理器（内存 + 磁盘持久化） |
| `util/LogExporter.kt` | 日志导出工具 |
| `ui/components/LogExportDialog.kt` | 日志导出对话框 |
| **领域模型** | |
| `domain/model/Course.kt` | 课程领域模型，位图周次判断，冲突检测 |
| `domain/model/Campus.kt` | 校区枚举（渭水/本部），上课时间表 |
| `domain/model/CourseType.kt` | 课程类型枚举（必修/选修/公选/体育/实践） |
| `data/local/preferences/UserPreferences.kt` | 偏好设置（学期开始日期、校区、日历同步） |
| **工具类** | |
| `util/TimeUtils.kt` | 时间工具（教学周计算、日期格式化） |
| `util/WebViewLogger.kt` | WebView 统一日志输出 |
| `util/ScriptInjector.kt` | JS 脚本注入（jQuery/beangle/underscore 模拟） |

### 版本演进

| 版本 | 主要改进 |
|------|---------|
| v50 | HTML 拦截注入，完整 jQuery/beangle 模拟 |
| v70 | 添加 underscore.js 支持 |
| v72 | 动态等待页面渲染（替代固定 3 秒等待） |
| v73 | 三步获取教学周和课表，首页 HTML 提取 |
| v74 | 冲突预计算缓存，周次切换 O(1) 性能 |
| v87 | 单双周识别功能 |
| v90 | 多教学班课程合并 |
| v91 | 修复多教学班连续节次合并问题 |
| v93 | 位图精确周次判断（53位 weeksBitmap） |
| v94 | 友好周次显示（非连续周次列表、单双周标注） |
| v96 | 修正位图索引（学校系统位图从第0周开始） |
| v98 | 系统日历同步，支持双校区不同上课时间 |
| v101 | 日历同步结果统计（成功/失败/提醒计数） |
| v107 | 日志缓存系统（AppLogger + 会话持久化） |
| v108 | 日志导出功能，全局崩溃处理 |
| v109 | 当前教学周自动检测，今日高亮，学期日期反推 |
| v110 | 课程手动编辑（CRUD 时段，位图周次编辑器，教师/教室建议） |
| v111 | 从 GeckoView 切换到 System WebView，应用架构重构 |

---

**开发者**: 缪承浩

**版本**: 1.0
