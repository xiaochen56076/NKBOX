# UtilityBox 架构设计（模块边界 · 依赖关系 · 扩展机制）

> 阶段：P00 ｜ 日期：2026-10-14 ｜ 状态：待批准
> 本文件是 P00 交付物第 6、7、8、11 项
> 最高约束见 `PROJECT_RULES.md`

---

## 1. 三条必须坚持的设计原则（设计稿第二节）

### 原则一：核心功能模块化

首期三个功能**相互独立**，共享公共基础设施，但**不相互依赖业务实现**。

- 好友模块不得直接调用今日密码模块的 ViewModel。
- 物价模块不得直接操作好友模块的缓存。
- 删除或重写任一功能，不影响其他功能。

### 原则二：公共能力统一管理

以下能力放公共层，**不在每个功能里单独实现一遍**：
网络请求 · 数据缓存 · 错误处理 · 日志 · 页面导航 · 主题系统 · UI 组件 · 动画规范 ·
数据更新时间 · 加载/空/错误状态 · 设置持久化 · 测试基础设施。

> 具体体现：三个模块**都**用同一个 `UiState` 与同一组 `UbLoading` / `UbEmpty` / `UbError` 组件，
> 而不是各写三套互不兼容的实现。

### 原则三：新增功能不需要重写首页

新增工具时，理想情况只需：① 创建模块 → ② 定义数据模型与业务逻辑 → ③ 实现页面 →
④ 注册工具信息 → ⑤ 配置导航入口 → ⑥ 添加测试。
**首页根据工具注册信息展示可用工具。** 不改首页、不重写导航、不动其他模块内部逻辑。

> 注：工具注册机制解决**应用内模块扩展**，**不等于**支持从网络动态下载执行第三方代码。
> 首期不引入该插件机制（安全风险）。

---

## 2. 分层与依赖方向

### 2.1 唯一允许的依赖方向

```
        ┌─────────────────────────────────────┐
        │  app/        组装层（导航、DI、入口） │
        └───────────────┬─────────────────────┘
                        │ 可以依赖一切
        ┌───────────────▼─────────────────────┐
        │  feature/    通用页面（首页/工具/设置）│
        └───────────────┬─────────────────────┘
                        │
        ┌───────────────▼─────────────────────┐
        │  game/<游戏>/ 游戏业务域              │
        │    friendstatus / dailycode / itemprice │
        │    （三者互不依赖）                    │
        └───────────────┬─────────────────────┘
                        │ 只依赖接口
        ┌───────────────▼─────────────────────┐
        │  domain/     契约层（模型 + 接口）     │
        │    model / tool / game / datasource   │
        └───────────────┬─────────────────────┘
                        │ 被依赖
        ┌───────────────▼─────────────────────┐
        │  core/       基础设施（不含业务）      │
        │  common/network/cache/design/         │
        │  navigation/tool/testing              │
        └─────────────────────────────────────┘
```

### 2.2 依赖铁律

| 规则 | 说明 | 违反后果 |
|---|---|---|
| R1 | `core/` **不得** import `game.*` 或 `feature.*` | 公共层被游戏业务污染 |
| R2 | `game/deltaforce/*` 三个功能**互不 import** | 无法独立删除/重写 |
| R3 | `feature/` **不得**直接依赖 `game/*/data/*` 实现类，只能依赖 `domain/` 接口 | 数据源无法替换 |
| R4 | 任何层**不得**直接 `new` HTTP 客户端，必须经 `core/network` | 网络行为无法统一治理 |
| R5 | `domain/` **不得**依赖 Android 框架（除必要注解） | 无法纯 JVM 单测 |
| R6 | 跨模块通信只能经 `domain/` 接口或工具注册表 | 隐式耦合 |

> **可执行的验证方式**（P03 后加入）：用 grep/脚本检查 `core/` 下是否出现 `import com.utilitybox.game.` 或 `import com.utilitybox.feature.`。

### 2.3 为什么首期只用一个 Gradle 模块

