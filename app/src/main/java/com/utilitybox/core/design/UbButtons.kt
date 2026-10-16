package com.utilitybox.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * 按钮族。
 * 统一承担：最小触控目标（≥ 48dp，无障碍硬性要求）、按压反馈、圆角与文字样式。
 * 页面不得自行拼装按钮外观。
 */

@Composable
fun UbPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val r = LocalUbRadius.current
    val sz = LocalUbSize.current
    val t = LocalUbTypography.current

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = sz.minTouch)
            .clip(RoundedCornerShape(r.button))
            .background(if (enabled) c.accent else c.bgSubtle)
            .ubClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = s.xl, vertical = s.m),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = t.bodyLarge,
            color = if (enabled) c.textOnAccent else c.textTertiary,
        )
    }
}

@Composable
fun UbSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val r = LocalUbRadius.current
    val sz = LocalUbSize.current
    val t = LocalUbTypography.current

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = sz.minTouch)
            .clip(RoundedCornerShape(r.button))
            .background(c.bgElevated)
            .border(BorderStroke(1.dp, c.divider), RoundedCornerShape(r.button))
            .ubClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = s.xl, vertical = s.m),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = t.bodyLarge,
            color = if (enabled) c.textPrimary else c.textTertiary,
        )
    }
}

/** 纯文字按钮（用于横幅、行内操作） */
@Composable
fun UbTextButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val sz = LocalUbSize.current
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 32.dp)
            .clip(RoundedCornerShape(LocalUbRadius.current.button))
            .ubClickable(onClick = onClick)
            .padding(horizontal = s.s, vertical = s.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = LocalUbTypography.current.labelMedium, color = c.accent)
    }
}

/** 标签 / Chip */
@Composable
fun UbChip(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val c = LocalUbColors.current
    val r = LocalUbRadius.current
    val t = LocalUbTypography.current
    val base = Modifier
        .clip(RoundedCornerShape(r.chip))
        .background(if (selected) c.accent else c.bgSubtle)
        .then(if (onClick != null) Modifier.ubClickable(onClick = onClick) else Modifier)
        .padding(horizontal = 12.dp, vertical = 6.dp)

    Box(modifier = modifier.then(base), contentAlignment = Alignment.Center) {
        Text(
            text = label,
            style = t.labelMedium,
            color = if (selected) c.textOnAccent else c.textSecondary,
        )
    }
}

/** 设置项用的开关行 —— 统一触控尺寸与右侧控件间距 */
@Composable
fun UbSettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val sz = LocalUbSize.current
    val t = LocalUbTypography.current

    Row(
        modifier = modifier
            .defaultMinSize(minHeight = sz.minTouch)
            .then(if (onClick != null) Modifier.ubClickable(onClick = onClick) else Modifier)
            .padding(horizontal = s.screenH, vertical = s.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s.m),
    ) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = t.bodyLarge, color = c.textPrimary)
            if (subtitle != null) {
                Text(text = subtitle, style = t.caption, color = c.textTertiary)
            }
        }
        if (trailing != null) trailing()
    }
}

/**
 * 设置类页面的入口行：**图标 + 标题 + 右侧箭头**。
 *
 * ★ 依据用户提供的参考图：设置列表用「图标 + 文字 + 箭头」的卡片分组，
 *   而不是纯文字行。图标统一在左、箭头统一在右，视觉更整齐。
 *
 * ★ 不再提供 subtitle（用户明确要求「小字就不用了」）。
 *   需要说明状态的场景（如"已配置 · sk-…97d"）改用 [trailing] 放右侧值文字。
 */
@Composable
fun UbNavRow(
    title: String,
    icon: UbIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingText: String? = null,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val sz = LocalUbSize.current
    val t = LocalUbTypography.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = sz.minTouch)
            .ubClickable(onClick = onClick)
            .padding(horizontal = s.screenH, vertical = s.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s.m),
    ) {
        UbIcons.Icon(icon = icon, tint = c.textSecondary, size = 22.dp)
        Text(
            text = title,
            style = t.bodyLarge,
            color = c.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (trailingText != null) {
            Text(text = trailingText, style = t.caption, color = c.textTertiary)
        }
        UbIcons.Icon(icon = UbIcon.ChevronRight, tint = c.textTertiary, size = 18.dp)
    }
}

/** 无涟漪点击 + 统一按压反馈（实现见 [ubClickable] / `UbInteraction.kt`） */
// 说明：按压反馈已收敛到 UbInteraction.kt 的 ubClickable / ubPressScale，
// 此处不再保留"丢掉 interactionSource 的裸 clickable"那一版 ——
// 那正是全 App 按下去没反应的根因。
