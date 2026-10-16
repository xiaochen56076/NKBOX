# UtilityBox 技术选型与风险清单

> 阶段：P00 ｜ 日期：2026-10-14 ｜ 状态：待批准
> 本文件是 P00 交付物第 10 项
> **原则：未验证的能力一律标注"未验证"，不得因"看起来可行"就当作已解决。**

---

## 1. 已验证的技术基线（可放心使用）

| 项 | 版本 | 证据 |
|---|---|---|
| AGP | 9.2.1 | `AGENT_GUIDE.md` §5.2 本机实测；Gradle 缓存中已有 `com.android.tools.build:gradle:9.2.1` |
| Kotlin | 2.2.10 | 同上；缓存中有完整 `kotlin-gradle-plugin:2.2.10` 及 `compose-compiler-gradle-plugin:2.2.10` |
| Compose BOM | 2026.02.01 | 同上；缓存中已解析为 **Compose UI 1.10.4** / **Material3 1.4.0** / foundation 1.10.4 |
| Gradle | 9.4.1 | 同上 |
| compileSdk | 36.1（AGP 9 新 DSL） | 同上；SDK 已装 `platforms;android-36.1` |
| build-tools | 36.0.0 / 36.1.0 / 37.0.0 | `devtools.ps1 status` 实测 |
| JDK | 21.0.7 LTS | `devtools.ps1 status` 实测 |
| AVD | `dsh_pixel10` / API 36 / 端口 5556 | `devtools.ps1 status` 实测 |
| 零依赖网络 | `HttpURLConnection` + `org.json` | GameMate 已实测可行 |
| 零依赖持久化 | `SharedPreferences` + JSON 字符串 | 同上 |
| 手绘图标 | Compose `Canvas` / `Path` | 同上（BOM 不含 material-icons） |

### 1.1 本机 Gradle 缓存现状（实测）

| 依赖 | 缓存状态 | 对 P01 的含义 |
|---|---|---|
| AGP 9.2.1 / Kotlin 2.2.10 / Compose 1.10.4 / Material3 1.4.0 | ✅ 已在缓存 | 最小工程可**离线快速**构建 |
| `androidx.activity:activity-compose` | ✅ 1.8.0 / 1.8.2 | 与版本基线一致 |
| `androidx.lifecycle:*` | ✅ 2.6.1 / 2.8.7 / 2.9.2 / 2.9.4 | 多版本可用 |
| `androidx.core:core-ktx` | ✅ 1.10.1 / 1.16.0 | — |
| `kotlinx-coroutines-core` | ✅ 1.6.4 / 1.7.3 / 1.8.0 / 1.9.0 | 随 Compose 传递引入 |
| **`androidx.navigation:navigation-compose`** | ❌ **未缓存** | 引入即需联网下载（首次构建变慢） |
| **`androidx.room`** | ❌ **未缓存** | 同上 |
| **`androidx.datastore`** | ❌ **未缓存** | 同上 |
| **`dev.chrisbanes.haze`（毛玻璃候选）** | ❌ **未缓存** | 同上 |

> 缓存证据来源：`%USERPROFILE%\.gradle\caches\modules-2\files-2.1`（P00 实测）

---

## 2. ★★ 决定性实测结果：7 变体 × 2 路线的完整构建矩阵

**方法**：同一份源码、同一套依赖，**唯一差异是 Kotlin 版本**。
`D:\APPDATA\PCD\DSH\ubox-verify`（路线A，Kotlin 2.2.10）与 `ubox-verify-b`（路线B，Kotlin 2.4.21），
`compileSdk = 37.0` / `minSdk 24` / `targetSdk 36` / Compose BOM 2026.02.01 / AGP 9.2.1 / Gradle 9.4.1。

| 变体 | 依赖 | 路线A Kotlin 2.2.10 | 路线B Kotlin 2.4.21 |
|---|---|---|---|
| **Baseline** | BOM + `navigation-compose 2.10.2` | ✅ **11.65 MB** | ✅ 11.72 MB |
| **Selfnav** | BOM + **零**导航依赖（手写回退栈） | ✅ **11.76 MB** | ✅ 11.85 MB |
| **Platformblur** | BOM + **零**玻璃依赖（官方 `Modifier.blur`） | ✅ **11.76 MB** | ✅ 11.85 MB |
| **Liquidglass** | `io.github.nadeemiqbal:liquid-glass:0.2.3` | ✅ **11.85 MB** | ✅ 11.90 MB |
| **Haze1** | `dev.chrisbanes.haze:haze:1.7.3`（纯模糊） | ✅ **11.73 MB** | ✅ 11.78 MB |
| **Haze2** | `haze 2.0.1` + `haze-glass 2.0.1`（**折射**） | ❌ **metadata 2.4.0 不兼容** | ✅ **12.14 MB** |
| **Backdrop** | `io.github.kyant0:backdrop:2.0.1` | ❌ API 用法 | ❌ API 用法 |

