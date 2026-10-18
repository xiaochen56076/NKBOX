package com.utilitybox.game.wutheringwaves

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.utilitybox.core.design.LocalUbColors
import com.utilitybox.core.design.LocalUbRadius
import com.utilitybox.core.design.LocalUbSize
import com.utilitybox.core.design.LocalUbSpacing
import com.utilitybox.core.design.LocalUbTypography
import com.utilitybox.core.design.SectionCard
import com.utilitybox.core.design.UbDivider
import com.utilitybox.core.design.UbIcon
import com.utilitybox.core.design.UbIcons
import com.utilitybox.core.design.UbItemIcon
import com.utilitybox.game.wutheringwaves.kuro.GachaInsights
import com.utilitybox.game.wutheringwaves.kuro.GachaRecord
import com.utilitybox.game.wutheringwaves.kuro.WuwaAvatarIndex
import com.utilitybox.game.wutheringwaves.kuro.computeInsights
import java.util.Locale

/**
 * 唤取统计页（P11 补 44 —— 独立页面 + 分池滑动）。
 *
 * ### 用户原话与对应改动
 *
 * > 「能不能做成这种样式（小黑盒）的，有一个**左右栏目来滑动不同池子**，
 * >   有**数据条**、**角色头像**」
 * > 「统计好后应该是一个**新的界面**，而不是仍然显示在那里下面」
 * > 「还有就是**有点卡顿**，应该优化下性能，你一下显示那么多」
 *
 * ### ★ 卡顿的真正来源（不是这个页面，是记录页）
 *
 * 唤取记录页把 **848 条记录一次性铺在 `Column` 里**（不是 `LazyColumn`）——
 * 那会同时实例化 848 个 composable。这才是主因，已一并修掉：
 * 记录页改为「最近 N 条 + 展开」。
 *
 * 本页的两处性能处理：
 * 1. [computeInsights] 用 `remember(records)` 缓存 —— 否则**每次重组都重跑**
 *    （排序 848 条 + 分段 + 逐条算垫抽），滑动时每帧都算一遍。
 * 2. 只渲染**当前选中池**的内容，不做 6 个池全量铺开。
 *
 * ### ★ 关于"角色头像"
 *
 * 小黑盒那种头像是**游戏素材**，本工程没有、也不能凭空造（红线：不伪造资源）。
 * 所以这里用**首字圆形标记**（名字第一个字 + 稀有度配色）作为正式的占位设计，
 * 而不是拿一个通用图标冒充立绘，也不是留一块空白。
 */
@Composable
fun GachaStatsScreen(
    records: List<GachaRecord>,
    modifier: Modifier = Modifier,
) {
    if (records.isEmpty()) return

    // ★ P11 补 96：加载「角色名 → 头像」索引。
    //   只触发一次：对象内部自己去重（已加载/已过期/正在加载都会短路）。
    //   加载完成后 `WuwaAvatarIndex.byName` 是 Compose 状态，读取它的行会自动重组，
    //   所以这里**不需要**再把结果往下传。
    LaunchedEffect(Unit) { WuwaAvatarIndex.ensureLoaded() }

    // ★ remember：只在 records 真的变了才重算。滑动/切换 tab 都不该重跑。
    val g = remember(records) { computeInsights(records) }

    val pools = remember(g) { g.perPool }
    var selected by remember { mutableIntStateOf(0) }
    val idx = selected.coerceIn(0, (pools.size - 1).coerceAtLeast(0))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = LocalUbSize.current.bottomBarReserved),
    ) {
        OverviewCard(g)

        // ★ P11 补 98：每个卡池一张汇总卡（横向滑动）。
        //   参考图的结构是「先看每个池的汇总，再看某个池的明细」，
        //   而原来只有一张总览卡把四个池糊在一起。
        Spacer(Modifier.height(LocalUbSpacing.current.s))
        PoolCardRow(g)

        if (pools.isNotEmpty()) {
            Spacer(Modifier.height(LocalUbSpacing.current.s))
            PoolTabs(
                labels = pools.map { shortPool(it.poolType) },
                selected = idx,
                onSelect = { selected = it },
            )
            PoolDetail(g, pools[idx])
        }

        UpCard(g)
        FiveStarCard(g)
    }
}

// ---------------------------------------------------------------------------
// 总览
// ---------------------------------------------------------------------------

