# 课程表 CHD (Course Schedule for CHD)

一款专为长安大学（CHD）学生设计的课程表 Android 应用。

## 功能特性

- **统一身份认证登录** - 通过学校 CAS 系统安全登录
- **自动获取课表** - 从教务系统自动获取并解析课程数据
- **当前教学周自动识别** - 登录时自动获取当前教学周，APP 启动自动跳转
- **今日高亮显示** - 课表中今日列高亮显示
- **周次切换** - 支持按周查看课程，快速定位当前周
- **本地存储** - 使用 Room 数据库缓存课程数据，离线可查看
- **课程冲突检测** - 自动检测课程时间冲突
- **单双周识别** - 自动识别单周/双周课程
- **多教学班合并** - 同一课程多个教学班自动合并显示
- **Material 3 设计** - 现代化的用户界面

## 技术栈

| 技术                  | 说明                 |
| --------------------- | -------------------- |
| Kotlin                | 主要开发语言         |
| Jetpack Compose       | 现代 UI 框架         |
| Material 3            | UI 设计规范          |
| Room                  | 本地数据库           |
| Retrofit + OkHttp     | 网络请求             |
| Jsoup                 | HTML 解析            |
| System WebView        | Android 系统 WebView |
| Koin                  | 依赖注入             |
| ViewModel + StateFlow | 状态管理             |
| DataStore             | 轻量级数据存储       |

## 项目结构

```
app/src/main/java/com/example/course_schedule_for_chd_v002/
├── data/                       # 数据层
│   ├── local/                  # 本地数据
│   │   ├── database/           # Room 数据库
│   │   └── preferences/        # DataStore 偏好设置
│   ├── remote/                 # 远程数据
│   │   ├── api/                # API 接口
│   │   ├── client/             # 网络客户端
│   │   ├── dto/                # 数据传输对象
│   │   └── parser/             # HTML 解析器
│   └── repository/             # 仓库实现
├── di/                         # 依赖注入模块
├── domain/                     # 领域层
│   ├── model/                  # 领域模型
│   └── repository/             # 仓库接口
├── ui/                         # UI 层
│   ├── components/             # 可复用组件
│   ├── navigation/             # 导航
│   ├── screens/                # 页面
│   └── theme/                  # 主题
└── util/                       # 工具类
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

## 注意事项

- 本应用仅供学习参考和个人使用
- 请勿用于任何商业用途
- 使用前请确保手机已连接校园网或 VPN
- 如遇到登录问题，请检查网络连接

## 常见问题

**Q: 登录失败怎么办？**
A: 请检查网络连接，确保能访问学校教务系统网站。

**Q: 课表显示不完整？**
A: 尝试重新登录获取最新数据，或检查是否有网络问题。

**Q: 支持哪些设备？**
A: 目前仅支持 arm64-v8a 架构的 Android 设备（大多数现代手机）。

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

## 技术详解：网页渲染问题处理方案

本节详细介绍应用如何处理教务系统网页的 JavaScript 依赖和动态渲染问题。

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

### 关键文件索引

| 文件 | 功能 |
|------|------|
| `ui/screens/login/WebViewScreen.kt` | WebView 登录界面，状态机管理，动态等待 |
| `util/ScriptInjector.kt` | JavaScript 环境模拟（jQuery, beangle, underscore） |
| `util/WebViewLogger.kt` | 统一日志输出，支持单双周识别专用日志 |
| `data/remote/parser/ScheduleHtmlParser.kt` | HTML 解析，单双周识别，多教学班合并 |
| `ui/screens/login/LoginViewModel.kt` | 登录流程管理，教学周处理 |
| `data/local/preferences/UserPreferences.kt` | 学期开始日期存储 |

### 版本演进

| 版本 | 主要改进 |
|------|---------|
| v50 | HTML 拦截注入，完整 jQuery/beangle 模拟 |
| v70 | 添加 underscore.js 支持 |
| v72 | 动态等待页面渲染（替代固定 3 秒等待） |
| v73 | 三步获取教学周和课表，首页 HTML 提取 |
| v87 | 单双周识别功能 |
| v90 | 多教学班课程合并 |
| v91 | 修复多教学班连续节次合并问题 |

---

**开发者**: 缪承浩

**版本**: 2.1