设计稿明确要求：

> 「这是一份逻辑结构示例，最终应根据真实代码规模决定哪些目录需要独立成为 Gradle 子工程。」
> 「**不建议一开始就拆出几十个独立模块。** 先保证边界清楚、依赖方向合理，再随着规模增长决定是否进一步拆分。」

**P00 决策**：

| 项 | 决策 | 理由 |
|---|---|---|
| Gradle 模块数 | **1 个 `:app`** | 快速迭代；避免早期配置成本与编译开销 |
| 边界保证 | **包结构 + 依赖铁律 R1–R6** | 用命名空间表达边界，用规则约束方向 |
| 未来拆分 | 预留 | 包结构已按未来模块边界设计，拆分时**只动 Gradle 配置，不动业务代码** |
| 拆分触发条件 | 满足任一即考虑拆分 | ① 单模块编译 > 60s；② 需要独立复用某模块；③ 团队多人并行开发冲突频繁 |

**包名即未来模块名**，例如 `com.utilitybox.game.deltaforce.dailycode` → 未来 `:game:deltaforce:dailycode`。

---

## 3. 项目目录结构

```
UtilityBox/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/                     # 静态资源（地图名映射等）
│       │   ├── java/com/utilitybox/
│       │   │   ├── MainActivity.kt
│       │   │   ├── UbApplication.kt
│       │   │   ├── app/                    # 组装层
│       │   │   │   ├── navigation/         # 根导航图、路由常量
│       │   │   │   └── di/                 # 依赖组装（手写容器，零新依赖）
│       │   │   ├── core/                   # 基础设施（无业务）
│       │   │   │   ├── common/             # Result/AppError/日志/时间
│       │   │   │   ├── network/            # HttpClient 抽象 + HttpURLConnection 实现
│       │   │   │   ├── cache/              # 缓存信封 + SharedPreferences 实现
│       │   │   │   ├── design/             # 设计系统：色彩/字体/圆角/间距/组件/动画
│       │   │   │   ├── navigation/         # 导航契约（路由接口）
│       │   │   │   ├── tool/               # ★ 工具注册机制
│       │   │   │   └── testing/            # 测试夹具、Fake 数据源
│       │   │   ├── domain/                 # 契约层（纯 Kotlin）
│       │   │   │   ├── model/              # Friend / DailyCode / ItemSummary ...
│       │   │   │   ├── tool/               # ToolDescriptor / ToolRegistry 接口
│       │   │   │   ├── game/               # GameDescriptor
│       │   │   │   └── datasource/         # 数据源接口（可替换点）
│       │   │   ├── game/                   # 游戏业务域
│       │   │   │   └── deltaforce/
│       │   │   │       ├── common/         # 该游戏共用的 API 客户端/映射/常量
│       │   │   │       ├── friendstatus/   # 好友状态（ui/domain/data/di）
│       │   │   │       ├── dailycode/      # 今日密码
│       │   │   │       └── itemprice/      # 物品物价
│       │   │   └── feature/                # 通用页面
│       │   │       ├── home/               # 首页
│       │   │       ├── tools/              # 工具列表
│       │   │       └── settings/           # 设置中心
│       │   └── res/
│       ├── test/java/com/utilitybox/       # 单元测试（纯 JVM）
│       └── androidTest/java/com/utilitybox/# UI / 集成测试
├── docs/
│   ├── requirements.md      # P00 需求规格说明书
│   ├── architecture.md      # 本文件对应 docs 版本
│   ├── ui-design.md         # 页面与导航结构
│   ├── data-sources.md      # 数据源说明与限制
│   └── risks.md             # 技术选型与风险清单
├── gradle/
│   ├── libs.versions.toml   # 版本目录
│   └── wrapper/
├── PROJECT_RULES.md
├── ROADMAP.md
├── ARCHITECTURE.md          # 指向 docs/architecture.md 的摘要
├── TASKS.md
├── CHANGELOG.md
├── DATA_SOURCES.md
├── TEST_REPORT.md
├── KNOWN_ISSUES.md
├── LICENSES.md
├── README.md
└── (build.gradle.kts / settings.gradle.kts / gradle.properties / local.properties)
```

