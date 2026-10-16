# UtilityBox 需求规格说明书

> 阶段：**P00 需求分析与需求冻结**
> 日期：2026-10-14
> 状态：**待用户批准**
> 依据：设计稿《设计稿ubox.docx》第一、二、三、七节 + `PROJECT_RULES.md`

---

## 1. 项目概述

### 1.1 一句话定义

UtilityBox 是一款**可持续扩展的 Android 游戏工具箱**，首期服务中国大陆版《三角洲行动》，提供好友在线状态、今日密码、物品物价三大功能，并通过统一的工具注册机制与数据源适配层，保证未来新增工具、新增游戏、替换数据源时**不需要重写首页或推翻架构**。

### 1.2 要解决的问题

| 问题 | 现状痛点 | UtilityBox 的做法 |
|---|---|---|
| 信息分散 | 每日密码、物价、好友状态散落在群聊与网页 | 收敛到一个 App |
| 工具易碎 | 数据源一换，整页重写 | 数据源适配器隔离 |
| 无法扩展 | 加功能就得改首页 | 工具注册机制 |
| 数据存疑 | 分不清真实数据与编造数据 | 强制数据来源与更新时间标注 |

### 1.3 明确的非目标（首期不做）

好友搜索、社区、聊天、交易系统、商城、广告位。
动态下载并执行第三方代码的插件系统（安全风险，首期不做）。

---

## 2. 首期功能清单

### 2.1 功能总表

| ID | 功能 | 优先级 | 数据依赖 | 可交付性 |
|---|---|---|---|---|
| F-01 | 工具箱首页 | P0 | 无 | ✅ 可交付 |
| F-02 | 工具列表与工具注册机制 | P0 | 无 | ✅ 可交付 |
| F-03 | 今日密码 | P0 | 社区接口 | ✅ 数据源已实测 |
| F-04 | 物品物价（浏览/查询/当前价/详情） | P0 | 社区接口 | ✅ 数据源已实测 |
| F-05 | 物品历史趋势（基于真实记录） | P1 | 社区接口 | ✅ 数据源已实测 |
| F-06 | 好友在线状态 | P0 | 社区接口 | ⚠️ **数据源受限，见 §2.2** |
| F-07 | 设置中心 | P0 | 无 | ✅ 可交付 |
| F-08 | 本地缓存与数据更新时间 | P0 | 无 | ✅ 可交付 |
| F-09 | 网络请求与统一错误处理 | P0 | 无 | ✅ 可交付 |
| F-10 | 统一设计系统与动画 | P0 | 无 | ✅ 可交付 |
| F-11 | Liquid Glass 视觉效果（可降级） | P1 | 无 | ⚠️ 待 P01 验证 |
| F-12 | 主题（浅色/深色/跟随系统） | P0 | 无 | ✅ 可交付 |
| F-13 | 单元测试 / UI 测试 / 集成测试 | P0 | 无 | ✅ 可交付 |
| F-14 | 数据源状态页 | P1 | 无 | ✅ 可交付 |

### 2.2 ⚠️ F-06 好友在线状态：数据源受限声明

**这是 P00 阶段必须上报的最重要问题。** 经实测核验（证据见 `DATA_SOURCES.md`）：

| 设计稿要求 | 数据源现实 | 差距 |
|---|---|---|
| 好友列表 | ❌ 无"获取好友列表"端点 | **缺失** |
| 游戏中状态 | ❌ 无任何端点提供 | **缺失** |
| 在线 / 离线 | ⚠️ 仅 `gamesafe/online` 提供**自己账号**的在线布尔值，且需**微信**安全中心 token | 不能覆盖好友 |
| 未知状态 | — | 仅在"无数据源"时成立 |

**因此本阶段的诚实结论是**：按设计稿要求实现"好友列表 + 四态排序"目前**没有可用数据源**。

**P00 给出的三个可选处置方案（需用户裁决，见 §8 待决问题 Q1）**：

