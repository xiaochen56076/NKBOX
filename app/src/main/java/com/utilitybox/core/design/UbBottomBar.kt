package com.utilitybox.core.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected as semanticsSelected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 悬浮底部导航（Apple Liquid Glass 风格，P10 按参考图重做）。
 *
 * ### 结构（对照用户提供的 iOS 参考图）
 *
 * ```
 * ┌──────────────────────────┐   ╭───╮
 * │  ╭─────╮                 │   │ ○ │   ← 独立的圆形动作按钮
 * │  │ 首页 │   设置          │   ╰───╯
 * │  ╰─────╯                 │
 * └──────────────────────────┘
 *    ↑ 胶囊容器（玻璃）  ↑ 选中项 = 内嵌药丸
 * ```
 *
 * ### 三处关键规格（均取自参考图）
 *
 * 1. **栏容器**：整条胶囊，玻璃材质 + 细描边，**悬浮**在内容之上（不占布局高度）
 * 2. **选中态**：内嵌**药丸**高亮 + accent 色调。药丸是"下沉进玻璃"的一层，
 *    它自己的圆角必须**同心**（父级圆角 − 内边距），否则角看起来会"外翻"
 * 3. **侧边动作**：圆形按钮**独立于胶囊**，与胶囊之间有间隙 ——
 *    这在参考图里是很显眼的一处结构特征
 *
 * ### ★ 药丸是**一个共享滑块**，不是每项各画一块（P11 用户反馈后重做）
 *
 * 原来每个标签在选中时给自己画背景。这有个必然的后果：
 * **位置只能整块闪现**，永远做不出"跟手滑动"。
 * 真机录屏逐帧实测（120fps + 模板匹配测页面位移）量到：
 * 页面已经滑到 73% 时，药丸还停在源标签，随后才跳过去。
 *
 * 用户原话：「tab 和页面应该是同步的，而不是工具页面滑动再 tab 动的」。
 *
 * 所以改成：整个胶囊里只有**一块**药丸，位置由 [selectedFraction]
 * 这个**连续值**决定 —— 页面滑到 73%，药丸就在 73% 处，两者同源同帧。
 *
 * ### ★ 一次判断错误的记录（P02 → P10）
 *
 * P02 复盘时我写道「iOS 选中态是 accent 色调，**没有背景块**」，
 * 并据此删掉了选中药丸。**这个判断是错的**：参考图里选中项明确有药丸。
 *
 * 当时的错误在于：把「浅色背景下药丸对比度低、不容易看清」
 * 误当成「不存在药丸」。**低对比 ≠ 不存在** ——
 * 药丸的立体感来自玻璃折射与描边，而不是靠明度差（实测
 * 选中区 `#ECECEC` vs 栏底 `#EAEAEA`，只差 2）。
 *
 * @param selectedFraction 药丸的**连续**位置：`0.0` = 第 1 项，`1.0` = 第 2 项，
 *   以此类推；`null` 表示当前没有连续来源（用离散的 [currentRoute] 并带动画）。
 *   左右滑动切换游戏标签时由 pager 逐帧灌入。
 * @param trailingAction 胶囊右侧的独立圆形按钮（参考图里的"搜索"位置）
 */
