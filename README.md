# 课程表 CHD (Course Schedule for CHD)

一款专为长安大学（CHD）学生设计的课程表 Android 应用。

## 功能特性

- **统一身份认证登录** - 通过学校 CAS 系统安全登录
- **自动获取课表** - 从教务系统自动获取并解析课程数据
- **周次切换** - 支持按周查看课程，快速定位当前周
- **本地存储** - 使用 Room 数据库缓存课程数据，离线可查看
- **课程冲突检测** - 自动检测课程时间冲突
- **多校区支持** - 支持不同校区的课程显示
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
| GeckoView             | Firefox 内核 WebView |
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

**开发者**: 缪承浩

**版本**: 1.0