| 方案 | 做法 | 优点 | 缺点 |
|---|---|---|---|
| **A. 降级实现** | 保留好友状态模块与四态模型，但数据源标注"不可用"，页面明确显示"当前无可用数据源" | 架构完整、不编造数据、未来数据源就绪即可点亮 | 首期该功能对用户不可用 |
| **B. 换数据源** | P01 期间继续寻找可行数据源（含用户自行提供） | 可能真正做成 | 不保证能找到；可能涉及合规风险 |
| **C. 先做框架，功能延后** | 首期只交付模块骨架 + 状态模型 + 排序逻辑（可单测），UI 显示"功能未启用" | 逻辑可验证、边界清晰 | 与 A 类似但更保守 |

> **无论选哪个方案，都不得编造好友状态数据。** 这是 `PROJECT_RULES.md` 第二节红线的第 1 条。

### 2.3 功能详述

#### F-01 工具箱首页
- 展示当前游戏（首期固定《三角洲行动》）与已启用工具入口。
- 工具内容**完全由工具注册表驱动**，首页不硬编码任何工具。
- 展示数据更新时间与数据源健康状态摘要。

#### F-02 工具注册机制
- 每个工具提供一份 `ToolDescriptor`（工具 ID、名称、描述、分类、图标、导航路由、启用状态、数据源需求）。
- 首页与工具列表从注册表读取，**新增工具不需要改首页代码**。
- 提供工具启用/停用能力（设置页）。

#### F-03 今日密码
- 展示日期、地图、密码，支持一键复制。
- **必须区分四种状态**：`当天有效` / `过期缓存` / `暂无数据` / `请求失败`。
- **不得用昨天的密码冒充今日密码**（`PROJECT_RULES.md` §3.5）。
- 展示数据来源与更新时间。

#### F-04 / F-05 物品物价与历史趋势
- 按名称搜索物品；展示当前均价、更新时间、来源。
- 物品详情页展示真实价格历史（折线图）。
- 历史数据点缺失时**留空显示**，不做插值伪造、不做美化平滑。
- 无成交数据（`hasValidPriceData: false`）必须明确标注"暂无成交数据"，而非显示 0 当真实价格。

#### F-07 设置中心
主题 · 自动刷新 · 减少动态效果 · 缓存管理 · 数据源状态 · 工具启用状态 · 关于与许可。

#### F-08 缓存与更新时间
- 缓存必须带 `fetchedAt` 时间戳与 `source` 来源标识。
- UI 区分"新鲜数据"与"陈旧数据"，陈旧数据显著标注抓取时间。

#### F-09 网络与错误处理
统一错误分类：`无网络` / `超时` / `服务端错误(5xx)` / `鉴权失败(401/403)` / `数据格式错误` / `业务错误(code≠0)` / `未知错误`。
每类给出可读文案与重试入口。

#### F-13 测试
- 单元测试：状态排序、四态判定、缓存新鲜度、错误映射、JSON 解析。
- UI 测试：首页工具渲染、工具点击导航、空/错误态展示。
- 集成测试：注册表 → 导航 → 页面 的完整链路。

---

## 3. 用户角色与场景

**唯一角色：玩家本人**（无多用户、无登录体系作为前置）。

> 注：部分个人数据接口需要 `frameworkToken`（登录凭据），但**首期三个核心功能中，今日密码与物品物价均不需要登录**。好友状态若采用方案 A/C，也不需要登录。

### 3.1 核心用户故事

| 编号 | 用户故事 | 验收要点 |
|---|---|---|
| US-01 | 我打开 App，能看到当前游戏下所有可用工具 | 首页由注册表驱动，无硬编码 |
| US-02 | 我想查今天的密码，一眼看到各地图密码并能复制 | 四态区分正确，复制成功有反馈 |
| US-03 | 我想查某个物品现在值多少钱 | 显示价格 + 来源 + 更新时间 |
| US-04 | 我想看某物品最近几天的价格走势 | 折线来自真实记录，缺失点留空 |
| US-05 | 我想知道哪些好友在线 | ⚠️ 受数据源限制，见 §2.2 |
| US-06 | 我想切换深色模式 | 三种主题模式生效且持久化 |
| US-07 | 我想在没网时还能看上次的数据 | 显示缓存 + 明确标注抓取时间 |
| US-08 | 我想关掉动态效果让手机更流畅 | 设置生效，玻璃效果降级 |
| US-09 | 我想知道数据是从哪来的、可靠吗 | 数据来源与状态页可查 |

---

## 4. 用户操作流程

### 4.1 冷启动主流程

