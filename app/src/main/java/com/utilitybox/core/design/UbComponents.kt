package com.utilitybox.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 统一状态组件。
 *
 * 依据 docs/architecture.md 原则二：三个业务模块**必须复用**同一套状态模型与组件，
 * 而不是各写三套互不兼容的实现。
 *
 * 每个数据页必须实现四种状态：加载中 / 空数据 / 错误 / 有数据（docs/ui-design.md §3.8）。
 */

/** 加载中：骨架屏，不用空白页 */
@Composable
fun UbLoading(
    modifier: Modifier = Modifier,
    lines: Int = 3,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val r = LocalUbRadius.current
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = s.screenH),
        verticalArrangement = Arrangement.spacedBy(s.listGap),
    ) {
        repeat(lines) { i ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (i == 0) 96.dp else 68.dp)
                    .clip(RoundedCornerShape(r.card))
                    .background(c.bgElevated),
            ) {
                Text(
                    text = "",
                    modifier = Modifier.padding(s.cardPad),
                )
            }
        }
        Text(
            text = "加载中…",
            style = LocalUbTypography.current.caption,
            color = c.textTertiary,
            modifier = Modifier.padding(top = s.xs),
        )
    }
}

/** 空数据：必须说明"为什么空" + "下一步做什么" */
@Composable
fun UbEmpty(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    StatusBlock(
        icon = UbIcon.Empty,
        iconTint = LocalUbColors.current.textTertiary,
        title = title,
        description = description,
        modifier = modifier,
        actionLabel = actionLabel,
        onAction = onAction,
    )
}

/**
 * 错误态：必须显示"错误类型文案" + 重试入口。
 * 文案映射依据 docs/ui-design.md §3.8。
 */
@Composable
fun UbError(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = "重试",
) {
    StatusBlock(
        icon = UbIcon.Warning,
        iconTint = LocalUbColors.current.danger,
        title = title,
        description = description,
        modifier = modifier,
        actionLabel = if (onRetry != null) retryLabel else null,
        onAction = onRetry,
    )
}

@Composable
private fun StatusBlock(
    icon: UbIcon,
    iconTint: Color,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String?,
    onAction: (() -> Unit)?,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = s.screenH, vertical = s.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(s.m),
    ) {
        UbIcons.Icon(icon = icon, tint = iconTint, size = 40.dp)
        Text(text = title, style = t.titleMedium, color = c.textPrimary, textAlign = TextAlign.Center)
        Text(
            text = description,
            style = t.bodyMedium,
            color = c.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            UbSecondaryButton(label = actionLabel, onClick = onAction)
        }
    }
}

/**
 * 陈旧数据横幅。
 * 依据红线：过期数据**不得**以新鲜数据的样式呈现，必须显示原始抓取时间。
 */
@Composable
fun UbStaleBanner(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val r = LocalUbRadius.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = s.screenH)
            .clip(RoundedCornerShape(r.button))
            .background(c.warning.copy(alpha = 0.12f))
            .padding(horizontal = s.m, vertical = s.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s.s),
    ) {
        UbIcons.Icon(icon = UbIcon.Clock, tint = c.warning, size = 18.dp)
        Text(
            text = message,
            style = LocalUbTypography.current.caption,
            color = c.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            UbTextButton(label = actionLabel, onClick = onAction)
        }
    }
}

/**
 * 模拟数据标记。
 * 依据 PROJECT_RULES.md §8：开发期允许 mock，但必须在 UI 上有可见标记。
 * 交付前应能通过全局搜索 isMock = true 定位所有残留 mock。
 */
@Composable
fun UbMockBadge(modifier: Modifier = Modifier) {
    val c = LocalUbColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(LocalUbRadius.current.chip))
            .background(c.warning)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        UbIcons.Icon(icon = UbIcon.Flask, tint = c.textOnAccent, size = 11.dp)
        Text(
            text = "模拟数据",
            style = LocalUbTypography.current.labelMedium,
            color = c.textOnAccent,
        )
    }
}

/** 数据来源与更新时间页脚 —— 每个承载真实数据的页面都应显示 */
@Composable
fun UbSourceFooter(
    source: String,
    updatedAt: String,
    modifier: Modifier = Modifier,
    isMock: Boolean = false,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = s.screenH, vertical = s.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s.s),
    ) {
        if (isMock) UbMockBadge()
        Text(
            text = "来源：$source · 更新 $updatedAt",
            style = LocalUbTypography.current.caption,
            color = c.textTertiary,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 状态色点 —— ⚠️ 必须与文字同时出现，不可仅靠颜色区分（无障碍） */
@Composable
fun UbStatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(LocalUbSize.current.statusDot)
            .clip(RoundedCornerShape(999.dp))
            .background(color),
    )
}

/** 细分隔线 */
@Composable
fun UbDivider(modifier: Modifier = Modifier, inset: Boolean = true) {
    val s = LocalUbSpacing.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = if (inset) s.screenH else 0.dp, end = if (inset) s.screenH else 0.dp)
            .height(1.dp)
            .background(LocalUbColors.current.divider),
    )
}

/** 小工具：占位宽度（避免魔法数值） */
@Composable
fun UbSpacerH(width: androidx.compose.ui.unit.Dp) = Box(Modifier.width(width))
