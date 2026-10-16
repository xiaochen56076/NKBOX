package com.utilitybox.game.deltaforce.pricerange

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.utilitybox.core.design.*
import com.utilitybox.domain.mock.MockItemPriceSource

/**
 * 价格区间统计（**实验性**）。
 *
 * ★ 本工具的首要目的在于**验证扩展机制**：
 *   它是照 architecture.md §4.4 的清单新增的 —— 新建包 + 页面 + provider + 注册 1 行。
 *   `MainActivity` / `HomeScreen` / `UbToolRegistry` / `UbNavigator` /
 *   `UbRoutes` / `UbIcons` / `UbBottomBar` **一行都没有改**。
 *
 * ★ 数据为**模拟数据**，因此界面带 `UbMockBadge`，工具状态标为 `EXPERIMENTAL`。
 *   接真实数据属 P07 之后的阶段。
 */
@Composable
fun PriceRangeScreen(modifier: Modifier = Modifier) {
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current
    val c = LocalUbColors.current

    val all = remember { MockItemPriceSource.items() }
    // ★ 只统计**有成交数据**的物品。
    //   无成交的物品若被当成 0 元计入，会把整个价格分布拉偏 ——
    //   这是红线「无成交不是 0 元」在本工具上的具体体现。
    val priced = remember(all) { all.filter { it.avgPrice != null } }

    val buckets = remember(priced) {
        listOf(
            "1 万以下" to priced.count { it.avgPrice!! < 10_000 },
            "1 万 – 5 万" to priced.count { it.avgPrice!! in 10_000 until 50_000 },
            "5 万 – 20 万" to priced.count { it.avgPrice!! in 50_000 until 200_000 },
            "20 万以上" to priced.count { it.avgPrice!! >= 200_000 },
        )
    }

    // ★ 闸门声明在 LazyColumn **之外**：LazyListScope 的 lambda 不是 @Composable 上下文
    val entrance = rememberUbListEntrance()

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = s.screenH,
            end = s.screenH,
            bottom = LocalUbSize.current.bottomBarReserved,
        ),
        verticalArrangement = Arrangement.spacedBy(s.listGap),
    ) {
        item(key = "badge") {
            Box(modifier = Modifier.padding(top = s.s)) { UbMockBadge() }
        }

        item(key = "hint") {
            Text(
                text = "按当前价格分布统计，共 ${priced.size} 件有成交数据的物品。" +
                    "无成交数据的物品不计入，避免把「没有数据」当成 0 元拉偏区间。",
                style = t.caption,
                color = c.textTertiary,
            )
        }

        itemsIndexed(buckets, key = { _, it -> it.first }) { index, (label, count) ->
            BucketCard(
                label = label,
                count = count,
                total = priced.size,
                // ★ P11 补 14：入场闸门（与其它列表一致）
                modifier = Modifier.ubListEnter(label, index, entrance),
            )
        }
    }
}

@Composable
private fun BucketCard(label: String, count: Int, total: Int, modifier: Modifier = Modifier) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current
    val r = LocalUbRadius.current
    val ratio = if (total == 0) 0f else count.toFloat() / total

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(r.card))
            .background(c.bgElevated)
            .padding(s.cardPad),
        verticalArrangement = Arrangement.spacedBy(s.s),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = t.bodyLarge, color = c.textPrimary)
            Text(
                text = "$count 件 · ${(ratio * 100).toInt()}%",
                style = t.labelMedium,
                color = c.textSecondary,
            )
        }
        // 占比条
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(r.capsule))
                .background(c.bgSubtle),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio)
                    .height(6.dp)
                    .clip(RoundedCornerShape(r.capsule))
                    .background(c.accent),
            )
        }
    }
}
