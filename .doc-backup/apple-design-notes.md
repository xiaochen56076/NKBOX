# Apple 设计规范落地记录（Tab 栏 / Liquid Glass）

> 建立：2026-10-14（P02 复盘）
> **本文件的作用**：把「苹果到底怎么做的」固化成可查证的依据，**取代凭感觉调参**。
>
> 起因：P02 第一版 Tab 栏被用户判定「不好看，还是要去看看苹果的设计模式」。
> 复盘发现我的实现有 **5 处系统性偏离苹果规范**，且其中一处（选中项背景药丸）
> **完全是我自创的，iOS 并无此设计**。教训：**做「像苹果」的 UI 前必须查官方规范，不能凭印象。**

---

## 1. 一手依据来源（可复查）

| 来源 | 链接 | 可用性 |
|---|---|---|
| WWDC25 · Build a UIKit app with the new design | https://developer.apple.com/videos/play/wwdc2025/284 | ✅ 文字稿可抓取 |
| WWDC25 · Get to know the new design system | https://developer.apple.com/videos/play/wwdc2025/356 | ✅ 文字稿可抓取 |
| WWDC25 · Meet Liquid Glass | https://developer.apple.com/videos/play/wwdc2025/219 | ✅ 文字稿可抓取 |
| HIG · Tab bars | https://developer.apple.com/design/human-interface-guidelines/tab-bars | ❌ **JS 渲染，抓不到正文** |
| HIG · Materials | https://developer.apple.com/design/human-interface-guidelines/materials | ❌ 同上 |
| Adopting Liquid Glass | https://developer.apple.com/documentation/TechnologyOverviews/adopting-liquid-glass | ❌ 同上 |

> **经验**：Apple 的 HIG 是 SPA，`web_fetch` 只能拿到标题。
> **要读官方设计规范，走 `/videos/play/wwdcYYYY/NNN` 的文字稿**——那是服务端渲染的。

---

## 2. 官方原文摘录（Tab 栏与形状）

### 2.1 Tab 栏形态

> "With the new design, **the tab bar on iPhone floats above the content**, and can be configured
> to minimize on scroll, keeping the focus on your content."
> — WWDC25/284

→ **悬浮于内容之上**，是 iOS 26 的既定形态。我们采用悬浮设计是正确的。

### 2.2 三种形状类型（关键）

> "We use **three shape types** to build concentric layouts: **fixed shapes** have a constant
> corner radius. **Capsules use a radius that's half the height of the container.** And
> **concentric shapes calculate their radius by subtracting padding from the parent's**."
> — WWDC25/356

> "The capsule's geometry naturally supports concentricity. Which is why you'll see it
> throughout the system—in the mirrored proportions of sliders and switches, and echoed in
> **bars**, buttons, and the rounded corners of grouped table views."
> — WWDC25/356

→ **胶囊是 Apple 最标志性的形状**（滑块、开关、bar 都在用）。
→ 内层圆角必须由父级圆角**减去 padding** 得出（concentric），不是各自拍一个数。

### 2.3 光学居中

> "to preserve optical balance, views are **mathematically centered when it makes sense—and
> subtly offset when it doesn't**."
> — WWDC25/356

### 2.4 Liquid Glass 的材质定位

> "By sculpting light like this, controls can feel **ultra lightweight and transparent** while
> still being visually distinguishable."
> — WWDC25/219

> "Liquid Glass uses these instinctive visual cues to provide separation and **communicate
> layering** in a new way while letting content shine through underneath it."
> — WWDC25/219

### 2.5 材质对交互的响应

> "Liquid Glass **responds to interaction by instantly flexing and energizing with light**."
> — WWDC25/219

> "And it has an inherent **gel-like flexibility** to it that communicates its transient and
> malleable nature, as it moves in tandem with your interaction."
> — WWDC25/219

→ 材质必须在**按压时给出反馈**（我们实现为轻微缩放 0.92）。

---

## 3. 我的 5 处偏离与修正对照

| # | 官方依据 | 修正前（错误） | 修正后 |
|---|---|---|---|
| 1 | Capsule = 半径取容器高度一半 | 固定 26dp 圆角 | `UbRadius.capsule = 999.dp`，Compose 自动取半高 |
| 2 | Concentric = 父级圆角 − padding | 内层圆角与父级无关 | `UbRadius.concentric(parent, padding)` 辅助函数 |
| 3 | 材质应 **ultra lightweight and transparent** | 底色不透明度 **0.72** + 模糊 26dp（厚白卡观感） | **0.52** + 模糊 22dp |
| 4 | 材质需 **flexing on interaction** | 无按压反馈 | 按压缩放 0.92（`animateFloatAsState` + `motion.press`） |
| 5 | iOS 选中态 = **accent 色调**（无背景块） | **自创的不透明灰色药丸衬底** | 删掉药丸；改为 accent 色 + `SemiBold` 字重 |

