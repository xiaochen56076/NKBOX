package com.utilitybox.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 首页与游戏页用的卡片组件。
 *
 * ### 为什么新增这个文件（P11 补 20）
 *
 * 用户反馈**首页「还是太毛胚」**。查下来根因不是"缺库"，而是：
 *
 * 1. **`Material3` 只用了 4 样东西**（`Text` / `Switch` / `OutlinedTextField` / `TextButton`）——
 *    而任务书明确要求「基础 UI 框架使用 Jetpack Compose Material 3」。
 *    `Card` / `Surface` / `Scaffold` / `TopAppBar` 一个都没用，全靠手搓 `Box + Column`。
 * 2. 于是卡片是**纯色矩形**：`clip + background(bgElevated)`，而 `bgElevated` 与 `bgBase`
 *    在浅色主题下只差一点点 —— 卡片和背景几乎糊在一起，**没有层次**。
 * 3. 游戏身份只有一行文字，**没有任何视觉锚点**。
 *
 * 所以这里做三件事，**不新增任何依赖**（Material3 已在依赖里）：
 * - 用 `Card` 的**描边 + 高度**把卡片从背景里"抬"起来
 * - 用**渐变 + 半透明水印图标**给游戏一个视觉身份
 * - 用**分节标题**把一屏内容分成可读的块
 *
 * ### ★ 交互语言不变
 *
 * 卡片仍然走 `ubClickable(scale = Card)` —— 按压缩放是用户已经确认过的手感
 * （「跟手了」）。**故意不用 `Card(onClick=)`**：那会引入 Material 的水波纹，
 * 与本项目 Apple 取向的"缩放反馈"是两套语言，混用会显得杂乱。
 * 这里只用 `Card` 的**视觉**能力（形状/描边/高度），不用它的交互能力。
 */

/**
 * 游戏身份卡 —— 首页顶部的视觉锚点。
 *
 * 结构：品牌小字 → 游戏名大字 → 一行元信息，背景是从强调色淡出的渐变，
 * 右侧有一个**大号半透明水印图标**填充视觉空间。
 *
 * ★ 水印图标用 `graphicsLayer` 不需要 —— 直接 `alpha` 控制即可；
 *   它必须**不可交互**（`Modifier` 上没有 clickable），否则会抢走整卡的点击。
 */