### 结论（每一条都有构建产物为证）

1. ✅ **`compileSdk 37` 是打开大门的钥匙。** 提到 37 之后，官方导航、liquid-glass、haze 1.7.3 **全部从"失败"变为"通过"**。
   （此前在 `compileSdk 36.1` 下它们全部被 AAR metadata 挡住。）
2. ✅ **`haze2` 是唯一被 Kotlin 版本硬卡住的项**：Kotlin 2.2.10 下编译失败，Kotlin 2.4.21 下**成功产出 12.14 MB APK**。
   → 这是对「Kotlin metadata +1 minor 天花板」的**构建级实证**，不再只是 javap 推断。
3. ✅ **两条路线都能跑通核心栈**：Baseline / Selfnav / Platformblur / Liquidglass / Haze1 在 **2.2.10 上即可用**。
   → **留在 Kotlin 2.2.10 是可行路线**，代价是放弃 `haze2`（折射式）。
4. ⚠️ **`backdrop` 两次都因 API 用法失败**（`Unresolved reference` / `No value passed`），
   **尚未与 metadata 兼容性分离**。它是 API 最复杂的候选（`drawBackdrop` 有 13 个参数），
   **不构成"技术不可行"的证据，只构成"接入成本高"的证据。** 结论待定。
5. 📌 **`liquid-glass:0.2.3` 在 Kotlin 2.2.10 上实测可编译**（11.85 MB），且**库自带
   `rememberPlatformLiquidGlassQuality()` 平台分级**——天然满足设计稿「必须可以独立降级或关闭」的硬性要求。

**实测踩到的两个"坑中坑"（写入经验）**
- `LiquidGlassState` 的构造函数是 **internal**，必须用公开工厂 `rememberLiquidGlassState(quality)`。
- `sdkmanager.bat` 会被分号拆参数；新版 CLI 用 `/` 作分隔符：
  `android --sdk=<SDK> sdk install 'platforms/android-37.0'`

---

## 3. 高风险项

### R-00a · ★★ `compileSdk 37` 是主要阻塞（比 Kotlin 版本更根本）🔴

| 项 | 内容 |
|---|---|
| 严重度 | **阻塞**（决定哪些库能用） |
| 发现方式 | **真实 Gradle 构建**（7 个 variant 全量对照） |
| 现象 | 多个候选库通过 **AAR metadata** 强制要求 `compileSdk ≥ 37`。项目原用 `compileSdk 36.1`（`AGENT_GUIDE.md` 基线）→ `check*AarMetadata FAILED` |

**实测报错原文**
```
Dependency 'X' requires libraries and applications that depend on it
to compile against version 37 or later of the Android APIs.
:app is currently compiled against android-36.1.
```

**被它挡住的依赖（实测列出）**

| 依赖 | 它引入的 Compose 版本 | 要求 |
|---|---|---|
| `androidx.navigation:navigation-compose-android:2.10.2` | → `lifecycle-viewmodel-compose:2.11.0` | compileSdk 37 |
| `androidx.navigation:navigation-common:2.10.2` | → `lifecycle-runtime/viewmodel/savedstate = **2.11.0**` | compileSdk 37 |
| `dev.chrisbanes.haze:haze-android:1.7.3` | → `compose.ui/foundation/animation = **1.12.0**` | compileSdk 37 |
| `dev.chrisbanes.haze:haze{,-glass,-utils}-android:2.0.1` | → `compose.* = **1.12.1**` | compileSdk 37 |
| `io.github.kyant0:backdrop-android:2.0.1` | → `compose.* = **1.12.0**` + `shapes-android:1.2.1` | compileSdk 37 |

