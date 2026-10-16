package com.utilitybox.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 图标集 —— **Lucide Icons**（P11 补 27 迁移）。
 *
 * ### 以前是手绘的，为什么换掉
 *
 * 迁移前这里是 23 个手绘 Canvas 路径。它们能看出是"线框图标"，
 * 但**形状是我自己描的** —— 描边粗细、圆角、留白都没有权威依据，
 * 凑近看就是不如成熟图标集规整。用户因此指定统一采用 Lucide。
 *
 * ### 为什么走 VectorDrawable 而不是图标包
 *
 * 本工程**一直用 `--offline` 构建**，本地 Gradle 缓存里没有任何 Lucide 的
 * Compose 封装（`com.composables` / `compose-icons` / `devsrsouza` 都没有），
 * 加 Maven 依赖会**直接构建失败**。
 *
 * 而 VectorDrawable 有个决定性优势：**`android:pathData` 与 SVG 的 `d`
 * 语法完全一致** —— 从官方 SVG 转换**不需要写路径解析器**，
 * 也就不会在转换中悄悄改掉图形。
 *
 * ### 只用到的图标才打包
 *
 * 与 [UbIcon] 的枚举项一一对应，一个多余都没有（用户要求 6）：
 *
 * | 来源 | 数量 | 资源 |
 * |---|---|---|
 * | Lucide | **21** | `ic_lucide_*.xml` |
 * | **本项目自绘** | **2** | `ic_game_deltaforce.xml` / `ic_game_wuwa.xml` |
 *
 * ★ 那 2 个自绘的是**游戏专属标识**，不是 Lucide —— 理由见 [UbIcon.Game] 的注释：
 *   通用图标说明不了是"哪一个"游戏，等于占位图。它们按 Lucide 规格绘制
 *   （24 视口 / 线宽 2 / 圆头端点），所以放在同排不会显得是两种风格。
 *
 * ### 颜色如何跟随主题
 *
 * 生成的 XML 里 `strokeColor` 是黑色占位，实际颜色由 [Icon] 传给
 * `material3.Icon(tint = ...)` 的 **ColorFilter 覆盖** ——
 * 所以主题色与语义色（危险 / 警告 / 成功）自动跟随，不需要为每个主题各生成一份图标。
 *
 * ★ 源：`lucide-static` **1.57.0**（固定版本，未用 main 分支）
 * ★ 许可证：**ISC**，全文与核实过程见项目根目录 `LICENSES.md` 第六节。
 *    ISC 要求在副本中保留版权声明 —— 每个生成的 XML 头部都带了版权行。
 */
object UbIcons {

    /**
     * 图标渲染。
     *
     * ★ 必须写全限定名 `androidx.compose.material3.Icon`：
     *   本对象叫 `UbIcons`、这个函数叫 `Icon`，直接写 `Icon(...)` 会**递归调用自己**。
     */
    @Composable
    fun Icon(
        icon: UbIcon,
        modifier: Modifier = Modifier,
        tint: Color,
        size: Dp = 24.dp,
    ) {
        androidx.compose.material3.Icon(
            painter = androidx.compose.ui.res.painterResource(drawableOf(icon)),
            contentDescription = null,
            tint = tint,
            modifier = modifier.size(size),
        )
    }

