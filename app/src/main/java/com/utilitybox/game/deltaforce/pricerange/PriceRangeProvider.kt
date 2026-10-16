package com.utilitybox.game.deltaforce.pricerange

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
 * 价格区间统计的 provider。
 *
 * ★ 这个文件是**扩展机制验收的产物**：加上 `PriceRangeScreen.kt` 与本文件，
 *   再在 `DeltaForceModule.providers` 加一行，工具就出现在首页了。
 *   公共层（根导航 / 首页 / 注册表 / 导航器 / 路由常量 / 图标 / Tab 栏）**零改动**。
 */
object PriceRangeProvider : ToolProvider {
    override val descriptor = ToolDescriptor(
        id = "deltaforce.pricerange",
        gameId = "deltaforce",
        name = "价格区间",
        description = "按价格档位统计物品分布",
        category = ToolCategory.PRICE,
        icon = UbIcon.Grid,
        route = DeltaForceRoutes.PRICE_RANGE,
        status = ToolStatus.EXPERIMENTAL,
        statusLabel = "实验性 · 模拟数据",
    )

    @Composable
    override fun screen(nav: ToolNavActions, modifier: Modifier) {
        PriceRangeScreen(modifier = modifier)
    }
}
