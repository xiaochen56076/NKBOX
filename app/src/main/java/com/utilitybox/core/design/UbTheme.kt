package com.utilitybox.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier

/**
 * 主题模式（依据 PROJECT_RULES.md 第一节：浅色 / 深色 / 跟随系统）。
 * P02 阶段先支持显式切换；"跟随系统"的读取在 P09 接入设置持久化时补完。
 */
enum class UbThemeMode { Light, Dark, System }

/**
 * UtilityBox 主题入口。
 *
 * 所有令牌经 CompositionLocal 下发，页面与组件一律通过 [LocalUbColors] / [LocalUbSpacing] /
 * [LocalUbRadius] / [LocalUbSize] / [LocalUbMotion] / [LocalUbTypography] 读取，
 * 从而保证：换主题只改一处、页面内不出现魔法数值。
 */
@Composable
fun UbTheme(
    mode: UbThemeMode = UbThemeMode.Light,
    reduceMotion: Boolean = false,
    glassEnabled: Boolean = true,
    /** 全局主题色种子；`null` = 跟随主题默认色（见 [UbColors.withAccent]） */
    accentSeed: androidx.compose.ui.graphics.Color? = null,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        UbThemeMode.Light -> false
        UbThemeMode.Dark -> true
        UbThemeMode.System -> androidx.compose.foundation.isSystemInDarkTheme()
    }

    // ★ 强调色在**主题底色之后**覆盖：整形要按当前深浅决定明度方向
    //   （浅色主题压暗、深色主题提亮），所以必须先知道 dark 才能算。
    val colors = (if (dark) UbDarkColors else UbLightColors).withAccent(accentSeed)
    val spacing = UbSpacing()
    val radius = UbRadius()
    val size = UbSize()
    // "减少动态效果"开启时把时长压到 0，组件无需各自判断。
    // ⚠️ 必须用 UbMotion.reduced()，**不能**写成 UbMotion(0, 0, 0, 0) ——
    //    那是按位置传前四个参数，新增的 enter/enterDelay 会保留默认非零值，
    //    于是无障碍开关打开后入场动画照放（P11 实测发现并修掉）。
    val motion = if (reduceMotion) UbMotion.reduced() else UbMotion()
    val type = UbDefaultTypography

    CompositionLocalProvider(
        LocalUbColors provides colors,
        LocalUbSpacing provides spacing,
        LocalUbRadius provides radius,
        LocalUbSize provides size,
        LocalUbMotion provides motion,
        LocalUbTypography provides type,
        LocalUbTokens provides UbTokens(colors, spacing, radius, size, motion, type),
        LocalUbReduceMotion provides reduceMotion,
        LocalUbGlassEnabled provides glassEnabled,
    ) {
        Box(modifier = Modifier.fillMaxSize().background(colors.bgBase)) {
            content()
        }
    }
}

val LocalUbReduceMotion = androidx.compose.runtime.staticCompositionLocalOf { false }
val LocalUbGlassEnabled = androidx.compose.runtime.staticCompositionLocalOf { true }