> ⚠️ **重要认知修正**：`navigation-compose` 本身不需要 compileSdk 37，
> 但它经由 `navigation-common:2.10.2` 把 **lifecycle 拉到 2.11.0**，而 lifecycle 2.11.0 要求 compileSdk 37。
> （注：`lifecycle-viewmodel-compose:2.11.0` 自己声明依赖 `compose.runtime:1.11.0`，**会突破 BOM 的 1.10.4** —— 见 R-02）

**缓解措施（已实测可行）**

```powershell
# 新版 android CLI 用 "/" 作包分隔符（sdkmanager.bat 会被分号拆参数）
& '<SDK>\cmdline-tools\latest\bin\android.exe' --sdk='<SDK>' sdk install 'platforms/android-37.0'
```

- ✅ **实测安装成功**：`platforms/android-37.0`（API=37.0，119.2 MB，`android.jar` 就位）
- 可用版本：`android-37.0` / `android-37.1` / `android-37.2`
- ⚠️ **影响版本基线**：`compileSdk` 要从 `36.1` 改为 `37.0`，属 `PROJECT_RULES.md` §4 管控范围 → **需用户批准**
- ✅ 改 `compileSdk` **不影响** `targetSdk`（36）与 `minSdk`（24），运行时行为不变

**放弃 compileSdk 37 的后果（即"仅用 36.1"路线）**

| 能力 | 36.1 下是否可用 |
|---|---|
| Compose BOM + 自研导航 + 平台 `Modifier.blur` | ✅ 可用（已实测出 APK） |
| 官方 `navigation-compose` | ❌ 不可用 |
| `navigation3:1.2.0` | 🟡 待实测（其依赖 compose 1.9.5/1.10.0，**可能不需要 37**） |
| 所有第三方玻璃库（haze / liquid-glass / backdrop） | ❌ 不可用（liquid-glass 亦受同一限制，见 R-00b） |

---

### R-00b · ★ Kotlin metadata 天花板：`mv=2.4.0` 的库在 Kotlin 2.2.10 上编译失败 🔴

| 项 | 内容 |
|---|---|
| 严重度 | **阻塞**（决定能否用真·折射液态玻璃） |
| 验证方式 | **独立实测**：下载 AAR → 取 `classes.jar` → 用 **JDK 21 `javap -v`** 读 class 的 `@Metadata(mv=...)`；**并用真实 Gradle 构建复现报错** |

**根因**：Kotlin 编译器只能读取「自身版本 **+1 个 minor**」的 metadata
（官方 `kotlin-metadata-jvm` 的 `LATEST_STABLE_SUPPORTED`）。
**Kotlin 2.2.10 → 最多只能读 metadata `2.3.x`。**

**实测 `@Metadata mv`（javap 读取，非推测）**

| 坐标 | 实测 mv | Kotlin 2.2.10 | 说明 |
|---|---|---|---|
| `dev.chrisbanes.haze:haze-android:2.0.1` | **[2, 4, 0]** | ❌ **不可用** | 需 ≤ 2.3.x |
| `io.github.kyant0:backdrop-android:2.0.1` | **[2, 4, 0]** | ❌ **不可用** | 即 Compose Liquid Glass 复刻库 |
| `dev.chrisbanes.haze:haze-android:1.7.3` | [2, 3, 0] | ✅ 可用 | 仅**纯模糊**，无折射 |
| `dev.chrisbanes.haze:haze-android:1.7.2` | [2, 2, 0] | ✅ 可用 | 仅**纯模糊**，无折射 |
| `io.github.nadeemiqbal:liquid-glass-android:0.2.3` | [2, 3, 0] | ✅ 可用 | 液态玻璃（另有 compileSdk 37 问题） |
| `androidx.compose.ui:ui-android:1.10.4` | [2, 0, 0] | ✅ 可用 | 基线自身无问题 |

**真实构建报错原文（不是推断）**
```
e: Module was compiled with an incompatible version of Kotlin.
   The binary version of its metadata is 2.4.0, expected version is 2.2.0.
e: Class 'dev.chrisbanes.haze.HazeState' was compiled with an incompatible version of Kotlin.
   The actual metadata version is 2.4.0, but the compiler version 2.2.0 can read versions up to 2.3.0.
```
> 注：haze 2.0.1 / backdrop 2.0.1 还会**连带拉入 `kotlin-stdlib 2.4.20` / `2.4.10`**，
> 导致连 `kotlin.Unit` 都读不了 —— 依赖树被整体污染。

