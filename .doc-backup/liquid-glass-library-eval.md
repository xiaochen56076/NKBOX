# Liquid Glass 组件库选型实测报告

> 建立：2026-10-14（P04 期间，依据用户要求「去 GitHub 看有没有较好的组件仓库，下载来使用合并」）
> **本文所有结论均为本机实测**，不是从文档或介绍页推断的。

---

## 0. 为什么当初没直接用现成库

P00/P01 时期结论是「**自研**」，依据是当时的环境约束：

- 当时 Kotlin 版本较低，编译器只能读 `@Metadata mv ≤ 2.3.x`，
  而 `haze 2.0.1` / `backdrop 2.0.1` 都是 `mv=[2,4,0]` → 直接不可读
- `compileSdk 36.1` 不满足部分库的 AAR metadata 要求

**这些约束在 P01 通过两项升级（Kotlin 2.4.21 + compileSdk 37.0）后被解除。**
因此「是否改用现成库」值得重新评估 —— 本文就是这次重新评估的结果。

---

## 1. 验证方法（可复现）

| 步骤 | 做法 |
|---|---|
| 找库 | Maven Central 搜索 API + **直连 `maven-metadata.xml`**（搜索 API 索引陈旧，`a:haze` 只报 latest=1.5.3，实际是 2.0.1，**不能只信搜索**） |
| 许可证 | 读 `.pom` 的 `<licenses>` |
| Kotlin 兼容 | 下载 AAR → 解 `classes.jar` → JDK 21 `javap -v` 读 `@Metadata(mv=...)` |
| **真实依赖** | 读 `.module`（Gradle Module Metadata）的 android 变体 |
| **最终判定** | **在交付工程的临时副本里真实构建 + 装到设备运行 + 截图** |

> ⚠️ 本机 `github.com` 被 DNS 劫持，**无法访问仓库页**。
> 因此 star 数、issue 活跃度、README 均**未核实**，本文不作评价。
> 存在性判断一律以 `maven-metadata.xml` / 目录列表为准。

---

## 2. 候选总表（实测）

| 坐标 | 最新版 | 许可证 | 实测 mv | minSdk | 实际落到 androidx | 是否抬 BOM |
|---|---|---|---|---|---|---|
| **`dev.chrisbanes.haze:haze`** | **1.7.2** | Apache-2.0 | **[2,2,0]** | 23 | **ui 1.10.0**（≤BOM 1.10.4） | ✅ **不抬** |
| **`dev.chrisbanes.haze:haze-materials`** | **1.7.2** | Apache-2.0 | **[2,2,0]** | 23 | **material3 1.4.0（=BOM）** | ✅ **不抬** |
| `dev.chrisbanes.haze:haze` | 2.0.1 | Apache-2.0 | [2,4,0] | 23 | ui/animation-core **1.12.1** | ❌ 抬 |
| `dev.chrisbanes.haze:haze-glass` | 2.0.1 | Apache-2.0 | [2,4,0] | 23 | foundation/ui **1.12.1** | ❌ 抬 |
| `dev.chrisbanes.haze:haze-glass-material3` | 2.0.1 | Apache-2.0 | [2,4,0] | 23 | material3 **1.4.0（=BOM）** | material3 轴对齐 |
| `io.github.kyant0:backdrop` | 2.0.1 | Apache-2.0 | [2,4,0] | 21 | foundation/ui **1.12.0** | ❌ 抬 |
| `io.github.nadeemiqbal:liquid-glass` | 0.2.3 | Apache-2.0 | [2,3,0] | **24** | ui **1.10.5**（BOM+1 patch） | 🟡 几乎不抬 |
| `com.github.skydoves:cloudy` | 1.0.0-alpha01 | Apache-2.0 | [2,4,0] | 23 | 声明 ui **1.11.4** | ❌ 抬 |

**不存在**（勿写入构建脚本）：`com.attafitamim.glass:*`（404）、`com.mohamedrejeb.*` 的 glass 系列。

> **没有任何库因许可证或 Kotlin metadata 被排除** —— 真实存在的候选**全部 Apache-2.0**，
> 实测 `mv` 分布为 `[2,2,0]` / `[2,3,0]` / `[2,4,0]`，**无一超过 2.5**，Kotlin 2.4.21 全可读。

---

## 3. ★ 决定性发现：Compose 版本是二选一

### 实测：Haze 2.0.1 会把 Compose 从 1.10.4 顶到 1.12.1

| | 原工程（BOM 管理） | 加 `haze-glass:2.0.1` 后 |
|---|---|---|
| `androidx.compose.ui` | **1.10.4** | **1.12.1** |
| `androidx.compose.runtime` | **1.10.4** | **1.12.1** |

即 **BOM 2026.02.01 的 ui 轴被实质作废**，运行时用上一套从未在本工程验证过的 Compose。

### 实测：想「保 BOM + 用 Haze 2.x」做不到

在 `app/build.gradle.kts` 加 `strictly("1.10.4")` 约束后：

```
> Component is the target of multiple version constraints with conflicting requirements:
> Could not resolve androidx.compose.runtime:runtime:{strictly 1.10.4}.
BUILD FAILED
```

**结论：二者互斥，没有折中方案。**

---

## 4. ★ 实测：Haze 1.7.2 完全不冲击基线

