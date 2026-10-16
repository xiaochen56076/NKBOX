package com.utilitybox.core.design

import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 间距令牌。基准栅格 4dp（依据 docs/ui-design.md §2.4）。
 * 页面代码不得出现裸 dp 数值，一律经 [UbSpacing] 取值。
 */
@Immutable
data class UbSpacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 12.dp,
    val l: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    /** 页面左右安全边距 */
    val screenH: Dp = 20.dp,
    /** 卡片内部 padding */
    val cardPad: Dp = 16.dp,
    /** 列表项垂直间距 */
    val listGap: Dp = 10.dp,
)

/**
 * 圆角令牌（依据 docs/ui-design.md §2.3 + Apple 官方形状理论）。
 *
 * Apple 在 WWDC25「Get to know the new design system」中明确三种形状类型：
 *  1. fixed shapes —— 固定圆角
 *  2. **capsules** —— 圆角半径 = 容器高度的一半
 *  3. **concentric shapes** —— 内层圆角 = 父级圆角 − padding
 *
 * 胶囊是 Apple 最标志性的形状（滑块、开关、bar 都在用），
 * 早期版本用固定圆角做悬浮导航，形状语言就已经偏了（P02 实测复盘）。
 */
@Immutable
data class UbRadius(
    val card: Dp = 20.dp,
    val cardLarge: Dp = 26.dp,
    val button: Dp = 14.dp,
    val chip: Dp = 999.dp,
    val sheet: Dp = 28.dp,
    val field: Dp = 16.dp,
    /** 胶囊标记：用 999.dp 让 Compose 自动取高度的一半（等价于 capsule） */
    val capsule: Dp = 999.dp,
) {
    /** concentric：由父级圆角减去内边距得到子级圆角（Apple 官方公式） */
    fun concentric(parent: Dp, padding: Dp): Dp = (parent - padding).coerceAtLeast(0.dp)
}

/**
 * 尺寸令牌。
 * 触控目标下限 48dp 为无障碍硬性要求（不可下调）。
 */
@Immutable
data class UbSize(
    /** 无障碍最小触控目标 */
    val minTouch: Dp = 48.dp,
    /** 顶部大标题区高度 */
    val headerHeight: Dp = 56.dp,
    /** 底部导航高度 */
    val bottomBarHeight: Dp = 64.dp,
    /** 底部导航悬浮时的外间距 */
    val bottomBarInset: Dp = 16.dp,
    /**
     * 工具卡片高度。
     * 依据实测：cardPad(16)+iconBox(44)+gap(8)+title(24)+gap(2)+caption(17)+cardPad(16) = 127dp，
     * 原 108dp 会把描述文字裁掉（P02 截图发现）。取 132dp 留出余量。
     */
    val toolCardHeight: Dp = 132.dp,
    /**
     * 悬浮底部导航占用的垂直空间（高度 + 上下外间距 + 余量）。
     * 可滚动内容必须至少留出这么多底部内边距，否则最后一行会被导航遮住。
     */
    val bottomBarReserved: Dp = 112.dp,
    /** 图标标准尺寸 */
    val icon: Dp = 24.dp,
    /** 图标容器（工具卡片里的圆角方块） */
    val iconBox: Dp = 44.dp,
    /** 状态色点 */
    val statusDot: Dp = 10.dp,
    /** 玻璃描边宽度 */
    val glassStroke: Dp = 1.dp,
)