**两条路（均已实测确认版本存在）**

| 路线 | 做法 | 代价与收益 |
|---|---|---|
| **① 留在 Kotlin 2.2.10** | Haze 1.7.x 纯模糊；或 `liquid-glass:0.2.3`（若可解 compileSdk 37） | ✅ 不动 Kotlin 基线 |
| **② 升级到 Kotlin 2.4.x** | `2.4.10` 或 `2.4.21`（**均为稳定版**，`kotlin-gradle-plugin` 与 `kotlin-compose-compiler-plugin-embeddable` 均实测存在） | ✅ 解锁 haze 2.0.1 Glass / backdrop 2.0.1；⚠️ **变更 Kotlin 基线** |

> **时机**：**现在升级代价最低**——工程尚未写任何一行应用代码。

---

### R-00c · 官方导航库把 lifecycle 拉到 2.11.0（会突破 BOM）🟠

| 项 | 内容 |
|---|---|
| 实测 | `navigation-common:2.10.2` 声明依赖 `lifecycle-runtime/viewmodel/viewmodel-savedstate/common = **2.11.0**` |
| 而 | `lifecycle-viewmodel-compose:2.11.0` 声明依赖 `compose.runtime: **1.11.0**`（**高于 BOM 的 1.10.4**） |
| 且 | `lifecycle 2.11.0` 系列要求 `compileSdk 37` |
| 结论 | **引入 `navigation-compose 2.10.2` = 同时承担"突破 BOM"与"要求 compileSdk 37"两项代价** |
| 现状 | 我们已为它把 `compileSdk` 提到 37（待批准），但仍需接受 `compose.runtime` 被抬到 1.11.0，或改用 **navigation3 1.2.0**（依赖 compose 1.9.5/1.10.0，**不突破 BOM**） |

---

### R-00 · ★ Kotlin metadata 天花板：`mv=2.4.0` 的液态玻璃库在 Kotlin 2.2.10 上编译不了 🔴

| 项 | 内容 |
|---|---|
| 严重度 | **阻塞**（决定 Liquid Glass 与整体技术路线） |
| 发现于 | P00 收尾 / P01 前置调研 |
| 验证方式 | **独立实测**：下载 AAR → 取 `classes.jar` → 用 **JDK 21 `javap -v`** 读取 class 的 `@Metadata(mv=...)` |

**根因**：Kotlin 编译器只能读取「自身版本 **+1 个 minor**」的 metadata
（官方约束，`kotlin-metadata-jvm` 的 `LATEST_STABLE_SUPPORTED`）。
**Kotlin 2.2.10 → 最多只能读 metadata `2.3.x`。**

**实测结果（javap 读取的 `@Metadata mv`，非推测）**

| 坐标 | 实测 mv | Kotlin 2.2.10 | 说明 |
|---|---|---|---|
| `dev.chrisbanes.haze:haze-android:2.0.1` | **[2, 4, 0]** | ❌ **不可用** | 需 ≤ 2.3.x |
| `io.github.kyant0:backdrop-android:2.0.1` | **[2, 4, 0]** | ❌ **不可用** | 即 Compose Liquid Glass 复刻库 |
| `dev.chrisbanes.haze:haze-android:1.7.3` | [2, 3, 0] | ✅ 可用 | 仅**纯模糊**，无折射 |
| `dev.chrisbanes.haze:haze-android:1.7.2` | [2, 2, 0] | ✅ 可用 | 仅**纯模糊**，无折射 |
| `io.github.nadeemiqbal:liquid-glass-android:0.2.3` | [2, 3, 0] | ✅ 可用 | ✅ **可用的液态玻璃方案** |
| `androidx.compose.ui:ui-android:1.10.4` | [2, 0, 0] | ✅ 可用 | 基线自身无问题 |

> 失败时的典型报错：
> `Module was compiled with an incompatible version of Kotlin. The binary version of its metadata is 2.4.0...`

**两条路（均为实测可行）**

