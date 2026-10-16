package com.utilitybox.core.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 交互反馈的**唯一出口**（P11「交互连贯性」整改）。
 *
 * ## 为什么需要这个文件
 *
 * 整改前全 App 只有底部导航栏的标签有按压缩放；设置行、工具卡片、物品列表行、
 * 按钮、返回键**按下去毫无反馈**。根因很具体：
 * 原来的 `ubClickable` 内部 `remember { MutableInteractionSource() }` 之后
 * **把这个 source 丢掉了** —— 外面拿不到按压状态，于是谁也做不了缩放。
 * 旧注释写着「P08 再做缩放」，一直没做。
 *
 * 现在按压与入场都收敛到这里，于是：
 * - 缩放只有两档（[UbPressScale.Card] / [UbPressScale.Row]）
 * - 按下用 [UbEaseExit]，松手用**真弹簧**
 * - 时长取 [UbMotion.press] / [UbMotion.pressWeak]
 *
 * ## 松手为什么用真弹簧，而不是照抄报告的三点关键帧
 *
 * 报告 §5.3④ 的做法是把 `ValueAnimator.ofFloat(1.0f, 0.95f, 1.0f)` 当"按下再弹回"。
 * 但报告自己在 §5.4 把「未使用包里已有的弹簧动画，按压反馈用三点关键帧模拟」
 * 列为**问题**（原话：效果不如真弹簧自然）。
 * 所以这里直接用 Compose 的 `spring` —— 比报告的做法更好，
 * 而不是连它的缺点一起抄。
 */

/**
 * 按压缩放反馈。
 *
 * 只负责"看起来被按下了"，**不接管点击** ——
 * 需要点击的场景用 [ubClickable]，它会把同一个 interaction source 复用给两者。
 *
 * @param interactionSource 按压状态来源；与 `clickable` 共用同一个实例
 * @param targetScale 按下时的缩放，取 [UbPressScale] 里的档位
 */
