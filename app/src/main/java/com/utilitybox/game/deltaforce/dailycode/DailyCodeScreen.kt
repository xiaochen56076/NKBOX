package com.utilitybox.game.deltaforce.dailycode

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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.utilitybox.core.design.*
import com.utilitybox.domain.model.AppError
import com.utilitybox.domain.model.DailyCode
import com.utilitybox.domain.model.DailyCodeState
import com.utilitybox.domain.model.UiState
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 今日密码页（P06：已接真实数据源）。
 *
 * ★ 红线落地（PROJECT_RULES.md §3.4/§3.5）：
 *   - 必须区分四种状态：当天有效 / 过期缓存 / 暂无数据 / 请求失败
 *   - **不得拿昨天的密码冒充今日密码** —— [DailyCodeState.Stale] 强制显示原始日期
 *   - 已实测：接口**不提供日期字段**，故 UI 必须写明判定依据
 *
 * ★ 四态判定**不在这里**，而在 [DailyCodeRepository]（已被 20 个单元用例覆盖）。
 *   本页只做"把状态画出来"，因此不重复实现任何业务规则。
 */
@Composable
fun DailyCodeScreen(modifier: Modifier = Modifier) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current
    val motion = LocalUbMotion.current
    val holder = rememberDailyCodeHolder()

    // ★★ 复制气泡由**页面**持有，作为浮层盖在内容之上。
    //
    //   为什么不在行内显示（第一版那样）：
    //   行在 `LazyColumn` 里，把气泡放进 item 会被列表裁剪，
    //   而且 item 移出屏幕时气泡会跟着消失 —— 实测**根本看不到**。
    //   改为页面级浮层：位置稳定、不受滚动与裁剪影响。
    var copiedCode by remember { mutableStateOf<String?>(null) }

    // ★ 诊断日志：确认复制事件是否真的到达了页面级状态。
    //   排查"气泡不显示"时，先要分清是**回调没触发**还是**渲染没出来** ——
    //   这两者的修法完全不同，靠猜会白改好几轮。
    if (com.utilitybox.BuildConfig.DEBUG) {
        androidx.compose.runtime.LaunchedEffect(copiedCode) {
            android.util.Log.d("UbCopy", "copiedCode=$copiedCode")
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 日期标题（始终显示今天，作为"这是哪一天"的基准）
        val today = LocalDate.now()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = s.screenH, vertical = s.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatDateCn(today),
                    style = t.bodyMedium,
                    color = c.textSecondary,
                )
            }
            // 手动刷新（首次加载后始终可用）
            UbChip(
                label = "刷新",
                selected = false,
                onClick = { holder.refresh() },
            )
        }

        when (val ui = holder.state) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    UbLoading(lines = 6)
                }
            }

            is UiState.Failed -> {
                // 仓库把所有失败都归为业务态，正常不会走到这里；
                // 保底处理，避免出现"什么都不显示"的白屏。
                FailedView(error = ui.error, onRetry = { holder.refresh() }, modifier = Modifier.weight(1f))
            }

            UiState.Empty -> {
                // 同理：仓库用 DailyCodeState.NoDataForToday 表达"没数据"，
                // 这个分支保底，语义与"暂无数据"一致。
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    UbEmpty(
                        title = "暂无数据",
                        description = "数据源目前没有返回任何内容。",
                        actionLabel = "重新加载",
                        onAction = { holder.refresh() },
                    )
                }
            }

            is UiState.Success -> when (val state = ui.data) {
                is DailyCodeState.Today -> {
                    // ★ 闸门必须声明在 LazyColumn **之外** —— LazyListScope 的 lambda
                    //   不是 @Composable 上下文，在里面调 remember 编译不过。
                    val entrance = rememberUbListEntrance()
                    UbStaleBanner(
                        message = "判定依据：按本机抓取时间判定（接口未提供生效日期）",
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(
                            start = s.screenH,
                            end = s.screenH,
                            top = s.s,
                            bottom = LocalUbSize.current.bottomBarReserved,
                        ),
                        verticalArrangement = Arrangement.spacedBy(s.listGap),
                    ) {
                        itemsIndexed(state.codes, key = { _, it -> it.mapId }) { index, code ->
                            MapCodeCard(
                                code = code,
                                prominent = true,
                                onCopied = { copiedCode = it },
                                // ★ P11 补 14：用入场闸门把动画要回来（见 UbListEntrance）
                                modifier = Modifier
                                    .ubListEnter(code.mapId, index, entrance)
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
                    UbSourceFooter(
                        source = state.source.displayName,
                        updatedAt = state.updatedAtLabel,
                        isMock = state.isMock,
                        modifier = Modifier.padding(bottom = s.l),
                    )
                }

                is DailyCodeState.Stale -> {
                    // ★ 同上：闸门声明在 LazyColumn 之外
                    val entrance = rememberUbListEntrance()
                    // ★ 最关键的一条：过期数据不得以今日数据的样式呈现
                    UbStaleBanner(
                        message = "这是 ${state.fetchedDate.format(DateTimeFormatter.ofPattern("M 月 d 日"))} 的数据，" +
                            "今日密码尚未更新。请勿将以下密码当作今日密码使用。",
                        actionLabel = "刷新",
                        onAction = { holder.refresh() },
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(
                            start = s.screenH,
                            end = s.screenH,
                            top = s.s,
                            bottom = LocalUbSize.current.bottomBarReserved,
                        ),
                        verticalArrangement = Arrangement.spacedBy(s.listGap),
                    ) {
                        itemsIndexed(state.codes, key = { _, it -> it.mapId }) { index, code ->
                            MapCodeCard(
                                code = code,
                                prominent = false,
                                dimmed = true,
                                onCopied = { copiedCode = it },
                                // ★ P11 补 14：同上
                                modifier = Modifier
                                    .ubListEnter(code.mapId, index, entrance)
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
                    UbSourceFooter(
                        source = state.source.displayName,
                        updatedAt = "抓取于 ${state.fetchedDate}",
                        isMock = state.isMock,
                        modifier = Modifier.padding(bottom = s.l),
                    )
                }

                DailyCodeState.NoDataForToday -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        UbEmpty(
                            title = "今日密码尚未发布",
                            description = "数据源目前还没有给出今天的密码，通常在北京时间 00:05 后更新。",
                            actionLabel = "重新加载",
                            onAction = { holder.refresh() },
                        )
                    }
                }

                is DailyCodeState.Failed -> {
                    FailedView(error = state.error, onRetry = { holder.refresh() }, modifier = Modifier.weight(1f))
                }
            }
        }
    }

        // ---- 复制气泡（页面级浮层）----
        // ★ 贴在**底部**、避开悬浮导航栏：那里不会遮住任何正在阅读的内容，
        //   而且不管点的是第几行，提示都出现在同一个位置，用户不用去找。
        UbToast(
            message = copiedCode?.let { "已复制 $it" },
            onDismiss = { copiedCode = null },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = LocalUbSize.current.bottomBarReserved),
        )
    }
}

