package com.utilitybox.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * UtilityBox 色彩令牌。
 *
 * 依据：docs/ui-design.md §2.1
 * 原则：页面代码**不得**出现硬编码颜色，一律经 [UbColors] 取值。
 *
 * 命名约定：
 *   Bg*     背景层
 *   Text*   文字层级（Primary > Secondary > Tertiary 视觉权重递减）
 *   Accent  强调色（主操作/选中态）
 *   Status* 业务语义色（好友状态等）
 */
@Immutable
data class UbColors(
    /** 页面背景（浅色极浅灰白 / 深色近黑非纯黑） */
    val bgBase: Color,
    /** 卡片背景（与背景形成轻微明度差） */
    val bgElevated: Color,
    /** 悬浮/分组用的次级容器 */
    val bgSubtle: Color,
    /** 玻璃层底色（半透明） */
    val bgGlass: Color,
    /** 玻璃层描边（1dp 高光） */
    val glassStroke: Color,
    /** 玻璃材质顶部高光 —— 模拟 iOS 材质的镜面边缘 */
    val glassHighlight: Color,
    /**
     * 悬浮导航未选中项的图标/文字色。
     * 选中项用 [accent] 表达，未选中项用这个中性次级色 —— Apple 不画选中背景块。
     */
    val tabContent: Color,

    /** 选中药丸的填充（中性半透明浮层，与玻璃同源） */
    val tabPill: Color,

    /**
     * 玻璃**材质色调**（P09-补 深色缺陷修复）。
     *
     * ★ 为什么需要它：Haze 的 `GlassStyle.regular` 官方说明是按
     *   **iOS 浅色外观**校准的，**没有深色变体**。深色主题下它会采样浅色卡片
     *   并输出一块亮灰（实测 `#999A9D`），在深色界面上非常刺眼。
     *
     *   原生 iOS/Android 的材质都是**随主题切换**的（浅色偏白、深色偏黑），
     *   所以这里补一层与主题同向的色调，叠在玻璃之上。
     *   亮色 = 白色薄纱（提亮）；暗色 = 近黑薄纱（压暗）。
     */
    val glassTint: Color,
    /** [glassTint] 的不透明度 */
    val glassTintAlpha: Float,

    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    /** 反色文字（用于强调色底上） */
    val textOnAccent: Color,

    /**
     * 反色背景 —— 用于浮动提示气泡（toast）。
     *
     * ★ 浅色主题下气泡是深色、深色主题下气泡是浅色，
     *   这是 iOS 的一贯做法：气泡要**从背景里跳出来**，
     *   与页面同色系就会被当成普通卡片。
     */
    val bgInverse: Color,

    /** 反色背景上的文字色 */
    val textInverse: Color,

    val accent: Color,
    val accentSoft: Color,

    /** 好友状态语义色 —— ⚠️ 必须配合图标或文字，不可仅靠颜色区分（无障碍要求） */
    val statusInGame: Color,
    val statusOnline: Color,
    val statusOffline: Color,
    val statusUnknown: Color,

    val danger: Color,
    val warning: Color,
    val success: Color,

    val divider: Color,

    /** 是否深色主题（玻璃/模糊策略会用到） */
    val isDark: Boolean,
)

/**
 * 浅色主题。
 * 取色取向：大面积中性、单一强调色、克制。
 */
