package com.utilitybox.game.deltaforce.ammoprice

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.utilitybox.core.design.*
import com.utilitybox.domain.model.AmmoPrice
import com.utilitybox.domain.model.AppError
import com.utilitybox.domain.model.UiState

/**
 * 弹药物价页（按用户反馈重做）。
 *
 * 用户提出的四点，逐条对应：
 *
 * 1. **分类筛选** → 口径分组横向 Chip + 击穿等级筛选
 * 2. **显示等级** → 显示 `ammoDetail.penetrationLevel`，这才是玩家说的"一级弹/二级弹"。
 *    ★ 它与稀有度 ★1–★6 是**两个不同概念**，所以两者都显示、分别标注，
 *      避免用户把 ★6 当成"6 级弹"。
 * 3. **显示图片** → 用接口的 `prePic`/`pic`（**腾讯官方图床**），
 *    由自写的 [UbItemIcon] 加载（零新增依赖）
 * 4. **搜索** → 按名称或口径实时过滤
 */
@Composable
fun AmmoPriceScreen(modifier: Modifier = Modifier) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current
    val motion = LocalUbMotion.current
    val holder = rememberAmmoPriceHolder()

    // ★ 搜索与筛选状态放在页面级：改筛选条件不该重新请求数据
    var query by remember { mutableStateOf("") }
    var caliberFilter by remember { mutableStateOf<String?>(null) }
    var penetrationFilter by remember { mutableStateOf<Int?>(null) }

    val all = (holder.state as? UiState.Success)?.data.orEmpty()

    // 筛选选项**从真实数据归纳**，不写死 —— 接口新增口径会自动出现
    val calibers = remember(all) { all.mapNotNull { it.caliberGroup }.distinct().sorted() }
    val penetrations = remember(all) { all.mapNotNull { it.penetrationLevel }.distinct().sorted() }

    val filtered = remember(all, query, caliberFilter, penetrationFilter) {
        all.filter { a ->
            val hitQuery = query.isBlank() ||
                a.name.contains(query, ignoreCase = true) ||
                (a.caliber?.contains(query, ignoreCase = true) == true)
            hitQuery &&
                (caliberFilter == null || a.caliberGroup == caliberFilter) &&
                (penetrationFilter == null || a.penetrationLevel == penetrationFilter)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // ---- 我的搜索框 ----
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = s.screenH, vertical = s.xs),
            singleLine = true,
            placeholder = {
                Text("搜索弹药名称或口径", style = t.bodyMedium, color = c.textTertiary)
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )

        // ---- 口径筛选 ----
        if (calibers.size > 1) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = s.screenH),
                horizontalArrangement = Arrangement.spacedBy(s.xs),
            ) {
                item {
                    UbChip(
                        label = "全部口径",
                        selected = caliberFilter == null,
                        onClick = { caliberFilter = null },
                    )
                }
                items(calibers) { cal ->
                    UbChip(
                        label = cal,
                        selected = caliberFilter == cal,
                        onClick = { caliberFilter = if (caliberFilter == cal) null else cal },
                    )
                }
            }
        }

        // ---- 击穿等级筛选（用户说的"一级/二级"）----
        if (penetrations.size > 1) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = s.xs),
                contentPadding = PaddingValues(horizontal = s.screenH),
                horizontalArrangement = Arrangement.spacedBy(s.xs),
            ) {
                item {
                    UbChip(
                        label = "全部等级",
                        selected = penetrationFilter == null,
                        onClick = { penetrationFilter = null },
                    )
                }
                items(penetrations) { lv ->
                    UbChip(
                        label = "$lv 级弹",
                        selected = penetrationFilter == lv,
                        onClick = {
                            penetrationFilter = if (penetrationFilter == lv) null else lv
                        },
                    )
                }
            }
        }

        // ---- 计数 + 刷新 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = s.screenH, vertical = s.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val filtering = query.isNotBlank() || caliberFilter != null || penetrationFilter != null
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        all.isEmpty() -> "弹药 · 近 7 天成交均价"
                        filtering -> "匹配 ${filtered.size} 种 · 共 ${all.size} 种"
                        else -> "共 ${all.size} 种"
                    },
                    style = t.bodyMedium,
                    color = c.textSecondary,
                )
                // ★ 涨跌**不可用**的提示（不是"价格不可用"）。
                //
                //   实测：顶层 avgPrice 有值（104/113 条），但 priceStats.dataPoints 全为 0
                //   —— 说明有成交价、却没有可用于比较的历史样本。
                //   所以涨跌幅一律不显示。
                //
                //   ⚠️ 这里曾写错成"价格暂时无法获取"，与同一屏上的真实价格自相矛盾。
                //      提示文字必须与屏幕上的实际内容一致，否则比不提示更糟。
                if (all.isNotEmpty() && all.all { (it.dataPoints ?: 0) <= 0 } &&
                    all.any { it.latestPrice != null }
                ) {
                    Text(
                        text = "该数据源没有历史样本，暂不显示涨跌",
                        style = t.caption,
                        color = c.textTertiary,
                    )
                }
            }
            UbChip(
                label = if (holder.isLoading) "加载中…" else "刷新",
                selected = false,
                onClick = { if (!holder.isLoading) holder.refresh() },
            )
        }

        // ---- 主体 ----
        when (val ui = holder.state) {
            UiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) { UbLoading(lines = 6) }

            UiState.Empty -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                UbEmpty(
                    title = "暂无弹药数据",
                    description = "数据源没有返回任何弹药条目。",
                    actionLabel = "重新加载",
                    onAction = { holder.refresh() },
                )
            }

            is UiState.Failed -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                UbError(
                    title = ammoErrorTitle(ui.error),
                    description = ammoErrorDesc(ui.error),
                    onRetry = if (ui.error == AppError.ApiKeyMissing) null else ({ holder.refresh() }),
                )
            }

            is UiState.Success -> if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    UbEmpty(
                        title = "没有匹配的弹药",
                        description = "试试换个关键词，或清掉筛选条件。",
                        actionLabel = "清除筛选",
                        onAction = {
                            query = ""
                            caliberFilter = null
                            penetrationFilter = null
                        },
                    )
                }
            } else {
                // 入场闸门：只有数据到达时那一屏元素播入场，滚动进入的不播
                val entrance = rememberUbListEntrance()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(
                        start = s.screenH,
                        end = s.screenH,
                        top = s.xs,
                        bottom = LocalUbSize.current.bottomBarReserved,
                    ),
                    verticalArrangement = Arrangement.spacedBy(s.listGap),
                ) {
                    itemsIndexed(filtered, key = { _, it -> it.objectId }) { index, ammo ->
                        // ★ 与物品列表用**同一套**增删参数（连贯性）：
                        //   筛选变化时 150ms 进 / 100ms 出 + 弹簧归位。
                        //
                        // ★★ P11 补 13：与物品列表**同时移除**了 `ubEnter(index = index)`。
                        //   两处必须一起改 —— 只改一处就会出现"物品列表顺滑、弹药物价卡白"，
                        //   那比两个都慢更糟，因为用户会以为其中一个坏了。
                        //
                        // ★★ P11 补 14：换 `ubListEnter` 把动画要回来（与物品列表同步改），
                        //   只有数据到达时的第一屏会播，滚动进来的元素直接显示。
                        AmmoRow(
                            ammo = ammo,
                            modifier = Modifier
                                .ubListEnter(ammo.objectId, index, entrance)
                                .animateItem(
                                    fadeInSpec = tween(motion.press, easing = UbEaseEnter),
                                    fadeOutSpec = tween(100, easing = UbEaseExit),
                                    placementSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AmmoRow(ammo: AmmoPrice, modifier: Modifier = Modifier) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current
    val r = LocalUbRadius.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(r.card))
            .background(c.bgElevated)
            .padding(s.cardPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s.m),
    ) {
        // ★ 图片：接口 prePic/pic 来自腾讯官方图床；无图时显示占位底色
        UbItemIcon(
            url = ammo.iconUrl,
            contentDescription = ammo.name,
            size = 48.dp,
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ammo.name,
                style = t.bodyLarge,
                color = c.textPrimary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            // ★ 元信息拆成两行放，避免窄屏上互相挤压换行
            //   （截图实测：四个标签挤在一行会让"护甲伤害 高"折行，很难看）
            Row(
                horizontalArrangement = Arrangement.spacedBy(s.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ammo.caliber?.let {
                    Text(
                        it,
                        style = t.caption,
                        color = c.textTertiary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                // ★ 击穿等级 —— 用户问的"一级还是二级"
                ammo.penetrationLevel?.let {
                    Text("$it 级弹", style = t.caption, color = c.accent)
                }
                // 稀有度是另一个概念，单独标注以免被误当成"几级弹"
                ammo.grade?.let { Text("★$it", style = t.caption, color = c.textTertiary) }
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            val price = ammo.latestPrice
            if (price != null) {
                Text(
                    text = formatAmmoPrice(price),
                    style = t.numericMedium,
                    color = c.textPrimary,
                )
            } else {
                // ★ 红线：无成交数据显示文字说明，绝不显示 0
                Text(text = "暂无成交数据", style = t.caption, color = c.textTertiary)
            }
            // ★ 涨跌幅只在**有样本**时显示。
            //   接口在无成交时会给 priceChangePercent: 0，
            //   直接显示会变成「↑ 0.0%」——那是在编"价格持平"的结论。
            ammo.changePercent?.let { pct ->
                val up = pct >= 0
                Text(
                    text = (if (up) "↑ " else "↓ ") + "%.1f%%".format(kotlin.math.abs(pct)),
                    style = t.caption,
                    color = if (up) c.danger else c.success,
                )
            }
        }
    }
}

internal fun formatAmmoPrice(v: Long): String =
    v.toString().reversed().chunked(3).joinToString(",").reversed()

private fun ammoErrorTitle(error: AppError): String = when (error) {
    AppError.ApiKeyMissing -> "还没填 API Key"
    AppError.Unauthorized -> "API Key 未被接受"
    AppError.NoNetwork -> "网络不可用"
    AppError.Timeout -> "请求超时"
    AppError.Malformed -> "数据格式无法识别"
    is AppError.Business -> "数据源返回错误"
    is AppError.Server -> "数据源服务异常"
    AppError.SourceUnavailable -> "该功能暂无数据源"
    is AppError.Unknown -> "加载失败"
}

private fun ammoErrorDesc(error: AppError): String = when (error) {
    AppError.ApiKeyMissing -> "请在底部「设置」→「API Key」里填写后重试。"
    AppError.Unauthorized -> "已填写的 Key 被拒绝，请到「设置」→「API Key」检查或更换。"
    AppError.NoNetwork -> "请检查网络连接后重试。"
    AppError.Timeout -> "数据源响应较慢，可以直接重试。"
    AppError.Malformed -> "数据源返回的结构与预期不一致，可能是对方已改版。"
    is AppError.Business -> "错误码 ${error.code}：${error.message.ifBlank { "（无说明）" }}"
    is AppError.Server -> "数据源返回 HTTP ${error.code}，通常是对方临时故障。"
    AppError.SourceUnavailable -> "当前没有可用的数据源提供这项数据。"
    is AppError.Unknown -> error.message.ifBlank { "发生了未预期的错误。" }
}
