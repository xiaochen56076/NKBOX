package com.utilitybox.game.deltaforce

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.utilitybox.core.design.UbIcon
import com.utilitybox.core.navigation.UbRoutes
import com.utilitybox.domain.model.GameDescriptor
import com.utilitybox.domain.model.ToolCategory
import com.utilitybox.domain.model.ToolDescriptor
import com.utilitybox.domain.model.ToolStatus
import com.utilitybox.domain.tool.GameModule
import com.utilitybox.domain.tool.ToolNavActions
import com.utilitybox.domain.tool.ToolProvider
import com.utilitybox.game.deltaforce.dailycode.DailyCodeScreen
import com.utilitybox.game.deltaforce.friendstatus.FriendStatusScreen
import com.utilitybox.game.deltaforce.itemprice.ItemPriceScreen

/**
 * 三角洲行动模块。
 *
 * 依据 docs/architecture.md §5：**游戏专属功能归属于对应游戏模块**，
 * 公共层不得依赖具体游戏的业务规则。
 *
 * ★ 本文件是"新增工具"的第 6 步（architecture.md §4.4）：
 *   实现完 [ToolProvider] 后，在这里的 [providers] 列表加一项。
 *   公共层（`core/`、`feature/`）**不会**因此改动。
 */
object DeltaForceModule : GameModule {

    override val game = GameDescriptor(
        id = "deltaforce",
        name = "三角洲行动",
        region = "CN",
        enabled = true,
    )

    override val providers: List<ToolProvider> = listOf(
        DailyCodeProvider,
        ItemPriceProvider,
        FriendStatusProvider,
        // ↓ 扩展机制验收：新增这一行 + 上面两个新文件，工具即出现在首页。
        //   公共层（根导航/首页/注册表/导航器/路由常量/图标/Tab 栏）零改动。
        com.utilitybox.game.deltaforce.pricerange.PriceRangeProvider,
        // ↓ P10 第二次验收：同样只加一行 + 新包，首页自动多出「弹药物价」
        com.utilitybox.game.deltaforce.ammoprice.AmmoPriceProvider,
    )
}

// ---------------------------------------------------------------------------
// 各工具 provider
// 每个 provider = 元信息（descriptor）+ 页面内容（screen），一一对应。
// 新增工具时照抄一个即可，公共层 0 改动。
// ---------------------------------------------------------------------------

object DailyCodeProvider : ToolProvider {
    override val descriptor = ToolDescriptor(
        id = "deltaforce.dailycode",
        gameId = "deltaforce",
        name = "今日密码",
        description = "每日地图密码，支持一键复制",
        category = ToolCategory.GAME_DATA,
        icon = UbIcon.Key,
        route = DeltaForceRoutes.DAILY_CODE,
    )

    @Composable
    override fun screen(nav: ToolNavActions, modifier: Modifier) {
        DailyCodeScreen(modifier = modifier)
    }

    /**
     * ★ P11 补 79：今日密码提供一个**首页摘要**（用户要求免去一次点击）。
     *
     * 首页只认识 `homeSummary` 这个槽位，不知道里面是"今日密码" ——
     * 具体渲染留在本工具自己的包里（见 `DailyCodeSummaryRow`）。
     */
    override val homeSummary: (@Composable () -> Unit)? = {
        com.utilitybox.game.deltaforce.dailycode.DailyCodeSummaryRow()
    }
}

object ItemPriceProvider : ToolProvider {
    override val descriptor = ToolDescriptor(
        id = "deltaforce.itemprice",
        gameId = "deltaforce",
        name = "物品物价",
        description = "查询当前价格与历史走势",
        category = ToolCategory.PRICE,
        icon = UbIcon.Tag,
        route = DeltaForceRoutes.ITEM_PRICE,
    )

    @Composable
    override fun screen(nav: ToolNavActions, modifier: Modifier) {
        // 二级页面通过宿主注入的 nav 打开，工具本身不依赖根导航器
        ItemPriceScreen(onOpenDetail = nav::openItemDetail, modifier = modifier)
    }
}

object FriendStatusProvider : ToolProvider {
    override val descriptor = ToolDescriptor(
        id = "deltaforce.friendstatus",
        gameId = "deltaforce",
        name = "好友状态",
        description = "好友在线情况",
        category = ToolCategory.GAME_DATA,
        icon = UbIcon.User,
        route = DeltaForceRoutes.FRIEND_STATUS,
        // 用户裁决：本功能先放放，保留接口占位，数据源就绪后再点亮。
        // 不隐藏入口，而是明确标注「开发中」，避免用户以为坏了。
        // 详见 domain/datasource/FriendStatusDataSource.kt 与 docs/data-sources.md §2.3。
        status = ToolStatus.PENDING_SOURCE,
        statusLabel = "开发中 · 数据源待接入",
    )

    @Composable
    override fun screen(nav: ToolNavActions, modifier: Modifier) {
        FriendStatusScreen(modifier = modifier)
    }
}
