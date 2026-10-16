package com.utilitybox.game.deltaforce.itemprice

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.utilitybox.core.design.*
import com.utilitybox.domain.model.AppError
import com.utilitybox.domain.model.ItemSummary
import com.utilitybox.domain.model.looksLikeSkin
import com.utilitybox.domain.model.UiState

/**
 * 物品物价页：搜索 + 列表（P07：已接真实数据源）。
 *
 * ★ 红线落地：`avgPrice == null` 表示**无成交数据**，必须显示「暂无成交数据」，
 *   **绝不能显示 0**（PROJECT_RULES.md §3.6）。
 *
 * ★ 搜索**不是本地过滤**，而是调数据源接口 —— 物品库远超本地可缓存规模，
 *   本地过滤只能搜到"已经看过的东西"，不是真正的搜索。防抖在 [ItemListHolder] 里。
 */
@Composable
fun ItemPriceScreen(
    onOpenDetail: (ItemSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val r = LocalUbRadius.current
    val t = LocalUbTypography.current
    val motion = LocalUbMotion.current
    val holder = rememberItemListHolder()

    // ★ P11 补 54：离开「物品物价」工具时清空搜索状态；
    //   但工具**内部**跳转（去详情页）时保留 —— 用户回来还要接着看列表。
    //
    //   `onDispose` 只知道"我要没了"，不知道"我要去哪"，
    //   所以判断放在 [onItemPriceScreenDisposed] 里，依据是同步更新的当前路由。
    DisposableEffect(Unit) {
        onDispose { onItemPriceScreenDisposed() }
    }

    // 「隐藏皮肤」—— 默认**关闭**：宁可多显示，也不替用户藏东西
    var hideSkins by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        // 搜索框
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = s.screenH, vertical = s.xs)
                .clip(RoundedCornerShape(r.field))
                .background(c.bgElevated)
                .padding(horizontal = s.m, vertical = s.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(s.s),
        ) {
            UbIcons.Icon(icon = UbIcon.Search, tint = c.textTertiary, size = 18.dp)
            Box(modifier = Modifier.weight(1f)) {
                if (holder.keyword.isEmpty()) {
                    Text(
                        text = "搜索物品名称，例如「钛」或「钢」",
                        style = t.bodyLarge,
                        color = c.textTertiary,
                    )
                }
                BasicTextField(
                    value = holder.keyword,
                    onValueChange = { holder.onKeywordChange(it) },
                    textStyle = t.bodyLarge.copy(color = c.textPrimary),
                    cursorBrush = SolidColor(c.accent),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        when (val ui = holder.state) {
            // 未输入关键字：这不是"没找到"，而是"还没开始找"
            UiState.Empty -> Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                // ★ P11 补 53：有搜索历史时**先把历史给出来**。
                //
                //   理由：复搜一个搜过的词是最常见的操作 ——
                //   让用户少打一次字，比让他看一句"输入名称开始搜索"有用。
                //   没有历史时才显示那条空态提示（也就没有"历史"这块可显示）。
                if (ItemSearchHistory.keywords.isNotEmpty()) {
                    ItemSearchHistorySection(
                        onPick = { holder.onKeywordChange(it) },
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        UbEmpty(
                            title = "输入名称开始搜索",
                            description = "物品库较大，按名称搜索比翻列表更快。",
                        )
                    }
                }
            }
            UiState.Loading -> Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                UbLoading(lines = 5)
            }

            is UiState.Failed -> Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                UbError(
                    title = searchErrorTitle(ui.error),
                    description = searchErrorDesc(ui.error),
                    // "没配 Key"重试也没用，不给按钮
                    onRetry = if (ui.error == AppError.ApiKeyMissing) {
                        null
                    } else {
                        { holder.retryNow() }
                    },
                )
            }

            is UiState.Success -> {
                val skins = ui.data.count { it.looksLikeSkin() }
                val shown = if (hideSkins) ui.data.filterNot { it.looksLikeSkin() } else ui.data

                Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    // 🌟 「隐藏皮肤」开关
                    //
                    // ★ 为什么是**开关**而不是直接过滤掉：
                    //   接口没有"这是皮肤"这个字段，识别靠名称里的连字符后缀，
                    //   属于**启发式判断**。启发式规则不该替用户做决定 ——
                    //   所以给开关 + 显示被过滤数量，让用户自己判断。
                    if (skins > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = s.screenH, vertical = s.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(s.s),
                        ) {
                            Text(
                                text = "共 ${ui.data.size} 条 · 其中疑似皮肤 $skins 条",
                                style = t.caption,
                                color = c.textTertiary,
                                modifier = Modifier.weight(1f),
                            )
                            UbChip(
                                label = if (hideSkins) "已隐藏皮肤" else "隐藏皮肤",
                                selected = hideSkins,
                                onClick = { hideSkins = !hideSkins },
                            )
                        }
                    }

                    if (shown.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            UbEmpty(
                                title = "结果里只剩皮肤",
                                description = "关掉「隐藏皮肤」就能看到它们。",
                                actionLabel = "显示全部",
                                onAction = { hideSkins = false },
                            )
                        }
                    } else {
                        // 入场闸门：只有数据到达时那一屏元素播入场，滚动进入的不播
                        val entrance = rememberUbListEntrance()
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = s.screenH,
                                end = s.screenH,
                                top = s.xs,
                                bottom = LocalUbSize.current.bottomBarReserved,
                            ),
                            verticalArrangement = Arrangement.spacedBy(s.listGap),
                        ) {
                            itemsIndexed(shown, key = { _, it -> it.objectId }) { index, item ->
                                // ★ 列表行：弱按压档。
                                //   `animateItem` 负责**搜索结果变化时的增删位移动画**
                                //   —— 时长梯度取自报告 §5.3⑥ 的 ItemAnimator 调优：
                                //   入场 150ms、离场 100ms（对应 setAddDuration / setRemoveDuration）。
                                //
                                // ★★ P11 补 13：这里**移除过** `ubEnter(index = index)`。
                                //   113 项的长列表套入场错峰 = 滑到第 30 项要等 1.3 秒
                                //   才从 alpha 0 显形（第 100 项 4.1 秒）——
                                //   用户原话「滑动过快就会造成全白，要等一下才能加载出来」。
                                //
                                // ★★ P11 补 14：换成 `ubListEnter` 把动画**要回来**，
                                //   同时不再复发那个 bug —— 它只在"入场闸门"还开着时播，
                                //   也就是只有数据到达时的第一屏会播；滚动进来的元素直接显示。
                                ItemRow(
                                    item = item,
                                    onClick = { onOpenDetail(item) },
                                    modifier = Modifier
                                        .ubListEnter(item.objectId, index, entrance)
                                        .animateItem(
                                            fadeInSpec = tween(
                                                durationMillis = motion.press,
                                                easing = UbEaseEnter,
                                            ),
                                            fadeOutSpec = tween(
                                                durationMillis = 100,
                                                easing = UbEaseExit,
                                            ),
                                            // 位置变化用弹簧：报告明确说"内容变化不要做动画以防闪屏"，
                                            // 但**位置**变化是必要的（否则筛选后整片瞬移）
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

        UbSourceFooter(
            source = "社区接口（ams）· 价格为均价",
            updatedAt = "实时查询",
            // 真实数据，不再有 mock 标记 —— 这条曾经是红线要求
            isMock = false,
            modifier = Modifier.padding(bottom = s.l),
        )
    }
}

/** 搜索失败时的标题。按错误类型给不同说明，与今日密码保持一致的语义。 */
private fun searchErrorTitle(error: AppError): String = when (error) {
    AppError.ApiKeyMissing -> "还没填 API Key"
    AppError.Unauthorized -> "API Key 未被接受"
    AppError.NoNetwork -> "网络不可用"
    AppError.Timeout -> "请求超时"
    AppError.Malformed -> "数据格式无法识别"
    is AppError.Business -> "数据源返回错误"
    is AppError.Server -> "数据源服务异常"
    AppError.SourceUnavailable -> "该功能暂无数据源"
    is AppError.Unknown -> "搜索失败"
}

private fun searchErrorDesc(error: AppError): String = when (error) {
    AppError.ApiKeyMissing ->
        "物品搜索需要数据源的 API Key。请在底部「设置」→「API Key」里填写后重试。"
    AppError.Unauthorized ->
        "已填写的 API Key 被数据源拒绝了，请到「设置」→「API Key」检查或更换。"
    AppError.NoNetwork -> "请检查网络连接后重试。"
    AppError.Timeout -> "数据源响应较慢，可以直接重试。"
    AppError.Malformed ->
        "数据源返回的内容与预期结构不一致，可能是对方已改版。" +
            "这属于数据源侧的变化，不是你的网络问题。"
    is AppError.Business ->
        "错误码 ${error.code}：${error.message.ifBlank { "（对方未提供说明）" }}"
    is AppError.Server -> "数据源返回 HTTP ${error.code}，通常是对方临时故障，稍后重试。"
    AppError.SourceUnavailable -> "当前没有可用的数据源来提供这项数据。"
    is AppError.Unknown -> error.message.ifBlank { "发生了未预期的错误。" }
}

@Composable
private fun ItemRow(item: ItemSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val r = LocalUbRadius.current
    val t = LocalUbTypography.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(r.card))
            .background(c.bgElevated)
            // ★ P11：裸 clickable → 统一按压反馈（此前按下去没有任何反应）
            .ubClickable(scale = UbPressScale.Row, onClick = onClick)
            .padding(s.cardPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s.m),
    ) {
        // ★ 真实图片：接口 `prePic`/`pic` 指向**腾讯官方图床**，非第三方服务器。
        //   （此前这里是纯色块占位，因为没有图片加载组件。）
        UbItemIcon(
            url = item.iconUrl,
            contentDescription = item.name,
            size = 48.dp,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = item.name, style = t.bodyLarge, color = c.textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(s.s)) {
                item.category?.let { Text(text = it, style = t.caption, color = c.textTertiary) }
                item.grade?.let { Text(text = "★$it", style = t.caption, color = c.textTertiary) }
                // ★ 皮肤是启发式判断，所以**标注出来**让用户知道为什么这条会被过滤，
                //   而不是悄悄按一个我们猜的规则把结果藏掉
                if (item.looksLikeSkin()) {
                    Text(text = "疑似皮肤", style = t.caption, color = c.textTertiary)
                }
            }
        }
        if (item.avgPrice != null) {
            Text(
                text = formatPrice(item.avgPrice),
                style = t.numericMedium,
                color = c.textPrimary,
            )
        } else {
            // ★ 不得显示 0 —— 必须明确说明"没有数据"
            Text(text = "暂无成交数据", style = t.caption, color = c.textTertiary)
        }
    }
}

internal fun formatPrice(v: Long): String =
    v.toString().reversed().chunked(3).joinToString(",").reversed()
