package com.utilitybox.game.deltaforce.itemprice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
// ---- 价格历史图表（Vico 2.x，P11 补 50）----
// ★ 这里的每一项都是**从 aar 里用 javap 读出来的**，不是凭记忆写的：
//   Vico 2.x 的 API 和 1.x 差别很大（rememberCartesianChart / CartesianChartHost /
//   lineSeries 在 1.x 里根本不存在），凭记忆写会编译错一片。
import androidx.compose.runtime.LaunchedEffect
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberToggleOnTap
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import androidx.compose.ui.graphics.toArgb
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.core.common.Fill
import com.patrykandpatrick.vico.core.common.Insets
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarkerController
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.utilitybox.core.design.*
import com.utilitybox.domain.model.AppError
import com.utilitybox.domain.model.ItemPriceDetailState
import com.utilitybox.domain.model.ItemSummary
import com.utilitybox.domain.model.PriceHistory
import com.utilitybox.domain.model.PriceTrend
import java.time.format.DateTimeFormatter

/**
 * 物品详情页（P07：已接真实数据源）。
 *
 * ★ 三条红线在这里的落地方式：
 *   1. `hasValidPriceData: false` ⇒ **「暂无成交数据」**，绝不显示 0 为真实价格
 *   2. 历史缺失点在图上**断开**，不插值（[PriceChart] 内部保证）
 *   3. 有效点不足 2 个时**不画线**，给说明而不是伪装成一条平线
 *
 * ★ 原型期的"图表三模式切换"开关已移除 —— 现在图表由真实数据驱动，
 *   缺失与不足两种情况都来自数据源本身，不需要人工构造。
 */
