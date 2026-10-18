package com.utilitybox.game.deltaforce.dailycode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.utilitybox.core.design.LocalUbColors
import com.utilitybox.core.design.LocalUbSpacing
import com.utilitybox.core.design.LocalUbTypography
import com.utilitybox.core.design.SectionCard
import com.utilitybox.core.design.UbIcon
import com.utilitybox.core.design.UbIcons
import com.utilitybox.domain.model.AppError
import com.utilitybox.domain.model.DailyCodeState
import com.utilitybox.domain.model.UiState

/**
 * 今日密码的**首页摘要行**（P11 补 79）。
 *
 * ### 为什么
 *
 * 用户要求：「把今日密码给抠出来，放到三角洲那个名称卡片的下面一排显示，
 * 主要就免去一步的点击交互」。
 *
 * 原来要看密码得先点进「今日密码」工具页 —— 每天只看一眼的东西，
 * 多这一步点击是纯损耗。
 *
 * ### ★ 红线：绝不能把过期密码当今日密码
 *
 * `PROJECT_RULES.md` §3.4/§3.5 要求四态必须区分：
 * `Today` / `Stale` / `NoDataForToday` / `Failed`。
 * **摘要行不是"简略版"就可以含糊** —— 摘要恰恰是最容易被当成
 * "今日密码"的地方，所以：
 * - `Stale` **必须**写明「N 月 N 日数据」，且用警示色；
 * - `NoDataForToday` / `Failed` **只显示状态，不显示任何密码**；
 * - 从不编造、从不回填。
 *
 * ### 为什么放在工具包里而不是 feature/home
 *
 * `HomeScreen` 的文件头写着「本文件不出现任何具体工具名」（架构铁律）。
 * 所以首页只调用 `ToolProvider.homeSummary` 这个**抽象槽位**，
 * 具体渲染留在今日密码自己的包里。
 */
@Composable
fun DailyCodeSummaryRow(modifier: Modifier = Modifier) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current
    val holder = rememberDailyCodeHolder()

    val state = holder.state
    val biz: DailyCodeState? = (state as? UiState.Success)?.data

    SectionCard(
        modifier = modifier,
        // ★ P11 补 83：这个卡片放在**首页网格**里，网格的 `contentPadding` 已经加了
        //   `s.screenH`；而 `SectionCard` 自己也带一份 screenH —— 不置 0 就是
        //   **双重缩进**，卡片会比上面的英雄卡左右各窄 64px（用户圈出来说没对齐）。
        horizontalPadding = 0.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = s.cardPad, vertical = s.m)) {
            // ---- 标题行 ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                UbIcons.Icon(icon = UbIcon.Key, tint = c.accent, size = 16.dp)
                Spacer(Modifier.width(s.xs))
                Text(text = "今日密码", style = t.caption, color = c.textSecondary)
                Spacer(Modifier.weight(1f))
                // 只在有真实数据时给出"更新于"，否则不写任何时间说明（避免暗示数据新鲜）
                val stamp = (biz as? DailyCodeState.Today)?.updatedAtLabel
                if (!stamp.isNullOrBlank()) {
                    Text(text = stamp, style = t.caption, color = c.textTertiary)
                }
            }

            Spacer(Modifier.height(s.xs))

            when {
                // 首次加载：不显示占位数字，避免被误读成密码
                state is UiState.Loading -> Hint("加载中…", c.textTertiary)

                biz == null -> Hint("加载中…", c.textTertiary)

                biz is DailyCodeState.Today -> {
                    if (biz.codes.isEmpty()) {
                        Hint("数据源未给出密码", c.textTertiary)
                    } else {
                        biz.codes.forEach { code ->
                            CodeLine(
                                mapName = code.mapName,
                                code = code.code,
                                accent = c.accent,
                                primary = c.textPrimary,
                                secondary = c.textSecondary,
                            )
                        }
                    }
                }

                // ★ 过期：必须带原始日期 + 警示色，绝不冒充今日
                biz is DailyCodeState.Stale -> {
                    Hint(
                        "⚠ 这是 ${biz.fetchedDate.monthValue} 月 ${biz.fetchedDate.dayOfMonth} 日的数据，今日尚未更新",
                        c.warning,
                    )
                    biz.codes.forEach { code ->
                        CodeLine(
                            mapName = code.mapName,
                            code = code.code,
                            accent = c.warning,
                            primary = c.textSecondary,
                            secondary = c.textTertiary,
                        )
                    }
                }

                biz is DailyCodeState.NoDataForToday -> Hint("今日密码尚未发布", c.textTertiary)

                // ★ P11 补 80：**区分"没绑 API"和"真的失败"**（用户要求）。
                //
                //   原来一律显示红字「加载失败」—— 但没绑 API 不是故障，
                //   是**还没配置**。红色错误会让人以为 App 坏了，
                //   而正确的下一步只是"去设置里填个 Key"。
                //   所以：没绑 → 中性提示 + 告诉去哪绑；Key 无效 → 警示色；其它 → 才是红色故障。
                biz is DailyCodeState.Failed -> when (biz.error) {
                    AppError.ApiKeyMissing ->
                        Hint("请绑定 API（设置 → API Key）", c.textTertiary)

                    AppError.Unauthorized ->
                        Hint("API Key 无效或已过期，请到设置里更新", c.warning)

                    else ->
                        Hint("今日密码加载失败，点进工具页可重试", c.danger)
                }

                else -> Hint("加载中…", c.textTertiary)
            }
        }
    }
}

@Composable
private fun Hint(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = LocalUbTypography.current.caption,
        color = color,
    )
}

/** 一行「地图名 —— 密码」。密码用等宽感的大字重，方便直接抄 */
@Composable
private fun CodeLine(
    mapName: String,
    code: String,
    accent: androidx.compose.ui.graphics.Color,
    primary: androidx.compose.ui.graphics.Color,
    secondary: androidx.compose.ui.graphics.Color,
) {
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current

    if (code.isBlank()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s.xs),
    ) {
        Text(text = mapName, style = t.caption, color = secondary)
        Spacer(Modifier.width(s.xs))
        Text(
            text = code,
            style = t.bodyLarge,
            color = accent,
            // 密码是"要抄下来"的内容，字重加大 + 字距放宽，避免 0/O 混淆时看不清
            modifier = Modifier.weight(1f),
        )
        // 占位保持右侧对齐稳定（无操作按钮）
        Text(text = "", style = t.caption, color = primary)
    }
}
