# Spotlight for Android

一个参考 iOS Spotlight 的安卓全局搜索：**桌面/锁屏向下滑即可呼出（需要 LSPosed 注入系统界面）**，
支持应用搜索、英汉词典、翻译、计算器与网页搜索。

> 没有 LSPosed 时下滑手势不可用，只能用悬浮把手、快捷磁贴或助理键打开。

- 包名：`com.spotlight.app`
- 版本：`1.0`
- 最低系统：Android 8.0（API 26），编译 SDK 35
- 技术栈：Kotlin + Jetpack Compose（Material 3），无第三方 UI 依赖

> 本项目是个人学习项目，不收集任何数据；词典/翻译走公开免费接口。
> 我比较懒，有BUG的话，大概也不会修（划掉）

---

## 功能

| 模块 | 说明 |
| --- | --- |
| 应用搜索 | 索引全部桌面应用；支持中文拼音、拼音首字母（`wx` → 微信）、子序列模糊匹配，命中字符高亮 |
| 离线词典 | 内置 **3021** 条英汉词条（音标、词性、中文释义、例句），本地 JSON，断网可用 |
| 在线词典 | 有道词典 jsonapi 提供中文释义（英汉），失败时回退 dictionaryapi.dev（英文释义） |
| 翻译 | MyMemory 中英互译，支持「仅离线 / 离线优先 / 在线优先」三种模式 |
| 计算器 | 支持 `+ - * / % ^`、括号、`sqrt/sin/cos/tan/log/ln/abs/exp`、`pi/e`、百分号 |
| 网页搜索 | 结果不足时跳浏览器，可选 Google / Bing / 百度 / DuckDuckGo |
| 最近使用 | 记录最近打开的应用（可关闭） |
| 复制 | 词典、翻译、计算器结果均可一键复制 |
| 词库扩展 | 设置里可导入第三方 JSON 词库并切换 |

界面为 iOS 风格：半透明遮罩 + 大圆角分组卡片，Android 12+ 自动开启系统级背景模糊。

## 呼出方式

### 1. LSPosed 下滑手势（桌面 + 锁屏）

1. 手机已 Root 并安装 **LSPosed**（Zygisk 版）。
2. 在 LSPosed 中启用本模块，作用域勾选 **系统界面（com.android.systemui）** 和你的 **桌面**
   （小米/红米为 `com.miui.home`；模块内置了 Pixel/MIUI/华为/OPPO/vivo/三星等常见桌面包名）。
3. 重启系统界面或重启手机。
4. 在桌面或锁屏，**除最顶部状态栏以外**的任意位置向下滑即可呼出。

防误触规则：起点在左右边缘 28dp、底部 56dp 内不接管；要求明显竖直（`dy > 2.2×dx`）、
≥64dp 且 900ms 内完成；系统界面只接管通知栏/状态栏根布局，侧滑返回边缘面板不会误触。

> 从最顶部状态栏下滑仍然是系统的通知栏/控制中心。

### 2. 桌面下拉 = 搜索（⚠️ 实测无效，仅供参考）

> **结论先说：不装 LSPosed 就没有可用的下滑呼出。**
> 本应用虽然注册了 `ACTION_SEARCH` / `ACTION_WEB_SEARCH` / `GLOBAL_SEARCH`，
> 但在小米澎湃 OS / MIUI 上实测：把「桌面下拉」设为「搜索」后，系统并不会把这次下拉
> 转给 Spotlight（仍会提示“没有原版搜索”或直接回到通知和控制中心）。
> 其它 ROM 是否生效尚未验证，**不要把这当成 LSPosed 的替代方案**。

理论上可以尝试：系统设置 → 桌面 → 桌面下拉 → 选择「搜索」，并确保设置里
**接管桌面下拉搜索** 开关是开着的。如果你的 ROM 恰好会把搜索动作交给第三方应用，
可能会拉起 Spotlight；否则请用上面的 LSPosed 手势。

### 3. 悬浮把手 / 快捷磁贴 / 助理键（无 LSPosed 时唯一可用的方式）

不使用 LSPosed 时，只有下面这些入口有效：