@Composable
fun ItemDetailScreen(
    summary: ItemSummary,
    modifier: Modifier = Modifier,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current
    val holder = rememberItemDetailHolder(summary)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = s.screenH),
        verticalArrangement = Arrangement.spacedBy(s.m),
    ) {
        // 标题区（名称来自列表，无需再查一次）
        //
        // ★ P11 补 52（用户要求「UI 设计再好看些，参考苹果，要对齐」）：
        //   原来这里只有「56dp 方块 + 名称 + 灰星级」，问题在于：
        //   · 图标和文字**没有视觉重心**：图标是死的灰块，文字没有主次
        //   · 星级和分类用同一个灰度 —— 稀有度是要一眼看出来的信息，不该是灰的
        //   · 整体贴着屏幕左边，缺少页面级的呼吸
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = s.s),
            horizontalArrangement = Arrangement.spacedBy(s.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 图标：没有网址时保留灰底占位，不塞通用图标（会让人误以为那就是该物品）
            if (summary.iconUrl != null) {
                UbItemIcon(
                    url = summary.iconUrl,
                    contentDescription = summary.name,
                    size = 64.dp,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(LocalUbRadius.current.card))
                        .background(c.bgSubtle),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(s.xs),
            ) {
                Text(text = summary.name, style = t.titleLarge, color = c.textPrimary)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(s.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    summary.category?.let {
                        Text(text = it, style = t.caption, color = c.textSecondary)
                    }
                    // ★ 星级用稀有度金色 —— 与列表页/统计页同一套语言，
                    //   不再和分类文字一样是灰的（那样等于没表达稀有度）
                    summary.grade?.let {
                        Text(text = "★$it", style = t.caption, color = c.warning)
                    }
                }
            }
        }

        when (val st = holder.state) {
            ItemPriceDetailState.Idle -> Box(
                modifier = Modifier.fillMaxWidth().padding(top = s.xl),
                contentAlignment = Alignment.Center,
            ) {
                UbLoading(lines = 5)
            }

            // ★ 红线：无成交数据 → 独立呈现。不是错误，更不是 0
            ItemPriceDetailState.NoPriceData -> Box(
                modifier = Modifier.fillMaxWidth().padding(top = s.xl),
                contentAlignment = Alignment.Center,
            ) {
                UbEmpty(
                    title = "暂无成交数据",
                    description = "数据源没有该物品的有效成交记录。" +
                        "这不代表价格是 0，而是近期没有成交，因此无法给出价格。",
                    actionLabel = "重新加载",
                    onAction = { holder.refresh() },
                )
            }

            is ItemPriceDetailState.Failed -> Box(
                modifier = Modifier.fillMaxWidth().padding(top = s.xl),
                contentAlignment = Alignment.Center,
            ) {
                UbError(
                    title = detailErrorTitle(st.error),
                    description = detailErrorDesc(st.error),
                    // "没配 Key"重试没用，不给按钮
                    onRetry = if (st.error == AppError.ApiKeyMissing) null else ({ holder.refresh() }),
                )
            }

            is ItemPriceDetailState.Ready -> {
                CurrentPriceCard(st.detail)

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(s.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "价格历史", style = t.titleMedium, color = c.textPrimary)
                        Box(modifier = Modifier.weight(1f))
                        UbChip(label = "刷新", selected = false, onClick = { holder.refresh() })
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = s.s)
                            .clip(RoundedCornerShape(LocalUbRadius.current.card))
                            .background(c.bgElevated)
                            .padding(s.cardPad),
                    ) {
                        val history = st.history
                        if (history != null) {
                            PriceChart(history = history)
                        } else {
                            // 历史取不到：如实说明，并区分"请求失败"与"确实没有历史"
                            Column(verticalArrangement = Arrangement.spacedBy(s.xs)) {
                                Text(
                                    text = if (st.historyError != null) {
                                        "历史价格暂时取不到"
                                    } else {
                                        "暂无历史价格"
                                    },
                                    style = t.bodyLarge,
                                    color = c.textSecondary,
                                )
                                Text(
                                    text = if (st.historyError != null) {
                                        "原因：${detailErrorDesc(st.historyError)}" +
                                            "当前价格不受影响，可点上方「刷新」重试。"
                                    } else {
                                        "数据源没有返回该物品的历史记录。"
                                    },
                                    style = t.caption,
                                    color = c.textTertiary,
                                )
                            }
                        }
                    }
                }

                UbSourceFooter(
                    source = st.detail.source.displayName,
                    updatedAt = st.detail.priceUpdatedAtLabel ?: "未知",
                    isMock = st.detail.isMock,
                    modifier = Modifier.padding(bottom = LocalUbSize.current.bottomBarReserved),
                )
            }
        }
    }
}

private fun detailErrorTitle(error: AppError): String = when (error) {
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

private fun detailErrorDesc(error: AppError): String = when (error) {
    AppError.ApiKeyMissing ->
        "行情数据需要数据源的 API Key。请在底部「设置」→「API Key」里填写后重试。"
    AppError.Unauthorized ->
        "已填写的 API Key 被数据源拒绝了，请到「设置」→「API Key」检查或更换。"
    AppError.NoNetwork -> "请检查网络连接后重试。"
    AppError.Timeout -> "数据源响应较慢，可以直接重试。"
    AppError.Malformed -> "数据源返回的内容与预期结构不一致，可能是对方已改版。"
    is AppError.Business ->
        "错误码 ${error.code}：${error.message.ifBlank { "（对方未提供说明）" }}"
    is AppError.Server -> "数据源返回 HTTP ${error.code}，通常是对方临时故障。"
    AppError.SourceUnavailable -> "当前没有可用的数据源来提供这项数据。"
    is AppError.Unknown -> error.message.ifBlank { "发生了未预期的错误。" }
}

@Composable
private fun CurrentPriceCard(detail: com.utilitybox.domain.model.ItemDetail) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current
    val variant = LocalUbGlassVariantTier.current

    UbGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LocalUbRadius.current.cardLarge),
        tier = variant,
        blurRadius = 22.dp,
    ) {
        Column(
            // ★ P11 补 52：上下留白加大 —— 大数值是这一屏的视觉主角，
            //   原来 `cardPad` 四周等距，数字像被挤在盒子里。
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = s.cardPad, vertical = s.l),
            verticalArrangement = Arrangement.spacedBy(s.xs),
        ) {
            // ★ 标签与时间**同一行、分列两端** —— 这比"标签 / 大数字 / 时间"三行堆叠
            //   更干净：时间属于元信息，不该和主数值抢纵向空间。
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "当前均价", style = t.caption, color = c.textSecondary)
                detail.priceUpdatedAtLabel?.let {
                    Text(text = "更新于 $it", style = t.caption, color = c.textTertiary)
                }
            }
            if (detail.latestPrice != null && detail.hasValidPriceData) {
                Text(
                    text = formatPrice(detail.latestPrice),
                    style = t.numericLarge,
                    color = c.textPrimary,
                )
            } else {
                // ★ 不得显示 0 当作真实价格
                Text(text = "暂无成交数据", style = t.titleMedium, color = c.textTertiary)
                Text(
                    text = "该物品近期没有可用的成交记录，因此无法给出价格。",
                    style = t.caption,
                    color = c.textTertiary,
                )
            }
        }
    }
}

