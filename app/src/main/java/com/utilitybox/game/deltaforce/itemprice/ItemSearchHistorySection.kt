package com.utilitybox.game.deltaforce.itemprice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.utilitybox.core.design.LocalUbColors
import com.utilitybox.core.design.LocalUbRadius
import com.utilitybox.core.design.LocalUbSpacing
import com.utilitybox.core.design.LocalUbTypography
import com.utilitybox.core.design.UbTextButton
import com.utilitybox.core.design.ubClickable

/**
 * 搜索历史标签流（P11 补 53）。
 *
 * ### 用户要求
 * > 「应该加上**历史记录**，**多了会自动折叠**那种…
 * >   历史记录是**可以全部清除**的」
 *
 * ### 显示时机
 *
 * **只在搜索框为空时显示**（调用方控制）。
 * 一旦有了搜索结果，历史就不是用户此刻要看的东西了 ——
 * 那时候把它顶在结果上方，是拿屏幕空间换了一个用不上的东西。
 *
 * ### 折叠规则
 *
 * 最多一行左右（[COLLAPSED_COUNT] 条），超出显示「展开全部 N 条」；
 * 展开后再给一个「收起」。**不用滚动容器** ——
 * 历史的价值是"一眼看到最近搜的"，要滚就失去意义了。
 */
@Composable
fun ItemSearchHistorySection(
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current
    val r = LocalUbRadius.current

    val history = ItemSearchHistory.keywords
    if (history.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    val shown = if (expanded) history else history.take(COLLAPSED_COUNT)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = s.screenH, vertical = s.s),
        verticalArrangement = Arrangement.spacedBy(s.s),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "搜索历史", style = t.bodyLarge, color = c.textPrimary)
            // ★ 只清空历史，**不动当前搜索结果**（用户明确要求）。
            //   当前本来就没有结果才会显示这一块，所以这里的行为是纯粹的"清历史"。
            UbTextButton(label = "清除", onClick = { ItemSearchHistory.clear() })
        }

        // 标签流：自动换行，不用横向滚动（横滑会让人以为只有几个）
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(s.s),
            verticalArrangement = Arrangement.spacedBy(s.s),
        ) {
            shown.forEach { kw ->
                HistoryChip(text = kw, onClick = { onPick(kw) })
            }
        }

        if (history.size > COLLAPSED_COUNT) {
            UbTextButton(
                label = if (expanded) "收起" else "展开全部 ${history.size} 条",
                onClick = { expanded = !expanded },
            )
        }
    }
}

/** 历史标签：胶囊、浅底、点击即搜 */
@Composable
private fun HistoryChip(text: String, onClick: () -> Unit) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current
    val r = LocalUbRadius.current

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .clip(RoundedCornerShape(r.capsule))
            .background(c.bgElevated)
            .ubClickable(scale = com.utilitybox.core.design.UbPressScale.Row, onClick = onClick)
            .padding(horizontal = s.l, vertical = s.s),
    ) {
        Text(text = text, style = t.bodyLarge, color = c.textSecondary)
    }
}

/**
 * 折叠时显示几条。
 *
 * ★ 8 的依据：常见物品名 2~6 个字，8 条大约占两行 ——
 *   超过两行就不叫"一眼看到"了。
 */
private const val COLLAPSED_COUNT = 8