```
启动 → 读取主题设置 → 渲染首页骨架
  → 读取工具注册表 → 渲染工具网格
  → 读取缓存（立即显示，标注时间）
  → 后台并发刷新各工具数据
  → 成功：替换为新鲜数据
  → 失败：保留缓存 + 顶部错误条 + 重试入口
```

### 4.2 查今日密码

```
首页 → 点「今日密码」 → 加载态
  ├─ 当天有效   → 地图卡片 + 密码大字 + 复制按钮 + 更新时间
  ├─ 过期缓存   → 卡片置灰 + 「这是 X 月 X 日的数据，今日尚未更新」
  ├─ 暂无数据   → 「今日密码尚未发布」
  └─ 请求失败   → 错误类型 + 重试按钮
点复制 → 写入剪贴板 → Toast/Snackbar「已复制」
```

### 4.3 查物品物价

```
首页 → 点「物品物价」 → 物品列表（默认热门/最近）
  ├─ 搜索框输入名称 → 防抖 300ms → 搜索结果列表
  └─ 点某个物品 → 物品详情页
       ├─ 名称 / 图标 / 等级 / 分类
       ├─ 当前均价 + 涨跌幅 + 更新时间 + 来源
       └─ 历史趋势折线（真实记录；无数据显示空态）
```

### 4.4 查好友状态（受 §2.2 约束）

```
首页 → 点「好友状态」
  ├─ 数据源可用 → 列表按 游戏中→在线→离线→未知 分组排序
  │    └─ 未知分组的说明文案：「状态未知（数据源未提供该好友状态）」
  └─ 数据源不可用 → 明确说明「当前无可用数据源」+ 不显示任何伪造条目
```

### 4.5 设置与故障流程

```
首页 → 「设置」
  ├─ 主题：浅色 / 深色 / 跟随系统  → 立即生效 + 持久化
  ├─ 减少动态效果：开 / 关          → 关闭玻璃与转场动画
  ├─ 自动刷新：间隔选择 / 关闭
  ├─ 缓存管理：查看占用 / 清除      → 清除后回落到空态
  ├─ 数据源状态：各功能数据源 已验证/受限/不可用 + 最后成功时间
  └─ 工具管理：启用 / 停用各工具   → 首页即时反映
```

---

## 5. 数据模型设计

> 原则：**模型必须能表达"未知"和"陈旧"，不允许用默认值掩盖数据缺失。**

### 5.1 通用基础模型

```kotlin
// 数据来源标识 —— 每个数据都必须知道自己是哪来的
data class DataSourceRef(
    val id: String,            // 如 "shallow-delta-v1"
    val displayName: String,   // 如 "社区接口（浅巷）"
    val verified: SourceState, // VERIFIED / PENDING / RESTRICTED / UNAVAILABLE
)

enum class SourceState { VERIFIED, PENDING, RESTRICTED, UNAVAILABLE }

// 带时间戳的数据封装 —— 让 UI 能区分新鲜与陈旧
data class Fresh<T>(
    val value: T,
    val fetchedAt: Instant,
    val source: DataSourceRef,
    val isMock: Boolean = false,   // 见 PROJECT_RULES §8
)

// 统一状态模型（所有模块复用，不各写一套）
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data object Empty : UiState<Nothing>                    // 请求成功但确实没有数据
    data class Failed(val error: AppError) : UiState<Nothing>
}

// 统一错误模型
sealed interface AppError {
    data object NoNetwork : AppError
    data object Timeout : AppError
    data class Server(val code: Int) : AppError
    data object Unauthorized : AppError
    data object Malformed : AppError                       // JSON 结构不符
    data class Business(val code: Int, val message: String) : AppError  // code != 0
    data class Unknown(val message: String) : AppError
}
```

### 5.2 工具与游戏（扩展核心）