/**
 * 失败态。
 *
 * ★ 设计稿要求「请求失败时显示**错误类型**与重试入口」——
 *   所以这里按 [AppError] 分类给出**不同的说明与下一步**，
 *   而不是笼统地说"网络错误"。用户需要知道该重试、该换个网络、还是该去配 Key。
 */
@Composable
private fun FailedView(
    error: AppError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (title, desc, retryable) = when (error) {
        AppError.NoNetwork -> Triple(
            "网络不可用",
            "请检查网络连接后重试。",
            true,
        )

        AppError.Timeout -> Triple(
            "请求超时",
            "数据源响应较慢，可以直接重试。",
            true,
        )

        // ★ 这一条与下一条必须分开（P06 实测踩到的设计缺陷）：
        //   两者用户要采取的行动完全不同，混在一起会造成
        //   "设置页说已配置、功能页说未配置"的自相矛盾界面。
        AppError.ApiKeyMissing -> Triple(
            "还没填 API Key",
            "本功能的数据来自第三方接口，需要你自己的 API Key。" +
                "请在底部「设置」→「API Key」里填写后回来重试。",
            false,
        )

        AppError.Unauthorized -> Triple(
            "API Key 未被接受",
            "已填写的 API Key 被数据源拒绝了。可能是 Key 无效、已过期，" +
                "或没有调用本接口的权限。请到「设置」→「API Key」检查或更换。",
            false,
        )

        AppError.Malformed -> Triple(
            "数据格式无法识别",
            "数据源返回的内容与本应用预期的结构不一致，可能是对方已改版。" +
                "这属于数据源侧的变化，不是你的网络问题。",
            false,
        )

        is AppError.Business -> Triple(
            "数据源返回错误",
            "错误码 ${error.code}：${error.message.ifBlank { "（对方未提供说明）" }}",
            true,
        )

        is AppError.Server -> Triple(
            "数据源服务异常",
            "数据源返回 HTTP ${error.code}，通常是对方临时故障，稍后重试。",
            true,
        )

        AppError.SourceUnavailable -> Triple(
            "该功能暂无数据源",
            "当前没有可用的数据源来提供这项数据。",
            false,
        )

        is AppError.Unknown -> Triple(
            "请求失败",
            error.message.ifBlank { "发生了未预期的错误。" },
            true,
        )
    }

    Box(
        // 注意：`weight` 是 ColumnScope 扩展，只能由调用方传入（本函数不在 Column 作用域内）
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        UbError(
            title = title,
            description = desc,
            // 只有可重试的错误才给入口；"没配 Key""数据源改版"这类重试也没用，
            // 给了按钮反而误导（设计稿：不得用空按钮冒充已完成的交互）
            onRetry = if (retryable) onRetry else null,
        )
    }
}