@Composable
fun Modifier.ubPressScale(
    interactionSource: InteractionSource,
    targetScale: Float = UbPressScale.Row,
): Modifier {
    val motion = LocalUbMotion.current
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }

    LaunchedEffect(pressed, motion.enabled, targetScale) {
        when {
            // 「减少动态效果」：直接停在 1，不做任何动画
            !motion.enabled -> scale.snapTo(1f)

            // 按下：加速离开（报告纲领"退场 Accelerate"），时长按档位取
            pressed -> scale.animateTo(
                targetValue = targetScale,
                animationSpec = tween(
                    durationMillis = if (targetScale >= UbPressScale.Card) {
                        motion.press
                    } else {
                        motion.pressWeak
                    },
                    easing = UbEaseExit,
                ),
            )

            // 松手：真弹簧回弹，带一点点过冲（报告批评的正是"没有用真弹簧"）
            else -> scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }

    // ★ 在 graphicsLayer 的 lambda 里读 scale.value：
    //   这样每帧只重绘、不重组，也不触发重新布局（缩放不占布局空间）。
    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/**
 * 点击 + 按压反馈，二者共用同一个 interaction source。
 *
 * ★ 全 App 的可点元素都应该走这个，而不是裸 `Modifier.clickable` ——
 *   裸 clickable 没有反馈，正是"哪里点着都像点了个寂寞"的来源。
 *
 * @param scale 按下缩放；大块卡片用 [UbPressScale.Card]，列表行用 [UbPressScale.Row]
 */
@Composable
fun Modifier.ubClickable(
    enabled: Boolean = true,
    scale: Float = UbPressScale.Row,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this
        .ubPressScale(
            interactionSource = interaction,
            targetScale = if (enabled) scale else UbPressScale.None,
        )
        .clickable(
            enabled = enabled,
            interactionSource = interaction,
            // 不分发涟漪：视觉反馈由缩放承担（Apple 风格，且避免与玻璃层叠加出脏边）
            indication = null,
            onClick = onClick,
        )
}

/**
 * 错峰最多叠加的步数。
 *
 * ★★ 这个上限是一次**线上事故的修复**（P11 补 13，用户反馈
 *   「数据显示太慢，滑动过快就会造成全白，要等一下才能加载出来」）。
 *
 *   原式是 `enterDelay + index × stagger`，**随索引无上限增长**：
 *
 *   | 索引 | 入场延迟 |
 *   |---|---|
 *   | 0 | 100ms |
 *   | 10 | 500ms |
 *   | 30 | 1300ms |
 *   | 100 | **4100ms** |
 *
 *   错峰本意是「同屏几个元素依次出现」，**同屏最多也就七八个** ——
 *   超过这个数就不再是"错峰"，而是在给后面的元素**判延迟**。
 *
 *   而它被套在了 113 项的弹药物价 / 物品物价列表上：滑到第 30 项，
 *   该项已经进入视口、布局也完成了，却因为 `alpha = 0` **空白 1.3 秒**；
 *   滑得越快、索引越大、白得越久 —— 用户看到的正是"全白，要等一下"。
 *
 *   **上限把"错峰"重新钉回它的本意**：无论列表多长，最后一个元素的延迟
 *   也不超过 [MAX_STAGGER_STEPS] 步。
 *
 * ★ 更根本的一层：**滚动列表的元素本来就不该有入场动画**。
 *   错峰只属于"页面首次呈现"这一刻；列表元素是**随着滚动进入视口**的，
 *   它们必须立刻可见。所以各长列表的 `ubEnter` 已一并移除（见各自的注释）。
 *   这个上限是防御：万一以后再被误用，后果也不会是秒级空白。
 */
private const val MAX_STAGGER_STEPS = 6

/**
 * 入场延迟的计算 —— **抽成纯函数是为了能被单测钉住**。
 *
 * 这段逻辑曾经造成过一次真实的线上观感事故（见 [MAX_STAGGER_STEPS]），
 * 而它的错误形态是"延迟随索引无上限增长"—— 只靠读代码很容易再次写错，
 * 必须由测试守住"**无论 index 多大，延迟都有上界**"这条不变式。
 */
internal fun ubEnterDelayMillis(index: Int, enterDelay: Int, stagger: Int): Int =
    enterDelay + index.coerceIn(0, MAX_STAGGER_STEPS) * stagger

/** 入场延迟的**硬上限**（毫秒）。列表再长也不能超过它 —— 否则就是"滑动变白"。 */
internal fun ubEnterMaxDelayMillis(enterDelay: Int, stagger: Int): Int =
    ubEnterDelayMillis(MAX_STAGGER_STEPS, enterDelay, stagger)

/**
 * 内容入场：淡入 + 从下方就位。
 *
 * 数值来源：报告 §5.3⑤「列表入场」——
 * `alpha 0→1`、`translationY 30dp→0`、`400ms`、`startDelay(100ms)`、`DecelerateInterpolator`。
 *
 * ★ 报告的做法是**整块 RecyclerView 一次性入场** + 100ms 延迟，
 *   目的是"等页面就位再入场，避免所有内容同时蹦出"。
 *   我们保留这个整体入场，另外用 [index] × [UbMotion.stagger] 做**同类元素之间的错峰** ——
 *   这一层是我们的补充，报告没有。
 *
 * ⚠️ **只用于"页面首次呈现"的少量元素**（工具网格、我的页各区块）。
 *   滚动列表的元素请用 `Modifier.animateItem()` —— 它们随滚动进入视口，
 *   必须立刻可见。误用会导致"滑动越快、空白越久"，见 [MAX_STAGGER_STEPS]。
 *
 * ⚠️ 性能陷阱：`alpha` 叠在 **Haze 玻璃**上会让模糊每帧重算。
 *   所以这个修饰符应加在**普通容器**上；玻璃卡片只做位移、不做透明度
 *   （需要给玻璃卡片用时传 `fade = false`）。
 *
 * @param index 同屏同类元素的序号，用于错峰；0 表示不额外延迟
 * @param rise 起始下移距离
 * @param fade 是否同时淡入；玻璃层请传 false
 */
@Composable
fun Modifier.ubEnter(
    index: Int = 0,
    rise: Dp = 30.dp,
    fade: Boolean = true,
): Modifier {
    val motion = LocalUbMotion.current
    val progress = remember { Animatable(0f) }

    LaunchedEffect(motion.enabled, index) {
        if (!motion.enabled) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = motion.enter,
                // ★ 索引必须夹住：无上限的错峰会变成秒级空白（见 MAX_STAGGER_STEPS）
                delayMillis = ubEnterDelayMillis(index, motion.enterDelay, motion.stagger),
                easing = UbEaseEnter,
            ),
        )
    }

    val risePx = with(LocalDensity.current) { rise.toPx() }
    return this.graphicsLayer {
        if (fade) alpha = progress.value
        translationY = (1f - progress.value) * risePx
    }
}

