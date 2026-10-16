package com.utilitybox.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * 字体层级令牌（依据 docs/ui-design.md §2.2）。
 *
 * 中文排版注意：CJK 字形在默认字体下的行高与西文差异明显，
 * 故统一给出较大的 lineHeight，并把行高对齐策略设为 CapToCap 以外的常规方式，
 * 避免中文出现"上紧下松"的观感问题。具体观感需在 P02 渲染后由人眼确认。
 */
@Immutable
data class UbTypography(
    /** 首页大标题 / 密码大字 */
    val displayLarge: TextStyle,
    /**
     * 二级页标题（比 [displayLarge] 小一档）。
     *
     * ★ 为什么需要它：二级页标题旁边有返回按钮，沿用 30sp 的 [displayLarge]
     *   在窄屏上会与返回键挤在一起；而 [titleLarge]（22sp）又太像"区块标题"，
     *   与内容分不开层级。26sp 是这两者之间的合适档位。
     */
    val displaySmall: TextStyle,
    /** 页面标题 */
    val titleLarge: TextStyle,
    /** 卡片标题 */
    val titleMedium: TextStyle,
    /** 正文（列表项主文案） */
    val bodyLarge: TextStyle,
    /** 正文次级 */
    val bodyMedium: TextStyle,
    /** 标签 / 分类 */
    val labelMedium: TextStyle,
    /** 时间戳 / 来源 / 免责声明 */
    val caption: TextStyle,
    /** 数据值（价格、密码等需要强调的数字） */
    val numericLarge: TextStyle,
    val numericMedium: TextStyle,
)

private val uiLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun cjk(
    size: Int,
    weight: FontWeight = FontWeight.Normal,
    lineHeight: Int = (size * 1.45f).toInt(),
    letterSpacing: Float = 0f,
) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
    lineHeightStyle = uiLineHeightStyle,
)

val UbDefaultTypography = UbTypography(
    displayLarge = cjk(30, FontWeight.Bold, lineHeight = 38, letterSpacing = -0.4f),
    displaySmall = cjk(26, FontWeight.Bold, lineHeight = 34, letterSpacing = -0.3f),
    titleLarge = cjk(22, FontWeight.SemiBold, lineHeight = 30),
    titleMedium = cjk(17, FontWeight.SemiBold, lineHeight = 24),
    bodyLarge = cjk(15, FontWeight.Normal, lineHeight = 22),
    bodyMedium = cjk(14, FontWeight.Normal, lineHeight = 21),
    labelMedium = cjk(12, FontWeight.Medium, lineHeight = 17, letterSpacing = 0.2f),
    caption = cjk(12, FontWeight.Normal, lineHeight = 17),
    numericLarge = cjk(32, FontWeight.Bold, lineHeight = 38, letterSpacing = -0.5f),
    numericMedium = cjk(18, FontWeight.SemiBold, lineHeight = 24),
)

val LocalUbTypography = staticCompositionLocalOf { UbDefaultTypography }

/** 便捷取值：供组件内部使用，减少重复的 LocalXxx.current 样板 */
@Immutable
data class UbTokens(
    val colors: UbColors,
    val spacing: UbSpacing,
    val radius: UbRadius,
    val size: UbSize,
    val motion: UbMotion,
    val type: UbTypography,
)

val LocalUbTokens = staticCompositionLocalOf {
    UbTokens(
        colors = UbLightColors,
        spacing = UbSpacing(),
        radius = UbRadius(),
        size = UbSize(),
        motion = UbMotion(),
        type = UbDefaultTypography,
    )
}
