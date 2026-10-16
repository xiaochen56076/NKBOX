# LICENSES — 第三方依赖与素材授权清单

> 最后更新：**2026-10-15（P11 补 20）**
> 依据 `PROJECT_RULES.md` §5：新增依赖必须先完成 **版本 / License / 兼容性 / 维护状态** 四项检查。

---

## ⚠️ 关于上一版

本文件此前停在 **P00（2026-10-14）**，且内容与工程**实际不符**：

| 旧文件写的 | 实际 |
|---|---|
| Compose BOM `2026.02.01` | `2026.09.00` |
| Kotlin `2.2.10` | `2.4.21` |
| Lifecycle Runtime KTX 2.6.1 | **早已不是依赖** |
| androidx.test / Espresso | **早已不是依赖** |
| （**完全没有 Haze**） | **Haze 是唯一第三方依赖** |

**唯一的外来依赖没登记，反而列了三个已经不存在的。** 这正是本项目反复出现的
「两份互相矛盾的真相」—— 这次在合规文档上。已按工程实际重写。

---

## 一、本项目自身

| 项 | 值 |
|---|---|
| 项目名 | N盒（UtilityBox） |
| 包名 | `com.utilitybox` |
| 版本 | `0.1.0-p11` |
| 许可证 | ⚠️ **待用户确定**（MIT / Apache-2.0 / 私有） |

---

## 二、运行时依赖（会打进 APK）

版本取自 `gradle/libs.versions.toml`，坐标为实际声明值。

| 坐标 | 版本 | 许可证 | 核实方式 |
|---|---|---|---|
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.4.21 | Apache-2.0 | 已知（随 Kotlin 工具链） |
| `androidx.core:core-ktx` | 1.10.1 | Apache-2.0 | 已知（AndroidX 统一许可） |
| `androidx.activity:activity-compose` | 1.13.0 | Apache-2.0 | 已知 |
| `androidx.compose:compose-bom` | 2026.09.00 | Apache-2.0 | 已知 |
| ├ `androidx.compose.ui:ui` | 由 BOM 管理 | Apache-2.0 | 已知 |
| ├ `androidx.compose.ui:ui-graphics` | 由 BOM 管理 | Apache-2.0 | 已知 |
| ├ `androidx.compose.foundation:foundation` | 由 BOM 管理 | Apache-2.0 | 已知 |
| └ `androidx.compose.material3:material3` | 由 BOM 管理 | Apache-2.0 | 已知 |
| **`dev.chrisbanes.haze:haze`** | **2.0.1** | **Apache-2.0** | ★ **已核实**：读本地 Gradle 缓存中的 `haze-2.0.1.pom`，`<licenses>` 声明 *The Apache Software License, Version 2.0* |
| `dev.chrisbanes.haze:haze-glass` | 2.0.1 | Apache-2.0 | 同 POM 组，同上 |
| `dev.chrisbanes.haze:haze-glass-material3` | 2.0.1 | Apache-2.0 | 同 POM 组，同上 |

**结论：运行时依赖全部为 Apache-2.0**（宽松许可，允许闭源分发，需保留版权与许可声明）。

★ Haze 是**唯一**的非 AndroidX/Kotlin 官方依赖。作者 Chris Banes（前 Google Android 团队成员），
仓库 <https://github.com/chrisbanes/haze/>。

---

## 三、仅测试期（不进 APK）

| 坐标 | 版本 | 许可证 |
|---|---|---|
| `junit:junit` | 4.13.2 | **EPL-1.0** |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1.9.0 | Apache-2.0 |

★ JUnit 的 EPL-1.0 与 Apache-2.0 不同，但它**只在测试 classpath**，不参与分发，无需随包声明。

---

## 四、构建期工具（不进 APK）

| 工具 | 版本 | 许可证 |
|---|---|---|
| Android Gradle Plugin | 9.2.1 | Apache-2.0 |
| Gradle | 9.4.1 | Apache-2.0 |
| Kotlin + `org.jetbrains.kotlin.plugin.compose` | 2.4.21 | Apache-2.0 |