@Composable
fun UbGameHeroCard(
    brand: String,
    title: String,
    meta: String,
    watermark: UbIcon,
    onClick: (() -> Unit)? = null,
    /**
     * 另一个游戏的名称 —— 传入即显示"切换到 XX"的入口。
     *
     * ★ 这是「方案 C」：底栏已经能切游戏，但**显式的第二入口**能让
     *   "这里有两个游戏"这件事更早被看到 —— 底栏的方块是导航，用户不一定会读它。
     *   为 null 时不显示（只有一款游戏时自然消失，不需要额外判断）。
     */
    switchLabel: String? = null,
    onSwitch: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val r = LocalUbRadius.current
    val sz = LocalUbSize.current
    val t = LocalUbTypography.current

    val shape = RoundedCornerShape(r.cardLarge)
    // ★ 渐变方向：左上 → 右下。强调色只在**起点**出现，避免整块都染色导致
    //   深色主题下发灰。终点回到普通卡片色，保证文字对比度不受影响。
    val wash = Brush.linearGradient(
        colors = listOf(
            c.accent.copy(alpha = if (c.isDark) 0.22f else 0.12f),
            c.bgElevated,
        ),
        start = Offset.Zero,
        end = Offset(900f, 600f),
    )

    val base = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(wash)
        .then(
            // 描边让卡片在任何背景色下都有边界（浅色主题下 bgElevated 与 bgBase 太接近）
            Modifier.drawBehind {
                drawRoundRect(
                    color = c.divider,
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r.cardLarge.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
                )
            },
        )

    Box(
        modifier = if (onClick != null) base.ubClickable(scale = UbPressScale.Card, onClick = onClick) else base,
    ) {
        // 水印图标：只做装饰，不参与交互，也不进无障碍树
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = s.m)
                .size(120.dp),
            contentAlignment = Alignment.Center,
        ) {
            UbIcons.Icon(
                icon = watermark,
                tint = c.accent.copy(alpha = if (c.isDark) 0.16f else 0.10f),
                size = 120.dp,
            )
        }

        Column(modifier = Modifier.padding(s.xl).padding(end = 96.dp)) {
            Text(text = brand, style = t.caption, color = c.textTertiary)
            Text(
                text = title,
                style = t.displayLarge,
                color = c.textPrimary,
                modifier = Modifier.padding(top = s.xxs),
            )
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = t.caption,
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = s.s),
                )
            }

            // ---- 切换到另一个游戏（方案 C）----
            if (switchLabel != null && onSwitch != null) {
                Spacer(Modifier.height(s.m))
                Box(
                    modifier = Modifier
                        // ★ 胶囊本身的视觉高度由内容决定，但**触控目标**要够 ——
                        //   用 defaultMinSize 撑到 48dp，内容居中，视觉上仍是正常大小的 chip。
                        //   （直接给 padding 撑高会让它看起来像块砖。）
                        .defaultMinSize(minHeight = sz.minTouch)
                        .clip(RoundedCornerShape(r.capsule))
                        .background(c.accent.copy(alpha = if (c.isDark) 0.22f else 0.12f))
                        .ubClickable(scale = UbPressScale.Row, onClick = onSwitch)
                        .padding(horizontal = s.m),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "切换到 $switchLabel",
                            style = t.caption,
                            color = c.accent,
                        )
                        Spacer(Modifier.width(s.xs))
                        UbIcons.Icon(
                            icon = UbIcon.ChevronRight,
                            tint = c.accent,
                            size = 14.dp,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 工具入口卡片。
 *
 * 与旧实现的差别（都在"层次"上）：
 * | | 旧 | 新 |
 * |---|---|---|
 * | 容器 | `clip + background` | Material3 `Card`：**描边 + 高度阴影** |
 * | 图标 | 44dp 方块 | 48dp，强调色底更明显 |
 * | 标题/说明 | 挤在一起 | `SpaceBetween` 改为固定间距，说明限 2 行 |
 * | 右侧 | 无 | 低对比度箭头，暗示"可进入" |
 *
 * ★ 不可用状态**不是隐藏**，而是换成灰底灰字 + 状态说明照常显示 ——
 *   用户需要知道"有这个功能"，只是现在不能用。
 */
@Composable
fun UbToolCard(
    title: String,
    subtitle: String,
    icon: UbIcon,
    statusColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val r = LocalUbRadius.current
    val sz = LocalUbSize.current
    val t = LocalUbTypography.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = sz.toolCardHeight)
            // ★ 用 ubClickable 而不是 Card(onClick=)：保留"按压缩放"这套语言，
            //   不引入 Material 水波纹（见文件头说明）
            .ubClickable(scale = UbPressScale.Card, onClick = onClick),
        shape = RoundedCornerShape(r.card),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) c.bgElevated else c.bgSubtle,
        ),
        // 1dp 高度：足够在浅色主题下把卡片从背景里分离出来，又不至于像"浮起来"
        elevation = CardDefaults.cardElevation(defaultElevation = if (enabled) 1.dp else 0.dp),
        border = BorderStroke(1.dp, if (enabled) c.divider else Color.Transparent),
    ) {
        Column(
            modifier = Modifier.padding(s.cardPad),
            verticalArrangement = Arrangement.spacedBy(s.m),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UbIconBox(
                    icon = icon,
                    tint = if (enabled) c.accent else c.textTertiary,
                    background = if (enabled) c.accentSoft else c.bgElevated,
                    boxSize = 48.dp,
                    iconSize = 26.dp,
                )
                Spacer(Modifier.weight(1f))
                UbIcons.Icon(
                    icon = UbIcon.ChevronRight,
                    tint = c.textTertiary.copy(alpha = 0.5f),
                    size = 18.dp,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = t.titleMedium,
                    color = if (enabled) c.textPrimary else c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = t.caption,
                    color = if (enabled) statusColor else c.textTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 分节标题。
 *
 * ★ 小写字距拉开的标签风格 —— 与设置页 `SectionCard(title=)` 的视觉语言一致，
 *   让"首页"和"设置页"看起来是同一个 App。
 */
@Composable
fun UbSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = s.xs, end = s.xs, top = s.m, bottom = s.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            style = t.labelMedium,
            color = c.textTertiary,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(text = trailing, style = t.caption, color = c.textTertiary)
        }
    }
}

/**
 * 一行状态摘要（用于首页底部"当前可用情况"）。
 *
 * ★ 数字**必须由调用方从注册表实际统计**，不得写死 ——
 *   写死的数字会随工具增减变成假话（P09 实测踩过：界面写"2 个可用"而实际有 4 个）。
 */
@Composable
fun UbStatRow(
    stats: List<Triple<String, String, Color>>,
    modifier: Modifier = Modifier,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LocalUbRadius.current.card),
        colors = CardDefaults.cardColors(containerColor = c.bgElevated),
        border = BorderStroke(1.dp, c.divider),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(s.cardPad),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            stats.forEach { (value, label, color) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = value, style = t.numericMedium, color = color)
                    Text(
                        text = label,
                        style = t.caption,
                        color = c.textTertiary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}