```kotlin
enum class ToolCategory { GAME_DATA, PRICE, UTILITY, PERSONAL }

enum class ToolStatus { ENABLED, DISABLED, UNAVAILABLE }

data class ToolDescriptor(
    val id: String,                 // 全局唯一，稳定，如 "deltaforce.dailycode"
    val gameId: String,             // 归属游戏，如 "deltaforce"
    val name: String,               // 显示名
    val description: String,
    val category: ToolCategory,
    val iconKey: String,            // 手绘图标标识（不用 material-icons）
    val route: String,              // 导航路由
    val status: ToolStatus,
    val requiresLogin: Boolean = false,
    val requiredDataSources: List<String> = emptyList(),
    val requiresLiquidGlass: Boolean = false,
)

data class GameDescriptor(
    val id: String,                 // "deltaforce"
    val name: String,               // "三角洲行动"
    val region: String,             // "CN"
    val enabled: Boolean = true,
    val tools: List<ToolDescriptor> = emptyList(),
)
```

### 5.3 今日密码

```kotlin
data class DailyCode(
    val mapId: String,
    val mapName: String,
    val code: String,               // 密码本体；空字符串表示数据源未给
    val validDate: LocalDate?,      // ★ 该密码对应的有效日期；null = 数据源未提供
)

sealed interface DailyCodeState {
    /** 当天有效数据 */
    data class Today(val codes: List<DailyCode>, val fetchedAt: Instant, val source: DataSourceRef)
    /** 过期缓存：必须携带原始日期，UI 必须显著标注 */
    data class Stale(val codes: List<DailyCode>, val validDate: LocalDate, val fetchedAt: Instant, val source: DataSourceRef)
    /** 请求成功但数据源没给今天的密码 */
    data object NoDataForToday
    /** 请求失败 */
    data class Failed(val error: AppError)
}

/** 判定规则（可单测，纯函数） */
fun classify(
    codes: List<DailyCode>?,
    fetchedAt: Instant,
    today: LocalDate,
    error: AppError?,
): DailyCodeState
```

> ⚠️ **P01 已实测确认（2026-10-14）**：`/api/v1/df/tools/dailykeyword` 的真实响应为
> `{"code":0,"message":"成功","data":{"list":[{"mapID":1,"mapName":"零号大坝","secret":"0144"}, ...]}}`，
> 共 6 张地图（零号大坝/长弓溪谷/巴克什/航天基地/潮汐监狱/AZ3），
> **字段仅 `mapID` / `mapName` / `secret`，确认没有任何日期、时间戳或有效期字段。**
>
> 因此「当天有效 / 过期缓存」**无法由接口判定**，只能由本机抓取时间推断。
> **本项目的处置（必须如实告知用户）**：
> - 记录每次成功抓取的**本机时间** `fetchedAt`。
> - 以「本机日期 == 抓取日期」判定为 `Today`；否则为 `Stale`，并显示**抓取日期**。
> - 页面必须写明判定依据，例如「按本机抓取时间判定（接口未提供生效日期）」。
> - **绝不允许**把昨日的密码当作今日的展示，也**绝不允许**编造一个有效日期。
>
> 这直接对应红线 §3.5，也是 `DailyCodeState` 设计成四态密封接口的原因。

### 5.4 物品物价

```kotlin
data class ItemSummary(
    val objectId: String,
    val name: String,
    val shortName: String?,
    val grade: Int?,                // 品质等级
    val secondClass: String?,       // 二级分类
    val avgPrice: Long?,            // null = 无成交数据（不是 0）
    val desc: String?,
)

data class ItemPrice(
    val objectId: String,
    val avgPrice: Long,
    val fetchedAt: Instant,
    val source: DataSourceRef,
)

data class PricePoint(
    val date: LocalDate,
    val avgPrice: Long?,
    val sampleCount: Int,           // 该日样本数；0 表示无成交
)

data class PriceHistory(
    val objectId: String,
    val granularity: String,        // "day" 等
    val points: List<PricePoint>,   // 真实记录；缺失点 avgPrice = null，图表留空
    val source: DataSourceRef,
) {
    /** 仅基于真实点计算，缺失点不参与 */
    val trend: PriceTrend get() = /* 由首尾有效点计算，无有效点时 UNKNOWN */
}

enum class PriceTrend { UP, DOWN, FLAT, UNKNOWN }

data class ItemDetail(
    val summary: ItemSummary,
    val currentPrice: ItemPrice?,
    val hasValidPriceData: Boolean, // false ⇒ UI 显示"暂无成交数据"
)
```

### 5.5 好友状态