| 路线 | 做法 | 代价与收益 |
|---|---|---|
| **① 留在 Kotlin 2.2.10** | 用 `io.github.nadeemiqbal:liquid-glass:0.2.3`（mv 2.3.0 / minSdk 24 / Apache-2.0）；或 Haze 1.7.x 的纯模糊 | ✅ 不动已验证基线；⚠️ 第三方库成熟度需实测；无「折射」级效果 |
| **② 升级到 Kotlin 2.4.x** | 例如 `2.4.10` 或 `2.4.21`（**均为稳定版**，已实测存在于 Maven Central；`kotlin-compose-compiler-plugin-embeddable` 同步有 2.4.10 / 2.4.21） | ✅ 解锁 Haze 2.0.1 Glass / Backdrop 2.0.1 等真·液态玻璃；⚠️ **变更已验证的版本基线**，需重新验证 AGP 9.2.1 + Gradle 9.4.1 + Compose 1.10.4 组合 |

> **关键时机判断**：**现在升级的代价最低**——工程尚未写任何一行代码。
> 若等到 P03 之后再升级，重构成本显著上升。
> 因此 **P01 应当把 Kotlin 版本作为首批验证项之一**，用实测数据决策，而不是等到 P08 才暴露。

**P01 必做验证**
1. 用一个最小 Demo 分别验证路线 ① 与 ② 能否 `assembleDebug` 成功。
2. 记录两条路上的实际编译错误（如有）与修复成本。
3. 在模拟器（软件渲染）上对比两种玻璃效果的**渲染表现与性能**，形成可对比证据。
4. 由用户裁决最终路线（本项涉及版本基线变更，属 `PROJECT_RULES.md` §4 管控范围）。

**⚠️ 铁律不变**：无论选哪条路，玻璃效果都必须**可独立降级或关闭**。

---

### R-01 · Liquid Glass 实现方案未验证 🔴

| 项 | 内容 |
|---|---|
| 严重度 | **高** |
| 现状 | **尚未验证任何方案**在本机版本基线下能否编译运行 |
| 设计稿要求 | 必须通过 **许可证 / 兼容性 / 构建 / 设备性能** 四项验证；且**正式采用的实现应当可以独立降级或关闭** |

**已知约束（来自 `AGENT_GUIDE.md`，本机实测）**

| 约束 | 影响 |
|---|---|
| 模拟器 `hw.gpu.enabled=no`（**软件渲染**） | 模糊/玻璃效果的性能表现在模拟器上**不具代表性**，不能据此下真机结论 |
| `minSdk 24` | Compose 的 `Modifier.blur` 等模糊能力有 **API 31+ 限制**，低版本必须走降级路径 |
| BOM 2026.02.01 不含 material-icons | 图标需手绘（已接受） |
| 本机无 GUI | 玻璃效果的"好不好看"**只能由人眼判断**（设计稿 §七.1 已明确） |

**候选方案（P00 不选定，P01 逐个实测）**

| 方案 | 坐标 / API | 最新版本 | 许可证 | 优点 | 风险 |
|---|---|---|---|---|---|
| **A. 平台模糊**（零依赖） | Compose `Modifier.blur` / `graphicsLayer` + `RenderEffect` | 平台 API | — | 零新依赖、官方 API | **API 31+ 限制**；`minSdk 24` 必须自备降级；软件渲染下可能很慢 |
| **B1. Haze**（毛玻璃库，主流） | `dev.chrisbanes.haze:haze` | **2.0.1** | 待核验 | 生态成熟、专为 Compose 毛玻璃设计、跨版本处理较好 | 新增依赖（**本机未缓存**）；需核验与 Compose 1.10.4 的兼容性；许可证待确认 |
| **B2. Backdrop / AndroidLiquidGlass**（专门的 Liquid Glass 复刻） | `io.github.kyant0:backdrop` | **2.0.1** | ✅ **Apache-2.0**（已从 Maven POM 实测确认） | **就是 Liquid Glass 本体效果**，最接近设计稿要求 | ⚠️ **其 POM 声明依赖 Kotlin stdlib 2.4.10 + Compose Multiplatform 1.12.0**，而本项目基线是 **Kotlin 2.2.10 + Compose 1.10.4**。强行引入会**向上拉高 Compose 运行时**，可能破坏已实测的版本基线 |
| **C. 自绘近似玻璃**（零依赖） | 半透明分层 + 渐变 + 细边框 + 轻噪点（可选叠 A） | 平台 API | — | 零依赖、性能可控、降级天然 | 视觉不如真模糊"液态" |
| **D. 组合（推荐路径）** | 能用 A 就用 A，否则落 C | 平台 API | — | 兼顾效果与稳健，零新增依赖 | 实现复杂度上升 |

**P00 关键判断**

