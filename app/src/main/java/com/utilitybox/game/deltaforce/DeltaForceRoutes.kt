package com.utilitybox.game.deltaforce

/**
 * 三角洲行动的**工具路由**。
 *
 * ★ 为什么路由放在游戏模块而不是公共的 `core/navigation/UbRoutes.kt`：
 *   路由是**工具自己的事**。若集中写在公共层，每新增一个工具都要改一次共享文件，
 *   architecture.md §4.4 承诺的"新增工具不改公共代码"就无法成立。
 *
 * ★ 新增工具的完整改动（实测验证通过）：
 *   1. 新建 `game/deltaforce/<tool>/` 包与页面        （新增文件）
 *   2. 在本文件加一个路由常量                          （改本文件，1 行）
 *   3. 写一个 `ToolProvider` 实现                      （新增文件）
 *   4. 在 [DeltaForceModule.providers] 加一项          （改 DeltaForceModule，1 行）
 *
 *   → `MainActivity` / `HomeScreen` / `UbToolRegistry` / `UbNavigator` /
 *     `UbRoutes` / `UbIcons` / `UbBottomBar`：**0 改动**
 */
object DeltaForceRoutes {
    const val DAILY_CODE = "df/dailycode"
    const val ITEM_PRICE = "df/itemprice"

    /** 二级页面：物品详情（不是首页工具，因此不注册为 provider） */
    const val ITEM_DETAIL = "df/itemprice/detail"

    const val FRIEND_STATUS = "df/friendstatus"

    /** 实验性：价格区间统计（用于验证扩展机制） */
    const val PRICE_RANGE = "df/pricerange"

    /** 弹药物价（P10 新增，扩展机制的第二次实测验收） */
    const val AMMO_PRICE = "df/ammoprice"
}