---

## 4. 工具注册机制设计（P00 交付第 7 项）

### 4.1 设计目标

| 目标 | 验收方式 |
|---|---|
| 新增工具不改首页 | 首页代码中不出现任何具体工具名 |
| 支持一个游戏的多个工具 | 注册表按 `gameId` 过滤 |
| 支持未来多游戏 | `GameRegistry` 管理游戏列表 |
| 支持工具启用/停用 | 注册表持有 `status`，设置页可改 |
| 不引入外部代码加载 | 注册完全在编译期完成，无反射、无网络下载 |

### 4.2 契约

```kotlin
// domain/tool/ToolDescriptor.kt —— 已见 docs/requirements.md §5.2

// domain/tool/ToolRegistry.kt
interface ToolRegistry {
    fun allGames(): List<GameDescriptor>
    fun activeGame(): GameDescriptor
    fun toolsOf(gameId: String): List<ToolDescriptor>
    fun enabledToolsOf(gameId: String): List<ToolDescriptor>
    fun byId(toolId: String): ToolDescriptor?
    fun setEnabled(toolId: String, enabled: Boolean)
    fun registerProvider(provider: ToolProvider)   // 各功能模块的接入点
}

// domain/tool/ToolProvider.kt —— 每个功能模块实现一个
interface ToolProvider {
    /** 该模块对外暴露的工具描述（可多于一个） */
    fun descriptors(): List<ToolDescriptor>
    /** 该模块自己的导航图构建器（模块自治，不污染根导航） */
    fun registerGraph(builder: NavGraphBuilder, navController: NavController)
}
```

### 4.3 组装方式（零新依赖，手写 DI）

```kotlin
// app/di/AppContainer.kt
class AppContainer(context: Context) {
    private val http = UrlConnectionHttpClient(...)          // core/network
    private val cache = PrefsCache(context)                  // core/cache

    // 每个游戏模块提供一个聚合入口
    private val deltaForce = DeltaForceModule(http, cache)

    val toolRegistry: ToolRegistry = DefaultToolRegistry(
        games = listOf(deltaForce.gameDescriptor()),
        providers = listOf(
            deltaForce.dailyCodeProvider(),      // 今日密码
            deltaForce.itemPriceProvider(),      // 物品物价
            deltaForce.friendStatusProvider(),   // 好友状态（可能为 UNAVAILABLE）
        ),
    )
}
```

### 4.4 新增一个工具的完整清单（必须只有这些改动）

> **✅ 已实测验证**（2026-10-14，P04）。用这个方法真的加了一个工具（`deltaforce.pricerange`
> 价格区间统计），并通过 SHA256 哈希比对证明**公共层 10 个文件 0 改动**。

| 步骤 | 动作 | 是否触碰已有代码 |
|---|---|---|
| 1 | 新建包 `game/<game>/<newtool>/` | 否（新增文件） |
| 2 | 实现 Composable 页面（只写**内容**，不写标题栏） | 否（新增文件） |
| 3 | 实现 `ToolProvider`（描述 + 页面 + **路由**） | 否（新增文件） |
| 4 | 在 `game/<game>/<Game>Routes.kt` 加一个路由常量 | ✅ 1 行 |
| 5 | 在 `<Game>Module.providers` 加一项 | ✅ 1 行 |
| 6 | 添加单元测试 | 否（新增文件） |

> **`MainActivity`（根导航）、`HomeScreen`、`UbToolRegistry`、`UbNavigator`、
> `UbRoutes`、`UbIcons`、`UbBottomBar`、`AppContainer`、`ToolProvider`、`UbToolPage`：
> 实测 0 改动。** 这就是原则三的验收标准。

#### 为什么路由属于游戏模块而不是公共的 `core/navigation/UbRoutes.kt`