```kotlin
enum class FriendStatus {
    IN_GAME,        // 游戏中 —— 仅当数据源可靠提供时才可出现
    ONLINE,
    OFFLINE,
    UNKNOWN;        // 未知 —— 绝不自动降级为 OFFLINE

    /** 排序权重：游戏中 → 在线 → 离线 → 未知 */
    val sortOrder: Int get() = ordinal
}

data class Friend(
    val id: String,                 // openid 等稳定标识
    val displayName: String?,       // null = 未取到昵称，UI 显示脱敏 ID 尾号
    val avatarUrl: String?,
    val status: FriendStatus,
    val statusSource: DataSourceRef,  // 状态来自哪个数据源
    val lastSeenAt: Instant?,
    val statusKnownAt: Instant?,    // 状态观测时间；null = 从未观测到
)

/** 分组排序（纯函数，可单测） */
fun groupAndSort(friends: List<Friend>): List<FriendGroup>
```

> **约束**：若 `statusSource.verified == UNAVAILABLE`，则不得产生任何 `Friend` 实例。
> 页面显示"当前无可用数据源"。**不得为了填满列表而编造好友。**

### 5.6 设置

```kotlin
enum class ThemeMode { LIGHT, DARK, SYSTEM }

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val autoRefreshEnabled: Boolean = true,
    val autoRefreshIntervalMinutes: Int = 30,
    val reduceMotion: Boolean = false,
    val liquidGlassEnabled: Boolean = true,          // 自动降级后可为 false
    val enabledToolIds: Set<String> = emptySet(),    // 空集 = 全部启用
)
```

### 5.7 缓存信封（持久化格式）

```kotlin
data class CacheEnvelope<T>(
    val schemaVersion: Int,   // 结构变更时递增，用于安全丢弃旧缓存
    val fetchedAt: Long,      // epoch millis
    val sourceId: String,
    val isMock: Boolean,
    val payload: T,
)
```

---

## 6. 页面与导航结构

### 6.1 导航图

```
UtilityBox (单一 Activity + Compose Navigation)
│
├── 根脚手架 RootScaffold
│   └── 底部悬浮导航（首页 / 设置）       ← Liquid Glass 候选位置
│
├── [Tab] 首页 graph:home
│   ├── HomeScreen
│   │   ├── 游戏选择器（首期仅三角洲，但结构就位）
│   │   ├── 工具网格（由 ToolRegistry 驱动）
│   │   └── 数据源状态摘要条
│   └── ToolsScreen（全部工具列表，按分类分组）
│
├── [Tab] 设置 graph:settings
│   ├── SettingsScreen
│   ├── DataSourceStatusScreen（数据源状态明细）
│   └── AboutScreen（版本、开源许可、免责声明）
│
└── [游戏功能] graph:deltaforce
    ├── DailyCodeScreen        今日密码
    ├── ItemPriceScreen        物品列表 + 搜索
    ├── ItemDetailScreen       物品详情 + 历史趋势
    └── FriendStatusScreen     好友状态（受数据源限制）
```

### 6.2 路由表

| 路由 | 页面 | 参数 | 来源工具 ID |
|---|---|---|---|
| `home` | 首页 | — | — |
| `tools` | 工具列表 | — | — |
| `settings` | 设置 | — | — |
| `settings/datasources` | 数据源状态 | — | — |
| `settings/about` | 关于 | — | — |
| `df/dailycode` | 今日密码 | — | `deltaforce.dailycode` |
| `df/itemprice` | 物品物价 | — | `deltaforce.itemprice` |
| `df/itemprice/{objectId}` | 物品详情 | `objectId` | — |
| `df/friendstatus` | 好友状态 | — | `deltaforce.friendstatus` |

> **扩展铁律**：新增工具 = 新增路由 + 注册 `ToolDescriptor`。
> **不得**因此修改 `HomeScreen` 的任何代码。

### 6.3 页面通用状态

每个数据页必须实现四种状态（复用 `core/design` 的统一组件）：

| 状态 | 组件 | 要求 |
|---|---|---|
| 加载中 | `UbLoading` | 骨架屏或进度指示，不用空白 |
| 空数据 | `UbEmpty` | 说明为什么空 + 下一步建议 |
| 错误 | `UbError` | 错误类型文案 + 重试按钮 |
| 有数据 | 业务内容 | 必须显示来源 + 更新时间 |