---

## 五、★ 游戏素材授权状态（**未核实**，如实标注）

任务书要求「游戏封面、角色立绘、Logo 等素材必须单独核实使用授权」。逐项交代：

| 素材 | 来源 | 状态 |
|---|---|---|
| 应用图标 / 启动页标志 | **用户自行提供**的 logo 图 | ✅ 用户提供，授权由用户掌握 |
| 鸣潮图鉴的角色立绘、头像、物品图 | 🔴 **热链接库街区 CDN**（`prod-alicdn-community.kurobbs.com`） | ❌ **未核实** |
| 三角洲物品 / 弹药图标 | 🔴 数据源返回的 `pic` / `prePic`，指向**腾讯官方** `playerhub.df.qq.com` | ❌ **未核实** |
| 游戏名称、封面 | 仅使用**文字名称**，未使用官方封面图 | ✅ 无素材风险 |
| **底栏游戏标识** | 手绘的通用手柄 / 水波图形 | ⚠️ **已知悉并接受**（见下） |

### ★ 底栏游戏标识：用户已明确接受该风险（P11 补 31）

底栏「三角洲行动 / 鸣潮」两个 tab 用的是**手绘的通用图形**（手柄 / 水波），
严格说属于「用通用图标当游戏身份」，不符合本文件上方那条要求。

**用户的裁决（原话）**：

> 关于游戏的 logo，不影响，因为这个是**个人使用，不会传播**的

### 这条记录该怎么读（不要误读成"已授权"）

| 是 | 不是 |
|---|---|
| 用户**知悉**该标识并非官方 Logo、也非授权素材 | ❌ **不是**"素材已获授权" |
| 用户**基于个人自用、不传播**的前提**接受**该风险 | ❌ **不是**"该做法在公开发行时也合规" |
| 该标识为**本项目自绘**，未抓取任何官方美术资源 | ❌ 不代表可以换成抓来的官方 Logo |

★ **若将来要公开发行或商业化，这一项必须重新处理。**
本节保留完整记录，就是为了让那时的判断有依据，而不是留下一句
"当时好像说过没问题"。

### 现状说明（不是辩解，是事实）

应用对这些图片做的是**热链接**（不下载、不打包、不二次分发），图片始终由官方 CDN 提供。
这与"把美术资源打进 APK 再分发"是不同程度的行为。

**但"程度较轻"不等于"已授权"。** 本项目为**个人自用、非商业**，风险可控；
**若将来要公开发行或商业化，必须先解决这一项**，做法二选一：

1. 取得库洛 / 腾讯的素材使用许可
2. 改为不显示官方美术资源（用纯文字 + 自绘占位）

★ 这一条**不应被当作已完成**。它现在是**已知的未核实项**。

---

## 六、★ 内嵌第三方资源（不是依赖，但同样受许可证约束）

| 资源 | 来源 | 版本 | 许可证 | 用途 |
|---|---|---|---|---|
| **Lucide Icons** | <https://lucide.dev> · <https://github.com/lucide-icons/lucide> | **lucide-static `1.57.0`**（固定版本） | **ISC** | 全 App 的统一图标 |

### 为什么它在"资源"而不是"依赖"里

图标**不是通过 Maven 引入的**，而是把官方 SVG 转成 `res/drawable/ic_lucide_*.xml` 内嵌进工程。

原因：本工程一直用 **`--offline` 构建**，本地 Gradle 缓存里没有任何 Lucide 的 Compose 封装
（`com.composables` / `compose-icons` / `devsrsouza` 全都没有）—— 加 Maven 依赖会**直接构建失败**。

而 VectorDrawable 有个决定性优势：**`android:pathData` 与 SVG 的 `d` 语法完全一致**，
所以转换不需要写路径解析器，也就不会在转换中悄悄改掉图形。

### ★ ISC 许可证要求（必须遵守）

```
ISC License
Copyright (c) 2026 Lucide Icons and Contributors

Permission to use, copy, modify, and/or distribute this software for any
purpose with or without fee is hereby granted, provided that the above
copyright notice and this permission notice appear in all copies.
```

