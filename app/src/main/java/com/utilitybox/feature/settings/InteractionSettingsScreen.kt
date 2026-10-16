package com.utilitybox.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.utilitybox.core.design.*

/**
 * 交互设置页（P11 补 24）。
 *
 * ★ 用户要求：「那个交互做成和这些一样的那种选项，**可以点进去的**」——
 *   即与「工具管理 / 外观 / 数据」并列的入口行 + 独立二级页，
 *   而不是把开关直接摊在「设置」页里。
 *
 * ★ 这个改动不只是形式：**开关会长大**。
 *   现在只有一项，以后会有"长按预览""震动反馈""左右滑动切换游戏"之类。
 *   摊在「设置」页里，那一页会越来越长；做成子页则天然有地方放。
 *
 * ★ 页面外壳（返回键 + 标题「交互」）由 `MainActivity` 的 `SubPage` 提供，
 *   本页**不自己画标题** —— 这正是 P11 补 23 修的那个坑：
 *   二级页自绘标题会变成两个标题叠在一起。
 */
@Composable
fun InteractionSettingsScreen(
    showGameSwitch: Boolean,
    onShowGameSwitchChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            // 悬浮底部导航会盖住内容，留出它的占位
            .padding(bottom = LocalUbSize.current.bottomBarReserved),
        verticalArrangement = Arrangement.spacedBy(s.s),
    ) {
        SectionCard(title = "游戏切换") {
            Column {
                UbSettingRow(
                    title = "游戏切换入口",
                    subtitle = if (showGameSwitch) {
                        "在游戏页顶部显示「切换到 XX」"
                    } else {
                        "已隐藏 —— 仍可用底部导航切换游戏"
                    },
                    onClick = { onShowGameSwitchChange(!showGameSwitch) },
                    trailing = {
                        Switch(
                            checked = showGameSwitch,
                            onCheckedChange = onShowGameSwitchChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = c.textOnAccent,
                                checkedTrackColor = c.accent,
                            ),
                        )
                    },
                )
            }
        }

        // ★ 副标题如实说明"关掉之后还能怎么切" ——
        //   否则用户会以为关掉就没法换游戏了，不敢关。
        Text(
            text = "关掉后，仍然可以在底部导航栏切换游戏，也可以用左右滑动。",
            style = t.caption,
            color = c.textTertiary,
            modifier = Modifier.padding(horizontal = s.cardPad),
        )
    }
}