```
+--- androidx.compose.ui:ui             -> 1.10.4    ← BOM 原值，未被抬升
+--- androidx.compose.ui:ui-graphics    -> 1.10.4
+--- androidx.compose.foundation:foundation -> 1.10.4
```

`haze:1.7.2` + `haze-materials:1.7.2`：`mv=[2,2,0]`、`minCompileSdk=1`、
只依赖 ui 1.10.0（≤BOM）与 material3 1.4.0（=BOM）→ **BOM / compileSdk / Kotlin 基线全部不动**。

---

## 5. ★ 实测：观感差异（有截图）

在试验工程里做**同形状、同尺寸、同底色**的并排对比（底面放彩色条纹，玻璃才有东西可折射）：

| | 表现 |
|---|---|
| **① 当前手写实现** | 胶囊边缘**溢出了**底部彩条（裁剪不精确），玻璃只是**整体均质模糊**，**没有边缘光学** |
| **② Haze `GlassStyle.regular`** | 胶囊**精确裁切**，边缘有**折射/收缩**，颜色更透亮 |
| **③ Haze `GlassStyle.clear`** | 最通透，折射最明显 |

截图：`D:\APPDATA\PCD\DSH\screenshots\haze-vs-handmade.png`

**Haze 确实做出了自研做不到的能力**：折射（refraction）、菲涅尔边缘提亮、
色散、光源位置、材质随尺寸自适应。

### Haze 2.x 还有的自研没有的能力（官方文档）

- **无障碍挂钩**：`LocalGlassAccessibilitySettings`（`reduceTransparency` / `increaseContrast` / `showBorders`）
- **自动降级**：简化渲染器会**自动省略折射/色散**但保留色调、形状、边缘 —— 无需写两套样式
- **交互响应**：`pressed { lightingIntensity / refractionMultiplier / whitePointDelta / scale }`
- **性能档位**：`HazePerformanceMode`，官方有 Benchmark 数据

> 官方明确声明：**「These are Haze styles informed by the platform distinction;
> they do not promise pixel parity with another system」**（不承诺与 iOS 像素级一致）。

---

## 6. 迁移成本（1.7.2 → 2.x）

官方有完整迁移指南。核心改动**很小**：

| Haze 1 | Haze 2 |
|---|---|
| `Modifier.haze(state)` | `Modifier.hazeSource(state)` |
| `Modifier.hazeEffect(state)` | `Modifier.hazeBlur(input, style)` |
| `HazeStyle { blurRadius = x }` | `HazeBlurStyle { blurRadius(x) }` |
| `haze-materials` | `haze-blur-materials` |

> **对本项目几乎无影响**：Haze 只会被封装在 `core/design/UbGlass.kt` **一个文件**内，
> 升级时只改那一处，调用方（Tab 栏、卡片）完全不动。

---

## 7. 建议（供决策）

| 方案 | 代价 | 收益 |
|---|---|---|
| **A. 维持自研现状** | 无 | 已经能用、零依赖、完全可控；但**做不出折射** |
| **B. 用 `haze:1.7.2` + `haze-materials:1.7.2`** | 加 2 个依赖；停在 2026-02 版本 | **零基线冲击**；真模糊 + Cupertino/Fluent 现成材质预设；**升级到 2.x 的路已铺好** |
| **C. 用 `haze-glass:2.0.1`** | **Compose 1.10.4 → 1.12.1，BOM 作废** | 最完整的液态玻璃：折射、色散、菲涅尔、无障碍、交互响应 |

**我的建议：C 的功能最强，但代价是把整套 Compose 换成本工程从未验证过的版本**，
风险面覆盖全部 8 类页面。建议**放到 P08「Liquid Glass 整合」专门做**，
那时可以整体升级 Compose、跑完整回归、量性能 —— 而不是现在顺手把一个基础依赖顶掉。

**若要在 P08 之前就拿到折射观感**，方案 B 是稳妥的中间步：
零基线冲击，且迁移到 2.x 的路径已明确。

---

## 8. 未能确认的部分（如实标注）

1. **androidx 侧 AAR 在本机是桩包**（只含 `AndroidManifest.xml` + `LICENSE.txt`，无 `classes.jar`）
   → 无法从元数据证明「Compose 1.12.1 + compileSdk 37.0」能否编译。
   **但本机已实测绕过此疑点**：Haze 2.0.1 在临时副本中**真实构建成功**，
   且装到 `emulator-5556` **运行正常、无崩溃、无类冲突**。
2. `haze-glass` 2.0.1 有 161 个 class，只抽样读了顶层签名，`GlassStyleScope` 字段**未逐项枚举**。
3. GitHub 仓库页未访问（DNS 劫持）→ **star / issue 活跃度 / README 均未核实**。
4. Maven Central 搜索 API 索引陈旧，**不能声称已穷尽枚举**全站玻璃库。
5. **未做性能测量** —— 软件渲染模拟器上的数据不具代表性（见 KNOWN_ISSUES ISSUE-006）。
6. 未评估在 **API < 31** 设备上的降级表现（无低版本镜像）。

---

## 9. 变更记录

| 日期 | 变更 |
|---|---|
| 2026-10-14 | 首次建立。Maven 实测 8 个坐标 + 真实构建/设备运行/截图对比 |