ISC 允许商用与闭源分发，但**要求在副本中保留版权声明与许可声明**。
落实方式：**每个生成的 XML 文件头部都带版权行与许可证指引**，本文件作为完整声明。

### 核实过程（不是凭印象写的）

| 事实 | 怎么核实的 |
|---|---|
| 版本 = `1.57.0` | 读 npm registry 的 `lucide-static/latest` 元数据 |
| 许可证 = ISC | ① 读仓库 `LICENSE` 文件原文；② npm 元数据的 `license` 字段。**两处一致** |
| 作用范围 | 只下载了**实际使用的 23 个**图标，没有整包引入（用户要求 6） |

### 用户提出的 7 条要求，逐条落实

| # | 要求 | 落实 |
|---|---|---|
| 1 | 线性、24dp 基准 | Lucide 原生 `stroke-width=2`、24×24 视口 |
| 2 | 统一描边/颜色/大小，不混用图标库 | **全 App 只有这一个图标源**，23 个图标同一套参数 |
| 3 | 颜色跟随主题与语义状态 | XML 里 `strokeColor` 是黑色占位，实际由 `Icon(tint=)` 的 **ColorFilter 覆盖** |
| 4 | 底栏轻量动画 | 现成的滑动胶囊，无旋转/弹跳。★ 用户 P11 补 30 裁决**主页底栏保持原样** |
| 5 | 游戏 Logo 不能冒充 | ⚠️ 见第五节「游戏素材授权状态」——**仍未解决** |
| 6 | 只打包用到的图标 | **23 个图标 = 23 个 XML**，一个多余都没有 |
| 7 | 核对并记录许可证 | 本节 |

---

## 七、已评估但**未采用**的候选库（任务书要求记录比较结果）

| 候选 | 用途 | 结论 | 理由 |
|---|---|---|---|
| [`Kyant0/AndroidLiquidGlass`](https://github.com/Kyant0/AndroidLiquidGlass) | Liquid Glass | **未采用** | 功能与 Haze 重叠。任务书第 8 条禁止"多个实现同一职责的 UI 库"；Haze 已在 P04 实测通过并接入 |
| [`canopas/compose-animated-navigationbar`](https://github.com/canopas/compose-animated-navigationbar) | 动画底栏 | **未采用** | 自研 `UbBottomBar` 已实现连续跟手的滑块（含纯函数单测钉住的位置算法），换库等于丢弃已验证的成果 |
| [`coil-kt/coil`](https://github.com/coil-kt/coil) | 图片加载 | **未采用** | 自研 `UbImageLoader` 已有内存 LruCache + 磁盘 LRU + 网络三级缓存；引入 Coil 属重复职责 |
| [`airbnb/lottie-android`](https://github.com/airbnb/lottie-android) | Lottie 动画 | **未采用** | 全部动效已用 Compose 原生动画实现且已按用户反馈调好手感；无 Lottie 使用场景 |
| [`patrykandpatrick/vico`](https://github.com/patrykandpatrick/vico) | 图表 | **未采用** | 唯一图表场景（物品历史价）**只允许画真实数据点**，不可插值/平滑；自绘更可控 |
| [`kizitonwose/Calendar`](https://github.com/kizitonwose/Calendar) | 日历 | **未采用** | 当前无日历场景。**若做「鸣潮游戏日历」则重新评估** |

★ 用户于 P11 补 20 授权「主页可以采用 UI 库里更好看的组件」后，实际做法是
**把已在依赖里的 Material3 真正用起来**（此前全项目只用了 `Text`/`Switch`/`OutlinedTextField`/`TextButton` 四样），
新增 `Card` / `CardDefaults` 的描边与高度、渐变身份卡、分节标题等 —— **依赖清单没有变化**。

---

## 七、变更记录

| 日期 | 变更 |
|---|---|
| 2026-10-14（P00） | 初版。**内容与工程不符，见文首** |
| 2026-10-15（P11 补 20） | 按工程实际重写；补 Haze（POM 核实）；补候选库比较表；补游戏素材授权状态 |