@Composable
private fun OverviewCard(g: GachaInsights) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current

    SectionCard(title = "总览") {
        Column(modifier = Modifier.padding(s.cardPad)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BigStat("总抽数", g.total.toString(), c.textPrimary)
                BigStat("五星", g.fiveStar.toString(), c.warning)
                BigStat("四星", g.fourStar.toString(), c.accent)
                // 没有五星时给「—」而不是 0 或 ∞
                BigStat("平均出金", g.avgFiveStar?.let { fmt(it) } ?: "—", c.textPrimary)
            }
            Spacer(Modifier.height(s.m))
            Text(
                text = "星声共 ${g.perPool.sumOf { it.astrite }}（按每抽 160 计）",
                style = LocalUbTypography.current.caption,
                color = c.textTertiary,
            )
        }
    }
}

@Composable
private fun BigStat(label: String, value: String, color: Color) {
    val t = LocalUbTypography.current
    val c = LocalUbColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = t.numericLarge, color = color)
        Spacer(Modifier.height(2.dp))
        Text(text = label, style = t.caption, color = c.textTertiary)
    }
}

// ---------------------------------------------------------------------------
// 分池卡片行（P11 补 98）
// ---------------------------------------------------------------------------

/**
 * 每个卡池一张小卡片，横向滑动。
 *
 * ### 为什么加这一块
 *
 * 用户要求「界面模仿第三张图片做的更精细」—— 参考图的核心结构是
 * **「先给每个卡池一张汇总卡（抽数 / 出卡 / 平均），再在下面看明细」**，
 * 而原来只有一张总览卡把四个池的数字全糊在一起，
 * 想知道"武器池单独花了多少"必须先切到对应 tab。
 *
 * ### 只用真实数字
 *
 * · 抽数、五星数：直接来自 [PoolSummary]
 * · 平均出金：该池每个五星的 `pityUsed` 求平均；**没有五星就是「—」**，
 *   不写 0、不写 ∞、也不按"欧非"编一个评分
 *
 * ★ 参考图那张卡里还有「生涯评分 / 称号」这类东西 —— 本工程**不做**：
 *   那是平台自己的评分模型，我们没有它的公式，凭空算一个数就是编数据。
 */
