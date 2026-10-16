package com.utilitybox.game.deltaforce.ammoprice

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.utilitybox.core.design.UbIcon
import com.utilitybox.domain.model.ToolCategory
import com.utilitybox.domain.model.ToolDescriptor
import com.utilitybox.domain.model.ToolStatus
import com.utilitybox.domain.tool.ToolNavActions
import com.utilitybox.domain.tool.ToolProvider
import com.utilitybox.game.deltaforce.DeltaForceRoutes

/**
 * 弹药物价工具（P10 新增）。
 *
 * ★ 这个工具是**扩展机制的活体验收**：它由 `DeltaForceModule.providers` 里的一行注册，
 *   `MainActivity` / 首页 / 导航 / 设计系统**一行都没改**。
 *
 * 端点为 P01 实测可用的 `/api/v1/df/object/ammo`（113 项），
 * 且**自带 `hasValidPriceData:false` 样本** —— 正好在真实数据上验证价格红线。
 */
object AmmoPriceProvider : ToolProvider {

    override val descriptor = ToolDescriptor(
        id = "deltaforce.ammoprice",
        gameId = "deltaforce",
        name = "弹药物价",
        description = "各口径弹药的成交均价与涨跌",
        category = ToolCategory.PRICE,
        icon = UbIcon.Tag,
        route = DeltaForceRoutes.AMMO_PRICE,
        status = ToolStatus.ENABLED,
    )

    @Composable
    override fun screen(nav: ToolNavActions, modifier: Modifier) {
        AmmoPriceScreen(modifier = modifier)
    }
}