@Composable
fun UbBottomBar(
    items: List<UbBottomItem>,
    currentRoute: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    trailingAction: UbBottomAction? = null,
    selectedFraction: Float? = null,
) {
    val s = LocalUbSpacing.current
    val r = LocalUbRadius.current
    val sz = LocalUbSize.current
    val c = LocalUbColors.current
    val motion = LocalUbMotion.current
    val tier = LocalUbGlassVariantTier.current

    // Apple: capsule = 高度的一半。用 999.dp 让 Compose 自行取半高。
    val barShape = RoundedCornerShape(r.capsule)

    /**
     * 选中药丸的形状。
     *
     * ★ 必须按**药丸自己的实际高度**算，不能拿 [UbRadius.capsule]（999.dp 哨兵）去减：
     *   `999 - 5` 仍是 999，Compose 照样取半高，同心关系根本表达不出来。
     *
     *   药丸高 = 栏高 − 上下内缩 ×2；胶囊半径 = 该高度的一半（Apple 的 capsule 定义）。
     *   这样药丸与栏**同心**，角不会"外翻"。
     */
    val pillHeight = sz.bottomBarHeight - PILL_INSET * 2
    val pillShape = RoundedCornerShape(pillHeight / 2)
    val circleShape = RoundedCornerShape(r.capsule)

    /** 离散命中项；`-1` = 当前路由不在这个胶囊里（例如停在「我的」） */
    val discreteIndex = items.indexOfFirst { it.route == currentRoute }

    /**
     * 没有连续来源时（点击切换、或在非游戏页），让药丸**滑**到目标位置，
     * 而不是闪现。★ 位置取 `coerceAtLeast(0)`：命中不到任何项时保留在上一处，
     * 由 alpha 负责淡出，避免"滑出去又滑回来"的多余动作。
     */
    val animatedDiscrete by animateFloatAsState(
        targetValue = discreteIndex.coerceAtLeast(0).toFloat(),
        animationSpec = tween(if (motion.transition == 0) 0 else motion.transition),
        label = "bottom-bar-pill-position",
    )

    val lastIndex = (items.size - 1).coerceAtLeast(0)
    val fraction = (selectedFraction ?: animatedDiscrete).coerceIn(0f, lastIndex.toFloat())

    /**
     * 图标/文字用**最近的那一项**，于是强调色在滑块过半时切换
     * （iOS 分段控件就是这个手感）。
     *
     * ★ 有连续来源时按 `fraction` 取最近项；否则用离散命中项 ——
     *   后者能在 `discreteIndex == -1`（「我的」）时正确地**什么都不选**。
     */
    val selectedIndex = if (selectedFraction != null) {
        fraction.roundToInt().coerceIn(0, lastIndex)
    } else {
        discreteIndex
    }

    val pillAlpha by animateFloatAsState(
        targetValue = if (selectedIndex >= 0) 1f else 0f,
        animationSpec = tween(if (motion.transition == 0) 0 else motion.transition),
        label = "bottom-bar-pill-alpha",
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GAP_BETWEEN_GROUP_AND_ACTION),
    ) {
        // ---- 胶囊：标签组 ----
        UbGlassSurface(
            modifier = Modifier.weight(1f),
            shape = barShape,
            tier = tier,
            style = UbGlassStyle.Clear,
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sz.bottomBarHeight)
                    .padding(PILL_INSET),
            ) {
                // ★ 几何：所有标签都是 `weight(1f)`，所以等宽；
                //   间距由 `spacedBy(PILL_GAP)` 给出，滑块步长 = 标签宽 + 间距。
                //   `BoxWithConstraints` 的 `maxWidth` 已经是**去掉内缩之后**的宽度
                //   （padding 在外层修饰符链上，会先收缩约束）。
                if (items.isNotEmpty()) {
                    val tabWidth = (maxWidth - PILL_GAP * (items.size - 1)) / items.size
                    val step = tabWidth + PILL_GAP
                    Box(
                        modifier = Modifier
                            .offset(x = step * fraction)
                            .width(tabWidth)
                            .fillMaxHeight()
                            .alpha(pillAlpha)
                            .clip(pillShape)
                            .background(c.tabPill),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PILL_GAP),
                ) {
                    items.forEachIndexed { index, item ->
                        NavPill(
                            item = item,
                            isSelected = index == selectedIndex,
                            shape = pillShape,
                            onClick = { onSelect(item.route) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // ---- 独立的圆形动作按钮（与胶囊分离）----
        if (trailingAction != null) {
            // ★ 必须把尺寸同时传给玻璃层与内容层：
            //   `UbGlassSurface` 内部是 Box，只给内容设尺寸会让玻璃层按父级约束塌成椭圆
            //   （P10 实测：右侧按钮被压成了竖长条）。
            //   固定 size 之后，圆角用 capsule 才能得到正圆。
            Box(modifier = Modifier.size(sz.bottomBarHeight)) {
                UbGlassSurface(
                    modifier = Modifier.matchParentSize(),
                    shape = circleShape,
                    tier = tier,
                    style = UbGlassStyle.Clear,
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            // ⚠️ semantics 在 clickable 之前（P02 实测：反过来 clickable 会成为叶节点）
                            .semantics(mergeDescendants = true) {
                                contentDescription = trailingAction.label
                                role = Role.Button
                                semanticsSelected = trailingAction.isSelected
                            }
                            .ubClickable(onClick = trailingAction.onClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        UbIcons.Icon(
                            icon = trailingAction.icon,
                            // ★ 选中时用强调色 —— 否则停在「我的」页时，
                            //   整条导航栏没有任何一处指示当前位置（P11 实测发现）
                            tint = if (trailingAction.isSelected) c.accent else c.tabContent,
                            size = 22.dp,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单个标签：只画图标与文字，**背景由父级共享滑块提供**。
 *
 * ★ 选中色用强调色（[UbColors.accent]）；药丸底色是中性半透明浮层
 *   （[UbColors.tabPill]），由 [UbBottomBar] 统一绘制。
 *   理由见 `UbColors.tabPill` 的说明：药丸是"玻璃上浮起的一层"，应与玻璃同源；
 *   填成强调色会让它变成一块彩色贴纸，与整体玻璃语言割裂。
 */
@Composable
private fun NavPill(
    item: UbBottomItem,
    isSelected: Boolean,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalUbColors.current
    val t = LocalUbTypography.current
    val motion = LocalUbMotion.current

    val interaction = remember { MutableInteractionSource() }

    val tint = if (isSelected) c.accent else c.tabContent

    Column(
        modifier = modifier
            .fillMaxHeight()
            .defaultMinSize(minHeight = LocalUbSize.current.minTouch)
            // ★ P11：按压反馈改用**全局统一**的那一套（UbInteraction.kt）。
            //   此前这里是自成一体的 0.94 / motion.press，与卡片、列表行都不一样；
            //   现在全 App 只有 Card(0.95) / Row(0.98) 两档，缓动也一致。
            .ubPressScale(interactionSource = interaction, targetScale = UbPressScale.Card)
            .clip(shape)
            // ★★ 这里**故意不画**选中背景：药丸由 [UbBottomBar] 统一绘制成**一块共享滑块**。
            //   每项各画一块的话，位置只能整块闪现 —— 就做不出"跟手滑动"了
            //   （P11 用户反馈 + 录屏逐帧实测，详见 UbBottomBar 的说明）。
            // ⚠️ semantics 必须在 clickable 之前 —— 见 NavPill 上方的说明
            .semantics(mergeDescendants = true) {
                contentDescription = item.label
                semanticsSelected = isSelected
                role = Role.Tab
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        UbIcons.Icon(icon = item.icon, tint = tint, size = 22.dp)
        Text(
            text = item.label,
            style = t.labelMedium,
            color = tint,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(top = 1.dp),
            // ★★ 必须禁止换行（P11 实测踩到）：
            //   中文标签在窄胶囊里会被逐字折行，「鸣潮」变成上下两个字 ——
            //   底部导航的标签**任何情况下都该是一行**，宁可省略也不能折。
            //   同时给个单行省略兜底，防止将来出现超长标签时撑破布局。
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

data class UbBottomItem(val route: String, val label: String, val icon: UbIcon)

/**
 * 胶囊右侧的独立圆形按钮（参考图里"搜索"的位置）。
 *
 * @param isSelected 该按钮当前是否代表所在页面。★ P11 补：此前这个圆钮
 *   **没有任何选中态**，用户停在「我的」页时整条导航栏没有任何一处指示当前位置。
 */
data class UbBottomAction(
    val label: String,
    val icon: UbIcon,
    val onClick: () -> Unit,
    val isSelected: Boolean = false,
)

// ---- 取自参考图的固定几何 ----
/** 药丸相对栏的内缩，同时决定同心圆角 */
private val PILL_INSET = 5.dp
/** 药丸之间的间隙 */
private val PILL_GAP = 4.dp
/** 胶囊与圆形按钮之间的间隙 */
private val GAP_BETWEEN_GROUP_AND_ACTION = 10.dp

/** 当前玻璃档位（由 rememberGlassTier 计算后下发，避免每个组件各自判断设备能力） */
val LocalUbGlassVariantTier = androidx.compose.runtime.staticCompositionLocalOf {
    GlassTier.Glass
}

/**
 * 圆形图标按钮（标题栏右侧、卡片内操作等）。
 *
 * 与底部栏右侧的动作圆钮同一形状语言（胶囊/圆形），
 * 但**不着玻璃** —— 它贴在页面内容上，加玻璃只会让层次变糊。
 */
@Composable
fun UbIconButton(
    icon: UbIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val c = LocalUbColors.current
    val sz = LocalUbSize.current

    Box(
        modifier = modifier
            .size(sz.minTouch)
            .clip(RoundedCornerShape(LocalUbRadius.current.capsule))
            .semantics(mergeDescendants = true) {
                contentDescription?.let { this.contentDescription = it }
                role = Role.Button
            }
            // ★ P11：图标按钮也走统一按压反馈（此前按下去没有任何反应）
            .ubClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        UbIcons.Icon(icon = icon, tint = c.textSecondary, size = 22.dp)
    }
}