- 设置 → 呼出方式 → 悬浮把手（需「显示在其他应用上层」权限）
- 下拉通知栏 → 编辑快捷开关 → 添加「Spotlight」
- 系统设置 → 默认应用 → 助理 → 选择 Spotlight

它们不会覆盖桌面/锁屏的下滑手势，需要手动点按或经快捷开关触发。

## 设置项

- **外观**：主题（跟随系统/深色/浅色）、默认搜索引擎
- **搜索内容**：应用、词典、翻译、计算器、网页搜索、建议栏
- **离线 / 在线**：离线词库（内置/导入）、词典来源、翻译来源（各三选一，互斥）
- **呼出方式**：LSPosed 说明、悬浮把手、磁贴说明、接管桌面下拉搜索（实验性，多数 ROM 无效）
- **手势自检**：显示模块是否注入系统界面/桌面、最近触发记录、被过滤的窗口类型
- **其他**：最近使用、清除记录、恢复默认

## 构建

环境：JDK 17+、Android SDK（platform 35、build-tools 35.0.0）。

```bash
# Linux / macOS
./gradlew assembleDebug

# Windows
.\gradlew.bat assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

也可以直接用 Android Studio 打开本目录。

项目还提供了两个 PowerShell 脚本（Windows）：

```powershell
.\build.ps1            # 编译 debug APK
.\build.ps1 -Test      # 先跑单元测试再编译
.\build.ps1 -Install   # 编译后用 adb 安装
.\device-test.ps1      # 连上设备后：安装、启动、抓日志、截图
```

`local.properties` 需要本地自行创建（或由 Android Studio 生成）：

```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

## 项目结构

```
app/src/main/java/com/spotlight/app/
├── core/                  # 设置项、全局搜索入口开关
│   ├── Prefs.kt
│   └── SearchIntegration.kt
├── search/                # 数据层
│   ├── AppRepository.kt   # 应用索引、图标缓存、拼音、模糊匹配
│   ├── Calculator.kt      # 递归下降表达式求值
│   ├── Dictionary.kt      # 离线词典 + 有道/dictionaryapi.dev
│   ├── Translation.kt     # MyMemory 翻译 + 缓存 + 语种校验
│   ├── Http.kt
│   └── SearchModels.kt
├── ui/                    # Compose 界面
│   ├── SpotlightActivity.kt
│   ├── SpotlightViewModel.kt
│   ├── SpotlightScreen.kt
│   ├── Components.kt
│   └── theme/
├── settings/              # 设置页
├── trigger/               # 悬浮把手、快捷磁贴、手势诊断
└── xposed/
    ├── SpotlightXposed.kt # LSPosed 入口（系统界面 / 桌面）
    └── SwipeDownHook.kt   # 下滑手势（含防误触）
```

离线词典数据：`app/src/main/assets/offline_dictionary.json`
（由 `build_dict.py` 合并 `app/src/main/assets/dict_source.txt` 与 `data/ecdict-core.json` 生成）。

## 已知限制

- **下滑手势必须依赖 LSPosed**：作用域需包含系统界面与桌面；桌面不在内置名单时需手动勾选。
  没有 LSPosed / 没注入成功时，桌面和锁屏下滑都不会生效。
- 「桌面下拉 = 搜索」在小米澎湃 OS / MIUI 上实测无效，不能替代 LSPosed。
- 锁屏显示受 MIUI 限制：系统会打印 `Show when locked PermissionDenied`，
  需要在系统的应用权限里为 Spotlight 打开「锁屏显示」，否则界面会被锁屏盖住。
- MIUI/HyperOS 的顶边手势、控制中心等定制窗口行为可能与 AOSP 不同，模块已按实测适配，
  其它 ROM 若失效可在设置 → 手势自检查看被过滤的窗口类型。
- 在线词典/翻译依赖公开免费接口，可能限流或不可用，失败时自动降级。
- 翻译为机翻，仅适合快速理解。

## 许可

代码以 [MIT License](LICENSE) 发布。随项目分发的第三方资源（ECDICT 词库、Xposed API 等）
及其授权见 [NOTICE.md](NOTICE.md)。