@Composable
private fun MapCodeCard(
    code: DailyCode,
    prominent: Boolean,
    dimmed: Boolean = false,
    onCopied: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val r = LocalUbRadius.current
    val t = LocalUbTypography.current
    val sz = LocalUbSize.current
    val clipboard = LocalClipboardManager.current

    // ★ 卡片**不再自己显示复制反馈** —— 反馈由页面级气泡承担（见 DailyCodeScreen）。
    //   卡片只负责"通知外面复制了什么"。
    //   这样按钮永远显示「复制」：不变样、不改宽度、不抖动。
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(r.card))
            .background(c.bgElevated)
            .padding(s.cardPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s.m),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = code.mapName,
                style = t.titleMedium,
                color = if (dimmed) c.textSecondary else c.textPrimary,
            )
            if (code.code.isBlank()) {
                // 数据源没给密码时如实说明，不留空、不编造
                Text(text = "数据源未提供该地图的密码", style = t.caption, color = c.warning)
            }
        }

        if (code.code.isNotBlank()) {
            Text(
                text = code.code,
                style = t.titleLarge,
                color = if (dimmed) c.textSecondary else c.accent,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
            // 一键复制：样式**不随复制状态改变**
            // ★ P11：改用统一按压反馈 —— 复制按钮是高频操作，最需要"按下去有反应"
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(r.capsule))
                    .ubClickable {
                        clipboard.setText(AnnotatedString(code.code))
                        onCopied(code.code)
                    }
                    .background(c.bgSubtle)
                    .padding(horizontal = s.m, vertical = s.s),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(s.xs),
                ) {
                    UbIcons.Icon(
                        // ★ 图标**恒定**为复制图标 —— 不再因复制过而变成对勾
                        icon = UbIcon.Copy,
                        tint = c.textSecondary,
                        size = 16.dp,
                    )
                    Text(
                        // ★ 文字**恒定**为「复制」—— 不再变成「已复制」，
                        //   按钮宽度因此不会变化，整行不会抖动
                        text = "复制",
                        style = LocalUbTypography.current.labelMedium,
                        color = c.textSecondary,
                    )
                }
            }
        }
    }
}

private fun weekdayCn(d: LocalDate): String =
    listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[d.dayOfWeek.value - 1]