这是实测中发现的**关键设计点**。若把每个工具的路由都写进公共层的 `UbRoutes`，
每新增一个工具就要改一次**共享文件** —— "新增工具不改公共代码"当场就不成立了。

因此约定：**应用级路由**（根标签页、设置子页）留在 `core/navigation/UbRoutes.kt`；
**工具路由**归各自游戏模块所有（`game/<game>/<Game>Routes.kt`）。

#### 路由分发靠注册表，不靠 `when` 分支

根导航的分发逻辑是：

```kotlin
val provider = UbToolRegistry.providerByRoute(route)
if (provider != null) {
    UbToolHost(provider, nav, onBack)   // 工具页：外壳统一，内容由 provider 提供
} else {
    when (route) { /* 仅根标签页与二级页面 */ }
}
```

`when` 分支里**没有任何具体工具**，因此新增工具不会碰到它。

#### 启动自检

`UbToolRegistry.verifyIntegrity()` 会校验：工具 id 唯一、路由唯一且非空、
每个工具的 `gameId` 都已在容器中注册。配置写错会在启动时**立刻暴露**，
而不是等用户点进去才发现空白页。

### 4.5 为什么不用反射/SPI/动态加载

| 方案 | 否决理由 |
|---|---|
| 反射扫描注解 | 需要额外依赖或手写扫描，R8/混淆下脆弱，收益低 |
| ServiceLoader (SPI) | Android 上打包与混淆复杂，收益低 |
| 动态下载 dex/插件 | **设计稿明确不做**，安全风险高 |
| **显式注册列表** | ✅ 编译期安全、可追踪、零依赖、易测试 |

---

## 5. 未来多游戏扩展方案（P00 交付第 8 项）

### 5.1 隔离原则

> **不要把《三角洲行动》的业务规则写进公共基础层。**

- 「今日密码」是三角洲特有的业务功能 ⇒ 归属 `game/deltaforce/dailycode`，
  **不是**所有游戏都必须实现的公共接口。
- 公共层（`core/`）只定义**真正通用**的能力：网络、缓存、主题、组件、错误、导航、注册机制。
- `domain/` 只定义**通用契约**：`GameDescriptor`、`ToolDescriptor`、`ToolRegistry`、
  以及各数据源接口（接口本身通用，实现属具体游戏）。

### 5.2 目标结构

```
UtilityBox
│
├── 游戏选择与管理（feature/home 的游戏选择器 + GameRegistry）
│
├── 三角洲行动（game/deltaforce）
│   ├── 好友状态      friendstatus
│   ├── 今日密码      dailycode
│   ├── 物品物价      itemprice
│   └── 后续扩展工具
│
├── 其他游戏 A（未来）  game/<gamea>
│   ├── 工具模块
│   └── 数据适配器
│
└── 其他游戏 B（未来）  game/<gameb>
    ├── 工具模块
    └── 数据适配器
```

### 5.3 数据隔离

| 数据 | 隔离方式 | 键格式 |
|---|---|---|
| 缓存 | 按游戏命名空间 | `cache:<gameId>:<dataType>:<key>` |
| 设置 | 全局设置与游戏设置分开 | `settings:global:*` / `settings:<gameId>:*` |
| 工具启用状态 | 按工具 ID（已含 gameId 前缀） | `tool.<gameId>.<tool>` |

### 5.4 新增一个游戏的清单

| 步骤 | 动作 | 是否触碰已有代码 |
|---|---|---|
| 1 | 新建包 `game/<newgame>/` | 否 |
| 2 | 实现 `GameDescriptor` | 否 |
| 3 | 实现该游戏的 `ToolProvider`（可复用 `core/` 全部能力） | 否 |
| 4 | 在 `AppContainer` 注册 | ✅ 1 行 |
| 5 | 添加测试 | 否 |

> **三角洲的代码：0 改动。** 首页自动出现游戏选择项。

### 5.5 首期的克制

