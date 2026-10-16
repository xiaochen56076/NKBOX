package com.utilitybox.domain.tool

import com.utilitybox.domain.model.GameDescriptor

/**
 * 应用装配容器 —— **唯一的注册点**。
 *
 * 依据 docs/architecture.md §4.4 第 7 步与 §5.4：
 * 新增工具 / 新增游戏时，**只需在这里各加一项**。
 *
 * ★ 为什么用显式列表而不是反射/SPI/动态加载：
 *   见 architecture.md §4.5 —— 显式注册**编译期安全、可追踪、零依赖、易测试**；
 *   反射在 R8 混淆下脆弱，动态加载 dex 则是设计稿明确禁止的（安全风险）。
 *
 * ★ 之所以要有这个容器而不是让注册表直接写死列表：
 *   注册表若自己 `listOf(三角洲的三个工具)`，就等于**公共层依赖了具体游戏**，
 *   违反 architecture.md §5 的依赖方向。由容器装配、模块自报，才能做到
 *   "接第二款游戏时三角洲代码 0 改动"。
 */
object AppContainer {

    /**
     * 已接入的游戏模块。
     *
     * 新增游戏：在这里加一项即可。游戏 tab、工具列表、缓存命名空间
     * 都会自动带上它（缓存命名空间用 `game.id`）。
     *
     * ★ P11 接入第二款游戏「鸣潮」：**三角洲的代码一行未改**，
     *   这正是 architecture.md §5.4 承诺的"接第二款游戏时第一款 0 改动"。
     *   鸣潮目前只有页面骨架、没有工具（用户要求先预留）。
     */
    val games: List<GameModule> = listOf(
        com.utilitybox.game.deltaforce.DeltaForceModule,
        com.utilitybox.game.wutheringwaves.WutheringWavesModule,
    )

    /** 全部工具 provider（扁平化，供注册表构建视图） */
    val providers: List<ToolProvider> = games.flatMap { it.providers }

    /**
     * 默认激活的游戏。
     *
     * ★ 保留但**不再用于"首页显示哪个游戏的工具"** —— 那是 `gameId` 显式传参的事。
     *   现在只有"首次启动落在哪个 tab"这类场景会用到它。
     */
    val activeGame: GameDescriptor = games.first().game

    /** 按 id 找游戏模块（tab 分发用） */
    fun gameById(id: String): GameModule? = games.firstOrNull { it.game.id == id }
}