@Composable
private fun PoolCardRow(g: GachaInsights) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current
    val r = LocalUbRadius.current

    if (g.perPool.isEmpty()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = s.screenH),
        horizontalArrangement = Arrangement.spacedBy(s.s),
    ) {
        g.perPool.forEach { p ->
            val pulls = g.fiveStarPulls.filter { it.poolType == p.poolType }
            // 平均出金：该池五星的垫抽数均值。没有五星 → null（显示「—」）
            val avg = pulls.takeIf { it.isNotEmpty() }?.map { it.pityUsed }?.average()

            Column(
                modifier = Modifier
                    .width(140.dp)
                    .clip(RoundedCornerShape(r.card))
                    .background(c.bgElevated)
                    .padding(s.m),
            ) {
                Text(
                    text = p.label,
                    style = t.caption,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(s.xs))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(text = "${p.total}", style = t.numericLarge, color = c.textPrimary)
                    Text(
                        text = " 抽",
                        style = t.caption,
                        color = c.textTertiary,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                Spacer(Modifier.height(s.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(s.s)) {
                    MiniStat("五星", p.fiveStar.toString(), c.warning)
                    MiniStat("平均", avg?.let { fmt(it) } ?: "—", c.textPrimary)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 分池：左右可滑动的栏目
// ---------------------------------------------------------------------------

@Composable
private fun PoolTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = LocalUbColors.current
    val t = LocalUbTypography.current

    ScrollableTabRow(
        selectedTabIndex = selected,
        containerColor = Color.Transparent,
        contentColor = c.accent,
        edgePadding = LocalUbSpacing.current.screenH,
        divider = {},
    ) {
        labels.forEachIndexed { i, label ->
            Tab(
                selected = i == selected,
                onClick = { onSelect(i) },
                text = {
                    Text(
                        text = label,
                        style = t.labelMedium,
                        color = if (i == selected) c.accent else c.textSecondary,
                    )
                },
            )
        }
    }
}

/**
 * 选中池的明细。
 *
 * ★ 这里就是用户要的「**数据条 + 头像**」：每个五星一行，
 *   左边是首字圆形标记，中间是名字，右边是垫抽数 + 一条**按抽数长度**的横条。
 *   横条让"这一发垫了多少"变成**可以一眼比出来的形状**，
 *   而不是一串要逐个读的数字。
 */
@Composable
private fun PoolDetail(g: GachaInsights, pool: com.utilitybox.game.wutheringwaves.kuro.PoolSummary) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current

    val list = g.fiveStarPulls.filter { it.poolType == pool.poolType }

    SectionCard(title = "${pool.label} · ${pool.total} 抽") {
        Column {
            // 该池的概括行
            Row(
                modifier = Modifier.fillMaxWidth().padding(s.cardPad),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MiniStat("五星", pool.fiveStar.toString(), c.warning)
                MiniStat("四星", pool.fourStar.toString(), c.accent)
                MiniStat("已垫", (g.pity[pool.poolType] ?: 0).toString(), c.textPrimary)
                MiniStat("星声", pool.astrite.toString(), c.textSecondary)
            }

            if (list.isEmpty()) {
                UbDivider()
                Text(
                    text = "这个池还没有五星记录",
                    style = t.caption,
                    color = c.textTertiary,
                    modifier = Modifier.padding(s.cardPad),
                )
            } else {
                // 该池里的最大垫抽数 —— 数据条按它归一化
                val maxPity = list.maxOf { it.pityUsed }.coerceAtLeast(1)
                list.reversed().forEach { f ->
                    UbDivider()
                    FiveStarRow(f.name, f.pityUsed, maxPity, f.isUp, shortDate(f.time))
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color) {
    val t = LocalUbTypography.current
    val c = LocalUbColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = t.numericMedium, color = color)
        Text(text = label, style = t.caption, color = c.textTertiary)
    }
}

/**
 * 一行五星：**角色头像** + 名字 + 数据条。
 *
 * ★ P11 补 96：原来这里用「名字首字」做圆形占位（用户反馈：
 *   「每个角色能不能获取其角色头像…而不是使用文字当头像」）。
 *   现在头像来自 `WuwaAvatarIndex`（库街区 wiki 的官方 `contentUrl`），
 *   经 `UbItemIcon` 加载并**磁盘缓存**。
 *
 * ★★★ 索引里**没有**这个角色时，仍然回退到首字占位 ——
 *   绝不拿通用图标或别的角色的图冒充。宁可显示首字，也不显示错的头像。
 */
@Composable
private fun FiveStarRow(
    name: String,
    pity: Int,
    maxPity: Int,
    isUp: Boolean?,
    date: String,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current

    // 读 Compose 状态：头像索引加载完成后这一行会自动重组
    val avatarUrl = WuwaAvatarIndex.urlOf(name)

    Row(
        modifier = Modifier.fillMaxWidth().padding(s.cardPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s.m),
    ) {
        if (avatarUrl != null) {
            UbItemIcon(
                url = avatarUrl,
                contentDescription = name,
                size = 38.dp,
            )
        } else {
            // 兜底：索引里没有这个角色 → 首字占位（不是"缺图"，是不假装）
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(c.warning.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name.take(1),
                    style = t.bodyLarge,
                    color = c.warning,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = t.bodyLarge,
                    color = c.textPrimary,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // UP / 歪 只在判得出来时显示；判不出来留空，不猜
                when (isUp) {
                    true -> { Spacer(Modifier.width(s.s)); Pill("UP", c.success) }
                    false -> { Spacer(Modifier.width(s.s)); Pill("歪", c.danger) }
                    null -> Unit
                }
            }
            Spacer(Modifier.height(4.dp))
            // ---- 数据条：长度 = 垫抽数 / 本池最大垫抽数 ----
            DataBar(
                fraction = pity.toFloat() / maxPity,
                color = pityColor(pity),
            )
            Spacer(Modifier.height(2.dp))
            Text(text = date, style = t.caption, color = c.textTertiary)
        }

        Text(
            text = "${pity}抽",
            style = t.numericMedium,
            color = pityColor(pity),
        )
    }
}

/** 横向数据条：底轨 + 按比例填充。长度本身承载"垫了多少"这个信息 */
@Composable
private fun DataBar(fraction: Float, color: Color) {
    val c = LocalUbColors.current
    val r = LocalUbRadius.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(r.chip))
            .background(c.bgSubtle),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                .height(6.dp)
                .clip(RoundedCornerShape(r.chip))
                .background(color),
        )
    }
}

// ---------------------------------------------------------------------------
// UP 分析
// ---------------------------------------------------------------------------

@Composable
private fun UpCard(g: GachaInsights) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current

    SectionCard(title = "UP 分析") {
        Column(modifier = Modifier.padding(s.cardPad), verticalArrangement = Arrangement.spacedBy(s.m)) {
            if (!g.upStatsReliable) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(s.s),
                ) {
                    UbIcons.Icon(UbIcon.Info, tint = c.warning, size = 18.dp)
                    Text(text = "样本不足，暂不计算", style = t.bodyLarge, color = c.textPrimary)
                }
                Text(
                    text = "判定「这次出的五星是 UP 还是歪」需要一个常驻五星名单，" +
                        "而这份名单是从你的常驻池记录里推导出来的 —— " +
                        "目前只推导出 ${g.standardSampleSize} 个（需要至少 4 个）。",
                    style = t.caption,
                    color = c.textSecondary,
                )
                Text(
                    text = "名单不够大时，本来就在常驻池里的角色会被误判成 UP。" +
                        "实测这份数据会算出 89.5% 的不歪率 —— 那不是统计，是错的。" +
                        "在常驻池多抽几次、名单够了，这里会自动显示。",
                    style = t.caption,
                    color = c.textTertiary,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    BigStat("小保底不歪", g.winRate?.let { "${fmt(it * 100)}%" } ?: "—", c.accent)
                    BigStat("每 UP 角色需", g.upCharacterAvg?.let { fmt(it) } ?: "—", c.textPrimary)
                    BigStat("每 UP 武器需", g.upWeaponAvg?.let { fmt(it) } ?: "—", c.textPrimary)
                }
                Text(
                    text = "基于 ${g.eventFiveStarCount} 个活动池五星 · 常驻名单 ${g.standardSampleSize} 个（推导）",
                    style = t.caption,
                    color = c.textTertiary,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 五星总表
// ---------------------------------------------------------------------------

@Composable
private fun FiveStarCard(g: GachaInsights) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current
    val r = LocalUbRadius.current

    if (g.fiveStarPulls.isEmpty()) return
    val list = g.fiveStarPulls.reversed()

    SectionCard(title = "五星记录（${list.size}）") {
        Column {
            list.forEachIndexed { i, f ->
                if (i > 0) UbDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(s.cardPad),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(s.m),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(r.chip))
                            .background(pityColor(f.pityUsed).copy(alpha = 0.14f))
                            .padding(horizontal = s.s, vertical = s.xs),
                    ) {
                        Text(text = "${f.pityUsed}", style = t.numericMedium, color = pityColor(f.pityUsed))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = f.name, style = t.bodyLarge, color = c.textPrimary)
                        Text(
                            text = "${shortPool(f.poolType)} · ${shortDate(f.time)}",
                            style = t.caption,
                            color = c.textTertiary,
                        )
                    }
                    when (f.isUp) {
                        true -> Pill("UP", c.success)
                        false -> Pill("歪", c.danger)
                        null -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    val t = LocalUbTypography.current
    val r = LocalUbRadius.current
    val s = LocalUbSpacing.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(r.chip))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = s.s, vertical = 2.dp),
    ) {
        Text(text = text, style = t.labelMedium, color = color)
    }
}

// ---------------------------------------------------------------------------

/** 垫抽数配色：<40 欧（绿）、<63 正常（蓝）、<71 偏非（橙）、≥71 非（红） */
@Composable
private fun pityColor(n: Int): Color {
    val c = LocalUbColors.current
    return when {
        n < 40 -> c.success
        n < 63 -> c.accent
        n < 71 -> c.warning
        else -> c.danger
    }
}

internal fun shortPool(poolType: Int): String = when (poolType) {
    1 -> "角色活动"
    2 -> "武器活动"
    3 -> "角色常驻"
    4 -> "武器常驻"
    5 -> "新手"
    else -> "卡池 $poolType"
}

/** `2026-09-30 18:40:23` → `09-30` */
internal fun shortDate(time: String): String =
    if (time.length >= 10) time.substring(5, 10) else time

private fun fmt(v: Double): String = String.format(Locale.US, "%.1f", v)
