# UtilityBox

> 可持续扩展的 Android 游戏工具箱
> 首期服务对象：中国大陆版《三角洲行动》

**当前状态：P00（需求分析与需求冻结）已完成，等待批准进入 P01。尚未生成任何应用代码。**

---

## 这是什么

UtilityBox 不是"包含三个功能的固定工具箱"，而是从第一天就按**可持续扩展的工具平台**设计的 Android 应用。

三个首期功能：

| 功能 | 说明 |
|---|---|
| 好友在线状态 | 好友列表与状态展示、排序 |
| 今日密码 | 每日密码、地图、日期、复制 |
| 物品物价 | 物品查询、当前价格、详情与真实历史趋势 |

未来可以添加新的工具模块、新的游戏、新的数据源，**不需要重写首页或推翻架构**。

---

## 关键约束（一句话版）

- **数据必须真实。** 不编造 API、密码、价格、好友状态。
- **架构必须可扩展。** 新增工具只改新工具 + 注册配置。
- **AI 不是运行依赖。** APK 独立安装运行。

详见 [`PROJECT_RULES.md`](PROJECT_RULES.md)。

---

## 技术栈

| 项 | 选择 |
|---|---|
| 语言 | Kotlin 2.2.10 |
| UI | Jetpack Compose（BOM 2026.02.01） |
| 构建 | AGP 9.2.1 / Gradle 9.4.1 |
| 架构 | MVVM + 分层 + 工具注册机制 |
| SDK | compileSdk 36.1 / minSdk 24 / targetSdk 36 |
| 依赖策略 | 零新依赖优先（`HttpURLConnection` + `org.json` + `SharedPreferences`） |

---

## 文档地图

| 文件 | 内容 |
|---|---|
| [`PROJECT_RULES.md`](PROJECT_RULES.md) | 项目不可变规则（最高约束） |
| [`ROADMAP.md`](ROADMAP.md) | P00～P11 开发计划 |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | 模块边界、依赖方向、设计决策 |
| [`TASKS.md`](TASKS.md) | 待办与问题 |
| [`CHANGELOG.md`](CHANGELOG.md) | 修改记录 |
| [`DATA_SOURCES.md`](DATA_SOURCES.md) | 数据源验证结果与限制 |
| [`TEST_REPORT.md`](TEST_REPORT.md) | 真实执行过的测试 |
| [`KNOWN_ISSUES.md`](KNOWN_ISSUES.md) | 已知问题 |
| [`docs/requirements.md`](docs/requirements.md) | **P00 需求规格说明书** |
| [`docs/architecture.md`](docs/architecture.md) | 模块边界与依赖关系详解 |
| [`docs/ui-design.md`](docs/ui-design.md) | 页面与导航结构 |
| [`docs/data-sources.md`](docs/data-sources.md) | 数据源风险清单 |
| [`docs/risks.md`](docs/risks.md) | 技术选型与风险清单 |

---

## 构建（本机环境）

```powershell
$env:JAVA_HOME = 'D:\CODE_Nskdfh_VS\Java Dev\Java\jdk-21'   # 必须！未持久化
Set-Location 'D:\CODE_Nskdfh_VS\android_Projects\UtilityBox'
.\gradlew.bat assembleDebug
```

或用辅助脚本：

```powershell
$S = 'D:\CODE_Nskdfh_VS\android_Projects\devtools.ps1'
& powershell -NoProfile -ExecutionPolicy Bypass -File $S emu
& powershell -NoProfile -ExecutionPolicy Bypass -File $S build D:\CODE_Nskdfh_VS\android_Projects\UtilityBox
```

---

## 数据来源声明

本应用使用社区第三方接口，**非腾讯官方接口**。
所有数据以《三角洲行动》官方为准，版权归属腾讯。本应用仅供技术学习与个人使用。
详见 [`DATA_SOURCES.md`](DATA_SOURCES.md) 与 [`LICENSES.md`](LICENSES.md)。