val UbLightColors = UbColors(
    bgBase = Color(0xFFF4F5F7),
    bgElevated = Color(0xFFFFFFFF),
    bgSubtle = Color(0xFFEDEFF2),
    // 玻璃层不透明度不可过低：它上面要承载图标与文字（P02 实测对比度不足后上调）
    bgGlass = Color(0xE6F0F1F4),
    // ★★ P11 补 12（用户反馈「这个全白的界面几乎看不出来玻璃效果，可以灰一些」）：
    //   原先描边是**白色 20%**、材质色调是**白色 10%** —— 在白底页面（设置、我的）
    //   上等于什么都没画，整条栏和背景糊在一起。
    //   浅色材质在浅色背景上**必须靠暗色描边与轻微灰调**来界定边界，
    //   这也是 Apple 的做法：浅色材质的那条 hairline 是**深色**的，不是白色高光。
    glassStroke = Color(0x14000000),
    glassHighlight = Color(0x99FFFFFF),
    /**
     * 悬浮导航**未选中项**的图标/文字色。
     *
     * ★ 选中态 = accent 色 + 内嵌药丸（P10 按参考图纠正）。
     *   P02 时我曾判定「iOS 没有选中背景块」并删掉了药丸 —— **那个判断是错的**。
     *   当时把「浅色背景下药丸对比度低」误当成「不存在药丸」。
     *   **低对比 ≠ 不存在**：药丸的立体感来自玻璃折射与描边，不靠明度差
     *   （参考图实测：选中区 `#ECECEC` vs 栏底 `#EAEAEA`，只差 2）。
     */
    tabContent = Color(0xFF8A9099),
    /**
     * 选中药丸的填充（P10）。
     *
     * ★ 用**中性半透明浮层**而不是 accent 实色填充：
     *   药丸是"玻璃上浮起的一层"，理应和玻璃同源（浅色偏白、深色偏黑）。
     *   用 accent 实色会让它变成一块彩色贴纸，与整体的玻璃语言割裂 ——
     *   强调色交给**图标与文字**表达就够了。
     */
    tabPill = Color(0xFFFFFFFF),
    // 浅色材质色调：**冷灰薄纱**。
    //   ★ P11 补 12 从"白色 10%"改过来 —— 见上面 glassStroke 的说明。
    //     白纱叠在白色的模糊结果上不产生任何可感知的差异；
    //     换成冷灰并提高不透明度后，栏才从白底页面里"浮"出来。
    glassTint = Color(0xFF8A93A3),
    glassTintAlpha = 0.16f,

    textPrimary = Color(0xFF111114),
    textSecondary = Color(0xFF5A5F66),
    textTertiary = Color(0xFF9AA0A8),
    textOnAccent = Color(0xFFFFFFFF),
    // 浅色主题下气泡用深色底 + 浅字（要能从页面里跳出来）
    bgInverse = Color(0xFF1C1F24),
    textInverse = Color(0xFFF7F8F9),

    accent = Color(0xFF2F6BFF),
    accentSoft = Color(0x1A2F6BFF),

    statusInGame = Color(0xFF12B886),
    statusOnline = Color(0xFF2F9E44),
    statusOffline = Color(0xFF9AA0A8),
    statusUnknown = Color(0xFFB0B6BE),

    danger = Color(0xFFE03131),
    warning = Color(0xFFF08C00),
    success = Color(0xFF2F9E44),

    divider = Color(0x14000000),
    isDark = false,
)

/**
 * 深色主题。
 * 注意：背景用近黑而非纯黑，避免 OLED 上的"断层感"，也更接近系统原生观感。
 */
val UbDarkColors = UbColors(
    bgBase = Color(0xFF0E0F12),
    bgElevated = Color(0xFF1A1C20),
    bgSubtle = Color(0xFF23262B),
    // 深色下同样要保证玻璃上的文字可读
    bgGlass = Color(0xE61A1C20),
    glassStroke = Color(0x1FFFFFFF),
    glassHighlight = Color(0x33FFFFFF),
    // 深色下未选中项用中灰，保证在玻璃上可读
    tabContent = Color(0xFF9AA1AA),
    // 深色下药丸用白色薄层（浮起感），而不是近黑（会看不见）
    tabPill = Color(0x26FFFFFF),
    // ★ 深色材质色调：近黑薄纱。
    //   Haze 的 regular 材质是按 iOS **浅色**外观校准的，没有深色变体；
    //   深色主题下它会采样浅色卡片并输出亮灰（实测 #999A9D），压暗后才是原生材质的观感。
    glassTint = Color(0xFF0A0B0D),
    glassTintAlpha = 0.55f,

    textPrimary = Color(0xFFF2F4F7),
    textSecondary = Color(0xFFA8AEB8),
    textTertiary = Color(0xFF6E757F),
    textOnAccent = Color(0xFFFFFFFF),
    // 深色主题下气泡反过来：浅色底 + 深字
    bgInverse = Color(0xFFE8EAED),
    textInverse = Color(0xFF15171A),

    accent = Color(0xFF5B8CFF),
    accentSoft = Color(0x2E5B8CFF),

    statusInGame = Color(0xFF20C997),
    statusOnline = Color(0xFF51CF66),
    statusOffline = Color(0xFF6E757F),
    statusUnknown = Color(0xFF565C65),

    danger = Color(0xFFFF6B6B),
    warning = Color(0xFFFFB84D),
    success = Color(0xFF51CF66),

    divider = Color(0x1FFFFFFF),
    isDark = true,
)

/** 供 Compose 树读取当前色彩令牌（页面用 LocalUbColors.current.xxx） */
val LocalUbColors = staticCompositionLocalOf { UbLightColors }

// ---------------------------------------------------------------------------
// 全局主题色（强调色）自定义
// ---------------------------------------------------------------------------

/**
 * 预设主题色。
 *
 * ★ [seed] 为 `null` 表示"跟随默认"—— 默认色**随深浅主题不同**
 *   （浅色 `#2F6BFF` / 深色 `#5B8CFF`），所以它不能被表达成一个固定色值。
 */
data class UbAccentPreset(
    val id: String,
    val label: String,
    val seed: Color?,
)

