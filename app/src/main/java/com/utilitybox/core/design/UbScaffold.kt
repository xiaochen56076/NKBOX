package com.utilitybox.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * 页面脚手架：统一大标题、返回、底部导航与安全区内边距。
 * 所有页面必须经此脚手架，避免各页自行处理边距导致视觉不统一。
 */
@Composable
fun UbScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    showBottomBar: Boolean = false,
    bottomBar: @Composable (() -> Unit)? = null,
    content: @Composable (Modifier) -> Unit,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current
    val sz = LocalUbSize.current

    Box(modifier = modifier.fillMaxSize().background(c.bgBase)) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            // 标题区
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = s.screenH, vertical = s.m)
                    .height(sz.headerHeight - s.m * 2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(s.s),
            ) {
                if (onBack != null) {
                    Box(
                        modifier = Modifier
                            .height(sz.minTouch)
                            .clip(RoundedCornerShape(LocalUbRadius.current.button))
                            .ubClickable(onClick = onBack)
                            .padding(horizontal = s.xs),
                        contentAlignment = Alignment.Center,
                    ) {
                        UbIcons.Icon(icon = UbIcon.ChevronLeft, tint = c.accent, size = sz.icon)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, style = t.titleLarge, color = c.textPrimary)
                    if (subtitle != null) {
                        Text(text = subtitle, style = t.caption, color = c.textTertiary)
                    }
                }
            }

            // 内容区
            content(Modifier.weight(1f))

            // 底部导航
            if (showBottomBar && bottomBar != null) {
                Box(
                    modifier = Modifier.padding(
                        horizontal = s.screenH,
                        vertical = sz.bottomBarInset,
                    )
                ) {
                    bottomBar()
                }
            }
        }
    }
}