1. **方案 B2（Backdrop）虽然许可证干净且效果最对口，但版本错配风险最大。**
   其依赖的 Compose 1.12.0 高于本机缓存与实测基线的 1.10.4，引入即等于**变更版本基线**，
   违反 `PROJECT_RULES.md` §4「不得随意升级版本基线」与 §5「零新依赖优先」。
   → **P00 不建议首期采用**，除非 P01 实测证明可与现有基线共存。

2. **方案 A + C 的组合（D）是零新增依赖的稳健路径**，且天然满足设计稿
   「必须能够降级或关闭」的硬性要求。

3. 若 P01 认为自绘效果达不到 Apple 风格要求，再评估 **B1（Haze）**，
   但必须先完成四项验证（许可证 / 兼容性 / 构建 / 设备性能）。

> **P00 结论**：**不预选任何第三方库**。P01 按 **A → D → C** 顺序实测，
> 以"能独立降级/关闭"为硬前提，以实测结论为准。**不得凭本建议直接写代码。**

**必须验证的内容（P01 清单）**
1. 候选方案在 `minSdk 24` 下的行为与降级路径。
2. 在本机模拟器（软件渲染）上是否崩溃/卡死。
3. 关闭玻璃后性能与可读性不退化。
4. 许可证（若引入第三方库）。

---

### R-02 · 导航方案未定：`navigation-compose` 为首次引入的新依赖 🟠

| 项 | 内容 |
|---|---|
| 严重度 | 中高 |
| 现状 | 设计稿要求"Navigation Compose 或经过验证的兼容方案" |

**实测事实**

| 事实 | 证据 |
|---|---|
| `navigation-compose` 最新 release 为 **2.10.2** | Google Maven `maven-metadata.xml`（`lastUpdated 20260923`），P00 实测 |
| 参考工程 GameMate **未使用**任何导航库 | GameMate `libs.versions.toml` 中无 navigation 条目 |
| 本机 Gradle 缓存**无** navigation | `modules-2` 实测 |

**风险**：这是本项目**第一次引入导航库**，兼容性未经本机验证。

**⚠️ 实测发现的隐蔽陷阱：BOM 只管 `androidx.compose.*`，其他库会**静默拉高** Compose 版本**

实测结果（读取各库 POM 的直接依赖）：

| 依赖 | 版本 | 它声明依赖的 Compose 版本 | 相对 BOM 的 1.10.4 | 判定 |
|---|---|---|---|---|
| `lifecycle-viewmodel-compose` | **2.11.0** | `compose.runtime:runtime = **1.11.0**` | **高于** | ❌ **不用** |
| `lifecycle-viewmodel-compose` | **2.10.0** | `compose.runtime:runtime = 1.9.0` | 低于 | ✅ **推荐** |
| `lifecycle-runtime-compose` | **2.10.0** | `compose.runtime:runtime = 1.9.0` | 低于 | ✅ 推荐 |
| `navigation-compose` | **2.10.2** | `ui` / `animation` / `runtime` / `runtime-saveable` = **1.10.5** | patch 上浮 | ⚠️ 可接受 |
| `navigation3-runtime` | **1.2.0** | `runtime` = 1.9.5、`savedstate` = 1.4.0 | 低于 | ✅ **最干净** |
| `navigation3-ui` | **1.2.0** | `runtime` / `ui` / `animation` = 1.10.0 | 低于 | ✅ |
| `activity-compose` | **1.13.0** | `runtime` = 1.7.0、`ui` = 1.0.1 | 低于 | ✅ **推荐** |

> Compose BOM 2026.02.01 只覆盖 **7 个 groupId**：
> `androidx.compose.animation / foundation / material / material3 / material3.adaptive / runtime / ui`。
> **lifecycle / navigation / activity / datastore / room 都不在 BOM 内**，必须自己写版本号，且**必须检查它们反向拉扯的 Compose 版本**。

**候选方案**

| 方案 | 说明 | 评估 |
|---|---|---|
| A. `navigation-compose 2.10.2` | 官方方案，生态最成熟 | 会把 `ui`/`runtime`/`animation` 抬到 **1.10.5**（patch 上浮，风险低但已偏离 BOM）；需下载（未缓存） |
| B. `navigation3 1.2.0` | Navigation 新方向，已 GA；`runtime 1.9.5`、自定义转场能力强 | **最不打扰 BOM**；适合 Apple 风格的自定义转场与手势返回 |
| C. **自研轻量导航** | Compose 状态 + `AnimatedContent` + 自维护回退栈 | **零依赖**；首期 9 个页面完全可控；**工具注册机制需要的"模块自治注册 graph"反而更易实现** |
| D. C 打底 + A/B 之一 | 先自研，后续换官方 | 稳妥但成本高 |