/**
 * 预设配色。用户先在这几个里选；不够再用色轮自选（用户要求）。
 *
 * ★★ 选色有一条**硬约束**，不是审美问题：
 *
 *   强调色在本应用里有两种用法，方向相反 ——
 *   ① **色块底**：上面画 `textOnAccent`（按钮、选中 chip）
 *   ② **白底上的文字/图标**：底部导航选中项、强调色标签
 *
 *   第 ② 种要求亮度 **≤ 0.183**（对白底 4.5:1），这一条最紧。
 *   第一版凭手感挑了琥珀 `#F59F00`（亮度 0.442）—— 它当色块底很好（深字 8.8:1），
 *   但当白底上的文字只有 **2.13:1**，等于"选中了却看不见字"。
 *   实测（Python 算 WCAG 亮度）后整批换成深一档的色，全部落在区间内。
 *
 * ★ 所以这里的每个色值都应当满足：`1.05 / (lum + 0.05) >= 4.5`。
 *   有一条单测钉住这件事 —— 以后加预设忘了这条约束会立刻失败。
 */
val UbAccentPresets: List<UbAccentPreset> = listOf(
    UbAccentPreset("default", "默认", null),
    UbAccentPreset("indigo", "靛蓝", Color(0xFF3B5BDB)),
    UbAccentPreset("violet", "紫罗兰", Color(0xFF7C4DFF)),
    UbAccentPreset("magenta", "品红", Color(0xFFC2255C)),
    UbAccentPreset("red", "朱红", Color(0xFFE03131)),
    UbAccentPreset("orange", "橙", Color(0xFFC2410C)),
    UbAccentPreset("gold", "金褐", Color(0xFF946800)),
    UbAccentPreset("green", "森绿", Color(0xFF1E7A2B)),
    UbAccentPreset("teal", "松绿", Color(0xFF087F5B)),
    UbAccentPreset("cyan", "青碧", Color(0xFF0B7285)),
    UbAccentPreset("graphite", "石墨", Color(0xFF6B7280)),
)

/** 把 `Color` 压成 `0xAARRGGBB`，用于落盘（避免依赖 android.graphics 的 toArgb） */
fun Color.toArgbLong(): Long {
    fun c(v: Float): Long = (v.coerceIn(0f, 1f) * 255f + 0.5f).toLong()
    return (c(alpha) shl 24) or (c(red) shl 16) or (c(green) shl 8) or c(blue)
}

/** [toArgbLong] 的逆运算 */
fun ubColorOfArgb(argb: Long): Color = Color(
    red = ((argb shr 16) and 0xFF).toInt(),
    green = ((argb shr 8) and 0xFF).toInt(),
    blue = (argb and 0xFF).toInt(),
    alpha = ((argb shr 24) and 0xFF).toInt(),
)

/**
 * 纯 Kotlin 的 RGB↔HSV。
 *
 * ★ 为什么不用 `android.graphics.Color.RGBToHSV`：
 *   那个是 Android 平台方法，**JVM 单元测试里是空实现**，
 *   于是"主题色派生"这条逻辑就只能靠人眼看，没法回归。
 *   自己写 20 行，换来的是它可以被单测钉住。
 */
internal object UbHsv {

    /** @return `[h(0..360), s(0..1), v(0..1)]` */
    fun fromRgb(r: Float, g: Float, b: Float): FloatArray {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        val h = when {
            d < 1e-6f -> 0f
            max == r -> 60f * (((g - b) / d) % 6f)
            max == g -> 60f * (((b - r) / d) + 2f)
            else -> 60f * (((r - g) / d) + 4f)
        }
        return floatArrayOf(if (h < 0f) h + 360f else h, if (max <= 0f) 0f else d / max, max)
    }

