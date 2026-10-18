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
    val s = LocalUbSpacing.current

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

        // ★ P11 补 86：删除下方那段灰色小字说明（用户要求「这个界面下面的小字去掉」）。
        //
        //   原文：「云备份会把唤取记录存到你自己的网盘里。换手机或清空数据后，
        //   登录一次就能把记录拉回来。」
        //
        //   ★ 删除的只是**首页列表上的说明文案**。云备份页自己仍然会说明它做什么 ——
        //     用户点进去就能看到，信息没有丢失，只是不在入口处重复。
    }
}