**第 5 条是最大教训**：我花了两轮迭代去调那块药丸的宽度、位置、颜色，
而**苹果根本不画这块背景**。凭印象做设计会浪费大量精力在错误的方向上。

### 附带修正

| # | 问题 | 修正 |
|---|---|---|
| 6 | 未选中项隐藏文字标签（我照猫创 App 参考图做的） | **iOS 标签栏始终显示全部标签** → 改为全部显示 |

---

## 4. 落地位置

| 规范 | 代码位置 |
|---|---|
| 胶囊 / 同心圆角令牌 | `core/design/UbDimens.kt` — `UbRadius.capsule` / `UbRadius.concentric()` |
| 材质变体与不透明度 | `core/design/UbGlass.kt` — `UbGlassStyle.Regular/Clear` |
| 顶部高光 | `core/design/UbGlass.kt` — `glassHighlight` 垂直渐变层 |
| 悬浮导航 | `core/design/UbBottomBar.kt` |
| Tab 内容色 | `core/design/UbColor.kt` — `tabContent` |
| **底部渐隐** | `MainActivity.kt` 的 `UbRootShell` — `bottomBarReserved` 高度的垂直渐变 |

### 为什么需要底部渐隐

Apple 的表述是「tab bar **floats above the content**」——内容在栏下**继续**。
如果只是把栏浮上去而不做处理，列表最后一项会被**硬生生切断**，观感廉价。
正确的做法是让内容在接近底部时**渐变隐入背景**，再由材质覆盖。

实测对比：加渐隐前，设置页最后一项「版本与开源许可」被导航压住、半截可见；
加渐隐后该项完整显示在导航上方，过渡自然。

---

## 5. 仍需遵守的红线（规范之外的自身约束）

| 红线 | 说明 |
|---|---|
| **可读性优先于通透** | 官方也说 Clear 材质需自行加压暗层。玻璃之上承载文字必须可读 |
| **玻璃必须可独立关闭** | 设计稿硬性要求；降级为不透明材质，功能与可读性不退化 |
| **状态不可仅靠颜色** | 无障碍要求：选中态除颜色外还有**字重**差异 |
| **所有可点元素需内容描述** | `contentDescription`；否则未选中项在 TalkBack 与 `uiautomator` 中不可见（实测踩到） |
| **减少动态效果要生效** | 按压与转场动画时长归零 |

---

## 6. 变更记录

| 日期 | 变更 |
|---|---|
| 2026-10-14 | 首次建立。依据用户「tab 栏不好看」的反馈，查证 WWDC25 三篇文字稿，修正 6 处偏离 |

---

## ★ 深色材质缺陷（P09-补，用户报告）

**用户报告**：「深色模式下，tab 栏为什么显示为灰白色」。

### 量化确认（不是主观观感）

| 场景 | 底部栏像素 | 页面背景 | 判定 |
|---|---|---|---|
| 深色 · 首页 | `#0E0F12` | `#0E0F12` | 玻璃完全不可见 |
| **深色 · 设置页** | **`#999A9D`** | `#1A1C20` | **亮灰色块 —— 确认为真缺陷** |

### 根因

Haze 的 `GlassStyle.regular` 官方说明其光色是
**「calibrated against native iOS 27 light appearance」** ——
**按浅色外观校准，没有深色变体**。深色主题下它会采样页面里的浅色卡片，输出一块亮灰。

> Haze 只承诺「informed by the platform distinction」，**不承诺与 iOS 像素级一致**，
> 也没有承诺深色下的表现。
> **「库没有对应能力」不等于「问题不存在」** —— 用户看得见，就得修。

### 修法

原生 iOS/Android 的材质都**随主题切换**（浅色偏白、深色偏黑），
所以按同一思路补一层**与主题同向的材质色调**，画在玻璃之上：

| 主题 | 色调 | 不透明度 |
|---|---|---|
| 浅色 | 白 `#FFFFFF` | 0.28 |
| 深色 | 近黑 `#0A0B0D` | 0.80 |

改在 `UbGlassSurface` 里（所有玻璃的唯一入口），**调用方零改动**，
将来换玻璃实现也只改这一个文件。

