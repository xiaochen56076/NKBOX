package com.utilitybox.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.utilitybox.core.design.LocalUbColors
import com.utilitybox.core.design.LocalUbSpacing
import com.utilitybox.core.design.LocalUbTypography
import com.utilitybox.core.design.SectionCard
import com.utilitybox.core.design.UbIcon
import com.utilitybox.core.design.UbNavRow

/**
 * 账号管理（P11 补 40 —— 按用户要求改成"入口列表"）。
 *
 * ★ 用户原话：
 *   > 「这个界面云备份是一个**单独的选项，进去才是**，而不是直接在账号管理的界面」
 *
 * 所以本页**只放入口**，不放任何配置项。点「云备份」才进 [CloudBackupScreen]。
 *
 * ★ 为什么这样更好（不只是照做）：
 *   账号管理天然是个**列表** —— 今天只有"云备份"一项，以后可能是
 *   库街区账号、三角洲账号……如果第一项就直接展开成表单，
 *   加第二项时整页结构就得推倒重来。
 *   先立住"列表 → 详情"的结构，后面只加行就行。
 */
@Composable
fun AccountManagerScreen(
    onOpenCloudBackup: () -> Unit,
    /** 云备份是否已登录 —— 显示在右侧 */
    cloudLoggedIn: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(s.s),
    ) {
        SectionCard(title = "账号") {
            Column {
                UbNavRow(
                    title = "云备份",
                    icon = UbIcon.Database,
                    onClick = onOpenCloudBackup,
                    trailingText = if (cloudLoggedIn) "已登录" else "未登录",
                )
            }
        }

        Text(
            // ★ 不要在这里写 Markdown 的 ** 加粗 —— Compose 的 Text **不解析 Markdown**，
            //   星号会原样渲染出来。这是实测在模拟器上看到的缺陷（P11 补 40）。
            text = "云备份会把唤取记录存到你自己的网盘里。" +
                "换手机或清空数据后，登录一次就能把记录拉回来。",
            style = t.caption,
            color = c.textTertiary,
        )
    }
}
