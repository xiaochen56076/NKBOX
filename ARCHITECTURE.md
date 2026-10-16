# ARCHITECTURE（摘要与决策索引）

> 本文件是**摘要 + 决策索引**。完整设计见 [`docs/architecture.md`](docs/architecture.md)。
> 两者冲突时**以 `docs/architecture.md` 为准**，并同步更新本文件。
>
> 最后更新：2026-10-14（P00）

---

## 一句话架构

**单 Gradle 模块 + 包边界分层（app / feature / game / domain / core）+ 工具注册机制 + 数据源适配器。**

---

## 依赖方向（唯一允许）

```
app  →  feature  →  game/<游戏>/  →  domain  ←  core
                                      ↑
                          （domain 只定义契约，core 被依赖）
```

### 铁律

| 规则 | 内容 |
|---|---|
| R1 | `core/` **不得** import `game.*` 或 `feature.*` |
| R2 | `game/deltaforce/*` 三个功能**互不依赖** |
| R3 | `feature/` 只依赖 `domain/` 接口，不依赖数据层实现 |
| R4 | 任何层不得直接 new HTTP 客户端，必经 `core/network` |
| R5 | `domain/` 不依赖 Android 框架（保证纯 JVM 单测） |
| R6 | 跨模块通信只经 `domain/` 接口或工具注册表 |

---

## 包结构（即未来的 Gradle 模块边界）

| 包 | 职责 | 未来模块名 |
|---|---|---|
| `com.utilitybox.app` | 组装层：导航、DI、入口 | `:app` |
| `com.utilitybox.core.*` | 基础设施：common / network / cache / design / navigation / tool / testing | `:core:*` |
| `com.utilitybox.domain.*` | 契约层：model / tool / game / datasource | `:domain` |
| `com.utilitybox.game.deltaforce.*` | 三角洲业务域（三个功能互不依赖） | `:game:deltaforce:*` |
| `com.utilitybox.feature.*` | 通用页面：home / tools / settings | `:feature:*` |

---

## 关键决策索引

| # | 决策 | 理由 | 详见 |
|---|---|---|---|
| D1 | 首期**单 Gradle 模块** + 包边界 | 设计稿"不建议一开始就拆出几十个独立模块" | `docs/architecture.md` §2.3 |
| D2 | **手写 DI 容器**，不引 Hilt/Koin | 零新依赖优先；首期依赖图简单 | §4.3 |
| D3 | `HttpURLConnection` + `org.json` | 本机已验证；`core/network` 已抽象，可换实现 | `PROJECT_RULES.md` §5 |
| D4 | `SharedPreferences` + JSON | 数据简单；接口已抽象 | 同上 |
| D5 | 工具注册用**显式注册列表** | 编译期安全、零依赖；动态加载被设计稿否决 | §4.5 |
| D6 | Compose **Canvas 手绘图标** | BOM 2026.02.01 不含 material-icons | §4 |
| D7 | 模型内建 `Fresh<T>` / `AppError` / `UiState` | 需求要求区分"陈旧/缺失"，不得用 0 冒充无数据 | `docs/requirements.md` §5 |
| D8 | 导航方案**待 P01 验证** | 不选未验证的方案 | `docs/risks.md` |

---

## 工具注册机制（扩展核心）

```kotlin
interface ToolProvider {                    // 每个功能模块实现一个
    fun descriptors(): List<ToolDescriptor>
    fun registerGraph(builder: NavGraphBuilder, nav: NavController)
}

interface ToolRegistry {                    // 首页/设置只依赖它
    fun enabledToolsOf(gameId: String): List<ToolDescriptor>
    fun setEnabled(toolId: String, enabled: Boolean)
    // ...
}
```

**新增一个工具的全部改动**：新建包 + 实现页面 + 实现 Provider + 在 `AppContainer` 的
`providers` 列表**加一行**。`HomeScreen`、根导航、其他模块 **0 改动**。

---

## 未来多游戏扩展

- 游戏隔离：`game/<gameId>/` 包 + 缓存键命名空间 `cache:<gameId>:...`
- 公共层**不得**含任何游戏专属业务规则
- 「今日密码」属三角洲特有 ⇒ 归 `game/deltaforce/dailycode`，**不是**公共接口
- 新增游戏：新建包 + 实现 `GameDescriptor` + 实现 Provider + `AppContainer` 注册一行

---

## 数据源可替换性

```
功能页面 → domain/datasource 接口 → 适配器实现 → HTTP
```

更换数据源只需新建适配器，**页面与分析逻辑不动**。
不可用时提供 `UnavailableDataSource`，返回"不可用"而**不是伪造数据**。

---

## 数据真实性如何体现在架构里

| 需求 | 架构机制 |
|---|---|
| 区分"陈旧"与"新鲜" | `Fresh<T>(value, fetchedAt, source, isMock)` |
| 区分"没有数据"与"数据是 0" | 可空类型 + `hasValidPriceData`；UI 强制分支 |
| 不编造好友状态 | `FriendStatusDataSource` 有 `Unavailable` 实现，不产生 `Friend` 实例 |
| 密码不冒充今日 | `DailyCodeState` 四态密封接口，`Stale` 必须携带原始日期 |
| mock 可见 | `isMock` 字段 + `UbMockBadge` 组件 + 类名 `Mock*` 前缀 |