### 验证（A/B，切换系统主题）

| 系统主题 | 底部栏 | 页面背景 | 判定 |
|---|---|---|---|
| 深色 | `#0B0C0E` | `#0E0F12` | ✅ 融合 |
| 浅色 | `#F7F8F9` | `#F4F5F7` | ✅ 明亮，未被误压暗 |

### 教训（4 条，都硬）

1. **「用了现成库」不等于「各主题下都对」。** 库的能力边界（这里：只校准了浅色）
   就是你的缺陷边界。
2. **深色缺陷在浅色环境下测不出来。** 我此前只在"系统浅色 + 应用内选深色"下验证，
   漏掉了**「系统深色 + 跟随系统」这条最常见的路径**。
3. **默认值要选不易出错的那个。** 主题默认原为写死「浅色」——
   系统处于深色的用户第一次打开会看到亮色界面，第一反应是"这应用坏了"。
   已改为**跟随系统**。
4. **量化胜于观感。** 用像素采样把"看着有点怪"变成
   `#999A9D vs #1A1C20` 的硬证据，才能确认是真缺陷而非感觉问题。

### 附：一次失败的自查（教训同样值得记）

排查过程中我一度用 `IndexOf("实验性")` 定位节点，**子串命中了
「2 个可用 · 1 个实验性 · 1 个开发中」里的「实验性」**，导致正则截取偏移，
凭空造出一个 y=2371 的假节点，于是追了半天的"幽灵透出"。

**教训**：用文本在结构化数据里定位时，**必须用带边界的完整属性匹配**
（`\stext="..."`），不能用无边界子串搜索。
否则工具会**制造**出你要找的 bug。

---

## ★ P10：底部导航按用户参考图重做

用户提供了 5 张 iOS 参考图，要求「tab 栏和这种风格一样」。

### 提取的规格

```
┌──────────────────────────┐   ╭───╮
│  ╭─────╮                 │   │ ○ │   ← 独立圆形动作按钮
│  │ 首页 │   设置          │   ╰───╯
│  ╰─────╯                 │
└──────────────────────────┘
   ↑ 胶囊容器（玻璃）  ↑ 选中项 = 内嵌药丸
```

### ★★ 纠正 P02 的一个错误判断（本文件上方那段旧结论已作废）

本文件先前写着：

> 「iOS 选中态 = accent 色调（**无背景块**）」
> 「**自创的不透明灰色药丸** → 删除药丸」

**这是错的。** 参考图里选中项明确有药丸。

**错误成因**：把「浅色背景下药丸对比度低」误当成「不存在药丸」。
**低对比 ≠ 不存在** —— 药丸的立体感来自玻璃折射与描边，不靠明度差：

| 采样点 | 像素 |
|---|---|
| 参考图 · 选中区 | `#ECECEC` |
| 参考图 · 栏底 | `#EAEAEA` |
| **差值** | **仅 2** |

> **真正该记住的教训**：我曾用一条**没有实测支撑**的"官方依据"
> 把正确的设计删掉了，还把它写进注释当结论，措辞相当自信
> （"iOS 并不这么做"）。
> **自信的措辞不等于正确的判断。** 涉及"官方怎么做"的结论，
> 要么有可核对的原文，要么有截图证据，否则不该写进代码注释。

### 实现要点

1. **药丸圆角按药丸自身高度算**：`capsule` 是 999dp 哨兵值，
   `999 - 5` 仍是 999，同心关系表达不出来。
2. **圆钮尺寸要同时给玻璃层与内容层**：否则玻璃层按父级约束塌成椭圆。
3. **药丸用中性半透明浮层**，不用 accent 实色 —— 它是"玻璃上浮起的一层"。

### 实测像素（双主题）

| 主题 | 选中药丸 | 未选中区 | 页面背景 |
|---|---|---|---|
| 浅色 | `#FFFFFF` | `#F7F8F9` | `#F4F5F7` |
| 深色 | `#2F3032` | `#0B0C0E` | `#0E0F12` |

圆钮节点 168×168 px = 正圆（64dp × 2.625）。

### 另一次自查失误（教训同样值得记）

排查时我在**2 倍放大的低分辨率裁剪图**上判断"圆钮是竖椭圆"，
并据此改了代码。实际上节点一直是 168×168 的**正圆**。

**教训**：放大低分辨率图会产生视觉假象。**判断几何问题要用节点 bounds，
不是用眼睛看放大图。**