> **P00 倾向**：首期页面少、路由简单、且工具注册机制用自研方案更直接 → 可优先考虑 **C**。
> 若要官方方案，**Navigation 3 (1.2.0) 比 navigation-compose 对 BOM 更友好**。
> **最终必须由 P01 实测决定**（设计稿要求"经过验证的兼容方案"）。

**注意**：无论选哪个，都必须满足"新增工具不改根导航"的可扩展性要求（见 `docs/architecture.md` §4）。

---

### R-02b · AGP 9 不再需要 `kotlin.android` 插件

| 项 | 内容 |
|---|---|
| 事实 | AGP 9.0+ **内置 Kotlin 支持**。官方原文：*"If you're using AGP 9.0.0 or later, you no longer need the `org-jetbrains-kotlin-android` plugin because the AGP has built-in Kotlin support."* |
| 且 | AGP 9.2.1 的 POM 直接依赖 `kotlin-gradle-plugin:2.2.10` + `kotlin-stdlib:2.2.10` → **与本项目基线是官方配对** |
| 影响 | 新工程**只加** `org.jetbrains.kotlin.plugin.compose`（版本必须 = Kotlin 版本 = `2.2.10`），**不要**再加 `org.jetbrains.kotlin.android`（可能与内置版本冲突） |
| 状态 | ✅ 已确认（AGP POM + 官方文档） |

---

### R-03 · 数据源为第三方社区服务 🟠

| 项 | 内容 |
|---|---|
| 严重度 | 高（业务连续性） |
| 现状 | `delta-test-api.shallow.ink` 为社区第三方，**非腾讯官方**，未找到公开服务条款 |
| 风险 | 可能随时停服、改接口、限流；数据来源未确认 |
| 缓解 | 数据源适配器隔离（`domain/datasource`）；缓存可离线看；应用内如实声明来源与免责 |

详见 `docs/data-sources.md` §3。

---

### R-04 · 好友在线状态无可用数据源 🔴

见 `KNOWN_ISSUES.md` ISSUE-001。**阻塞 P05**，需用户裁决。

---

### R-05 · 今日密码缺少日期字段 🟠

见 `KNOWN_ISSUES.md` ISSUE-002。影响 P06 的四态判定，需 P01 实测确认。

---

## 3. 中风险项

### R-06 · 模拟器为软件渲染，性能结论不可信

| 项 | 内容 |
|---|---|
| 现象 | AVD 配置 `hw.gpu.enabled=no` |
| 影响 | P10 的性能结论、玻璃效果开销结论在模拟器上**不代表真机** |
| 缓解 | 所有性能结论必须标注测试环境；玻璃效果性能需真机复核；**不得**在模拟器上得出"性能达标"的结论 |

### R-07 · 无 GUI：视觉验收只能靠人眼

| 项 | 内容 |
|---|---|
| 现象 | 代理无法操作 GUI，只能读文本 dump 与截图 |
| 影响 | 配色、间距、玻璃强度、动画手感无法自动判断 |
| 缓解 | 文字级验证用 `devtools.ps1 ui`；视觉判断请人看截图（设计稿 §七.1 已承认这是必须由人做的事） |

### R-08 · 中文界面文字与字体

| 项 | 内容 |
|---|---|
| 现象 | CJK 字形与字重在不同字体下差异明显 |
| 影响 | 设计稿要求 Apple 风格精致排版，中文字体处理不当会明显掉档 |
| 缓解 | P02 显式指定中文字体回退并实际渲染检查 |

### R-09 · uiautomator dump 对 Compose 的支持度

| 项 | 内容 |
|---|---|
| 现象 | Compose 的语义树需要正确设置语义信息才能被 dump 出可读文字 |
| 影响 | 无 GUI 环境下，"界面验证"依赖 dump 能否读到文字 |
| 缓解 | P03 起即验证 Compose 页面的 dump 输出；必要时补充 `contentDescription` 与 `testTag`；把这作为**可测试性设计的一部分** |