设计稿明确：「不要为了预留未来功能而提前开发一大堆暂时用不到的功能，但一定要提前确定正确的模块边界。」

因此首期：
- ✅ 首页**有**游戏选择器结构，但只有一个游戏时表现为标题而非可切换列表。
- ✅ `ToolDescriptor.gameId` 字段就位。
- ✅ 缓存键带 `gameId` 命名空间。
- ❌ **不**实现游戏下载、游戏配置远程拉取、多游戏数据合并等无实际需求的功能。

---

## 6. 四层扩展能力对应实现

| 设计稿层级 | 扩展内容 | 本架构对应机制 | 首期状态 |
|---|---|---|---|
| 第一层 工具扩展 | 战绩/地图/资讯/计算器/日历/收藏 | `ToolProvider` + `ToolRegistry` | 机制就位，工具未实现 |
| 第二层 游戏扩展 | 接入其他游戏 | `GameRegistry` + `game/<id>/` 包 + 缓存命名空间 | 机制就位，仅 1 款游戏 |
| 第三层 数据源扩展 | 同一功能换/增数据源 | `domain/datasource` 接口 + 适配器 | 机制就位，1 个实现 |
| 第四层 视觉交互扩展 | 统一色彩/字体/圆角/间距/动画/玻璃 | `core/design` 设计系统 | 待 P02 落地 |

---

## 7. 关键技术决策记录

| # | 决策 | 备选 | 理由 |
|---|---|---|---|
| D1 | 单 Gradle 模块 + 包边界 | 多模块 | 设计稿明确不建议早期过度拆分；包名即未来模块名 |
| D2 | 手写 DI 容器 | Hilt / Koin | 零新依赖优先；首期依赖图简单，手写可读性更好 |
| D3 | `HttpURLConnection` + `org.json` | OkHttp / Retrofit / Gson | 本机已验证可行；`core/network` 已抽象，未来可换实现 |
| D4 | `SharedPreferences` + JSON | Room / DataStore | 首期数据结构简单（缓存 + 设置）；接口已抽象 |
| D5 | 显式注册列表 | 反射 / SPI / 动态加载 | 编译期安全、零依赖；动态加载被设计稿否决 |
| D6 | Compose Canvas 手绘图标 | `material-icons-core` | ⚠️ **前提已修正**：BOM 2026.02.01 **仍管理** `material-icons-core:1.7.8`（实测 240 条），官方只是把它移出 material3 的传递依赖。仍选手绘的理由是**零依赖优先 + 视觉可控**（"Apple 风格"需要定制图标），而非"库不存在" |
| D7 | 数据模型内建 `Fresh<T>` / `AppError` | 直接用裸数据 + 异常 | 需求要求"区分陈旧与缺失""不得用 0 冒充无数据" |
| D8 | 导航方案 | Navigation Compose / 自研 | ⚠️ **待 P01 验证**（见 `docs/risks.md`）。若 Navigation Compose 与版本基线不兼容，则用 `feature/home` 内自研导航图 |

---

## 8. 测试基础设施

| 层级 | 内容 | 工具 |
|---|---|---|
| 单元测试 | 纯函数：状态排序、四态判定、缓存新鲜度、错误映射、JSON 解析 | JUnit4 |
| Fake 数据源 | `core/testing` 提供 `FakeDailyCodeDataSource` 等，可注入各种状态 | 手写 |
| UI 测试 | 首页工具渲染、点击导航、空/错误态 | compose-ui-test |
| 集成测试 | 注册表 → 导航 → 页面 全链路 | compose-ui-test |

> **约束**：`domain/` 与业务逻辑（排序、判定）必须能在**无设备**的情况下用单元测试验证。
> 这是"不得跳过阶段"和能力核验的基础。

---

## 9. 本文件与 `ARCHITECTURE.md` 的关系

`ARCHITECTURE.md`（根目录）是**摘要 + 决策索引**，本文件是**完整设计**。
发生冲突时以本文件为准，并同步更新根目录摘要。