/** 每次列表呈现允许播放入场的元素个数。约等于一屏可见项 */
private const val UB_LIST_ENTER_SLOTS = 8

/**
 * 列表入场闸门。
 *
 * ### 它解决的问题
 *
 * P11 补 13 修「滑动过快就全白」时，我把长列表的入场动画**整个删掉了** ——
 * 用户随即反馈「跟手了，但是动画没了呀」。这是一次**过度修正**：
 * 真正错的不是"有动画"，而是**没区分两种"出现"**：
 *
 * | 场景 | 应该怎样 | 原实现 |
 * |---|---|---|
 * | 数据到达、列表首次呈现 | 错峰入场（这是设计意图） | ✅ 正确 |
 * | 元素随滚动进入视口 | **立刻可见** | ❌ 也播入场 → 空白 |
 *
 * ### 闸门怎么分辨
 *
 * 每次列表组合只有一个闸门实例，**前 [UB_LIST_ENTER_SLOTS] 个前来认领的元素**播放入场，
 * 之后闸门关闭，一律直接显示。
 *
 * ★ 为什么用"认领计数"而不是"时间窗"：
 *   时间窗需要定时器，而且慢设备上第一屏还没组合完窗口就关了；
 *   计数与组合顺序天然对齐 —— 列表是自上而下组合的，所以认领到的就是第一屏那批。
 *
 * ★ 为什么 [rememberUbListEntrance] 不接 key：
 *   若按"列表内容"当 key，筛选一变就重开闸门 —— 这没错，但调用方往往在
 *   `composable` 里现算列表（每次重组都是新实例），于是**每次重组都重开闸门**，
 *   滚动时新元素又会认领到名额，等于 bug 原样复发。
 *   一次组合一个闸门是**不会错**的保守选择：筛选变化的增删动画由
 *   `animateItem(fadeInSpec=…)` 承担（那才是它的职责）。
 */
class UbListEntrance internal constructor() {
    private var claimed = 0

    /** 认领一个入场名额；名额用尽后恒为 false */
    internal fun claim(): Boolean {
        if (claimed >= UB_LIST_ENTER_SLOTS) return false
        claimed++
        return true
    }
}

@Composable
fun rememberUbListEntrance(): UbListEntrance = remember { UbListEntrance() }

/**
 * 列表元素入场：**仅在闸门还开着时**播放，之后随滚动进入视口的元素直接显示。
 *
 * @param itemKey 元素标识（用列表的 key），保证同一元素只认领一次名额
 * @param index 错峰序号（已有上界，见 [ubEnterDelayMillis]）
 */
@Composable
fun Modifier.ubListEnter(
    itemKey: Any?,
    index: Int,
    entrance: UbListEntrance,
    rise: Dp = 30.dp,
): Modifier {
    val motion = LocalUbMotion.current

    // ★ 在 remember 里认领名额：每个 key 只认领一次。
    //   滚出屏幕再滚回来时 `remember(itemKey)` 会重新求值，但闸门已经关了 →
    //   拿到 false → 不再播动画。这正是我们要的"只播一次"。
    val wonSlot = remember(itemKey) { entrance.claim() }
    val play = wonSlot && motion.enabled

    val progress = remember { Animatable(if (play) 0f else 1f) }
    LaunchedEffect(itemKey, play) {
        if (!play) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = motion.enter,
                delayMillis = ubEnterDelayMillis(index, motion.enterDelay, motion.stagger),
                easing = UbEaseEnter,
            ),
        )
    }

    val risePx = with(LocalDensity.current) { rise.toPx() }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * risePx
    }
}