    /**
     * `UbIcon` → 图标资源。
     *
     * ★ **全 App 只有这一处映射**。好处有两个：
     *   1. 以后要换图标集，只改这一个函数，**40 多处调用点一行都不用动**；
     *   2. 业务代码里写的是**语义**（`UbIcon.Clock`）而不是"某个库的图标名"，
     *      所以换库不会污染业务代码。
     */
    private fun drawableOf(icon: UbIcon): Int = when (icon) {
        UbIcon.Home -> com.utilitybox.R.drawable.ic_lucide_home
        UbIcon.Grid -> com.utilitybox.R.drawable.ic_lucide_grid
        UbIcon.Gear -> com.utilitybox.R.drawable.ic_lucide_gear
        UbIcon.Key -> com.utilitybox.R.drawable.ic_lucide_key
        UbIcon.Tag -> com.utilitybox.R.drawable.ic_lucide_tag
        UbIcon.User -> com.utilitybox.R.drawable.ic_lucide_user
        UbIcon.ChevronRight -> com.utilitybox.R.drawable.ic_lucide_chevron_right
        UbIcon.ChevronLeft -> com.utilitybox.R.drawable.ic_lucide_chevron_left
        UbIcon.Copy -> com.utilitybox.R.drawable.ic_lucide_copy
        UbIcon.Search -> com.utilitybox.R.drawable.ic_lucide_search
        UbIcon.Refresh -> com.utilitybox.R.drawable.ic_lucide_refresh
        UbIcon.Warning -> com.utilitybox.R.drawable.ic_lucide_warning
        UbIcon.Info -> com.utilitybox.R.drawable.ic_lucide_info
        UbIcon.Empty -> com.utilitybox.R.drawable.ic_lucide_empty
        UbIcon.Check -> com.utilitybox.R.drawable.ic_lucide_check
        UbIcon.Clock -> com.utilitybox.R.drawable.ic_lucide_clock
        UbIcon.Flask -> com.utilitybox.R.drawable.ic_lucide_flask
        // ★ 这两个**不是** Lucide，是本项目自绘的游戏标识（见 XML 内的设计说明）
        UbIcon.Game -> com.utilitybox.R.drawable.ic_game_deltaforce
        UbIcon.Wave -> com.utilitybox.R.drawable.ic_game_wuwa
        UbIcon.Palette -> com.utilitybox.R.drawable.ic_lucide_palette
        UbIcon.Sliders -> com.utilitybox.R.drawable.ic_lucide_sliders
        UbIcon.Database -> com.utilitybox.R.drawable.ic_lucide_database
        UbIcon.Power -> com.utilitybox.R.drawable.ic_lucide_power
    }
}

/**
 * 图标标识。
 *
 * ★ 这是**语义枚举**，不是"Lucide 图标名列表" ——
 *   业务代码依赖它，所以换图标集时它不需要改，只改 `UbIcons.drawableOf`。
 *   新增图标时在此扩展，并在 `drawableOf` 里加一行映射，
 *   同时把对应的 `ic_lucide_*.xml` 放进 `res/drawable/`。
 */
enum class UbIcon {
    Home, Grid, Gear, Key, Tag, User,
    ChevronRight, ChevronLeft,
    Copy, Search, Refresh,
    Warning, Info, Empty, Check, Clock, Flask,

    /**
     * 「三角洲行动」的标识（**自绘 Δ**，不是 Lucide）。
     *
     * ★ 原先用的是 Lucide `gamepad-2`（手柄）—— 那是**所有游戏**的通用符号，
     *   说明不了是哪一个游戏，等于占位图。用户 P11 补 32 要求换成游戏专属标识。
     */
    Game,

    /**
     * 「鸣潮」的标识（**自绘 浪 + 潮头**，不是 Lucide）。
     *
     * ★ 原先用的是 Lucide `waves`（三条等宽波浪）—— 同样是通用符号。
     *   现在是「一道浪 + 浪尖上的锐角潮头」，取自「鸣（声）+ 潮（浪）」。
     */
    Wave,

    /** 外观设置（调色板） */
    Palette,

    /**
     * 交互偏好（滑块）。
     *
     * ★ 为什么专门加这个图标而不是拿 Refresh / Gear 凑：
     *   「交互」是**偏好设置**，滑块（Material 的 Tune）才是这个语义的通用符号。
     *   刷新图标会让人以为点了会重新加载；齿轮会和「设置」本身重复。
     *   **图标语义错了比图标丑更糟** —— 用户会按图标而不是按文字去理解。
     */
    Sliders,

    /** 数据设置（数据库） */
    Database,

    /** 电源 / 退出登录 */
    Power,
}

/** 便捷包装：把图标放进一个居中的圆角方框（工具卡片常用） */
@Composable
fun UbIconBox(
    icon: UbIcon,
    tint: Color,
    background: Color,
    modifier: Modifier = Modifier,
    boxSize: Dp = LocalUbSize.current.iconBox,
    iconSize: Dp = LocalUbSize.current.icon,
) {
    Box(
        modifier = modifier
            .size(boxSize)
            .clip(RoundedCornerShape(LocalUbRadius.current.button))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        UbIcons.Icon(icon = icon, tint = tint, size = iconSize)
    }
}