### R-10 · 首次构建耗时

| 项 | 内容 |
|---|---|
| 现象 | 核心依赖已在缓存（AGP/Kotlin/Compose），但首次构建仍需配置与编译 |
| 影响 | P01 时间预算 |
| 缓解 | 复用版本基线；若引入 navigation 等未缓存依赖，预留下载时间 |

---

## 4. 低风险项 / 已接受

| # | 项 | 处理 |
|---|---|---|
| R-11 | BOM 不含 material-icons | 已接受：图标全部 Canvas/Path 手绘（本机已验证可行） |
| R-12 | `JAVA_HOME` 未持久化 | 已接受：`devtools.ps1 build` 自动设置；文档已记录 |
| R-13 | `devtools.ps1` 必须存为带 BOM 的 UTF-8 | 已接受：改脚本时遵守 |
| R-14 | PowerShell 读 XML/JSON 需显式 UTF-8 | 已接受：已写入 `PROJECT_RULES.md` §7 |

---

## 5. 技术选型决策表（P00 结论）

| 领域 | P00 决策 | 状态 |
|---|---|---|
| 语言/构建 | Kotlin 2.2.10 + AGP 9.2.1 + Gradle 9.4.1 | ✅ 已实测；**是否升 Kotlin 见 R-00（待裁决）** |
| 插件 | **只加** `org.jetbrains.kotlin.plugin.compose`（=`2.2.10`） | ✅ 已确认（AGP 9 内置 Kotlin，不加 `kotlin.android`） |
| UI | Jetpack Compose（BOM 2026.02.01 → UI **1.10.4** / M3 **1.4.0** / icons **1.7.8**） | ✅ 已实测（BOM 240 条） |
| 架构 | MVVM + 分层 + 包边界（单 Gradle 模块） | ✅ 已决定，P03 落地 |
| 依赖注入 | 手写容器（不引 Hilt/Koin） | ✅ 已决定 |
| 网络 | `HttpURLConnection`，经 `core/network` 抽象 | ✅ 已决定 |
| JSON | 内置 `org.json`，解析函数纯化以便单测 | ✅ 已决定 |
| 持久化 | `SharedPreferences` + JSON + `schemaVersion` | ✅ 已决定 |
| 图标 | Compose Canvas/Path 手绘（**库其实可用，但选手绘**） | ✅ 已决定（前提已修正） |
| ViewModel | `lifecycle-viewmodel-compose` **2.10.0** | ⚠️ **刻意不用 2.11.0**（会拉 runtime 到 1.11.0） |
| Activity | `activity-compose` **1.13.0** | ✅ 已实测（不拉高 Compose） |
| **导航** | **待 P01 实测**（navigation3 1.2.0 / navigation-compose 2.10.2 / 自研） | 🟠 **未验证** |
| **Kotlin 版本路线** | **待用户裁决**（留 2.2.10 vs 升 2.4.x） | 🔴 **阻塞 R-00** |
| **Liquid Glass** | **待 P01 实测**（见 R-00 两条路） | 🔴 **未验证** |
| 图表 | 优先自绘（Canvas 折线，零依赖） | 🟡 倾向自绘，待 P07 |
| 测试 | JUnit4 4.13.2（已缓存）+ compose-ui-test 1.10.4（已缓存） | ✅ 已实测 |

---

## 6. P01 必须产出结论的问题

| # | 问题 | 判定标准 |
|---|---|---|
| 1 | 版本组合能否实机构建 | `BUILD SUCCESSFUL` + APK 生成 |
| 2 | 最小工程能否安装启动 | adb 安装成功 + 无 `FATAL EXCEPTION` |
| 3 | Liquid Glass 选哪个方案 | 四项验证（许可证/兼容性/构建/性能）全部通过 |
| 4 | 导航选哪个方案 | 兼容性实测通过，且满足"不改根导航"扩展性 |
| 5 | Compose 页面能否被 uiautomator dump 读到文字 | dump 出预期的中文文字 |
| 6 | 三个数据源的真实可用性 | 各有明确分级结论 |

> 以上任一项未完成结论，**不得进入 P02**。

---

## 7. 风险登记变更记录

| 日期 | 变更 |
|---|---|
| 2026-10-14 | 首次建立（P00）。确认版本基线为已实测；标记 Liquid Glass 与导航为未验证高危项；记录本机 Gradle 缓存现状 |