    /** @return `[r, g, b]`，各 0..1 */
    fun toRgb(h: Float, s: Float, v: Float): FloatArray {
        val hh = ((h % 360f) + 360f) % 360f / 60f
        val c = v * s
        val x = c * (1f - kotlin.math.abs((hh % 2f) - 1f))
        val m = v - c
        val (r1, g1, b1) = when (hh.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return floatArrayOf(r1 + m, g1 + m, b1 + m)
    }
}

/** WCAG 相对亮度（0=黑，1=白） */
fun ubLuminance(r: Float, g: Float, b: Float): Float {
    fun lin(c: Float): Float =
        if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    return 0.2126f * lin(r) + 0.7152f * lin(g) + 0.0722f * lin(b)
}

/**
 * 把用户选的颜色**整形**成当前主题下可用的强调色。
 *
 * ### 设计取向：最小干预
 *
 * 第一版按 HSV 的 `value` 把明度夹进固定区间，结果**连应用自带的默认蓝都被改暗了**
 * （`#2F6BFF` 的 v=1.0，被夹到 0.62 后明显发闷）——
 * 预设色块整排都变得灰扑扑的，是模拟器截图一眼看出来的。
 *
 * 三个认知错误，逐条修掉：
 *
 * 1. **`value` 不是感知亮度**。HSV 的 v 是最大通道值，对高饱和色严重高估亮度。
 *    判断"能不能看清"必须用 WCAG 相对亮度。
 * 2. **不该无差别地夹**。绝大多数颜色本来就可用，整形只应处理**真的不可读**的极端色。
 * 3. ★ **只缩放 `value` 提不亮高饱和色**：`#3B5BDB` 在饱和度 0.73 下，
 *    把 value 拉到 1.0 也只有亮度 0.178 —— 而深色主题要求 ≥ 0.28。
 *    所以改为**朝黑/白混合**：变暗往黑走、变亮往白走。这条路径永远可达
 *    （纯黑亮度 0、纯白亮度 1），且色相基本不变。
 */
fun ubAccentFor(seed: Color, dark: Boolean): Color {
    val base = Color(seed.red, seed.green, seed.blue, 1f)
    val l = ubLuminance(base.red, base.green, base.blue)

    // ★ 边界直接取**应用自带强调色的亮度**，不写魔法数字：
    //   `#2F6BFF` 本身就是按"白底 4.5:1"校准的，所以"不比它更亮"就是可用性边界。
    //   自带色因此**天然原样通过**，不会出现"校准过的默认色反被整形改掉"这种荒唐事。
    //   以后有人调整自带色，边界会跟着走，不会与它脱节。
    val maxLum = if (dark) 0.60f else ubLuminance(
        UbLightColors.accent.red, UbLightColors.accent.green, UbLightColors.accent.blue,
    )
    val minLum = if (dark) ubLuminance(
        UbDarkColors.accent.red, UbDarkColors.accent.green, UbDarkColors.accent.blue,
    ) else 0.03f

    // ★ 绝大多数颜色走这一支：原样返回
    if (l in minLum..maxLum) return base

    val darken = l > maxLum
    val target = if (darken) maxLum else minLum
    val anchor = if (darken) Color.Black else Color.White

    fun blend(t: Float) = Color(
        red = base.red + (anchor.red - base.red) * t,
        green = base.green + (anchor.green - base.green) * t,
        blue = base.blue + (anchor.blue - base.blue) * t,
        alpha = 1f,
    )

    // 亮度关于混合比例单调 → 二分；答案取"已经修正到位"的那一侧
    var lo = 0f
    var hi = 1f
    repeat(24) {
        val mid = (lo + hi) / 2f
        val c = blend(mid)
        val cl = ubLuminance(c.red, c.green, c.blue)
        val stillUncorrected = if (darken) cl > target else cl < target
        if (stillUncorrected) lo = mid else hi = mid
    }
    return blend(hi)
}

/**
 * 强调色底上的文字色。
 *
 * ★ 必须自动决定黑白：用户可能选亮黄当强调色，
 *   此时白字对比度只有 1.1:1 —— 完全读不出来。
 *
 * ★★ 第一版用的是"亮度 > 0.179 就走深字"这个**理论交叉点**，被单测抓到不够：
 *   那个 0.179 是「纯黑 vs 纯白」的交叉点，而我们的深字是 `#111114`（不是纯黑，
 *   亮度 0.0057），真实交叉点在 **0.192**。于是亮度 0.183 的强调色会走到深字那支，
 *   只有 4.18:1，而白字本来能给到 4.51:1。
 *
 *   现在直接**把两个对比度都算出来比大小** —— 没有阈值就不会有阈值错。
 */
fun ubOnAccent(accent: Color): Color {
    val onDark = Color(0xFF111114)
    val onLight = Color(0xFFFFFFFF)
    fun contrast(a: Color, b: Color): Float {
        val la = ubLuminance(a.red, a.green, a.blue)
        val lb = ubLuminance(b.red, b.green, b.blue)
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }
    return if (contrast(accent, onDark) > contrast(accent, onLight)) onDark else onLight
}

/**
 * 用用户选的种子色覆盖强调色相关令牌。
 *
 * [seed] 为 `null` 时**原样返回**（跟随主题默认色）——
 * 这正是 `UbSettings.accentSeed` 用 `null` 表达"默认"的意义：
 * 恢复默认后深浅主题各自回到自己那支颜色。
 */
fun UbColors.withAccent(seed: Color?): UbColors {
    if (seed == null) return this
    val accent = ubAccentFor(seed, isDark)
    return copy(
        accent = accent,
        // 软色底：深色下要更实一点才看得见（与内置令牌同取向）
        accentSoft = accent.copy(alpha = if (isDark) 0.22f else 0.12f),
        textOnAccent = ubOnAccent(accent),
    )
}