### 6.4 Liquid Glass 应用范围（设计稿 §5 要求：优先少而精）

| 位置 | 是否用 | 理由 |
|---|---|---|
| 底部悬浮导航 | ✅ 用 | 悬浮于内容之上，最能体现层次 |
| 少量重点卡片（如密码大字卡） | ✅ 用 | 视觉焦点 |
| 列表内所有卡片 | ❌ 不用 | 滚动性能与可读性风险 |
| 正文文字背景 | ❌ 不用 | 可读性红线 |
| 转场动画 | ⚠️ 可选 | 必须在"减少动态效果"下关闭 |

---

## 7. 非功能需求

| 类别 | 要求 | 验证方式 |
|---|---|---|
| 启动 | 冷启动到首页可交互 ≤ 2s（模拟器为参考，非硬指标） | 手工计时 |
| 列表滚动 | 长列表（≥100 项）滚动无明显掉帧 | 真机/模拟器观察 + 不做重活在组合期 |
| 可读性 | 文字对比度符合无障碍要求；玻璃效果下文字仍清晰 | 浅色/深色双主题目视检查 |
| 无障碍 | 所有可点元素有内容描述；触控目标 ≥ 48dp | UI 测试 + dump 检查 |
| 离线 | 无网时可展示缓存并标注时间 | 断网测试 |
| 降级 | 玻璃效果可在设置中关闭；低端设备自动降级 | 设置验证 + 性能阈值 |
| 安全 | API Key 仅存本机；不写日志；不实现 Cookie 登录 | 代码审查 + grep |
| 版权 | 遵循数据源与依赖的 License | `LICENSES.md` |

---

## 8. 待决问题（需用户裁决）

| ID | 问题 | 影响 | 建议 |
|---|---|---|---|
| **Q1** | **好友在线状态无可用数据源，如何处置？** | 决定 F-06 交付形态 | 建议 **方案 A**（降级实现 + 明确告知），保留架构与单测逻辑，等数据源就绪即可点亮 |
| **Q2** | 是否接受使用社区第三方接口（非腾讯官方）？ | 决定 F-03/F-04/F-05 能否交付 | 建议接受，但必须在应用内如实声明来源与免责 |
| **Q3** | 今日密码的有效日期判定方式（接口未提供日期字段） | 决定四态判定的准确性 | P01 实测后确认；若确无日期，采用"抓取日期 + 如实说明" |
| **Q4** | 是否需要 API Key 让用户自行填写？ | 决定配置页复杂度 | 建议：内置默认 + 允许用户覆盖（便于接口失效时自救） |
| **Q5** | Liquid Glass 若 P01 验证代价过高，是否接受"自绘 + 高斯模糊"替代方案？ | 决定 F-11 实现路径 | 待 P01 结论，见 `docs/risks.md` |

---

## 9. 每阶段验收标准（与 `ROADMAP.md` 一致）

| 阶段 | 验收标准（必须可核验） |
|---|---|
| P00 | 13 项交付物产出；待决问题 Q1–Q5 得到答复；用户明确批准 |
| P01 | 版本组合实机构建通过；Liquid Glass 方案实测结论；三个数据源真实可用性结论；许可证清单 |
| P02 | 8 类页面原型；设计令牌（颜色/字号/圆角/间距）落地为代码常量 |
| P03 | `assembleDebug` 成功 + APK 装到 `emulator-5556` + 启动不崩溃 |
| P04 | 首页工具网格由注册表渲染；新增一个假工具**不改首页代码**即可出现在列表 |
| P05 | 好友状态四态排序单测通过；数据源不可用时页面如实说明（不造假数据） |
| P06 | 今日密码四态判定单测通过；复制可用；**过期数据不冒充今日** |
| P07 | 物品搜索/详情/历史折线可用；缺失数据点留空；来源与时间可见 |
| P08 | 玻璃效果统一应用且可关闭；关闭后功能与可读性不退化 |
| P09 | 设置全部生效且持久化；工具启停即时反映；数据源状态页真实反映 |
| P10 | 单元/UI/集成测试真实执行并记录；异常路径覆盖；性能问题修复 |
| P11 | Debug APK 实际生成并安装验证；文档与代码一致；许可清单完整 |