/**
 * 动画令牌。
 *
 * ## ★ 时长阶梯：全 App 只有四个数字（P11 交互连贯性整改）
 *
 * 数值来源：`REPORT-base1-apk.md` §9.1 的缓动纲领 ——
 * **「进场 Decelerate / 退场 Accelerate(3f)，时长 150 / 200 / 300 / 400 ms 四档」**。
 * 那份报告分析的是一个成熟商业 App 的**实测**动画参数。
 *
 * 采用它的理由不是"照抄"，而是**收敛选择**：
 * 以前每个页面各自 `tween(180)` / `tween(220)` / `tween(300)`，
 * 于是同一个 App 里各种节奏并存，滑动起来就不连贯。
 * 现在任何新动效都必须归入下面某一档。
 *
 * | 档位 | 时长 | 用在哪 |
 * |---|---|---|
 * | [press] | 150ms | 卡片按压（报告"强反馈"） |
 * | [pressWeak] | 200ms | 列表行/设置项按压（报告"弱反馈"） |
 * | [settle] | 300ms | 小元素淡入、开关状态切换 |
 * | [enter] | 400ms | 内容/列表入场（配 [enterDelay]） |
 * | [transition] | 260ms | 页面转场 —— **本应用自有值**，已在真机验证过，不随报告改 |
 *
 * ★ [stagger] 是**我们自己的**错峰步长。报告只给了「整体入场 + 延迟 100ms」，
 *   **没有**逐项递增延迟的做法 —— 所以不声称它是抄来的。
 *
 * ★ [quick] 保留旧名，取值与 [pressWeak] 一致（避开"两个 200ms 各有各的名字"的混乱）。
 */
@Immutable
data class UbMotion(
    /** 按压反馈（强）：卡片类 */
    val press: Int = 150,
    /** 按压反馈（弱）：列表行 / 设置项 */
    val pressWeak: Int = 200,
    /** 常规状态切换 */
    val quick: Int = 200,
    /** 小元素入场 */
    val settle: Int = 300,
    /** 页面转场（自有值，已验证） */
    val transition: Int = 260,
    /** 内容 / 列表入场 */
    val enter: Int = 400,
    /** 入场起始延迟（报告 `startDelay(100)`：等页面就位再入场，避免整屏同时蹦出） */
    val enterDelay: Int = 100,
    /** 同类元素之间错峰步长 */
    val stagger: Int = 40,
) {
    /**
     * 是否允许动效。
     *
     * ★ 判据是 [transition] —— 「减少动态效果」下 [reduced] 会把**每一档**归零，
     *   所以任一档为 0 都等价。
     */
    val enabled: Boolean get() = transition != 0

    companion object {
        /**
         * 「减少动态效果」下的取值：**每一档都归零**。
         *
         * ⚠️ 绝不能写成 `UbMotion(0, 0, 0, 0)` ——
         *   那是**按位置**传前四个参数，后面新增的 [enter] / [enterDelay]
         *   会保留默认的非零值。于是无障碍开关打开后入场动画照放，
         *   属于静默失效（改了参数却没生效，还看不出错）。
         */
        fun reduced(): UbMotion = UbMotion(
            press = 0,
            pressWeak = 0,
            quick = 0,
            settle = 0,
            transition = 0,
            enter = 0,
            enterDelay = 0,
            stagger = 0,
        )
    }
}

/**
 * 进场缓动 = Android `DecelerateInterpolator(3f)`，即 `1 − (1−t)³`。
 *
 * 报告 §9.1 的纲领是「进场 Decelerate / 退场 Accelerate(3f)」，
 * 且特别指出用的是 **3.0f 幂次**而非默认（默认是二次），
 * 曲线更「脆」、更有力。
 */
val UbEaseEnter: Easing = Easing { t ->
    val u = 1f - t
    1f - u * u * u
}

/** 退场缓动 = Android `AccelerateInterpolator(3f)`，即 `t³`。 */
val UbEaseExit: Easing = Easing { t -> t * t * t }

/**
 * 按压缩放档位 —— **全 App 只有两档**。
 *
 * 数值来自报告 §5.3④：强反馈 `1.0 → 0.95 → 1.0`、弱反馈 `1.0 → 0.98 → 1.0`。
 * 报告用三点关键帧模拟"按下再弹回"；我们改用真弹簧（见 `ubPressScale`），
 * 因为报告自己在 §5.4 把"没用包里的弹簧动画"列为**问题**。
 */
object UbPressScale {
    /** 卡片类（大块可点区域） */
    const val Card = 0.95f

    /** 列表行 / 设置项 */
    const val Row = 0.98f

    /** 不动（用于禁用态） */
    const val None = 1f
}

val LocalUbSpacing = staticCompositionLocalOf { UbSpacing() }
val LocalUbRadius = staticCompositionLocalOf { UbRadius() }
val LocalUbSize = staticCompositionLocalOf { UbSize() }
val LocalUbMotion = staticCompositionLocalOf { UbMotion() }