/**
 * 折线图。
 *
 * 实现要点（对齐红线）：
 *  - 缺失点（`avgPrice == null`）→ **打断**折线，而不是连过去
 *  - 有效点 < 2 → 不画线，显示说明
 *  - 所有点直接来自 [PriceHistory.points]，**无任何插值/平滑/拟合**
 */
@Composable
private fun PriceChart(history: PriceHistory) {
    val c = LocalUbColors.current
    val t = LocalUbTypography.current
    val s = LocalUbSpacing.current

    val valid = history.points.mapNotNull { it.avgPrice }
    if (history.points.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
            Text(text = "暂无历史价格记录", style = t.bodyMedium, color = c.textTertiary)
        }
        return
    }
    if (valid.size < 2) {
        Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "有效数据点不足，无法绘制趋势", style = t.bodyMedium, color = c.textTertiary)
                Text(
                    text = "只有 ${valid.size} 个有效记录，绘制折线会造成误导。",
                    style = t.caption,
                    color = c.textTertiary,
                )
            }
        }
        return
    }

    val minV = valid.min()
    val maxV = valid.max()
    val span = (maxV - minV).coerceAtLeast(1L)

    Column(verticalArrangement = Arrangement.spacedBy(s.s)) {
        // ★★ P11 补 50：手绘 Canvas → Vico 2.5.2。
        //
        //   换掉的理由有两条实证（见 `libs.versions.toml` 的 vico 条目）：
        //   1. 手绘版坐标轴要自己排版，**数字过长会挤成竖排**
        //      （真机截图：`31,947,82832,568,813…` 下面跟着竖排的 `4 8 , 9 1 0 / 1 5`）
        //   2. 没有"点击某点看数值"的交互
        //
        // ### ★ 最重要的：**缺失点仍然必须是断线**
        //
        //   项目红线写明「图表只画真实点，缺失处留空」。
        //   而 Vico 的 `series(x, y)` 会把给它的点**依次连起来** ——
        //   如果我把有效点挑出来塞成一条线，中间缺失的那几天就会被**连起来**，
        //   看起来像"那几天有价格"，**那就是编数据**。
        //
        //   所以这里按**连续有效段**拆成多条 series：
        //   每一段单独 `series(...)`，段与段之间 Vico 自然不连线 —— 缺口得以保留。
        //   x 用**原始下标**（不是压缩后的序号），所以缺口在轴上的**位置也是真的**。
        val modelProducer = remember { CartesianChartModelProducer() }
        LaunchedEffect(history) {
            modelProducer.runTransaction {
                lineSeries {
                    // 切出连续有效段
                    var runX = mutableListOf<Number>()
                    var runY = mutableListOf<Number>()
                    history.points.forEachIndexed { idx, p ->
                        val v = p.avgPrice
                        if (v != null) {
                            runX.add(idx)
                            runY.add(v)
                        } else if (runY.isNotEmpty()) {
                            series(x = runX, y = runY)
                            runX = mutableListOf()
                            runY = mutableListOf()
                        }
                    }
                    if (runY.isNotEmpty()) series(x = runX, y = runY)
                }
            }
        }

        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(),
                // ★ P11 补 58：上一版**没有任何可见变化** —— 两次截图里文字都是**蓝色**，
                //   而我传的是 `color = c.textPrimary`（浅色主题下应为深色）。
                //   说明 `label` 这套参数**没有被应用到画出来的标签上**，
                //   那么 `background` 自然也没生效。**我原来的假设是错的。**
                //
                //   这一版换成**强对比**的深色卡片（反色 tooltip）：
                //   即使颜色语义不对，也能一眼看出背景**到底画没画** ——
                //   先区分"参数没生效"和"生效了但看不见"，再谈调色。
                marker = rememberDefaultCartesianMarker(
                    label = rememberTextComponent(
                        color = c.bgElevated,
                        background = rememberShapeComponent(
                            fill = Fill(color = c.textPrimary.toArgb()),
                            shape = CorneredShape.rounded(allDp = 8f),
                        ),
                        padding = Insets(allDp = 8f),
                    ),
                ),
                // ★ 交互选 `rememberToggleOnTap`（点一下显示、再点隐藏），
                //   而不是 `rememberShowOnPress`（按住才显示）：
                //   用户要的是"点一下看数值" —— 按住看的话，手指一松就没了，
                //   想对着数字看两眼都做不到。
                markerController = CartesianMarkerController.rememberToggleOnTap(),
            ),
            modelProducer = modelProducer,
            modifier = Modifier.fillMaxWidth().height(170.dp),
        )

        // ★ P11 补 50：原来这里有一行「逐点铺数值 + 日期」。
        //
        //   它承载过 P11 补 49 的修复（紧凑记法 + 横向滚动）——
        //   那是在**手绘图表的约束下**能做的极限。换成 Vico 之后，
        //   数值由**坐标轴自己**排版（轴标签会避让、不会挤成竖排），
        //   所以这一行连同它的两个补丁**一起删掉** —— 留着就是重复信息。
        //
        //   ★ 但"缺失日要有交代"这件事不能丢：缺口在图上是断线，
        //     下方趋势结论里那句「缺失 N 天（图上已断开）」仍然保留。

        // 趋势结论：数据不足时明确说"无法判断"，不伪装持平
        //
        // ★ P11 补 51（用户要求）：**红跌绿涨**。
        //   之前这里是不管涨跌都用 `textTertiary`（灰），
        //   等于把"跌了 6.7%"和"涨了 6.7%"画成同一个颜色 —— 涨跌是要一眼看出来的信息。
        //   用户明确「红色是跌，绿色是涨」，与同类产品一致。
        val trendLabel = when (history.trend) {
            PriceTrend.UP -> "较首日上涨"
            PriceTrend.DOWN -> "较首日下跌"
            PriceTrend.FLAT -> "与首日持平"
            PriceTrend.UNKNOWN -> "有效数据点不足，无法判断趋势"
        }
        // FLAT / UNKNOWN 没有方向，用中性色 —— 不给它安一个涨或跌的颜色
        val trendColor = when (history.trend) {
            PriceTrend.UP -> c.success
            PriceTrend.DOWN -> c.danger
            PriceTrend.FLAT, PriceTrend.UNKNOWN -> c.textTertiary
        }
        val pct = history.changePercent
        Text(
            text = buildString {
                append(trendLabel)
                if (pct != null) append("（${"%+.1f".format(pct)}%）")
                val missing = history.points.count { it.avgPrice == null }
                if (missing > 0) append("　·　缺失 $missing 天（图上已断开）")
            },
            style = t.caption,
            color = trendColor,
        )
    }
}
